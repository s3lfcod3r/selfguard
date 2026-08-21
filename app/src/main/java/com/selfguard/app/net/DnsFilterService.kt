package com.selfguard.app.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.selfguard.app.MainActivity
import com.selfguard.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/** Laufende Zähler des Filters, für die Anzeige. */
data class FilterStats(
    val blocked: Long = 0,
    val passed: Long = 0,
    val recentBlocked: List<String> = emptyList(),
) {
    val total: Long get() = blocked + passed
}

/**
 * Der Werbe- und Tracker-Filter.
 *
 * Der entscheidende Entwurfsgedanke steckt in [establish]: Die
 * Tunnelschnittstelle bekommt **nur eine einzige Route** -- die auf den
 * vorgetäuschten Namensserver. Damit wandert ausschließlich DNS-Verkehr
 * durch den Tunnel, aller übrige Verkehr läuft unberührt daran vorbei.
 *
 * Das erspart genau die Arbeit, für die vergleichbare Projekte einen
 * vollständigen Netzwerkstapel mitbringen: kein Zerlegen von TCP, keine
 * Verbindungsverfolgung, kein messbarer Akkuverbrauch. Der Filter sieht
 * Anfragen wie "welche Adresse hat werbenetz.example" und beantwortet sie
 * selbst oder reicht sie weiter.
 *
 * Bekannte Grenze: DNS über TCP wird nicht bedient. Android fragt praktisch
 * immer über UDP; nur bei ungewöhnlich großen Antworten fände ein Programm
 * keinen Weg zurück.
 */
