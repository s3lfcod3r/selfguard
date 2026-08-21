package com.selfguard.app

import com.selfguard.app.net.Blocklist
import com.selfguard.app.net.BlocklistUpdater
import com.selfguard.app.net.DnsMessage
import com.selfguard.app.net.Ipv4Udp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft den Netzfilter auf der Ebene, auf der Fehler sonst still bleiben:
 * einzelne Bytes in IP- und DNS-Paketen.
 *
 * Genau hier wären Fehler am teuersten -- ein falsch gesetztes Längenfeld
 * bricht nicht auffällig, sondern lässt einzelne Namensauflösungen
 * gelegentlich hängen.
 */
class NetworkFilterTest {

    // -- Sperrlisten-Abgleich -------------------------------------------

    private fun blocklistOf(vararg domains: String): Blocklist =
        Blocklist.of(domains.map { Blocklist.hashOf(it) })

    @Test
    fun `sperrt eingetragene Domain`() {
        assertTrue(blocklistOf("werbenetz.example").blocks("werbenetz.example"))
    }

    @Test
    fun `sperrt Unterdomain einer eingetragenen Domain`() {
        val list = blocklistOf("doubleclick.net")
        assertTrue(list.blocks("ad.doubleclick.net"))
        assertTrue(list.blocks("tief.verschachtelt.ad.doubleclick.net"))
    }

    @Test
    fun `lässt nicht eingetragene Domain durch`() {
        assertFalse(blocklistOf("werbenetz.example").blocks("meine-bank.de"))
    }

    @Test
    fun `sperrt niemals eine ganze Endung`() {
        // Wäre ".net" jemals ein Treffer, wäre das halbe Netz unerreichbar.
        val list = blocklistOf("net", "de")
        assertFalse(list.blocks("meine-bank.de"))
        assertFalse(list.blocks("beispiel.net"))
    }

    @Test
    fun `ignoriert Groß-Kleinschreibung und Punkt am Ende`() {
        val list = blocklistOf("werbenetz.example")
        assertTrue(list.blocks("WerbeNetz.Example"))
        assertTrue(list.blocks("werbenetz.example."))
    }

    @Test
    fun `leere Liste sperrt nichts`() {
        assertFalse(Blocklist.EMPTY.blocks("werbenetz.example"))
    }

    @Test
    fun `entfernt doppelte Einträge`() {
        val list = Blocklist.of(
            listOf("a.example", "a.example", "b.example").map { Blocklist.hashOf(it) }
        )
        assertEquals(2, list.size)
    }

    // -- Aufbereitung der Listenzeilen ----------------------------------

    @Test
    fun `liest gewöhnliche Listenzeile`() {
        assertEquals("werbenetz.example", BlocklistUpdater.clean("werbenetz.example"))
    }

    @Test
    fun `entfernt Wildcard-Präfix`() {
        assertEquals("werbenetz.example", BlocklistUpdater.clean("*.werbenetz.example"))
    }

    @Test
    fun `überspringt Kommentare und Leerzeilen`() {
        assertNull(BlocklistUpdater.clean("# Title: HaGeZi"))
        assertNull(BlocklistUpdater.clean("   "))
        assertNull(BlocklistUpdater.clean("!AdBlock-Kommentar"))
    }

    @Test
    fun `überspringt Zeilen im hosts-Format`() {
        // Die Wildcard-Listen führen nur nackte Domains; eine hosts-Zeile
        // wäre ein Hinweis auf die falsche Quelldatei.
        assertNull(BlocklistUpdater.clean("0.0.0.0 werbenetz.example"))
    }

    @Test
    fun `überspringt Eintrag ohne Punkt`() {
        assertNull(BlocklistUpdater.clean("localhost"))
    }

    // -- DNS ------------------------------------------------------------

