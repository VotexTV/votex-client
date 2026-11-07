# Votex Client 5.7 Beta — Quellcode

Eine Windows-Anwendung (Tauri 2 / Rust / React) mit Fabric-26.2-Core für Minecraft. Dieses Repository enthält den Quellcode für **5.7**. Ein signierter 5.7-Installer ist noch nicht veröffentlicht; der zuletzt öffentlich angebotene Build ist **5.0 Beta 1** (unsigniert). Signier- und Releaseablauf: [SIGNING-POLICY.md](SIGNING-POLICY.md), `.github/workflows/signed-release.yml`.

## Code signing policy

The project plans to apply for free code signing from [SignPath.io](https://about.signpath.io/) using a certificate provided by [SignPath Foundation](https://signpath.org/). No application has been approved and no 5.7 artifact has been signed. The proposed signing scope, approvers and build process are described in [SIGNING-POLICY.md](SIGNING-POLICY.md).

- Authors, reviewers and approvers: Lasse Wieting
- Privacy: this program will not transfer any information to other networked systems unless specifically requested by the user or the person installing or operating it.

## Release-Prüfstand · 8. November 2026

Der lokale TypeScript-Check besteht; Vitest besteht 109 von 110 Tests. Die native Sichtfreigabe für 15 geänderte Flügelmodelle ist offen und bleibt ein verpflichtendes Release-Gate. GitHub-/SignPath-Zugang, Signierung des NSIS-Deinstallierers und Plugin-Prüfung sowie tatsächliche Installation, Start und Deinstallation unter aktivem SAC sind nicht abgeschlossen. Der Workflow erstellt höchstens einen Entwurf, keine öffentliche 5.7-Freigabe.

## Aus dem Quellcode bauen

Voraussetzungen: Node.js 24, pnpm 10, Rust stable (MSVC), JDK 25, Visual Studio C++ Build Tools, Windows 10/11 x64.

```powershell
pnpm install --frozen-lockfile
cd minecraft/votex-core; ./gradlew.bat build; cd ../..
./scripts/stage-core.ps1
pnpm desktop:build
```

Das Ergebnis liegt unter `apps/launcher/src-tauri/target/release/bundle/nsis/`. Der Build ist **unsigniert**, solange er nicht über den Release-Workflow signiert wurde; Windows Smart App Control kann ihn deshalb blockieren. Der Quellcode enthält keine Zertifikate, Tokens oder Minecraft-Binaries.

## Älterer Stand: 0.4.1 Preview · 25.10.2026

Aktuelle [Windows-Preview](https://votex-client-project.adoredpeach257.chatgpt.site/#download): Build vom25.10.2026, mit26 HUD-Modulen und314 kostenlosen Katalogeinträgen. Veröffentlichung und aktuelle Prüfgrenzen: [Preview-Prüfstand](docs/PREVIEW-WEBSITE-20261025.md). Die folgenden RC1-Abschnitte beschreiben den älteren Stand; ältere Desktop-Verknüpfungen wurden nicht ersetzt. Die neue Preview bleibt unsigniert und kann von Smart App Control blockiert werden.

Eine echte Windows-Anwendung mit eingebetteter Oberfläche und Fabric-26.2-Core. Die EXE benötigt keinen laufenden Webserver. Diese Preview ist deutlich ausgebaut; ein bestätigter Microsoft-Konto-Test steht wegen der noch ausstehenden Mojang-API-Freigabe aus.

## Neu in 0.4.1

Native Glaskarten im Ingame-Menü (rechte Umschalttaste oder Escape → Votex Client), zehn einzeln aktivierbare HUD-Module mit Ziehen/Positionierung, acht Emote-Plätze mit Spielervorschau und ein lokales Performance Center. Im Launcher: präzise Sekunden-Keyframes, freie Pixel-Skalierung auf XYZ, echte Mod-Bilder in zwei Spalten, breite Profilsuche und Übergänge von der angeklickten Schaltfläche. Ultra Performance bzw. reduzierte Bewegung schalten diese Übergänge absichtlich aus; **High** aktiviert den umfangreicheren Bewegungsstil.

Der Sodium-Startfehler durch Windows-Pfad-Aliase ist korrigiert. Bestehende Mods werden erhalten. Die VPE reduziert konkret gemessene Emote-Allokationen und wiederholte Textbreitenarbeit. Ein allgemeiner FPS-Vorsprung ist nicht belegt; [Technik, Messungen und offene Engine-Bereiche](docs/PERFORMANCE-ENGINE.md).

## Starten

[Windows-Download](https://votex-client-project.adoredpeach257.chatgpt.site/#download). Lokal: Desktop-Verknüpfung **Votex Client 0.4.1 RC1** oder die EXE im gleichnamigen Desktop-Ordner. Der separat beigelegte Setup-Installer installiert die Anwendung. Für den Offline-Test auf **Offline starten** klicken; **Spiel installieren / reparieren** prüft lediglich die Installation.

Der aktuelle optimierte Windows-Build und NSIS-Installer sind 0.4.1. Die EXE wurde mit einem getrennten Testdatenordner auf dem Entwicklungsrechner gestartet und direkt bedient. Das Release-Paket liegt unter `../releases/0.4.1-rc.1/`; enthalten sind Setup, portable EXE, Quellcode, Core und SHA-256-Prüfsummen. Details stehen im [Prüfstand](docs/TEST-RESULTS.md).

Windows 10/11 x64 und WebView2 sind Voraussetzung. Der Build ist **unsigniert**. Der erfolgreiche Start auf diesem Rechner garantiert keine Freigabe auf anderen Rechnern. Es wurden keine Windows-Schutzfunktionen verändert, Zertifikate gekauft oder kostenpflichtigen Dienste eingerichtet. Details: [Signierung](docs/WINDOWS-SIGNIERUNG.md).

Für einen vollständig getrennten Teststart kann die EXE mit `--votex-data-dir="C:\Votex-Testdaten"` gestartet werden. Der absolute Pfad muss ausdrücklich angegeben werden; ohne den Parameter bleibt der bestehende Speicherort `%LOCALAPPDATA%/VotexClient` unverändert.

## Neu in 0.4

- **3D-Atelier:** Teile direkt in der Ansicht auswählen, an Achsen verschieben, drehen und skalieren. Orbitkamera, Kameraverschiebung, Rasterfang, Gelenkzuordnung, benannte Teile, Duplizieren, Spiegeln und Rückgängig. Die Arbeitsfläche beginnt leer; die Skinreferenz ist abschaltbar.
- **Farbe und Bewegung:** Verläufe und ein bemalbares 8×8-Muster ergänzen einfarbige Materialien. Eine Keyframe-Zeitleiste animiert einzelne Modellteile. Das Format funktioniert im Launcher und im Fabric-Core; es führt keine Animationsskripte aus.
- **Animationsstudio:** große Skinvorschau mit auswählbaren Gelenken und Drehachsen, editierbaren Körper-Keyframes, Vorschau und importierbaren eigenen Gesten. Die Vorschau spielt nur nach bewusstem Start.
- **Gemeinsame Oberfläche:** gerundete Tastatur-Dropdowns, Dialoge mit Öffnungsursprung am Auslöser, ruhige Karten und einmaliger Aufbau beim Scrollen. Reduzierte Bewegung und die sparsame Designstufe schalten die zusätzlichen Bewegungen ab.
- **Passform:** sieben bedeckende Kopfbedeckungen wurden angepasst; darunter wird die äußere Skin-Kopfschicht ausgeblendet. Ein Votex-Cape erhält im Spiel eine einzige zuständige Render-Schicht. Getrennte Leistungsprofilwerte werden nun auch nativ zuverlässig gespeichert.

RC1 ist ein prüfbarer Releasekandidat. Die Minecraft-API-Freigabe und damit die vollständige Microsoft-Anmeldung sind weiterhin offen. Ein Umfangs- oder Qualitätsvergleich mit dem vollständigen Blockbench ist keine zugesicherte Eigenschaft.

## Bereits enthalten

- **Charakterdarstellung:** Die Startseite wechselt zwischen Charakterstudio und einer echten 3D-Szene im selben Renderer. Wolkeninsel, Lichtstudio und Nachtgarten bieten Orbitkamera, Zoom, Kameraverschiebung und drei Lichtvorgaben. Modus, Szene und Beleuchtung werden gespeichert. Vorhandene Kosmetik bleibt sichtbar; eingebaute und eigene Emotes lassen sich auch ohne Ruheanimation abspielen. Vorschauen pausieren bei fehlendem Fensterfokus, unsichtbarem Fenster und während Minecraft startet oder läuft. Die neuen Ansichten sind im Browser geprüft; ihr nativer Windows-Laufzeittest ist noch offen.
- **Afterglow:** eine zusammenhängend überarbeitete Oberfläche, eigenes Panorama, deinen Standardskin, ruhige Karten, deutliche Schaltflächen und helles/dunkles Design. Dialoge werden unabhängig vom Inhalt zentriert; die Navigation bleibt auch bei 1000 × 650 erreichbar.
- **Dein Standardskin:** der vom Projektinhaber gelieferte Skin ist unverändert als Fallback eingebunden. Über dem Spieler steht im Launcher „Votex Client“, auch im PNG-Export. Gewählte lokale Skins und der angemeldete Account-Skin haben weiterhin Vorrang.
- **Feste GUI-Stufen:** 1–5 entsprechen 75, 90, 100, 125 und 150 %. Die Auswahl verändert die Oberfläche erst mit **Größe übernehmen**. Oben ist eine jederzeit erreichbare Rückkehr zu 100 %. Fenster ab 800 × 560 werden unterstützt; Layouts reagieren auf den tatsächlich verbleibenden Platz einschließlich GUI-Skalierung. Im Profil-Assistenten bleiben Weiter/Zurück sichtbar, während der Inhalt scrollt.
- **Creator Collection:** neun frei lizenzierte Modelle anderer Künstler, darunter echte texturierte Federn, unterschiedliche Flügelsilhouetten, Mützen, Kopfhörer, ein Fuchs und Brillen. Standardmäßig erscheinen diese und drei Votex-Capes. Die bisherigen Entwürfe bleiben unter „Alle Modelle & Atelier“ erreichbar. [Quellen und Lizenzen](docs/CREATOR-CREDITS.md).
- **Echte Oberflächenmodelle:** 45 gemeinsame Geometrieeinträge, darunter importierte Modelle samt Original-UV-Texturen sowie die bisherigen Atelier-Flächen. Die Anprobe überblendet den Modellwechsel, erhält die Kamera und kann den echten Look als transparentes PNG aufnehmen. In der Desktop-App wird das PNG unter `%LOCALAPPDATA%/VotexClient/studio-shots/` gespeichert.
- **Flüssige Gesten:** weicher Einstieg und Ausklang, gedämpfte Übergänge beim Wechsel, sanftes Stoppen, Wiederholung mit Ruhepause und einstellbares Vorschautempo. Das Tempo und Wiederholen gelten für das Vorschau-Studio; die Core-Gesten laufen einmal im natürlichen Tempo.
- **Gesten-Editor:** gespeicherte eigene Gesten lassen sich erneut bearbeiten. Die Zeitleiste übernimmt alle Keyframes, Gelenkwinkel und vertikale Bewegung. Private Gesten werden lokal aktualisiert; beim Bearbeiten veröffentlichter Gesten entsteht eine private Kopie, damit der Originalbeitrag erhalten bleibt.
- **Cinematic mit 32 Chunks:** 32 Sichtweite, 12 Simulation, 8 GB RAM, fabelhafte Grafik und 120 FPS. Shader sind eine bewusste zusätzliche Wahl. Alte Werte bleiben erhalten, bis du die Vorlage auswählst. Exakte FPS-Werte wie 144/165 sind möglich; Ausschalten und erneutes Einschalten merkt sich das letzte Limit während der Sitzung.
- **Schneller zum Ziel:** Strg+K für Seiten, Profile und Inhaltsordner. Größere Profilkarten, zuverlässig geprüfter Zeiger-Drag zum Kopieren von Mods und derselbe Versionsassistent beim Erstellen und Bearbeiten. Die Startseite leitet Versionsänderungen zur Profilverwaltung weiter. Mod-Details nennen ihr Installationsziel ausdrücklich. Die RAM-Anzeige fragt die tatsächliche physische Windows-Kapazität ab; sie ist keine laufende Auslastungsanzeige.
- **Offline starten:** Profile können ohne Microsoft-Token mit einem lokal gespeicherten Spielernamen gestartet werden. Das genügt für Einzelspieler und Server mit Offline-Authentifizierung; Server mit Online-Authentifizierung verlangen weiterhin ein geeignetes Microsoft-/Minecraft-Konto. „Spiel installieren / reparieren“ ist klar vom eigentlichen Start getrennt. Jedes Profil hat außerdem seinen eigenen Startknopf.
- **Spielwerte pro Profilvorlage:** Performance, Ausgewogen, Cinematic und Maximale Entlastung merken sich deine individuellen Werte getrennt. Der Reset stellt die Werkseinstellungen der gerade gewählten Vorlage wieder her.
- **Pixelgenaue Skin-Bearbeitung:** lokale Skin-Texturen lassen sich in 64×64 oder 64×32 Pixeln bemalen, radieren und aufnehmen. Die 3D-Figur zeigt die Änderungen während des Malens; Speichern aktualisiert den lokalen Skin.
- **Lokales Profilbild:** Beim Beenden speichert der Client Core den zuletzt sichtbaren Minecraft-Bildschirm als kleines PNG im Spielprofil. Die Profilkarte zeigt es an. Das Bild bleibt lokal und wird nicht veröffentlicht.
- **Design Studio Schnellschalter:** Glas, Licht in Mausnähe, Leuchten, Reflexionen, federnder Tastendruck und Atmosphäre lassen sich unabhängig umschalten und bleiben je Designstufe erhalten. Ultra Performance schaltet Hintergrundbewegung, Nähetracking, Blur und Leuchteffekte ab; die höheren Stufen können mehr GPU-Arbeit verursachen. Hardwaremessungen für FPS und Energiebedarf sind für diese Preview noch offen.

## Enthaltene Funktionen

- **Design Studio:** Ultra Performance, Performance, High und Ultra High Core. Jede Stufe merkt sich ihre eigenen Änderungen und lässt sich einzeln zurücksetzen. Farbwelten und Layout bleiben unabhängig. Licht an der Maus, Näherungseffekte, schwebende Karten, neue Regler und aufklappbare Detailgruppen. Kein fortlaufender Näherungs-Animationsloop; Arbeit ist ereignisgesteuert und begrenzt. Für die Startseite stehen Landschaft, Wolken, animierte Aurora, Farbfläche sowie eigene Bilder und MP4-/WebM-Hintergründe bis 5 MB bereit. Helligkeit, Weichzeichnung, Ausschnitt und Zoom lassen sich getrennt einstellen. Videos pausieren außerhalb der Startseite, bei minimiertem Fenster, reduzierter Bewegung und während Minecraft läuft.
- **Cloud Nine & Startseite:** eigenes Afterglow-Panorama und Pixelwolke als Marke. Ohne Konto bleibt der Blick auf die Landschaft frei. Mit Anmeldung erscheint dein Account-Skin automatisch. Lokale Skins lassen sich direkt auf der Startseite auswählen.
- **Profil-Assistent:** Versionskarten mit neuesten Veröffentlichungen zuerst, Suche, kompatible Loader-Auswahl, Performance oder Quality/Cinematic, optionale Performance-/Shader-Pakete und RAM. Spigot/Paper werden als Server-Software erklärt. Profile besitzen getrennte Spielordner. Größere Profilkarten öffnen über **Mods verwalten** ihre eigene Inhaltsansicht. Die Mod-Übernahme kann bereits bei der Erstellung gewählt werden.
- **Inhalte:** Modrinth-Suche mit automatischem Nachladen, Kategorien, Sortierung und Mods, Modpacks, Shadern sowie Ressourcenpaketen. Native CurseForge-Anbindung mit sicherer Schlüsselverwaltung ist vorbereitet. **Du hast noch keinen eigenen CurseForge-Schlüssel; die authentifizierte Suche und Downloads konnten daher nicht getestet werden.** Modrinth benötigt keinen solchen Schlüssel.
- **Mods übernehmen:** Den Mod-Ordnergriff mit der Maus auf ein anderes Profil ziehen oder am Zielprofil anklicken und eine Quelle wählen. Votex identifiziert Dateien per Modrinth-SHA-512-Abfrage, löst passende Zielversionen und Pflichtabhängigkeiten auf und zeigt einen Bestätigungsplan. Nicht erkennbare Dateien werden nur bei identischer Minecraft-Version und gleichem Loader unverändert kopiert; beim Versionswechsel werden sie ausgelassen. Deaktivierte Quellmods bleiben deaktiviert. Quelle und Welten bleiben erhalten. Das Ziel erhält einen Sicherungspunkt; geänderte Dateien machen den Plan ungültig.
- **Modpacks:** `.mrpack` und CurseForge-ZIP werden geprüft, getrennt vorbereitet und als neues Profil übernommen. Prüfsummen, Pfade, Links, doppelte Namen und Größenlimits werden kontrolliert. Optionale Dateien werden ausgelassen. Gesperrte Drittanbieter-Downloads werden nicht umgangen.
- **Kostenloser Kosmetikkatalog:** 45 Modelle in neun Kategorien, davon neun aus der Creator Collection. Flügel, Kopf-/Gesichtsobjekte, Begleiter, Rückenteile, geometrische Auren, Outfits, Schuhe und drei Cape-Motive. Suche, Filter, Anprobe, Ausrüsten und gespeicherte Looks. Eigene Cape-Bilder lassen sich weiterhin zuschneiden. Ohne Währung oder Bezahlvorgang. Vorschau und Core verwenden dieselbe Geometrie und dieselben UV-Texturen; die Beleuchtung unterscheidet sich zwischen Studio und Spiel.
- **Community-Atelier:** Erstelle eigene Kosmetik aus Würfeln, Kugeln, Flächen und Pixelteilen, positioniere sie an Kopf, Körper oder Gliedmaßen und probiere sie sofort im 3D-Studio an. Lokale Modelle lassen sich aus „Meine Beiträge“ erneut öffnen und weiterbearbeiten. Öffentliche oder nicht gelistete Originale bleiben beim Bearbeiten online unverändert; Votex speichert die Änderung als private Kopie. Das einfache Atelier bietet Rückgängig/Wiederholen, Teil duplizieren, Rasterfang sowie matte, polierte und metallische Materialien. Modelle lassen sich als geprüfte `.votex-cosmetic.json` (höchstens 24 Teile) importieren und exportieren; importierte Meshes werden lokal neu aufgebaut. Einzelne Teile können mit begrenzten, sicheren Schwingbewegungen animiert werden. [Format und Grenzen](docs/CUSTOM-COSMETIC-FORMAT.md).
- **Eigene Emotes:** Baue Gesten aus Körper-Keyframes und weichen Übergängen. Das Emote-Rad liegt direkt um die 3D-Figur; ein Slot-Klick startet die zugewiesene Geste, und die Auswahl unten weist ein Emote dauerhaft zu. Eigene Emotes lassen sich im Vorschau-Studio prüfen. Es werden keine Animationsskripte ausgeführt; die Darstellung ändert weder Serverbewegung noch Trefferbox.
- **Community-Veröffentlichung:** Beiträge sind beim Erstellen standardmäßig öffentlich, können aber lokal bleiben, verborgen, gelöscht und später erneut veröffentlicht werden. Die Community zeigt Minecraft-Spielernamen und veröffentlichte Modelle, Emotes oder Capes. Der kurzlebige Minecraft-Zugriffstoken wird nur zur Profilprüfung an den Votex-Dienst und den offiziellen Minecraft-Profilserver gesendet und nicht gespeichert. Details: [Community-Datenschutz](docs/COMMUNITY.md). Veröffentlichung benötigt ein angemeldetes Minecraft-Java-Konto; die Minecraft-API-Freigabe für Votex ist noch offen.
- **Animationen:** Acht eingebaute Gesten und ein frei belegbares Emote-Rad. Im Core B halten, auswählen, loslassen. Rechte Umschalttaste öffnet das Menü. Bewegung beendet eine Geste. Andere Spieler sehen lokale Darstellung nicht automatisch; sie können Community-Inhalte im Client ansehen und übernehmen.
- **Spielwerte:** Verständliche Vorlagen und echte Optionen für Sichtweite, Simulation, FPS, Grafik, VSync und RAM. Änderungen aus Minecraft werden nicht bei jedem Start überschrieben. Ein Upgrade erhält vorhandene Spieloptionen, bis du im Launcher eine Vorlage oder einen Wert wählst.
- **Java:** Ohne eigenen Pfad lädt Votex bei Bedarf eine zur Minecraft-Version passende kostenlose portable Laufzeit mit SHA-256-Prüfung. Ist Eclipse Temurin nicht erreichbar, wird die offizielle Azul-Zulu-Quelle verwendet. Die Java-Version wird vor der Übernahme geprüft. System-Java, PATH und Registry bleiben unverändert. Ein eigener Java-Pfad bleibt möglich.
- **Wiederherstellung:** Deutsche Erklärungen, Sicherungsverlauf, geschützter funktionierender Stand, Änderungsprüfung und isolierter Teststart. Gesichert werden Mods, Konfiguration und Spieloptionen; **Welten werden nicht zurückgesetzt**.

## Minecraft nach der API-Freigabe

Die eigene Microsoft-App von Votex ist bereits registriert. Der AppID-Antrag wurde eingereicht, der Empfang bestätigt; die Genehmigung ist weiterhin offen. Die Anmeldung umgeht diese Freigabe nicht. [Antrag](docs/MINECRAFT-APP-REVIEW.md) · [Anmeldung](docs/AUTHENTICATION.md).

1. Oben **Anmelden → Mit Microsoft anmelden**. Passwort und Gerätecode auf der Microsoft-Seite selbst eingeben.
2. Ein Profil erstellen. Für die aktuellen Votex-Funktionen im Spiel **Fabric 26.2** wählen.
3. **Installieren / prüfen**. Normale Fabric-26.2-Profile bereiten Core und Fabric API mit vor. Importierte Modpacks werden nicht automatisch um Votex ergänzt; dort kannst du Core ausdrücklich unter Mods installieren.
4. Optional Performance-Mods und **Votex SoftLight + Iris** wählen. SoftLight ist eine dezente eigene Farbkorrektur/Vignette, kein Raytracing- oder Schattenpaket. Kompatible Mod-Versionen werden vor Downloads geprüft. Installierte Mods lassen sich unter Mods einzeln deaktivieren.
5. **Minecraft starten**. Mod-Dateiänderungen gelten beim nächsten Start. Für Spiel und Kontobesitz wird ein berechtigtes Microsoft-Konto geprüft.

Core ist für Fabric **26.2** gebaut. Andere Versionen können über den Launcher verwaltet werden; sie besitzen nicht automatisch einen passenden Votex-Core. Fabric/Quilt-Metadaten, eine isolierte Forge-Installation und Java 25 wurden separat geprüft. Vollständige Starts aller Version-/Loader-Kombinationen, NeoForge, Shader-Darstellung und die persönliche Anmeldung sind weiterhin offen. Ein isolierter Fabric-Clienttest hat alle 45 Modelle und acht Gesten im tatsächlichen Minecraft-Renderer ausgeführt und aufgenommen; das bestätigt keine persönliche Kontoberechtigung. Es werden keine ungemessenen FPS- oder RAM-Gewinne versprochen.

## Daten & Upgrade

Eigene Daten liegen unter `%LOCALAPPDATA%/VotexClient/`. Die frühere Schema-Migration sichert die Launcher-Konfiguration als `config/launcher-before-0.2-*.json`. Für dieses Upgrade wird zusätzlich eine Kopie vor dem ersten Desktop-Start unter `config/launcher-before-0.3-*.json` erhalten. Das 0.3-Format übernimmt die vorhandenen Profile und Ausrüstungs-IDs. Profile, Kontoverweise und Farbänderungen bleiben erhalten. OAuth-Refresh-Tokens und ein eigener CurseForge-Schlüssel liegen in der Windows-Anmeldeinformationsverwaltung und werden nicht exportiert.

Core-Upgrades erhalten die vorherige JAR in der Quarantäne und vermeiden doppelte Mod-IDs. Ein deaktivierter Core bleibt beim automatischen Upgrade deaktiviert. Profile entfernen löscht nur den Listeneintrag; Instanzordner und Welten bleiben erhalten. Das Aufräumen von Sicherungen entfernt unreferenzierte Sicherungsblöcke, keine Welten.

## Aus Quellen bauen

Voraussetzungen: Node.js/pnpm, Rust stable MSVC, Microsoft C++ Build Tools/Windows SDK, WebView2 und JDK 25 (auch als Gradle-Laufzeit; ein neueres JDK muss von der verwendeten Gradle-Version ausdrücklich unterstützt werden).

```powershell
# JAVA_HOME auf ein JDK 25 setzen.
pnpm install --frozen-lockfile
pnpm test
pnpm build
cd minecraft/votex-core
.\gradlew.bat build --no-daemon
cd ../..
.\scripts\stage-core.ps1
cargo test --manifest-path apps/launcher/src-tauri/Cargo.toml
pnpm desktop:build
```

`pnpm dev` öffnet eine getrennte Browser-Vorschau mit lokalen Einstellungen und öffentlicher Modrinth-Suche. Konten, Installation und Dateiverwaltung gehören zur Windows-App. Im ursprünglichen Workspace aktiviert `. .\scripts\local-toolchain.ps1` die dort vorbereiteten Werkzeuge; diese sind nicht im Quellpaket enthalten.

[Prüfstand](docs/TEST-RESULTS.md) · [Bildquelle und genauer Prompt](docs/ARTWORK-PROMPT.md) · [Core-Render-Test](docs/CORE-RENDER-TEST.md) · [Architektur](docs/ARCHITECTURE.md) · [Pixelwolke](docs/BRANDING.md) · [Lizenzen](THIRD-PARTY-NOTICES.md)