class DnsFilterService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null
    private var scope: CoroutineScope? = null

    /** Ausgehende Pakete laufen über einen einzigen Schreiber. */
    private val outgoing = Channel<ByteArray>(capacity = OUTGOING_CAPACITY)

    private var blocklist: Blocklist = Blocklist.EMPTY
    private var upstreamServers: List<InetAddress> = emptyList()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Muss als Allererstes geschehen: der Dienst wird mit
        // startForegroundService gestartet und hat danach fünf Sekunden Zeit,
        // sich als Vordergrunddienst zu melden. Sonst beendet Android ihn mit
        // einer Ausnahme -- auch dann, wenn wir gleich wieder aufhören, weil
        // die VPN-Freigabe fehlt.
        startForeground(NOTIFICATION_ID, buildNotification(FilterStats()))

        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        startFiltering()
        // START_STICKY: der Filter soll nach einem Speicherengpass von selbst
        // zurückkommen, ohne dass der Nutzer die App wieder öffnen muss.
        return START_STICKY
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    /** Vom System gerufen, wenn der Nutzer den Tunnel in den Einstellungen kappt. */
    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    private fun startFiltering() {
        if (tunnel != null) return

        blocklist = BlocklistStore.load(this)
        // Die echten Namensserver müssen vor dem Aufbau gelesen werden --
        // danach steht dort unsere eigene vorgetäuschte Adresse.
        upstreamServers = systemDnsServers().ifEmpty { FALLBACK_DNS.map(InetAddress::getByName) }

        val descriptor = establish() ?: run {
            shutdown()
            return
        }
        tunnel = descriptor

        _running.value = true

        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = serviceScope
        serviceScope.launch { writeLoop(descriptor) }
        serviceScope.launch { readLoop(descriptor, serviceScope) }
    }

    /**
     * Baut die Tunnelschnittstelle auf.
     *
     * Die drei Zeilen mit Adresse, Namensserver und Route sind der ganze
     * Trick: Der vorgetäuschte Namensserver ist die einzige Adresse, für die
     * eine Route gesetzt wird. Alles andere kennt der Tunnel nicht und läuft
     * deshalb außen herum.
     */
    private fun establish(): ParcelFileDescriptor? = runCatching {
        Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(TUNNEL_ADDRESS, TUNNEL_PREFIX)
            .addDnsServer(PROXY_DNS_ADDRESS)
            .addRoute(PROXY_DNS_ADDRESS, HOST_PREFIX)
            .setMtu(MTU)
            .setBlocking(true)
            .also { builder ->
                // Die eigene App bleibt außen vor, damit das Nachladen der
                // Sperrlisten nicht durch den eigenen Filter läuft.
                runCatching { builder.addDisallowedApplication(packageName) }
            }
            .establish()
    }.getOrNull()

    /** Liest Pakete aus dem Tunnel und bearbeitet jede Anfrage nebenläufig. */
    private suspend fun readLoop(descriptor: ParcelFileDescriptor, serviceScope: CoroutineScope) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(MTU)

        try {
            while (serviceScope.isActive) {
                val length = input.read(buffer)
                if (length <= 0) continue

                val datagram = Ipv4Udp.parse(buffer, length) ?: continue
                if (datagram.destinationPort != DNS_PORT) continue

                // Kopie ziehen: der Lesepuffer wird sofort wiederverwendet.
                val request = datagram.copy(payload = datagram.payload.copyOf())
                serviceScope.launch { handle(request) }
            }
        } catch (_: Exception) {
            // Beim Abbau wird die Schnittstelle geschlossen; das erzeugt hier
            // eine Ausnahme und ist der normale Weg aus der Schleife.
        }
    }

    /** Entscheidet über eine einzelne Anfrage. */
    private suspend fun handle(request: UdpDatagram) {
        val name = DnsMessage.questionName(request.payload)

        if (name != null && blocklist.blocks(name)) {
            countBlocked(name)
            val response = DnsMessage.nxDomainResponse(request.payload)
            outgoing.trySend(Ipv4Udp.buildResponse(request, response))
            return
        }

        val answer = askUpstream(request.payload)
        if (answer != null) {
            countPassed()
            outgoing.trySend(Ipv4Udp.buildResponse(request, answer))
        }
    }

    /**
     * Reicht eine erlaubte Anfrage an den echten Namensserver weiter.
     *
     * Drei Vorkehrungen, die zusammen verhindern, dass jemand einer
     * Namensauflösung eine falsche Adresse unterschiebt:
     *
     * 1. `protect` hält die Abfrage aus dem eigenen Tunnel heraus -- sonst
     *    liefe sie im Kreis.
     * 2. `connect` bindet den Anschluss an genau diesen Namensserver. Der Kern
     *    verwirft danach jedes Paket von einer anderen Adresse. Ohne das nimmt
     *    ein ungebundener Anschluss Antworten von **jedem** Absender an.
     * 3. Die Vorgangskennung der Antwort muss zur Anfrage passen. Damit
     *    scheitern auch Versuche, die die Absenderadresse fälschen.
     *
     * Ohne diese Prüfungen könnte ein Angreifer im selben Netz beliebige
     * Namen umlenken -- für alle Apps auf dem Gerät gleichzeitig, solange der
     * Filter läuft.
     */
    private fun askUpstream(query: ByteArray): ByteArray? {
        val expectedId = DnsMessage.transactionId(query) ?: return null

        for (server in upstreamServers) {
            val answer = runCatching {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = UPSTREAM_TIMEOUT_MS
                    socket.connect(InetSocketAddress(server, DNS_PORT))
                    socket.send(DatagramPacket(query, query.size))
                    receiveMatching(socket, expectedId)
                }
            }.getOrNull()
            if (answer != null) return answer
        }
        return null
    }

    /**
     * Wartet auf eine Antwort mit passender Vorgangskennung.
     *
     * Unpassende Pakete werden verworfen, nicht zurückgegeben -- ein
     * vorgeschobenes Paket soll die echte Antwort nicht verdrängen. Die Zahl
     * der Versuche ist begrenzt, damit ein Dauerbeschuss den Aufruf nicht
     * festhält.
     */
    private fun receiveMatching(socket: DatagramSocket, expectedId: Int): ByteArray? {
        val buffer = ByteArray(MTU)
        repeat(MAX_UPSTREAM_ATTEMPTS) {
            val reply = DatagramPacket(buffer, buffer.size)
            socket.receive(reply)

            // Passt die Antwort nicht mehr in den Tunnel, wäre sie beim
            // Zurückschreiben abgeschnitten und damit unbrauchbar.
            if (reply.length >= buffer.size) return null

            val response = buffer.copyOf(reply.length)
            if (DnsMessage.transactionId(response) == expectedId) return response
        }
        return null
    }

    /** Einziger Schreiber auf die Tunnelschnittstelle. */
    private suspend fun writeLoop(descriptor: ParcelFileDescriptor) {
        val output = FileOutputStream(descriptor.fileDescriptor)
        try {
            for (packet in outgoing) {
                output.write(packet)
            }
        } catch (_: Exception) {
            // Schnittstelle geschlossen; Schleife endet.
        }
    }

    private fun systemDnsServers(): List<InetAddress> = runCatching {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val network = manager.activeNetwork ?: return emptyList()
        manager.getLinkProperties(network)?.dnsServers.orEmpty()
            // Nur IPv4: die Tunnelschnittstelle führt keine IPv6-Adresse.
            .filter { it.address.size == IPV4_ADDRESS_SIZE }
    }.getOrDefault(emptyList())

    private fun countBlocked(domain: String) {
        val current = _stats.value
        val recent = (listOf(domain) + current.recentBlocked).take(RECENT_LIMIT)
        _stats.value = current.copy(blocked = current.blocked + 1, recentBlocked = recent)
    }

    private fun countPassed() {
        _stats.value = _stats.value.let { it.copy(passed = it.passed + 1) }
    }

    private fun shutdown() {
        _running.value = false
        scope?.cancel()
        scope = null
        runCatching { tunnel?.close() }
        tunnel = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(stats: FilterStats): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.filter_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            manager?.createNotificationChannel(channel)
        }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.filter_active))
            .setContentText(getString(R.string.filter_blocked_count, stats.blocked))
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val ACTION_STOP = "com.selfguard.app.STOP_FILTER"

        /**
         * Adressbereich für den Tunnel. Bewusst aus dem gemeinsam genutzten
         * Bereich 10.x, aber ungewöhnlich weit hinten, damit er nicht mit
         * einem Heimnetz zusammenfällt.
         */
        private const val TUNNEL_ADDRESS = "10.111.222.1"
        private const val PROXY_DNS_ADDRESS = "10.111.222.2"
        private const val TUNNEL_PREFIX = 30
        private const val HOST_PREFIX = 32

        private const val MTU = 1500
        private const val DNS_PORT = 53
        private const val UPSTREAM_TIMEOUT_MS = 4_000

        /** Wie oft ein untergeschobenes Paket verworfen wird, bevor aufgegeben wird. */
        private const val MAX_UPSTREAM_ATTEMPTS = 4
        private const val OUTGOING_CAPACITY = 256
        private const val RECENT_LIMIT = 50
        private const val IPV4_ADDRESS_SIZE = 4

        /** Quad9 filtert selbst bereits Schadsoftware und speichert keine Adressen. */
        private val FALLBACK_DNS = listOf("9.9.9.9", "149.112.112.112")

        private const val CHANNEL_ID = "selfguard_filter"
        private const val NOTIFICATION_ID = 42

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _stats = MutableStateFlow(FilterStats())
        val stats: StateFlow<FilterStats> = _stats.asStateFlow()

        fun start(context: Context) {
            startCommand(context, Intent(context, DnsFilterService::class.java))
        }

        fun stop(context: Context) {
            startCommand(
                context,
                Intent(context, DnsFilterService::class.java).setAction(ACTION_STOP),
            )
        }

        /**
         * Ab Android 8 muss ein Vordergrunddienst über
         * `startForegroundService` angestoßen werden -- ein einfaches
         * `startService` lehnt das System aus dem Hintergrund ab.
         */
        private fun startCommand(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
