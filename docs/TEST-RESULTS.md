# Prüfstand — Votex Client Preview

## Update 0.4.1 RC1 — 21. Oktober 2026

| Prüfung | Ergebnis |
| --- | --- |
| Frontend | 73 Tests / 14 Dateien bestanden. Produktionsbuild mit 2.318 Modulen erfolgreich. Sekundengenaues Einfügen, bestehende Frames wählen, Nachbarschaftsgrenzen und Pixel-Geometrie auf XYZ geprüft. |
| Rust / Windows | 54 Tests bestanden, 8 optionale Online-Checks übersprungen. Die zuvor blockierten Windows-Pfadtests liefen in diesem aktuellen Testläufer. |
| Java / Core | 15 Tests bestanden: Konfigurationsmigration/Positionen, unabhängige vs. wiederverwendete Emote-Puffer, Statistik-Perzentile, Adaptive-Hysterese und absichtliche FPS-/VSync-Grenzen. |
| Natives Minecraft | Voller Fabric-26.2-Renderlauf erfolgreich: 45 Kosmetikmodelle, acht Gesten, eigenes Keyframe-Modell und neue native Menüs. `VOTEX_RENDER_SMOKE_PASS 0.4.1`. Kurzer Folgelauf prüfte den finalen Cache und Menüvorschauen. |
| Vollständiger Modsatz | Neue isolierte QA-Instanz, keine Produktionswelten/Konten kopiert. Minecraft startete mit 64 Mods (einschließlich Sodium, Iris, Lithium, Continuity, Voice Chat) und öffnete die generierte Testwelt. Der doppelte Klassenladerfehler fehlt. |
| Launcher-UI | Native EXE gestartet, echte JAR-Icons und zwei installierte Mods pro Zeile sichtbar. System-Scrollbalken entfernt, Glas-Scrollleiste im Modbereich beim Scrollen sichtbar. Der Profilassistent hat ein breites Suchfeld. |
| Übergänge | Normale/High-Stufe aktiviert, WAAPI-Dialogübergang mit tatsächlicher Zwischen-Transformation und anschließend natürlicher Endgröße beobachtet. Seitenübergänge nativ sichtbar; native Details werden in der Höhe animiert. Ultra Performance und reduzierte Bewegung bleiben absichtlich ohne diesen Effekt. |
| Präzise Zeitleiste | UI-Test: Abspielkopf 0,73 Sekunden → neuer Frame exakt dort, rechte Pfeiltaste → 0,74 Sekunden. Endpunkte bleiben fest, wiederholtes Einfügen erzeugt keinen doppelten Zeitpunkt. 3D-Render und native Studio-Werkzeuge bleiben erhalten. |
| Performance | Emote-Methode: rund 1.680 Byte/Schritt → praktisch keine temporären Pose-Daten. Wiederholte native Textbreiten: rund 48 Byte/Abfrage → praktisch null. Drei Läufe je Variante, Rohdaten und Reproduktionsskript vorhanden. Kein allgemeiner FPS-Vorsprung belegt. |
| FPS-Vergleich | Sechs echte Spiel-Captures aufgezeichnet, aber wegen wechselnder Hintergrund-Drosselung für FPS-Vergleiche verworfen. Kein 2×- oder Konkurrenzvorsprung behauptet. GPU-/Chunk-Timing, Traversal, Entity-Stresstest und Langzeittest offen. |
| Sicherheit / Konten | Keine Microsoft-Anmeldung, keine öffentlichen Community-Uploads, keine bezahlten Dienste. API-Freigabe offen und EXE unsigniert; keine Windows-Richtlinie verändert. |

Direkte Desktop-Abnahme: EXE `ACEA7E9D0D304C3FA39FDC9B172E09A91BE949D2EAC5016F5890B080ADC641C5`, Core `3DE9A1570499E08046F10FBD38B21195FAEFF1E56DD49B626B453FC84CE972C4`. Der Launcher aktualisierte einen Core mit gleicher Versionsnummer tatsächlich auf diese Bytes. Escape → Votex Client öffnete das neue Menü im vollständigen Modpack. Koordinaten aktivieren, die FPS-Karte von links oben in die Fläche ziehen und speichern funktionierten; `modules.json` enthielt anschließend Position x=137/y=64. [Prüfaufnahme](evidence/0.4.1/native-hud-drag.png).

