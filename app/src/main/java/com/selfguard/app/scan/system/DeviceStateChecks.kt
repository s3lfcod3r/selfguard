package com.selfguard.app.scan.system

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.scottyab.rootbeer.RootBeer
import com.selfguard.app.core.Finding
import com.selfguard.app.core.FindingCode
import com.selfguard.app.core.Severity

/**
 * S05, S06, S07 und S09 -- der Zustand des Geräts selbst.
 *
 * Diese Prüfungen hängen an keiner einzelnen App, sondern an Einstellungen,
 * die das gesamte Schutzniveau bestimmen. Eine fehlende Bildschirmsperre
 * entwertet jede andere Maßnahme.
 */
object DeviceStateChecks {

    private const val SUBJECT_DEVICE = "gerät"

    fun run(context: Context): List<Finding> = buildList {
        addAll(developerAccess(context))
        addAll(root(context))
        addAll(integrity())
        addAll(screenLock(context))
    }

    /** S05 -- Entwickleroptionen und USB-Debugging. */
    private fun developerAccess(context: Context): List<Finding> {
        val devEnabled = globalFlag(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)
        val adbEnabled = globalFlag(context, Settings.Global.ADB_ENABLED)
        if (!devEnabled && !adbEnabled) return emptyList()

        // ADB wiegt schwerer: damit reicht ein Kabel für weitreichenden Zugriff.
        val severity = if (adbEnabled) Severity.MEDIUM else Severity.LOW

        return listOf(
            Finding(
                code = FindingCode.SYS_DEVELOPER_OPTIONS,
                severity = severity,
                subject = SUBJECT_DEVICE,
                subjectLabel = deviceName(),
                evidence = buildList {
                    if (devEnabled) add("Entwickleroptionen eingeschaltet")
                    if (adbEnabled) add("USB-Debugging eingeschaltet")
                },
            )
        )
    }

    /** S06 -- Root-Erkennung über RootBeer (Apache-2.0). */
    private fun root(context: Context): List<Finding> {
        val rooted = runCatching { RootBeer(context).isRooted }.getOrDefault(false)
        if (!rooted) return emptyList()

        return listOf(
            Finding(
                code = FindingCode.SYS_ROOTED,
                severity = Severity.HIGH,
                subject = SUBJECT_DEVICE,
                subjectLabel = deviceName(),
                evidence = listOf("RootBeer meldet Root-Zugriff"),
            )
        )
    }

    /**
     * S07 -- Hinweis auf ein nicht ab Werk signiertes System.
     *
     * Bewusst nur die billige Prüfung auf Test-Signaturschlüssel. Eine
     * belastbare Aussage käme von der Play-Integrity-Prüfung, die aber einen
     * eigenen Server mit Einmalwert braucht -- die App soll offline nutzbar
     * bleiben. Als Vorbehalt steht das so auch im Befundtext.
     */
    private fun integrity(): List<Finding> {
        val tags = Build.TAGS.orEmpty()
        if (!tags.contains("test-keys")) return emptyList()

        return listOf(
            Finding(
                code = FindingCode.SYS_INTEGRITY,
                severity = Severity.MEDIUM,
                subject = SUBJECT_DEVICE,
                subjectLabel = deviceName(),
                evidence = listOf("Build-Kennung: $tags"),
            )
        )
    }

    /** S09 -- Bildschirmsperre. */
    private fun screenLock(context: Context): List<Finding> {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
            ?: return emptyList()
        if (runCatching { keyguard.isDeviceSecure }.getOrDefault(true)) return emptyList()

        return listOf(
            Finding(
                code = FindingCode.SYS_NO_SCREEN_LOCK,
                severity = Severity.HIGH,
                subject = SUBJECT_DEVICE,
                subjectLabel = deviceName(),
                evidence = listOf("keine PIN, kein Muster, kein Kennwort gesetzt"),
            )
        )
    }

    private fun globalFlag(context: Context, key: String): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, key, 0) == 1
    }.getOrDefault(false)

    private fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
}
