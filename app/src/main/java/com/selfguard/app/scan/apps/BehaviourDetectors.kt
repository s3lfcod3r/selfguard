package com.selfguard.app.scan.apps

import com.selfguard.app.core.Capability
import com.selfguard.app.core.Finding
import com.selfguard.app.core.FindingCode
import com.selfguard.app.core.Severity

/**
 * D03 -- laeuft, zeigt sich aber nicht.
 *
 * Der zuverlaessigste billige Hinweis auf Ueberwachungssoftware: kein Symbol
 * im Startbildschirm, aber Hintergrunddienste und Empfaenger. Genau so
 * verhalten sich Handy-Ueberwachungs-Apps, die der Ueberwachte nicht
 * bemerken soll.
 *
 * System-Apps sind ausgenommen -- dort ist das der Normalfall.
 */
object HiddenAppDetector : AppDetector {

    override fun inspect(app: InstalledApp, ctx: DetectorContext): List<Finding> {
        if (app.isSystemApp || app.hasLauncher) return emptyList()
        if (app.serviceCount == 0 && app.receiverCount == 0) return emptyList()

        // Aus einem gepflegten Katalog kommt keine Ueberwachungssoftware --
        // die wird von Hand aufgespielt. Ohne diese Einschraenkung meldete
        // SelfGuard voellig gewoehnliche Begleit-Apps wie den Galaxy Watch5
        // Manager oder Carrier Services, die legitim kein Symbol haben.
        // Genau solche Fehlalarme bringen einen Nutzer dazu, kuenftig auch
        // die echten Meldungen zu ueberblaettern.
        if (app.installerPackage in ctx.trustedInstallers) return emptyList()

        return listOf(
            Finding(
                code = FindingCode.APP_HIDDEN_NO_LAUNCHER,
                severity = Severity.HIGH,
                subject = app.packageName,
                subjectLabel = app.label,
                evidence = listOf(
                    "kein Starter-Symbol",
                    "${app.serviceCount} Dienste, ${app.receiverCount} Empfaenger",
                ),
            )
        )
    }
}

/**
 * D04 -- Berechtigungsprofil einer Ueberwachungs-App.
 *
 * Einzelne Berechtigungen sagen wenig: eine Messenger-App darf Kontakte lesen,
 * eine Kamera-App die Kamera nutzen. Aussagekraeftig wird erst die *Breite* --
 * wie viele voneinander unabhaengige Ueberwachungsfaehigkeiten eine einzelne
 * App zusammenzieht.
 *
 * System-Apps sind ausgenommen: Dienste wie die Google-Play-Dienste fordern
 * praktisch alles an und wuerden jede Schwelle sprengen.
 *
 * Gemeldet werden die Faehigkeiten als [Capability] und nicht als blosser
 * Text, damit die Oberflaeche zu jeder Zeile den Weg in die passende
 * Systemeinstellung anbieten kann. Welche Berechtigung zu welcher Faehigkeit
 * gehoert, steht dort -- dieser Detektor entscheidet nur noch ueber die
 * Schwelle.
 */
object SpyPermissionDetector : AppDetector {

    /** Ab so vielen Faehigkeiten wird es erwaehnenswert. */
    private const val THRESHOLD_NOTABLE = 5

    /** Ab so vielen ist das Profil kaum noch anders erklaerbar. */
    private const val THRESHOLD_SEVERE = 7

    /** Mit Bedienungshilfen-Zugriff reicht schon eine geringere Breite. */
    private const val THRESHOLD_WITH_ACCESSIBILITY = 4

    override fun inspect(app: InstalledApp, ctx: DetectorContext): List<Finding> {
        if (app.isSystemApp) return emptyList()

        val requested = app.requestedPermissions.toSet()
        val matched = Capability.FROM_PERMISSIONS
            .filter { capability -> capability.permissions.any { it in requested } }
            .toMutableList()

        // Angemeldete Dienste sind eigenstaendige Faehigkeiten -- sie tauchen
        // nicht in den Berechtigungen auf, wiegen aber schwerer als die meisten.
        if (app.declaresAccessibilityService) matched += Capability.ACCESSIBILITY
        if (app.declaresDeviceAdmin) matched += Capability.DEVICE_ADMIN
        if (app.declaresNotificationListener) matched += Capability.NOTIFICATION_LISTENER

        val severity = severityFor(matched.size, app.declaresAccessibilityService)
            ?: return emptyList()

        return listOf(
            Finding(
                code = FindingCode.APP_SPY_PERMISSIONS,
                severity = severity,
                subject = app.packageName,
                subjectLabel = app.label,
                capabilities = matched.sorted(),
            )
        )
    }

    /** null bedeutet: unauffaellig, kein Befund. */
    private fun severityFor(count: Int, hasAccessibility: Boolean): Severity? = when {
        count >= THRESHOLD_SEVERE -> Severity.HIGH
        hasAccessibility && count >= THRESHOLD_WITH_ACCESSIBILITY -> Severity.HIGH
        count >= THRESHOLD_NOTABLE -> Severity.MEDIUM
        else -> null
    }
}

/**
 * D05 -- Abgleich gegen die Stalkerware-Indikatoren von Echap.
 *
 * Der einzige Detektor, der ein Urteil faellt statt zu schaetzen: hier liegt
 * ein belegter Treffer gegen einen gepflegten Datensatz vor. Deshalb als
 * einziger [Severity.CRITICAL].
 *
 * Der Zertifikatsabgleich ist der wichtigere Teil -- Paketnamen werden von
 * Stalkerware-Anbietern regelmaessig gewechselt, der Signaturschluessel
 * bleibt dagegen meist derselbe.
 */
object StalkerwareDetector : AppDetector {

    override fun inspect(app: InstalledApp, ctx: DetectorContext): List<Finding> {
        val evidence = mutableListOf<String>()

        if (app.packageName.lowercase() in ctx.stalkerwarePackages) {
            evidence += "Paketname in der Stalkerware-Liste"
        }
        // Zertifikatstreffer nur bei Apps, die jemand selbst installiert hat.
        // Eine System-App gehoert zum Geraet; steht ihr Fingerabdruck in der
        // Liste, ist der Fingerabdruck das Problem und nicht die App. Ohne
        // diese Regel meldete SelfGuard auf einem gewoehnlichen Samsung die
        // Android-Komponente com.android.cts.ctsshim als Stalkerware.
        if (!app.isSystemApp) {
            // Der Datensatz fuehrt SHA-1-Fingerabdruecke, nicht SHA-256.
            app.signingSha1
                .map { it.lowercase() }
                // Geteilte AOSP-Schluessel sagen ueber die App gar nichts aus.
                .filterNot { PublicSigningKeys.isPublic(it) }
                .filter { it in ctx.stalkerwareCertSha1 }
                .forEach { evidence += "Signaturzertifikat bekannt: ${it.take(16)}..." }
        }

        if (evidence.isEmpty()) return emptyList()

        return listOf(
            Finding(
                code = FindingCode.APP_KNOWN_STALKERWARE,
                severity = Severity.CRITICAL,
                subject = app.packageName,
                subjectLabel = app.label,
                evidence = evidence.toList(),
            )
        )
    }
}
