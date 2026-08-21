package com.selfguard.app.scan.apps

/**
 * Woher eine App stammt -- in Worten, die auch ohne Vorwissen stimmen.
 *
 * Anlass war eine Fehlbezeichnung in der Oberflaeche: Die App-Liste nannte
 * ihren Filter "Selbst installiert", zeigte darunter aber schlicht alles, was
 * keine System-App ist. Darin landeten Dinge wie der Galaxy Watch5 Manager
 * oder Samsungs Sprachpakete -- vom Hersteller nachgeladen, nie von Hand
 * installiert. Wer daraufhin nach unbekannten Namen sucht, bekommt Angst vor
 * seinem eigenen Telefon.
 *
 * "Nicht ab Werk" und "von Hand installiert" sind zwei verschiedene Dinge.
 * Diese Datei trennt sie.
 */
object InstallOrigin {

    /**
     * Ein bekannter Weg, auf dem Apps auf das Geraet kommen.
     *
     * @param trusted Ob ein gepflegter Katalog dahintersteht. Aus einem
     *   solchen kommt keine Ueberwachungssoftware -- die wird von Hand
     *   aufgespielt. Die Detektoren nutzen das, um Fehlalarme zu vermeiden.
     */
    data class Source(
        val packageName: String,
        val label: String,
        val trusted: Boolean,
    )

    private val KNOWN: List<Source> = listOf(
        Source("com.android.vending", "Play Store", trusted = true),
        Source("com.google.android.feedback", "Play Store", trusted = true),
        // Der Galaxy Store heisst seit jeher com.sec.android.app.samsungapps.
        // Die frueher hier eingetragene Schreibweise com.samsung.android.galaxyapps
        // gibt es auf keinem Geraet -- dadurch galt jede ueber den Galaxy Store
        // nachgeladene Samsung-App als App unbekannter Herkunft, und
        // Begleit-Apps ohne Startsymbol wie der Galaxy Watch5 Manager wurden
        // als versteckt laufend gemeldet. Beide Schreibweisen bleiben stehen,
        // damit aeltere Geraete nicht durchs Raster fallen.
        Source("com.sec.android.app.samsungapps", "Galaxy Store", trusted = true),
        Source("com.samsung.android.galaxyapps", "Galaxy Store", trusted = true),
        Source("com.sec.android.easyMover", "Smart Switch", trusted = true),
        Source("com.huawei.appmarket", "AppGallery", trusted = true),
        Source("com.amazon.venezia", "Amazon Appstore", trusted = true),
        Source("org.fdroid.fdroid", "F-Droid", trusted = true),
        Source("com.aurora.store", "Aurora Store", trusted = true),
        Source("com.selfstore.app", "SelfStore", trusted = true),
        Source("com.android.packageinstaller", "Von Hand installiert", trusted = true),
        Source("com.google.android.packageinstaller", "Von Hand installiert", trusted = true),
        Source("com.miui.packageinstaller", "Von Hand installiert", trusted = true),
    )

    private val BY_PACKAGE: Map<String, Source> = KNOWN.associateBy { it.packageName }

    /** Installationsquellen, die als regulaer gelten. */
    val TRUSTED: Set<String> = KNOWN.filter { it.trusted }.map { it.packageName }.toSet()

    /**
     * Eine Zeile Klartext zur Herkunft -- das, was in der Liste unter dem
     * Namen steht.
     *
     * Die Reihenfolge der Faelle ist wichtig: Eine aktualisierte System-App
     * traegt oft den Play Store als Quelle, gehoert aber trotzdem zum Geraet.
     * Wer das nicht zuerst prueft, erklaert dem Nutzer eine vorinstallierte
     * App zur selbst geladenen.
     */
    fun describe(app: InstalledApp): String = when {
        app.isUpdatedSystemApp -> "Ab Werk dabei, seitdem aktualisiert"
        app.isSystemApp -> "Ab Werk vorinstalliert"
        else -> BY_PACKAGE[app.installerPackage]?.label
            ?: app.installerPackage?.let { "Installiert von $it" }
            ?: "Herkunft nicht hinterlegt"
    }
}
