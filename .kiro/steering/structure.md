# Votex Client — Projektstruktur und Architektur

## Top-Level-Karte

```text
.
├── apps/launcher/                 Tauri Desktop-App und React/Vite-UI
│   ├── src/                       App-Shell, Seiten, Komponenten, Services, Stores, Styles, Daten
│   └── src-tauri/                 Rust-Binär-App, Kommandos, Backend-Module, Windows-Bundle
├── packages/
│   ├── shared-config/             Zod-Schema, Migration, Defaults, Presets und Tests
│   ├── shared-types/              typisierte Schnittstellen/Contracts
│   └── brand/                     gemeinsame Markenressourcen
├── minecraft/
│   ├── votex-core/                Fabric-Client-Mod in Java, Mixins, Ressourcen, Tests
│   ├── votex-relay/                Relay-Implementierung und Tests
│   └── votex-selfhost/             getrennte Self-Host-Komponente/-Vorlagen
├── scripts/                       Build-, Assets-, Release-, Self-Host- und QA-Helfer
├── tests/                         übergreifende Frontend-/Integrationsfixtures
├── assets-source/                 Quellen, Generatoren und Drittanbieter-Asset-Nachweise
├── docs/                           Architektur, Contracts, QA-Belege und historische Übergaben
├── work/                           Kandidaten, lokale QA-Daten, Berichte und temporäre Arbeitsstände
└── .kiro/steering/                diese produkt-/technikbezogene Übergabe
```

Prüfe tatsächliche Verzeichnisse, weil optionale Paketordner je nach Checkout fehlen können. `work/` ist Arbeits- und Belegmaterial, kein automatisch distributierbarer Produktinhalt. Secrets, echte Konten, Welten, lokale Logs oder QA-Daten nicht in Releasepakete kopieren.

## Launcher-Frontend

`apps/launcher/src/App.tsx` erstellt das Fensterlayout, Navigationsseiten, globale Dialoge, Konten-/Offline-Auswahl, aktive Profile, Installationsfeedback und Spielstartzustände. Seitengruppen:

- `pages/Profiles.tsx`: Profile, Erstellen/Bearbeiten/Starten und Profil-UI.
- `pages/Mods.tsx`: installierte Mods, Modrinth/CurseForge-Entdeckung und Änderungen.
- `pages/Skins.tsx`: Skinbibliothek/Pixelbearbeitung.
- `pages/Cosmetics.tsx`: Cosmetics und Animationen/Editoren.
- `pages/Settings.tsx`: Design, Leistung und Spieloptionen.
- `pages/Recovery.tsx`: Sicherung, Diagnose und Wiederherstellung.
- `pages/DesignStudio.tsx`: Launcher-Themes, Effekte und Anpassung.
- `components/`: wiederverwendbare Glasflächen, Dialoge, Profile Wizard, PlayStage, Vorschau/3D-Render, Editor, HUD-/Timeline-Elemente, Installation feedback, globale Übergänge und Scrollverhalten.
- `services/`: Tauri-Adapter, Offline-Identität, Installationsjobs, Meldungsformatierung und Desktop-Funktionen.
- `stores/useApp.ts`: UI-State, initiales Laden/Speichern und Konfigurationsupdates.
- `data/`: Versionslabels, Kataloge/Manifeste und UI-spezifische Konstanten.

Bei visuellen Änderungen in allen UI-Skalierungen, Browserpreview und nativer WebView bedenken. `motion`-Animationen und Cursorbeleuchtung dürfen nicht die Eingabelatenz erhöhen. CSS und React sind keine Quelle für geschützte Dateischreibvorgänge oder Spielprozessverwaltung.

## Rust-Backend / Tauri-Kommandos

`apps/launcher/src-tauri/src/main.rs` registriert die Tauri-Kommandos und initialisiert Store/Appdaten. Unter-Module nach Zuständigkeit:

| Modul | Rolle |
| --- | --- |
| `storage.rs` | kanonische Konfiguration, Pfadbegrenzungen, JSON/PNG, atomare Writes, Migration/Validierung, Profilinstanzen |
| `auth.rs` | Microsoft-/Xbox-/Minecraft-Auth-Flows und Credential Manager-Grenze |
| `minecraft.rs`, `loaders.rs`, `prepare.rs` | offizielle Metadaten, Java-/Loader-Vorbereitung, Spielstart |
| `modrinth.rs`, `curseforge.rs`, `catalog.rs`, `modpacks.rs`, `starter.rs`, `pending_addons.rs`, `transfers.rs` | Mod-/Shader-/Modpack-Katalog, Abhängigkeiten, Staging, Profiletransfer und Installationsrezepte |
| `performance.rs`, `game_options.rs` | JVM-/Performancezustand und Synchronisation mit Minecraft-Optionen |
| `recovery.rs`, `diagnostics.rs` | Startdiagnose, Sicherung und Restore-Flows |
| `studio.rs` | lokales Creator-/Projektmaterial |
| `community.rs`, `network.rs` | Community-API-Payloads und freigegebene Netzwerkoperationen |
| `play_history.rs` | zuletzt gestartete Profile/Server-Verlauf |
| `core_install.rs` | Votex Fabric-Core bereitstellen/aktualisieren |
| `native_client.rs`, `runtime.rs` | optionale externe Pomme-Runtime prüfen/installieren/starten und Java-Laufzeit auflösen |
| `horizon.rs` | Offizielles Horizon-Nightly signiert installieren, Dateien prüfen, API abfragen, Ressourcen vorbereiten und isoliertes Profil starten |

