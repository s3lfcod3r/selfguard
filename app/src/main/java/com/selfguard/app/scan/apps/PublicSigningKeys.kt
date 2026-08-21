package com.selfguard.app.scan.apps

/**
 * Signaturschlüssel, die **niemals** als Hinweis auf Schadsoftware taugen.
 *
 * Der Grund ist eine unangenehme Eigenheit von Android: Im AOSP-Quelltext
 * liegen Schlüsselpaare offen -- allen voran `testkey`. Jeder kann damit
 * signieren. Zwei völlig unbeteiligte Programme tragen dann denselben
 * Fingerabdruck.
 *
 * Genau das ist passiert: Der Stalkerware-Datensatz enthielt den
 * AOSP-Testschlüssel, weil neun verschiedene Überwachungsprodukte ihn
 * benutzt hatten. Damit meldete SelfGuard auf einem gewöhnlichen Samsung die
 * Systemkomponente `com.android.cts.ctsshim` als "bekannte
 * Überwachungssoftware" -- ein Fehlalarm der schlimmsten Sorte, weil er
 * ausgerechnet die glaubwürdigste Meldung der App betraf.
 *
 * Hier stehen nur Fingerabdrücke, die **belegt** sind. Der eingetragene
 * stammt aus dem Datensatz selbst und war dort von neun unabhängigen
 * Anbietern geteilt. Weitere AOSP-Schlüssel werden bewusst **nicht auf
 * Verdacht** ergänzt -- ein falsch abgetippter Fingerabdruck würde stillos
 * eine echte Erkennung abschalten.
 *
 * Der eigentliche Schutz ist deshalb strukturell und kommt ohne diese Liste
 * aus:
 *  1. Beim Erzeugen des Bündels fliegen Zertifikate raus, die drei oder mehr
 *     unabhängige Anbieter teilen (siehe [com.selfguard.app.ioc.IocUpdater]).
 *  2. Bei System-Apps wird ein Zertifikatstreffer gar nicht erst gemeldet
 *     (siehe [StalkerwareDetector]).
 *
 * Diese Liste ist nur die dritte Absicherung für den bekannten Fall.
 */
object PublicSigningKeys {

    /** SHA-1 des AOSP-Testschlüssels, klein geschrieben. */
    val PUBLIC: Set<String> = setOf(
        "61ed377e85d386a8dfee6b864bd85b0bfaa5af81",
    )

    fun isPublic(sha1: String): Boolean = sha1.lowercase() in PUBLIC
}
