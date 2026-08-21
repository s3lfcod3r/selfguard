package com.selfguard.app.ioc

import android.content.Context
import com.selfguard.app.scan.apps.DetectorContext
import java.io.BufferedReader
import java.io.File

/**
 * Die geladenen Indikatoren.
 *
 * Alles klein geschrieben -- der Abgleich normalisiert beide Seiten, damit
 * Gross-/Kleinschreibung keine Treffer verschluckt.
 */
data class IocSet(
    val packages: Set<String> = emptySet(),
    val certSha1: Set<String> = emptySet(),
    val sampleSha256: Set<String> = emptySet(),
    val domains: Set<String> = emptySet(),
    /** Kopfzeilen der Quelldatei -- Herkunft und Erzeugungsdatum. */
    val header: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = packages.isEmpty() && certSha1.isEmpty()

    val totalIndicators: Int
        get() = packages.size + certSha1.size + sampleSha256.size + domains.size

    companion object {
        val EMPTY = IocSet()
    }
}

/**
 * Laedt die Stalkerware-Indikatoren von Echap.
 *
 * Zwei Quellen in fester Rangfolge:
 *  1. eine aktualisierte Fassung im internen Speicher, falls vorhanden
 *  2. das mitgelieferte Buendel aus den Assets
 *
 * Dadurch ist die Erkennung ab der ersten Sekunde ohne Netzverbindung
 * einsatzbereit und wird trotzdem aktuell gehalten. Die Aktualisierung
 * uebernimmt [IocUpdater].
 *
 * Datenquelle: AssoEchap/stalkerware-indicators, Lizenz CC-BY 4.0. Die
 * Namensnennung steht in den App-Einstellungen.
 */
object IocStore {

    const val ASSET_PATH = "ioc/stalkerware.txt"
    private const val CACHE_DIR = "ioc"
    private const val CACHE_FILE = "stalkerware.txt"

    private const val SECTION_PACKAGES = "[packages]"
    private const val SECTION_CERTS = "[certs-sha1]"
    private const val SECTION_SAMPLES = "[samples-sha256]"
    private const val SECTION_DOMAINS = "[domains]"

    @Volatile
    private var cached: IocSet? = null

    /** Datei, in die [IocUpdater] eine frischere Fassung schreibt. */
    fun cacheFile(context: Context): File =
        File(File(context.filesDir, CACHE_DIR).apply { mkdirs() }, CACHE_FILE)

    /** Einmal geladen, danach im Speicher gehalten. */
    fun load(context: Context): IocSet {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val loaded = readUpdated(context) ?: readBundled(context) ?: IocSet.EMPTY
            cached = loaded
            return loaded
        }
    }

    /** Nach einer Aktualisierung aufrufen, damit der naechste Scan neu liest. */
    fun invalidate() {
        cached = null
    }

    /** Fertig verpackt fuer die App-Detektoren. */
    fun detectorContext(context: Context): DetectorContext {
        val iocs = load(context)
        return DetectorContext(
            stalkerwarePackages = iocs.packages,
            stalkerwareCertSha1 = iocs.certSha1,
        )
    }

    private fun readUpdated(context: Context): IocSet? {
        val file = cacheFile(context)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching { file.bufferedReader().use { parse(it) } }.getOrNull()
    }

    private fun readBundled(context: Context): IocSet? = runCatching {
        context.assets.open(ASSET_PATH).bufferedReader().use { parse(it) }
    }.getOrNull()

    /**
     * Liest das Abschnittsformat:
     *
     * ```
     * # Kommentar
     * [packages]
     * com.example.spy
     * [certs-sha1]
     * 7f5c0d54...
     * ```
     */
    fun parse(reader: BufferedReader): IocSet {
        val packages = mutableSetOf<String>()
        val certs = mutableSetOf<String>()
        val samples = mutableSetOf<String>()
        val domains = mutableSetOf<String>()
        val header = mutableListOf<String>()
        var current: MutableSet<String>? = null

        reader.forEachLine { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#") -> header += line.removePrefix("#").trim()
                line == SECTION_PACKAGES -> current = packages
                line == SECTION_CERTS -> current = certs
                line == SECTION_SAMPLES -> current = samples
                line == SECTION_DOMAINS -> current = domains
                // Unbekannte Abschnitte werden uebersprungen, damit ein
                // spaeter erweitertes Format aeltere Fassungen nicht bricht.
                line.startsWith("[") -> current = null
                else -> current?.add(line.lowercase())
            }
        }

        return IocSet(
            packages = packages,
            certSha1 = certs,
            sampleSha256 = samples,
            domains = domains,
            header = header.toList(),
        )
    }
}
