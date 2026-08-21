package com.selfguard.app

import com.selfguard.app.scan.apps.DetectorContext
import com.selfguard.app.scan.apps.HiddenAppDetector
import com.selfguard.app.scan.apps.InstallOrigin
import com.selfguard.app.scan.apps.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueft, woher eine App laut Anzeige stammt.
 *
 * Hintergrund ist ein Fehler, der auf einem echten Geraet auffiel: Der Filter
 * der App-Liste hiess "Selbst installiert", zeigte aber alles an, was keine
 * System-App ist. Darunter fielen vom Hersteller nachgeladene Begleit-Apps
 * wie der Galaxy Watch5 Manager -- die hatte niemand selbst installiert.
 */
class InstallOriginTest {

    private fun app(
        packageName: String = "com.example.test",
        installerPackage: String? = null,
        isSystemApp: Boolean = false,
        isUpdatedSystemApp: Boolean = false,
        hasLauncher: Boolean = true,
        serviceCount: Int = 1,
        receiverCount: Int = 1,
    ) = InstalledApp(
        packageName = packageName,
        label = "Testapp",
        versionName = "1.0",
        installerPackage = installerPackage,
        isSystemApp = isSystemApp,
        isUpdatedSystemApp = isUpdatedSystemApp,
        isDebuggable = false,
        targetSdk = 34,
        requestedPermissions = emptyList(),
        signingSha256 = listOf("a".repeat(64)),
        signingSha1 = listOf("b".repeat(40)),
        signerSubjects = listOf("CN=Test"),
        hasLauncher = hasLauncher,
        declaresAccessibilityService = false,
        declaresDeviceAdmin = false,
        declaresNotificationListener = false,
        serviceCount = serviceCount,
        receiverCount = receiverCount,
        firstInstallTime = 0,
        lastUpdateTime = 0,
        apkPath = "/data/app/test.apk",
    )

    @Test
    fun `nennt den Play Store beim Namen`() {
        assertEquals(
            "Play Store",
            InstallOrigin.describe(app(installerPackage = "com.android.vending")),
        )
    }

    @Test
    fun `erkennt den Galaxy Store unter seinem echten Paketnamen`() {
        assertEquals(
            "Galaxy Store",
            InstallOrigin.describe(app(installerPackage = "com.sec.android.app.samsungapps")),
        )
    }

    @Test
    fun `nennt von Hand aufgespielte Apps so`() {
        assertEquals(
            "Von Hand installiert",
            InstallOrigin.describe(
                app(installerPackage = "com.google.android.packageinstaller")
            ),
        )
    }

    @Test
    fun `sagt bei fehlender Quelle nicht selbst installiert`() {
        val text = InstallOrigin.describe(app(installerPackage = null))
        assertEquals("Herkunft nicht hinterlegt", text)
    }

    @Test
    fun `zaehlt vorinstallierte Apps nicht zu den nachinstallierten`() {
        assertEquals(
            "Ab Werk vorinstalliert",
            InstallOrigin.describe(app(isSystemApp = true)),
        )
    }

    @Test
    fun `erkennt aktualisierte System-Apps trotz Store-Eintrag als vorinstalliert`() {
        // Eine aktualisierte System-App traegt oft den Play Store als Quelle.
        // Wer nur darauf schaut, erklaert eine ab Werk vorhandene App zur
        // selbst geladenen.
        val text = InstallOrigin.describe(
            app(
                installerPackage = "com.android.vending",
                isSystemApp = true,
                isUpdatedSystemApp = true,
            )
        )
        assertEquals("Ab Werk dabei, seitdem aktualisiert", text)
    }

    // -- Rueckwirkung auf die Erkennung ---------------------------------

    @Test
    fun `meldet Begleit-App aus dem Galaxy Store nicht als versteckt laufend`() {
        // Der Fall aus dem Screenshot: Galaxy Watch5 Manager, kein
        // Startsymbol, viele Dienste -- aber regulaer aus dem Galaxy Store.
        // Solange dessen Paketname falsch eingetragen war, meldete SelfGuard
        // das als schwerwiegenden Befund. Genau solche Fehlalarme bringen
        // Nutzer dazu, kuenftig auch die echten Meldungen zu ueberblaettern.
        val watchManager = app(
            packageName = "com.samsung.android.heartplugin",
            installerPackage = "com.sec.android.app.samsungapps",
            hasLauncher = false,
            serviceCount = 122,
            receiverCount = 105,
        )

        assertTrue(HiddenAppDetector.inspect(watchManager, DetectorContext()).isEmpty())
    }

    @Test
    fun `meldet App ohne Startsymbol aus unbekannter Quelle weiterhin`() {
        // Gegenprobe: Die Erkennung darf durch die Korrektur nicht stumpf
        // werden. Ohne hinterlegte Quelle bleibt der Befund.
        val versteckt = app(
            packageName = "com.unbekannt.dienst",
            installerPackage = null,
            hasLauncher = false,
            serviceCount = 4,
            receiverCount = 3,
        )

        assertTrue(HiddenAppDetector.inspect(versteckt, DetectorContext()).isNotEmpty())
    }
}
