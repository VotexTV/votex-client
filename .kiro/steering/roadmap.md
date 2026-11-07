# Votex Client — Entwicklungsfahrplan

## Jetzt: 5.0 Beta vervollständigen (nicht release-ready)

**Stand aus dem letzten V5-Beleg:** `work/cosmetics2-20261106/V5-STATUS.json` setzt `releaseReady` auf `false`; `native-27` war noch laufend, und `releaseEXE` war nicht gebaut. Ziel ist eine abnehmbare Beta, keine unbelegte „fertige 5.0“.

### Reihenfolge

1. **Arbeitsstand sichern und abgrenzen.** Aktuelle Branch-/Quell-/Buildzustände, laufende Prozesse und neueste V5-Evidenz prüfen. Keine alten, bereits abgenommenen Kandidaten mit verändertem Source-Kopf verwechseln.
2. **Cosmetics-Katalog schließen.** Jedes Modell einzeln auf Attachment, Clippen, Silhouette, UV/Texture, Größe, Transparenz, Armor/Head clearance, First-/Third-Person und Simultankombination prüfen. Fehlende individuelle Joint-Rigs bei Tieren hinzufügen, soweit sinnvoll; sichere Schulterlandung/Return entweder korrekt implementieren oder im Beta-Umfang klar begrenzen.
3. **Cape-/Flügel-Animationen abnehmen.** Alle relevanten Motion-Stati einschließlich Sprint, Richtungswechsel, Stop, Schleichen, Schwimmen und Gleiten prüfen. Flügel mit Schulterverbindung und gespiegelt stabilen, ruhigen Idle-Flaps verifizieren. Springparameter framerateunabhängig halten und Aufwand messen.
4. **Emotes und Kamera abschließen.** Alle Emotes aus Front-/Rück-/Seitenansicht für Classic und Slim prüfen, First Person inklusive kontinuierlicher Bewegung, FOV-Wechsel, Itemhand und Stop/Reset. Nur ausgewiesene Körperteile ändern; Modellgröße/Armlänge nicht durch Kamera-Workarounds beschädigen.
5. **Mehrspieler-/Performance-Freigabe.** Mindestens zwei reale Clients (zunächst isoliertes QA), veröffentlichte und private Appearance, Consent, Disconnect/Reload, Modpackgröße; Framezeit/CPU/RAM messen. Offline-QA klar vom Microsoft-authentifizierten öffentlichen Betrieb trennen.
6. **Launcher/Core Versionskette vereinheitlichen.** Root-Paket, sichtbares Label, Cargo, Tauri-Konfiguration, Core-JAR, NSIS-Datei und Release-Manifest konsistent und reproduzierbar versionieren. Drittanbieterhinweise und Lizenzdateien in Installer/Release erhalten.
7. **Abnahme, Paket und Preview.** Frontend-, Rust- und Java-Core-Build/Teststände getrennt protokollieren; finale EXE installieren/öffnen in einem legitimen unterstützten Windows-Umfeld; Profil-/Start-/Recovery-/Update-Flows mit leerem QA-Profil prüfen; Hashes erzeugen. Erst danach Beta verteilen und Nutzerabnahme einholen. Keine Website-Aktualisierung aus dieser Dokumentation ableiten.

### V5-Akzeptanzkriterien

- Keine Blockierung bei den gezielten Cosmetics-/Cape-/Emote-Szenarien und kein regressiver First-Person-FOV-Effekt.
- Jede im Store gezeigte Cosmetic hat konsistente Vorschau und Ingame-Geometrie; bekannte Ausnahmen/Engine-Grenzen sind sichtbar begrenzt.
- Restore/Exit/Versionierung funktionieren getrennt in realer Desktop-Anwendung und Minecraft-Core.
- Drei konkrete Artefakte sind hashgebunden: Launcher/Installer, Core-JAR, ggf. Self-Host-Bundle.
- Reale visuelle Nutzerabnahme bleibt erforderlich; automatische Screenshots/Tests allein machen die Releasefreigabe nicht.
- Keine Behauptung einer universell ausführbaren Windows-EXE ohne legitime Code-Signing-/App-Control-Prüfung.

## Danach: 6.0 Native-Runtime-Strategie

### Was jetzt existiert

`apps/launcher/src-tauri/src/native_client.rs` integriert optional **Pomme**. Zusätzlich ist eine source-seitige **Horizon**-Integration in `horizon.rs` begonnen: Neue Profile können beim Erstellen Java oder Horizon wählen. Horizon lädt eine Windows-x64-Nightly-Datei aus dem offiziellen GitHub-Release, prüft die versionsgebundene Ed25519-Signatur und den SHA-256-Hash, fragt Horizons experimentelle Launch-API ab, bereitet Minecraft 26.3 aus dem Java-Edition-Konto vor und startet `horizon-client` als separaten Prozess. Es unterstützt nur Vanilla 26.3 und Microsoft-Konten. Java-Mods, Votex Core, Votex-Cosmetics/Emotes und Offlineidentität laufen dort nicht. Profile behalten ihre Engine; ein Wechsel am bestehenden Profil wird in UI und Backend abgewiesen. Java bleibt Standard und Kompatibilitätspfad. Beide sind Upstream-Integrationen, keine eigene Rust-/C++-Minecraft-Implementierung.

