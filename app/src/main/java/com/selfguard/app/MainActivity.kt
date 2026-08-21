package com.selfguard.app

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.selfguard.app.core.Capability
import com.selfguard.app.data.Prefs
import com.selfguard.app.ioc.IocStore
import com.selfguard.app.ioc.IocUpdater
import com.selfguard.app.net.BlocklistTier
import com.selfguard.app.net.BlocklistUpdater
import com.selfguard.app.net.DnsFilterService
import com.selfguard.app.net.PrivateDns
import com.selfguard.app.net.PrivateDnsStatus
import com.selfguard.app.net.VpnStatus
import com.selfguard.app.scan.ScanEngine
import com.selfguard.app.scan.apps.AppAction
import com.selfguard.app.scan.apps.AppActions
import com.selfguard.app.scan.apps.AppInventory
import com.selfguard.app.scan.apps.PermissionActions
import com.selfguard.app.scan.files.FileScanner
import com.selfguard.app.ui.AppRow
import com.selfguard.app.ui.AppScreen
import com.selfguard.app.ui.FileScanState
import com.selfguard.app.ui.ListUpdateState
import com.selfguard.app.ui.theme.SelfGuardTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {

    private var scanJob: Job? = null

    private val tier = mutableStateOf(BlocklistTier.STANDARD)
    private val blockedDomains = mutableStateOf(0)
    private val updateState = mutableStateOf<ListUpdateState>(ListUpdateState.Ruht)
    private val iocSummary = mutableStateOf(emptyList<String>())
    private val foreignVpn = mutableStateOf(false)
    private val privateDns = mutableStateOf<PrivateDns>(PrivateDns.Aus)
    private val fileScanState = mutableStateOf<FileScanState>(FileScanState.Bereit)
    private val reputationEnabled = mutableStateOf(false)
    private val reputationEndpoint = mutableStateOf("")

    /** Wird gesetzt, solange der Schalter noch Sperrlisten nachlädt. */
    private val filterBusy = mutableStateOf(false)

    private val appRows = mutableStateOf(emptyList<AppRow>())
    private val appsLoading = mutableStateOf(false)
    private val appActions = mutableStateOf(emptyMap<String, AppAction>())
    private val knownPackages = mutableStateOf(emptySet<String>())

    /**
     * Noch abzuarbeitende Deinstallationen.
     *
     * Android zeigt für jede App einen eigenen Bestätigungsdialog, und
     * es kann immer nur einer offen sein. Deshalb wird die Auswahl
     * abgearbeitet wie eine Schlange: nächster Dialog erst, wenn der
     * vorige geschlossen ist.
     */
    private val pendingUninstalls = ArrayDeque<String>()

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) startFileScan(uri) }

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) DnsFilterService.start(this)
    }

    /**
     * Ergebnis eines Deinstall-Dialogs. Egal ob bestätigt oder abgebrochen:
     * danach ist der nächste Eintrag der Schlange dran.
     */
    private val uninstallLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { proceedUninstallQueue() }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Der Filter läuft auch ohne sichtbare Benachrichtigung weiter. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        tier.value = runCatching { BlocklistTier.valueOf(Prefs.blocklistTier(this)) }
            .getOrDefault(BlocklistTier.STANDARD)
        blockedDomains.value = Prefs.blockedDomains(this)
        reputationEnabled.value = Prefs.reputationLookupEnabled(this)
        reputationEndpoint.value = Prefs.reputationEndpoint(this)
        refreshIocSummary()

        setContent {
            SelfGuardTheme {
                val scanState by ScanEngine.state.collectAsState()
                val filterRunning by DnsFilterService.running.collectAsState()
                val filterStats by DnsFilterService.stats.collectAsState()

                AppScreen(
                    scanState = scanState,
                    lang = Prefs.language(this),
                    lastScanText = lastScanText(),
                    filterRunning = filterRunning,
                    filterBusy = filterBusy.value,
                    blockedToday = filterStats.blocked,
                    foreignVpnActive = foreignVpn.value,
                    privateDns = privateDns.value,
                    tier = tier.value,
                    blockedDomains = blockedDomains.value,
                    updateState = updateState.value,
                    iocSummary = iocSummary.value,
                    fileScanState = fileScanState.value,
                    reputationEnabled = reputationEnabled.value,
                    reputationEndpoint = reputationEndpoint.value,
                    onScan = ::startScan,
                    onToggleFilter = ::toggleFilter,
                    onTierChange = ::changeTier,
                    onUpdateLists = { lifecycleScope.launch { updateBlocklists() } },
                    onUpdateIocs = ::updateIndicators,
                    onPickFolder = { folderPicker.launch(null) },
                    onToggleReputation = ::toggleReputation,
                    appRows = appRows.value,
                    appsLoading = appsLoading.value,
                    appActions = appActions.value,
                    knownPackages = knownPackages.value,
                    onEndpointChange = ::saveEndpoint,
                    onLoadApps = ::loadApps,
                    onUninstall = ::startUninstall,
                    onOpenDetails = ::openAppDetails,
                    onOpenNetworkSettings = ::openNetworkSettings,
                    onOpenCapability = ::openCapability,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        foreignVpn.value = VpnStatus.foreignVpnActive(this)
        // Bei jeder Rueckkehr neu lesen -- der Nutzer koennte die Einstellung
        // gerade in den Systemeinstellungen geaendert haben.
        privateDns.value = PrivateDnsStatus.read(this)
    }

    private fun lastScanText(): String {
        val last = Prefs.lastScanAt(this)
        if (last == 0L) return "Tippen Sie auf Handy prüfen"
        val formatted = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(last))
        return "Zuletzt geprüft: $formatted"
    }

    private fun startScan() {
        scanJob?.cancel()
        scanJob = lifecycleScope.launch { ScanEngine.scan(this@MainActivity) }
    }

    /**
     * Ein Schalter, der sich um alles kümmert.
     *
     * Vorher musste man erst in den Einstellungen die Sperrlisten laden und
     * dann hier einschalten -- wer das nicht wusste, hatte einen Filter, der
     * nichts blockte und das auch nicht sagte. Jetzt lädt der Schalter beim
     * ersten Einschalten selbst nach und startet danach.
     */
    private fun toggleFilter(enabled: Boolean) {
        if (!enabled) {
            DnsFilterService.stop(this)
            return
        }

        lifecycleScope.launch {
            if (blockedDomains.value == 0) {
                filterBusy.value = true
                updateBlocklists()
                filterBusy.value = false
                // Ohne Liste blockt der Filter nichts -- dann lieber gar nicht
                // erst starten und den Fehler in den Einstellungen zeigen.
                if (blockedDomains.value == 0) return@launch
            }

            val intent = VpnService.prepare(this@MainActivity)
            if (intent == null) {
                DnsFilterService.start(this@MainActivity)
            } else {
                vpnPermission.launch(intent)
            }
        }
    }

    private fun changeTier(selected: BlocklistTier) {
        tier.value = selected
        Prefs.setBlocklistTier(this, selected.name)
    }

    private suspend fun updateBlocklists() {
        updateState.value = ListUpdateState.Läuft("Sperrlisten")
        val result = BlocklistUpdater.refresh(this, tier.value) { source ->
            updateState.value = ListUpdateState.Läuft(source)
        }
        result.fold(
            onSuccess = {
                blockedDomains.value = it.domains
                Prefs.setBlockedDomains(this, it.domains)
                updateState.value = ListUpdateState.Fertig(it.domains, it.sources)
            },
            onFailure = {
                updateState.value = ListUpdateState.Fehler(it.message ?: "unbekannt")
            },
        )
    }

    private fun toggleReputation(enabled: Boolean) {
        reputationEnabled.value = enabled
        Prefs.setReputationLookupEnabled(this, enabled)
    }

    private fun saveEndpoint(value: String) {
        Prefs.setReputationEndpoint(this, value)
        reputationEndpoint.value = Prefs.reputationEndpoint(this)
    }

    private fun startFileScan(treeUri: Uri) {
        lifecycleScope.launch {
            fileScanState.value = FileScanState.Läuft(0, "")
            val result = FileScanner.scan(
                context = this@MainActivity,
                treeUri = treeUri,
                useReputation = reputationEnabled.value,
                onProgress = { progress ->
                    fileScanState.value =
                        FileScanState.Läuft(progress.scanned, progress.currentName)
                },
            )
            fileScanState.value = FileScanState.Fertig(result)
        }
    }

    private fun updateIndicators() {
        lifecycleScope.launch {
            IocUpdater.refresh(this@MainActivity)
            refreshIocSummary()
        }
    }

    // -- App-Liste und Deinstallation ------------------------------------

    /**
     * Liest das App-Verzeichnis für die Listenansicht.
     *
     * Wenn gerade ein Scan gelaufen ist, wird dessen Ergebnis
     * wiederverwendet -- der PackageManager ist langsam, und zweimal
     * dasselbe zu lesen wäre nur Wartezeit.
     */
    private fun loadApps() {
        val cached = ScanEngine.lastInventory
        if (cached.isNotEmpty()) {
            publishApps(cached)
            return
        }

        appsLoading.value = true
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                AppInventory.readAll(this@MainActivity)
            }
            publishApps(apps)
            appsLoading.value = false
        }
    }

    private fun publishApps(apps: List<com.selfguard.app.scan.apps.InstalledApp>) {
        val bands = (ScanEngine.state.value as? com.selfguard.app.scan.ScanState.Fertig)
            ?.report?.subjects?.associate { it.subject to it.band }
            .orEmpty()

        appRows.value = apps.map { AppRow(it, bands[it.packageName]) }
        appActions.value = apps.associate { it.packageName to AppActions.actionFor(it) }
        knownPackages.value = apps.map { it.packageName }.toSet()
    }

    /** Startet die Abarbeitung der ausgewählten Deinstallationen. */
    private fun startUninstall(packages: List<String>) {
        pendingUninstalls.addAll(packages)
        proceedUninstallQueue()
    }

    /**
     * Öffnet den nächsten Dialog. Ist die Schlange leer, wird die Liste
     * neu gelesen -- entfernte Apps sollen sofort verschwinden.
     */
    private fun proceedUninstallQueue() {
        val next = pendingUninstalls.removeFirstOrNull()
        if (next == null) {
            refreshAppsAfterRemoval()
            return
        }

        // Je nach App ein anderes Ziel: Löschdialog bei selbst
        // installierten, Systemseite zum Deaktivieren bei
        // Systembestandteilen. Ist die App nicht mehr auffindbar --
        // etwa weil sie gerade entfernt wurde -- geht es einfach weiter.
        val app = appRows.value.firstOrNull { it.app.packageName == next }?.app
        val intent = app?.let { AppActions.intentFor(it) }
        if (intent == null) {
            proceedUninstallQueue()
            return
        }

        runCatching { uninstallLauncher.launch(intent) }
            .onFailure { proceedUninstallQueue() }
    }

    private fun refreshAppsAfterRemoval() {
        appsLoading.value = true
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                AppInventory.readAll(this@MainActivity)
            }
            publishApps(apps)
            appsLoading.value = false
        }
    }

    /**
     * Fuehrt in die Netzwerkeinstellungen, wo das private DNS sitzt.
     *
     * Direkter geht es nicht: Android kennt keine oeffentliche Aktion fuer
     * die Private-DNS-Seite, und der versteckte Aufruf heisst je nach
     * Hersteller anders. Lieber verlaesslich eine Ebene zu hoch landen als
     * mit einem Absturz auf einem fremden Geraet.
     */
    private fun openNetworkSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun openAppDetails(packageName: String) {
        runCatching { startActivity(AppActions.detailsIntent(packageName)) }
    }

    /**
     * Bringt den Nutzer auf die Seite, auf der sich die angetippte Fähigkeit
     * abstellen lässt.
     *
     * Abgestellt wird dort von Hand. SelfGuard kann fremde Berechtigungen
     * nicht selbst entziehen -- Android lässt das keiner App zu, und das aus
     * gutem Grund (siehe [PermissionActions]).
     */
    private fun openCapability(packageName: String, capability: Capability) {
        runCatching {
            startActivity(PermissionActions.intentFor(this, capability, packageName))
        }
    }

    private fun refreshIocSummary() {
        val iocs = IocStore.load(this)
        iocSummary.value = listOf(
            "${iocs.packages.size} Pakete, ${iocs.certSha1.size} Zertifikate, " +
                "${iocs.sampleSha256.size} Datei-Prüfsummen, ${iocs.domains.size} Domains"
        )
    }
}
