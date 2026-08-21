package com.selfguard.app.scan.files

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.selfguard.app.core.Finding
import com.selfguard.app.core.FindingCode
import com.selfguard.app.core.Hashing
import com.selfguard.app.core.Severity
import com.selfguard.app.ioc.IocStore
import com.selfguard.app.net.ReputationClient
import com.selfguard.app.net.ReputationVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Fortschritt eines Dateidurchlaufs. */
data class FileScanProgress(
    val scanned: Int,
    val currentName: String,
)

/** Ergebnis eines Dateidurchlaufs. */
data class FileScanResult(
    val findings: List<Finding>,
    val filesScanned: Int,
    val bytesScanned: Long,
    val skipped: Int,
)

/**
 * Prueft Dateien in einem vom Nutzer gewaehlten Ordner.
 *
 * Bewusste Entscheidung: Es wird **kein** `MANAGE_EXTERNAL_STORAGE`
 * angefordert. Diese Berechtigung gibt Zugriff auf den gesamten Speicher,
 * loest zusaetzliche Play-Protect-Warnungen aus und waere fuer den Zweck
 * unverhaeltnismaessig. Stattdessen waehlt der Nutzer den Ordner selbst aus --
 * fuer den Regelfall "meine Downloads pruefen" reicht das vollstaendig, und
 * die App bekommt nie mehr Zugriff als noetig.
 *
 * Erkannt wird ueber Pruefsummen, nicht ueber Verhalten: eine Datei, deren
 * SHA-256 in einer Schadsoftware-Datenbank steht, ist ein belegter Treffer.
 * Unbekannte Schadsoftware findet dieser Weg nicht -- das ist die ehrliche
 * Grenze eines Pruefsummenabgleichs und steht so auch in den Befundtexten.
 */
object FileScanner {

    /** Groesse, ab der eine Datei uebersprungen wird (Video, Abbild, Archiv). */
    private const val MAX_FILE_SIZE = 256L * 1024 * 1024

    /** Wie tief in Unterordner gestiegen wird. */
    private const val MAX_DEPTH = 8

    suspend fun scan(
        context: Context,
        treeUri: Uri,
        useReputation: Boolean,
        onProgress: (FileScanProgress) -> Unit = {},
    ): FileScanResult = withContext(Dispatchers.IO) {
        val knownBad = IocStore.load(context).sampleSha256
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext FileScanResult(emptyList(), 0, 0L, 0)

        val state = ScanState()
        walk(context, root, depth = 0, state = state, knownBad = knownBad,
            useReputation = useReputation, onProgress = onProgress)

        FileScanResult(
            findings = state.findings.toList(),
            filesScanned = state.scanned,
            bytesScanned = state.bytes,
            skipped = state.skipped,
        )
    }

    private class ScanState {
        val findings = mutableListOf<Finding>()
        var scanned = 0
        var bytes = 0L
        var skipped = 0
    }

    private suspend fun walk(
        context: Context,
        directory: DocumentFile,
        depth: Int,
        state: ScanState,
        knownBad: Set<String>,
        useReputation: Boolean,
        onProgress: (FileScanProgress) -> Unit,
    ) {
        if (depth > MAX_DEPTH) return

        for (entry in directory.listFiles()) {
            if (!currentCoroutineContext().isActive) return

            if (entry.isDirectory) {
                walk(context, entry, depth + 1, state, knownBad, useReputation, onProgress)
                continue
            }

            val name = entry.name ?: continue
            if (entry.length() > MAX_FILE_SIZE) {
                state.skipped++
                continue
            }

            onProgress(FileScanProgress(state.scanned, name))
            inspect(context, entry, name, state, knownBad, useReputation)
        }
    }

    private suspend fun inspect(
        context: Context,
        file: DocumentFile,
        name: String,
        state: ScanState,
        knownBad: Set<String>,
        useReputation: Boolean,
    ) {
        val hash = runCatching {
            context.contentResolver.openInputStream(file.uri)?.use { Hashing.sha256(it) }
        }.getOrNull()

        if (hash == null) {
            state.skipped++
            return
        }

        state.scanned++
        state.bytes += file.length()

        if (hash in knownBad) {
            state.findings += finding(
                name = name,
                uri = file.uri.toString(),
                hash = hash,
                source = "Stalkerware-Datensatz (Echap)",
            )
            return
        }

        if (!useReputation) return
        val verdict = ReputationClient.lookup(context, hash)
        if (verdict is ReputationVerdict.Bekannt) {
            state.findings += finding(
                name = name,
                uri = file.uri.toString(),
                hash = hash,
                source = verdict.describe(),
            )
        }
    }

    private fun finding(name: String, uri: String, hash: String, source: String) = Finding(
        code = FindingCode.FILE_KNOWN_MALWARE,
        severity = Severity.CRITICAL,
        subject = uri,
        subjectLabel = name,
        evidence = listOf("SHA-256: $hash", "Quelle: $source"),
    )
}
