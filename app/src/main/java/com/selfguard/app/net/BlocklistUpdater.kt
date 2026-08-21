package com.selfguard.app.net

import android.content.Context
import com.selfguard.app.ioc.IocStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Was beim Aktualisieren herauskam. */
data class BlocklistUpdateResult(
    val domains: Int,
    val sources: List<String>,
)

/**
 * Lädt die gewählten Sperrlisten und baut daraus die Nachschlagedatei.
 *
 * Die Domains werden beim Lesen sofort zu Streuwerten verrechnet und nie
 * vollständig als Zeichenketten gehalten -- bei der strengen Stufe wären das
 * sonst zweistellige Megabyte allein für den Aufbau.
 */
object BlocklistUpdater {

    suspend fun refresh(
        context: Context,
        tier: BlocklistTier,
        onProgress: (String) -> Unit = {},
    ): Result<BlocklistUpdateResult> = withContext(Dispatchers.IO) {
        runCatching {
            val hashes = HashSet<Long>(INITIAL_CAPACITY)
            val sources = mutableListOf<String>()

            tier.urls.forEach { url ->
                onProgress(shortNameOf(url))
                var count = 0
                Downloads.forEachLine(url) { line ->
                    val domain = clean(line)
                    if (domain != null) {
                        hashes += Blocklist.hashOf(domain)
                        count++
                    }
                }
                sources += "${shortNameOf(url)} ($count)"
            }

            // Die C2-Adressen aus dem Stalkerware-Datensatz kommen immer dazu.
            // Sie sind der Grund, warum der Filter auch gegen Überwachung hilft
            // und nicht nur gegen Werbung.
            val stalkerwareDomains = IocStore.load(context).domains
            stalkerwareDomains.forEach { hashes += Blocklist.hashOf(it) }
            if (stalkerwareDomains.isNotEmpty()) {
                sources += "Stalkerware-C2 (${stalkerwareDomains.size})"
            }

            onProgress("Liste wird aufgebaut")
            val sorted = hashes.toLongArray()
            sorted.sort()
            BlocklistStore.save(context, sorted)

            BlocklistUpdateResult(domains = sorted.size, sources = sources.toList())
        }
    }

    /** Grob geschätzte Endgröße, damit die Menge nicht ständig wächst. */
    private const val INITIAL_CAPACITY = 300_000

    /**
     * Macht aus einer Listenzeile eine Domain -- oder null, wenn die Zeile
     * keine ist. Kommentare, Leerzeilen und die Wildcard-Schreibweise `*.`
     * kommen in den Quelllisten alle vor.
     */
    fun clean(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) return null

        val domain = trimmed.removePrefix("*.").lowercase().trimEnd('.')
        if (!domain.contains('.')) return null
        // Zeilen im hosts-Format ("0.0.0.0 domain.tld") kämen mit Leerzeichen.
        if (domain.any { it.isWhitespace() }) return null
        return domain
    }

    private fun shortNameOf(url: String): String =
        url.substringAfterLast('/').removeSuffix("-onlydomains.txt")
}
