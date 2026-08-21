package com.selfguard.app.scan.apps

import com.selfguard.app.core.Finding

/**
 * Zusatzwissen, das ein Detektor braucht, aber nicht selbst beschaffen kann.
 * Unveränderlich und ohne Android-Bezug -- damit bleiben alle Detektoren
 * als reine Funktionen testbar.
 */
data class DetectorContext(
    /** Installationsquellen, die als regulär gelten. */
    val trustedInstallers: Set<String> = DEFAULT_TRUSTED_INSTALLERS,
    /** Paketnamen bekannter Stalkerware (Echap-Datensatz, siehe Modul IOC). */
    val stalkerwarePackages: Set<String> = emptySet(),
    /** SHA-1-Fingerabdrücke der Signaturzertifikate bekannter Stalkerware. */
    val stalkerwareCertSha1: Set<String> = emptySet(),
) {
    companion object {
        /**
         * Gepflegt in [InstallOrigin] -- dieselbe Liste, aus der die
         * Oberfläche die Herkunft in Worte fasst. Zwei getrennte Listen
         * wären zwei Listen, die auseinanderlaufen.
         */
        val DEFAULT_TRUSTED_INSTALLERS: Set<String> = InstallOrigin.TRUSTED
    }
}

/**
 * Eine einzelne Prüfung auf einer installierten App.
 *
 * Rückgabe ist eine Liste, weil ein Detektor auch mehrere Befunde melden
 * darf -- und eine leere Liste, wenn nichts auffällt.
 */
fun interface AppDetector {
    fun inspect(app: InstalledApp, ctx: DetectorContext): List<Finding>
}

/** Alle App-Detektoren an einer Stelle. */
object AppDetectors {

    val ALL: List<AppDetector> = listOf(
        DebugCertDetector,
        InstallerDetector,
        HiddenAppDetector,
        SpyPermissionDetector,
        StalkerwareDetector,
        BuildFlagsDetector,
        BrandImpersonationDetector,
    )

    /** Lässt alle Detektoren auf eine App los und sammelt die Befunde ein. */
    fun inspect(app: InstalledApp, ctx: DetectorContext = DetectorContext()): List<Finding> =
        ALL.flatMap { it.inspect(app, ctx) }
}
