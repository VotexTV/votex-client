# Votex Client — Entwicklung, Entscheidungen und offene Fehler

## Verlauf in groben Phasen

Die Historie ist aus dem aktuellen Projektbaum, datierten Übergaben und QA-Belegen rekonstruiert. Ältere Featureberichte beziehen sich auf konkrete frühere Builds und sind kein automatischer Nachweis für den heutigen Installer.

### Frühe Prototypen bis 0.3

Auf den anfänglichen Launcher mit Profilen, Skin/Kosmetik-Katalog und einfacherem UI folgten eigener Standardskin, GUI-Skalierung, Creator Collection, 3D-Vorschau und erste Editor-/Animationspfade. Frühe Builds waren bereits an Windows Code Integrity/App Control auf dem Entwicklerrechner gescheitert; spätere Starts auf einzelnen Builds heben diese unsignierte Verteilungsschranke nicht generell auf.

### 0.4.1 / Preview-Zyklus im Oktober 2026

Der Launcher wuchs um isolierte Minecraft-Profile, Fabric-Core-Integration, Mod-/Modpackverwaltung, Performance, Recovery, Microsoft-Login, Offline-Identität, Social/Voice und zahlreiche Ingame-Module. Es gab öffentlich bereitgestellte, konkrete Preview-Artefakte sowie separate interne EXE/Core-Kandidaten. Die im Oktober dokumentierten Prüfsummen und Erfolgszahlen gehören nur zu den dort genannten Dateien und sind nicht auf den aktuellen V5-Quellstand übertragbar.

Ein wichtiger Abwärtskompatibilitätsfehler betraf `starterMods: null`: Rust serialisierte fehlende optionale Felder als JSON-`null`, während das TypeScript/Zod-Schema nur fehlendes/Objekt akzeptierte. Migration und Tests normalisieren diesen Altwert, ohne vorhandene Opt-outs/Profilfelder zu löschen. Referenz: `docs/PROFILE-CONFIG-HOTFIX-20261025.md`, `packages/shared-config/legacy-starter-mods.test.ts`, Rust-Tests in `storage.rs`.

### Ingame-UI und Runtime-Verbesserungen Anfang November

Der Ingame-Core erhielt gebündelte Liquid-Glass-Komponenten/Shaderpfade, elastische Slider, Cursorbeleuchtung, Navigation und HUD-Editor. Die Kategorieanimation wurde darauf ausgerichtet, den äußeren Menücontainer stabil zu halten. Launcher-Navigation/Offline-Spielername und UI-Übergänge erhielten eigene Korrekturen. Die datierten Belege `docs/SLIDERS-NAVIGATION-OFFLINE-2026-11-06.md`, `docs/INGAME-HOVER-FIX-2026-11-06.md` und `docs/INGAME-LIQUID-GLASS-2026-11-06.md` beschreiben konkrete geprüfte Kandidaten, nicht jede spätere Verpackung. OpenGL/RTX-Prüfungen sind keine Vulkan-Abnahme.

### 5.0 Cosmetic-/Emote-Überarbeitung

Der neueste Arbeitsstand steht unter `work/cosmetics2-20261106/V5-STATUS.json` und `V5-ARBEITSSTAND.md`. Umgesetzt wurden unter anderem vier verbundene Cape-Segmente, gemeinsame Bewegungsprofile für 167 Begleiter, Dämpfung/Sekundärbewegung, fünf neue flügelartige Modelle, Geometriecache, Feather-Segmenttexturen, Classic-/Slim-Rig-Checks, first-person Parent-Space-Korrektur und zeitbasierte Emote-Vorschau. Der Status ist explizit `releaseReady: false`; aktuelle/native-27-Arbeit war im letzten dokumentierten Stand noch laufend. Eine neue Release-EXE wurde nicht gebaut und es erfolgte keine Website-Veröffentlichung dieses 5.0-Kandidaten.

## Wichtige technische Entscheidungen

