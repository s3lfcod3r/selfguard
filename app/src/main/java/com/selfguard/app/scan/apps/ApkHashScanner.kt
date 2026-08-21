package com.selfguard.app.scan.apps

import android.content.Context
import com.selfguard.app.core.Finding
import com.selfguard.app.core.FindingCode
import com.selfguard.app.core.Hashing
import com.selfguard.app.core.Severity
import java.io.File

/**
 * Prueft die installierten APK-Dateien selbst gegen bekannte Schadsoftware.
 *
 * Das ist der Teil, der einem Virenscanner am naechsten kommt: Es wird nicht
 * geraten und nicht heuristisch bewertet, sondern die Datei auf dem Geraet
 * gehasht und die Pruefsumme gegen eine Schadsoftware-Datenbank gehalten.
 * Ein Treffer ist eindeutig.
 *
 * **Der Zwischenspeicher ist hier nicht Beiwerk, sondern Voraussetzung.**
 * Ein Telefon traegt schnell 150 Apps mit zusammen mehreren Gigabyte. Diese
 * bei jedem Durchlauf neu zu lesen dauert Minuten und kostet spuerbar Akku --
 * die Pruefung wuerde einfach nicht mehr benutzt. Eine APK aendert sich nur
 * beim Update, also wird das Ergebnis anhand von Aenderungszeit und Groesse
 * behalten. Der erste Durchlauf ist langsam, jeder weitere dauert Sekunden.
 */
object ApkHashScanner {

    private const val CACHE_FILE = "apk-hashes.txt"
    private const val FIELD_SEPARATOR = "|"
    private const val EXPECTED_FIELDS = 4

    /** Ein gemerktes Ergebnis. Passt eines der Merkmale nicht, wird neu gelesen. */
    private data class CacheEntry(
        val lastUpdateTime: Long,
        val size: Long,
        val sha256: String,
    )

    /**
     * @param onProgress meldet (fertig, gesamt, aktueller Name)
     * @param isActive wird vor jeder Datei geprueft; false bricht ab
     * @param onlineLookup optionale Nachfrage beim Reputationsdienst, gibt die
     *   Quelle des Treffers zurueck oder null
     */
    suspend fun scan(
        context: Context,
        apps: List<InstalledApp>,
        knownBad: Set<String>,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> },
        isActive: () -> Boolean = { true },
        onlineLookup: (suspend (String) -> String?)? = null,
    ): List<Finding> {
        val cache = readCache(context).toMutableMap()
        val findings = mutableListOf<Finding>()
        var cacheChanged = false

        apps.forEachIndexed { index, app ->
            if (!isActive()) return@forEachIndexed
            onProgress(index, apps.size, app.label)

            val file = File(app.apkPath)
            if (!file.exists() || !file.canRead()) return@forEachIndexed

            val cached = cache[app.packageName]
            val stillValid = cached != null &&
                cached.lastUpdateTime == app.lastUpdateTime &&
                cached.size == file.length()

            val hash = if (stillValid) {
                cached.sha256
            } else {
                val computed = Hashing.sha256(file) { isActive() } ?: return@forEachIndexed
                cache[app.packageName] = CacheEntry(app.lastUpdateTime, file.length(), computed)
                cacheChanged = true
                computed
            }

            if (hash in knownBad) {
                findings += finding(app, hash, "Stalkerware-Datensatz (Echap)")
                return@forEachIndexed
            }

            val onlineSource = onlineLookup?.invoke(hash)
            if (onlineSource != null) findings += finding(app, hash, onlineSource)
        }

        if (cacheChanged) writeCache(context, cache)
        return findings.toList()
    }

    private fun finding(app: InstalledApp, hash: String, source: String) = Finding(
        code = FindingCode.APP_KNOWN_MALWARE,
        severity = Severity.CRITICAL,
        subject = app.packageName,
        subjectLabel = app.label,
        evidence = listOf("SHA-256: ${hash.take(24)}...", "Quelle: $source"),
    )

    private fun cacheFile(context: Context) = File(context.filesDir, CACHE_FILE)

    private fun readCache(context: Context): Map<String, CacheEntry> {
        val file = cacheFile(context)
        if (!file.exists()) return emptyMap()

        return runCatching {
            file.readLines().mapNotNull { line ->
                val parts = line.split(FIELD_SEPARATOR)
                if (parts.size != EXPECTED_FIELDS) return@mapNotNull null
                val time = parts[1].toLongOrNull() ?: return@mapNotNull null
                val size = parts[2].toLongOrNull() ?: return@mapNotNull null
                parts[0] to CacheEntry(time, size, parts[3])
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun writeCache(context: Context, cache: Map<String, CacheEntry>) {
        runCatching {
            cacheFile(context).writeText(
                cache.entries.joinToString("\n") { (pkg, entry) ->
                    listOf(pkg, entry.lastUpdateTime, entry.size, entry.sha256)
                        .joinToString(FIELD_SEPARATOR)
                }
            )
        }
    }
}
