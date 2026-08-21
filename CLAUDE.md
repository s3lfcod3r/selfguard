# SelfGuard

Android-Sicherheits-App: Geräte-Audit, Stalkerware-Erkennung, Dateiprüfung,
Werbe-/Trackerfilter. Kotlin + Compose, `com.selfguard.app`, minSdk 26,
targetSdk 34.

Eigenständig. Keine Code-Abhängigkeit zu SelfWG, SelfStore oder anderen
Self-Projekten.

## Bauen und Testen

**Es gibt keinen Gradle-Wrapper.** `./gradlew` schlägt fehl -- gebaut wird
über die entpackte Gradle-Installation, und `JAVA_HOME` muss gesetzt sein.

```bash
export JAVA_HOME=/pfad/zu/jdk21
GRADLE=gradle

"$GRADLE" assembleDebug        # Debug-APK
"$GRADLE" assembleRelease      # signiert, rund 1,8 MB
"$GRADLE" testDebugUnitTest    # 81 Tests
```

Signatur prüfen:

```bash
java -jar \
  "apksigner.jar" \
  verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## Aufbau

- `scan/apps/` -- Detektoren D01 bis D08. **Reine Funktionen ohne
  Android-Bezug**, deshalb ohne Gerät und ohne Robolectric testbar. Der
  gesamte Android-Zugriff steckt allein in `AppInventory`.
- `scan/system/` -- Geräteprüfungen S01 bis S09.
- `net/` -- DNS-Filter. Die Tunnelschnittstelle bekommt **nur eine Route**,
  die auf den vorgetäuschten Namensserver. Dadurch läuft ausschließlich
  DNS durch den Tunnel; alles andere geht daran vorbei. Das erspart
  IP-Paket-Zerlegung und Verbindungsverfolgung.
- `ioc/` -- Stalkerware-Indikatoren, gebündelt in `assets/ioc/stalkerware.txt`.
- `worker/` -- Cloudflare Worker für die Reputationsabfrage.

Hintergrund und Begründungen: [docs/PLAN.md](docs/PLAN.md), [README.md](README.md).

## Nicht vergessen

- **Echap führt Zertifikate als SHA-1, nicht SHA-256.** Ein Abgleich gegen
  `signingSha256` erzeugt nie einen Treffer -- der Detektor wäre still
  wirkungslos. Dafür gibt es einen Test.
- **HaGeZi-Sperrlisten stehen unter GPL-3.0.** Zur Laufzeit laden, niemals
  ins APK bündeln.
- **Reputations-API-Schlüssel gehören nur in den Cloudflare Worker**, nie
  in die App. Ein Schlüssel im APK ist in Minuten ausgelesen.
- **Kein automatisches Löschen.** Nur `ACTION_DELETE`; der Nutzer bestätigt
  den Standard-Dialog. Alles andere lässt Android ohnehin nicht zu.
- **Risiko-Punktzahlen statt Urteilen.** Einzig D05 (Echap-Treffer) fällt ein
  Urteil. Ein Scanner, der bei normalen Apps Alarm schlägt, wird ignoriert.
- **Neue Marken in `KnownBrands` nur bei sicherem Paketnamen.** Ein falscher
  Eintrag meldet die echte App als Fälschung.
- **Geteilte Signaturschlüssel sind keine Indikatoren.** Der AOSP-Testkey
  `61ed377e85d386a8...` stand im Echap-Datensatz (neun Anbieter nutzten ihn)
  und ließ SelfGuard `com.android.cts.ctsshim` als Stalkerware melden.
  Drei Absicherungen: Zertifikate mit 3+ Anbietern fliegen beim Aufbereiten
  raus, System-Apps werden per Zertifikat nie gemeldet, und `PublicSigningKeys`
  sperrt den bekannten Fall hart.
- **Kein Starter-Symbol allein reicht nicht.** Begleit-Apps wie der Galaxy
  Watch5 Manager haben legitim keins. D03 meldet nur bei unbekannter
  Installationsquelle.
- **Der Galaxy Store heißt `com.sec.android.app.samsungapps`.** Der früher
  eingetragene Name `com.samsung.android.galaxyapps` existiert auf keinem
  Gerät. Dadurch lief die D03-Ausnahme ins Leere und der Galaxy Watch5
  Manager wurde auf einem echten Samsung doch als versteckt laufend gemeldet.
  Installationsquellen stehen jetzt nur noch in `InstallOrigin`, damit
  Detektoren und Oberfläche dieselbe Liste benutzen.
- **SelfGuard kann fremde Berechtigungen nicht entziehen.** Android lässt das
  keiner App zu -- `REVOKE_RUNTIME_PERMISSIONS` ist der Systemsignatur
  vorbehalten. Die Berechtigungszeilen im Befund öffnen nur die passende
  Systemseite. Beschriftung und Texte müssen das sagen, sonst tippt der
  Nutzer und wundert sich, dass nichts passiert ist.
- **"Nicht ab Werk" ist nicht "selbst installiert".** Der Filter in der
  App-Liste hieß früher "Selbst installiert", zeigte aber jede Nicht-System-App
  -- auch vom Hersteller nachgeladene wie Samsungs Sprachpakete. Er heißt
  jetzt "Nachinstalliert", und die Herkunft steht bei jeder App.
- **Ein Fehlalarm kostet mehr als eine verpasste Erkennung.** Wer einmal
  erlebt, dass die App eine Systemkomponente anschwärzt, glaubt ihr beim
  echten Treffer nicht mehr. Jede neue Heuristik braucht eine Gegenprobe
  in `FalsePositiveTest`.
- **XML-Kommentare vertragen kein `--`.** Der Build bricht daran ab; im
  Kotlin-Code ist es dagegen die übliche Schreibweise.

## Release

`selfguard-release.jks` liegt in `Android/`, Zugangsdaten in
`keystore.properties` (nicht im Repo). **Beides sichern** -- ohne den
Schlüssel gibt es für diese App nie wieder ein Update.

Für den SelfStore-Katalog fehlt in dessen Manifest noch
`<package android:name="com.selfguard.app" />` in der `<queries>`-Liste,
sonst erkennt der Store keine Updates.

## Offener Punkt

Noch **kein Test auf einem echten Gerät**. Geprüft sind Build, Signatur,
APK-Inhalt und 81 Unit-Tests -- nicht das Laufzeitverhalten von Scan und
Filter.
