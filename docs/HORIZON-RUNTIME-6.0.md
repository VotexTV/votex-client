# Horizon Rust-Runtime in Votex 6.0

## Umfang und Status

Votex kann neben dem bestehenden Java-Pfad ein neues Profil mit `engine: "horizon"` anlegen. Die neue Option startet Horizons eigenen Windows-x64-Client als separaten Upstream-Prozess. Votex enthält weder Horizons Engine noch Minecraft-Binaries im Installer.

Der Stand ist **Quellcodeintegration, noch nicht release- oder laufzeitabgenommen**. `pnpm build` hat den TypeScript-/Vite-Frontend-Build erfolgreich abgeschlossen. Auf der verwendeten Umgebung waren `cargo` und `rustc` nicht verfügbar; das Rust-/Tauri-Backend wurde daher nicht kompiliert. Horizon wurde nicht installiert oder gestartet, und es gibt keine gemessene FPS-Aussage.

## Nutzerfluss

1. Beim Erstellen eines Profils wird **Java Edition** oder **Horizon · Rust Nightly** gewählt. Java bleibt vorausgewählt.
2. Horizon-Profile werden auf Minecraft `26.3` und `vanilla` festgelegt. Eine Engine kann bei bestehenden Profilen nicht gewechselt werden; dafür ist ein zweites Profil vorgesehen.
3. Auf der Play-Seite kann der Nutzer das offizielle signierte Horizon-Nightly installieren. Beim Start verlangt Votex ein Microsoft-Konto mit Java-Edition-Berechtigung.
4. Votex lädt und prüft die Java-Edition-Quelldateien über die vorhandene Mojang-Installation. Horizons `horizon-prepare` importiert daraus die eigenen Ressourcen. Der Mojang-Server-JAR wird separat anhand des in Mojangs Versionsmetadaten veröffentlichten SHA-1 geprüft.
5. Horizon erhält die Microsoft-Sitzung als JSON über stdin. Der Token steht nicht in der Befehlszeile oder einem Votex-Log.
6. Runtime, vorbereitete Ressourcen, Spielordner und Logs liegen unter Votex' `native-data/horizon/` außerhalb der Java-Profilinstanz.

## Was nicht unterstützt wird

- Fabric, Forge, Quilt, NeoForge und Java-Mods
- Votex Core, Ingame-Cosmetics und Emotes
- Votex Offline-/Cracked-Identität
- Safe Mode oder Java-Installation über Horizons Profil
- ein zugesicherter FPS-Vorteil

Die Java-Profile, Mods, Welten und Microsoft-Daten bleiben getrennt und werden durch diese Integration nicht migriert.

## Installations- und Vertrauensprüfung

- Die Release-Abfrage zielt auf den offiziellen Horizon-Nightly-Tag. Das GitHub-`latest`-Release ist für diese Integration ungeeignet, weil es den separaten Horizon-Launcher bezeichnet.
- Das Nightly-Archiv muss unter dem offiziellen Windows-x64-Assetnamen erscheinen. Release-Titel müssen Datum und Commit enthalten.
- Votex lädt `SHA256SUMS` und `SHA256SUMS.v2.sig`. `ring` prüft Ed25519 gegen den im Backend eingebauten öffentlichen Horizon-Schlüssel; die Signatur wird für `nightly-<datum>-<commit>` geprüft, damit ein älterer Build nicht als neuerer ausgegeben werden kann.
- Das Archiv muss den darin gelisteten SHA-256-Wert erfüllen. Extraktion begrenzt Eintragszahl, Einzeldatei- und Gesamtausgabegröße, verhindert Pfadtraversierung, Symlinks und doppelte Dateinamen und prüft PE-x64 für `horizon-client.exe` sowie `horizon-prepare.exe`.
- Ein lokales Manifest speichert SHA-256-Werte der entpackten Release-Dateien. Vor dem Start wird der Bestand erneut geprüft. Das von Horizon für temporäre Vorbereitung verwendete `.local`-Arbeitsverzeichnis gehört nicht zu diesem Manifest.
- Die vom Client und Preparer bereitgestellte `--launch-api`-Antwort wird pro Start abgefragt. Votex prüft die Launch-API-Version, Minecraft-/Java-Pins und die Optionen, bevor es einen Prozess startet.
- Download- oder Prozessstarts unterliegen den normalen Windows-Richtlinien. Smart App Control/App Control wird weder deaktiviert noch umgangen; nötigenfalls muss ein Administrator oder eine freigegebene Testumgebung die Buildherkunft prüfen.

## Relevante Implementierungsdateien

- `packages/shared-config/schema.ts`: Profilfeld `engine`, abwärtskompatibler Default Java.
- `apps/launcher/src/components/ProfileWizard.tsx`: Engine-Auswahl bei Profilerstellung; Enginebindung beim Bearbeiten.
- `apps/launcher/src/components/PlayStage.tsx`, `apps/launcher/src/App.tsx`: Play-Status, Installation und Profilstart.
- `apps/launcher/src/pages/Profiles.tsx`: Horizon-Kennzeichnung und Sperre für Java-Modverwaltung/-übertragung.
- `apps/launcher/src-tauri/src/storage.rs`: Rust-Profilmodell und Minecraft-/Loader-Validierung.
- `apps/launcher/src-tauri/src/main.rs`: Runtime-Auswahl, Authentifizierungs- und Profilgrenzen sowie Engine-Wechsel-Schutz.
- `apps/launcher/src-tauri/src/horizon.rs`: Nightly-Vertrauen, Installation, Ressourcen-Vorbereitung, API-Erkennung und Start.
- `apps/launcher/src-tauri/src/network.rs`: begrenzter Download mit Integritätsprüfung.
- `apps/launcher/src-tauri/Cargo.toml`: `ring` für Ed25519.

## Noch notwendige Abnahme

1. Mit installierter Rust-/Tauri-Toolchain `cargo check --locked` bzw. den Repository-Wrapper ausführen und Lockfile-/Windows-Buildfehler beheben.
2. Frontend und Rust-Binary mit unveränderlichem Quellstand, Version und Hash als Desktop-App paketieren.
3. In einem leeren, getrennten Windows-Testprofil Runtimeinstallation und Signaturprüfung beobachten.
4. Microsoft-Anmeldung, Ressourcenimport, 26.3-Start, Multiplayer, Fehler-/Exitstatus und Datenisolation prüfen.
5. Ein Java-Profil inklusive Mods starten, um Regressionen und unveränderte Java-Daten zu bestätigen.
6. Erst danach Performance auf gleicher Hardware, Welt, Grafik und Messdauer mit FPS-Verteilung, Framezeit, CPU/RAM/GPU und Startzeit vergleichen.

## Upstream-Quellen

- [Horizon-Quellcode und MIT-Lizenz](https://github.com/horizon-rust/horizon)
- [Horizon Launch API](https://github.com/horizon-rust/horizon/blob/main/docs/api/launch.md)
- [Anleitung für Launcher-Integrationen](https://github.com/horizon-rust/horizon/blob/main/docs/api/launch-guide.md)
- [Horizon-Sicherheit und Nightly-Signaturen](https://github.com/horizon-rust/horizon/blob/main/docs/security.md)
- [Öffentlicher Horizon-Release-Schlüssel](https://github.com/horizon-rust/horizon/blob/main/crates/horizon-release-sign/release-keys.txt)
