# Votex Client — Technische Entscheidungen und Arbeitsabläufe

## Sprachen und Laufzeiten

| Teil | Technologie | Zuständigkeit |
| --- | --- | --- |
| Desktop-Launcher UI | TypeScript, React 19, Vite 8, Motion, Lucide, skinview3d/Three.js | Fenster, Seiten, Vorschauen, Nutzerinteraktion und Aufruf typisierter Tauri-Kommandos. Läuft als WebView; Browserpreview besitzt keine native Minecraft- oder Dateisystemfähigkeit. |
| Launcher-Backend | Rust 2021, Tauri 2 | Lokales Dateisystem, Profile/Instanzen, Downloads, Hashes/Archive, Authentifizierung, Prozessstart, Diagnose und Speicherung. Enthält optionale, getrennte Upstream-Runtimes (Pomme und Horizon). Java-/Minecraft-Prozesse nicht aus React heraus nachbauen. |
| Minecraft Votex Core | Java mit Fabric Loom/Gradle, Mixins und Fabric API | Ingame UI, Renderer, HUD, Module, Animationen, Emotes, Cosmetics, Social-/Voice-Bridges und Spielkonfiguration. Das Core-JAR ist eine getrennte Build-/Release-Einheit. |
| Gemeinsame Produktverträge | TypeScript/Zod-Modelle plus JSON-Dateien; Java/Rust spiegeln Teile davon | Launcher-State, Cosmetics/Emotes, Theme, HUD, Mods und Presets. Änderungen erfordern kompatible Formate/Migrationen über alle beteiligten Sprachen. |
| Self-Host-Komponente | Separater Java/Fabric-naher Relay-/Voice-Betrieb und Paketierungsskripte | Operatorbetrieb für Social-/Voice-Pfade; Paketvorbereitung ist kein öffentlicher Betrieb oder DNS/Netzwerkfreigabe. |

Die Root-Paketversion ist gegenwärtig **0.4.1-rc.1** (`package.json`), Rust/Tauri sind **5.0.0-beta.2** (`Cargo.toml`, `tauri.conf.json`) und die UI zeigt `5.0 Beta 2` (`apps/launcher/src/data/buildLabel.ts`). `tauri.conf.json` referenziert noch `resources/votex-client-core-0.4.1.jar`. Das Root-README und Testreport sind älter. **Vor einem Distributionsbuild muss eine verantwortliche Stelle diese Versions-/Artefaktzuordnung abstimmen.** Nicht allein eine Versionsnummer ändern, ohne Core-JAR, About/UI, Installer, Manifeste und Prüfsummen miteinander zu verknüpfen.

## Wichtige Abhängigkeiten

Root `package.json` und `pnpm-lock.yaml` sind maßgeblich für JS-Abhängigkeiten. Sichtbare Laufzeitpakete umfassen `@tauri-apps/api`, React/React DOM, `motion`, `lucide-react`, `skinview3d`, `three` und Zod. Werkzeuge: TypeScript, Vite, Vitest, Prettier und Tauri CLI. Die genaue installierte Plattform-/Toolchain-Version kann sich ändern; Versionen aus Lockfiles und Gradle-Wrapper verwenden.

Rust-Abhängigkeiten aus `apps/launcher/src-tauri/Cargo.toml`: Tauri 2, serde/serde_json, reqwest mit rustls/json/stream, Tokio, SHA-1/SHA-2/MD5/hex, UUID, Chrono, ZIP/Deflate, Windows Credential Manager über `keyring`/`windows-native`, Base64, Ed25519-Prüfung über `ring`, `open`, futures-util und walkdir; Testhilfe `tempfile`.

Der Java-Core definiert seine Minecraft-, Loader-, Java-, Loom- und Fabric-API-Stände in `minecraft/votex-core/gradle.properties`, `build.gradle`, `settings.gradle`, Wrapper und Fabric-Mod-Metadaten. Diese Stände sind versionsabhängig. Vor Update einer Minecraft-Version Mixins, Mappings, Mod-APIs, Startreihenfolge und Third-Party-Mod-Kompatibilität aus dem aktuellen Baum ableiten.

## Build- und Entwicklungskommandos

Im Projektstamm:

```powershell
pnpm install --frozen-lockfile
pnpm dev
pnpm build
pnpm test
pnpm check:rust
pnpm desktop
pnpm desktop:build
```

Die Scripts stehen in `package.json`. Der Tauri-Frontend-Build führt über `scripts/build-frontend.mjs` den UI-Build aus; Desktop-Paketierung verlangt zudem die passende Windows-, Rust-, WebView2-, NSIS- und Java/Gradle-Umgebung. Für den Core im Modulstamm `minecraft/votex-core` den vorhandenen Wrapper nutzen, z. B. `./gradlew test` beziehungsweise gezielte Compile-/JAR-Tasks; vor jedem Release die realen Tasks in `build.gradle` und `gradlew tasks` prüfen. Tests hier beschreiben Arbeitsbefehle, sie wurden im Rahmen dieser Dokumentationsaufgabe nicht ausgeführt.