**Statusgrenze:** Der TypeScript-/Vite-Produktionsbuild ist für die aktuelle Horizon-UI erfolgreich. Auf dieser Umgebung ist `cargo`/`rustc` nicht verfügbar, daher wurde das neue Rust-Backend nicht kompiliert; es gab keinen Horizon-Installations- oder Spielstart und keine FPS-Messung. Horizon kündigt seine externe Launch-API selbst als experimentell an und Nightly-Artefakte sind kurzlebig. Siehe `docs/HORIZON-RUNTIME-6.0.md`.

### Zielbild, noch nicht erreicht

Vor einer tieferen Rust-/C++-Integration ist eine Architekturentscheidung erforderlich: separate Vanilla-Runtime mit klarer Trennung, native Bibliothek/Renderer mit dokumentierten APIs oder Eigenentwicklung. Die Lösung muss Lizenz, Minecraft-Protokoll-/Asset-Verträge, Microsoft-Authentifizierung/Entitlements, Versionen, Mod-Ökosystem, Votex Appearance/Social und Windows-Verteilung berücksichtigen. Keine proprietären/geschlossenen Assets oder unerlaubten Minecraft-Binaries übernehmen. Keine Annahme, dass eine native Sprache automatisch höhere FPS liefert.

### Phasen und Gates

1. **Machbarkeit und Rechte:** Für Horizon sind MIT-Lizenz, offizielle Release-Signatur, Windows-x64-Nightly und experimentelle Launch-API zunächst source-geprüft. Bei jeder Installation aktuelle API-Antwort, Versionspins, signierte Metadaten und Quellen erneut prüfen; keine dauerhafte Verfügbarkeit annehmen.
2. **Runtime-Abstraktion:** Java-Startpipeline stabil halten; Runtime bleibt am Profil gebunden. Account-/Credentialgrenzen, Microsoft-Eigentumsprüfung und unabhängige Datenordner spezifizieren. Fehler dürfen Java-Daten nicht verändern.
3. **Isolierter Proof of Concept (offen):** Rust-Backend kompilieren und den Install/Hash/Update/Paket-/Prozesspfad in einem leeren, erlaubten Windows-Testprofil prüfen. Kein Standardwechsel, kein Zugriff auf echte Java-Mods/Spielstände. App Control nicht umgehen.
4. **Funktionsparität:** Horizons Fähigkeit für Authentifizierung, Multiplayer und Ressourcen wird gegen die jeweilige reale API geprüft. Java-Mods, Votex Core, Cosmetics und Emotes bleiben explizit außerhalb des Horizon-Umfangs, bis Upstream passende Schnittstellen anbietet.
5. **Leistungsvergleich und Freigabe:** gleiche Hardware/Welt/Renderdistanz/Shader/Messdauer für FPS-Verteilung, Framezeit, CPU/RAM/GPU/Startup verwenden; mehrere GPUs/Treiber und Recovery prüfen. Bis zu reproduzierbarer Messung keinerlei FPS-Vorteil behaupten.

### 6.0 explizite Nichtziele ohne separate Entscheidung

- Java-Startpfad entfernen oder automatisch auf Native umstellen.
- Pomme als Votex-eigene Engine darstellen, obwohl es Upstream ist.
- Votex-Java-Mods/Cosmetics/Emotes für Pomme versprechen.
- Microsoft-Anforderungen oder Smart App Control umgehen.
- Leistungsvorteile aus Sprache, Vulkan oder synthetischem Benchmark allein behaupten.

## Nachgelagerte Produktbereiche

- **Community-Publishing:** Eigentümeridentität, Standard-Öffentlichkeit, private Option, erneutes Veröffentlichen und Widerruf in reale API/Auth-/Moderationsflüsse einbinden. Upload/Öffentlichkeit erst mit Serverbetrieb, realem Konto, Rate-/Missbrauchsgrenzen und Versionsverträgen abnehmen.
- **Friends-/World-/Voice-Infrastruktur:** Self-Host-Paket bleibt separat; Anbieter/Betreiber muss Host, DNS, TCP/UDP, Zertifikate, Datenschutz und Betrieb bereitstellen. Lokale Schleifen nicht als global erreichbare Funktion vermarkten.
- **Creator-Editor:** vorhandene Blockbench-inspirierte 3D-Model-/Emote-Werkzeuge auf direkte Mausmanipulation, Achsen, Timeline, Texturen/Verläufe, Keyframes, Undo/Redo, Import/Export und robuste Vorschau erweitern. Fremdformate und rigging klar spezifizieren; keine Blockbench-Kompatibilität behaupten, ohne Format-/Roundtrip-Tests.
- **Modul-/HUD-Qualität:** jedes Modul – Rüstungsanzeige, Item Physics, Light Level, HUD-Widgets etc. – einzeln an echten Minecraft-Zuständen prüfen. Pixelgenaues Verschieben und Snap-Linien müssen persistent und pro Layout sein.

## Offene externe Freigaben

- Minecraft-AppID-Antrag: zuletzt keine Freigabe dokumentiert, siehe `docs/MINECRAFT-APP-REVIEW.md`.
- Windows-Verteilung: kein öffentlich vertrauenswürdiges Signierzertifikat eingerichtet, siehe `docs/WINDOWS-SIGNIERUNG.md`.
- Audio: physischer Mikrofon-/Lautsprechertest vertagt.
- Öffentliche soziale/Relay-/Community-Funktion: Self-Host-/Loopback-Bereiche sind kein produktiver Hosting- oder Internetbetrieb.

Diese Freigaben dürfen nicht durch Mockbelege, Offline-Identitäten oder lokale Build-Erfolge ersetzt werden.
