<div align="center">

<img src="assets/logo.png" width="240" alt="SelfGuard logo" />

**Android security app: device audit, stalkerware detection, file checks and a local ad/tracker filter — fully offline scan.**<br>
*Sicherheits-App für Android: Geräte-Audit, Stalkerware-Erkennung, Dateiprüfung und ein lokaler Werbe-/Trackerfilter — Scan komplett offline.*

[![Download](https://img.shields.io/badge/download-latest%20APK-33A78C?logo=android&logoColor=white)](https://github.com/s3lfcod3r/selfguard/releases/latest)
![Version](https://img.shields.io/badge/version-0.7.0-33A78C)
[![License](https://img.shields.io/badge/license-All%20rights%20reserved-8A9CAA)](LICENSE)
![Stack](https://img.shields.io/badge/stack-Kotlin%20%C2%B7%20Compose-43D3AD)
![App](https://img.shields.io/badge/app-Android%208%2B-9DBDD0)
![Worker](https://img.shields.io/badge/worker-Cloudflare-1F7763)

**[🇬🇧 English](#-english) · [🇩🇪 Deutsch](#-deutsch)**

📰 **What's new / Neuigkeiten** → [CHANGELOG.md](CHANGELOG.md)

</div>

---

<a id="-english"></a>

## 🇬🇧 English

**SelfGuard** checks an Android phone for signs of surveillance: a device audit, stalkerware detection, file checksum checks and a local ad/tracker DNS filter. It gives **risk scores with reasons**, not "virus found" verdicts. It is **not** an antivirus and cannot detect state spyware — the [limits](#en-limits) are named openly. The scan runs **fully offline**; the network is only needed to update the lists and for the optional online lookup.

Part of the **Self** family (SelfMailer, SelfStream, SelfArchiver, SelfDashboard …) — same design system, but standalone: no code dependency on other Self projects.

> **TL;DR** — 8 app detectors · 9 device checks · stalkerware data from Echap · DNS filter without real VPN traffic · offline scan · 89 unit tests

### 📑 Table of contents

[Features](#en-features) · [Limits](#en-limits) · [Architecture](#en-architecture) · [Quick start](#en-quick-start) · [Reputation service](#en-worker) · [Security](#en-security) · [Tech stack](#en-tech-stack) · [Development](#en-development) · [Roadmap](#en-roadmap)

<a id="en-features"></a>

### ✨ Features

**🔍 Check** — detectors on every installed app (D01–D08) and checks on the device (S01–S09):

| ID | Check |
|---|---|
| D01 | Signed with the public Android debug key |
| D02 | Unknown installation source |
| D03 | No launcher icon, but background services |
| D04 | Permission profile of a surveillance app |
| D05 | Match against Echap's stalkerware indicators |
| D06 | targetSdk below 23 |
| D07 | `debuggable` flag in a release app |
| D08 | Pretends to be a well-known brand |
| S01 | **Root certificate added afterwards** |
| S02 | Active accessibility service |
| S03 | App with device admin rights |
| S04 | App reads all notifications |
| S05 | Developer options / USB debugging |
| S06 | Root (RootBeer) |
| S07 | System not signed by the vendor |
| S08 | App may install other apps |
| S09 | No screen lock |

- **Risk scores with reasons**, no "virus found" verdicts. Only D05 gives a verdict — there is a proven match against a maintained dataset.
- **S01 is the most valuable finding:** a user-added root certificate is the best sign that someone reads encrypted traffic, and hardly any tool shows it.
- **Clean up:** remove suspicious apps via the normal uninstall dialog, reset to factory version or disable; permission rows open the matching system page.

**🚫 Filter**
- **Local DNS filter** via `VpnService`. The trick: the tunnel interface gets **only one route** — to the fake name server. So only DNS traffic goes through the tunnel, everything else passes by. No TCP splitting, no connection tracking, hardly any battery use.
- Lists from **HaGeZi** plus the C2 addresses from the stalkerware dataset, **three levels** from "breaks nothing" to "strict"
- Stored as sorted 64-bit hashes: eight bytes per domain instead of a string
- **Ways to block** explained in the app: SelfGuard filter, Android private DNS or a filter in your home network — each with its real downsides; an active filtering private DNS is detected

**📁 Files**
- **Checksum lookup** against the Echap dataset, optionally also against your own [reputation service](#en-worker)
- Folder selection via the system — **no** `MANAGE_EXTERNAL_STORAGE`

<a id="en-limits"></a>

### 🧱 Limits

Named openly, because a security tool without named limits fakes security:

- **Not an antivirus.** Android lets no app look into another: no memory scan, no real-time hook, no deleting. Removal goes through the normal uninstall dialog.
- **No detection of state spyware.** Pegasus, Predator and state trojans run with system or kernel rights and hide from the level this app runs on. Even Amnesty's MVT needs an offline dump on a computer. SelfGuard detects commercial stalkerware — something different, and it helps real victims.
- **Checksums only find known files.** New or modified malware has a different checksum and slips through.
- **Only one VPN at a time.** While the filter runs, no other tunnel is possible (Android limit). The app detects a foreign VPN and warns first.
- **DNS over TCP is not served.** Android almost always asks via UDP; only with unusually large answers would an app find no way back.
- **Play Protect warns on install.** `QUERY_ALL_PACKAGES` is essential for a scanner and triggers a warning when sideloading.
- **Foreign permissions cannot be revoked** — Android reserves that for system-signed apps; SelfGuard only opens the system page.

<a id="en-architecture"></a>

### 🧭 Architecture

```text
app/
 ├─ scan/apps/     detectors D01–D08 — pure functions, no Android dependency
 │                 (all Android access lives in AppInventory)
 ├─ scan/system/   device checks S01–S09
 ├─ net/           DNS filter: VpnService with ONE route to the fake name server
 │                   DNS ──▶ tunnel ──▶ blocklist (64-bit hashes) ──▶ upstream
 │                   all other traffic ──▶ bypasses the tunnel
 └─ ioc/           stalkerware indicators (assets/ioc/stalkerware.txt)

worker/            Cloudflare Worker: checksum lookup at MalwareBazaar / VirusTotal
                   (API keys only there, never in the APK)
```

Background and reasoning: [docs/PLAN.md](docs/PLAN.md).

<a id="en-quick-start"></a>

### 🚀 Quick start (install)

1. Download the latest APK from **[Releases](https://github.com/s3lfcod3r/selfguard/releases/latest)** (or install it from **SelfStore**).
2. Open it on your Android device and install (allow installation from unknown sources).
3. Start a scan — it runs offline. Turn on the filter and the online lookup only if you want them.

> **⚠️ Play Protect:** the warning on install comes from `QUERY_ALL_PACKAGES`, which a scanner needs — for this sideloaded app it is expected.

<a id="en-worker"></a>

### 🔌 Reputation service (optional)

A small **Cloudflare Worker** in [`worker/`](worker/README.md) checks file checksums against MalwareBazaar and VirusTotal, so that no API key sits in the APK. The app only knows the worker's address (enter it under *Sources*, `https` only). Endpoint: `GET /v1/hash/<sha256>`.

```bash
npm install -g wrangler
wrangler secret put MALWAREBAZAAR_KEY
wrangler secret put VIRUSTOTAL_KEY
wrangler deploy
```

> **⚠️ Privacy:** the online lookup is **off** by default — every request tells the service that a certain file is on this device.

<a id="en-security"></a>

### 🛡️ Security

Security review on 18 Aug 2026 (ECC catalog, as far as it applies to Android): the real attack surface is raw network packets, foreign data, secrets and the worker.

**Fixed**
- **Spoofed DNS answers (severe):** `askUpstream` used an unbound `DatagramSocket` and did not check the transaction ID — someone in the same network could have redirected any name for all apps. Fixed with `socket.connect`, ID check and dropping answers that no longer fit into the tunnel; four tests cover it.
- **Worker without rate limit (high):** anyone knowing the address could drain the VirusTotal quota (~500 requests/day). Now 60 requests per minute and sender address via the rate-limiting binding, plus `Retry-After`.
- **Plain-text connections (medium):** downloads now enforce `https`; the UI says so explicitly for the user-entered reputation address.

**Checked and fine**
- No built-in secrets — API keys only as Cloudflare secrets; `keystore.properties` and `*.jks` are ignored
- No logging in production code — no checksums or domain names can end up in logs
- Only the launcher activity is exported; the VPN service is not
- `allowBackup="false"` — no backup of scan results via ADB
- All length fields in `Ipv4Udp.parse` and `DnsMessage.questionName` are checked against the real packet size; pointers in the question section are rejected
- The worker checks the checksum against a fixed pattern before passing it on

**Open / deliberate trade-offs** (documented)
- The worker's rate limit is **not tested against a real deployment**; if the binding is missing, the worker lets the request through instead of blocking everything
- Not yet tested on a real device (build, signature, APK content and unit tests are)

Security update history → [CHANGELOG.md](CHANGELOG.md) (entries marked 🔒).

<a id="en-tech-stack"></a>

### 🧱 Tech stack

| Part | Tech |
|---|---|
| App | Kotlin 2.0 · Jetpack Compose · package `com.selfguard.app` · release APK ~1.8 MB |
| Platform | minSdk 26 (Android 8) · targetSdk 34 · compileSdk 35 |
| Worker | Cloudflare Workers · wrangler · MalwareBazaar · VirusTotal |
| Data | [Echap stalkerware-indicators](https://github.com/AssoEchap/stalkerware-indicators) (CC-BY 4.0, bundled) · [HaGeZi dns-blocklists](https://github.com/hagezi/dns-blocklists) (GPL-3.0, **loaded at runtime, not bundled**) · [RootBeer](https://github.com/scottyab/rootbeer) (Apache-2.0) |
| Release | GitHub Releases · SelfStore catalog sync |

<a id="en-development"></a>

### 🛠️ Development

There is **no Gradle wrapper** — build with an installed Gradle and set `JAVA_HOME` (JDK 21):

```bash
JAVA_HOME=/path/to/jdk21 gradle assembleRelease
```

**Tests** — 89 unit tests on the detectors, packet parsing and DNS answer mapping; the detectors are pure functions and run without a device or Robolectric:

```bash
JAVA_HOME=/path/to/jdk21 gradle testDebugUnitTest
```

> **⚠️ Signing key:** `selfguard-release.jks` and `keystore.properties` are not in the repo. **Back up both** — without the key there will never be an update for this app. Certificate SHA-256: `370f2fd8176bb642a407cc51d7cb226656eba7418305744fc5a42c91e40c5ccc`

<a id="en-roadmap"></a>

### 🗺️ Roadmap

- [x] App detectors D01–D08, device checks S01–S09
- [x] Local DNS filter with HaGeZi lists, three levels
- [x] Checksum lookup + optional reputation worker
- [x] Security review (DNS spoofing, rate limit, https)
- [x] Working uninstall, "ways to block", private DNS detection
- [ ] Test on a real device
- [ ] Test the worker's rate limit on a real Cloudflare deployment

<p align="right"><a href="#-english">⬆ back to top</a></p>

---

<a id="-deutsch"></a>

## 🇩🇪 Deutsch

**SelfGuard** prüft ein Android-Handy auf Anzeichen von Überwachung: Geräte-Audit, Stalkerware-Erkennung, Prüfsummen-Abgleich von Dateien und ein lokaler Werbe-/Tracker-DNS-Filter. Ausgegeben werden **Risiko-Punktzahlen mit Begründung**, keine „Virus gefunden“-Urteile. Es ist **kein** Virenscanner und erkennt keine staatliche Spionagesoftware — die [Grenzen](#de-grenzen) stehen offen da. Der Scan läuft **vollständig offline**; Netz braucht nur die Aktualisierung der Listen und der freiwillige Online-Abgleich.

Teil der **Self**-Familie (SelfMailer, SelfStream, SelfArchiver, SelfDashboard …) — gleiches Design-System, aber eigenständig: keine Code-Abhängigkeit zu anderen Self-Projekten.

> **Kurz** — 8 App-Detektoren · 9 Geräteprüfungen · Stalkerware-Daten von Echap · DNS-Filter ohne echten VPN-Verkehr · Offline-Scan · 89 Unit-Tests

### 📑 Inhaltsverzeichnis

[Funktionen](#de-funktionen) · [Grenzen](#de-grenzen) · [Architektur](#de-architektur) · [Schnellstart](#de-schnellstart) · [Reputationsdienst](#de-worker) · [Sicherheit](#de-sicherheit) · [Tech-Stack](#de-tech-stack) · [Entwicklung](#de-entwicklung) · [Roadmap](#de-roadmap)

<a id="de-funktionen"></a>

### ✨ Funktionen

**🔍 Prüfung** — Detektoren auf jeder installierten App (D01–D08) und Prüfungen am Gerät (S01–S09):

| ID | Prüfung |
|---|---|
| D01 | Mit dem öffentlichen Android-Debug-Schlüssel signiert |
| D02 | Installationsquelle unbekannt |
| D03 | Kein Starter-Symbol, aber Hintergrunddienste |
| D04 | Berechtigungsprofil einer Überwachungs-App |
| D05 | Treffer gegen die Stalkerware-Indikatoren von Echap |
| D06 | targetSdk unter 23 |
| D07 | `debuggable`-Kennzeichen in einer Release-App |
| D08 | Gibt sich als bekannte Marke aus |
| S01 | **Nachträglich eingerichtetes Wurzelzertifikat** |
| S02 | Aktiver Bedienungshilfen-Dienst |
| S03 | App mit Geräteverwalter-Rechten |
| S04 | App liest alle Benachrichtigungen mit |
| S05 | Entwickleroptionen / USB-Debugging |
| S06 | Root (RootBeer) |
| S07 | System nicht ab Werk signiert |
| S08 | App darf weitere Apps installieren |
| S09 | Keine Bildschirmsperre |

- **Risiko-Punktzahlen mit Begründung**, keine „Virus gefunden“-Urteile. Einzig D05 fällt ein Urteil — dort liegt ein belegter Treffer gegen einen gepflegten Datensatz vor.
- **S01 ist der wertvollste Befund:** Ein selbst hinterlegtes Wurzelzertifikat ist der beste Hinweis darauf, dass jemand verschlüsselten Verkehr mitliest, und kaum ein Werkzeug zeigt ihn an.
- **Aufräumen:** auffällige Apps über den normalen Deinstallieren-Dialog entfernen, auf die Werksfassung zurücksetzen oder deaktivieren; Berechtigungszeilen öffnen die passende Systemseite.

**🚫 Filter**
- **Lokaler DNS-Filter** über `VpnService`. Der Kniff: Die Tunnelschnittstelle bekommt **nur eine Route** — die auf den vorgetäuschten Namensserver. Dadurch läuft ausschließlich DNS-Verkehr durch den Tunnel, alles andere daran vorbei. Kein TCP-Zerlegen, keine Verbindungsverfolgung, kaum Akkuverbrauch.
- Listen von **HaGeZi**, dazu die C2-Adressen aus dem Stalkerware-Datensatz, **drei Stufen** von „bricht nichts“ bis „streng“
- Gespeichert als sortierte 64-Bit-Streuwerte: acht Byte je Domain statt einer Zeichenkette
- **Wege zum Blocken** in der App erklärt: Filter von SelfGuard, privates DNS von Android oder ein Filter im Heimnetz — jeweils mit den echten Nachteilen; ein schon filterndes privates DNS wird erkannt

**📁 Dateien**
- **Prüfsummenabgleich** gegen den Echap-Datensatz, wahlweise zusätzlich gegen den eigenen [Reputationsdienst](#de-worker)
- Ordnerauswahl über das System — **kein** `MANAGE_EXTERNAL_STORAGE`

<a id="de-grenzen"></a>

### 🧱 Grenzen

Ehrlich benannt, weil ein Sicherheitswerkzeug ohne benannte Grenzen Sicherheit vortäuscht:

- **Kein Virenscanner.** Android lässt keine App in eine andere sehen: kein Speicher-Scan, kein Echtzeit-Hook, kein Löschen. Entfernt wird über den normalen Deinstallieren-Dialog.
- **Keine Erkennung staatlicher Spionagesoftware.** Pegasus, Predator und Staatstrojaner laufen mit System- oder Kernelrechten und verstecken sich vor der Ebene, auf der diese App läuft. Selbst Amnestys MVT braucht dafür einen Offline-Abzug am Rechner. Erkannt wird handelsübliche Stalkerware — das ist etwas anderes, und es hilft realen Betroffenen.
- **Prüfsummen finden nur Bekanntes.** Neue oder abgewandelte Schadsoftware hat eine andere Prüfsumme und fällt durch.
- **Nur ein VPN gleichzeitig.** Läuft der Filter, ist kein anderer Tunnel möglich (Android-Grenze). Die App erkennt ein fremdes VPN und warnt vorher.
- **DNS über TCP wird nicht bedient.** Android fragt praktisch immer über UDP; nur bei ungewöhnlich großen Antworten fände ein Programm keinen Weg zurück.
- **Play Protect warnt beim Installieren.** `QUERY_ALL_PACKAGES` ist für einen Scanner unverzichtbar und löst beim Installieren außerhalb des Play Stores eine Warnung aus.
- **Fremde Berechtigungen lassen sich nicht entziehen** — Android behält das systemsignierten Apps vor; SelfGuard öffnet nur die Systemseite.

<a id="de-architektur"></a>

### 🧭 Architektur

```text
app/
 ├─ scan/apps/     Detektoren D01–D08 — reine Funktionen ohne Android-Bezug
 │                 (der ganze Android-Zugriff steckt in AppInventory)
 ├─ scan/system/   Geräteprüfungen S01–S09
 ├─ net/           DNS-Filter: VpnService mit EINER Route zum vorgetäuschten Namensserver
 │                   DNS ──▶ Tunnel ──▶ Sperrliste (64-Bit-Streuwerte) ──▶ Upstream
 │                   übriger Verkehr ──▶ läuft am Tunnel vorbei
 └─ ioc/           Stalkerware-Indikatoren (assets/ioc/stalkerware.txt)

worker/            Cloudflare Worker: Prüfsummen-Abfrage bei MalwareBazaar / VirusTotal
                   (API-Schlüssel nur dort, nie im APK)
```

Hintergrund und Begründungen: [docs/PLAN.md](docs/PLAN.md).

<a id="de-schnellstart"></a>

### 🚀 Schnellstart (Installation)

1. Aktuelles APK unter **[Releases](https://github.com/s3lfcod3r/selfguard/releases/latest)** herunterladen (oder aus dem **SelfStore** installieren).
2. Auf dem Android-Gerät öffnen und installieren (Installation aus unbekannten Quellen erlauben).
3. Scan starten — er läuft offline. Filter und Online-Abgleich nur einschalten, wenn du sie willst.

> **⚠️ Play Protect:** Die Warnung beim Installieren kommt von `QUERY_ALL_PACKAGES`, das ein Scanner braucht — bei dieser App außerhalb des Play Stores ist sie zu erwarten.

<a id="de-worker"></a>

### 🔌 Reputationsdienst (optional)

Ein kleiner **Cloudflare Worker** in [`worker/`](worker/README.md) prüft Datei-Prüfsummen gegen MalwareBazaar und VirusTotal, damit kein API-Schlüssel im APK liegt. Die App kennt nur die Adresse des Workers (unter *Quellen* eintragen, nur `https`). Schnittstelle: `GET /v1/hash/<sha256>`.

```bash
npm install -g wrangler
wrangler secret put MALWAREBAZAAR_KEY
wrangler secret put VIRUSTOTAL_KEY
wrangler deploy
```

> **⚠️ Privatsphäre:** Der Online-Abgleich ist ab Werk **aus** — jede Abfrage verrät dem Dienst, dass eine bestimmte Datei auf diesem Gerät liegt.

<a id="de-sicherheit"></a>

### 🛡️ Sicherheit

Sicherheitsprüfung am 18.08.2026 (ECC-Katalog, soweit auf Android übertragbar): Die echte Angriffsfläche sind rohe Netzpakete, Fremddaten, Geheimnisse und der Worker.

**Behoben**
- **Untergeschobene DNS-Antworten (schwerwiegend):** `askUpstream` benutzte einen ungebundenen `DatagramSocket` und prüfte die Vorgangskennung nicht — jemand im selben Netz hätte beliebige Namen für alle Apps umlenken können. Behoben mit `socket.connect`, Prüfung der Kennung und Verwerfen von Antworten, die nicht mehr in den Tunnel passen; vier Tests decken das ab.
- **Worker ohne Durchsatzbegrenzung (hoch):** Wer die Adresse kannte, konnte das VirusTotal-Kontingent (~500 Abfragen/Tag) leerlaufen lassen. Jetzt 60 Anfragen je Minute und Absenderadresse über das Rate-Limiting-Binding, plus `Retry-After`.
- **Klartextverbindungen (mittel):** Downloads erzwingen jetzt `https`; für die selbst eingetragene Adresse des Reputationsdienstes sagt die Oberfläche das ausdrücklich.

**Geprüft und in Ordnung**
- Keine fest eingebauten Geheimnisse — API-Schlüssel nur als Cloudflare-Secrets; `keystore.properties` und `*.jks` sind ignoriert
- Keine Protokollierung im Produktivcode — keine Prüfsummen oder Domainnamen in Logs
- Nur die Startaktivität ist exportiert; der VPN-Dienst nicht
- `allowBackup="false"` — keine Sicherung der Scan-Ergebnisse über ADB
- Alle Längenfelder in `Ipv4Udp.parse` und `DnsMessage.questionName` sind gegen die tatsächliche Paketgröße geprüft; Verweise im Fragenabschnitt werden abgelehnt
- Der Worker prüft die Prüfsumme gegen ein festes Muster, bevor er sie weiterreicht

**Offen / bewusste Kompromisse** (dokumentiert)
- Die Durchsatzbegrenzung des Workers ist **nicht gegen eine echte Bereitstellung geprüft**; fehlt das Binding, lässt der Worker die Anfrage durch, statt alles zu blockieren
- Noch nicht auf einem echten Gerät getestet (Build, Signatur, APK-Inhalt und Unit-Tests schon)

Verlauf der Sicherheitsupdates → [CHANGELOG.md](CHANGELOG.md) (Einträge mit 🔒).

<a id="de-tech-stack"></a>

### 🧱 Tech-Stack

| Teil | Technik |
|---|---|
| App | Kotlin 2.0 · Jetpack Compose · Paket `com.selfguard.app` · Release-APK ~1,8 MB |
| Plattform | minSdk 26 (Android 8) · targetSdk 34 · compileSdk 35 |
| Worker | Cloudflare Workers · wrangler · MalwareBazaar · VirusTotal |
| Daten | [Echap stalkerware-indicators](https://github.com/AssoEchap/stalkerware-indicators) (CC-BY 4.0, mitgeliefert) · [HaGeZi dns-blocklists](https://github.com/hagezi/dns-blocklists) (GPL-3.0, **zur Laufzeit geladen, nicht mitgeliefert**) · [RootBeer](https://github.com/scottyab/rootbeer) (Apache-2.0) |
| Release | GitHub Releases · SelfStore-Katalog-Sync |

<a id="de-entwicklung"></a>

### 🛠️ Entwicklung

Es gibt **keinen Gradle-Wrapper** — gebaut wird mit einer installierten Gradle-Version, `JAVA_HOME` muss gesetzt sein (JDK 21):

```bash
JAVA_HOME=/pfad/zu/jdk21 gradle assembleRelease
```

**Tests** — 89 Unit-Tests auf den Detektoren, dem Paketaufbau und der Zuordnung von DNS-Antworten; die Detektoren sind reine Funktionen und laufen ohne Gerät und ohne Robolectric:

```bash
JAVA_HOME=/pfad/zu/jdk21 gradle testDebugUnitTest
```

> **⚠️ Signaturschlüssel:** `selfguard-release.jks` und `keystore.properties` liegen nicht im Repo. **Beides sichern** — ohne den Schlüssel gibt es für diese App nie wieder ein Update. Zertifikat SHA-256: `370f2fd8176bb642a407cc51d7cb226656eba7418305744fc5a42c91e40c5ccc`

<a id="de-roadmap"></a>

### 🗺️ Roadmap

- [x] App-Detektoren D01–D08, Geräteprüfungen S01–S09
- [x] Lokaler DNS-Filter mit HaGeZi-Listen, drei Stufen
- [x] Prüfsummenabgleich + optionaler Reputations-Worker
- [x] Sicherheitsprüfung (DNS-Fälschung, Durchsatzbegrenzung, https)
- [x] Funktionierende Deinstallation, „Wege zum Blocken“, Erkennung von privatem DNS
- [ ] Test auf einem echten Gerät
- [ ] Durchsatzbegrenzung des Workers an einer echten Cloudflare-Bereitstellung testen

<p align="right"><a href="#-deutsch">⬆ nach oben</a></p>

---

## ⚖️ Lizenz / License

**🇩🇪 Alle Rechte vorbehalten.** © 2026 s3lfcod3r / SelfCoder

Du darfst die Software **privat und nicht-kommerziell** installieren und benutzen (z. B. im Homelab, über Docker oder Unraid).

**Ohne schriftliche Erlaubnis verboten:** kommerzielle Nutzung · Kopieren · Verändern · Weitergeben · Einbauen in eigene Projekte (auch Closed Source) · Verkaufen.

Der mitgelieferte Stalkerware-Datensatz stammt von [Echap](https://github.com/AssoEchap/stalkerware-indicators) und bleibt unter CC-BY 4.0 (Namensnennung in der App unter *Einstellungen, Datenquellen*). Die anderen Datenquellen behalten ihre Lizenzen (siehe Tech-Stack).

Details: [LICENSE](LICENSE)

**🇬🇧 All rights reserved.** © 2026 s3lfcod3r / SelfCoder

You may install and use the software **privately and non-commercially** (e.g. in your home lab, via Docker or Unraid).

**Not permitted without written permission:** commercial use · copying · modifying · redistributing · incorporating into other projects (including closed source) · selling.

The bundled stalkerware dataset is by [Echap](https://github.com/AssoEchap/stalkerware-indicators) and stays under CC-BY 4.0 (attribution in the app under *Settings, Data sources*). Other data sources keep their licenses (see tech stack).

Details: [LICENSE](LICENSE)

---

<div align="center">

**SelfGuard** · part of the Self family · Teil der Self-Familie<br>
made with 🛡️ for self-hosting

</div>
