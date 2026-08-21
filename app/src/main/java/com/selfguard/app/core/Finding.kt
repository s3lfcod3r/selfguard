package com.selfguard.app.core

/**
 * Schweregrad eines Befundes. Bewusst fuenfstufig: ein Scanner, der alles rot
 * faerbt, wird nach zwei Tagen ignoriert. Das Gewicht fliesst in die
 * Risiko-Punktzahl ein (siehe [RiskScore]).
 */
enum class Severity(val weight: Int) {
    /** Reine Information, kein Handlungsbedarf. */
    INFO(0),

    /** Auffaellig, aber fuer sich genommen harmlos. */
    LOW(5),

    /** Sollte man sich anschauen. */
    MEDIUM(15),

    /** Deutliches Warnzeichen. */
    HIGH(35),

    /** Belegter Treffer gegen bekannte Schadsoftware oder aktiver Mitleseweg. */
    CRITICAL(70),
}

/**
 * Katalog aller Pruefungen. Die ID entspricht dem Plan (docs/PLAN.md), damit
 * Befund und Dokumentation zusammenpassen. Texte liegen getrennt in
 * [FindingTexts] -- so bleiben die Detektoren frei von Uebersetzungen und
 * damit ohne Android-Abhaengigkeit testbar.
 */
enum class FindingCode(val id: String) {
    // -- App-Audit ------------------------------------------------------
    /** Mit dem oeffentlichen Android-Debug-Schluessel signiert. */
    APP_DEBUG_CERT("D01"),

    /** Herkunft unbekannt bzw. nicht aus einer erwarteten Quelle. */
    APP_UNKNOWN_INSTALLER("D02"),

    /** Kein Starter-Symbol, laeuft aber im Hintergrund. */
    APP_HIDDEN_NO_LAUNCHER("D03"),

    /** Berechtigungskombination mit Ueberwachungsprofil. */
    APP_SPY_PERMISSIONS("D04"),

    /** Treffer gegen die Stalkerware-Indikatoren von Echap. */
    APP_KNOWN_STALKERWARE("D05"),

    /** targetSdk unter 23 -- umgeht das Laufzeit-Berechtigungsmodell. */
    APP_ANCIENT_TARGET_SDK("D06"),

    /** debuggable-Flag in einer ausgelieferten App. */
    APP_DEBUGGABLE("D07"),

    /** Gibt sich als bekannte Marke aus, gehoert aber nicht dazu. */
    APP_BRAND_IMPERSONATION("D08"),

    /** Die APK-Datei selbst steht in einer Schadsoftware-Datenbank. */
    APP_KNOWN_MALWARE("D09"),

    // -- Systemcheck ----------------------------------------------------
    /** Nachtraeglich installiertes Wurzelzertifikat -- Mitlesen moeglich. */
    SYS_USER_CA_CERT("S01"),

    /** Aktiver Bedienungshilfen-Dienst mit Vollzugriff auf den Bildschirm. */
    SYS_ACCESSIBILITY_SERVICE("S02"),

    /** App mit Geraeteverwalter-Rechten. */
    SYS_DEVICE_ADMIN("S03"),

    /** App liest alle Benachrichtigungen mit. */
    SYS_NOTIFICATION_LISTENER("S04"),

    /** Entwickleroptionen oder ADB aktiv. */
    SYS_DEVELOPER_OPTIONS("S05"),

    /** Geraet ist gerootet. */
    SYS_ROOTED("S06"),

    /** Bootloader entsperrt / Integritaet nicht belegbar. */
    SYS_INTEGRITY("S07"),

    /** App darf fremde Pakete installieren. */
    SYS_UNKNOWN_SOURCES("S08"),

    /** Keine Bildschirmsperre gesetzt. */
    SYS_NO_SCREEN_LOCK("S09"),

    // -- Dateiscan ------------------------------------------------------
    /** Datei-Hash in einer Schadsoftware-Datenbank gefunden. */
    FILE_KNOWN_MALWARE("F01"),
}

/**
 * Ein einzelner Befund. Unveraenderlich -- Detektoren geben neue Objekte
 * zurueck, statt bestehende zu aendern.
 *
 * @param subject technischer Bezug (Paketname, Dateipfad, Zertifikats-Alias)
 * @param subjectLabel Klartextname fuer die Anzeige
 * @param evidence konkrete Belege, die den Befund nachvollziehbar machen
 * @param capabilities Faehigkeiten, die sich abstellen lassen. Nur D04 fuellt
 *   das. Getrennt von [evidence], weil hier nicht nur ein Text steht, sondern
 *   auch das Ziel in den Systemeinstellungen und die Folgen des Entziehens --
 *   die Oberflaeche macht daraus antippbare Zeilen statt einer Aufzaehlung.
 */
data class Finding(
    val code: FindingCode,
    val severity: Severity,
    val subject: String,
    val subjectLabel: String,
    val evidence: List<String> = emptyList(),
    val capabilities: List<Capability> = emptyList(),
)
