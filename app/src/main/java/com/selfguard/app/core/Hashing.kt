package com.selfguard.app.core

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** SHA-256 an einer Stelle -- fuer Zertifikate wie fuer Dateien. */
object Hashing {

    private const val BUFFER_SIZE = 64 * 1024

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /**
     * SHA-1 wird ausschliesslich fuer den Abgleich mit fremden Datensaetzen
     * gebraucht -- der Stalkerware-Datensatz von Echap fuehrt
     * Signaturzertifikate als SHA-1-Fingerabdruck. Nicht als Sicherheitsmerkmal
     * verwenden, SHA-1 gilt als gebrochen.
     */
    fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).toHex()

    /**
     * Haelt den Speicherverbrauch unabhaengig von der Dateigroesse -- eine
     * 4-GB-Videodatei darf den Scan nicht abschiessen.
     *
     * @param onProgress erhaelt die bisher gelesenen Bytes, damit lange Scans
     *   abbrechbar bleiben. Gibt der Aufrufer false zurueck, bricht das Lesen ab
     *   und die Funktion liefert null.
     */
    fun sha256(file: File, onProgress: (Long) -> Boolean = { true }): String? =
        file.inputStream().use { stream -> sha256(stream, onProgress) }

    fun sha256(stream: InputStream, onProgress: (Long) -> Boolean = { true }): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
            total += read
            if (!onProgress(total)) return null
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            if (value < 0x10) out.append('0')
            out.append(Integer.toHexString(value))
        }
        return out.toString()
    }
}
