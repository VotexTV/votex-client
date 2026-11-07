# Votex Client — Produktüberblick

## Zweck und aktueller Stand

Votex ist ein Windows-Launcher für Minecraft Java Edition mit einem separat gebauten Fabric-Client-Core. Das Produkt verbindet Profil- und Modverwaltung, Spielstart und Wiederherstellung mit Skin-, Cosmetic- und Emote-Werkzeugen sowie einem anpassbaren Ingame-Menü. Das aktuelle Quellprojekt ist ein **5.0 Beta 2 / laufender 5.0-Kandidat**, keine fertige oder freigegebene Version 5.0. Die V6-Arbeit ist eine zusätzliche, optionale Runtime-Erkundung und kein Ersatz für den etablierten Java-Weg.

Statusbegriffe in dieser Übergabe:

- **Implementiert:** Code ist im aktuellen Quellbaum vorhanden. Das bedeutet nicht automatisch, dass alle Geräte, Konten, Versionen oder veröffentlichten Pakete abgenommen sind.
- **Teilweise:** Kernpfad oder Teilmengen existieren; bekannte Lücken oder ausstehende End-to-End-Prüfungen sind genannt.
- **Geplant / nicht belegt:** Ziel oder Entwurf, der nicht als aktuelle, funktionierende Implementierung dargestellt werden darf.

Bei widersprüchlichen Versions- und Testaussagen gilt der aktuelle Quellcode vor undatierten oder älteren Statusseiten; für konkrete Abnahme gilt ein aktueller, zum Binärartefakt passender Prüfbeleg. `README.md`, mehrere `docs/*20261025*`-Dateien und `docs/TEST-RESULTS.md` enthalten historische Zwischenstände.

## Produktbereiche