Eine reguläre lokale Messung aus dem Ingame-Menü erfasste nach Aufwärmen 4.181 Frames: Ø 139,36 FPS bei einem wirksamen Limit von 140 FPS, P95 7,50 ms, P99 7,78 ms. JRE 25.0.4.1, flache QA-Welt, Sichtweite 12. Das ist ein Funktionsnachweis der Messung und kein Vorher/Nachher- oder Konkurrenzvergleich. [Messdaten](evidence/0.4.1/normal-client-capture.json).

Sites-Version 6 veröffentlicht. Der öffentliche Setup-Download wurde danach erneut geladen: 9.594.179 Bytes, SHA-256 `2e6ce50635b0b4037b8a9b7a23dcd9a44862b6664ed570811fba338a44da5027`, identisch zum Releasepaket. [Downloadnachweis](evidence/0.4.1/published-download.json).

[Performance-Architektur, Messwerte, Status je Engine-Bereich und Grenzen](PERFORMANCE-ENGINE.md). Die Engine ist in den genannten Teilen implementiert; der gesamte umfangreiche Master-Prompt ist damit nicht als vollständig erfüllt ausgewiesen. GPU-Puffer, eigene Chunk-Scheduler und zusätzliche Culling-Hooks wurden ohne Nachweis einer sicheren Verbesserung nicht eingeführt.


## Update 0.4.0 RC1 — 20. Oktober 2026

| Prüfung | Ergebnis |
| --- | --- |
| Frontend | 69 Tests in 13 Dateien bestanden: unter anderem Modellgeometrie, Farbverlauf, Pixeltextur und Keyframe-Interpolation. |
| Rust / Windows | 50 Tests bestanden; 8 ausdrücklich optionale Online-Checks übersprungen. Gespeicherte benannte Leistungsvorlagen werden nativ validiert und behalten. |
| Produktionsbuild | TypeScript/Vite erfolgreich, 2.313 Module. Optimierter Tauri-Release und NSIS-Installer 0.4.0 erfolgreich gebaut. Die zuvor übersehene lokale Rust/MSVC-Toolchain wird mit `scripts/local-toolchain.ps1` aktiviert; die historische Aussage, Cargo sei nicht vorhanden, gilt nicht. |
| Fabric / echtes Minecraft | Core 0.4.0 gebaut. `runClientGameTest` in Minecraft 26.2 erfolgreich: 45 Katalogmodelle, acht Gesten und ein eigenes Keyframe-Modell gerendert. `VOTEX_RENDER_SMOKE_PASS 0.4.0`. Die Cape-/Kopfschicht-Mixins wurden im Spiel geladen. [Kopfbedeckung](evidence/0.4.0/core-frog-hat.png), [eigene Animation](evidence/0.4.0/core-custom-keyframe.png). |
| Native EXE | Optimierte EXE auf diesem Rechner gestartet, getrennte QA-Daten. Vollflächiger Editor, Teil hinzufügen, Achse ziehen, einzelner Undo-/Redo-Schritt, Dropdown mit Pfeiltaste/Enter und private Speicherung eines Modells mit 64 Texturfarben sowie Verlauf bestätigt. Die Produktionsprofile wurden nicht verändert. |
| Endgültige Desktop-Datei | `Desktop/Votex Client 0.4.0 RC1/Votex Client.exe`, SHA-256 `084fd43631ba12316736f96abab86c77945ccc9ee114917b224ed442c8d685a8`: direkt gestartet, nochmals Minecraft bis zum Hauptmenü geladen und über „Quit Game“ beendet. Der Launcher stellte sein Fenster anschließend automatisch wieder her. |
| Offline-Start aus der EXE | Nach Korrektur der Win32-Langpfade wurden Fabric API und Core installiert, Java 25 gestartet und Minecraft 26.2 bis zum Titelbildschirm geladen. Der Core schrieb die passende `title-screen`-Startbestätigung. Das Spiel wurde über „Quit Game“ beendet. [Native Aufnahme](evidence/0.4.0/native-offline-title.png). Das ist ein tatsächlicher Launcher-Spielstart ohne Microsoft-Konto, kein Ersatz für den offenen Online-Kontotest. |
| Letzte Windows-Regressionen | Zwei zusätzliche Tests für atomaren Ersatz bei Pfaden über 260 Zeichen und den ursprünglichen Fehlercode wurden kompiliert. Windows App Control blockierte diesen neuen Debug-Testläufer (4551); sie konnten deshalb nicht ausgeführt werden. Der optimierte App-Build mit derselben Pfadkorrektur startete und installierte das Spiel erfolgreich. Die 50 vorherigen Windows-Tests sind von diesem blockierten Nachtest getrennt zu betrachten. |
| Konten / Veröffentlichung | Keine Microsoft-Anmeldung und kein öffentlicher Community-Upload während dieser Prüfung. Minecraft-App-Freigabe steht weiterhin aus. Unsignierte EXE; keine Schutzrichtlinien verändert, keine bezahlten Dienste. |
| Öffentlicher Download | Sites-Version 5 veröffentlicht. Setup erneut per HTTPS geladen: HTTP 200, 9.541.806 Bytes, SHA-256 `6957d912bcb0c63173eba86e7e6e33e454e5c63bce9971048633c0306a4d3783`, identisch zum Releasepaket. [Nachweis](evidence/0.4.0/published-download.json). Keine Azure-/R2-Ressource hinzugefügt. |

