# AGENTS.md — selfguard

Kurze Orientierung fuer KI-Assistenten (Bonsai/Claude). **Diese Datei zuerst lesen, nicht die grosse README.**
Zum Finden: `rg -n "BEGRIFF"`. Fuer Symbole: Serena `get_symbols_overview`/`find_symbol`. Grosse Dateien nur zeilenweise (`sed -n`).

## Typ & Stack

- Android-App: Quellcode in `_android-src/` (gitignored, nie pushen)

## Bauen / Testen / Starten

```bash
bash /mnt/user/appdata/android-build/apk-release.sh selfguard build   # APK bauen
bash /mnt/user/appdata/android-build/apk-release.sh selfguard         # komplettes Release
```

## Struktur (oberste Ebenen)

- `.github/` — CI/Workflows
- `_android-src/` — Android-Quellcode (lokal)
- `app/` — App-Code
- `docs/` — Dokumentation
- `worker/`

---
*Automatisch erzeugt als Navigationshilfe. Bei Bedarf von Hand praeziser machen.*
