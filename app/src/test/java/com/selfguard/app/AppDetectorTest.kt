package com.selfguard.app

import com.selfguard.app.core.FindingCode
import com.selfguard.app.core.RiskBand
import com.selfguard.app.core.RiskScore
import com.selfguard.app.core.Severity
import com.selfguard.app.scan.apps.AppDetectors
import com.selfguard.app.scan.apps.BrandImpersonationDetector
import com.selfguard.app.scan.apps.BuildFlagsDetector
import com.selfguard.app.scan.apps.DebugCertDetector
import com.selfguard.app.scan.apps.DetectorContext
import com.selfguard.app.scan.apps.HiddenAppDetector
import com.selfguard.app.scan.apps.InstalledApp
import com.selfguard.app.scan.apps.InstallerDetector
import com.selfguard.app.scan.apps.SpyPermissionDetector
import com.selfguard.app.scan.apps.StalkerwareDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueft die Detektoren als reine Funktionen -- ohne Geraet, ohne Robolectric.
 *
 * Der wichtigste Teil sind die Gegenproben: ein Scanner, der bei normalen Apps
 * anschlaegt, ist wertlos. Zu jedem Treffer gehoert deshalb ein Fall, der
 * *nicht* anschlagen darf.
 */
class AppDetectorTest {

    // -- Testdaten ------------------------------------------------------

    private fun app(
        packageName: String = "com.example.harmlos",
        label: String = "Harmlose App",
        installerPackage: String? = "com.android.vending",
        isSystemApp: Boolean = false,
        isDebuggable: Boolean = false,
        targetSdk: Int = 34,
        requestedPermissions: List<String> = emptyList(),
        signingSha256: List<String> = listOf("a".repeat(64)),
        signingSha1: List<String> = listOf("b".repeat(40)),
        signerSubjects: List<String> = listOf("CN=Beispiel GmbH, O=Beispiel, C=DE"),
        hasLauncher: Boolean = true,
        declaresAccessibilityService: Boolean = false,
        declaresDeviceAdmin: Boolean = false,
        declaresNotificationListener: Boolean = false,
        serviceCount: Int = 1,
        receiverCount: Int = 1,
    ) = InstalledApp(
        packageName = packageName,
        label = label,
        versionName = "1.0",
        installerPackage = installerPackage,
        isSystemApp = isSystemApp,
        isUpdatedSystemApp = false,
        isDebuggable = isDebuggable,
        targetSdk = targetSdk,
        requestedPermissions = requestedPermissions,
        signingSha256 = signingSha256,
        signingSha1 = signingSha1,
        signerSubjects = signerSubjects,
        hasLauncher = hasLauncher,
        declaresAccessibilityService = declaresAccessibilityService,
        declaresDeviceAdmin = declaresDeviceAdmin,
        declaresNotificationListener = declaresNotificationListener,
        serviceCount = serviceCount,
        receiverCount = receiverCount,
        firstInstallTime = 0L,
        lastUpdateTime = 0L,
        apkPath = "/data/app/base.apk",
    )

    private val ctx = DetectorContext()

    // -- D01 Debug-Zertifikat -------------------------------------------

    @Test
    fun `meldet App mit Android-Debug-Zertifikat`() {
        val findings = DebugCertDetector.inspect(
            app(signerSubjects = listOf("CN=Android Debug, O=Android, C=US")), ctx
        )
        assertEquals(1, findings.size)
        assertEquals(FindingCode.APP_DEBUG_CERT, findings.first().code)
    }

    @Test
    fun `meldet regulaer signierte App nicht`() {
        assertTrue(DebugCertDetector.inspect(app(), ctx).isEmpty())
    }

    @Test
    fun `ignoriert Debug-Zertifikat bei System-Apps`() {
        val findings = DebugCertDetector.inspect(
            app(isSystemApp = true, signerSubjects = listOf("CN=Android Debug, O=Android, C=US")),
            ctx,
        )
        assertTrue(findings.isEmpty())
    }

    // -- D02 Installationsquelle ----------------------------------------

    @Test
    fun `meldet App ohne hinterlegte Installationsquelle`() {
        val findings = InstallerDetector.inspect(app(installerPackage = null), ctx)
        assertEquals(FindingCode.APP_UNKNOWN_INSTALLER, findings.first().code)
    }

    @Test
    fun `akzeptiert App aus dem eigenen Katalog`() {
        val findings = InstallerDetector.inspect(app(installerPackage = "com.selfstore.app"), ctx)
        assertTrue(findings.isEmpty())
    }