| Bereich | Stand | Produktumfang und Grenze |
| --- | --- | --- |
| Launcher-Oberfläche | Implementiert, laufende Überarbeitung | React-Oberfläche mit Seiten für Spielen, Profile, Mods, Skins, Kosmetik, Animationen, Leistung, Wiederherstellung und Design/Einstellungen. Liquid-Glass-Stil, Animationen, Suche, Benachrichtigungen und Installationsfeedback sind vorhanden. Die 5.0-Beta benötigt noch visuelle und Paketabnahme. |
| Minecraft-Profile | Implementiert | Getrennte Instanzen und profilbezogene Loader-/Version-/Moddaten, Profilanlage, Auswahl, Verwaltung und Start. Bearbeitung oder Modänderungen während laufender Downloads/Spiele sind bewusst gegen Profiländerungen gesperrt. Sicheres Wechseln/Neustarten wird durch die Benutzeroberfläche vermittelt. |
| Java-Spielstart | Implementiert | Download/Prüfung offizieller Spieldateien, Loader-Vorbereitung und Java-Start; Minecraft-Prozesse bleiben außerhalb des Renderers. Offline-Identität ist nur für Offline-/Einzelspieler bzw. Offline-Server. Sie umgeht keine Microsoft-Authentifizierung für Online- oder lizenzpflichtige Dienste. |
| Microsoft-Konten | Implementiert, externe Freigabe offen | Device-Flow und Kontoverwaltung sind im Rust-Backend angelegt; Refresh-Tokens liegen im Windows Credential Manager und werden nicht an React zurückgegeben. Die Minecraft-AppID-Freigabe war im zuletzt dokumentierten Stand nicht bestätigt. Echte Freigabe und authentifizierte End-to-End-Starts sind deshalb nicht pauschal als abgenommen zu werten. |
| Offline-Spielername | Implementiert | Persistenter Offline-Name, getrennt von Microsoft-Konto und nur beim Offline-Spielstart verwendet. Beleg und Umfang: `docs/SLIDERS-NAVIGATION-OFFLINE-2026-11-06.md`. |
| Mods, Modpacks und Shader | Implementiert, providerabhängig | Modrinth-Suche/Abhängigkeiten, Downloads mit Integritätsprüfung, profilbezogene Installation, Deaktivierung/Entfernung, Modpacks und Startmod-Voreinstellungen. CurseForge ist ein zusätzlicher Adapter, benötigt aber gültige Provider-Berechtigung/Schlüssel und hat dadurch eigene offene Akzeptanzgrenzen. Mod-Änderungen werden für einen nächsten Start vorbereitet; kein Hot-Loading-Versprechen. |
| Leistung und Minecraft-Optionen | Implementiert, Synchronisationsumfang weiter zu prüfen | Presets und profilbezogene JVM-/Spieloptionen, darunter FPS, GUI-Skalierung und FOV. Spieloptionen werden mit Minecraft-Dateien synchronisiert und können in Launcher-Zustand einfließen. Verschiedene Spielversionen und externe Änderungen müssen anhand `game_options.rs`, `performance.rs` und aktueller Tests geprüft werden. |
| Skins, Cosmetics und Creator | Implementiert, 5.0-Abnahme offen | Skin-Vorschau/-Verwaltung, 3D-Cosmetic-Katalog, eigene Modelle/Emotes, Animationstimeline und Vorschau. Launcher- und Core-Daten teilen ein JSON-/Presetformat. Katalogabdeckung oder gerenderte Kontaktbögen beweisen keine fehlerfreie Kombination jedes Assets, jeder Animation, First-Person-Pose oder Mehrspielerlast. Drittanbieter-Assets haben eigene Herkunfts- und Lizenzhinweise. |
| Cosmetics im Spiel | Teilweise | Auswahl, Vorschau, Skin/Cape/Modell-Presets und Wiedergabe existieren. Multiplayer-Relay und Zustimmung/Übertragung sind implementiert und in isolierter Zwei-Client-Loopback-QA geprüft. Eine globale Community-Veröffentlichung/Verfügbarkeit ist nicht dadurch belegt; authentifizierter öffentlicher Betrieb bleibt gesondert zu konfigurieren und abzunehmen. |
| Emotes | Teilweise | Integrierte und benutzerdefinierte Emote-Definitionen, Rad, Wiedergabe und First-Person-Pfad sind vorhanden. Aktuelle 5.0-Arbeit korrigiert Rig-/Zeit-/Armkamera-Verhalten, benötigt jedoch noch kontinuierliche First-/Third-Person-, FOV-, Classic-/Slim- und Multiplayerprüfung. |
| Ingame-Mod-Menü | Implementiert, Modulabdeckung uneinheitlich | Eigenes Glas-Menü mit Navigation, Suche, anpassbaren HUD-Elementen und Modulen, unter anderem FPS/Informationen, Light-Level-Darstellung, Item Physics, Appearance, Emotes, Mods/Shader und soziale Bereiche. Eine Modulkarte im Menü bedeutet nicht, dass jedes Modul gleich tief oder in jedem Spielkontext fertig ist. Item Physics ändert nur lokale Clientdarstellung, nicht Serverphysik. |
| HUD-Editor und Persistenz | Implementiert | Elemente positionieren/anpassen, Layouts speichern und Einstellungen profilbezogen führen. Präzises Pixelraster, Ausrichtung/Snapping und alle Skalierungen sind laufende Qualitätsbereiche; UI-Auswahl-/Drag-Prüfungen sind von produktiver Spiel-FPS-Messung getrennt. |
| Wiederherstellung | Implementiert | Sicherungspunkte, Startfehlerdiagnose und Wiederherstellungs-/Rollback-Flows. Backups sollen nicht mit einem Beweis gleichgesetzt werden, dass beliebige fremde Dateien oder Welten verlustfrei rücksetzbar sind. Vor destruktiven Arbeiten immer Bestand und Restore-Pfad verifizieren. |
| Freunde, Community und Voice | Teilweise | Social-Daten/Relay, Zustimmungsabläufe, lokale Freunde-/Serveraktionen, Self-Host-Vorbereitung und Simple Voice Chat Integration existieren in unterschiedlichem Reifegrad. Loopback-Verbindung ist kein öffentlicher Server. Reale Mikrofon-/Lautsprecher-Rundreise wurde vom Nutzer auf später verschoben. |
| Rust-Native-Runtime | Experimentell/teilweise, V6-Arbeit | Für neue Profile gibt es jetzt zusätzlich „Horizon · Rust“ neben Java. Votex installiert das offizielle signierte Horizon-Nightly getrennt, übergibt die Microsoft-Sitzung über stdin und bereitet Minecraft-26.3-Ressourcen vor. Die externe Launch-API ist experimentell; Fabric/Java-Mods, Votex-Core/Cosmetics/Emotes und Offline-Start werden dort nicht unterstützt. Ein FPS-Vorteil wurde nicht gemessen. Java bleibt der Standard- und Kompatibilitätspfad. Siehe `roadmap.md`, `docs/NATIVE-CLIENT-6.0.md` und `docs/HORIZON-RUNTIME-6.0.md`. |

