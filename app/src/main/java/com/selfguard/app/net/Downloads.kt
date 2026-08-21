package com.selfguard.app.net

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Schlanker HTTP-Abruf für Listen und Indikatoren.
 *
 * Bewusst ohne zusätzliche Netzbibliothek: geladen werden ausschließlich
 * statische Textdateien von bekannten Adressen. Eine vollständige
 * HTTP-Bibliothek wäre hier reines Gewicht.
 */
object Downloads {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val USER_AGENT = "SelfGuard/0.1 (Android)"

    /** Für kleine Dateien, die vollständig in den Speicher passen. */
    fun text(url: String): String = open(url).use { it.readText() }

    /**
     * Zeilenweise -- für die Sperrlisten, die mehrere Megabyte groß sind.
     * Der Inhalt landet nie vollständig im Speicher.
     */
    fun forEachLine(url: String, action: (String) -> Unit) {
        open(url).use { reader -> reader.forEachLine(action) }
    }

    private fun open(url: String): BufferedReader {
        // Nur verschlüsselt. Die eingebauten Adressen sind ohnehin https, aber
        // die Adresse des Reputationsdienstes gibt der Nutzer selbst ein --
        // über Klartext wären die abgefragten Prüfsummen für jeden im Netz
        // mitlesbar, und die Antwort ließe sich fälschen.
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
            error("HTTP $code für $url")
        }

        val stream = if (connection.contentEncoding.equals("gzip", ignoreCase = true)) {
            GZIPInputStream(connection.inputStream)
        } else {
            connection.inputStream
        }
        return stream.bufferedReader()
    }
}