    // -- D03 Versteckte App ---------------------------------------------

    @Test
    fun `meldet App ohne Starter-Symbol mit Hintergrunddiensten`() {
        // Ohne bekannte Installationsquelle -- so kommt Ueberwachungssoftware
        // auf ein Geraet. Aus einem gepflegten Katalog waere derselbe Aufbau
        // eine gewoehnliche Begleit-App und darf nicht gemeldet werden;
        // dafuer gibt es eine eigene Gegenprobe in FalsePositiveTest.
        val findings = HiddenAppDetector.inspect(
            app(installerPackage = null, hasLauncher = false, serviceCount = 3), ctx
        )
        assertEquals(FindingCode.APP_HIDDEN_NO_LAUNCHER, findings.first().code)
        assertEquals(Severity.HIGH, findings.first().severity)
    }

    @Test
    fun `meldet reine Bibliothek ohne Dienste nicht`() {
        val findings = HiddenAppDetector.inspect(
            app(hasLauncher = false, serviceCount = 0, receiverCount = 0), ctx
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `meldet System-App ohne Starter-Symbol nicht`() {
        val findings = HiddenAppDetector.inspect(
            app(isSystemApp = true, hasLauncher = false, serviceCount = 5), ctx
        )
        assertTrue(findings.isEmpty())
    }

    // -- D04 Berechtigungsprofil ----------------------------------------

    private val spyPermissions = listOf(
        "android.permission.READ_SMS",
        "android.permission.READ_CALL_LOG",
        "android.permission.RECORD_AUDIO",
        "android.permission.CAMERA",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_CONTACTS",
        "android.permission.RECEIVE_BOOT_COMPLETED",
    )

    @Test
    fun `meldet breites Ueberwachungsprofil als schwerwiegend`() {
        val findings = SpyPermissionDetector.inspect(app(requestedPermissions = spyPermissions), ctx)
        assertEquals(FindingCode.APP_SPY_PERMISSIONS, findings.first().code)
        assertEquals(Severity.HIGH, findings.first().severity)
    }

    @Test
    fun `meldet uebliche Messenger-Berechtigungen nicht`() {
        val messenger = listOf(
            "android.permission.CAMERA",
            "android.permission.READ_CONTACTS",
            "android.permission.RECORD_AUDIO",
            "android.permission.INTERNET",
        )
        assertTrue(SpyPermissionDetector.inspect(app(requestedPermissions = messenger), ctx).isEmpty())
    }

    @Test
    fun `wertet Bedienungshilfen-Dienst hoeher als reine Berechtigungen`() {
        val app = app(
            requestedPermissions = listOf(
                "android.permission.READ_SMS",
                "android.permission.CAMERA",
                "android.permission.RECEIVE_BOOT_COMPLETED",
            ),
            declaresAccessibilityService = true,
        )
        val findings = SpyPermissionDetector.inspect(app, ctx)
        assertEquals(Severity.HIGH, findings.first().severity)
    }

    @Test
    fun `ignoriert Berechtigungsprofil bei System-Apps`() {
        val findings = SpyPermissionDetector.inspect(
            app(isSystemApp = true, requestedPermissions = spyPermissions), ctx
        )
        assertTrue(findings.isEmpty())
    }

    // -- D05 Stalkerware ------------------------------------------------

    @Test
    fun `meldet bekannten Stalkerware-Paketnamen als kritisch`() {
        val iocs = DetectorContext(stalkerwarePackages = setOf("com.topspy"))
        val findings = StalkerwareDetector.inspect(app(packageName = "com.topspy"), iocs)
        assertEquals(Severity.CRITICAL, findings.first().severity)
    }

    @Test
    fun `meldet bekanntes Signaturzertifikat auch bei neuem Paketnamen`() {
        // Stalkerware-Anbieter wechseln Paketnamen, den Schluessel meist nicht.
        val knownCert = "7f5c0d54a813ba9b87a91420ca2c3de5e7948f09"
        val iocs = DetectorContext(stalkerwareCertSha1 = setOf(knownCert))
        val findings = StalkerwareDetector.inspect(
            app(packageName = "com.frisch.getarnt", signingSha1 = listOf(knownCert.uppercase())),
            iocs,
        )
        assertEquals(FindingCode.APP_KNOWN_STALKERWARE, findings.first().code)
    }

    @Test
    fun `meldet unbekannte App nicht als Stalkerware`() {
        val iocs = DetectorContext(stalkerwarePackages = setOf("com.topspy"))
        assertTrue(StalkerwareDetector.inspect(app(), iocs).isEmpty())
    }

    // -- D06 und D07 Bau-Kennzeichen ------------------------------------

    @Test
    fun `meldet veraltetes targetSdk`() {
        val findings = BuildFlagsDetector.inspect(app(targetSdk = 19), ctx)
        assertEquals(FindingCode.APP_ANCIENT_TARGET_SDK, findings.first().code)
    }

    @Test
    fun `meldet debuggable-Kennzeichen als schwerwiegend`() {
        val findings = BuildFlagsDetector.inspect(app(isDebuggable = true), ctx)
        assertEquals(Severity.HIGH, findings.first().severity)
    }

    @Test
    fun `meldet aktuelle App ohne Debug-Kennzeichen nicht`() {
        assertTrue(BuildFlagsDetector.inspect(app(), ctx).isEmpty())
    }

    // -- D08 Markenmissbrauch -------------------------------------------

    @Test
    fun `meldet gefaelschtes WhatsApp`() {
        val findings = BrandImpersonationDetector.inspect(
            app(packageName = "com.boese.faelschung", label = "WhatsApp"), ctx
        )
        assertEquals(FindingCode.APP_BRAND_IMPERSONATION, findings.first().code)
    }

    @Test
    fun `meldet echtes WhatsApp nicht`() {
        val findings = BrandImpersonationDetector.inspect(
            app(packageName = "com.whatsapp", label = "WhatsApp"), ctx
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `erkennt Schreibweise mit Leerzeichen als denselben Namen`() {
        val findings = BrandImpersonationDetector.inspect(
            app(packageName = "com.boese.faelschung", label = "Whats App"), ctx
        )
        assertEquals(FindingCode.APP_BRAND_IMPERSONATION, findings.first().code)
    }

    @Test
    fun `meldet unbeteiligte App mit eigenem Namen nicht`() {
        val findings = BrandImpersonationDetector.inspect(
            app(packageName = "de.meinefirma.app", label = "Meine Firma"), ctx
        )
        assertTrue(findings.isEmpty())
    }

    // -- Zusammenspiel und Bewertung ------------------------------------

    @Test
    fun `saubere App erzeugt ueberhaupt keine Befunde`() {
        assertTrue(AppDetectors.inspect(app(), ctx).isEmpty())
    }

    @Test
    fun `typische Stalkerware sammelt mehrere Befunde ein`() {
        val findings = AppDetectors.inspect(
            app(
                packageName = "com.getarnt.dienst",
                label = "System Service",
                installerPackage = null,
                requestedPermissions = spyPermissions,
                hasLauncher = false,
                serviceCount = 4,
                declaresAccessibilityService = true,
                declaresDeviceAdmin = true,
            ),
            ctx,
        )
        val codes = findings.map { it.code }.toSet()
        assertTrue(FindingCode.APP_HIDDEN_NO_LAUNCHER in codes)
        assertTrue(FindingCode.APP_SPY_PERMISSIONS in codes)
        assertTrue(FindingCode.APP_UNKNOWN_INSTALLER in codes)
        assertEquals(RiskBand.ALARM, RiskScore.band(findings))
    }

    @Test
    fun `einzelne Kleinigkeit bleibt unter der Warnschwelle`() {
        val findings = AppDetectors.inspect(app(installerPackage = null), ctx)
        assertEquals(RiskBand.SAUBER, RiskScore.band(findings))
    }

    @Test
    fun `ein kritischer Befund hebt immer auf Alarm`() {
        val iocs = DetectorContext(stalkerwarePackages = setOf("com.topspy"))
        val findings = AppDetectors.inspect(app(packageName = "com.topspy"), iocs)
        assertEquals(RiskBand.ALARM, RiskScore.band(findings))
    }

    @Test
    fun `Punktzahl bleibt bei hundert gedeckelt`() {
        val iocs = DetectorContext(
            stalkerwarePackages = setOf("com.topspy"),
            stalkerwareCertSha1 = setOf("b".repeat(40)),
        )
        val findings = AppDetectors.inspect(
            app(
                packageName = "com.topspy",
                installerPackage = null,
                isDebuggable = true,
                targetSdk = 19,
                requestedPermissions = spyPermissions,
                signerSubjects = listOf("CN=Android Debug, O=Android, C=US"),
                hasLauncher = false,
                declaresAccessibilityService = true,
            ),
            iocs,
        )
        assertEquals(100, RiskScore.of(findings))
    }
}
