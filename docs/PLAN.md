# SelfGuard — Plan

Eigenständige Sicherheits-App für den SelfStore-Katalog. Keine Code-Abhängigkeit
zu SelfWG, SelfStore oder anderen Self-Projekten.

- Paket: `com.selfguard.app`
- minSdk 26, targetSdk 34, compileSdk 35 (wie die übrigen Android-Apps)
- AGP 8.7.2, Kotlin 2.0.21, Compose BOM 2024.10.01
- Eigener Release-Key: `selfguard-release.jks`

Name ist jetzt noch billig änderbar, später nicht mehr — die `applicationId`
hängt an Signatur und Update-Pfad.

---

## 1. Was die App leistet — und was nicht

Android lässt keine App in eine andere hineinsehen. Kein Speicher-Scan, kein
Echtzeit-Hook auf fremde Prozesse, kein Zugriff auf deren Datenverzeichnisse,
kein Löschen. Ein klassischer Virenscanner ist auf Android technisch unmöglich.

Was möglich ist: **jede installierte APK-Datei lesen** (`sourceDir` ist
world-readable), ihre Metadaten auswerten, den Systemzustand prüfen und Dateien
im geteilten Speicher hashen. Daraus lässt sich ein belastbares Geräte-Audit
bauen — es ist nur etwas anderes als ein Virenscanner.

### Ausdrücklich nicht gebaut

| Nicht gebaut | Grund |
|---|---|
| Bundestrojaner-/Pegasus-Erkennung | Läuft mit System-/Kernel-Rechten und versteckt sich vor der App-Ebene. Selbst Amnestys MVT braucht dafür einen Offline-Dump am PC. Ein solches Versprechen wäre unhaltbar. |
| Automatisches Entfernen | Technisch verboten. Nur `ACTION_DELETE`-Intent, der Nutzer bestätigt den Standard-Dialog. |
| Echtzeitscan beim Schreiben | Ginge über `FileObserver`, aber es gibt keine gepflegte freie Signaturquelle mehr (siehe Hypatia unten). Ein Scanner mit toten Signaturen ist schlimmer als keiner. |
| Scannen fremder Apps im Speicher | Sandbox. |

### Bekannte Einschränkungen

- **Nur ein VPN gleichzeitig.** Solange der Netzfilter läuft, kann kein anderes
  VPN aktiv sein. Android-Grenze, kein Designfehler. Lösung: klarer Ein/Aus-Schalter,
  und die App erkennt ein fremdes aktives VPN und sagt es dem Nutzer.
- **Play-Protect-Warnung beim Installieren.** `QUERY_ALL_PACKAGES` und
  `MANAGE_EXTERNAL_STORAGE` sind genau die Berechtigungen, vor denen Google beim
  Sideload warnt. Für einen Scanner unvermeidbar. SelfStore vermeidet
  `QUERY_ALL_PACKAGES` bewusst — SelfGuard kann das nicht. Muss in der
  Store-Beschreibung erklärt werden.
- **Keine Gewissheit, nur Hinweise.** Die App gibt Risiko-Punktzahlen mit
  Begründung aus, keine Virus-gefunden-Urteile. Ein Scanner, der bei normalen
  Apps Alarm schlägt, wird nach zwei Tagen ignoriert.

---

## 2. Recherche-Ergebnis: was übernommen wird

Lizenz entscheidet. GPL/AGPL würde SelfGuard zur Offenlegung zwingen.

| Projekt | Lizenz | Stand | Verwendung |
|---|---|---|---|
| AssoEchap/stalkerware-indicators | CC-BY 4.0 | 21.06.2026 | **Daten übernehmen.** 174 Apps, 3.228 Samples, Paketnamen + Zertifikate + C2-Domains + Hashes. Maschinenlesbar (CSV/hosts/STIX2), automatisch aktualisiert. Namensnennung nötig. |
| scottyab/rootbeer | Apache-2.0 | 17.03.2026 | **Bibliothek einbinden** für Root-Erkennung. |
| celzero/rethink-app | Apache-2.0 | 09.08.2026 | **Als Referenz lesen**, nicht forken. Zeigt, wie DNS-Filter + VpnService sauber gebaut wird. |
| blokadaorg/blokada | MPL-2.0 | 17.08.2026 | Referenz für Blocklisten-Handhabung. |
| NetGuard, TrackerControl, AdAway, DNS66, personalDNSfilter | GPL-2.0/3.0 | — | **Nicht anfassen.** Infizieren die Lizenz. |
| MaintainTeam/Hypatia | AGPL-3.0 | Mai 2026 | **Nicht nutzen.** Doppelt: AGPL, und das Ursprungsprojekt DivestOS wurde eingestellt — die Signaturdatenbank bekommt keine Updates mehr. |

