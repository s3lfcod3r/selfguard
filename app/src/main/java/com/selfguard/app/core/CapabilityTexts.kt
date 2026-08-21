package com.selfguard.app.core

/**
 * Name einer Fähigkeit und die Folgen des Entziehens.
 *
 * @param label kurze Bezeichnung für die Liste
 * @param consequence was danach nicht mehr geht -- der eigentliche Punkt
 * @param action Beschriftung des Knopfes
 */
data class CapabilityText(
    val label: String,
    val consequence: String,
    val action: String,
)

/**
 * Klartext zu jeder [Capability], zweisprachig -- wie [FindingTexts] getrennt
 * von der Logik gehalten.
 *
 * Leitlinie für die Folgentexte: **konkret und ohne Beschönigung.** Ein
 * Sicherheitsprogramm, das zum Abschalten rät und verschweigt, dass danach
 * die Smartwatch stumm bleibt oder der Passwortmanager nicht mehr ausfüllt,
 * verliert das Vertrauen des Nutzers beim ersten Mal -- und der schaltet dann
 * alles wieder ein, auch das, was wirklich störte. Deshalb steht hier
 * ausdrücklich auch, wann eine Berechtigung berechtigt ist.
 */
object CapabilityTexts {

    fun of(capability: Capability, lang: String = "de"): CapabilityText =
        if (lang == "en") ENGLISH.getValue(capability) else GERMAN.getValue(capability)

    private const val REVOKE_DE = "Entziehen"
    private const val INSPECT_DE = "Ansehen"
    private const val REVOKE_EN = "Revoke"
    private const val INSPECT_EN = "Inspect"

