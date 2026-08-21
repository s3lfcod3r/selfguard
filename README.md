# SelfGuard

Sicherheits-App fuer Android: Geraete-Audit, Stalkerware-Erkennung,
Dateipruefung und ein Werbe-/Trackerfilter. Eigenstaendig, ohne Verbindung zu
den uebrigen Self-Projekten.

- Paket `com.selfguard.app`, minSdk 26, targetSdk 34
- Kotlin + Compose, Release-APK **1,8 MB**
- Der Scan laeuft **vollstaendig offline**. Netz braucht nur die
  Aktualisierung der Listen und der freiwillige Online-Abgleich.

## Was die App macht

### Pruefung

Sieben Detektoren auf jeder installierten App, neun Pruefungen am Geraet:

| ID | Pruefung |
|----|----------|
| D01 | mit dem oeffentlichen Android-Debug-Schluessel signiert |
| D02 | Installationsquelle unbekannt |
| D03 | kein Starter-Symbol, aber Hintergrunddienste |
| D04 | Berechtigungsprofil einer Ueberwachungs-App |
| D05 | Treffer gegen die Stalkerware-Indikatoren von Echap |
| D06 | targetSdk unter 23 |
| D07 | debuggable-Kennzeichen in einer Release-App |
| D08 | gibt sich als bekannte Marke aus |
| S01 | **nachtraeglich eingerichtetes Wurzelzertifikat** |
| S02 | aktiver Bedienungshilfen-Dienst |
| S03 | App mit Geraeteverwalter-Rechten |
| S04 | App liest alle Benachrichtigungen mit |
| S05 | Entwickleroptionen / USB-Debugging |
| S06 | Root (RootBeer) |
| S07 | System nicht ab Werk signiert |
| S08 | App darf weitere Apps installieren |
| S09 | keine Bildschirmsperre |

Ausgegeben werden **Risiko-Punktzahlen mit Begruendung**, keine
Virus-gefunden-Urteile. Einzig D05 faellt ein Urteil -- dort liegt ein
belegter Treffer gegen einen gepflegten Datensatz vor.

S01 ist der wertvollste Befund: ein selbst hinterlegtes Wurzelzertifikat ist
der beste Hinweis darauf, dass jemand verschluesselten Verkehr mitliest, und
kaum ein Werkzeug zeigt ihn an.

### Filter

Lokaler DNS-Filter ueber `VpnService`. Der Kniff: die Tunnelschnittstelle
bekommt **nur eine Route** -- die auf den vorgetaeuschten Namensserver.
Dadurch laeuft ausschliesslich DNS-Verkehr durch den Tunnel, alles andere
daran vorbei. Kein TCP-Zerlegen, keine Verbindungsverfolgung, kaum
Akkuverbrauch.

Listen von HaGeZi, dazu die C2-Adressen aus dem Stalkerware-Datensatz. Drei
Stufen von "bricht nichts" bis "streng". Gespeichert als sortierte
64-Bit-Streuwerte: acht Byte je Domain statt einer Zeichenkette.

### Dateien

Pruefsummenabgleich gegen den Echap-Datensatz, wahlweise zusaetzlich gegen
den eigenen Reputationsdienst (`worker/`). Ordnerauswahl ueber das System --
**kein** `MANAGE_EXTERNAL_STORAGE`.

## Grenzen

Ehrlich benannt, weil ein Sicherheitswerkzeug ohne benannte Grenzen
Sicherheit vortaeuscht:

- **Kein Virenscanner.** Android laesst keine App in eine andere sehen: kein
  Speicher-Scan, kein Echtzeit-Hook, kein Loeschen. Entfernt wird ueber den
  normalen Deinstall-Dialog.
- **Keine Erkennung staatlicher Spionagesoftware.** Pegasus, Predator und
  Quellen-TKUe laufen mit System- oder Kernelrechten und verstecken sich vor
  der Ebene, auf der diese App laeuft. Selbst Amnestys MVT braucht dafuer
  einen Offline-Dump am Rechner. Erkannt wird handelsuebliche Stalkerware --
  das ist etwas anderes, und es hilft realen Betroffenen.
- **Pruefsummen finden nur Bekanntes.** Neue oder abgewandelte Schadsoftware
  hat eine andere Pruefsumme und faellt durch.
- **Nur ein VPN gleichzeitig.** Laeuft der Filter, ist kein anderer Tunnel
  moeglich. Android-Grenze. Die App erkennt ein fremdes VPN und warnt vorher.
- **DNS ueber TCP wird nicht bedient.** Android fragt praktisch immer ueber
  UDP; nur bei ungewoehnlich grossen Antworten faende ein Programm keinen
  Weg zurueck.
- **Play Protect warnt beim Installieren.** `QUERY_ALL_PACKAGES` ist fuer
  einen Scanner unverzichtbar und loest beim Sideload eine Warnung aus. Muss
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
Funktionen ohne Android-Bezug und laufen deshalb ohne Geraet und ohne
Robolectric.

