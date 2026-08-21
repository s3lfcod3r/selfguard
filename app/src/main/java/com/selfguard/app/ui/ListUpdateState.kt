package com.selfguard.app.ui

/** Zustand der Sperrlisten-Aktualisierung, damit die Oberflaeche etwas anzeigen kann. */
sealed interface ListUpdateState {
    data object Ruht : ListUpdateState
    data class Laeuft(val source: String) : ListUpdateState
    data class Fertig(val domains: Int, val sources: List<String>) : ListUpdateState
    data class Fehler(val message: String) : ListUpdateState
}
