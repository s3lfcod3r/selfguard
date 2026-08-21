package com.selfguard.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.selfguard.app.core.Capability
import com.selfguard.app.net.BlocklistTier
import com.selfguard.app.scan.ScanState
import com.selfguard.app.net.PrivateDns
import com.selfguard.app.scan.apps.AppAction

/** Die vier Ansichten. Kein Reiter-Menü -- man ist immer auf genau einer. */
private enum class Ansicht { START, APPS, DATEIEN, METHODEN, EINSTELLUNGEN }

/**
 * Ganz einfache Navigation.
 *
 * Früher lagen vier gleichrangige Reiter nebeneinander, in denen Scan,
 * Filter, Dateien und Quellen gleich wichtig aussahen. Jetzt gibt es einen
 * Startbildschirm, von dem drei Nebenansichten abgehen -- und die Zurücktaste
 * tut, was man erwartet.
 */
@Composable
fun AppScreen(
    scanState: ScanState,
    lang: String,
    lastScanText: String,
    filterRunning: Boolean,
    filterBusy: Boolean,
    blockedToday: Long,
    foreignVpnActive: Boolean,
    privateDns: PrivateDns,
    tier: BlocklistTier,
    blockedDomains: Int,
    updateState: ListUpdateState,
    iocSummary: List<String>,
    fileScanState: FileScanState,
    reputationEnabled: Boolean,
    reputationEndpoint: String,
    appRows: List<AppRow>,
    appsLoading: Boolean,
    appActions: Map<String, AppAction>,
    knownPackages: Set<String>,
    onScan: () -> Unit,
    onToggleFilter: (Boolean) -> Unit,
    onTierChange: (BlocklistTier) -> Unit,
    onUpdateLists: () -> Unit,
    onUpdateIocs: () -> Unit,
    onPickFolder: () -> Unit,
    onToggleReputation: (Boolean) -> Unit,
    onEndpointChange: (String) -> Unit,
    onLoadApps: () -> Unit,
    onUninstall: (List<String>) -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenNetworkSettings: () -> Unit,
    onOpenCapability: (String, Capability) -> Unit,
) {
    var ansicht by remember { mutableStateOf(Ansicht.START) }

    BackHandler(enabled = ansicht != Ansicht.START) { ansicht = Ansicht.START }

    when (ansicht) {
        Ansicht.START -> HomeScreen(
            scanState = scanState,
            lang = lang,
            lastScanText = lastScanText,
            filterRunning = filterRunning,
            filterBusy = filterBusy,
            blockedToday = blockedToday,
            foreignVpnActive = foreignVpnActive,
            privateDns = privateDns,
            onScan = onScan,
            onToggleFilter = onToggleFilter,
            appActions = appActions,
            knownPackages = knownPackages,
            onOpenFiles = { ansicht = Ansicht.DATEIEN },
            onOpenMethods = { ansicht = Ansicht.METHODEN },
            onOpenApps = {
                onLoadApps()
                ansicht = Ansicht.APPS
            },
            onAct = { onUninstall(listOf(it)) },
            onOpenDetails = onOpenDetails,
            onOpenCapability = onOpenCapability,
            onOpenSettings = { ansicht = Ansicht.EINSTELLUNGEN },
        )

        Ansicht.APPS -> AppsScreen(
            rows = appRows,
            loading = appsLoading,
            onBack = { ansicht = Ansicht.START },
            onUninstall = onUninstall,
            onOpenDetails = onOpenDetails,
        )

        Ansicht.METHODEN -> BlockingMethodsScreen(
            filterRunning = filterRunning,
            privateDns = privateDns,
            onBack = { ansicht = Ansicht.START },
            onOpenNetworkSettings = onOpenNetworkSettings,
        )

        Ansicht.DATEIEN -> FileScanScreen(
            state = fileScanState,
            lang = lang,
            onBack = { ansicht = Ansicht.START },
            onPickFolder = onPickFolder,
        )

        Ansicht.EINSTELLUNGEN -> SettingsScreen(
            tier = tier,
            blockedDomains = blockedDomains,
            updateState = updateState,
            iocSummary = iocSummary,
            reputationEnabled = reputationEnabled,
            reputationEndpoint = reputationEndpoint,
            onBack = { ansicht = Ansicht.START },
            onTierChange = onTierChange,
            onUpdateLists = onUpdateLists,
            onUpdateIocs = onUpdateIocs,
            onToggleReputation = onToggleReputation,
            onEndpointChange = onEndpointChange,
        )
    }
}
