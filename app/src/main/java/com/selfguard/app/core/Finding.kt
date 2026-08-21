package com.selfguard.app.core

/**
 * Schweregrad eines Befundes. Bewusst fünfstufig: ein Scanner, der alles rot
 * färbt, wird nach zwei Tagen ignoriert. Das Gewicht fließt in die
 * Risiko-Punktzahl ein (siehe [RiskScore]).
 */
enum class Severity(val weight: Int) {
    /** Reine Information, kein Handlungsbedarf. */
    INFO(0),

    /** Auffällig, aber für sich genommen harmlos. */
    LOW(5),

    /** Sollte man sich anschauen. */
    MEDIUM(15),

    /** Deutliches Warnzeichen. */
    HIGH(35),

    /** Belegter Treffer gegen bekannte Schadsoftware oder aktiver Mitleseweg. */
    CRITICAL(70),
}

/**
 * Katalog aller Prüfungen. Die ID entspricht dem Plan (docs/PLAN.md), damit
 * Befund und Dokumentation zusammenpassen. Texte liegen getrennt in
 * [FindingTexts] -- so bleiben die Detektoren frei von Übersetzungen und
 * damit ohne Android-Abhängigkeit testbar.
 */
enum class FindingCode(val id: String) {
    // -- App-Audit ------------------------------------------------------
    /** Mit dem öffentlichen Android-Debug-Schlüssel signiert. */
    APP_DEBUG_CERT("D01"),

    /** Herkunft unbekannt bzw. nicht aus einer erwarteten Quelle. */
    APP_UNKNOWN_INSTALLER("D02"),

    /** Kein Starter-Symbol, läuft aber im Hintergrund. */
    APP_HIDDEN_NO_LAUNCHER("D03"),

    /** Berechtigungskombination mit Überwachungsprofil. */
    APP_SPY_PERMISSIONS("D04"),

    /** Treffer gegen die Stalkerware-Indikatoren von Echap. */
    APP_KNOWN_STALKERWARE("D05"),

    /** targetSdk unter 23 -- umgeht das Laufzeit-Berechtigungsmodell. */
    APP_ANCIENT_TARGET_SDK("D06"),

    /** debuggable-Flag in einer ausgelieferten App. */
    APP_DEBUGGABLE("D07"),

    /** Gibt sich als bekannte Marke aus, gehört aber nicht dazu. */
    APP_BRAND_IMPERSONATION("D08"),

    /** Die APK-Datei selbst steht in einer Schadsoftware-Datenbank. */
    APP_KNOWN_MALWARE("D09"),

    // -- Systemcheck ----------------------------------------------------
    /** Nachträglich installiertes Wurzelzertifikat -- Mitlesen möglich. */
    SYS_USER_CA_CERT("S01"),

    /** Aktiver Bedienungshilfen-Dienst mit Vollzugriff auf den Bildschirm. */
    SYS_ACCESSIBILITY_SERVICE("S02"),

    /** App mit Geräteverwalter-Rechten. */
    SYS_DEVICE_ADMIN("S03"),

    /** App liest alle Benachrichtigungen mit. */
    SYS_NOTIFICATION_LISTENER("S04"),

    /** Entwickleroptionen oder ADB aktiv. */
    SYS_DEVELOPER_OPTIONS("S05"),

    /** Gerät ist gerootet. */
    SYS_ROOTED("S06"),

    /** Bootloader entsperrt / Integrität nicht belegbar. */
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
 * Ein einzelner Befund. Unveränderlich -- Detektoren geben neue Objekte
 * zurück, statt bestehende zu ändern.
 *
 * @param subject technischer Bezug (Paketname, Dateipfad, Zertifikats-Alias)
 * @param subjectLabel Klartextname für die Anzeige
 * @param evidence konkrete Belege, die den Befund nachvollziehbar machen
 * @param capabilities Fähigkeiten, die sich abstellen lassen. Nur D04 füllt
 *   das. Getrennt von [evidence], weil hier nicht nur ein Text steht, sondern
 *   auch das Ziel in den Systemeinstellungen und die Folgen des Entziehens --
 *   die Oberfläche macht daraus antippbare Zeilen statt einer Aufzählung.
 */
data class Finding(
    val code: FindingCode,
    val severity: Severity,
    val subject: String,
    val subjectLabel: String,
    val evidence: List<String> = emptyList(),
    val capabilities: List<Capability> = emptyList(),
)
