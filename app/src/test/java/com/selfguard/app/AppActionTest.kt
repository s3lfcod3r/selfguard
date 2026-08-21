package com.selfguard.app

import com.selfguard.app.scan.apps.AppAction
import com.selfguard.app.scan.apps.AppActions
import com.selfguard.app.scan.apps.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueft, dass jede App den richtigen Weg bekommt.
 *
 * Der Punkt dahinter: Ein Knopf, der etwas anderes tut als er verspricht, ist
 * schlimmer als gar keiner. Wer "Entfernen" liest und eine Einstellungsseite
 * bekommt, glaubt der App beim naechsten Mal nicht mehr.
 */
class AppActionTest {

    private fun app(
        packageName: String = "com.example.app",
        isSystemApp: Boolean = false,
        isUpdatedSystemApp: Boolean = false,
    ) = InstalledApp(
        packageName = packageName,
        label = "Beispiel",
        versionName = "1.0",
        installerPackage = "com.android.vending",
        isSystemApp = isSystemApp,
        isUpdatedSystemApp = isUpdatedSystemApp,
        isDebuggable = false,
        targetSdk = 34,
        requestedPermissions = emptyList(),
        signingSha256 = listOf("a".repeat(64)),
        signingSha1 = listOf("b".repeat(40)),
        signerSubjects = listOf("CN=Beispiel"),
        hasLauncher = true,
        declaresAccessibilityService = false,
        declaresDeviceAdmin = false,
        declaresNotificationListener = false,
        serviceCount = 1,
        receiverCount = 1,
        firstInstallTime = 0L,
        lastUpdateTime = 0L,
        apkPath = "/data/app/base.apk",
    )

    @Test
    fun `selbst installierte App laesst sich entfernen`() {
        assertEquals(AppAction.ENTFERNEN, AppActions.actionFor(app()))
        assertTrue(AppActions.canUninstall(app()))
    }

    @Test
    fun `Systembestandteil wird deaktiviert statt geloescht`() {
        // Der Kern der Sache: Loeschen geht nicht, aber es bleibt ein Weg --
        // frueher gab es hier gar keine Handlungsmoeglichkeit.
        val system = app(packageName = "com.android.cts.ctsshim", isSystemApp = true)
        assertEquals(AppAction.DEAKTIVIEREN, AppActions.actionFor(system))
        assertFalse(AppActions.canUninstall(system))
        assertTrue(AppActions.isActionable(system))
    }

    @Test
    fun `aktualisierte System-App bietet das Zuruecksetzen an`() {
        // Schonendster Eingriff: das Update faellt weg, die Werksfassung bleibt.
        val updated = app(isSystemApp = true, isUpdatedSystemApp = true)
        assertEquals(AppAction.UPDATES_ENTFERNEN, AppActions.actionFor(updated))
    }

    @Test
    fun `SelfGuard schlaegt sich nicht selbst vor`() {
        val self = app(packageName = "com.selfguard.app")
        assertEquals(AppAction.KEINE, AppActions.actionFor(self))
        assertFalse(AppActions.isActionable(self))
        assertNull(AppActions.label(AppAction.KEINE))
    }

    @Test
    fun `jede Handlung traegt Beschriftung und Erklaerung`() {
        // Ohne Erklaerung waere beim Deaktivieren unklar, warum statt eines
        // Dialogs eine Systemseite aufgeht.
        AppAction.entries.filter { it != AppAction.KEINE }.forEach { action ->
            assertNotNull("Beschriftung fehlt fuer $action", AppActions.label(action))
            assertNotNull("Erklaerung fehlt fuer $action", AppActions.explain(action))
        }
    }
}