Der Editor bietet direktes Modellieren und begrenzte Modell-/Körper-Keyframes. Vollständige Blockbench-Kompatibilität, beliebige UV-/Rig-Werkzeuge und ein fehlerfreier Betrieb in allen Kombinationen sind nicht nachgewiesen. Die Version wird als Releasekandidat, nicht als garantiert fehlerfreie Endversion, verteilt.

## Update 0.3.11 — 20. Oktober 2026

| Prüfung | Ergebnis |
| --- | --- |
| Frontend / Konfiguration | 65 Tests bestanden; darunter Presetmigration, getrennte Designstufen, Emote-Keyframes und Charakterdarstellung. |
| TypeScript / Vite | Strikter Produktionsbuild erfolgreich; 2.306 Module transformiert. |
| Fabric Client Core | `compileClientJava` erfolgreich mit Minecraft 26.2 / Fabric. OpenGL-Aufnahme wurde kompiliert, aber noch nicht in einem laufenden Spiel ausgelöst. |
| Rust / Tauri | `cargo check` konnte in dieser Arbeitsumgebung nicht gestartet werden: `cargo.exe` ist weder in PATH noch unter den geprüften Standardpfaden vorhanden. Die neue `profile_covers`-Tauri-Anbindung und Offline-Startänderung sind daher noch nicht kompiliert. |
| Skin Pixel Editor | Browser-QA auf `127.0.0.1:1425`: 64×64-Textur angezeigt, Pinselstrich aktiviert „Rückgängig“, Undo deaktiviert es und aktiviert „Wiederholen“. Der Teststrich wurde rückgängig gemacht und nicht gespeichert. WebGL war in diesem Browsergerät nicht verfügbar; die Live-3D-Skinvorschau ist hier nicht bestätigt. [Prüfaufnahme](evidence/0.3.11-skin-pixel-editor.png). |
| Design Studio | Alle sechs Schnellschalter sichtbar. Ultra Performance wählte sich korrekt aus und stellte Glas, Licht, Leuchten, Reflexionen, Federdruck und Atmosphäre aus; danach stellte Performance seine vorher gespeicherten Werte wieder her. |
| Minecraft / Microsoft / Offline | Kein Minecraft-Spielstart, keine Microsoft-Anmeldung und kein Spielserverlauf in dieser Prüfung. Publisher-Freigabe steht weiter aus. |
| Desktop-EXE / Installer | Für 0.3.11 wurde keine EXE gebaut: Cargo fehlt. Der zuletzt verifizierte Installer bleibt 0.3.8; ein aktueller nativer Laufzeittest fehlt. Es wurden keine Windows-Schutzfunktionen geändert und keine kostenpflichtigen Dienste genutzt. |

Die Browserprüfung nutzte ein separates lokales Testprofil. Die sechs Schnellschalter und das Rückgängigmachen wurden dort nur mit den QA-Einstellungen bedient. Die Spielaufnahme, das Auslesen des Titelbilds in der nativen Launcher-UI, der Offline-Start und die Presetwerte in einem laufenden Minecraft-Prozess benötigen einen späteren nativen Test.

