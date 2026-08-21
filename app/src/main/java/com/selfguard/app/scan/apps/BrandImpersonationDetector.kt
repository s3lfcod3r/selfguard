package com.selfguard.app.scan.apps

import com.selfguard.app.core.Finding
import com.selfguard.app.core.FindingCode
import com.selfguard.app.core.Severity

/**
 * D08 -- traegt einen bekannten Namen, gehoert aber nicht dazu.
 *
 * Der klassische Betrugsweg bei sideloadeten Apps: eine App nennt sich
 * "WhatsApp" oder "Sparkasse", sieht auch so aus, laeuft aber unter einem
 * fremden Paketnamen. Android verhindert das nicht -- Anzeigenamen sind frei
 * waehlbar, nur der Paketname ist eindeutig.
 */
object BrandImpersonationDetector : AppDetector {

    override fun inspect(app: InstalledApp, ctx: DetectorContext): List<Finding> {
        if (app.isSystemApp) return emptyList()

        val normalized = KnownBrands.normalize(app.label)
        val official = KnownBrands.OFFICIAL[normalized] ?: return emptyList()
        if (app.packageName in official) return emptyList()

        return listOf(
            Finding(
                code = FindingCode.APP_BRAND_IMPERSONATION,
                severity = Severity.HIGH,
                subject = app.packageName,
                subjectLabel = app.label,
                evidence = listOf(
                    "nennt sich \"${app.label}\"",
                    "laeuft aber als ${app.packageName}",
                    "echt waere: ${official.joinToString(" oder ")}",
                ),
            )
        )
    }
}
