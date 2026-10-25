# SelfGuard

Sicherheits-App für Android: Geräte-Audit, Stalkerware-Erkennung,
Dateiprüfung und ein Werbe-/Trackerfilter. Eigenständig, ohne Verbindung zu
den übrigen Self-Projekten.

- Paket `com.selfguard.app`, minSdk 26, targetSdk 34
- Kotlin + Compose, Release-APK **1,8 MB**
- Der Scan läuft **vollständig offline**. Netz braucht nur die
  Aktualisierung der Listen und der freiwillige Online-Abgleich.

## Was die App macht

### Prüfung

Sieben Detektoren auf jeder installierten App, neun Prüfungen am Gerät:

| ID | Prüfung |
|----|----------|
| D01 | mit dem öffentlichen Android-Debug-Schlüssel signiert |
| D02 | Installationsquelle unbekannt |
| D03 | kein Starter-Symbol, aber Hintergrunddienste |
| D04 | Berechtigungsprofil einer Überwachungs-App |
| D05 | Treffer gegen die Stalkerware-Indikatoren von Echap |
| D06 | targetSdk unter 23 |
| D07 | debuggable-Kennzeichen in einer Release-App |
| D08 | gibt sich als bekannte Marke aus |
| S01 | **nachträglich eingerichtetes Wurzelzertifikat** |
| S02 | aktiver Bedienungshilfen-Dienst |
| S03 | App mit Geräteverwalter-Rechten |
| S04 | App liest alle Benachrichtigungen mit |
| S05 | Entwickleroptionen / USB-Debugging |
| S06 | Root (RootBeer) |
| S07 | System nicht ab Werk signiert |
| S08 | App darf weitere Apps installieren |
| S09 | keine Bildschirmsperre |

Ausgegeben werden **Risiko-Punktzahlen mit Begründung**, keine
Virus-gefunden-Urteile. Einzig D05 fällt ein Urteil -- dort liegt ein
belegter Treffer gegen einen gepflegten Datensatz vor.

S01 ist der wertvollste Befund: ein selbst hinterlegtes Wurzelzertifikat ist
der beste Hinweis darauf, dass jemand verschlüsselten Verkehr mitliest, und
kaum ein Werkzeug zeigt ihn an.

### Filter

Lokaler DNS-Filter über `VpnService`. Der Kniff: die Tunnelschnittstelle
bekommt **nur eine Route** -- die auf den vorgetäuschten Namensserver.
Dadurch läuft ausschließlich DNS-Verkehr durch den Tunnel, alles andere
daran vorbei. Kein TCP-Zerlegen, keine Verbindungsverfolgung, kaum
Akkuverbrauch.

Listen von HaGeZi, dazu die C2-Adressen aus dem Stalkerware-Datensatz. Drei
Stufen von "bricht nichts" bis "streng". Gespeichert als sortierte
64-Bit-Streuwerte: acht Byte je Domain statt einer Zeichenkette.

### Dateien

Prüfsummenabgleich gegen den Echap-Datensatz, wahlweise zusätzlich gegen
den eigenen Reputationsdienst (`worker/`). Ordnerauswahl über das System --
**kein** `MANAGE_EXTERNAL_STORAGE`.

## Grenzen

Ehrlich benannt, weil ein Sicherheitswerkzeug ohne benannte Grenzen
Sicherheit vortäuscht:

- **Kein Virenscanner.** Android lässt keine App in eine andere sehen: kein
  Speicher-Scan, kein Echtzeit-Hook, kein Löschen. Entfernt wird über den
  normalen Deinstall-Dialog.
- **Keine Erkennung staatlicher Spionagesoftware.** Pegasus, Predator und
  Quellen-TKÜ laufen mit System- oder Kernelrechten und verstecken sich vor
  der Ebene, auf der diese App läuft. Selbst Amnestys MVT braucht dafür
  einen Offline-Dump am Rechner. Erkannt wird handelsübliche Stalkerware --
  das ist etwas anderes, und es hilft realen Betroffenen.
- **Prüfsummen finden nur Bekanntes.** Neue oder abgewandelte Schadsoftware
  hat eine andere Prüfsumme und fällt durch.
- **Nur ein VPN gleichzeitig.** Läuft der Filter, ist kein anderer Tunnel
  möglich. Android-Grenze. Die App erkennt ein fremdes VPN und warnt vorher.
- **DNS über TCP wird nicht bedient.** Android fragt praktisch immer über
  UDP; nur bei ungewöhnlich großen Antworten fände ein Programm keinen
  Weg zurück.
- **Play Protect warnt beim Installieren.** `QUERY_ALL_PACKAGES` ist für
  einen Scanner unverzichtbar und löst beim Sideload eine Warnung aus. Muss
  in der Store-Beschreibung stehen.

## Bauen

```bash
JAVA_HOME=/pfad/zu/jdk21 \
  gradle assembleRelease
```

Tests:

```bash
JAVA_HOME=/pfad/zu/jdk21 \
  gradle testDebugUnitTest
```

56 Tests auf den Detektoren, dem Paketaufbau und der Zuordnung von DNS-
Antworten. Die Detektoren sind reine
Funktionen ohne Android-Bezug und laufen deshalb ohne Gerät und ohne
Robolectric.

## Schlüssel

`selfguard-release.jks` liegt in `Android/`, die Zugangsdaten in
`keystore.properties` (nicht im Repo). **Beides sichern** -- ohne den
Schlüssel gibt es für diese App nie wieder ein Update.

Zertifikat SHA-256:
`370f2fd8176bb642a407cc51d7cb226656eba7418305744fc5a42c91e40c5ccc`

