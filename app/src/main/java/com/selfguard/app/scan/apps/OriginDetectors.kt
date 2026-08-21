package com.selfguard.app.scan.apps

import com.selfguard.app.core.Finding
import com.selfguard.app.core.FindingCode
import com.selfguard.app.core.Severity

/**
 * D01 -- signiert mit dem öffentlichen Android-Debug-Schlüssel.
 *
 * Dieser Schlüssel liegt jedem Android-SDK bei und ist auf jedem Rechner der
 * Welt derselbe. Eine so signierte App kann jeder durch eine eigene Fassung
 * ersetzen, weil die Signaturprüfung beim Update für alle aufgeht. Bei
 * selbst gebauten Apps ist das üblich, bei allem anderen ein Warnzeichen.
 */
object DebugCertDetector : AppDetector {

    private const val DEBUG_SUBJECT = "CN=Android Debug"

    override fun inspect(app: InstalledApp, ctx: DetectorContext): List<Finding> {
        if (app.isSystemApp) return emptyList()
        val debugSigners = app.signerSubjects.filter { it.startsWith(DEBUG_SUBJECT, ignoreCase = true) }
        if (debugSigners.isEmpty()) return emptyList()

        return listOf(
            Finding(
                code = FindingCode.APP_DEBUG_CERT,
                severity = Severity.MEDIUM,
                subject = app.packageName,
                subjectLabel = app.label,
                evidence = debugSigners,
            )
        )
    }
}

/**
 * D02 -- Herkunft unbekannt.
 *
 * Für sich genommen harmlos: wer Apps aus einem eigenen Katalog installiert,
 * hat ständig solche Einträge. Der Wert liegt in der Summe -- zusammen mit
 * versteckter Ausführung oder Spionageberechtigungen ergibt sich ein Bild.
 */
object InstallerDetector : AppDetector {

    override fun inspect(app: InstalledApp, ctx: DetectorContext): List<Finding> {
        if (app.isSystemApp) return emptyList()
        val installer = app.installerPackage
        if (installer != null && installer in ctx.trustedInstallers) return emptyList()

        return listOf(
            Finding(
                code = FindingCode.APP_UNKNOWN_INSTALLER,
                severity = Severity.LOW,
                subject = app.packageName,
                subjectLabel = app.label,
                evidence = listOf(installer ?: "keine Quelle hinterlegt"),
            )
        )
    }
}

/**
 * D06 und D07 -- wie die App gebaut wurde.
 *
 * D06: targetSdk unter 23 heißt, die App läuft noch im alten Modell, in dem
 * alle Berechtigungen bei der Installation pauschal erteilt werden. Damit
 * greift das Laufzeit-Berechtigungssystem gar nicht erst.
 *
 * D07: eine ausgelieferte App mit debuggable-Flag lässt jeden mit
 * ADB-Zugang einen Debugger anhängen und ihre Daten auslesen.
 */
object BuildFlagsDetector : AppDetector {

    /** Ab Android 6 (API 23) gibt es Laufzeit-Berechtigungen. */
    private const val RUNTIME_PERMISSIONS_SDK = 23

    override fun inspect(app: InstalledApp, ctx: DetectorContext): List<Finding> {
        if (app.isSystemApp) return emptyList()
        val findings = mutableListOf<Finding>()

        if (app.targetSdk < RUNTIME_PERMISSIONS_SDK) {
            findings += Finding(
                code = FindingCode.APP_ANCIENT_TARGET_SDK,
                severity = Severity.MEDIUM,
                subject = app.packageName,
                subjectLabel = app.label,
                evidence = listOf("targetSdk ${app.targetSdk}"),
            )
        }

        if (app.isDebuggable) {
            findings += Finding(
                code = FindingCode.APP_DEBUGGABLE,
                severity = Severity.HIGH,
                subject = app.packageName,
                subjectLabel = app.label,
                evidence = listOf("android:debuggable=\"true\""),
            )
        }

        return findings.toList()
    }
}
