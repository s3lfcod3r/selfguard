package com.selfguard.app.scan

import android.content.Context
import com.selfguard.app.core.Finding
import com.selfguard.app.core.ScanReport
import com.selfguard.app.data.Prefs
import com.selfguard.app.ioc.IocStore
import com.selfguard.app.net.ReputationClient
import com.selfguard.app.net.ReputationVerdict
import com.selfguard.app.scan.apps.ApkHashScanner
import com.selfguard.app.scan.apps.AppDetectors
import com.selfguard.app.scan.apps.AppInventory
import com.selfguard.app.scan.apps.InstalledApp
import com.selfguard.app.scan.system.CertificateCheck
import com.selfguard.app.scan.system.DeviceStateChecks
import com.selfguard.app.scan.system.PrivilegedServiceChecks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Wo der Durchlauf gerade steht. */
sealed interface ScanState {
    data object Bereit : ScanState

    data class Laeuft(
        val step: ScanStep,
        val done: Int,
        val total: Int,
        val detail: String = "",
    ) : ScanState

    data class Fertig(val report: ScanReport) : ScanState
}

/**
 * Abschnitte eines Durchlaufs.
 *
 * Die Beschriftung steht hier und nicht in der Oberflaeche, damit ueberall
 * dasselbe steht -- im Fortschritt wie in der Abdeckungsliste am Ende.
 */
enum class ScanStep(val label: String) {
    APPS_LESEN("Apps werden erfasst"),
    SCHADSOFTWARE("Programmdateien werden gegen Schadsoftware geprueft"),
    VERHALTEN("Apps werden auf Spionage-Verhalten geprueft"),
    SYSTEM("Geraet wird auf offene Tueren geprueft"),
}

/**
 * Fuehrt alle Pruefungen zusammen.
 *
 * Reihenfolge ist bewusst gewaehlt: erst die eindeutigen Treffer
 * (Pruefsummen), dann die Verhaltensmerkmale, zuletzt der Geraetezustand.
 * So steht das Belastbarste zuerst fest.
 */
object ScanEngine {

    /**
     * Das zuletzt gelesene App-Verzeichnis.
     *
     * Wird aufgehoben, damit die Oberflaeche danach weiss, welche Befunde zu
     * entfernbaren Apps gehoeren -- ohne den PackageManager ein zweites Mal
     * abzufragen.
     */
    @Volatile
    var lastInventory: List<InstalledApp> = emptyList()
        private set

    private val _state = MutableStateFlow<ScanState>(ScanState.Bereit)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /**
     * Laeuft vollstaendig auf dem IO-Dispatcher -- Signaturzertifikate lesen
     * und APK-Dateien hashen sind teuer und duerfen die Oberflaeche nicht
     * blockieren.
     */
    suspend fun scan(context: Context): ScanReport = withContext(Dispatchers.IO) {
        val scope = this
        val iocs = IocStore.load(context)
        val detectorContext = IocStore.detectorContext(context)
        val findings = mutableListOf<Finding>()

        // 1. Bestandsaufnahme
        _state.value = ScanState.Laeuft(ScanStep.APPS_LESEN, 0, 0)
        val apps = AppInventory.readAll(
            context = context,
            onProgress = { done, total ->
                _state.value = ScanState.Laeuft(ScanStep.APPS_LESEN, done, total)
            },
            isActive = { scope.isActive },
        )
        lastInventory = apps

        // 2. Pruefsummen der Programmdateien -- die eindeutigen Treffer
        findings += ApkHashScanner.scan(
            context = context,
            apps = apps,
            knownBad = iocs.sampleSha256,
            onProgress = { done, total, name ->
                _state.value = ScanState.Laeuft(ScanStep.SCHADSOFTWARE, done, total, name)
            },
            isActive = { scope.isActive },
            onlineLookup = onlineLookupOrNull(context),
        )

        // 3. Verhaltensmerkmale
        apps.forEachIndexed { index, app ->
            _state.value = ScanState.Laeuft(ScanStep.VERHALTEN, index, apps.size, app.label)
            findings += AppDetectors.inspect(app, detectorContext)
        }

        // 4. Geraetezustand
        _state.value = ScanState.Laeuft(ScanStep.SYSTEM, 0, 3)
        findings += CertificateCheck.run()
        _state.value = ScanState.Laeuft(ScanStep.SYSTEM, 1, 3)
        findings += PrivilegedServiceChecks.run(context, apps)
        _state.value = ScanState.Laeuft(ScanStep.SYSTEM, 2, 3)
        findings += DeviceStateChecks.run(context)

        val finishedAt = System.currentTimeMillis()
        Prefs.setLastScanAt(context, finishedAt)

        val report = ScanReport.from(findings, apps.size, finishedAt)
        _state.value = ScanState.Fertig(report)
        report
    }

    /**
     * Die Online-Nachfrage wird nur durchgereicht, wenn der Nutzer sie
     * eingeschaltet **und** eine Adresse hinterlegt hat. Sonst bleibt der
     * Durchlauf vollstaendig oertlich.
     */
    private fun onlineLookupOrNull(context: Context): (suspend (String) -> String?)? {
        if (!Prefs.reputationLookupEnabled(context)) return null
        if (Prefs.reputationEndpoint(context).isBlank()) return null

        return { hash ->
            when (val verdict = ReputationClient.lookup(context, hash)) {
                is ReputationVerdict.Bekannt -> verdict.describe()
                else -> null
            }
        }
    }

    /** Zuruecksetzen, damit ein neuer Durchlauf sauber startet. */
    fun reset() {
        _state.value = ScanState.Bereit
    }
}
