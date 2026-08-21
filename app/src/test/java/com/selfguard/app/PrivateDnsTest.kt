package com.selfguard.app

import com.selfguard.app.net.PrivateDnsStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft die Einschätzung, ob ein DNS-Anbieter filtert.
 *
 * Bewusst nur eine Vermutung: Ob ein Anbieter wirklich Werbung blockt, kann
 * die App nicht messen. Wichtig ist deshalb, dass sie bei Unbekanntem nichts
 * behauptet -- ein falsches "filtert schon" würde jemanden dazu bringen, den
 * eigenen Filter auszulassen und ungeschützt zu bleiben.
 */
class PrivateDnsTest {

    @Test
    fun `erkennt bekannte filternde Anbieter`() {
        listOf(
            "dns.adguard-dns.com",
            "abcdef.dns.nextdns.io",
            "freedns.controld.com",
            "base.dns.mullvad.net",
            "dns.quad9.net",
            "security-filter-dns.cleanbrowsing.org",
            "dns0.eu",
        ).forEach {
            assertTrue("sollte als filternd gelten: $it", PrivateDnsStatus.looksLikeFiltering(it))
        }
    }

    @Test
    fun `haelt gewoehnliche Anbieter nicht fuer Filter`() {
        // Diese verschlüsseln, filtern aber nichts. Wer hier faelschlich
        // "filtert schon" liest, schaltet den eigenen Filter aus und steht
        // ohne Schutz da.
        listOf(
            "dns.google",
            "one.one.one.one",
            "dot.sb",
            "dns.mein-anbieter.de",
        ).forEach {
            assertFalse("sollte nicht als filternd gelten: $it", PrivateDnsStatus.looksLikeFiltering(it))
        }
    }

    @Test
    fun `ignoriert Gross-Kleinschreibung`() {
        assertTrue(PrivateDnsStatus.looksLikeFiltering("DNS.AdGuard-DNS.com"))
    }
}
