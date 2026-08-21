package com.selfguard.app.scan.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.selfguard.app.core.Capability
import com.selfguard.app.core.SettingsTarget

/**
 * Der Weg von einem Befund zur passenden Systemeinstellung.
 *
 * **Warum nicht einfach abschalten?** Android lässt keine App die
 * Berechtigungen einer anderen entziehen. `REVOKE_RUNTIME_PERMISSIONS` ist
 * der Systemsignatur vorbehalten, und daran lässt sich nichts drehen -- auch
 * nicht mit mehr Berechtigungen im Manifest. Das ist kein Mangel, sondern
 * Absicht: könnte eine beliebige App fremde Rechte ändern, wäre der erste
 * Schritt jeder Schadsoftware, dem Schutzprogramm die seinen zu nehmen.
 *
 * Was bleibt, ist der kurze Weg: den Nutzer genau auf die Seite bringen, auf
 * der der Schalter sitzt. Das ist mehr wert, als es klingt -- der Unterschied
 * zwischen "Einstellungen, Apps, App suchen, Berechtigungen, Mikrofon" und
 * einem Antippen ist der Unterschied zwischen erledigt und irgendwann.
 *
 * Jedes Ziel hat eine Ersatzkette. Hersteller bauen ihre Einstellungen um,
 * benennen Seiten anders oder lassen welche weg; ein Absturz wäre die
 * schlechteste Antwort darauf. Am Ende jeder Kette steht die App-Info, die es
 * auf jedem Gerät gibt.
 */
object PermissionActions {

    /**
     * Nicht öffentlich, aber seit Android 6 auf praktisch jedem Gerät
     * vorhanden: die Berechtigungsseite einer einzelnen App. Über die
     * offizielle API ist nur die App-Info erreichbar, von der aus der Nutzer
     * noch einmal tippen müsste. Wir versuchen den direkten Weg und fangen
     * den Fall ab, dass es ihn nicht gibt.
     */
    private const val ACTION_MANAGE_APP_PERMISSIONS = "android.intent.action.MANAGE_APP_PERMISSIONS"
    private const val EXTRA_PACKAGE_NAME = "android.intent.extra.PACKAGE_NAME"

    /** Ebenfalls nicht öffentlich, aber verbreitet: die Geräteverwalter-Liste. */
    private const val ACTION_DEVICE_ADMIN_SETTINGS = "android.app.action.DEVICE_ADMIN_SETTINGS"

    /**
     * Liefert die Absicht, die den Nutzer der Fähigkeit am nächsten bringt.
     * Gibt immer etwas zurück -- im Zweifel die App-Info.
     */
    fun intentFor(context: Context, capability: Capability, packageName: String): Intent =
        firstResolvable(context, candidates(capability.target, packageName))
            ?: AppActions.detailsIntent(packageName)

    private fun candidates(target: SettingsTarget, packageName: String): List<Intent> {
        val uri = Uri.parse("package:$packageName")
        return when (target) {
            SettingsTarget.APP_PERMISSIONS -> listOf(
                Intent(ACTION_MANAGE_APP_PERMISSIONS).putExtra(EXTRA_PACKAGE_NAME, packageName),
            )

            SettingsTarget.OVERLAY -> listOf(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri),
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            )

            SettingsTarget.ACCESSIBILITY -> listOf(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            )

            SettingsTarget.NOTIFICATION_LISTENER -> listOf(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            )

            SettingsTarget.INSTALL_UNKNOWN_APPS -> listOf(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri),
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            )

            SettingsTarget.USAGE_ACCESS -> listOf(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            )

            // Die App-bezogene Fassung gibt es erst ab Android 11 -- davor
            // existierte die Berechtigung selbst noch nicht.
            SettingsTarget.ALL_FILES -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                listOf(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri),
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                )
            } else {
                emptyList()
            }

            SettingsTarget.DEVICE_ADMIN -> listOf(
                Intent(ACTION_DEVICE_ADMIN_SETTINGS),
                Intent(Settings.ACTION_SECURITY_SETTINGS),
            )

            SettingsTarget.APP_DETAILS -> emptyList()
        }
    }

    private fun firstResolvable(context: Context, candidates: List<Intent>): Intent? =
        candidates.firstOrNull { resolves(context, it) }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Suppress("DEPRECATION")
    private fun resolves(context: Context, intent: Intent): Boolean = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.resolveActivity(intent, 0)
        } != null
    }.getOrDefault(false)
}
