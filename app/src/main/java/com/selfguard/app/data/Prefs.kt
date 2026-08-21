package com.selfguard.app.data

import android.content.Context

/** Einfache (nicht geheime) App-Einstellungen. */
object Prefs {
    private const val FILE = "selfguard_prefs"
    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Sprache der Oberflaeche: "de" oder "en". */
    fun language(ctx: Context): String = p(ctx).getString("lang", "de") ?: "de"
    fun setLanguage(ctx: Context, value: String) =
        p(ctx).edit().putString("lang", value).apply()

    /** Zeitpunkt des letzten vollstaendigen Scans. */
    fun lastScanAt(ctx: Context): Long = p(ctx).getLong("last_scan", 0L)
    fun setLastScanAt(ctx: Context, value: Long) =
        p(ctx).edit().putLong("last_scan", value).apply()

    /**
     * Darf die App fuer die Dateipruefung Hashes an den eigenen
     * Reputationsdienst schicken? Standard aus -- die App funktioniert
     * vollstaendig offline, und das soll die bewusste Entscheidung des
     * Nutzers bleiben.
     */
    fun reputationLookupEnabled(ctx: Context): Boolean =
        p(ctx).getBoolean("reputation_lookup", false)
    fun setReputationLookupEnabled(ctx: Context, value: Boolean) =
        p(ctx).edit().putBoolean("reputation_lookup", value).apply()

    /**
     * Adresse des eigenen Reputationsdienstes (Cloudflare Worker).
     *
     * Leer bedeutet: keine Abfrage. Die Adresse ist einstellbar, damit
     * kein fremder Dienst fest eingebaut ist und jeder seinen eigenen
     * betreiben kann.
     */
    fun reputationEndpoint(ctx: Context): String = p(ctx).getString("reputation_url", "") ?: ""
    fun setReputationEndpoint(ctx: Context, value: String) =
        p(ctx).edit().putString("reputation_url", value.trim()).apply()

    /** Gewaehlte Sperrlisten-Stufe fuer den Netzfilter. */
    fun blocklistTier(ctx: Context): String = p(ctx).getString("tier", "STANDARD") ?: "STANDARD"
    fun setBlocklistTier(ctx: Context, value: String) =
        p(ctx).edit().putString("tier", value).apply()

    /** Anzahl Domains in der zuletzt gebauten Sperrliste. */
    fun blockedDomains(ctx: Context): Int = p(ctx).getInt("blocked_domains", 0)
    fun setBlockedDomains(ctx: Context, value: Int) =
        p(ctx).edit().putInt("blocked_domains", value).apply()
}
