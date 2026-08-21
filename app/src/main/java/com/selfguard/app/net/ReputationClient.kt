package com.selfguard.app.net

import android.content.Context
import com.selfguard.app.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Antwort des Reputationsdienstes zu einer Prüfsumme. */
sealed interface ReputationVerdict {
    /** Der Dienst wurde nicht gefragt oder war nicht erreichbar. */
    data object Unbekannt : ReputationVerdict

    /** Prüfsumme ist keiner Datenbank bekannt. */
    data object Unauffällig : ReputationVerdict

    data class Bekannt(
        val source: String,
        val family: String?,
        val detections: Int,
    ) : ReputationVerdict {
        fun describe(): String = buildString {
            append(source)
            family?.let { append(", Familie $it") }
            if (detections > 0) append(", $detections Erkennungen")
        }
    }
}

/**
 * Fragt Prüfsummen beim eigenen Reputationsdienst ab.
 *
 * Warum ein eigener Zwischendienst und nicht direkt VirusTotal: **API-Zugänge
 * gehören niemals in ein APK.** Ein Schlüssel im Programmcode ist in wenigen
 * Minuten ausgelesen und wird dann von Fremden auf Kosten des Kontos benutzt.
 * Der Schlüssel liegt deshalb ausschließlich im Cloudflare Worker, die App
 * kennt nur dessen Adresse.
 *
 * Die Abfrage ist außerdem **standardmäßig abgeschaltet**. Jede Anfrage
 * verrät dem Dienst, dass eine bestimmte Datei auf diesem Gerät liegt --
 * das soll eine bewusste Entscheidung des Nutzers bleiben, keine
 * Voreinstellung. Ohne sie arbeitet die App vollständig offline.
 */
object ReputationClient {

    private const val TIMEOUT_NOTE = "Zeitüberschreitung"

    suspend fun lookup(context: Context, sha256: String): ReputationVerdict =
        withContext(Dispatchers.IO) {
            if (!Prefs.reputationLookupEnabled(context)) return@withContext ReputationVerdict.Unbekannt
            val base = Prefs.reputationEndpoint(context)
            if (base.isBlank()) return@withContext ReputationVerdict.Unbekannt

            runCatching {
                val body = Downloads.text("${base.trimEnd('/')}/v1/hash/$sha256")
                parse(body)
            }.getOrDefault(ReputationVerdict.Unbekannt)
        }

    /**
     * Erwartetes Format:
     * `{"known":true,"source":"MalwareBazaar","family":"Cerberus","detections":42}`
     */
    fun parse(body: String): ReputationVerdict {
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: return ReputationVerdict.Unbekannt

        if (!json.optBoolean("known", false)) return ReputationVerdict.Unauffällig

        return ReputationVerdict.Bekannt(
            source = json.optString("source", "unbekannte Quelle"),
            family = json.optString("family").takeIf { it.isNotBlank() },
            detections = json.optInt("detections", 0),
        )
    }

    /** Nur für aussagekräftige Fehlermeldungen in der Oberfläche. */
    fun describeFailure(error: Throwable): String =
        error.message ?: TIMEOUT_NOTE
}
