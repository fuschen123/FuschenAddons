# FuschenAddons: verbindliche Arbeitsregeln

## Vor jeder neuen Änderung den aktuellen GitHub-Stand einbeziehen

- Zuerst `git status` und die Remotes prüfen. Uncommittete Änderungen erhalten;
  niemals ohne Auftrag überschreiben, verwerfen oder mit einem Hard-Reset beseitigen.
- Vor der Bearbeitung `git fetch` für das zugehörige GitHub-Repository ausführen.
  Den maßgeblichen Entwicklungsbranch prüfen; ohne andere Vereinbarung gilt der
  aktuelle Default-Branch des Repositories, nicht ein historisch angenommener Branch.
- Den neuesten Remote-Stand als Ausgangspunkt verwenden und vor weiterer Arbeit
  in einen vorhandenen Arbeitsbranch integrieren. Noch nicht gemergte eigene Arbeit
  dabei erhalten. Nötige Sicherungen bestehender lokaler Änderungen ausdrücklich
  nachvollziehbar anlegen; Änderungen anderer Arbeiten nicht vereinnahmen.
- Keine ältere lokale Kopie oder frühere JAR als Entwicklungsgrundlage verwenden,
  wenn GitHub neuer ist. Ist GitHub nicht erreichbar, das ausdrücklich melden und
  einen lokalen Stand nicht als aktuell verifiziert darstellen.
- Beim Abschluss den verwendeten Ausgangsbranch und vollständigen Commit nennen.

## Projektumfang

Nur FuschenAddons bearbeiten. Keine FuschenPlus-Fork oder parallele zweite Mod pflegen.

## Gemeinsamer Funktionsstand für Minecraft 26.1.2, 26.2 und 26.3

- Gemeinsame Änderungen müssen für alle drei unterstützten Minecraft-Versionen
  berücksichtigt und geprüft werden. Keine Variante darf unbemerkt zurückbleiben.
- Feature-Code bleibt zentral in `src/main` und `src/client`. Nur tatsächlich
  notwendige API-Anpassungen gehören in `src/compat/<minecraft_version>`.
- Jede Variante separat mit `-Pminecraft_version=<Version> clean build` bauen.
  Die GitHub-Actions-Matrix muss alle drei Versionen enthalten. Bei Client-/API-
  Änderungen auch die lokale Smoke-Suite je Version ausführen, soweit verfügbar.
- Erfolgreiche Builds, durchgeführte Laufzeittests und offene Ingame-Prüfungen
  getrennt dokumentieren. Fehlende Abhängigkeiten oder Prüfungen ausdrücklich nennen;
  niemals Kompatibilität allein durch Metadaten behaupten.
- Releases verwenden eindeutige Dateinamen `FuschenAddons-<Modversion>-mc<Version>.jar`.
  Bestehende Releases und Nutzereinstellungen erhalten; keine stillen Funktionsforks.