Blocklisten für den Netzfilter: StevenBlack hosts, OISD, AdGuard DNS filter,
plus die C2-Domains aus dem Echap-Datensatz.

---

## 3. Module

### Modul A — App-Audit (offline, kein Netz)

Jeder Detektor liefert einen Befund mit Schweregrad und Klartext-Begründung.

| ID | Prüfung | Wonach es riecht |
|---|---|---|
| D01 | Signaturzertifikat SHA-256 (`GET_SIGNING_CERTIFICATES`) | Umgepackte Fake-App (falsches WhatsApp mit fremdem Cert) |
| D02 | Installationsquelle (`getInstallSourceInfo`) | Sideload aus unbekannter Quelle |
| D03 | Kein Launcher-Icon, aber Hintergrunddienste | Klassischster Stalkerware-Indikator |
| D04 | Berechtigungs-Kombis: SMS + Overlay + Accessibility + DeviceAdmin + READ_CALL_LOG | Spionage-Profil |
| D05 | Abgleich gegen Echap-Paketliste und -Zertifikate | Bekannte Stalkerware / Watchware |
| D06 | `targetSdk` < 23 | Umgeht das Laufzeit-Berechtigungsmodell |
| D07 | `FLAG_DEBUGGABLE` in einer Release-App | Schlampig gebaut oder manipuliert |
| D08 | Datenverbrauch pro App (`NetworkStatsManager`) | Ausreißer sichtbar machen |

### Modul B — Systemcheck

| ID | Prüfung | Warum es zählt |
|---|---|---|
| S01 | **Nutzer-installierte CA-Zertifikate** — `KeyStore.getInstance("AndroidCAStore")`, Aliase mit Präfix `user:` statt `system:` | Bester verfügbarer Hinweis auf aktives Mitlesen (MITM). Sieht sonst fast niemand. |
| S02 | Aktive Accessibility-Dienste | Vollzugriff auf Bildschirminhalt und Eingaben |
| S03 | Device-Admin- und Device-Owner-Apps | Schwer zu entfernen, weitreichende Rechte |
| S04 | Notification-Listener | Liest alle Benachrichtigungen mit |
| S05 | Entwickleroptionen, ADB, ADB über WLAN | Offene Tür |
| S06 | Root (RootBeer) | Sandbox ausgehebelt |
| S07 | Play Integrity / Bootloader-Status | Geräteintegrität |
| S08 | Welche Apps dürfen "Unbekannte Quellen" installieren | Einfallstor |
| S09 | Bildschirmsperre gesetzt, Verschlüsselung aktiv | Grundhygiene |

### Modul C — Dateiscan

- Ordnerwahl über SAF; `MANAGE_EXTERNAL_STORAGE` optional für Vollscan
- SHA-256 im Stream, damit große Dateien den Speicher nicht sprengen
- Lokaler Abgleich gegen die gebündelten Echap-Sample-Hashes (3.228 Stück)
- **Optional** Reputationsabfrage über eigenen Cloudflare Worker →
  VirusTotal + MalwareBazaar. Die App funktioniert vollständig ohne.
- API-Keys **niemals in der APK** — die sind in Minuten extrahiert. Nur im Worker.

### Modul D — Netzfilter (Werbung/Tracker)

Der entscheidende Kniff: **nicht den gesamten Verkehr durch den TUN leiten.**
Die VPN-Schnittstelle bekommt eine Fake-Adresse, `addDnsServer` zeigt auf eine
lokale Fake-IP, und `addRoute` deckt **nur diese IP** ab. Damit landet
ausschließlich DNS-Verkehr im TUN — alles andere geht am Filter vorbei.

Das erspart komplettes IP-Paket-Parsing (den Weg, für den RethinkDNS einen
eigenen Netzwerk-Stack in Go schreiben musste) und kostet fast keine Akkulaufzeit.

