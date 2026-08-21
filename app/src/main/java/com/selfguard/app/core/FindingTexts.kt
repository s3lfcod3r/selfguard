package com.selfguard.app.core

/** Titel, Erklaerung und Handlungsempfehlung zu einem Befund. */
data class FindingText(
    val title: String,
    val detail: String,
    val advice: String,
)

/**
 * Klartext zu jedem Befund, zweisprachig.
 *
 * Getrennt von den Detektoren gehalten -- die bleiben dadurch frei von
 * Uebersetzungen und ohne Android-Abhaengigkeit testbar.
 *
 * Leitlinie fuer die Texte: erst was gefunden wurde, dann warum das zaehlt,
 * dann was zu tun ist. Kein Fachjargon ohne Erklaerung, keine Panikmache.
 */
object FindingTexts {

    fun of(code: FindingCode, lang: String = "de"): FindingText =
        if (lang == "en") ENGLISH.getValue(code) else GERMAN.getValue(code)

    private val GERMAN: Map<FindingCode, FindingText> = mapOf(
        FindingCode.APP_DEBUG_CERT to FindingText(
            title = "Mit dem Standard-Testschluessel signiert",
            detail = "Diese App wurde mit dem Android-Debug-Schluessel unterschrieben. " +
                "Der liegt jedem Entwicklungswerkzeug bei und ist weltweit derselbe. " +
                "Dadurch koennte jeder eine eigene Fassung als vermeintliches Update " +
                "unterschieben -- die Signaturpruefung wuerde aufgehen.",
            advice = "Bei selbst gebauten Apps normal. Stammt sie von jemand anderem, " +
                "beim Anbieter nach einer regulaer signierten Fassung fragen.",
        ),
        FindingCode.APP_UNKNOWN_INSTALLER to FindingText(
            title = "Herkunft nicht nachvollziehbar",
            detail = "Zu dieser App ist keine bekannte Installationsquelle hinterlegt. " +
                "Sie kam also weder aus dem Play Store noch aus einem der eingetragenen " +
                "Kataloge.",
            advice = "Fuer sich genommen unbedenklich -- bei selbst installierten Apps " +
                "ist das der Normalfall. Erst zusammen mit anderen Auffaelligkeiten " +
                "aussagekraeftig.",
        ),
        FindingCode.APP_HIDDEN_NO_LAUNCHER to FindingText(
            title = "Laeuft im Hintergrund, zeigt sich aber nicht",
            detail = "Die App hat kein Symbol im Startbildschirm, betreibt aber " +
                "Hintergrunddienste. Genau so verhalten sich Ueberwachungs-Apps, die " +
                "der Betroffene nicht bemerken soll.",
            advice = "Nachsehen, ob die App bewusst installiert wurde. Manche " +
                "Zusatzmodule und Tastaturen verhalten sich ebenfalls so -- ist Ihnen " +
                "der Name unbekannt, unter Einstellungen, Apps genauer ansehen.",
        ),
        FindingCode.APP_SPY_PERMISSIONS to FindingText(
            title = "Ungewoehnlich breiter Zugriff",
            detail = "Diese App vereint mehrere voneinander unabhaengige " +
                "Ueberwachungsmoeglichkeiten -- etwa Nachrichten, Standort, Mikrofon " +
                "und Anrufliste zugleich. Einzeln ist jede davon erklaerbar, in dieser " +
                "Breite selten.",
            advice = "Ueberlegen, ob die App diese Faehigkeiten fuer ihren Zweck " +
                "wirklich braucht. Tippen Sie eine Zeile an -- dort steht, was ohne " +
                "sie nicht mehr geht, und der Knopf fuehrt direkt auf die passende " +
                "Systemeinstellung. Abschalten muessen Sie selbst: Android laesst " +
                "keine App die Rechte einer anderen aendern.",
        ),
        FindingCode.APP_KNOWN_STALKERWARE to FindingText(
            title = "Bekannte Ueberwachungssoftware",
            detail = "Diese App steht in einem gepflegten Verzeichnis von Stalkerware -- " +
                "Programmen, die heimlich Standort, Nachrichten und Aktivitaeten an " +
                "Dritte weitergeben. Das ist kein Verdacht, sondern ein belegter Treffer.",
            advice = "Vorsicht beim Entfernen: Wurde die App von einer Person aus Ihrem " +
                "Umfeld installiert, kann das Verschwinden auffallen. Wenn Sie sich " +
                "bedroht fuehlen, holen Sie sich zuerst Rat -- etwa beim Hilfetelefon " +
                "Gewalt gegen Frauen unter 116 016.",
        ),
        FindingCode.APP_ANCIENT_TARGET_SDK to FindingText(
            title = "Auf eine veraltete Android-Fassung ausgelegt",
            detail = "Die App ist fuer Android 5 oder aelter gebaut. Damit gilt fuer sie " +
                "noch das alte Modell, in dem alle Berechtigungen pauschal bei der " +
                "Installation erteilt werden -- einzeln entziehen laesst sich dann nichts.",
            advice = "Nach einer aktuellen Fassung suchen. Gibt es keine, ist die App " +
                "vermutlich seit Jahren ohne Pflege.",
        ),
        FindingCode.APP_DEBUGGABLE to FindingText(
            title = "Im Testmodus ausgeliefert",
            detail = "Die App traegt das debuggable-Kennzeichen. Wer Zugriff auf das " +
                "Geraet per ADB hat, kann sich in die laufende App einklinken und ihre " +
                "gespeicherten Daten auslesen -- auch Zugangsdaten.",
            advice = "So sollte keine fertige App ausgeliefert werden. Beim Anbieter " +
                "nachfragen oder auf eine regulaere Fassung wechseln.",
        ),
        FindingCode.APP_BRAND_IMPERSONATION to FindingText(
            title = "Gibt sich als bekannte App aus",
            detail = "Der angezeigte Name gehoert zu einem bekannten Anbieter, der " +
                "technische Paketname aber nicht. Anzeigenamen sind frei waehlbar -- " +
                "eindeutig ist nur der Paketname. Das ist der uebliche Weg, gefaelschte " +
                "Banking- und Messenger-Apps unterzuschieben.",
            advice = "Nicht oeffnen und keine Zugangsdaten eingeben. App entfernen und " +
                "die echte Fassung aus einer bekannten Quelle installieren.",
        ),
        FindingCode.APP_KNOWN_MALWARE to FindingText(
            title = "Als Schadsoftware bekannt",
            detail = "Die Programmdatei dieser App wurde gelesen und ihre " +
                "Pruefsumme in einer Schadsoftware-Datenbank gefunden. Das ist " +
                "kein Verdacht und keine Schaetzung, sondern ein eindeutiger " +
                "Treffer auf genau diese Datei.",
            advice = "Diese App entfernen. Danach Passwoerter aendern, die Sie " +
                "auf dem Geraet benutzt haben -- Schadsoftware liest sie " +
                "haeufig mit.",
        ),
        FindingCode.SYS_USER_CA_CERT to FindingText(
            title = "Nachtraeglich eingerichtetes Wurzelzertifikat",
            detail = "Auf dem Geraet liegt ein Zertifikat, das nicht ab Werk dabei war. " +
                "Wer ein solches Zertifikat einrichtet, kann verschluesselten Verkehr " +
                "aufbrechen und mitlesen. In Firmennetzen ist das gewollt, sonst " +
                "praktisch nie.",
            advice = "Unter Einstellungen, Sicherheit, Verschluesselung, " +
                "Vertrauenswuerdige Anmeldedaten, Nutzer nachsehen. Kennen Sie den " +
                "Aussteller nicht, entfernen.",
        ),
        FindingCode.SYS_ACCESSIBILITY_SERVICE to FindingText(
            title = "Bedienungshilfe mit Vollzugriff aktiv",
            detail = "Ein aktiver Bedienungshilfen-Dienst sieht alles auf dem Bildschirm " +
                "und kann Eingaben mitlesen und ausloesen -- einschliesslich Passwoertern. " +
                "Passwortverwaltungen und Automatisierungswerkzeuge nutzen das legitim, " +
                "Schadsoftware ebenso.",
            advice = "Unter Einstellungen, Bedienungshilfen pruefen, ob Ihnen jeder " +
                "Eintrag bekannt ist. Unbekanntes abschalten.",
        ),
        FindingCode.SYS_DEVICE_ADMIN to FindingText(
            title = "App mit Geraeteverwalter-Rechten",
            detail = "Diese App darf das Geraet sperren, Daten loeschen und " +
                "Passwortregeln erzwingen. Solange die Rechte aktiv sind, laesst sie " +
                "sich nicht normal deinstallieren -- ein beliebter Trick, um sich " +
                "festzusetzen.",
            advice = "Unter Einstellungen, Sicherheit, Geraeteverwaltungs-Apps pruefen. " +
                "Rechte entziehen, dann laesst sich die App entfernen.",
        ),
        FindingCode.SYS_NOTIFICATION_LISTENER to FindingText(
            title = "App liest alle Benachrichtigungen mit",
            detail = "Diese App bekommt jede eingehende Benachrichtigung im Wortlaut -- " +
                "auch Nachrichten und Einmalkennwoerter von Banken.",
            advice = "Unter Einstellungen, Benachrichtigungen, Benachrichtigungszugriff " +
                "pruefen und Unbekanntes entziehen.",
        ),
        FindingCode.SYS_DEVELOPER_OPTIONS to FindingText(
            title = "Entwickleroptionen aktiv",
            detail = "Die Entwickleroptionen sind eingeschaltet. Ist zusaetzlich das " +
                "USB-Debugging aktiv, kann jeder mit Kabelzugriff weitreichend auf das " +
                "Geraet zugreifen.",
            advice = "Wenn Sie sie nicht brauchen: Einstellungen, System, " +
                "Entwickleroptionen ausschalten.",
        ),
        FindingCode.SYS_ROOTED to FindingText(
            title = "Geraet ist gerootet",
            detail = "Es wurden Anzeichen fuer Root-Zugriff gefunden. Damit ist die " +
                "Trennung zwischen den Apps aufgehoben -- eine App kann dann auf die " +
                "Daten jeder anderen zugreifen. Diese Sicherheits-App eingeschlossen.",
            advice = "Wenn Sie das Geraet bewusst gerootet haben, ist das erwartbar. " +
                "Andernfalls sollten Sie es ernst nehmen.",
        ),
        FindingCode.SYS_INTEGRITY to FindingText(
            title = "Geraeteintegritaet nicht belegbar",
            detail = "Das Geraet konnte seine Unversehrtheit nicht bestaetigen. Das " +
                "passiert bei entsperrtem Bootloader, eigener Firmware oder fehlenden " +
                "Google-Diensten -- und ebenso bei einem manipulierten System.",
            advice = "Bei einem Geraet mit eigener Firmware erwartbar. Bei einem " +
                "unveraenderten Geraet ab Werk waere es ein ernstes Zeichen.",
        ),
        FindingCode.SYS_UNKNOWN_SOURCES to FindingText(
            title = "App darf weitere Apps installieren",
            detail = "Diese App darf Pakete aus eigener Quelle installieren. Bei einem " +
                "App-Katalog oder Dateiverwalter ist das notwendig -- bei allem anderen " +
                "ein Weg, unbemerkt nachzuladen.",
            advice = "Unter Einstellungen, Apps, Spezieller Zugriff, Unbekannte Apps " +
                "installieren pruefen und Unnoetiges entziehen.",
        ),
        FindingCode.SYS_NO_SCREEN_LOCK to FindingText(
            title = "Keine Bildschirmsperre eingerichtet",
            detail = "Ohne Sperre kommt jeder, der das Geraet kurz in die Hand bekommt, " +
                "an alle Daten -- und kann in Minuten Ueberwachungssoftware einrichten. " +
                "Ohne Sperre ist zudem die Geraeteverschluesselung wirkungslos.",
            advice = "Einstellungen, Sicherheit, Displaysperre. PIN mit mindestens " +
                "sechs Stellen, besser ein Kennwort.",
        ),
        FindingCode.FILE_KNOWN_MALWARE to FindingText(
            title = "Datei als Schadsoftware bekannt",
            detail = "Die Pruefsumme dieser Datei steht in einer Schadsoftware-Datenbank. " +
                "Das ist ein eindeutiger Treffer, keine Schaetzung.",
            advice = "Datei nicht oeffnen und nicht weitergeben. Loeschen Sie sie ueber " +
                "Ihren Dateiverwalter.",
        ),
    )

