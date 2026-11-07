# Hinweise für KI-Agenten und Entwickler

## Vor Beginn

1. Lies `.kiro/steering/product.md`, `tech.md`, `structure.md`, `development-history.md` und `roadmap.md` sowie die passende Detaildatei unter `docs/`.
2. Prüfe den aktuellen Source-/Arbeitsstand und laufende Tätigkeiten. `work/` enthält lokale QA- und Entwicklungsartefakte; sie können persönliche oder isolierte Testdaten enthalten und gehören nicht automatisch in Commits, Pakete oder öffentliche Logs.
3. Die Steering-Dokumente sind Stand 7. November 2026 und können veralten. Quelle und konkretes Artefakt gegenprüfen, besonders Versionsnummer, Tests, Freigaben, Website und Runtime.

## Änderungsregeln

- Erhalte bestehende Profile, Opt-outs, Tokens, Minecraft-Welten und lokale Nutzerdaten. Keine Lösch-/Resetmigration als Reparatur. Migrationen idempotent und rückwärtskompatibel gestalten, Defaultwerte nur für neue Profile einführen und problematische Altdaten separat sichern.
- Betrachte React, Rust/Tauri und Java/Fabric als getrennte Vertrauens- und Prozessgrenzen. Für Änderungen an IPC-Verträgen, JSON, Theme, Presets, Mods, Spieloptionen oder Offline-Identität alle Reader/Writer, Typen, Migrationen und Tests prüfen.
- Halte das Single-Writer-Prinzip für `config/votex`-Dateien aufrecht. Auth-Tokens bleiben im Windows Credential Manager; niemals in React, Browserstorage, Commits, Logs oder Community-Payloads schreiben.
- Minecraft-/Mod-/Shader-Downloads über bestehende erlaubte Hosts, Redirect- und Hashprüfung sowie Staging/Rollback durchführen. Benutzerbestätigungen und Provider-Lizenzen respektieren. Keine Authentifizierungs-/Entitlement-Prüfung umgehen.
- Drittanbieter-Cosmetics, Icons, Fonts und Renderer-Assets vor Kopie/Änderung lizenzrechtlich prüfen und Quellen/Notices aktualisieren.
- Vor Produktbuild Versionskette von `package.json`, `buildLabel.ts`, Cargo/Tauri, Core-JAR und Installer abstimmen. Aktuell ist sie inkonsistent; keine Version oder Releasebereitschaft erfinden.
- Öffentliche Veröffentlichung, Upload, Kontoauthentifizierung, Änderung an echter Installation oder Betrieb eines öffentlichen Servers erfordert passenden konkreten Auftrag und freigabefähigen Artefaktstand. Niemals durch bloße Dateierstellung eine Veröffentlichung behaupten.

## Sicherheit und Windows

- Windows Smart App Control/App Control nicht umgehen oder deaktivieren. Keine Tarnung, fremde Signatur, Richtlinienausnahme, manipulierte Mark-of-the-Web-/Zone-Daten, alternative Hostausführung oder Änderung am Zertifikatsvertrauen. Bei Policy-Block legitime Signatur/Administrationsfreigabe oder genehmigte Testumgebung verwenden.
- Keine fremden Minecraft-Binaries, privaten Schlüssel, QA-Credentials, Microsoft-Tokens, Weltdateien, personenbezogene Logs oder Self-Host-Secrets in Artefakte aufnehmen.
- Keine echten Profile oder laufenden Minecraft-Prozesse in Tests verändern. Für destruktive/inkompatible Migrationen zuerst eine verifizierte Sicherung und Wiederherstellungsmöglichkeit nachweisen.

## Entwicklungs- und Belegregeln

- Mod-/Core-Kompatibilität konkret an Minecraft-, Loader-, Java- und Fabric-API-Version binden. Änderungen an Mixins benötigen echte native Regression/Renderer-Abnahme zusätzlich zu Unit-Tests.
- Trenne Tests nach Frontend, Rust, Java-Core, nativer Ingame-Laufzeit, Multiplayer, Auth, Audio und Paket. Ein Teststatus gilt nur für den dokumentierten Source-/Binary-Hash.
- Bei UI-Effekten Reduced Motion, Fokus, Tastatur/Controller, DPI/GUI-Scale, Resize und Cursorlatenz beachten. Keine Renderinganimation bei Kategorienwechsel erneut auf dem äußeren Fenster abspielen.
- Release-/QA-Notizen müssen *Implementiert*, *Teilweise*, *Geplant* und *nicht getestet* klar unterscheiden. Screenshots, Fixture/Mock, Loopback und Produktionsbetrieb sind unterschiedliche Evidenzstufen.
- Behaupte keinen Start, erfolgreichen Test, Minecraft-AppID-Zugang, öffentlich sichtbare Cosmetic, funktionierenden Mikrofonpfad, Performancegewinn oder verteilbaren Installer ohne passende aktuelle Beobachtung.
- Verwende bestehende Lockfiles/Wrapper; keine lockfilefreie Abhängigkeitsauflösung im Build. Keine großflächige Formatierung oder Generated-Asset-Änderung als Beifang.
- Halte Änderungen fokussiert und kontrolliere vor Abschluss `git diff`/Dateiliste auf versehentliche Änderungen an Anwendungscode, Daten oder generierten Dateien. Der ursprüngliche Handoff-Auftrag hier erlaubt nur Dokumentation.

## Nützliche Kommandos

```powershell
pnpm install --frozen-lockfile
pnpm build
pnpm test
pnpm check:rust
pnpm desktop:build
```

Diese Befehle wurden bei Erstellung dieser Übergabe nicht ausgeführt. Der Java-Core hat einen eigenen Gradle-Wrapper unter `minecraft/votex-core`; vor dem Ausführen die projektspezifischen Tasks prüfen. Erfolgreicher Build ist keine Windows-Signierung, Smart-App-Control-Freigabe oder End-to-End-Abnahme.