    private val GERMAN: Map<Capability, CapabilityText> = mapOf(
        Capability.MESSAGES to CapabilityText(
            label = "Nachrichten mitlesen",
            consequence = "Die App bekommt danach keine SMS mehr. Bei Messengern und " +
                "Banking-Apps fällt damit das automatische Eintragen des " +
                "Bestätigungscodes weg -- den müssen Sie dann abtippen.",
            action = REVOKE_DE,
        ),
        Capability.CALL_LOG to CapabilityText(
            label = "Anrufe protokollieren",
            consequence = "Die App sieht die Anrufliste nicht mehr. Anrufer-Erkennung " +
                "und Spam-Warnung bei unbekannten Nummern funktionieren danach nicht.",
            action = REVOKE_DE,
        ),
        Capability.MICROPHONE to CapabilityText(
            label = "Mikrofon aufzeichnen",
            consequence = "Sprachnachrichten, Telefonate über die App und Diktieren " +
                "gehen danach nicht mehr.",
            action = REVOKE_DE,
        ),
        Capability.CAMERA to CapabilityText(
            label = "Kamera nutzen",
            consequence = "Fotos, Videoanrufe und das Scannen von QR-Codes gehen in " +
                "dieser App danach nicht mehr.",
            action = REVOKE_DE,
        ),
        Capability.LOCATION_BACKGROUND to CapabilityText(
            label = "Standort im Hintergrund",
            consequence = "Die App kennt den Standort nur noch, solange sie offen ist. " +
                "Automatische Ortsfunktionen entfallen -- etwa das Erkennen, dass Sie " +
                "zu Hause angekommen sind, oder das Wiederfinden eines verlegten " +
                "Geräts.",
            action = REVOKE_DE,
        ),
        Capability.LOCATION to CapabilityText(
            label = "Standort abfragen",
            consequence = "Karten, Wetter am Ort und Umkreissuche funktionieren in " +
                "dieser App danach nicht mehr.",
            action = REVOKE_DE,
        ),
        Capability.CONTACTS to CapabilityText(
            label = "Kontakte lesen",
            consequence = "Die App zeigt danach nur noch Nummern statt Namen. " +
                "Kontaktvorschläge beim Teilen entfallen.",
            action = REVOKE_DE,
        ),
        Capability.OVERLAY to CapabilityText(
            label = "Bildschirm überlagern",
            consequence = "Schwebende Fenster dieser App verschwinden -- etwa " +
                "Chat-Blasen, ein eingeblendeter Anrufbildschirm oder ein " +
                "Blaulichtfilter.",
            action = REVOKE_DE,
        ),
        Capability.AUTOSTART to CapabilityText(
            label = "Automatisch starten",
            consequence = "Android lässt diese Berechtigung nicht einzeln entziehen -- " +
                "sie wird bei der Installation vergeben. Viele Hersteller haben dafür " +
                "eine eigene Akku-Einstellung: Wird die App dort eingeschränkt, kommen " +
                "ihre Benachrichtigungen später oder gar nicht.",
            action = INSPECT_DE,
        ),
        Capability.APP_LIST to CapabilityText(
            label = "Alle installierten Apps sehen",
            consequence = "Android lässt diese Berechtigung nicht einzeln entziehen. " +
                "Sie bleibt, solange die App installiert ist -- wer sie loswerden will, " +
                "muss die App entfernen.",
            action = INSPECT_DE,
        ),
        Capability.INSTALL_PACKAGES to CapabilityText(
            label = "Apps installieren",
            consequence = "Die App kann sich danach nicht mehr selbst aktualisieren. " +
                "Bei einem App-Katalog wie F-Droid oder einem eigenen Store fällt " +
                "damit die Update-Funktion aus -- bei allem anderen ist der Zugriff " +
                "überflüssig.",
            action = REVOKE_DE,
        ),
        Capability.USAGE_STATS to CapabilityText(
            label = "Nutzungsverlauf auslesen",
            consequence = "Bildschirmzeit-Auswertungen und Nutzungsstatistiken dieser " +
                "App bleiben danach leer.",
            action = REVOKE_DE,
        ),
        Capability.ALL_FILES to CapabilityText(
            label = "Gespeicherte Dateien lesen",
            consequence = "Die App kommt danach nur noch an ihre eigenen Ordner und an " +
                "das, was Sie ihr einzeln freigeben. Dateiverwalter und Sicherungs-Apps " +
                "brauchen diesen Zugriff wirklich.",
            action = REVOKE_DE,
        ),
        Capability.ACCESSIBILITY to CapabilityText(
            label = "Bedienungshilfen-Dienst",
            consequence = "Vorsicht: Hiervon hängen echte Hilfsfunktionen ab. Bei einer " +
                "Passwortverwaltung fällt das automatische Ausfüllen weg, ein " +
                "Vorlesedienst verstummt, Fernwartung und Automatisierungen hören auf " +
                "zu arbeiten. Kennen Sie den Eintrag nicht, gehört er abgeschaltet.",
            action = REVOKE_DE,
        ),
        Capability.DEVICE_ADMIN to CapabilityText(
            label = "Geräteverwalter",
            consequence = "Muss ohnehin entzogen werden, bevor sich die App überhaupt " +
                "entfernen lässt. Bei einem verwalteten Diensthandy verlieren Sie " +
                "danach den Zugriff auf Firmenkonten und Firmendaten.",
            action = REVOKE_DE,
        ),
        Capability.NOTIFICATION_LISTENER to CapabilityText(
            label = "Benachrichtigungen mitlesen",
            consequence = "Wichtig bei Smartwatches und Kopfhörern: Ohne diesen Zugriff " +
                "kommen dort keine Benachrichtigungen mehr an. Auch Autoradios und " +
                "Verlaufs-Apps brauchen ihn.",
            action = REVOKE_DE,
        ),
    )