- Blocklisten als kompaktes Hash-Set, Update über `WorkManager`
- Per-App-Ausnahmen über `addDisallowedApplication`
- Zähler: geblockte Anfragen, Top-Domains

---

## 4. Phasen

Jede Phase endet mit einem gebauten, installierten, geprüften APK.

| # | Inhalt | Fertig, wenn |
|---|---|---|
| 1 | Gradle-Gerüst, Compose-Grundgerüst, Modul A (D01–D07) | Release-APK baut, Scan listet echte Befunde auf einem Testgerät |
| 2 | Modul B komplett | S01 meldet ein testweise installiertes CA-Zertifikat |
| 3 | Echap-IOC-Pipeline: Daten bündeln + `WorkManager`-Aktualisierung | D05 erkennt eine bekannte Stalkerware-Paket-ID |
| 4 | Modul D, Netzfilter | Werbedomain wird nachweislich geblockt, normale Seiten laden |
| 5 | Modul C, Dateiscan (+ optional Cloudflare-Worker) | Bekannter Test-Hash wird erkannt |
| 6 | UI-Feinschliff, Store-Eintrag, Release-Key | Im SelfStore installierbar |

## 5. Verifikation

```bash
JAVA_HOME=/pfad/zu/jdk21 \
  gradle assembleRelease
```

- Unit-Tests für jeden Detektor mit gefälschten `PackageInfo`-Objekten (Robolectric)
- Ziel 80 % Abdeckung für die Detektor-Logik
- Falsch-Positiv-Gegenprobe: Scan gegen ein sauberes Gerät muss ruhig bleiben
- Netzfilter: `nslookup` gegen eine Werbedomain vor/nach Aktivierung

## 6. Namensnennung

Der Stalkerware-Datensatz steht unter CC-BY 4.0 und verlangt Attribution.
In den App-Einstellungen unter "Datenquellen": *Stalkerware-Indikatoren von
Echap (echap.eu.org), CC-BY 4.0*.

---

## Stand nach der Umsetzung (18.08.2026)

Alle sechs Phasen umgesetzt. Einzelheiten in [../README.md](../README.md).

**Abweichungen vom Plan, jeweils mit Grund:**

1. **Kein `MANAGE_EXTERNAL_STORAGE`.** Der Dateiscan läuft über die
   Ordnerauswahl des Systems. Ergebnis ist dasselbe, die App bekommt aber nie
   mehr Zugriff als nötig und löst eine Play-Protect-Warnung weniger aus.

2. **Zertifikatsabgleich über SHA-1 statt SHA-256.** Der Echap-Datensatz
   führt Signaturzertifikate als SHA-1-Fingerabdruck. Der ursprünglich
   geplante SHA-256-Abgleich hätte **nie** einen Treffer erzeugt -- der
   Detektor wäre still wirkungslos geblieben.

3. **Sperrlisten von HaGeZi statt StevenBlack/OISD.** HaGeZi ist der
   bestgepflegte freie Satz und liefert zusätzlich eine reine
   Schadsoftware-Liste (`tif`), die den Filter auch gegen Überwachung
   wirken lässt. Wegen GPL-3.0 werden die Listen zur Laufzeit geladen und
   nicht mitgeliefert.

4. **Play Integrity nicht umgesetzt (S07).** Eine belastbare Prüfung braucht
   einen eigenen Server mit Einmalwert. Stattdessen die billige Prüfung auf
   Test-Signaturschlüssel, mit entsprechendem Vorbehalt im Befundtext.

5. **Kein Echtzeitscan.** Wie geplant nicht gebaut -- es gibt keine gepflegte
   freie Signaturquelle mehr, seit Hypatia eingestellt wurde.

**Noch offen:**

- Test auf einem echten Gerät. Es war weder ein Gerät angeschlossen noch ein
  Emulator-Abbild vorhanden. Geprüft sind bisher: Build, Signatur,
  APK-Inhalt und 52 Unit-Tests auf Detektoren und Paketaufbau -- **nicht** das
  Laufzeitverhalten von Scan und Filter.
- Eintrag im SelfStore-Katalog. Dafür muss in SelfStores Manifest die Zeile
  `<package android:name="com.selfguard.app" />` in die `<queries>`-Liste,
  sonst erkennt der Store keine Updates.
