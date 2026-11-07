# Preview 6 — Glasregler, stabiles Menü und Offline-Spielername

## Bedienung

Im Launcher oben rechts auf **Anmelden / Microsoft-Konto** klicken. Im Kontenfenster befindet sich jetzt **Offline-Spielername** mit einem Eingabefeld und **Speichern**. Zulässig sind die von Minecraft unterstützten 1–16 Zeichen aus A–Z, a–z, 0–9 und Unterstrich. Der gespeicherte Name gilt ab dem nächsten Offline-Spielstart. Ein laufendes Spiel und Microsoft-Kontodaten werden dadurch nicht umbenannt.

Das Ingame-Menü öffnet weiterhin mit rechter Umschalttaste. Die Kategorien in der linken Leiste wechseln jetzt innerhalb desselben Fensters. Unter **Einstellungen → Liquid Glass gestalten** und bei den HUD-Moduldetails stehen die neuen Glasregler bereit. Reduzierte Bewegung und ausgeschaltete Effekte werden berücksichtigt.

## Glasregler

Alle eigenen Votex-Schieberegler verwenden `GlassSlider`. Die Oberfläche und die runde Linse verwenden dieselbe GPU-Glass-Engine wie der restliche Client: SDF-Rundung, Szenentextur, Brechung, Reflexe, Kanten und Schatten. Die textfreie Linse verwendet weniger Abdeckung und weniger Blur als eine beschriftete Fläche, damit der Hintergrund tatsächlich durchscheint. Reduzierte Transparenz behält die stärkere Abdeckung.

Die Linse vergrößert sich beim Greifen, streckt sich bei schneller Bewegung leicht und kann bei Richtungswechseln nachschwingen. Druck, Streckung und Neigung sind getrennte gedämpfte Federzustände mit erhaltener Geschwindigkeit. Die analytische Federintegration ist unabhängig von der Framerate. Form, Licht und Brechung werden gemeinsam transformiert. Es gibt keine zusätzliche FBO-Kette oder eine separate Hintergrundaufnahme pro Slider.

Der tatsächliche Wert und der Mittelpunkt der Linse folgen weiterhin unmittelbar der Eingabe. Die Feder animiert ausschließlich die Darstellung. Die sichtbaren Endpunkte werden vor der Übergabe an die native `AbstractSliderButton`-Bedienung auf deren Koordinaten abgebildet. Dadurch stimmen Mauswert und sichtbare Position überein; Tastatur und Narration bleiben von der nativen Komponente übernommen. Die Animation ist begrenzt und federt nach dem Loslassen zurück.

## Kategorienwechsel

Die Ursache war die Konstruktion eines neuen `VotexScreen` bei jedem Kategorienklick. Dabei entstand eine neue Öffnungszeit, und die kurz zuvor erfasste Auslöserposition wurde als Ursprung der gesamten Fensteranimation verwendet.

Jetzt wird der bestehende Screen beibehalten. Nur Kategorieinhalt, Suche und zugehörige Bedienelemente werden erneuert. Die Öffnungszeit, Fensterposition und Fenstergröße bleiben erhalten. Der Inhalt hat einen kurzen Weg von sechs logischen Pixeln; seine Mauskoordinaten werden entsprechend zurückgerechnet. Die Navigationsmarkierung hat einen eigenen Federzustand. Das erstmalige Öffnen und das bewusste Schließen bleiben getrennte Animationen.

## Offline-Name und Speicherung

`OfflineIdentity` ergänzt das vorhandene Kontenfenster. Es bearbeitet ausschließlich das bereits bestehende `offlineUsername`-Feld. `withOfflineUsername` validiert den Namen und lässt alle anderen Zustandsteile unverändert. Der vorhandene `StateDraft` wird synchron aktualisiert, anschließend wird das Speichern ausdrücklich abgewartet. Auch die Browser-Vorschau speichert bei dieser Aktion sofort, statt nur auf den bisherigen Debounce zu warten.

Der bisherige Startpfad verwendet den Namen beim nächsten Offline-Start. Im Microsoft-Zweig verwirft der Rust-Dispatcher den Offline-Namen weiterhin und ermittelt die Identität aus dem authentifizierten Konto. Microsoft-Anmeldesitzung, Tokens und Kontenverwaltung wurden nicht umgebaut. Es wurde keine neue Anmeldung durchgeführt.

## Prüfung und Nachweise

Die Java-Suite enthält Tests für Framerate-Unabhängigkeit der Feder, begrenzte Verformung, Abklingen nach Loslassen und präzise Endpunkte. Die Launcher-Suite prüft Normalisierung, ungültige Namen, unveränderte restliche Einstellungen und JSON-Speicherung.

Der isolierte native Minecraft-/Fabric-Test klickt die Navigationskategorien an und prüft dasselbe Screen-Objekt, dieselbe Öffnungszeit, unveränderte Fenstergrenzen und eine unveränderte äußere Darstellungsmatrix. Der Slider-Test greift, zieht bis zu beiden Enden, wechselt die Richtung und lässt los. Der bestehende Test der transformierten Mausposition, Shader-/Mod-Kompatibilität, Einstellungen, HUDs und GPU-Ressourcen läuft zusätzlich weiter.

Ein separater Chromium-Test bedient das neue Kontenformular im gebauten Frontend, speichert einen Namen, lädt die Seite neu und prüft den wiederhergestellten Namen. Ungültige Eingabe darf den gespeicherten Namen nicht ersetzen. Die Microsoft-Aktion und die bisherige Client-ID bleiben erhalten. Dafür wird eine private Browserinstanz mit Testdaten verwendet; echte Profile und Konten bleiben unberührt.

Die endgültigen Zähler, Core-Hashes und GPU-Teilmesswerte stehen in `acceptance-summary.json`, die Browserprüfung in `offline-browser-proof.json`. Die QA-ZIP enthält tatsächliche Screenshots und Rohdaten. Die vorhandene Qualitätserfassung misst Szene-Copy/Blur und GUI-Zeit auf dieser GPU, nicht die gesamte Minecraft-GPU-Framezeit. Daraus wird kein allgemeiner FPS-Gewinn oder Nullkosten auf beliebiger Hardware abgeleitet.

Ein Zwischenlauf bestand die Oberflächenprüfungen, stieß aber beim Beenden an die bisherige Grenze von 200 Testphasen im ausschließlich für Gametests vorhandenen Shutdown-Adapter. Dieser Adapter wartet jetzt zeitlich begrenzt und lässt dem Serverthread Zeit zum Abschluss. Er ist nicht Bestandteil des verteilten Core-JARs. Der abschließende Lauf muss mit Exitcode 0 beenden.

Getestete Grafik: Minecraft 26.2, Fabric, Sodium/Iris, Complementary Reimagined r5.9.3 und BSL v10.1.8 auf OpenGL / RTX 4060 Ti. Vulkan und andere GPUs wurden nicht neu freigegeben. Native Windows-Installation, authentifizierter Microsoft-Start und physischer Audio-Test sind nicht Teil dieser Prüfung. Die EXE besitzt weiterhin keine öffentlich vertrauenswürdige Signatur. Die Website wurde nicht geändert.

Sicherung vor den Änderungen: `work/sliders-menu-20261106/before-source.zip`. Die installierbare Preview enthält den passenden Core; keine zweite Votex-Core-JAR daneben installieren. Für das Update zuerst Minecraft und den bisherigen Launcher schließen und danach oben **0.4.1 Preview 6** kontrollieren.
