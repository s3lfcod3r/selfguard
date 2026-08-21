package com.selfguard.app.core

/** Grobe Einordnung einer Punktzahl fuer die Farbgebung in der Oberflaeche. */
enum class RiskBand { SAUBER, BEOBACHTEN, BEDENKLICH, ALARM }

/**
 * Verdichtet Befunde zu einer Punktzahl von 0 bis 100.
 *
 * Bewusst keine Ja/Nein-Aussage: Android laesst keine sichere Unterscheidung
 * zwischen "boese" und "ungewoehnlich" zu. Die Punktzahl sagt, wie genau man
 * hinsehen sollte -- die Begruendung im Befund sagt, warum.
 */
object RiskScore {

    private const val MAX = 100

    /** Ab dieser Punktzahl gilt ein Bereich als auffaellig. */
    private const val THRESHOLD_WATCH = 10
    private const val THRESHOLD_CONCERN = 30
    private const val THRESHOLD_ALARM = 60

    /**
     * Summiert die Gewichte und deckelt bei 100.
     *
     * Mehrere kleine Auffaelligkeiten sollen sich addieren duerfen -- genau
     * daraus entsteht das Bild einer verdaechtigen App. Einzelne Punkte
     * bleiben dabei unter der Warnschwelle.
     */
    fun of(findings: List<Finding>): Int =
        findings.sumOf { it.severity.weight }.coerceAtMost(MAX)

    /**
     * Ordnet Punktzahl und Befunde einer Stufe zu.
     *
     * Ein einzelner kritischer Befund (etwa ein Treffer gegen die
     * Stalkerware-Liste) hebt immer auf [RiskBand.ALARM] -- unabhaengig
     * von der Summe.
     */
    fun band(findings: List<Finding>): RiskBand {
        if (findings.any { it.severity == Severity.CRITICAL }) return RiskBand.ALARM
        return when (val score = of(findings)) {
            in 0 until THRESHOLD_WATCH -> RiskBand.SAUBER
            in THRESHOLD_WATCH until THRESHOLD_CONCERN -> RiskBand.BEOBACHTEN
            in THRESHOLD_CONCERN until THRESHOLD_ALARM -> RiskBand.BEDENKLICH
            else -> if (score >= THRESHOLD_ALARM) RiskBand.ALARM else RiskBand.BEDENKLICH
        }
    }
}