## Update 0.3.10 — 20. Oktober 2026

| Prüfung | Ergebnis |
| --- | --- |
| Frontend / Konfiguration | 58 Tests bestanden. Zusätzlich zu Medien-/Fehlerprüfungen: Migration der Charakteransicht unter Erhalt von Profilen/Skins/Looks, ungültige Szenenimporte, endliche Szenengrenzen, einmalige Freigabe von Geometrie/Materialien, abschaltbare Wolkenbewegung und der Renderer-Pausenzeitpunkt nach dem Draw-Callback. |
| TypeScript / Vite | Strikter Produktionsbuild erfolgreich; 2.300 Module transformiert |
| Rust / Windows | 48 Tests bestanden; 8 optionale Online-Checks bewusst übersprungen. Charakteransicht wird validiert; ältere Einstellungen ohne dieses Feld bleiben lesbar. Launcher-Medien bleiben aus Profil-Core-Dateien ausgeschlossen. |
| Native Debug-Anwendung | Tauri-Windows-Debug-Build nach den Änderungen an Charakteransicht und Rendering-Lebenszyklus erfolgreich: `apps/launcher/src-tauri/target/debug/votex-launcher.exe`. Keine neue Installation und kein nativer Laufzeitnachweis dieses letzten Builds; eine zuvor gebaute 0.3.10-EXE wurde beim Start durch Windows App Control blockiert. |
| Debug-Artefakt | SHA-256 des zuletzt gebauten Artefakts: `23519E461B39209F07FFFB119D7524C33D5FDEE7EED266A788982AB799C1F7A6`. Dieser Hash kennzeichnet den Build und ist kein Laufzeit- oder Signierungsnachweis. |
| Sichtbare Vorschau | Ein leeres Browserprofil auf eigenem Loopback-Port zeigte alle neuen Atmosphären-Regler. Es wurde keine vorhandene Browser- oder Desktop-Konfiguration verändert. |
| Fehleranzeige / Dialoge | Auf einer weiteren getrennten Loopback-Origin wurde eine tatsächlich fehlerhafte JSON-Datei über „Farbwelt importieren“ ausgewählt. Die deutsche Fehlermeldung erschien im Mitteilungsverlauf; „Technische Details“ enthielt unverändert `INVALID_JSON: Unexpected end of JSON input`. Tab erreichte die Details, Enter öffnete sie. Bei 1280 px Fensterbreite wurden 620 px für normale Dialoge, 780 px für den Profilassistenten und 1080 px für das Kosmetik-Atelier bestätigt. Assistent und Editor wurden ohne Speichern geschlossen. [Prüfaufnahme](evidence/0.3.10-localized-import-error.png). Dies ist ein Frontend-Test, kein nativer Windows-Laufzeitnachweis. |
| Optimierter Release / Installer | Noch offen: der frühere 0.3.9-Releaseversuch wurde von Windows App Control am `vswhom-sys`-Buildskript blockiert. Für 0.3.10 wurde kein NSIS-Installer gebaut. Keine Richtlinie wurde geändert. |

Charakteransicht, getrennte Browser-Origin auf Port 1424: Wolkeninsel, Lichtstudio und Nachtgarten wurden gerendert. Szenen-/Lichtwechsel behielten dieselbe Viewer-ID. Nach „Gespeichert“ und Neuladen waren `scene / night-garden / daylight` erhalten. „Sitzen“ zeigte eine tatsächliche Pose bei ausgeschalteter Ruheanimation. Ziehen auf dem Canvas änderte den Orbit; „Modell vollständig einpassen“ führte zur Vorderansicht zurück. Bei 150 % GUI-Größe in einem 1280-px-Browserfenster blieb `.page-content` ohne horizontalen Überlauf (Client-/Scrollbreite jeweils 781 logische Pixel). Anschließend auf 100 % zurückgesetzt. [Prüfaufnahme](evidence/0.3.10-character-scene.png). Kameraverschiebung mit rechter Maustaste, native Fensterzustände, echte Windows-Hoch-DPI/kleine Fenster und GPU-/FPS-Messungen sind noch nicht manuell geprüft. Kein Konto, Spielstart oder öffentliches Hochladen.

