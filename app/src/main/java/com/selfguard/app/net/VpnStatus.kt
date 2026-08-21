package com.selfguard.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Erkennt, ob bereits ein fremdes VPN läuft.
 *
 * Android lässt immer nur **einen** VpnService gleichzeitig zu. Startet der
 * Nutzer den Filter, während etwa ein WireGuard-Tunnel aktiv ist, wird der
 * andere Tunnel wortlos gekappt. Das ist eine Grenze des Systems, kein Fehler
 * dieser App -- aber der Nutzer soll es vorher wissen und nicht hinterher
 * raten, warum seine Verbindung weg ist.
 */
object VpnStatus {

    /** Läuft irgendein VPN, das nicht dieser Filter ist? */
    fun foreignVpnActive(context: Context): Boolean {
        if (DnsFilterService.running.value) return false

        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return false

        // Läuft ein VPN, ist es das aktive Netz -- genau darum geht es hier.
        return runCatching {
            val active = manager.activeNetwork ?: return false
            manager.getNetworkCapabilities(active)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }.getOrDefault(false)
    }
}
