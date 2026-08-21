package com.selfguard.app.net

/**
 * Gerade so viel DNS, wie zum Filtern nötig ist.
 *
 * Absichtlich keine vollständige DNS-Bibliothek: gebraucht werden genau zwei
 * Dinge -- den gefragten Namen herauslesen und eine Absage bauen. Alles andere
 * reicht der Filter unverändert an den echten Namensserver weiter, der es
 * ohnehin besser kann.
 */
object DnsMessage {

    /** Kopf eines DNS-Pakets: Kennung, Merker und vier Abschnittszähler. */
    private const val HEADER_SIZE = 12

    private const val FLAGS_HIGH_OFFSET = 2
    private const val FLAGS_LOW_OFFSET = 3

    /** Oberstes Bit im ersten Merkerbyte: 1 bedeutet Antwort. */
    private const val FLAG_RESPONSE = 0x80

    /** Antwort möglich (RA) gesetzt, Rückmeldecode 3 = Name existiert nicht. */
    private const val FLAGS_LOW_NXDOMAIN = 0x83

    /** Ab 0xC0 ist ein Längenbyte ein Verweis statt einer Bezeichnung. */
    private const val LABEL_POINTER_MASK = 0xC0

    /** Größte zulässige Bezeichnungslänge nach RFC 1035. */
    private const val MAX_LABEL_LENGTH = 63

    /**
     * Liest die Vorgangskennung aus den ersten beiden Bytes.
     *
     * Sie ist das einzige Merkmal, an dem sich eine Antwort ihrer Anfrage
     * zuordnen lässt. Wer sie nicht prüft, nimmt jede Antwort an, die
     * zufällig auf dem richtigen Anschluss eintrifft -- der klassische Weg,
     * einer Namensauflösung eine falsche Adresse unterzuschieben.
     */
    fun transactionId(message: ByteArray): Int? {
        if (message.size < HEADER_SIZE) return null
        return readUShort(message, 0)
    }

    /**
     * Liest den gefragten Namen aus einer Anfrage.
     *
     * Rückgabe null, wenn das Paket unvollständig ist, keinen Frageabschnitt
     * hat oder mit Verweisen arbeitet -- in der Frage sind die nicht erlaubt,
     * und ein Paket, das sich nicht daran hält, wird nicht gefiltert sondern
     * durchgereicht. Lieber eine Anfrage zu wenig blocken als eine kaputte
     * Antwort bauen.
     */
    fun questionName(payload: ByteArray): String? {
        if (payload.size <= HEADER_SIZE) return null
        // Ohne Frageabschnitt gibt es nichts zu prüfen.
        val questionCount = readUShort(payload, 4)
        if (questionCount == 0) return null

        val builder = StringBuilder()
        var offset = HEADER_SIZE

        while (offset < payload.size) {
            val length = payload[offset].toInt() and 0xFF
            when {
                length == 0 -> return builder.toString().ifEmpty { null }
                length and LABEL_POINTER_MASK == LABEL_POINTER_MASK -> return null
                length > MAX_LABEL_LENGTH -> return null
            }

            val start = offset + 1
            val end = start + length
            if (end > payload.size) return null

            if (builder.isNotEmpty()) builder.append('.')
            for (index in start until end) {
                builder.append((payload[index].toInt() and 0xFF).toChar())
            }
            offset = end
        }
        return null
    }

    /**
     * Baut aus der Anfrage eine Absage ("diesen Namen gibt es nicht").
     *
     * NXDOMAIN statt einer Antwort mit 0.0.0.0: Apps geben dann sofort auf,
     * statt vergeblich eine Verbindung zu versuchen und in einen Zeitablauf zu
     * laufen. Das spart Akku und lässt Oberflächen schneller weiterlaufen.
     */
    fun nxDomainResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        if (response.size < HEADER_SIZE) return response

        // Antwortbit setzen, den Rest der oberen Merker (Opcode, RD) behalten.
        response[FLAGS_HIGH_OFFSET] =
            (response[FLAGS_HIGH_OFFSET].toInt() or FLAG_RESPONSE).toByte()
        response[FLAGS_LOW_OFFSET] = FLAGS_LOW_NXDOMAIN.toByte()

        // Antwort-, Autoritäts- und Zusatzabschnitt bleiben leer.
        writeUShort(response, 6, 0)
        writeUShort(response, 8, 0)
        writeUShort(response, 10, 0)
        return response
    }

    private fun readUShort(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

    private fun writeUShort(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }
}