`pnpm dev`/Browserpreview testet die UI, nicht native Tauri-Kommandos, echte Minecraft-Starts, Konten, GPU-Mixins oder Audio. `pnpm build` ist ein Produktionsfrontend-Build, kein Windows-Installer. `pnpm check:rust` ist keine vollständige Bundling- oder App-Control-Freigabe. Testergebnisse immer mit Commit/Quellstand, Core-/EXE-Hash, Profiltyp und tatsächlicher Prüfebene dokumentieren.

## Datenfluss und Persistenz

- React hält den UI-Zustand über `apps/launcher/src/stores/useApp.ts` und greift über `apps/launcher/src/services/desktop.ts` auf `invoke`/Events zu. Browserfallback ist eingeschränkt und nutzt keine Desktop-Rechte.
- Root-Konfigurationsschema und Migrationen: `packages/shared-config/schema.ts`, `packages/shared-config/migrate.ts`; Performance-Voreinstellungen und neue Profile in `packages/shared-config/performance.ts`.
- Rust `storage.rs` besitzt den kanonischen Zustand und sichere lokale Schreib-/Instanzpfade. Bestehende Altprofile erhalten; optionale Altwerte wie `starterMods: null` müssen durch dokumentierte Migration normalisiert werden.
- Profile haben separate Instanzordner. `game_options.rs` spiegelt ausgewählte Minecraft-Optionen. Core-Dateien unter `config/votex/` umfassen Theme, Module/HUD, Presets, Looks/Skins/Cosmetics/Emotes und zuletzt beigetretene Server; konkrete Dateien immer zuerst im Code finden.
- Ein Writer pro Datei: Launcher exportiert `config/votex/theme.json` und Preset-Metadaten/Assets; der Core verwaltet seine Modulkonfiguration. `main.rs` markiert dieses Single-Writer-Prinzip ausdrücklich. Keine konkurrierenden Schreiber in beide Seiten einführen.
- Microsoft-Anmeldedaten bleiben im Windows Credential Manager. Account- und Passwort-/Tokenmaterial nicht in Frontendzustand, Logs, Testsnapshots oder Community-Payloads ablegen.

## Netzwerk- und Installationsmodell

Backend-Module setzen erlaubte Hosts, URL-/Redirect-/Hash-Prüfungen, Staging, Commit und Rollback um. Modrinth löst Abhängigkeitsgraphen auf; CurseForge unterliegt separaten API-/Distributionserlaubnissen. Externe Downloads nicht durch eine undifferenzierte URL- oder Redirectfreigabe ersetzen. Vor Installationsänderungen aktive Spiel-/Downloadoperationen sperren (`Busy`-Schutz in `main.rs`) und Dateien transaktional in Profilinstanzen schreiben. Offline-Modus darf keine Online-Authentifizierung ersetzen.

## Windows-Verteilung und Sicherheit

Tauri ist derzeit für Windows NSIS konfiguriert. Die vorliegenden Dokumente bestätigen keine vertrauenswürdige Code-Signing-Identität. Smart App Control/App Control kann unsignierte Binaries sperren. `docs/WINDOWS-SIGNIERUNG.md` beschreibt den Befund und die legitime Signiergrenze. Keine Schutzfunktion umgehen, Richtlinien ändern, fremde Signatur verwenden oder den Prozess tarnen. Nicht behaupten, dass ein Artefakt auf verwalteten Windows-Rechnern ausführbar ist, bis es dort mit legitimer Freigabe geprüft wurde.

Pomme wird upstream aus dem offiziellen GitHub-Release geladen, SHA-256 geprüft, lokal isoliert abgelegt und nicht in den Votex-Installer gebündelt. Horizon wird aus dem offiziellen Nightly-Kanal geladen; sein versionsgebunden signiertes `SHA256SUMS.v2.sig` wird gegen einen eingebauten Ed25519-Public-Key geprüft, danach wird das ZIP entpackt und jede Datei gegen ein lokales Integritätsmanifest geprüft. Beide bleiben unveränderte Upstream-Prozesse außerhalb des Installers. Horizon-Details und Grenzen: `docs/HORIZON-RUNTIME-6.0.md`; Pomme: `docs/NATIVE-CLIENT-6.0.md`.

## Assets und Lizenz

Projektcode hat Root-`LICENSE`; importierte Modelle/Icons/Fonts/Media haben eigene Attribution/License. `THIRD-PARTY-NOTICES.md`, `docs/CREATOR-CREDITS.md`, `assets-source/` und Core-Ressourcenhinweise vor Asset-Änderungen lesen. Den MIT-Projektcode nicht als Rechteübertragung für Drittanbieter-Assets interpretieren.
