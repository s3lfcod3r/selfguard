# RootBeer prueft auf Root teils ueber native Aufrufe und Klassennamen.
# Ohne diese Regel koennte R8 Teile davon entfernen und die Erkennung
# stillschweigend abschalten.
-keep class com.scottyab.rootbeer.** { *; }

# Die Befund-Aufzaehlungen werden ueber ihre Namen in Textkatalogen
# nachgeschlagen -- Umbenennen waere hier folgenlos, aber die Klarnamen
# helfen bei Fehlerberichten aus dem Feld.
-keepnames class com.selfguard.app.core.FindingCode
