# Votex Social Relay

Optionaler Fabric-Server-Begleiter für Minecraft **26.2**, Fabric Loader **0.19.5+**, Fabric API **0.161.0+26.2**, Java **25+**. Der Serverteil startet keine externen Dienste und benötigt keinen weiteren Port. Auf einem externen Server ist dieser Begleiter notwendig, damit Votex-Spieler die zusätzlichen Votex-Emotes, Skins, Capes und Kosmetikmodelle gegenseitig sehen. Andere Spieler können weiterhin normal beitreten.

Build im Quellprojekt unter Windows:

```powershell
cd minecraft\votex-core
.\gradlew.bat -p ..\votex-relay build
```

Das JAR liegt danach unter `minecraft/votex-relay/build/libs/votex-social-relay-0.4.1.jar`. Es gehört zusammen mit Fabric API in den `mods`-Ordner des **Fabric-Servers**. Das Client-Core-JAR gehört nicht auf einen Dedicated Server. Die im Votex-Client integrierte Einzelspieler-/LAN-Welt registriert den Begleiter bereits intern.

## Tatsächliche Funktionen

- Ein ausdrücklicher Protokoll-Handshake verhindert Social-Pakete an Server, die den Begleiter nicht anbieten.
- Der Relay liest UUID und Spielernamen von der Minecraft-Verbindung. Ein Client kann keine andere Absenderidentität angeben. Auf Servern mit `online-mode=false` prüft Minecraft selbst keine Microsoft-Identität; der Client zeigt diesen Zustand ausdrücklich an.
- Nur verbundene Votex-Spieler derselben Dimension innerhalb 128 Blöcken erhalten die ausgewählten Looks. Beim Verlassen, Dimensionswechsel und Entfernen aus dem Bereich werden Modelle, Texturen und Emotezustände wieder entfernt.
- Ausgewählte eingebaute Kosmetik und begrenzte eigene Modelle mit Vertexfarben, validierte 64×64-Skins/64×32-Capes sowie eigene Emotes werden übertragen. Es werden keine URLs, Dateien von beliebigen Pfaden, Skripte oder Netzwerkbewegungen ausgeführt.
- Emote-Starts teilen denselben zukünftigen Welt-Tick. Geschwindigkeit von 0,5× bis 2× und Wiederholung werden weitergegeben. Wiederholungen enden serverseitig spätestens nach 30 Sekunden; Bewegen, Springen, Schleichen, Tod und Zustandswechsel brechen ab.
- Ein Handschlag benötigt eine einmalige Einladung und die Bestätigung des **eingeladenen** Spielers mit **G**. Einladungen verfallen nach zehn Sekunden. Beide müssen höchstens 1,8 Blöcke auseinander stehen, Sichtkontakt haben und ruhig stehen. Für sichtbaren Handkontakt sollten sie einander ansehen. Weder Position, Blickrichtung noch Kamera werden erzwungen.
- Pro Spieler werden Inhaltsgröße, Geometrie, Animationswerte, Bildmaße, Partikel-/Modellanzahl, Paketfrequenz und Übertragungsdauer begrenzt. Komprimierte Inhalte werden außerhalb des Spiel-/Renderthreads geprüft. Bestätigungen mit Inhalts-Hash erlauben erneutes Senden bei Überlast.

## Infrastrukturgrenzen

Der Begleiter ist kein weltweiter Freundes-, Identitäts-, Voice- oder Hostingdienst. Die aktuelle Minecraft-26.2-Freundesoberfläche verwendet den Mojang/Microsoft-Dienst und dessen Konto-/Datenschutzeinstellungen. Simple Voice Chat benötigt seine eigene Client- und Serverinstallation sowie die dort konfigurierte UDP-Erreichbarkeit. Eine LAN-Freigabe allein macht eine Welt nicht aus dem Internet erreichbar. Für adressbasierten Fernzugriff wird ein betreuter Server oder ein Tunnel benötigt.

Der reale Zwei-Client-Test gegen einen separaten lokalen Dedicated-Server wurde am 21.10.2026 erfolgreich durchgeführt: empfangene PNG-Skins/Capes und eigenes Modell, eingebaute/eigene Emotes, G-Zustimmung, gegenseitiges Abbrechen bei Stop/Bewegung/Trennen sowie Bereichswechsel und echte Voice-UDP-Initialisierung. Die beiden Testprozesse verwendeten ausdrücklich lokale Offline-Identitäten; der vorbereitete Betreiber-Server behält `online-mode=true` und seine Zugangsliste. Mikrofon und Audio blieben im Test deaktiviert. Einzelheiten und tatsächliche Grenzen stehen in `docs/SELFHOST-VALIDATION.md`.

Microsoft-authentifizierte Spieler, öffentliche Erreichbarkeit, Paketverlust, Wand-/Sichtkontaktfälle, genaue Handberührung bei unterschiedlichen Größen sowie hörbares Voice-Audio sind weiterhin getrennte Nachweise vor einer vollständigen öffentlichen Freigabe.

Technische Primärquellen: [Fabric Networking 26.2](https://docs.fabricmc.net/develop/networking), [Simple Voice Chat Installation](https://modrepo.de/minecraft/voicechat/wiki), [Simple Voice Chat 26.2 Quellcode](https://github.com/henkelmax/simple-voice-chat/tree/26.2).