1. **Java-Runtime bleibt der Kompatibilitätspfad.** Fabric/Loader, Java-Mods, Votex-Core, Cosmetics und Emotes setzen sie voraus. Ein optionaler nativer Client darf diesen Pfad nicht stillschweigend ersetzen.
2. **Launcher, Core und Runtime sind getrennte Artefakte.** Jede Freigabe muss UI, Rust-Binary/Installer und eingebettetes/installiertes Core-JAR mit Versions- und Hashbezug ausweisen.
3. **Profil- und Appdaten sind isoliert.** Keine Migration darf ohne Grund Benutzerprofile oder echte Minecraft-Daten zurücksetzen. Vor Import/Export/Restore Staging und Sicherung verwenden.
4. **Einzelne Datei hat einen verantwortlichen Writer.** Launcher schreibt Theme/Preset-Exports; Core schreibt laufende Modulwerte. Dieses Prinzip verhindert gegenseitige Überschreibung.
5. **Accountgeheimnisse bleiben OS-geschützt.** Keine Tokens in UI, WebStorage, Reports oder Community-Payloads.
6. **Offline-Modus ist kein Auth-Bypass.** Er ist begrenzt auf lokale/offline Spielmodi. Keine Aussage über Online-Serverberechtigung.
7. **Assets behalten ihre Lizenz.** Drittanbieter-Modelle werden nicht als Originale oder automatisch frei verwendbar markiert.
8. **Windows-Sicherheitsrichtlinien werden nicht umgangen.** Fehlende Zertifikate sind Release-Hindernis, kein Anlass, App Control oder Smart App Control zu deaktivieren.
9. **UI-/Unit-Beleg ist kein Runtime-Beleg.** Renderer-Fixtures, Mockserver, Loopback-Zwei-Clients und echte angemeldete Nutzer-/Internet-/Audio-Tests müssen getrennt dokumentiert werden.

## Bekannte oder zuletzt offene Punkte

### V5 Cosmetics / Emotes

- `releaseReady` bleibt false. Keine neue V5-EXE im letzten V5-Status.
- Alle verbliebenen Cosmetics benötigen noch Einzelansichten/-prüfung; Texturen, Attachments, Armor-/Helm-Abstand, Classic/Slim, First-/Third-Person und gleichzeitige Wearables systematisch abnehmen.
- Cape-Texturen zusammen mit Sprint/Stop/Turn/Sneak/Swim/Glide visuell prüfen. Verbundene Segmente sind eine Implementierung, kein Beweis, dass jedes Cape vollständig animiert/3D-fehlerfrei ist.
- Tiere brauchen individuelle Kopf-/Ohr-/Schwanzgelenke und Bewegungsgrenzen. Der Schulter-Sprung hat noch keinen kollisions- und terrain-sicheren Abstieg zum Boden. Längliche Tiermodelle können frontal schlecht lesbar sein; Woodland-Fox-Kontakt bleibt offen.
- First-Person-Emotes und Armpose brauchen kontinuierliche Bewegung sowie breiteren FOV-Test. Einzelbilder belegen weder jede Kamera-/FOV-Einstellung noch gehaltene Items oder jede Slim-/Armor-Kombination.
- Multiplayer-Framezeit/Ressourcenmessung auf echter Sitzung fehlt. Lokaler Renderer-Benchmark ist kein Produktionsserver-/Spiel-FPS-Nachweis.

### Launcher, Versionen und Veröffentlichung

- Versionsinkonsistenz: Root NPM `0.4.1-rc.1`, Cargo/Tauri `5.0.0-beta.2`, UI `5.0 Beta 2`, Core-Bundle-Referenz `votex-client-core-0.4.1.jar`; vor Release sauber synchronisieren.
- Manche README-/Test-/Acceptance-Dokumente zeigen Oktober-Previewstände und teils widersprüchliche Historie. An jedem Releasebeleg Artefaktpfad, Zeitstempel und Hash prüfen.
- Keine neue 5.0 Beta-Veröffentlichung auf Website im aktuellen V5-Status belegt. Veröffentlichung erst nach Build, Abnahme, Version/Hash/Notices und gezieltem Website-Schritt.
- Microsoft-AppID-Genehmigung war zuletzt nicht bestätigt (`docs/MINECRAFT-APP-REVIEW.md`); kein authentifizierter Nutzertest daraus ableiten.
- Kein vertrauenswürdiges Code-Signing-Zertifikat eingerichtet (`docs/WINDOWS-SIGNIERUNG.md`). Ausführbarkeit auf verwalteten Windows-Installationen unbestätigt.