Stand: 20. Oktober 2026. Windows App Control blockierte bereits einen erneuten Start der Debug-EXE direkt aus dem Checkout. Die vorherige isolierte 0.3.10-Prozessprüfung fand vor den aktuellen Änderungen statt. Die bereits laufende getrennte 0.3.6-Instanz ließ sich sichtbar prüfen; sie ist kein UI-Nachweis für den aktuellen Quellstand. Echtes natives Auswählen, Abspielen und Pausieren einer Videodatei bleibt offen. Der verifizierte Desktop-Installer bleibt 0.3.8. Keine Richtlinie wurde geändert oder umgangen.

## Update 0.3.9 — 20. Oktober 2026

| Prüfung | Ergebnis |
| --- | --- |
| Frontend / Konfiguration | 37 Tests bestanden; native Fenstersteuerung und Startmarke besitzen jetzt deutsche zugängliche Namen, wenn Deutsch ausgewählt ist. |
| TypeScript / Vite | Strikter Produktionsbuild erfolgreich; 2.295 Module transformiert |
| Rust / Windows | 45 Tests bestanden; 8 optionale Online-Checks bewusst übersprungen |
| Nativer Debug-Build | Tauri-Windows-App erfolgreich ohne Installer gebaut und aus `target/debug/votex-launcher.exe` mit einem neuen, getrennten Datenordner gestartet. Windows-Prozesspfad und Fenstertitel `Votex Client` bestätigt; der Testordner enthält nur neu angelegte Launcher-Datenverzeichnisse. Keine Kontoanmeldung, kein Spielstart und keine Mod-Installation. |
| Optimierter Release / Installer | **Blockiert:** Windows App Control verweigerte die Ausführung des `vswhom-sys`-Buildskripts (Windows-Fehler 4551). Keine Schutzrichtlinie wurde geändert oder umgangen; es entstand kein 0.3.9-Installer. |
| Verifizierter Desktop-Installer | Weiterhin `C:\Users\wieti\Desktop\Votex Client 0.3.8 Setup.exe`; 9.301.459 Bytes; SHA-256 `B183D5C229F544FE3148AE933D213160914BC021FB226665FB13A3719BEF9B6A`; Authenticode `NotSigned` |

Stand: 20. Oktober 2026. Das Debug-Fenster lässt sich nicht über die native UI-Erfassung dieser Sitzung visuell prüfen. Die sprachlichen Namen sind im Produktionsbundle enthalten und TypeScript-/Frontend-Prüfungen bestanden.

## Update 0.3.8 — 20. Oktober 2026

| Prüfung | Ergebnis |
| --- | --- |
| Frontend / Konfiguration | 37 Tests bestanden; Emote-Keyframes, Gelenkwinkel und vertikale Bewegung lassen sich verlustarm in die Zeitleiste einlesen und wieder speichern. Private Gesten werden in-place bearbeitet; veröffentlichte Gesten erhalten beim Bearbeiten eine neue private Kopie. Bearbeitete lokale Importe werden als eigene Beiträge verwaltbar. |
| TypeScript / Vite | Strikter Produktionsbuild erfolgreich; 2.295 Module transformiert |
| Rust / Windows | 45 Tests bestanden; 8 bewusst ausgeschlossene Online-Checks |
| Nativer Windows-Build | Optimierter Release mit NSIS-Installer erfolgreich; 9.303.089 Bytes |
| Desktop-Installer | `C:\Users\wieti\Desktop\Votex Client 0.3.8 Setup.exe`; 9.301.459 Bytes; SHA-256 `B183D5C229F544FE3148AE933D213160914BC021FB226665FB13A3719BEF9B6A`; Authenticode `NotSigned` |
| Isolierte native Oberfläche | Finaler 0.3.8-Build startete im getrennten Testdatenordner. Private Geste „QA Aufstieg“ mit drei Keyframes erstellt und gespeichert, danach erneut geöffnet. Name, 0/50/100-%-Keyframes, −135° Armwinkel und 1,2 px vertikale Bewegung waren erhalten. Ateliermodell-Speichern und Wiederöffnen wurde im 0.3.7-Build ebenfalls isoliert verifiziert. Keine Anmeldung, Veröffentlichung oder Spielstart. |

Stand: 20. Oktober 2026. Die auslieferbare Desktop-Datei und der Installer aus dem Release-Verzeichnis haben denselben SHA-256-Hash.

