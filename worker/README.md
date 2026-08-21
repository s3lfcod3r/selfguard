# SelfGuard Reputationsdienst

Kleiner Cloudflare Worker, der Datei-Pruefsummen gegen MalwareBazaar und
VirusTotal prueft.

## Wozu

Damit kein API-Schluessel im APK liegt. Ein Schluessel im Programmcode ist in
Minuten ausgelesen und wird dann auf fremde Kosten benutzt. Die App kennt nur
die Adresse dieses Workers.

## Einrichten

```bash
npm install -g wrangler
wrangler secret put MALWAREBAZAAR_KEY   # kostenlos: https://auth.abuse.ch/
wrangler secret put VIRUSTOTAL_KEY      # freiwillig: https://virustotal.com
wrangler deploy
```

Die ausgegebene Adresse in SelfGuard unter Quellen eintragen.

## Schnittstelle

```
GET /v1/hash/<sha256>
```

```json
{"known": false}
{"known": true, "source": "MalwareBazaar", "family": "Cerberus", "detections": 0}
```

## Grenzen

- VirusTotal erlaubt kostenlos etwa vier Abfragen je Minute und 500 am Tag.
  Deshalb wird MalwareBazaar zuerst gefragt und jede Antwort zwischengespeichert.
- Erst ab drei unabhaengigen Erkennungen gilt eine Datei bei VirusTotal als
  auffaellig. Darunter sind es fast immer Fehlalarme einzelner Motoren.
- Der Dienst speichert nichts. Er sieht aber die angefragten Pruefsummen --
  deshalb ist die Abfrage in der App standardmaessig ausgeschaltet.