### Multiplayer, Community und Voice

- Paired-Client-Belege verwenden isolierte Offline-QA-Identitäten und Loopback. Sie belegen keine Microsoft-authentifizierte öffentliche Sitzung oder globale Hosting-Infrastruktur.
- Self-Host-Paket/Vorbereitung ist kein deployed Relay, DNS, Portfreigabe oder öffentliche Community-Datenbank. Betreiberumgebung erforderlich.
- SVC UDP-Setup/Verbindung ist dokumentiert; physische Mikrofonaufnahme, hörbare Wiedergabe und echte Audio-Rundreise wurden auf Wunsch vertagt.
- Community-Endpunkte, authentifizierter Eigentümer, öffentlich/privat/erneut veröffentlichen brauchen reale Konten und vertrauenswürdigen Betrieb; Fixture-/Stub-Tests sind keine Accountabnahme.

### 6.0 Native Runtime

Java bleibt Standard. Der aktuelle Quellstand ergänzt neben Pomme einen Profiltyp `engine: horizon`: Auswahl bei Profilerstellung, feste Enginebindung je Profil, Horizon-only Runtimewahl, Mod-/Transfer-Sperren, separates Laufzeit-/Spiel-/Home-Verzeichnis, Nightly-Download mit Ed25519-Signatur und SHA-256-Integritätsprüfung, API-Erkennung, Mojang-26.3-Ressourcenvorbereitung und Microsoft-Sitzungsübergabe über stdin. Details: `docs/HORIZON-RUNTIME-6.0.md`.

Das ist derzeit eine **teilweise implementierte, nicht abgenommene Upstream-Integration**. Der Frontend-Produktionsbuild lief erfolgreich. `cargo`/`rustc` fehlten in der Umgebung; Rust/Tauri wurde deshalb nicht kompiliert. Der Horizon-Installer wurde nicht ausgeführt, Minecraft nicht gestartet, Auth/Multiplayer nicht live geprüft und FPS nicht gemessen. Die externe Launch-API ist von Horizon als experimentell dokumentiert. Java-/Fabric-Mods, Offline-Identität, Votex-Core, Cosmetics und Emotes sind keine Horizon-Funktionen. Windows App Control/Smart App Control darf nicht umgangen werden.

## Relevante offene Bugs/Regressionen als historische Hinweise

- `starterMods:null`-Crash in heruntergeladener Preview wurde behoben; bei Konfigurationsfehlern zuerst Migration/Schema und konkrete Datei-/Versionsgeneration prüfen, nicht Benutzerdaten löschen.
- Sodium-Startfehler wurden in älteren Profilen durch einen inkompatiblen Core-/Modstand sichtbar. Nie automatisch annehmen, dass „Mod crashed on startup“ pauschal durch Entfernen von Sodium gelöst wird; genaue Exception, Loader-Version und gemeinsam gebaute Artefakte vergleichen.
- Kategoriewechsel/Slider-/Hover-Probleme wurden in datierten Kandidaten adressiert; neue Änderungen benötigen native Input-/Reduced-motion-/Fenstergrößenprüfung, nicht nur Screenshot.
- Unsigniert/Windows-Block ist Sicherheitsrichtlinienverhalten, keine Evidenz für Malware und kein Grund zum Bypass.

## Belegindex

`docs/INGAME-GOAL.md`, `docs/ACCEPTANCE-GATES-20261025.md`, `docs/TEST-RESULTS.md` und Einzelberichte beschreiben Testgrenzen. Aktuelle V5-Details sind ausschließlich in `work/cosmetics2-20261106/` nachzulesen. Vor Wiederaufnahme neuen Kandidaten und laufende Jobs im Arbeitsstand prüfen; einen früheren Status nie als heutigen Prozesszustand ausgeben.
