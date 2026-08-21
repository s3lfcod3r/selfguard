package com.selfguard.app.net

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Schlanker HTTP-Abruf fuer Listen und Indikatoren.
 *
 * Bewusst ohne zusaetzliche Netzbibliothek: geladen werden ausschliesslich
 * statische Textdateien von bekannten Adressen. Eine vollstaendige
 * HTTP-Bibliothek waere hier reines Gewicht.
 */
object Downloads {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val USER_AGENT = "SelfGuard/0.1 (Android)"

    /** Fuer kleine Dateien, die vollstaendig in den Speicher passen. */
    fun text(url: String): String = open(url).use { it.readText() }

    /**
     * Zeilenweise -- fuer die Sperrlisten, die mehrere Megabyte gross sind.
     * Der Inhalt landet nie vollstaendig im Speicher.
     */
    fun forEachLine(url: String, action: (String) -> Unit) {
        open(url).use { reader -> reader.forEachLine(action) }
    }

    private fun open(url: String): BufferedReader {
        // Nur verschluesselt. Die eingebauten Adressen sind ohnehin https, aber
        // die Adresse des Reputationsdienstes gibt der Nutzer selbst ein --
        // ueber Klartext waeren die abgefragten Pruefsummen fuer jeden im Netz
        // mitlesbar, und die Antwort liesse sich faelschen.
        require(url.startsWith("https://", ignoreCase = true)) {
            "Nur https erlaubt, nicht: ${url.substringBefore("://")}"
        }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Encoding", "gzip")
        }

        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            error("HTTP $code fuer $url")
        }

        val stream = if (connection.contentEncoding.equals("gzip", ignoreCase = true)) {
            GZIPInputStream(connection.inputStream)
        } else {
            connection.inputStream
        }
        return stream.bufferedReader()
    }
}
