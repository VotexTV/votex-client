# Ingame Liquid Glass — 0.4.1 Preview 4

## Benutzung

Minecraft 26.2 mit Fabric starten. **Rechte Umschalttaste** öffnet das Votex-Menü. Unter **Einstellungen → Liquid Glass gestalten** liegen die drei Presets, fünf Qualitätsstufen und die Materialregler. **B halten** öffnet das Emote-Rad; Loslassen übernimmt die Auswahl. **HUD gestalten** bietet Verschieben, Skalieren, Ausrichten und acht gespeicherte Layouts. Das Zahnrad an einer Modulkarte öffnet ihre Darstellung und Sichtbarkeit.

Auf kleinen Fenstern bleiben Inhaltslisten und der Designeditor scrollbar. Die externe Launcher-Oberfläche wurde nicht neu gestaltet; nur Preview-Kennzeichnung und die mitgelieferte Core-Datei werden aktualisiert.

## Technische Umsetzung

`VotexGlassEngine` zeichnet native GUI-RenderStates über Minecraft 26.2 / Blaze3D. Es gibt keine Browseransicht, keine statischen Hintergrundbilder und keine direkten OpenGL-Aufrufe. Ein Snapshot der tatsächlichen Spielszene wird vor den GUI-Zeichenbefehlen in zwei gemeinsam genutzten, verkleinerten Gaussian-Pässen gefiltert. Alle Glasflächen desselben Frames verwenden diese Texturen. Schrift und Icons werden anschließend scharf gezeichnet.

Der eigenständig implementierte Fragmentshader verwendet eine Rounded-Box-SDF für Kapseln, Blasen und große Panels. Aus ihrer Randnormalen entstehen stärkere konvexe Randbrechung, geringe innere Verzerrung, dezente Fresnel-/Specular-Lichtkanten und ein vom Mauszeiger abhängiger Reflex. Die Varianten Clear, Regular, Thick und Ultra stammen aus `VotexGlassDesignSystem`; sie ändern Materialparameter innerhalb derselben Familie.

Adaptive Hintergrundabdeckung begrenzt die Helligkeit unter heller Schrift. Light verwendet helle Abdeckung und dunkle UI-Schrift; Dark dunkle Abdeckung. Reduzierte Transparenz verstärkt die Abdeckung. Kleine Schalterknöpfe, Text, Icons und Spielinhalte bleiben bewusst scharf und solide.

`GlassButton`, `GlassSlider`, `GlassModalScreen` und die bestehende `GlassRender`-Fassade teilen Material, Rundung und Einstellungen. Hover und Druck reagieren zeitbasiert. Neue Modals öffnen sich aus einer erfassten Auslöserposition; die Maus wird mit der inversen Darstellungsmatrix transformiert. Bestehende Untermenüs verwenden ihre vorhandene Fensteranimation mit den gemeinsamen Bewegungs-/Tempoeinstellungen. Reduce Motion sowie Off unterbinden diese Animationen. Das Emote-Rad verwendet weiterhin echte Spieler- und Emote-Posen.

Die Flächen verwenden einzelne SDF-Körper. Es gibt keine physikalische Flüssigkeitssimulation oder geometrische Verschmelzung beliebiger benachbarter Fenster. Die Morph-Übergänge entstehen durch gemeinsame Materialparameter, Skalierung und Positionsinterpolation. Das ist eine eigenständige Minecraft-Implementierung, kein Port von Apples SwiftUI-Renderer.

## Einstellungen und Daten

`modules.json` behält Schema 1 und alte HUD-Positionen. Ergänzt werden `glass`, `layouts`, Bezugsauflösungen der Positionen und lokale Glasintensität. Fehlende Felder erhalten kompatible Defaults. Explizit deaktivierte Module bleiben deaktiviert. Bestehende Profile, Mods, Skins, Emotes und Lootbrain-Dateien werden nicht migriert oder gelöscht.

Alle Materialwerte werden validiert. Acht vollständige Layouts mit allen 28 HUD-Widgets passen in das auf 256 KB begrenzte Format; dafür existiert ein Save/Reload-Regressionstest. Atomare JSON-Ersetzung erhält die alte Datei bis zum erfolgreichen Austausch. Kurzzeitiges Windows-AccessDenied wird auf dem IO-Worker begrenzt wiederholt; anhaltende Fehler werden gemeldet. Ein Regressionstest hält währenddessen einen Leser offen.

FPS, Frametimes und CPS stammen aus tatsächlichen Frames bzw. Maustastenereignissen. CPS zählt nur Spiel-Eingaben ohne offenes GUI. Alte Funktionen werden nicht durch erfundene Werte ersetzt. Layouts speichern die HUD-Auswahl, Positionen, Größe und Darstellung; andere Client-Funktionen werden beim Laden nicht umgeschaltet.

Native Client-Funktionen können unmittelbar umgeschaltet werden. Installierte Fabric-JARs bleiben im bestehenden Mod-Library-Ablauf mit Warteschlange, Neustartstatus, Prüfsummen und Wiederherstellung. Es gibt keine vorgetäuschte Hot-Unload-Funktion.

## Ressourcen und Kosten

Scene/Ping/Blur-Targets werden wiederverwendet und nur bei Größe, Qualitätsstufe oder Farbformat neu angelegt. Nach Inaktivität, Renderer-Schließen bzw. Ressourcenwechsel werden sie geschlossen. Der native Test bestätigt am Ende null eigene Targets. Dies ist eine Prüfung des eigenen Ressourcenlebenszyklus und kein universeller Nachweis gegen alle Lecks fremder Mods oder Treiber.