    /** Baut eine echte DNS-Anfrage nach `www.example.com`. */
    private fun dnsQuery(vararg labels: String): ByteArray {
        val header = byteArrayOf(
            0x12, 0x34,             // Kennung
            0x01, 0x00,             // Standardanfrage, Rekursion erwünscht
            0x00, 0x01,             // eine Frage
            0x00, 0x00,             // keine Antworten
            0x00, 0x00,
            0x00, 0x00,
        )
        val question = mutableListOf<Byte>()
        labels.forEach { label ->
            question += label.length.toByte()
            label.forEach { question += it.code.toByte() }
        }
        question += 0             // Ende des Namens
        question += byteArrayOf(0x00, 0x01, 0x00, 0x01).toList() // Typ A, Klasse IN
        return header + question.toByteArray()
    }

    @Test
    fun `liest gefragten Namen aus der Anfrage`() {
        assertEquals("www.example.com", DnsMessage.questionName(dnsQuery("www", "example", "com")))
    }

    @Test
    fun `liefert null bei zu kurzem Paket`() {
        assertNull(DnsMessage.questionName(ByteArray(8)))
    }

    @Test
    fun `liefert null wenn kein Frageabschnitt vorhanden ist`() {
        val query = dnsQuery("www", "example", "com")
        query[4] = 0.toByte()
        query[5] = 0.toByte()
        assertNull(DnsMessage.questionName(query))
    }

    @Test
    fun `liefert null bei abgeschnittenem Namen`() {
        val query = dnsQuery("www", "example", "com")
        assertNull(DnsMessage.questionName(query.copyOf(16)))
    }

    @Test
    fun `Absage behält Kennung und Frage`() {
        val query = dnsQuery("werbenetz", "example")
        val response = DnsMessage.nxDomainResponse(query)

        assertEquals(query.size, response.size)
        // Die Kennung muss stimmen, sonst ordnet der Anfrager die Antwort
        // nicht zu und wartet ins Leere.
        assertEquals(query[0], response[0])
        assertEquals(query[1], response[1])
        assertEquals("werbenetz.example", DnsMessage.questionName(response))
    }

    @Test
    fun `Absage setzt Antwortbit und Rückmeldecode drei`() {
        val response = DnsMessage.nxDomainResponse(dnsQuery("werbenetz", "example"))
        val flagsHigh = response[2].toInt() and 0xFF
        val flagsLow = response[3].toInt() and 0xFF

        assertEquals(0x80, flagsHigh and 0x80)          // Antwort
        assertEquals(3, flagsLow and 0x0F)              // NXDOMAIN
        assertEquals(0x01, flagsHigh and 0x01)          // Rekursion war erwünscht
    }

    // -- Zuordnung von Antwort zu Anfrage -------------------------------

    @Test
    fun `liest die Vorgangskennung aus dem Kopf`() {
        // Die Testanfrage trägt 0x1234.
        assertEquals(0x1234, DnsMessage.transactionId(dnsQuery("werbenetz", "example")))
    }

    @Test
    fun `liefert keine Vorgangskennung bei zu kurzem Paket`() {
        assertNull(DnsMessage.transactionId(ByteArray(4)))
    }

    @Test
    fun `erkennt eine untergeschobene Antwort an abweichender Kennung`() {
        // Kern der Absicherung: Eine Antwort mit fremder Kennung gehört nicht
        // zu unserer Anfrage. Wer das nicht prüft, nimmt gefälschte Adressen
        // an, die zufällig auf dem richtigen Anschluss eintreffen.
        val query = dnsQuery("meine-bank", "de")
        val forged = dnsQuery("meine-bank", "de")
        forged[0] = 0xAB.toByte()
        forged[1] = 0xCD.toByte()

        assertNotEquals(DnsMessage.transactionId(query), DnsMessage.transactionId(forged))
    }

    @Test
    fun `Absage trägt dieselbe Kennung wie die Anfrage`() {
        val query = dnsQuery("werbenetz", "example")
        val response = DnsMessage.nxDomainResponse(query)

        assertEquals(DnsMessage.transactionId(query), DnsMessage.transactionId(response))
    }

    // -- IPv4 und UDP ---------------------------------------------------