## Datenquellen

| Quelle | Lizenz | Verwendung |
|--------|--------|------------|
| [AssoEchap/stalkerware-indicators](https://github.com/AssoEchap/stalkerware-indicators) | CC-BY 4.0 | Stalkerware-Erkennung, C2-Domains, Datei-Prüfsummen |
| [hagezi/dns-blocklists](https://github.com/hagezi/dns-blocklists) | GPL-3.0 | Sperrlisten -- **zur Laufzeit geladen, nicht mitgeliefert** |
| [scottyab/rootbeer](https://github.com/scottyab/rootbeer) | Apache-2.0 | Root-Erkennung |

Die Namensnennung für Echap steht in der App unter *Quellen*, wie von
CC-BY gefordert.

## Sicherheitsprüfung (18.08.2026)

Geprüft nach dem ECC-Katalog, soweit auf Android übertragbar. SQL-Injection,
CSRF und Row Level Security haben hier keine Entsprechung; geprüft wurden
stattdessen die tatsächliche Angriffsfläche: rohe Netzpakete, Fremddaten,
Geheimnisse und der Worker.

### Behoben

**Untergeschobene DNS-Antworten (schwerwiegend).** `askUpstream` benutzte einen
ungebundenen `DatagramSocket` und prüfte die Vorgangskennung der Antwort
nicht. Ein ungebundener Anschluss nimmt Pakete von **jedem** Absender an --
damit hätte jemand im selben Netz beliebige Namen umlenken können, für alle
Apps auf dem Gerät gleichzeitig, solange der Filter läuft. In einer
Sicherheits-App besonders unangenehm.

Behoben durch `socket.connect` (der Kern verwirft alles, was nicht vom
befragten Namensserver kommt), Prüfung der Vorgangskennung, und Verwerfen
statt Weiterreichen von Antworten, die nicht mehr in den Tunnel passen. Vier
Tests decken das ab.

**Worker ohne Durchsatzbegrenzung (hoch).** Wer die Adresse kannte, konnte das
VirusTotal-Kontingent von rund 500 Abfragen täglich leerlaufen lassen. Jetzt
60 Anfragen je Minute und Absenderadresse über das Rate-Limiting-Binding,
plus `Retry-After` in der Absage.

**Klartextverbindungen (mittel).** `Downloads` erzwingt jetzt `https`. Die
eingebauten Adressen waren ohnehin verschlüsselt, aber die Adresse des
Reputationsdienstes gibt der Nutzer selbst ein -- über Klartext wären die
abgefragten Prüfsummen mitlesbar und die Antwort fälschbar. Die Oberfläche
sagt es jetzt ausdrücklich, statt still nichts zu tun.

### Geprüft und in Ordnung

- Keine fest eingebauten Geheimnisse. API-Schlüssel ausschließlich als
  Cloudflare-Secrets, `keystore.properties` und `*.jks` sind ignoriert.
- Keine Protokollierung im Produktivcode -- damit können auch keine
  Prüfsummen oder Domainnamen in Logs landen.
- Nur die Startaktivität ist exportiert; der VPN-Dienst nicht.
- `allowBackup="false"`, also keine Sicherung der Scan-Ergebnisse über ADB.
- Alle Längenfelder in `Ipv4Udp.parse` und `DnsMessage.questionName` sind
  gegen die tatsächliche Paketgröße geprüft; Verweise im Fragenabschnitt
  werden abgelehnt statt verfolgt.
- Der Worker prüft die Prüfsumme gegen ein festes Muster, bevor er sie
  weiterreicht.
- Der Online-Abgleich ist ab Werk **aus**. Jede Abfrage verriete dem Dienst,
  dass eine bestimmte Datei auf diesem Gerät liegt.

### Offen

- Die Durchsatzbegrenzung des Workers ist **nicht gegen eine echte Bereitstellung
  geprüft** -- dafür bräuchte es einen Cloudflare-Zugang. Syntax und
  Rückfallverhalten sind geprüft: fehlt das Binding, lässt der Dienst die
  Anfrage durch, statt alles zu blockieren.

## ⚖️ Lizenz / License

**🇩🇪 Alle Rechte vorbehalten.** © 2026 s3lfcod3r / SelfCoder

Du darfst die Software **privat und nicht-kommerziell** installieren und benutzen (z. B. im Homelab, über Docker oder Unraid).

**Ohne schriftliche Erlaubnis verboten:** kommerzielle Nutzung · Kopieren · Verändern · Weitergeben · Einbauen in eigene Projekte (auch Closed Source) · Verkaufen.

Der mitgelieferte Stalkerware-Datensatz stammt von [Echap](https://github.com/AssoEchap/stalkerware-indicators) und bleibt unter CC-BY 4.0 (Namensnennung in der App unter *Einstellungen, Datenquellen*). Die anderen Datenquellen behalten ihre Lizenzen (siehe Tabelle oben).

Details: [LICENSE](LICENSE)

**🇬🇧 All rights reserved.** © 2026 s3lfcod3r / SelfCoder

You may install and use the software **privately and non-commercially** (e.g. in your home lab, via Docker or Unraid).

**Not permitted without written permission:** commercial use · copying · modifying · redistributing · incorporating into other projects (including closed source) · selling.

The bundled stalkerware dataset is by [Echap](https://github.com/AssoEchap/stalkerware-indicators) and stays under CC-BY 4.0 (attribution in the app under *Settings, Data sources*). Other data sources keep their licenses (see table above).

Details: [LICENSE](LICENSE)
