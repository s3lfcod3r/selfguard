package com.selfguard.app.ioc

import android.content.Context
import com.selfguard.app.net.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Holt die Stalkerware-Indikatoren frisch von Echap und schreibt sie in das
 * Abschnittsformat, das [IocStore] liest.
 *
 * Warum umgeformt und nicht roh gespeichert: die Quelldateien sind CSV mit
 * Feldern, die wir nicht brauchen. Die Umformung beim Herunterladen spart bei
 * jedem Scan das Zerlegen von 3.000 Zeilen.
 *
 * Datenquelle: AssoEchap/stalkerware-indicators, Lizenz CC-BY 4.0.
 */
object IocUpdater {

    private const val BASE = "https://raw.githubusercontent.com/AssoEchap/stalkerware-indicators/master"
    private const val SAMPLES_URL = "$BASE/samples.csv"
    private const val NETWORK_URL = "$BASE/generated/network.csv"

    private const val SHA256_LENGTH = 64
    private const val SHA1_LENGTH = 40

    /** Spalte "App" in samples.csv -- der Anbietername. */
    private const val VENDOR_COLUMN = 4

    /**
     * Ab so vielen unabhaengigen Anbietern gilt ein Zertifikat als geteilt
     * und taugt nicht mehr als Indikator. Zwei koennen eine Umbenennung
     * desselben Produkts sein, drei sind es nicht mehr.
     */
    private const val SHARED_CERT_VENDOR_THRESHOLD = 3

    /**
     * Laedt beide Quelldateien und ersetzt die zwischengespeicherte Fassung.
     *
     * Geschrieben wird erst nach vollstaendigem Download in eine temporaere
     * Datei, die dann umbenannt wird -- ein Abbruch mitten im Download darf
     * keine halbe Indikatorenliste hinterlassen, mit der die Erkennung still
     * schlechter wird.
     */
    suspend fun refresh(context: Context): Result<IocSet> = withContext(Dispatchers.IO) {
        runCatching {
            val samples = Downloads.text(SAMPLES_URL)
            val network = Downloads.text(NETWORK_URL)
            val rendered = render(samples, network)

            val target = IocStore.cacheFile(context)
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(rendered)
            if (!temp.renameTo(target)) {
                target.writeText(rendered)
                temp.delete()
            }

            IocStore.invalidate()
            IocStore.load(context)
        }
    }

    /**
     * Formt die beiden CSV-Dateien in das Abschnittsformat um.
     *
     * Dabei fliegen Zertifikate raus, die **drei oder mehr unabhaengige
     * Anbieter** teilen. Solche Fingerabdruecke gehoeren zu offenen
     * Schluesseln aus dem Android-Quelltext und sagen ueber eine App nichts
     * aus: Der AOSP-Testschluessel etwa taucht bei neun Ueberwachungs-
     * produkten auf -- und ebenso bei echten Systemkomponenten. Wer darauf
     * abgleicht, meldet ein normales Telefon als verseucht.
     */
    fun render(samplesCsv: String, networkCsv: String): String {
        val packages = sortedSetOf<String>()
        val certs = sortedSetOf<String>()
        val samples = sortedSetOf<String>()
        val domains = sortedSetOf<String>()
        val vendorsPerCert = mutableMapOf<String, MutableSet<String>>()

        // samples.csv: SHA256,Package Name,Certificate,Version,App
        samplesCsv.lineSequence().drop(1).forEach { line ->
            val cols = line.split(",")
            if (cols.size < 3) return@forEach
            cols[0].trim().lowercase().takeIf { it.length == SHA256_LENGTH }?.let { samples += it }
            cols[1].trim().lowercase().takeIf { it.isNotEmpty() }?.let { packages += it }

            val cert = cols[2].trim().lowercase().takeIf { it.length == SHA1_LENGTH }
            if (cert != null) {
                certs += cert
                val vendor = cols.getOrNull(VENDOR_COLUMN)?.trim().orEmpty()
                if (vendor.isNotEmpty()) {
                    vendorsPerCert.getOrPut(cert) { mutableSetOf() } += vendor
                }
            }
        }

        certs.removeAll { cert ->
            val vendors = vendorsPerCert[cert]?.size ?: 0
            vendors >= SHARED_CERT_VENDOR_THRESHOLD
        }

        // network.csv: type,indicator,app
        networkCsv.lineSequence().drop(1).forEach { line ->
            val cols = line.split(",")
            if (cols.size < 2) return@forEach
            if (cols[0].trim() != "domain") return@forEach
            cols[1].trim().lowercase().takeIf { it.contains('.') }?.let { domains += it }
        }

        return buildString {
            appendLine("# SelfGuard IOC-Buendel -- Stalkerware-Indikatoren")
            appendLine("# Quelle: AssoEchap/stalkerware-indicators, Lizenz CC-BY 4.0")
            appendLine("# https://github.com/AssoEchap/stalkerware-indicators")
            appendLine("# aktualisiert am ${java.time.LocalDate.now()}")
            appendLine("[packages]")
            packages.forEach { appendLine(it) }
            appendLine("[certs-sha1]")
            certs.forEach { appendLine(it) }
            appendLine("[samples-sha256]")
            samples.forEach { appendLine(it) }
            appendLine("[domains]")
            domains.forEach { appendLine(it) }
        }
    }
}
