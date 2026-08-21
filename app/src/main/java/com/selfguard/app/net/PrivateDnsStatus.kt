package com.selfguard.app.net

import android.content.Context
import android.provider.Settings

/** Wie das eingebaute private DNS von Android gerade eingestellt ist. */
sealed interface PrivateDns {
    /** Abgeschaltet -- Namensanfragen laufen unverschlüsselt zum Netzbetreiber. */
    data object Aus : PrivateDns

    /**
     * Automatik: Android verschlüsselt, **wenn** der Namensserver des Netzes
     * es anbietet. Das ist die Voreinstellung und blockt nichts -- es
     * verschlüsselt nur, sofern möglich.
     */
    data object Automatisch : PrivateDns

    /** Fester Anbieter. Nur hier kann überhaupt gefiltert werden. */
    data class Anbieter(val hostname: String) : PrivateDns
}

/**
 * Liest die Private-DNS-Einstellung des Systems.
 *
 * **Warum nur lesen und nicht setzen:** Android hat dafür keine öffentliche
 * Schnittstelle. Die Werte liegen in `Settings.Global` und lassen sich von
 * jeder App lesen, aber schreiben darf sie nur das System selbst. Auch der
 * direkte Sprung auf die Einstellungsseite ist nicht vorgesehen -- es gibt
 * keine offizielle Aktion dafür, und der versteckte Aufruf heisst von
 * Hersteller zu Hersteller anders. Bleibt: den Zustand anzeigen, erklären
 * und in die Netzwerkeinstellungen führen.
 *
 * Die Schlüsselnamen sind seit Android 9 unverändert, gehören aber nicht zur
 * öffentlichen Schnittstelle -- deshalb als Zeichenkette und mit Rückfall auf
 * [PrivateDns.Aus], falls ein Hersteller sie anders benennt.
 */
object PrivateDnsStatus {

    private const val MODE_KEY = "private_dns_mode"
    private const val HOSTNAME_KEY = "private_dns_specifier"

    private const val MODE_OFF = "off"
    private const val MODE_OPPORTUNISTIC = "opportunistic"
    private const val MODE_HOSTNAME = "hostname"

    fun read(context: Context): PrivateDns {
        val resolver = context.contentResolver
        val mode = runCatching {
            Settings.Global.getString(resolver, MODE_KEY)
        }.getOrNull()

        return when (mode) {
            MODE_HOSTNAME -> {
                val host = runCatching {
                    Settings.Global.getString(resolver, HOSTNAME_KEY)
                }.getOrNull().orEmpty()
                if (host.isBlank()) PrivateDns.Aus else PrivateDns.Anbieter(host)
            }
            MODE_OPPORTUNISTIC -> PrivateDns.Automatisch
            MODE_OFF -> PrivateDns.Aus
            // Unbekannter oder fehlender Wert: lieber nichts behaupten.
            else -> PrivateDns.Aus
        }
    }

    /**
     * Filtert dieser Anbieter mutmasslich Werbung?
     *
     * Nur eine Einschätzung anhand bekannter Adressen -- ob ein Anbieter
     * wirklich filtert, kann die App nicht messen. Deshalb wird das Ergebnis
     * in der Oberfläche auch als Vermutung formuliert und nicht als Tatsache.
     */
    fun looksLikeFiltering(hostname: String): Boolean {
        val host = hostname.lowercase()
        return FILTERING_HINTS.any { host.contains(it) }
    }

    private val FILTERING_HINTS = listOf(
        "adguard",
        "nextdns",
        "controld",
        "mullvad",
        "quad9",
        "cleanbrowsing",
        "dns0",
    )
}
