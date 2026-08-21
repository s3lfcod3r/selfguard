package com.selfguard.app.scan.apps

/**
 * Bekannte Marken und die Pakete, unter denen sie echt sind.
 *
 * Zweck: gefälschte Apps entlarven, die sich einen vertrauten Namen geben --
 * ein "WhatsApp", das nicht `com.whatsapp` ist, ist keins.
 *
 * Die Liste ist bewusst **konservativ**. Ein falscher Eintrag hier würde die
 * echte App als Fälschung melden, und ein Scanner, der bei einer Bank-App
 * grundlos Alarm schlägt, ist schlimmer als keiner. Deshalb stehen hier nur
 * Pakete, die zweifelsfrei stimmen. Erweitern ist jederzeit gefahrlos --
 * weglassen kostet nur einen nicht erkannten Betrugsfall, ein falscher
 * Eintrag kostet Vertrauen.
 */
object KnownBrands {

    /** Angezeigter Name (normalisiert) -> alle legitimen Paketnamen. */
    val OFFICIAL: Map<String, Set<String>> = mapOf(
        // Messenger -- die häufigsten Ziele für untergeschobene Fassungen
        "whatsapp" to setOf("com.whatsapp", "com.whatsapp.w4b"),
        "telegram" to setOf("org.telegram.messenger"),
        "signal" to setOf("org.thoughtcrime.securesms"),
        "threema" to setOf("ch.threema.app"),
        "discord" to setOf("com.discord"),

        // Soziale Netze
        "facebook" to setOf("com.facebook.katana"),
        "instagram" to setOf("com.instagram.android"),
        "snapchat" to setOf("com.snapchat.android"),
        "tiktok" to setOf("com.zhiliaoapp.musically"),
        "linkedin" to setOf("com.linkedin.android"),

        // Geld -- hier tut eine Fälschung am meisten weh
        "paypal" to setOf("com.paypal.android.p2pmobile"),
        "sparkasse" to setOf("com.starfinanz.smob.android.sfinanzstatus"),
        "ing" to setOf("de.ingdiba.bankingapp"),
        "commerzbank" to setOf("de.commerzbanking.mobil"),

        // Alltag
        "netflix" to setOf("com.netflix.mediaclient"),
        "spotify" to setOf("com.spotify.music"),
        "gmail" to setOf("com.google.android.gm"),
        "chrome" to setOf("com.android.chrome"),
        "firefox" to setOf("org.mozilla.firefox"),
        "outlook" to setOf("com.microsoft.office.outlook"),
    )

    /**
     * Vereinheitlicht einen Anzeigenamen für den Vergleich: Kleinschreibung,
     * ohne Leerzeichen und Sonderzeichen. So fällt "Whats App" mit
     * "WhatsApp" zusammen -- ein beliebter Trick, um Sperren zu umgehen.
     */
    fun normalize(label: String): String =
        label.lowercase().filter { it.isLetterOrDigit() }
}
