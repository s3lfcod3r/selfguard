package com.selfguard.app.core

/**
 * Alle Befunde zu einem Gegenstand -- einer App, einem Systembereich, einer
 * Datei. Die Bündelung ist wichtig für die Bewertung: erst in der Summe
 * ergibt sich aus mehreren kleinen Auffälligkeiten ein Bild.
 */
data class SubjectReport(
    val subject: String,
    val label: String,
    val findings: List<Finding>,
) {
    val score: Int get() = RiskScore.of(findings)
    val band: RiskBand get() = RiskScore.band(findings)
}

/** Ergebnis eines vollständigen Durchlaufs. */
data class ScanReport(
    val subjects: List<SubjectReport>,
    val appsScanned: Int,
    val finishedAt: Long,
) {
    /** Die schlimmste Einzelbewertung bestimmt den Gesamteindruck. */
    val worstBand: RiskBand
        get() = subjects.maxOfOrNull { it.band } ?: RiskBand.SAUBER

    val findingCount: Int get() = subjects.sumOf { it.findings.size }

    companion object {
        val EMPTY = ScanReport(emptyList(), 0, 0L)

        /**
         * Gruppiert lose Befunde nach Gegenstand und sortiert absteigend nach
         * Schwere -- das Wichtigste steht oben.
         */
        fun from(findings: List<Finding>, appsScanned: Int, finishedAt: Long): ScanReport {
            val subjects = findings
                .groupBy { it.subject }
                .map { (subject, list) ->
                    SubjectReport(subject, list.first().subjectLabel, list)
                }
                .sortedWith(compareByDescending<SubjectReport> { it.band }.thenByDescending { it.score })
            return ScanReport(subjects, appsScanned, finishedAt)
        }
    }
}