    /** Baut ein vollständiges IPv4-UDP-Paket. */
    private fun udpPacket(payload: ByteArray): ByteArray {
        val total = 20 + 8 + payload.size
        val packet = ByteArray(total)
        packet[0] = 0x45.toByte()                                  // IPv4, 5 Wörter Kopf
        packet[2] = ((total shr 8) and 0xFF).toByte()
        packet[3] = (total and 0xFF).toByte()
        packet[8] = 64.toByte()                                    // Lebensdauer
        packet[9] = 17.toByte()                                    // UDP
        byteArrayOf(10, 111, 222.toByte(), 1).copyInto(packet, 12)  // Absender
        byteArrayOf(10, 111, 222.toByte(), 2).copyInto(packet, 16)  // Ziel
        packet[20] = 0x9C.toByte()                        // Quellanschluss 40000
        packet[21] = 0x40.toByte()
        packet[22] = 0x00.toByte()                                 // Zielanschluss 53
        packet[23] = 0x35.toByte()
        val udpLength = 8 + payload.size
        packet[24] = ((udpLength shr 8) and 0xFF).toByte()
        packet[25] = (udpLength and 0xFF).toByte()
        payload.copyInto(packet, 28)
        return packet
    }

    @Test
    fun `zerlegt UDP-Paket in seine Bestandteile`() {
        val payload = dnsQuery("werbenetz", "example")
        val datagram = Ipv4Udp.parse(udpPacket(payload), 28 + payload.size)

        assertNotNull(datagram)
        assertEquals(40000, datagram!!.sourcePort)
        assertEquals(53, datagram.destinationPort)
        assertArrayEquals(byteArrayOf(10, 111, 222.toByte(), 2), datagram.destinationAddress)
        assertArrayEquals(payload, datagram.payload)
    }

    @Test
    fun `verwirft Pakete die kein UDP sind`() {
        val packet = udpPacket(ByteArray(20))
        packet[9] = 6 // TCP
        assertNull(Ipv4Udp.parse(packet, packet.size))
    }

    @Test
    fun `verwirft IPv6-Pakete`() {
        val packet = udpPacket(ByteArray(20))
        packet[0] = 0x60
        assertNull(Ipv4Udp.parse(packet, packet.size))
    }

    @Test
    fun `verwirft zu kurze Pakete`() {
        assertNull(Ipv4Udp.parse(ByteArray(10), 10))
    }

    @Test
    fun `Antwort vertauscht Adressen und Anschlüsse`() {
        val payload = dnsQuery("werbenetz", "example")
        val request = Ipv4Udp.parse(udpPacket(payload), 28 + payload.size)!!
        val answer = DnsMessage.nxDomainResponse(payload)

        val response = Ipv4Udp.buildResponse(request, answer)
        val parsed = Ipv4Udp.parse(response, response.size)!!

        // Die Antwort muss vom vorgetäuschten Namensserver zum Frager laufen.
        assertArrayEquals(byteArrayOf(10, 111, 222.toByte(), 2), parsed.sourceAddress)
        assertArrayEquals(byteArrayOf(10, 111, 222.toByte(), 1), parsed.destinationAddress)
        assertEquals(53, parsed.sourcePort)
        assertEquals(40000, parsed.destinationPort)
        assertArrayEquals(answer, parsed.payload)
    }

    @Test
    fun `Antwort trägt eine gültige IP-Prüfsumme`() {
        val payload = dnsQuery("werbenetz", "example")
        val request = Ipv4Udp.parse(udpPacket(payload), 28 + payload.size)!!
        val response = Ipv4Udp.buildResponse(request, payload)

        // Über einen korrekten Kopf inklusive Prüfsumme gerechnet ergibt die
        // Einerkomplementsumme immer 0xFFFF. Ein Empfänger, der das nicht
        // sieht, verwirft das Paket wortlos.
        var sum = 0L
        var index = 0
        while (index < 20) {
            sum += ((response[index].toInt() and 0xFF) shl 8) or
                (response[index + 1].toInt() and 0xFF)
            index += 2
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        assertEquals(0xFFFFL, sum)
    }

    @Test
    fun `Antwort meldet die richtige Gesamtlänge`() {
        val payload = dnsQuery("werbenetz", "example")
        val request = Ipv4Udp.parse(udpPacket(payload), 28 + payload.size)!!
        val response = Ipv4Udp.buildResponse(request, payload)

        val declared = ((response[2].toInt() and 0xFF) shl 8) or (response[3].toInt() and 0xFF)
        assertEquals(response.size, declared)
    }
}
