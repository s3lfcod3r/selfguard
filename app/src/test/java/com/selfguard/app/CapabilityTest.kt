package com.selfguard.app

import com.selfguard.app.core.Capability
import com.selfguard.app.core.CapabilityTexts
import com.selfguard.app.core.FindingCode
import com.selfguard.app.core.FindingTexts
import com.selfguard.app.core.SettingsTarget
import com.selfguard.app.scan.apps.DetectorContext
import com.selfguard.app.scan.apps.InstalledApp
import com.selfguard.app.scan.apps.SpyPermissionDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft die Fähigkeiten eines Befundes und ihre Texte.
 *
 * Der Grund für die Vollständigkeitstests: [CapabilityTexts] und
 * [FindingTexts] greifen mit `getValue` zu. Fehlt ein Eintrag, merkt das kein
 * Compiler -- die App stürzt beim Aufklappen des Befundes ab, also genau
 * dann, wenn der Nutzer sie am dringendsten braucht. Eine neue Fähigkeit
 * ohne Text soll deshalb hier scheitern und nicht auf dem Telefon.
 */
class CapabilityTest {

    private val ctx = DetectorContext()

    private fun app(
        permissions: List<String> = emptyList(),
        accessibility: Boolean = false,
        deviceAdmin: Boolean = false,
        notificationListener: Boolean = false,
    ) = InstalledApp(
        packageName = "com.example.test",
        label = "Testapp",
        versionName = "1.0",
        installerPackage = "com.android.vending",
        isSystemApp = false,
        isUpdatedSystemApp = false,
        isDebuggable = false,
        targetSdk = 34,
        requestedPermissions = permissions,
        signingSha256 = listOf("a".repeat(64)),
        signingSha1 = listOf("b".repeat(40)),
        signerSubjects = listOf("CN=Test"),
        hasLauncher = true,
        declaresAccessibilityService = accessibility,
        declaresDeviceAdmin = deviceAdmin,
        declaresNotificationListener = notificationListener,
        serviceCount = 1,
        receiverCount = 1,
        firstInstallTime = 0,
        lastUpdateTime = 0,
        apkPath = "/data/app/test.apk",
    )

    // -- Vollständigkeit -----------------------------------------------

    @Test
    fun `jede Fähigkeit hat einen deutschen Text`() {
        Capability.entries.forEach { capability ->
            val text = CapabilityTexts.of(capability, "de")
            assertTrue(
                "Name fehlt bei $capability",
                text.label.isNotBlank(),
            )
            assertTrue(
                "Folgen fehlen bei $capability",
                text.consequence.isNotBlank(),
            )
        }
    }

    @Test
    fun `jede Fähigkeit hat einen englischen Text`() {
        Capability.entries.forEach { capability ->
            val text = CapabilityTexts.of(capability, "en")
            assertTrue("Name fehlt bei $capability", text.label.isNotBlank())
            assertTrue("Folgen fehlen bei $capability", text.consequence.isNotBlank())
        }
    }

    @Test
    fun `jeder Befundtext liegt in beiden Sprachen vor`() {
        FindingCode.entries.forEach { code ->
            assertTrue(
                "Deutscher Text fehlt bei $code",
                FindingTexts.of(code, "de").title.isNotBlank(),
            )
            assertTrue(
                "Englischer Text fehlt bei $code",
                FindingTexts.of(code, "en").title.isNotBlank(),
            )
        }
    }

    // -- Zuordnung Berechtigung zu Fähigkeit ---------------------------

    @Test
    fun `erkennt Mikrofon und Kamera als eigene Fähigkeiten`() {
        val found = SpyPermissionDetector.inspect(
            app(
                permissions = listOf(
                    "android.permission.RECORD_AUDIO",
                    "android.permission.CAMERA",
                    "android.permission.READ_SMS",
                    "android.permission.READ_CONTACTS",
                    "android.permission.ACCESS_FINE_LOCATION",
                )
            ),
            ctx,
        ).first()

        assertTrue(Capability.MICROPHONE in found.capabilities)
        assertTrue(Capability.CAMERA in found.capabilities)
        assertTrue(Capability.MESSAGES in found.capabilities)
    }

