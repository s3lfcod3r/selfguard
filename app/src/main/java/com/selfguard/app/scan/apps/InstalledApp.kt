package com.selfguard.app.scan.apps

/**
 * Momentaufnahme einer installierten App -- alles, was die Detektoren brauchen,
 * und nichts weiter.
 *
 * Der Zweck dieser Zwischenschicht: das Auslesen des PackageManagers steckt
 * allein in [AppInventory] (schwer testbar), die Detektoren arbeiten nur noch
 * auf diesem reinen Datensatz und lassen sich damit ohne Geraet und ohne
 * Robolectric pruefen.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    /** Paket, das die Installation ausgeloest hat -- null bei unbekannter Quelle. */
    val installerPackage: String?,
    val isSystemApp: Boolean,
    /** Aktualisierte System-App: liegt im System, wurde aber ersetzt. */
    val isUpdatedSystemApp: Boolean,
    val isDebuggable: Boolean,
    val targetSdk: Int,
    val requestedPermissions: List<String>,
    /** SHA-256 aller Signaturzertifikate, klein geschrieben, ohne Trenner. */
    val signingSha256: List<String>,
    /** SHA-1 derselben Zertifikate -- fremde Datensaetze fuehren diesen Fingerabdruck. */
    val signingSha1: List<String>,
    /** Kennungen der Zertifikatsinhaber, etwa "CN=Android Debug, O=Android, C=US". */
    val signerSubjects: List<String>,
    /** Hat die App ein Symbol im Startbildschirm? */
    val hasLauncher: Boolean,
    /** Meldet einen Bedienungshilfen-Dienst an -- Vollzugriff auf Bildschirm und Eingaben. */
    val declaresAccessibilityService: Boolean,
    /** Meldet einen Geraeteverwalter an -- schwer zu entfernen, weitreichende Rechte. */
    val declaresDeviceAdmin: Boolean,
    /** Meldet einen Benachrichtigungs-Mithoerer an. */
    val declaresNotificationListener: Boolean,
    val serviceCount: Int,
    val receiverCount: Int,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val apkPath: String,
)
