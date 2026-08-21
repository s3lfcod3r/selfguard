package com.selfguard.app.core

/**
 * Wohin fuehrt der Weg, um eine Faehigkeit wieder loszuwerden?
 *
 * Android kennt kein einheitliches Ziel: Laufzeitberechtigungen liegen auf der
 * Berechtigungsseite der App, das Ueberlagern des Bildschirms und das
 * Installieren fremder Pakete jeweils auf einer eigenen Sonderseite, und
 * Bedienungshilfen bzw. Benachrichtigungszugriff in einer geraeteweiten Liste.
 * Wer den Nutzer auf die falsche Seite schickt, hat ihm nicht geholfen --
 * deshalb wird das Ziel hier je Faehigkeit festgehalten.
 */
enum class SettingsTarget {
    /** Berechtigungsseite der App. */
    APP_PERMISSIONS,

    /** Sonderzugriff "Ueber anderen Apps anzeigen". */
    OVERLAY,

    /** Geraeteweite Liste der Bedienungshilfen. */
    ACCESSIBILITY,

    /** Geraeteweite Liste des Benachrichtigungszugriffs. */
    NOTIFICATION_LISTENER,

    /** Sonderzugriff "Unbekannte Apps installieren". */
    INSTALL_UNKNOWN_APPS,

    /** Sonderzugriff "Nutzungsdaten". */
    USAGE_ACCESS,

    /** Sonderzugriff "Alle Dateien verwalten". */
    ALL_FILES,

    /** Liste der Geraeteverwaltungs-Apps. */
    DEVICE_ADMIN,

    /** App-Info -- letzter Ausweg, wenn es keine feinere Seite gibt. */
    APP_DETAILS,
}

/**
 * Eine einzelne Ueberwachungsfaehigkeit einer App.
 *
 * Frueher waren das blosse Zeichenketten in [Finding.evidence]. Als Text
 * liessen sie sich anzeigen, aber nichts damit anfangen -- der Nutzer las
 * "Mikrofon aufzeichnen" und musste sich selbst durch die Einstellungen
 * suchen. Als Aufzaehlung tragen sie jetzt mit, wo man sie abstellt und was
 * danach nicht mehr geht.
 *
 * **Wichtig:** SelfGuard entzieht nichts selbst. Android laesst keine App die
 * Berechtigungen einer anderen aendern (`REVOKE_RUNTIME_PERMISSIONS` ist der
 * Systemsignatur vorbehalten) -- und das ist richtig so, sonst koennte
 * Schadsoftware genau diesen Weg nutzen, um Schutzprogramme zu entwaffnen.
 * SelfGuard oeffnet nur die passende Systemseite; tippen muss der Nutzer.
 *
 * Die Reihenfolge der Eintraege ist die Anzeigereihenfolge: erst was
 * mitliest, dann was zugreift, dann die Sonderrechte.
 *
 * @param permissions Berechtigungen, die diese Faehigkeit belegen. Innerhalb
 *   einer Faehigkeit zaehlt ein Treffer genau einmal -- READ_SMS und
 *   RECEIVE_SMS sind dasselbe Koennen.
 * @param revocable Ob Android das ueberhaupt einzeln entziehen laesst.
 *   Normale Berechtigungen wie RECEIVE_BOOT_COMPLETED werden bei der
 *   Installation vergeben und bleiben; einen Schalter dafuer gibt es nicht.
 *   Das ehrlich zu sagen ist besser, als den Nutzer auf eine Seite zu
 *   schicken, auf der er nichts findet.
 */
enum class Capability(
    val permissions: Set<String> = emptySet(),
    val target: SettingsTarget,
    val revocable: Boolean = true,
) {
    MESSAGES(
        permissions = setOf(
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.RECEIVE_MMS",
        ),
        target = SettingsTarget.APP_PERMISSIONS,
    ),
    CALL_LOG(
        permissions = setOf(
            "android.permission.READ_CALL_LOG",
            "android.permission.PROCESS_OUTGOING_CALLS",
            "android.permission.ANSWER_PHONE_CALLS",
        ),
        target = SettingsTarget.APP_PERMISSIONS,
    ),
    MICROPHONE(
        permissions = setOf("android.permission.RECORD_AUDIO"),
        target = SettingsTarget.APP_PERMISSIONS,
    ),
    CAMERA(
        permissions = setOf("android.permission.CAMERA"),
        target = SettingsTarget.APP_PERMISSIONS,
    ),
    LOCATION_BACKGROUND(
        permissions = setOf("android.permission.ACCESS_BACKGROUND_LOCATION"),
        target = SettingsTarget.APP_PERMISSIONS,
    ),
    LOCATION(
        permissions = setOf("android.permission.ACCESS_FINE_LOCATION"),
        target = SettingsTarget.APP_PERMISSIONS,
    ),
    CONTACTS(
        permissions = setOf("android.permission.READ_CONTACTS"),
        target = SettingsTarget.APP_PERMISSIONS,
    ),
    OVERLAY(
        permissions = setOf("android.permission.SYSTEM_ALERT_WINDOW"),
        target = SettingsTarget.OVERLAY,
    ),
    AUTOSTART(
        permissions = setOf("android.permission.RECEIVE_BOOT_COMPLETED"),
        target = SettingsTarget.APP_DETAILS,
        revocable = false,
    ),
    APP_LIST(
        permissions = setOf("android.permission.QUERY_ALL_PACKAGES"),
        target = SettingsTarget.APP_DETAILS,
        revocable = false,
    ),
    INSTALL_PACKAGES(
        permissions = setOf("android.permission.REQUEST_INSTALL_PACKAGES"),
        target = SettingsTarget.INSTALL_UNKNOWN_APPS,
    ),
    USAGE_STATS(
        permissions = setOf("android.permission.PACKAGE_USAGE_STATS"),
        target = SettingsTarget.USAGE_ACCESS,
    ),
    ALL_FILES(
        permissions = setOf("android.permission.MANAGE_EXTERNAL_STORAGE"),
        target = SettingsTarget.ALL_FILES,
    ),

    // -- Angemeldete Dienste. Tauchen nicht in den Berechtigungen auf,
    //    wiegen aber schwerer als die meisten davon.
    ACCESSIBILITY(target = SettingsTarget.ACCESSIBILITY),
    DEVICE_ADMIN(target = SettingsTarget.DEVICE_ADMIN),
    NOTIFICATION_LISTENER(target = SettingsTarget.NOTIFICATION_LISTENER),
    ;

    companion object {
        /** Faehigkeiten, die sich allein aus den Berechtigungen ergeben. */
        val FROM_PERMISSIONS: List<Capability> = entries.filter { it.permissions.isNotEmpty() }
    }
}