Ultra, High, Balanced und Performance reduzieren gemeinsam genutzte Blur-Auflösung und Brechungsqualität. Off erstellt keine weiteren Szenenkopien/Blur-Pässe und nutzt native abgerundete Ersatzflächen. Shader-/Gerätefehler führen zum Ersatzmaterial mit Logmeldung. GPU-Zeitstempel werden ohne blockierende Warteabfragen gelesen; sie messen Szene-Copy/Blur und den ganzen GUI-Abschnitt, nicht die gesamte Minecraft-GPU-Framezeit.

Die genauen Messwerte, Hardware und Stichproben stehen im mitgelieferten `acceptance-summary.json`. Die festen Qualitätsreihenfolgen, kurze Aufwärmphase und wechselnde Spiel-/Treiberlast erlauben daraus keine belastbare FPS-Rangliste und keine Garantie für FPS-Gewinne. Ganze Game-Frame-GPU-Zeit, Chunk-Builds und andere Rechner wurden nicht gemessen.

## Reale Prüfung und Grenzen

Ein isolierter Fabric-Client-Gametest startet die tatsächliche Minecraft-Renderpipeline und eine private Welt. Die abgenommene Ausführung, Exitcode, Core-Hash, Quellhashes, Rohmesswerte und Screenshots liegen unter `work/liquid-glass-20261106/native-10`. Das JAR wird gegen die tatsächlich kompilierten Produktionsklassen und Shader geprüft; Gametest-Klassen dürfen nicht im Release-JAR enthalten sein.

Geprüft wurden Right Shift, Escape, B halten/loslassen, ein nativer Slider mit korrekter inverser Mausposition, Speichern/erneutes Lesen, Layouts, alle Qualitätsstufen, Light/Dark/Adaptive, reduzierte Bewegung/Transparenz, 800×480 und 1920×1080 mit GUI-Skala 3 sowie 1280×720 mit Skala 2. Aufnahmen stammen aus Schnee-/Obsidian-Kontrastszene, dunklem Raum, tatsächlichem Wasser, Nether und normaler Spielwelt. Votex-Menüs und HUD wurden visuell anhand der Aufnahmen geprüft. Vanilla-Advancement-Toasts bleiben dabei Vanilla und können über der Oberfläche liegen.

Sodium/Iris und die vorhandenen Renderer-Mods wurden gemeinsam geladen. Complementary Reimagined r5.9.3 und BSL v10.1.8 wurden mit tatsächlich aktiven Iris-Pipelines geprüft, einschließlich Glasmenü und Ego-Emote-Submission. Keine Stimme/Mikrofone und keine authentifizierte Multiplayer-Sitzung wurden für diese Designänderung getestet.

Die native Abnahme lief mit OpenGL auf NVIDIA RTX 4060 Ti, Treiber 610.88 und Java 25. Der explizite Vulkan-Wunsch einer früheren Testinstanz führte im Harness weiterhin zu OpenGL; **Vulkan ist nicht als getestet freigegeben**. Backend-neutrale Blaze3D-APIs allein ersetzen diesen fehlenden Laufzeittest nicht. Andere GPUs, HDR, sämtliche Shader-/Mod-Kombinationen und langfristige Benutzung bleiben weitere Kompatibilitätsprüfungen.

Frühere Versuche bleiben zur Nachvollziehbarkeit erhalten: native-02 fand einen Pipeline-Identifier-Fehler, native-03 einen reservierten GLSL-Namen. native-04/05 waren numerisch erfolgreich, zeichneten wegen vertauschter Draw-Argumente jedoch keinen echten Blur. Das wurde durch Bildprüfung erkannt und korrigiert. Die endgültige Pixelprüfung verlangt reduzierte Kantenenergie bei erhaltener mittlerer Helligkeit sowie Brechungsunterschiede oberhalb der unbewegten Kontrollszene. native-06 fand eine SVC-Annahme im Test ohne installierte Voice-Mod. native-08 fand den kurzzeitigen Windows-Speicherkonflikt. Beide wurden behoben; nachfolgende Läufe prüfen die tatsächlichen Effekte und Speicherung.

## Sicherung und Quellen

Vorheriger Core-Quellstand: `work/liquid-glass-20261106/before-source.zip` und Hashliste. Vorherige Launcher-Ressource und App-Datei liegen ebenfalls dort. Echte Benutzerprofile wurden für die Tests nicht geändert. Die bisherige öffentliche Preview und Website werden durch diesen lokalen Build nicht überschrieben.

Designreferenzen: [Apple Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/liquid-glass), [Custom Views](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views), [Materials](https://developer.apple.com/design/human-interface-guidelines/materials). Keine proprietären Apple-Assets oder SwiftUI-Implementierungen übernommen.

Technische Referenzen: [Fabric 26.2 Rendering](https://docs.fabricmc.net/develop/rendering/basic-concepts), [GUI](https://docs.fabricmc.net/develop/rendering/gui-graphics), [HUD](https://docs.fabricmc.net/develop/rendering/hud). Exakte Signaturen wurden zusätzlich gegen die tatsächlich verwendeten 26.2-Klassen geprüft; die Engine nutzt deren RenderPipeline-/RenderState-/Blaze3D-APIs. Bestehende Abhängigkeits- und Schriftlizenzen bleiben im Projekt erhalten.
