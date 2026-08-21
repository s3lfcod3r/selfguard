package com.selfguard.app.scan.apps

import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Was mit einer App tatsaechlich getan werden kann.
 *
 * Android kennt drei sehr verschiedene Faelle, und sie zu vermischen fuehrt
 * zu Knoepfen, die nichts tun. Deshalb steht hier fuer jede App genau eine
 * Moeglichkeit.
 */
enum class AppAction {
    /** Selbst installiert -- laesst sich vollstaendig entfernen. */
    ENTFERNEN,

    /**
     * Ab Werk vorhanden, aber inzwischen aktualisiert. Das Update laesst sich
     * zurueckdrehen; die urspruengliche Fassung bleibt.
     */
    UPDATES_ENTFERNEN,

    /**
     * Fester Systembestandteil. Nicht entfernbar, aber meist abschaltbar --
     * danach laeuft die App nicht mehr und verschwindet aus der App-Liste.
     */
    DEAKTIVIEREN,

    /** SelfGuard selbst. */
    KEINE,
}

/** Warum eine App nicht entfernt werden kann. */
enum class RemovalBlock {
    SYSTEM,
    SELF,
    NONE,
}

/**
 * Was mit einer App getan werden kann -- und was nicht.
 *
 * Leitgedanke: **Das Telefon muss danach noch funktionieren.**
 *
 * Zwei Dinge kann keine Android-App fuer eine andere erledigen, egal wie viele
 * Berechtigungen sie anfordert: deinstallieren und deaktivieren. Beides ist
 * dem System vorbehalten. Fuer das Deinstallieren gibt es immerhin einen
 * Standarddialog, den wir oeffnen duerfen; fuers Deaktivieren gibt es nur die
 * Einstellungsseite der App, auf der der Schalter sitzt.
 *
 * Das ist keine Schwaeche dieser App, sondern Absicht des Systems -- koennte
 * ein beliebiges Programm fremde Apps abschalten, waere der erste Schritt
 * jeder Schadsoftware, das Schutzprogramm stillzulegen.
 *
 * Was bleibt, ist der kurze Weg: den Nutzer mit einem Antippen genau dorthin
 * bringen, wo der richtige Knopf sitzt.
 */
object AppActions {

    private const val OWN_PACKAGE = "com.selfguard.app"

    fun actionFor(app: InstalledApp): AppAction = when {
        app.packageName == OWN_PACKAGE -> AppAction.KEINE
        // Reihenfolge wichtig: eine aktualisierte System-App traegt beide
        // Kennzeichen. Das Zurueckdrehen des Updates ist der schonendere
        // Eingriff und kommt deshalb zuerst.
        app.isUpdatedSystemApp -> AppAction.UPDATES_ENTFERNEN
        app.isSystemApp -> AppAction.DEAKTIVIEREN
        else -> AppAction.ENTFERNEN
    }

    /** Beschriftung des Knopfes. */
    fun label(action: AppAction): String? = when (action) {
        AppAction.ENTFERNEN -> "Entfernen"
        AppAction.UPDATES_ENTFERNEN -> "Updates entfernen"
        AppAction.DEAKTIVIEREN -> "Deaktivieren"
        AppAction.KEINE -> null
    }

    /**
     * Was beim Antippen passiert -- und was danach von der App uebrig bleibt.
     *
     * Beim Deaktivieren steht bewusst dabei, dass eine Systemseite aufgeht.
     * Ein Knopf, der etwas anderes tut als er verspricht, kostet mehr
     * Vertrauen als eine ehrliche Zeile Erklaerung.
     */
    fun explain(action: AppAction): String? = when (action) {
        AppAction.ENTFERNEN ->
            "Die App wird vollstaendig geloescht. Android fragt vorher nach."
        AppAction.UPDATES_ENTFERNEN ->
            "Setzt die App auf die Fassung zurueck, die ab Werk auf dem Geraet " +
                "war. Die App bleibt vorhanden und funktioniert weiter."
        AppAction.DEAKTIVIEREN ->
            "Systembestandteil, laesst sich nicht loeschen. Es oeffnet sich die " +
                "Systemseite dieser App -- dort auf Deaktivieren tippen. Danach " +
                "laeuft sie nicht mehr und taucht nirgends mehr auf. " +
                "Ruecknahme jederzeit an derselben Stelle."
        AppAction.KEINE -> null
    }

    /**
     * Der passende Weg fuer diese App.
     *
     * Null nur fuer SelfGuard selbst -- alles andere hat ein sinnvolles Ziel.
     */
    fun intentFor(app: InstalledApp): Intent? = when (actionFor(app)) {
        AppAction.ENTFERNEN, AppAction.UPDATES_ENTFERNEN -> uninstallIntent(app.packageName)
        AppAction.DEAKTIVIEREN -> detailsIntent(app.packageName)
        AppAction.KEINE -> null
    }

    /** Kann ueberhaupt etwas getan werden? */
    fun isActionable(app: InstalledApp): Boolean = actionFor(app) != AppAction.KEINE

    fun canUninstall(app: InstalledApp): Boolean = actionFor(app) == AppAction.ENTFERNEN

    fun removalBlock(app: InstalledApp): RemovalBlock = when (actionFor(app)) {
        AppAction.KEINE -> RemovalBlock.SELF
        AppAction.DEAKTIVIEREN, AppAction.UPDATES_ENTFERNEN -> RemovalBlock.SYSTEM
        AppAction.ENTFERNEN -> RemovalBlock.NONE
    }

    /**
     * Oeffnet den Standarddialog zum Deinstallieren.
     *
     * `ACTION_DELETE` statt der neueren Paketverwaltung, weil damit der
     * gewohnte Systemdialog erscheint -- der Nutzer sieht, was passiert, und
     * kann abbrechen. Bei einer aktualisierten System-App bietet derselbe
     * Dialog von sich aus nur das Entfernen der Updates an.
     */
    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Die App-Info-Seite in den Systemeinstellungen -- dort sitzt "Deaktivieren". */
    fun detailsIntent(packageName: String): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