    private val ENGLISH: Map<Capability, CapabilityText> = mapOf(
        Capability.MESSAGES to CapabilityText(
            label = "Read messages",
            consequence = "The app will no longer receive SMS. For messengers and " +
                "banking apps this removes automatic entry of the confirmation code -- " +
                "you will have to type it in.",
            action = REVOKE_EN,
        ),
        Capability.CALL_LOG to CapabilityText(
            label = "Log calls",
            consequence = "The app loses access to the call log. Caller identification " +
                "and spam warnings for unknown numbers stop working.",
            action = REVOKE_EN,
        ),
        Capability.MICROPHONE to CapabilityText(
            label = "Record microphone",
            consequence = "Voice messages, in-app calls and dictation stop working.",
            action = REVOKE_EN,
        ),
        Capability.CAMERA to CapabilityText(
            label = "Use camera",
            consequence = "Photos, video calls and QR code scanning stop working in " +
                "this app.",
            action = REVOKE_EN,
        ),
        Capability.LOCATION_BACKGROUND to CapabilityText(
            label = "Location in background",
            consequence = "The app only knows your location while it is open. " +
                "Automatic location features -- noticing that you arrived home, " +
                "finding a mislaid device -- no longer work.",
            action = REVOKE_EN,
        ),
        Capability.LOCATION to CapabilityText(
            label = "Request location",
            consequence = "Maps, local weather and nearby search stop working in this app.",
            action = REVOKE_EN,
        ),
        Capability.CONTACTS to CapabilityText(
            label = "Read contacts",
            consequence = "The app will show numbers instead of names. Contact " +
                "suggestions when sharing disappear.",
            action = REVOKE_EN,
        ),
        Capability.OVERLAY to CapabilityText(
            label = "Draw over the screen",
            consequence = "Floating windows from this app disappear -- chat bubbles, an " +
                "overlaid call screen or a blue-light filter.",
            action = REVOKE_EN,
        ),
        Capability.AUTOSTART to CapabilityText(
            label = "Start automatically",
            consequence = "Android does not let this permission be revoked individually " +
                "-- it is granted at install time. Many vendors offer a battery setting " +
                "instead: restrict the app there and its notifications arrive late or " +
                "not at all.",
            action = INSPECT_EN,
        ),
        Capability.APP_LIST to CapabilityText(
            label = "See all installed apps",
            consequence = "Android does not let this permission be revoked individually. " +
                "It stays as long as the app is installed -- removing the app is the " +
                "only way out.",
            action = INSPECT_EN,
        ),
        Capability.INSTALL_PACKAGES to CapabilityText(
            label = "Install apps",
            consequence = "The app can no longer update itself. For an app catalogue " +
                "like F-Droid or your own store this removes updates -- for anything " +
                "else the access is unnecessary.",
            action = REVOKE_EN,
        ),
        Capability.USAGE_STATS to CapabilityText(
            label = "Read usage history",
            consequence = "Screen time reports and usage statistics in this app stay empty.",
            action = REVOKE_EN,
        ),
        Capability.ALL_FILES to CapabilityText(
            label = "Read stored files",
            consequence = "The app is limited to its own folders and whatever you share " +
                "with it individually. File managers and backup apps genuinely need " +
                "this access.",
            action = REVOKE_EN,
        ),
        Capability.ACCESSIBILITY to CapabilityText(
            label = "Accessibility service",
            consequence = "Careful: real assistive features depend on this. A password " +
                "manager loses autofill, a screen reader goes silent, remote support " +
                "and automation stop working. If you do not recognise the entry, turn " +
                "it off.",
            action = REVOKE_EN,
        ),
        Capability.DEVICE_ADMIN to CapabilityText(
            label = "Device administrator",
            consequence = "Has to be revoked before the app can be uninstalled at all. " +
                "On a managed work phone you will lose access to company accounts and " +
                "data.",
            action = REVOKE_EN,
        ),
        Capability.NOTIFICATION_LISTENER to CapabilityText(
            label = "Read all notifications",
            consequence = "Important for smartwatches and headphones: without this " +
                "access they receive no notifications at all. Car displays and history " +
                "apps need it too.",
            action = REVOKE_EN,
        ),
    )
}