`main.rs` verwendet einen Operation-Lock (`begin`/`Busy`) für exklusive Installations-/Spielprozesse und lehnt Profiländerungen während aktiver Prozesse ab. Änderungen an dieser Sperre brauchen parallele Download-/Starttests. Tauri-Commands bilden den IPC-Vertrag; passende TS-Typen und Fehlerdarstellung müssen synchron geändert werden.

## Gemeinsame Konfiguration und Übergabepunkte

`packages/shared-config/schema.ts` validiert den App-State, `migrate.ts` normalisiert ältere Stateformen und `performance.ts` liefert neue Profildefaults. Rust validiert und speichert ebenfalls. Beim Statefeld muss geprüft werden, ob es (a) nur Launcher ist, (b) nach Minecraft-Optionsdatei synchronisiert wird oder (c) in Votex Core-Presets exportiert wird.

Der Launcher exportiert pro Instanz Theme und Appearance-Presets unter `config/votex/`. Core-Ressourcen und JSON-Kataloge liegen unter `minecraft/votex-core/src/main/resources/assets/votex_client/`; Core-Konfigurationen sind in `src/main/.../config`. Java-Klassen unter `src/client/java/dev/votex/client/` implementieren Menüs/Renderer/Input und `mixin/` hängt an Minecraft/Fabric-Render- oder Screenpfaden. `src/main/java/dev/votex/...` enthält überwiegend rendererunabhängige Datenmodelle, Bewegung, Social-Regeln und UI-Geometrie. Tests sind unter `src/test` und `src/gametest` getrennt.

**Wichtig:** Launcher und Core sind nicht ein einziger Prozess. Der Launcher schreibt vor dem Start definierte Votex-Dateien; der Core liest/schreibt laufende Ingame-Optionen. Eine Änderung an gemeinsam verwendeten JSON-Feldern ist eine formatübergreifende Änderung inklusive Schema, Migration, Export, Core-Reader, Core-Writer und Tests.

## Vertrauens- und Netzwerkgrenzen

```text
React UI ──Tauri IPC──> Rust Launcher ──> Profilinstanzen / Appdaten
                                ├───────> Minecraft-Metadaten, Modanbieter, Community API
                                ├───────> Microsoft OAuth/Xbox/Minecraft APIs
                                ├───────> javaw.exe oder optional Pomme.exe
                                └───────> optional horizon-client.exe (Minecraft 26.3)
Minecraft Java ──> Fabric Votex Core + installierte Mods
                         ├────> config/votex Presets / HUD / Theme
                         └────> optional Social Relay / Simple Voice Chat
```

Tokens dürfen nicht den Rust-/OS-Credential-Store verlassen, außer für die ausdrücklich vorgesehene Microsoft-Sitzungsübergabe direkt über Horizons stdin. Öffentliche Community-/Relay-Endpunkte sind eigene Betriebs- und Authentifizierungsgrenzen; lokaler Loopback-Beleg ist kein Internetdeployment. Pomme und Horizon sind unabhängige Upstream-Programme, keine in den Launcher eingebetteten Engines. Horizon-Profile sind getrennt von Java-Mods und Java-Spielständen.

## Navigation in den Dokumenten

- `docs/ARCHITECTURE.md`: historische Detailbeschreibung von Besitz-, Auth-, Netzwerk- und Recovery-Grenzen.
- `docs/COMMUNITY-CONTRACT-20261025.md`, `docs/INGAME-SOCIAL.md`, `docs/INGAME-VOICE-PROVISIONING.md`: Social- und Voice-Verträge.
- `docs/CUSTOM-COSMETIC-FORMAT.md`, `docs/APPEARANCE-MOTION.md`: Format und Animation.
- `docs/SELFHOST-VALIDATION.md`: nur Self-Host-Betrieb, Loopback-Belege und offene Infrastruktur.
- `docs/TEST-RESULTS.md`, `docs/ACCEPTANCE-GATES-20261025.md`, `work/cosmetics2-20261106/`: versions-/kandidatengebundene QA.