    private val ENGLISH: Map<FindingCode, FindingText> = mapOf(
        FindingCode.APP_DEBUG_CERT to FindingText(
            title = "Signed with the default test key",
            detail = "This app is signed with the Android debug key, which ships with " +
                "every development toolchain and is identical worldwide. Anyone could " +
                "push a modified build as an update and the signature check would pass.",
            advice = "Normal for self-built apps. If it came from someone else, ask for " +
                "a properly signed release.",
        ),
        FindingCode.APP_UNKNOWN_INSTALLER to FindingText(
            title = "Origin cannot be traced",
            detail = "No known install source is recorded for this app, so it came " +
                "neither from the Play Store nor from any registered catalogue.",
            advice = "Harmless on its own -- normal for manually installed apps. Only " +
                "meaningful combined with other findings.",
        ),
        FindingCode.APP_HIDDEN_NO_LAUNCHER to FindingText(
            title = "Runs in the background without showing itself",
            detail = "The app has no launcher icon but runs background services. This is " +
                "exactly how monitoring apps behave when the person being monitored is " +
                "not supposed to notice.",
            advice = "Check whether you installed it deliberately. Some plugins and " +
                "keyboards behave the same way -- if the name is unfamiliar, inspect it " +
                "under Settings, Apps.",
        ),
        FindingCode.APP_SPY_PERMISSIONS to FindingText(
            title = "Unusually broad access",
            detail = "This app combines several independent surveillance capabilities at " +
                "once -- messages, location, microphone and call log, for example. Each " +
                "is explainable alone; this breadth rarely is.",
            advice = "Consider whether the app really needs all of this. Tap a line " +
                "to see what stops working without it -- the button takes you " +
                "straight to the matching system setting. You revoke it yourself: " +
                "Android lets no app change the permissions of another.",
        ),
        FindingCode.APP_KNOWN_STALKERWARE to FindingText(
            title = "Known monitoring software",
            detail = "This app appears in a maintained registry of stalkerware -- " +
                "programs that secretly forward location, messages and activity to a " +
                "third party. This is a confirmed match, not a guess.",
            advice = "Be careful about removing it: if someone close to you installed " +
                "it, its disappearance may be noticed. If you feel unsafe, seek advice " +
                "first.",
        ),
        FindingCode.APP_ANCIENT_TARGET_SDK to FindingText(
            title = "Built for an outdated Android version",
            detail = "The app targets Android 5 or older, so the old model applies: all " +
                "permissions are granted at install time and cannot be revoked " +
                "individually.",
            advice = "Look for a current version. If none exists, the app has likely " +
                "been unmaintained for years.",
        ),
        FindingCode.APP_DEBUGGABLE to FindingText(
            title = "Shipped in debug mode",
            detail = "The app carries the debuggable flag. Anyone with ADB access can " +
                "attach to the running app and read its stored data, including " +
                "credentials.",
            advice = "No finished app should ship this way. Ask the vendor or switch to " +
                "a proper release build.",
        ),
        FindingCode.APP_BRAND_IMPERSONATION to FindingText(
            title = "Impersonates a well-known app",
            detail = "The display name belongs to a well-known vendor but the package " +
                "name does not. Display names are freely chosen; only the package name " +
                "is unique. This is the standard way fake banking and messenger apps are " +
                "slipped in.",
            advice = "Do not open it and do not enter credentials. Remove it and install " +
                "the genuine version from a known source.",
        ),
        FindingCode.APP_KNOWN_MALWARE to FindingText(
            title = "Known malware",
            detail = "This app's program file was read and its checksum found " +
                "in a malware database. That is a definite match on this exact " +
                "file, not a guess.",
            advice = "Remove this app. Then change any passwords you used on " +
                "this device -- malware commonly captures them.",
        ),
        FindingCode.SYS_USER_CA_CERT to FindingText(
            title = "User-installed root certificate",
            detail = "A certificate is present that did not ship with the device. " +
                "Whoever installed it can break open and read encrypted traffic. " +
                "Intentional in corporate networks, almost never otherwise.",
            advice = "Check Settings, Security, Encryption, Trusted credentials, User. " +
                "Remove anything whose issuer you do not recognise.",
        ),
        FindingCode.SYS_ACCESSIBILITY_SERVICE to FindingText(
            title = "Accessibility service with full access is active",
            detail = "An active accessibility service sees everything on screen and can " +
                "read and inject input, including passwords. Password managers and " +
                "automation tools use this legitimately -- so does malware.",
            advice = "Check Settings, Accessibility and turn off anything you do not " +
                "recognise.",
        ),
        FindingCode.SYS_DEVICE_ADMIN to FindingText(
            title = "App holds device administrator rights",
            detail = "This app may lock the device, wipe data and enforce password " +
                "rules. While those rights are active it cannot be uninstalled normally " +
                "-- a common trick for persistence.",
            advice = "Check Settings, Security, Device admin apps. Revoke the rights, " +
                "then uninstall.",
        ),
        FindingCode.SYS_NOTIFICATION_LISTENER to FindingText(
            title = "App reads all notifications",
            detail = "This app receives every incoming notification verbatim, including " +
                "messages and one-time banking codes.",
            advice = "Check Settings, Notifications, Notification access and revoke " +
                "anything unfamiliar.",
        ),
        FindingCode.SYS_DEVELOPER_OPTIONS to FindingText(
            title = "Developer options enabled",
            detail = "Developer options are on. If USB debugging is also active, anyone " +
                "with cable access has far-reaching control over the device.",
            advice = "If you do not need them, turn them off under Settings, System, " +
                "Developer options.",
        ),
        FindingCode.SYS_ROOTED to FindingText(
            title = "Device is rooted",
            detail = "Signs of root access were found. The separation between apps no " +
                "longer holds -- any app can reach any other app's data, including this " +
                "one's.",
            advice = "Expected if you rooted the device deliberately. Otherwise take it " +
                "seriously.",
        ),
        FindingCode.SYS_INTEGRITY to FindingText(
            title = "Device integrity cannot be confirmed",
            detail = "The device could not attest its integrity. This happens with an " +
                "unlocked bootloader, custom firmware or missing Google services -- and " +
                "equally with a tampered system.",
            advice = "Expected on a device with custom firmware. On an untouched stock " +
                "device it would be a serious sign.",
        ),
        FindingCode.SYS_UNKNOWN_SOURCES to FindingText(
            title = "App may install other apps",
            detail = "This app is allowed to install packages. Necessary for an app " +
                "catalogue or file manager -- for anything else, a way to load code " +
                "unnoticed.",
            advice = "Check Settings, Apps, Special access, Install unknown apps and " +
                "revoke what is not needed.",
        ),
        FindingCode.SYS_NO_SCREEN_LOCK to FindingText(
            title = "No screen lock configured",
            detail = "Without a lock, anyone who briefly holds the device reaches all " +
                "data and can install monitoring software in minutes. Device encryption " +
                "is also ineffective without one.",
            advice = "Settings, Security, Screen lock. Use a PIN of at least six digits, " +
                "preferably a password.",
        ),
        FindingCode.FILE_KNOWN_MALWARE to FindingText(
            title = "File is known malware",
            detail = "This file's checksum appears in a malware database. That is a " +
                "definite match, not an estimate.",
            advice = "Do not open or share it. Delete it using your file manager.",
        ),
    )
}
