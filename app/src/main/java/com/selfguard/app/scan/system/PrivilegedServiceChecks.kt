package com.selfguard.app.scan.system

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.selfguard.app.core.Finding
import com.selfguard.app.core.FindingCode
import com.selfguard.app.core.Severity
import com.selfguard.app.scan.apps.InstalledApp

/**
 * S02, S03, S04 und S08 -- wer auf diesem Gerät besondere Rechte hält.
 *
 * Unterschied zum App-Audit: dort wird geprüft, was eine App *anmeldet*.
 * Hier zählt, was tatsächlich *eingeschaltet* ist. Das ist der deutlich
 * stärkere Befund -- ein angemeldeter Bedienungshilfen-Dienst ist harmlos,
 * ein aktiver sieht alles auf dem Bildschirm.
 */
object PrivilegedServiceChecks {

    private const val NOTIFICATION_LISTENERS = "enabled_notification_listeners"
    private const val COMPONENT_SEPARATOR = ":"

    fun run(context: Context, apps: List<InstalledApp>): List<Finding> {
        val byPackage = apps.associateBy { it.packageName }
        return buildList {
            addAll(accessibilityServices(context, byPackage))
            addAll(deviceAdmins(context, byPackage))
            addAll(notificationListeners(context, byPackage))
            addAll(installerPrivileges(apps))
        }
    }

    /** S02 -- aktive Bedienungshilfen-Dienste. */
    private fun accessibilityServices(
        context: Context,
        byPackage: Map<String, InstalledApp>,
    ): List<Finding> = enabledComponents(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        .mapNotNull { component ->
            val app = byPackage[component.packageName]
            // System-Dienste wie TalkBack sind erwartbar und würden nur Rauschen erzeugen.
            if (app?.isSystemApp == true) return@mapNotNull null
            Finding(
                code = FindingCode.SYS_ACCESSIBILITY_SERVICE,
                severity = Severity.MEDIUM,
                subject = component.packageName,
                subjectLabel = app?.label ?: component.packageName,
                evidence = listOf("Dienst: ${component.className}"),
            )
        }

    /** S03 -- Apps mit Geräteverwalter-Rechten. */
    private fun deviceAdmins(
        context: Context,
        byPackage: Map<String, InstalledApp>,
    ): List<Finding> {
        val manager = context.getSystemService(DevicePolicyManager::class.java)
            ?: return emptyList()
        val admins = runCatching { manager.activeAdmins }.getOrNull().orEmpty()

        return admins.mapNotNull { component ->
            val app = byPackage[component.packageName]
            // "Mein Gerät finden" und Firmenverwaltung sind System-Apps -- kein Befund.
            if (app?.isSystemApp == true) return@mapNotNull null
            Finding(
                code = FindingCode.SYS_DEVICE_ADMIN,
                severity = Severity.MEDIUM,
                subject = component.packageName,
                subjectLabel = app?.label ?: component.packageName,
                evidence = listOfNotNull(
                    "Verwalter: ${component.className}",
                    "lässt sich erst nach Entzug der Rechte entfernen",
                    if (app?.hasLauncher == false) "ohne Starter-Symbol" else null,
                ),
            )
        }
    }

    /** S04 -- Apps, die alle Benachrichtigungen mitlesen. */
    private fun notificationListeners(
        context: Context,
        byPackage: Map<String, InstalledApp>,
    ): List<Finding> = enabledComponents(context, NOTIFICATION_LISTENERS)
        .mapNotNull { component ->
            val app = byPackage[component.packageName]
            if (app?.isSystemApp == true) return@mapNotNull null
            Finding(
                code = FindingCode.SYS_NOTIFICATION_LISTENER,
                severity = Severity.MEDIUM,
                subject = component.packageName,
                subjectLabel = app?.label ?: component.packageName,
                evidence = listOf("Mithörer: ${component.className}"),
            )
        }

    /**
     * S08 -- Apps, die weitere Apps installieren dürfen.
     *
     * Ob das Recht tatsächlich erteilt wurde, lässt sich für fremde Apps
     * nicht abfragen; gemeldet wird deshalb, wer es anfordert. Bei einem
     * App-Katalog ist das erwartbar, sonst ein Weg zum Nachladen.
     */
    private fun installerPrivileges(apps: List<InstalledApp>): List<Finding> = apps
        .filter { !it.isSystemApp }
        .filter { "android.permission.REQUEST_INSTALL_PACKAGES" in it.requestedPermissions }
        .map { app ->
            Finding(
                code = FindingCode.SYS_UNKNOWN_SOURCES,
                severity = Severity.LOW,
                subject = app.packageName,
                subjectLabel = app.label,
                evidence = listOf("fordert REQUEST_INSTALL_PACKAGES an"),
            )
        }

    /**
     * Liest eine der doppelpunktgetrennten Komponentenlisten aus den
     * Secure-Einstellungen. Fehlt der Eintrag oder ist er leer, ist nichts
     * eingeschaltet.
     */
    private fun enabledComponents(context: Context, key: String): List<ComponentName> {
        val raw = runCatching {
            Settings.Secure.getString(context.contentResolver, key)
        }.getOrNull().orEmpty()

        if (raw.isBlank()) return emptyList()

        return raw.split(COMPONENT_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { ComponentName.unflattenFromString(it) }
    }
}
