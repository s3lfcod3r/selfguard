package com.selfguard.app

import com.selfguard.app.ioc.IocStore
import com.selfguard.app.ioc.IocUpdater
import com.selfguard.app.scan.apps.DetectorContext
import com.selfguard.app.scan.apps.HiddenAppDetector
import com.selfguard.app.scan.apps.InstalledApp
import com.selfguard.app.scan.apps.PublicSigningKeys
import com.selfguard.app.scan.apps.StalkerwareDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hält die Fehlalarme fest, die auf einem echten Samsung aufgetreten sind.
 *
 * Beide waren schlimmer als eine verpasste Erkennung: Wer einmal erlebt, dass
 * die App eine Android-Systemkomponente als Überwachungssoftware meldet,
 * glaubt ihr auch beim nächsten, echten Treffer nicht mehr.
 */
class FalsePositiveTest {

    /** Der AOSP-Testschlüssel -- öffentlich, von jedem verwendbar. */
    private val aospTestKey = "61ed377e85d386a8dfee6b864bd85b0bfaa5af81"

    private fun app(
        packageName: String = "com.example.app",
        label: String = "Beispiel",
        installerPackage: String? = "com.android.vending",
        isSystemApp: Boolean = false,
        signingSha1: List<String> = listOf("b".repeat(40)),
        hasLauncher: Boolean = true,
        serviceCount: Int = 1,
    ) = InstalledApp(
        packageName = packageName,
        label = label,
        versionName = "1.0",
        installerPackage = installerPackage,
        isSystemApp = isSystemApp,
        isUpdatedSystemApp = false,
        isDebuggable = false,
        targetSdk = 34,
        requestedPermissions = emptyList(),
        signingSha256 = listOf("a".repeat(64)),
        signingSha1 = signingSha1,
        signerSubjects = listOf("CN=Beispiel"),
        hasLauncher = hasLauncher,
        declaresAccessibilityService = false,
        declaresDeviceAdmin = false,
        declaresNotificationListener = false,
        serviceCount = serviceCount,
        receiverCount = 1,
        firstInstallTime = 0L,
        lastUpdateTime = 0L,
        apkPath = "/system/app/base.apk",
    )

    // -- Fehlalarm 1: Systemkomponente als Stalkerware ------------------

    @Test
    fun `meldet Systemkomponente nicht als Stalkerware`() {
        // Genau der Fall aus dem Feld: com.android.cts.ctsshim ist mit dem
        // AOSP-Testschlüssel signiert, der im Datensatz stand.
        val iocs = DetectorContext(stalkerwareCertSha1 = setOf(aospTestKey))
        val findings = StalkerwareDetector.inspect(
            app(
                packageName = "com.android.cts.ctsshim",
                label = "ctsshim",
                isSystemApp = true,
                signingSha1 = listOf(aospTestKey),
            ),
            iocs,
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `wertet den öffentlichen AOSP-Schlüssel nie als Treffer`() {
        // Auch bei einer selbst installierten App darf dieser Schlüssel
        // keinen Treffer erzeugen -- er sagt über die App nichts aus.
        val iocs = DetectorContext(stalkerwareCertSha1 = setOf(aospTestKey))
        val findings = StalkerwareDetector.inspect(
            app(packageName = "de.eigene.app", signingSha1 = listOf(aospTestKey)),
            iocs,
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `erkennt den AOSP-Schlüssel als öffentlich`() {
        assertTrue(PublicSigningKeys.isPublic(aospTestKey))
        assertTrue(PublicSigningKeys.isPublic(aospTestKey.uppercase()))
        assertFalse(PublicSigningKeys.isPublic("c".repeat(40)))
    }

    @Test
    fun `meldet echte Stalkerware weiterhin`() {
        // Gegenprobe: die Absicherung darf die Erkennung nicht abschalten.
        val echterSchluessel = "7f5c0d54a813ba9b87a91420ca2c3de5e7948f09"
        val iocs = DetectorContext(stalkerwareCertSha1 = setOf(echterSchluessel))
        val findings = StalkerwareDetector.inspect(
            app(packageName = "com.getarnt.spion", signingSha1 = listOf(echterSchluessel)),
            iocs,
        )
        assertTrue(findings.isNotEmpty())
    }

    // -- Fehlalarm 2: Begleit-App ohne Symbol ---------------------------

    @Test
    fun `meldet Begleit-App aus dem Play Store nicht`() {
        // Galaxy Watch5 Manager und Carrier Services haben legitim kein Symbol.
        val findings = HiddenAppDetector.inspect(
            app(
                packageName = "com.samsung.android.geargplugin",
                label = "Galaxy Watch5 Manager",
                installerPackage = "com.android.vending",
                hasLauncher = false,
                serviceCount = 5,
            ),
            DetectorContext(),
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `meldet versteckte App aus unbekannter Quelle weiterhin`() {
        val findings = HiddenAppDetector.inspect(
            app(
                packageName = "com.getarnt.dienst",
                label = "System Service",
                installerPackage = null,
                hasLauncher = false,
                serviceCount = 3,
            ),
            DetectorContext(),
        )
        assertTrue(findings.isNotEmpty())
    }

    // -- Absicherung in den Daten ---------------------------------------

    @Test
    fun `entfernt beim Aufbereiten Zertifikate mehrerer Anbieter`() {
        val samples = buildString {
            appendLine("SHA256,Package Name,Certificate,Version,App")
            // Ein Schlüssel, drei unabhängige Anbieter -> muss rausfliegen.
            appendLine("${"1".repeat(64)},com.a,$aospTestKey,1,AnbieterA")
            appendLine("${"2".repeat(64)},com.b,$aospTestKey,1,AnbieterB")
            appendLine("${"3".repeat(64)},com.c,$aospTestKey,1,AnbieterC")
            // Ein Schlüssel, ein Anbieter -> muss bleiben.
            appendLine("${"4".repeat(64)},com.d,${"e".repeat(40)},1,AnbieterD")
        }
        val rendered = IocUpdater.render(samples, "type,indicator,app\n")
        val parsed = IocStore.parse(rendered.reader().buffered())

        assertFalse(aospTestKey in parsed.certSha1)
        assertTrue("e".repeat(40) in parsed.certSha1)
        // Die Paketnamen bleiben unangetastet -- nur das Zertifikat taugte nicht.
        assertTrue("com.a" in parsed.packages)
    }
}
