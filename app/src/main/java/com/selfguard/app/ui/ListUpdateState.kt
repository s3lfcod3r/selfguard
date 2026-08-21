package com.selfguard.app.ui

/** Zustand der Sperrlisten-Aktualisierung, damit die Oberfläche etwas anzeigen kann. */
sealed interface ListUpdateState {
    data object Ruht : ListUpdateState
    data class Läuft(val source: String) : ListUpdateState
    data class Fertig(val domains: Int, val sources: List<String>) : ListUpdateState
    data class Fehler(val message: String) : ListUpdateState
}
