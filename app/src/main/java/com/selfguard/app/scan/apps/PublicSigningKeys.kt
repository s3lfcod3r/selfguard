package com.selfguard.app.scan.apps

/**
 * Signaturschluessel, die **niemals** als Hinweis auf Schadsoftware taugen.
 *
 * Der Grund ist eine unangenehme Eigenheit von Android: Im AOSP-Quelltext
 * liegen Schluesselpaare offen -- allen voran `testkey`. Jeder kann damit
 * signieren. Zwei voellig unbeteiligte Programme tragen dann denselben
 * Fingerabdruck.
 *
 * Genau das ist passiert: Der Stalkerware-Datensatz enthielt den
 * AOSP-Testschluessel, weil neun verschiedene Ueberwachungsprodukte ihn
 * benutzt hatten. Damit meldete SelfGuard auf einem gewoehnlichen Samsung die
 * Systemkomponente `com.android.cts.ctsshim` als "bekannte
 * Ueberwachungssoftware" -- ein Fehlalarm der schlimmsten Sorte, weil er
 * ausgerechnet die glaubwuerdigste Meldung der App betraf.
 *
 * Hier stehen nur Fingerabdruecke, die **belegt** sind. Der eingetragene
 * stammt aus dem Datensatz selbst und war dort von neun unabhaengigen
 * Anbietern geteilt. Weitere AOSP-Schluessel werden bewusst **nicht auf
 * Verdacht** ergaenzt -- ein falsch abgetippter Fingerabdruck wuerde stillos
 * eine echte Erkennung abschalten.
 *
 * Der eigentliche Schutz ist deshalb strukturell und kommt ohne diese Liste
 * aus:
 *  1. Beim Erzeugen des Buendels fliegen Zertifikate raus, die drei oder mehr
 *     unabhaengige Anbieter teilen (siehe [com.selfguard.app.ioc.IocUpdater]).
 *  2. Bei System-Apps wird ein Zertifikatstreffer gar nicht erst gemeldet
 *     (siehe [StalkerwareDetector]).
 *
 * Diese Liste ist nur die dritte Absicherung fuer den bekannten Fall.
 */
object PublicSigningKeys {

    /** SHA-1 des AOSP-Testschluessels, klein geschrieben. */
    val PUBLIC: Set<String> = setOf(
        "61ed377e85d386a8dfee6b864bd85b0bfaa5af81",
    )

    fun isPublic(sha1: String): Boolean = sha1.lowercase() in PUBLIC
}