## Kernnutzerabläufe

1. Profil anlegen, Minecraft-Version und Modloader auswählen, optionale Starter-Mods und Leistungspreset konfigurieren.
2. Assets und Loader über den Launcher vorbereiten, Integrität prüfen, Mods für das Profil verwalten und das Profil starten.
3. Microsoft oder Offline-Modus wählen; Offline-Identität bleibt klar von Microsoft-Kontodaten getrennt.
4. Skin/Cosmetics/Emotes im Launcher auswählen oder gestalten und ihre Presets in die profilbezogene Core-Konfiguration exportieren.
5. Im Spiel das Votex-Menü öffnen, Module und HUD anpassen, Emotes/Cosmetics verwenden und optional soziale/Voice-Funktionen mit Zustimmung nutzen.
6. Nach einem Startfehler Diagnose und vorhandene Wiederherstellungspunkte verwenden.

## Produktprinzipien

- Java + Fabric ist der kompatible Hauptpfad. Andere Loader nur dort versprechen, wo sie im Code für konkrete Minecraft-Versionen umgesetzt sind.
- Profile, Konfigurationen, Tokens und lokale Minecraft-Instanzen gehören in die jeweilige Backend-/Instanzgrenze, nicht in React-UI-Workarounds.
- Nutzer opt-outs und vorhandene Profile bleiben bei Migrationen erhalten. `starterMods`-Defaults gelten für neue Profile, nicht als Grund, gespeicherte Altprofile umzuschreiben.
- Authentifizierung, Mod-Download und öffentliche Community-Übertragung müssen Herkunft, Zustimmung und Integrität berücksichtigen.
- Zwischen UI-Animation, statischem Render-/Build-Test und echter Minecraft-/Multiplayer-Abnahme unterscheiden.
- Keine fremden Kosmetikmodelle ohne Prüfung ihrer Lizenz übernehmen. Herkunft in `docs/CREATOR-CREDITS.md` und `THIRD-PARTY-NOTICES.md` erhalten.

## Quellen für Folgearbeit

Architektur: `docs/ARCHITECTURE.md`; Produktziel und Akzeptanzhistorie: `docs/INGAME-GOAL.md`, `docs/ACCEPTANCE-GATES-20261025.md`; Mods: `docs/MODPACK-INTEROPERABILITY-20261024.md`, `docs/INGAME-CURSEFORGE.md`; Darstellung/Format: `docs/CUSTOM-COSMETIC-FORMAT.md`, `docs/APPEARANCE-MOTION.md`, `docs/CATALOG-COVERAGE.md`; Social/Voice: `docs/COMMUNITY.md`, `docs/INGAME-SOCIAL.md`, `docs/INGAME-VOICE-PROVISIONING.md`, `docs/SELFHOST-VALIDATION.md`; V6: `docs/NATIVE-CLIENT-6.0.md`.