## Update 0.3.7 — 20. Oktober 2026

| Prüfung | Ergebnis |
| --- | --- |
| Frontend / Konfiguration | 32 Tests bestanden; neu: gespeicherte Ateliermodelle lassen sich erneut öffnen. Private Änderungen aktualisieren das lokale Modell; Bearbeitungen veröffentlichter Modelle werden als neue private Kopie gespeichert und überschreiben das Original nicht. |
| TypeScript / Vite | Strikter Produktionsbuild erfolgreich; 2.294 Module transformiert |
| Nativer Windows-Build | Optimierter Release mit NSIS-Installer erfolgreich; 9.256.362 Bytes |
| Desktop-Installer | `C:\Users\wieti\Desktop\Votex Client 0.3.7 Setup.exe`; SHA-256 `CCEFA8B1B440206AC6BBF4B21932A62C36EBDB590DF836C2A2546F78C2E69ACC`; Authenticode `NotSigned` |
| Isolierter nativer Test | 0.3.7 im leeren Testdatenordner ohne Anmeldung und ohne Spielstart geöffnet. Ein privates Modell mit Fläche und Kugel gespeichert, unter „Meine Beiträge“ gefunden und wieder geöffnet; beide Teile, Name und Kategorie waren erhalten. Anschließend Editor geschlossen. Keine Veröffentlichung und keine Kontoaktion. |

Stand: 20. Oktober 2026. Installer am Desktop und Release-Installer haben denselben SHA-256-Hash. Dieser Schritt verifiziert den lokalen Atelierablauf; Microsoft-/Minecraft-Freigabe, Kontoanmeldung und tatsächlicher Spielstart bleiben davon getrennte offene Prüfungen.

## Update 0.3.6 — 20. Oktober 2026

| Prüfung | Ergebnis |
| --- | --- |
| Frontend / Konfiguration | 30 Tests bestanden; einschließlich sicherem Kosmetik-JSON-Import, Geometriegrenzen und Materialprofilen |
| TypeScript / Vite | Strikter Produktionsbuild erfolgreich; 2.294 Module transformiert |
| Rust / Windows | 45 Tests bestanden, 8 ausdrücklich optionale Online-Checks übersprungen |
| Nativer Windows-Build | Optimierter Release mit NSIS-Installer erfolgreich; 8,87 MiB |
| Desktop-Installer | `C:\Users\wieti\Desktop\Votex Client 0.3.6 Setup.exe`; SHA-256 `069E3B045EBC34F1297F03BC7AB9407EC6842ACF08AE39FE201A74CD8DB412FB`; Authenticode `NotSigned` |
| Native Oberfläche | 0.3.6-Preview: im leeren, getrennten Testdatenordner visuell geöffnet. Kosmetik-Galerie mit 12 Modellen, Quellen/Lizenzen und Kategorien sowie Atelier geprüft. Kugel hinzufügen, Rückgängig/Wiederholen und Teil duplizieren sichtbar bestätigt; lange Einstellungen scrollen im Dialog. Testmodell anschließend abgebrochen; nichts veröffentlicht, keine Anmeldung und kein Spielstart. 0.3.5-Emote-Rad wurde zuvor ebenfalls im isolierten Profil geprüft. |

Stand: 20. Oktober 2026, Revision mit eigenem Standardskin, festen GUI-Stufen und Creator Collection. Quell-/Dateitests, Browser, nativer Prozess und persönlicher Minecraft-Start werden getrennt bewertet.

## Aktuelle Ergebnisse

| Prüfung | Ergebnis / Beleg |
| --- | --- |
| Frontend / Konfiguration | 26 Tests bestanden; `evidence/refinement-frontend-tests.txt`. Schema/Migration, 32-Chunk-Cinematic, kontinuierliche Gesten und 45 identische begrenzte Core-/Launcher-Modelle einschließlich Original-UV-Texturen |
| TypeScript / Vite / Tauri | Strikter Produktionsbuild und optimierter Windows-Release-Build mit NSIS-Installer erfolgreich; `evidence/refinement-release-build.txt` |
| Rust / Windows | 41 Tests bestanden, 8 bewusst ausgeschlossene Online-Tests; `evidence/refinement-rust-tests.txt`. Der Rust-Befehlscode blieb bei der anschließenden Skin-/GUI-/Creator-Revision unverändert |
| Fabric Core | 8 Java-Tests bestanden; abschließender Build erfolgreich. `evidence/refinement-core-build.txt` und Gradle-Testberichte |
| Tatsächliches Minecraft | 45 Modelle, 8 Gesten und Neutralpose in separatem Fabric-ClientGameTest ausgeführt; 54 frische Spielaufnahmen. `evidence/creator-core-render-test.txt` / `creator-core-run.json` |
| Native EXE | Exakter Hash, Prozess-/Fensterzustand, Desktop-Verknüpfung, fehlender localhost-Listener sowie Profilbestand und bestätigte Benutzeränderungen werden in `evidence/refinement-native-smoke.json` festgehalten |