    @Test
    fun `zählt gleichbedeutende SMS-Berechtigungen nur einmal`() {
        val found = SpyPermissionDetector.inspect(
            app(
                permissions = listOf(
                    "android.permission.READ_SMS",
                    "android.permission.RECEIVE_SMS",
                    "android.permission.RECEIVE_MMS",
                    "android.permission.CAMERA",
                    "android.permission.RECORD_AUDIO",
                    "android.permission.READ_CONTACTS",
                    "android.permission.ACCESS_FINE_LOCATION",
                )
            ),
            ctx,
        ).first()

        assertEquals(1, found.capabilities.count { it == Capability.MESSAGES })
    }

    @Test
    fun `nimmt angemeldete Dienste als eigene Fähigkeiten auf`() {
        val found = SpyPermissionDetector.inspect(
            app(
                permissions = listOf(
                    "android.permission.RECORD_AUDIO",
                    "android.permission.CAMERA",
                    "android.permission.READ_CONTACTS",
                ),
                accessibility = true,
                notificationListener = true,
            ),
            ctx,
        ).first()

        assertTrue(Capability.ACCESSIBILITY in found.capabilities)
        assertTrue(Capability.NOTIFICATION_LISTENER in found.capabilities)
    }

    @Test
    fun `meldet die Fähigkeiten in fester Reihenfolge`() {
        val found = SpyPermissionDetector.inspect(
            app(
                permissions = listOf(
                    "android.permission.RECEIVE_BOOT_COMPLETED",
                    "android.permission.READ_SMS",
                    "android.permission.CAMERA",
                    "android.permission.RECORD_AUDIO",
                    "android.permission.READ_CONTACTS",
                )
            ),
            ctx,
        ).first()

        assertEquals(found.capabilities.sorted(), found.capabilities)
        assertEquals(Capability.MESSAGES, found.capabilities.first())
    }

    // -- Ziele in den Einstellungen -------------------------------------

    @Test
    fun `Sonderrechte zeigen nicht auf die Berechtigungsseite`() {
        // Diese vier sitzen unter "Spezieller Zugriff" bzw. in eigenen
        // Listen. Wer dafür die Berechtigungsseite öffnet, schickt den
        // Nutzer auf eine Seite, auf der der gesuchte Schalter nicht ist.
        assertEquals(SettingsTarget.OVERLAY, Capability.OVERLAY.target)
        assertEquals(SettingsTarget.ACCESSIBILITY, Capability.ACCESSIBILITY.target)
        assertEquals(
            SettingsTarget.NOTIFICATION_LISTENER,
            Capability.NOTIFICATION_LISTENER.target,
        )
        assertEquals(
            SettingsTarget.INSTALL_UNKNOWN_APPS,
            Capability.INSTALL_PACKAGES.target,
        )
    }

    @Test
    fun `nicht entziehbare Berechtigungen sind als solche gekennzeichnet`() {
        // Beide werden bei der Installation vergeben; Android bietet keinen
        // Schalter dafür. Ein Knopf mit der Aufschrift "Entziehen" wäre
        // hier eine Lüge.
        assertFalse(Capability.AUTOSTART.revocable)
        assertFalse(Capability.APP_LIST.revocable)
        assertTrue(Capability.MICROPHONE.revocable)
    }

    @Test
    fun `nicht entziehbare Fähigkeiten sagen das im Text`() {
        listOf(Capability.AUTOSTART, Capability.APP_LIST).forEach { capability ->
            val consequence = CapabilityTexts.of(capability, "de").consequence
            assertTrue(
                "Text zu $capability verschweigt, dass Android das nicht zulässt",
                consequence.contains("nicht einzeln entziehen"),
            )
        }
    }
}
