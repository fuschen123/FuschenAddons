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
