package com.selfguard.app.net

/**
 * Ein aus dem Tunnel gelesenes UDP-Paket, so weit zerlegt wie noetig.
 */
data class UdpDatagram(
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray,
) {
    // Ein data class mit Feldern vom Typ ByteArray braucht diese beiden
    // Methoden von Hand, sonst wird nach Objektkennung statt nach Inhalt
    // verglichen.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UdpDatagram) return false
        return sourcePort == other.sourcePort &&
            destinationPort == other.destinationPort &&
            sourceAddress.contentEquals(other.sourceAddress) &&
            destinationAddress.contentEquals(other.destinationAddress) &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = sourceAddress.contentHashCode()
        result = 31 * result + destinationAddress.contentHashCode()
        result = 31 * result + sourcePort
        result = 31 * result + destinationPort
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * Der Umgang mit rohen IPv4-Paketen aus der Tunnelschnittstelle.
 *
 * Warum das ueberhaupt noetig ist: aus einem VpnService kommen rohe
 * IP-Pakete, keine fertigen Datagramme. Der Umfang bleibt aber winzig, weil
 * die Tunnelschnittstelle so eng geroutet wird, dass **ausschliesslich**
 * DNS-Verkehr hier ankommt. Ohne diesen Kniff muesste man den gesamten
 * Netzverkehr des Geraets zerlegen -- der Aufwand, fuer den andere Projekte
 * einen kompletten Netzwerkstapel mitbringen.
 *
 * IPv6 wird bewusst nicht behandelt: die Tunnelschnittstelle bekommt nur eine
 * IPv4-Adresse, damit kommt hier auch nur IPv4 an.
 */
object Ipv4Udp {

    private const val VERSION_IPV4 = 4
    private const val PROTOCOL_UDP = 17
    private const val MIN_HEADER_WORDS = 5
    private const val IP_HEADER_SIZE = 20
    private const val UDP_HEADER_SIZE = 8

    private const val OFFSET_TOTAL_LENGTH = 2
    private const val OFFSET_PROTOCOL = 9
    private const val OFFSET_CHECKSUM = 10
    private const val OFFSET_SOURCE_IP = 12
    private const val OFFSET_DEST_IP = 16

    /** Standardwert fuer die Lebensdauer; das Paket bleibt ohnehin lokal. */
    private const val DEFAULT_TTL = 64

    /**
     * Zerlegt ein rohes Paket. Alles, was kein IPv4-UDP ist, liefert null und
     * wird vom Aufrufer unveraendert verworfen.
     */
    fun parse(packet: ByteArray, length: Int): UdpDatagram? {
        if (length < IP_HEADER_SIZE) return null

        val versionAndLength = packet[0].toInt() and 0xFF
        if ((versionAndLength shr 4) != VERSION_IPV4) return null

        val headerWords = versionAndLength and 0x0F
        if (headerWords < MIN_HEADER_WORDS) return null
        val headerSize = headerWords * 4

        if ((packet[OFFSET_PROTOCOL].toInt() and 0xFF) != PROTOCOL_UDP) return null
        if (length < headerSize + UDP_HEADER_SIZE) return null

        val udpStart = headerSize
        val udpLength = readUShort(packet, udpStart + 4)
        // Die Laengenangabe schliesst den UDP-Kopf ein.
        val payloadSize = (udpLength - UDP_HEADER_SIZE)
            .coerceAtMost(length - udpStart - UDP_HEADER_SIZE)
        if (payloadSize <= 0) return null

        val payloadStart = udpStart + UDP_HEADER_SIZE
        return UdpDatagram(
            sourceAddress = packet.copyOfRange(OFFSET_SOURCE_IP, OFFSET_SOURCE_IP + 4),
            destinationAddress = packet.copyOfRange(OFFSET_DEST_IP, OFFSET_DEST_IP + 4),
            sourcePort = readUShort(packet, udpStart),
            destinationPort = readUShort(packet, udpStart + 2),
            payload = packet.copyOfRange(payloadStart, payloadStart + payloadSize),
        )
    }

    /**
     * Baut die Antwort zurueck an den Fragesteller: Adressen und Anschluesse
     * werden getauscht, die Nutzdaten sind die DNS-Antwort.
     *
     * Die UDP-Pruefsumme bleibt null. Bei IPv4 ist das ausdruecklich erlaubt
     * und bedeutet "nicht berechnet" -- das spart einen Rechendurchgang je
     * Antwort. Die IP-Pruefsumme dagegen ist Pflicht und wird gesetzt.
     */
    fun buildResponse(request: UdpDatagram, payload: ByteArray): ByteArray {
        val totalLength = IP_HEADER_SIZE + UDP_HEADER_SIZE + payload.size
        val packet = ByteArray(totalLength)

        packet[0] = ((VERSION_IPV4 shl 4) or MIN_HEADER_WORDS).toByte()
        packet[1] = 0 // keine besondere Behandlung
        writeUShort(packet, OFFSET_TOTAL_LENGTH, totalLength)
        writeUShort(packet, 4, 0) // Kennung
        writeUShort(packet, 6, 0) // keine Fragmentierung
        packet[8] = DEFAULT_TTL.toByte()
        packet[OFFSET_PROTOCOL] = PROTOCOL_UDP.toByte()

        // Antwort: Ziel und Quelle vertauscht.
        request.destinationAddress.copyInto(packet, OFFSET_SOURCE_IP)
        request.sourceAddress.copyInto(packet, OFFSET_DEST_IP)

        writeUShort(packet, OFFSET_CHECKSUM, 0)
        writeUShort(packet, OFFSET_CHECKSUM, checksum(packet, 0, IP_HEADER_SIZE))

        val udpStart = IP_HEADER_SIZE
        writeUShort(packet, udpStart, request.destinationPort)
        writeUShort(packet, udpStart + 2, request.sourcePort)
        writeUShort(packet, udpStart + 4, UDP_HEADER_SIZE + payload.size)
        writeUShort(packet, udpStart + 6, 0)

        payload.copyInto(packet, udpStart + UDP_HEADER_SIZE)
        return packet
    }

    /** Einerkomplement-Pruefsumme nach RFC 1071. */
    private fun checksum(buffer: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var index = offset
        val end = offset + length

        while (index + 1 < end) {
            sum += readUShort(buffer, index).toLong()
            index += 2
        }
        if (index < end) sum += ((buffer[index].toInt() and 0xFF) shl 8).toLong()

        // Ueberlaeufe zurueck in die unteren 16 Bit falten.
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun readUShort(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

    private fun writeUShort(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }
}
