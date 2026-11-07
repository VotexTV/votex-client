# Preview 5 — direktes Mauslicht und abgerundete Hover-Reflexion

## Behobene Ursachen

Die bisherige `HoverMotion`-Klasse verwendete für Lichtposition und Intensität dieselben gedämpften Kanäle. Bei abrupten Mausbewegungen folgte dadurch die Lichtposition verspätet. Jetzt werden die normalisierten Cursorpositionen im selben Schritt übernommen; Hover-Höhe, Intensität und Ein-/Ausblendung behalten ihre zeitbasierte Animation.

Zusätzlich zeichnete `GlassRender.interaction` nach dem runden Material einen rechteckigen `fillGradient`. Dieser alte Aufsatz verursachte die im Screenshot sichtbare eckige Reflexionsfläche. Sowohl er als auch der verschobene `pointer_glow`-Sprite wurden aus dem aktiven Zeichenpfad entfernt.

Der neue `glass_light`-RenderPipeline zeichnet lokale Licht- und Reflexionsanteile mit derselben Rounded-Box-SDF wie die Oberfläche. Die komplette Alpha-Ausgabe wird mit deren Kantendeckung multipliziert. Transformierte Position, Rundung und Schere werden gemeinsam übernommen. Der Mauspunkt wird mit 1/8 logischem Pixel Präzision übertragen. Die frühere 4-Bit-Quantisierung des dynamischen Zeigerwinkels im Materialshader wurde entfernt; feste Materialreflexe und direkte lokale Cursorreflexe sind getrennt.

Der Effekt zeichnet über den vorhandenen GUI-Pfad, ohne zusätzliche Framebuffer, Hintergrundkopien oder OpenGL-Aufrufe. Bei ausgeschalteten Glaseffekten, reduzierter Bewegung oder deaktiviertem Hover erscheint kein alter rechteckiger Ersatzverlauf.

## Mod-Verwaltung

Die Karten für installierte Mods, Suchergebnisse und vorgemerkte Dateien erhalten jetzt ebenfalls den gemeinsamen Hover-/Reflexionspfad. Der Zustand wird pro Eintrag gespeichert, auf 64 Einträge begrenzt und beim Schließen freigegeben. Kartenfläche und Licht verwenden dieselbe Rundung. Die bisherige Verwaltung mit Icons, Installation, Prüfsummen, Warteschlange und Neustartstatus bleibt erhalten.

Eine zusätzliche lesende Prüfung der vorhandenen lokalen Votex-Profilordner fand ältere Core-Hashes. Keiner entsprach der zuvor gebauten Preview 4. Die bereitgestellte Preview 5 enthält deshalb die aktuelle Core-Datei im Launcher-Paket. Die Aktualisierung derselben Core-Version wurde mit echten alten und neuen JARs in getrennten Testverzeichnissen geprüft, einschließlich erhaltener Einstellungen, deaktivierter Core-Auswahl, Wiederherstellung und wiederholter Installation. Echte Benutzerprofile wurden nicht direkt überschrieben.

## Reale Prüfung

Der abschließende isolierte Minecraft-26.2-/Fabric-Test lief mit Sodium, Iris und den vorhandenen Renderer-Mods auf OpenGL / NVIDIA RTX 4060 Ti / Treiber 610.88. Complementary Reimagined r5.9.3 und BSL v10.1.8 wurden tatsächlich aktiviert. Derselbe Test prüfte erneut Menüs, HUD, native Tasten und Slider, Speichern/Laden, Auflösungen, Qualitätsstufen, Blur/Brechung und das Freigeben eigener Targets.

Neu ist ein Bildtest einer runden Kapsel über der echten Spielwelt: Aufnahme ohne Licht, links beleuchtet und unmittelbar nach dem Wechsel rechts beleuchtet. Außerhalb der vier gerundeten Ecken war der maximale zusätzliche Kanalunterschied 1/255. Die Lichtenergie wanderte deutlich von links nach rechts, statt als Nachzieher stehen zu bleiben. Zusätzlich prüft ein Java-Test abrupt wechselnde Mauskoordinaten ohne einen verzögerten Zwischenschritt. Der reale GPU-Zeichenpfad für den neuen Effekt wurde im Test nachgewiesen.

Ergebnis: 131 Java-Tests bestanden, ein vorhandener Opt-in-Test übersprungen; 102 Launcher-Tests bestanden. 56 echte Screenshots und die Rohmesswerte liegen in der QA-ZIP. Die Produktionsklassen und Shader des finalen JARs stimmen bytegenau mit dem nativen Test überein. Der Core-Update-Test bestand ebenfalls.

Grenzen: Dies ist keine Messung der gesamten physischen Eingabelatenz von Maus, Betriebssystem, Monitor und VSync. Vulkan, andere GPUs und alle fremden Mod-/Shaderkombinationen sind weiterhin nicht vollständig geprüft. Die Windows-Setup-Datei ist gebaut und strukturell geprüft; eine interaktive Installation wurde nicht durchgeführt. Eine öffentlich vertrauenswürdige Codesignatur ist weiterhin nicht vorhanden. Die Website wurde nicht geändert.

## Dateien und Einstieg

`VotexClient-0.4.1-preview5-2026-11-06-Setup.exe` enthält den passenden Core. Vor dem Installieren Minecraft und den bisherigen Launcher schließen. Nach dem Öffnen muss oben **0.4.1 Preview 5** stehen. Ein bewusst deaktivierter Core bleibt deaktiviert und muss im Profil ausdrücklich eingeschaltet werden. Keine zweite Votex-Core-JAR daneben installieren.

Rechte Umschalttaste öffnet das Ingame-Menü; Mods & Shader öffnet die überarbeitete Mod-Verwaltung. Die Materialregler bleiben unter Einstellungen → Liquid Glass gestalten verfügbar.

Quellsicherung vor dieser Korrektur: `work/glass-hover-20261106/before-hover.zip`. Nativer Test und Hashprotokoll: `work/glass-hover-20261106/native-01`. Messzusammenfassung: `acceptance-summary.json` im Release-Ordner.