## Schluessel

`selfguard-release.jks` liegt in `Android/`, die Zugangsdaten in
`keystore.properties` (nicht im Repo). **Beides sichern** -- ohne den
Schluessel gibt es fuer diese App nie wieder ein Update.

Zertifikat SHA-256:
`370f2fd8176bb642a407cc51d7cb226656eba7418305744fc5a42c91e40c5ccc`

## Datenquellen

| Quelle | Lizenz | Verwendung |
|--------|--------|------------|
| [AssoEchap/stalkerware-indicators](https://github.com/AssoEchap/stalkerware-indicators) | CC-BY 4.0 | Stalkerware-Erkennung, C2-Domains, Datei-Pruefsummen |
| [hagezi/dns-blocklists](https://github.com/hagezi/dns-blocklists) | GPL-3.0 | Sperrlisten -- **zur Laufzeit geladen, nicht mitgeliefert** |
| [scottyab/rootbeer](https://github.com/scottyab/rootbeer) | Apache-2.0 | Root-Erkennung |

Die Namensnennung fuer Echap steht in der App unter *Quellen*, wie von
CC-BY gefordert.

## Sicherheitspruefung (18.08.2026)

Geprueft nach dem ECC-Katalog, soweit auf Android uebertragbar. SQL-Injection,
CSRF und Row Level Security haben hier keine Entsprechung; geprueft wurden
stattdessen die tatsaechliche Angriffsflaeche: rohe Netzpakete, Fremddaten,
Geheimnisse und der Worker.

### Behoben

**Untergeschobene DNS-Antworten (schwerwiegend).** `askUpstream` benutzte einen
ungebundenen `DatagramSocket` und pruefte die Vorgangskennung der Antwort
nicht. Ein ungebundener Anschluss nimmt Pakete von **jedem** Absender an --
damit haette jemand im selben Netz beliebige Namen umlenken koennen, fuer alle
Apps auf dem Geraet gleichzeitig, solange der Filter laeuft. In einer
Sicherheits-App besonders unangenehm.

Behoben durch `socket.connect` (der Kern verwirft alles, was nicht vom
befragten Namensserver kommt), Pruefung der Vorgangskennung, und Verwerfen
statt Weiterreichen von Antworten, die nicht mehr in den Tunnel passen. Vier
Tests decken das ab.

**Worker ohne Durchsatzbegrenzung (hoch).** Wer die Adresse kannte, konnte das
VirusTotal-Kontingent von rund 500 Abfragen taeglich leerlaufen lassen. Jetzt
60 Anfragen je Minute und Absenderadresse ueber das Rate-Limiting-Binding,
plus `Retry-After` in der Absage.

**Klartextverbindungen (mittel).** `Downloads` erzwingt jetzt `https`. Die
eingebauten Adressen waren ohnehin verschluesselt, aber die Adresse des
Reputationsdienstes gibt der Nutzer selbst ein -- ueber Klartext waeren die
abgefragten Pruefsummen mitlesbar und die Antwort faelschbar. Die Oberflaeche
sagt es jetzt ausdruecklich, statt still nichts zu tun.

### Geprueft und in Ordnung

- Keine fest eingebauten Geheimnisse. API-Schluessel ausschliesslich als
  Cloudflare-Secrets, `keystore.properties` und `*.jks` sind ignoriert.
- Keine Protokollierung im Produktivcode -- damit koennen auch keine
  Pruefsummen oder Domainnamen in Logs landen.
- Nur die Startaktivitaet ist exportiert; der VPN-Dienst nicht.
- `allowBackup="false"`, also keine Sicherung der Scan-Ergebnisse ueber ADB.
- Alle Laengenfelder in `Ipv4Udp.parse` und `DnsMessage.questionName` sind
  gegen die tatsaechliche Paketgroesse geprueft; Verweise im Fragenabschnitt
  werden abgelehnt statt verfolgt.
- Der Worker prueft die Pruefsumme gegen ein festes Muster, bevor er sie
  weiterreicht.
- Der Online-Abgleich ist ab Werk **aus**. Jede Abfrage verriete dem Dienst,
  dass eine bestimmte Datei auf diesem Geraet liegt.

### Offen

- Die Durchsatzbegrenzung des Workers ist **nicht gegen eine echte Bereitstellung
  geprueft** -- dafuer braeuchte es einen Cloudflare-Zugang. Syntax und
  Rueckfallverhalten sind geprueft: fehlt das Binding, laesst der Dienst die
  Anfrage durch, statt alles zu blockieren.

## Lizenz

GPL-2.0 (siehe [LICENSE](LICENSE)).

Der mitgelieferte Stalkerware-Datensatz stammt von
[Echap](https://github.com/AssoEchap/stalkerware-indicators) und steht unter
CC-BY 4.0 -- die Namensnennung steht in der App unter *Einstellungen,
Datenquellen*.