## Standardskin und Modelle

Der PNG-Skin des Projektinhabers wurde bytegleich übernommen. Im Launcher steht „Votex Client“ als echte 3D-Bildmarke über dem Spieler; er ist auch im Studio-PNG enthalten. Ein gewählter lokaler Skin bzw. Account-Skin hat weiter Vorrang. Es wurde kein Microsoft-Konto-Skin geändert.

Neun frei lizenzierte Modelle von DevMops, Michael Fuchs, Poly by Google, J-Toastie, Quaternius und Kenney sind tatsächlich importiert. Quellen, Lizenzlinks, Anpassungen und Originaldateien sind dokumentiert; siehe `CREATOR-CREDITS.md`. Die standardmäßige Auswahl zeigt diese neun Modelle und drei Votex-Capes. Die alten Atelier-Entwürfe und vorhandenen Ausrüstungs-IDs bleiben erreichbar.

Alle neun Creator-Modelle wurden im Browser nacheinander anprobiert, bis zur fertigen Geometrie-/Texturladung abgewartet und über den echten Aufnahme-Dialog als transparente PNGs ausgegeben. Alle Modellnamen stimmten mit der Anprobe überein. Die tatsächlichen PNGs und Kontaktbogen liegen unter `evidence/refinement/creator-tryon/`; die Galerie unter `creator-gallery.png`. Anschließend wurden die neun neuen Modelle im echten Minecraft-Renderer visuell geprüft, einschließlich UV-Flügeltextur, Mützenpassform, Kopfhörer, Brillen und Schulterfuchs. `evidence/refinement/creator-core-render/` enthält alle 54 aktuellen Spielaufnahmen. Studio- und Minecraft-Beleuchtung sind verschieden; es wird keine identische Materialwirkung behauptet.

## GUI-Größe und kleine Fenster

Fünf Stufen: 75, 90, 100, 125 und 150 %. Die Auswahl ist ein lokaler Entwurf; erst „Größe übernehmen“ ändert die aktive Oberfläche. Beim Auswählen jeder Stufe blieb der tatsächliche CSS-Zoom unverändert, erst beim Anwenden wechselte er. Der jederzeit sichtbare Größenknopf oben setzt auf 100 % zurück. Bisherige Zwischenwerte werden beim Laden erhalten.

Die vorherige doppelte Größenkompensation und doppelte Schrift-Skalierung wurden entfernt. Das Layout richtet sich nach der verbleibenden Inhaltsbreite einschließlich GUI-Zoom. Native Mindestgröße ist jetzt 800 × 560.

Bei 800 × 560 wurden neun tatsächlich eingeblendete Seiten in allen fünf Stufen geprüft (45 Kombinationen). Die Prüfung wartet auf die innere Route und liest deren echte Überschrift; eine erste Messung während des ausgehenden Seitenübergangs wurde verworfen. Kein horizontaler Überlauf des Seiteninhalts. Der verbindliche Trace ist `evidence/refinement/window-layout-audit.json`. Das ist kein Beweis aller Windows-DPI-/Monitor-Kombinationen.

Der Profil-Assistent wurde bei 150 % im kleinen Fenster geöffnet und die Versionsauswahl bis zum Modloader bedient. Kopf und Weiter/Zurück bleiben im Fenster; nur der Inhalt scrollt. Bei einem Schrittwechsel beginnt der Inhalt oben. Screenshots `wizard-800-150.png` und `wizard-loader-800-150.png`. Normales vertikales Scrollen bleibt bei großen Bedienelementen bewusst möglich.

## Bereits geprüfte übrige Abläufe

- Profilbearbeitung erhält bestehende IDs; neueste Versionen zuerst, Versionssuche, Loader-/Qualitätswahl und Quellprofil für eine Mod-Übernahme wurden bedient.
- Profilbezogene Mod-Verwaltung unabhängig vom Play-Profil, konkrete Installationsziele und echter Zeiger-Drag Main → QA bis zur Übernahmeprüfung. Separater Online-Test mit realen Sodium-Dateien 1.21.1 → 1.21.4, Hashprüfung/Rollback/erhaltener Quellwelt: sechs Tests unter `evidence/profile-transfer-tests.txt`.
- Cinematic zeigt 32 Sichtweite / 12 Simulation / 8 GB / Fabulous / 120 FPS. Alte eigene Werte bleiben unverändert. Exakte 144 FPS und Ausschalten/Einschalten des Limits wurden bedient.
- Strg+K-Suche, feste Inhaltsordner, helle/dunkle Gestaltung, Konten-/Quellen-/Mitteilungsdialoge, Recovery-Erklärungen und alle Seiten wurden im Browser geprüft. Frühere Screenshots bleiben als Verlauf erhalten.
- Gesten-Einstieg, Wechsel, Wiederholung und Ausblenden halten denselben Viewer/Kamerazustand; gemeinsame mathematische Frontend-/Java-Tests prüfen Kontinuität. Die acht Gesten sind zusätzlich im Spiel gerendert. Statische Bilder sind keine subjektive Bewegungsabnahme und kein Benchmark.

## Native Rückmeldung und offene Grenzen

Der Nutzer bestätigte zuvor die sichtbare 0.3-Version, Berglandschaft, Cloud-Feathers-Anprobe und eine gespeicherte Aufnahme. Die Gegenprüfung fand bislang keine PNG am erwarteten nativen Speicherort. Die Frage nach dem tatsächlich geöffneten Ordner ist offen. Deshalb ist der native Klick-/Dateiablauf trotz erfolgreichem Backend-Dateitest und Browser-PNGs nicht abschließend bestätigt. Der Dialog zeigt nach einem erfolgreichen nativen Speichern nun zusätzlich den vollständig zurückgegebenen Dateipfad.

Ein reagierender Windows-Prozess samt Fenster-Handle ist kein direkter nativer Sicht-/Bediennachweis. Die vorangehende Release-Datei wurde durch Windows Code Integrity abgewiesen; der Befund bleibt als `evidence/history-0.3-blocked-release.json` erhalten. Keine Richtlinie, Vertrauensstellung oder Schutzfunktion wurde geändert. Die Anwendung bleibt unsigniert; der aktuelle konkrete Startzustand steht im nativen Nachweis.

Der eigene Minecraft-AppID-Antrag wurde eingereicht; Genehmigung und vollständiger persönlicher Microsoft-/Besitz-/Token-/Spieltest sind weiterhin unbestätigt. Letzter persönlicher Fehler: `MINECRAFT_APP_NOT_APPROVED`. Der isolierte Fabric-Test verwendet eine Entwicklungs-Testidentität, keine persönliche Kontoanmeldung. Es gibt keine fremde Client-ID oder Produktions-Offline-Umgehung.

CurseForge benötigt weiter den fehlenden eigenen API-Schlüssel. Authentifizierte CurseForge-Suche/Downloads, alle Version-/Loader-Kombinationen, NeoForge, tatsächliche Shader-Darstellung, Installer-Upgrade/Deinstallation und reale FPS-/RAM-Vergleichsmessungen bleiben offen. Es wurden keine kostenpflichtigen Assets, Zertifikate, Dienste oder Azure-Ressourcen gekauft. Keine Minecraft-Binärdateien, Konten, Tokens oder Java-Laufzeiten liegen in den Release-Archiven.

Der Nutzer bestätigte während der abschließenden Prüfung ausdrücklich, dass er die zwei aktuellen „Mein Abenteuer“-Profile selbst beim Testen geändert hat. Beim ersten Creator-Start waren noch drei Profile unverändert vorhanden. Dieser spätere beabsichtigte Wechsel wird nicht als verlorenes Profil oder weiterhin identischer Bestand ausgegeben. Die aktuellen Daten bleiben erhalten.
