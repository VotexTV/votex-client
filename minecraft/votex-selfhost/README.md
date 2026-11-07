# Votex selbst betreiben

Dieses Paket bereitet einen echten Fabric-Server mit dem Votex-Begleiter und Simple Voice Chat vor. Optional wird eine eigene FRP-Verbindung zwischen einem öffentlich erreichbaren Betreibergerät und dem Rechner mit der Welt vorbereitet. Es legt keine Cloudkonten an, kauft keine Dienste und behauptet keine öffentliche Adresse, bevor eine reale Verbindung existiert.

Der In-Game-Ausbau ist noch in Arbeit. Die Server-Komponente ist zur lokalen Prüfung vorbereitet; daraus folgt keine Freigabe des gesamten Clients. Das Paket enthält weder Minecraft selbst noch Welten, Microsoft-Tokens oder erzeugte Tunnel-Schlüssel. `Prepare-Server.ps1` lädt die festgelegten Abhängigkeiten beim Betreiber und startet noch keinen Dienst.

## 1. Server vorbereiten

Voraussetzungen: Java 25 oder neuer, PowerShell 7, eine freie Minecraft-Java-Lizenz je Spieler und ein **neuer, leerer Zielordner**. Die Skripte lesen keine vorhandenen Votex-Profile, Kontotokens oder Welten.

```powershell
.\Prepare-Server.ps1 -Destination 'D:\Votex-Server' -JavaPath 'C:\Program Files\Java\jdk-27\bin\java.exe' -PublicHost 'spiel.example.org'
```

`PublicHost` gehört dir bzw. zeigt auf deinen tatsächlich erreichbaren Server. Ohne Tunnel kann der Parameter weggelassen werden. Das Skript lädt den Fabric-Installer 1.1.2, Minecraft 26.2 mit Loader 0.19.5, Fabric API 0.161.0 und Simple Voice Chat 2.6.24 von deren offiziellen Quellen; die Zusatzdateien werden gegen veröffentlichte Prüfsummen geprüft. Der Votex-Begleiter wird aus dem gebauten Quellprojekt bzw. dem Paket kopiert. In der Manifestdatei stehen die tatsächlichen SHA-256-Werte.

Lies die [Minecraft-EULA](https://www.minecraft.net/eula), setze bei Zustimmung `eula=true` in `eula.txt`, starte danach `Start-Server.ps1`. Der Server läuft zuerst nur auf `127.0.0.1:25565`, Simple Voice Chat nur auf `127.0.0.1:24454/UDP`. RCON und Query sind aus. Microsoft-Anmeldung (`online-mode=true`) und die Zugangsliste sind an.

Unter Linux kann die Vorbereitung ebenfalls mit PowerShell 7 (`pwsh -File ./Prepare-Server.ps1 ...`) ausgeführt werden. Zum Start im erzeugten Serverordner steht außerdem `sh ./Start-Server.sh` bereit. `JAVA_BIN=/pfad/zu/java MEMORY_GIB=4 sh ./Start-Server.sh` wählt Java und Arbeitsspeicher. Der Linux-Starthelfer ist Quelltext; der tatsächliche Dedicated-Start wurde auf Windows mit Java 27 geprüft. Beide Starthelfer verlangen Java 25+ und die ausdrücklich gesetzte EULA-Datei.

Füge erlaubte Spieler **in der Serverkonsole** hinzu:

```text
whitelist add ECHTER_MICROSOFT_SPIELERNAME
whitelist list
```

Gib `op` nur an Betreiber, die diese Rechte benötigen. Ändere für öffentliche Nutzung weder `online-mode` noch die Zugangsliste auf eine unsichere Ersatzlösung. Der Votex-Offline-Spielmodus ist keine authentifizierte Identität für einen öffentlichen Server. Mit `stop` fährt der Server sauber herunter; sichere den gesamten Serverordner im ausgeschalteten Zustand.

## 2. Eine erreichbare Adresse

**Direkter eigener Server:** Falls dieser Rechner bereits eine öffentliche Adresse bzw. eine kontrollierte Routerweiterleitung besitzt, setze `server-ip=` und `bind_address=` in der Voice-Konfiguration, damit der Server auf den vorgesehenen Schnittstellen lauscht. Erlaube auf dem Betreibergerät/Router gezielt **25565/TCP** und **24454/UDP**. Die Skripte ändern keine Firewall- oder Routereinstellungen. Ein gewöhnlicher LAN-Host hinter CGNAT ist allein noch keine aus dem Internet erreichbare Adresse.

**Eigener FRP-Server:** Wenn die Welt zuhause läuft, kann ein bereits vorhandenes, öffentlich erreichbares Betreibergerät den Zugang weiterleiten. Bereite auf deinem Rechner folgende Dateien vor:

```powershell
.\Prepare-Tunnel.ps1 -PublicHost 'spiel.example.org' -Destination 'D:\Votex-Tunnel-Privat' -DownloadBinaries
```

Das erzeugt neue, gegenseitig geprüfte TLS-Zertifikate, einen zufälligen 256-Bit-Zugangsschlüssel und FRP-0.71.1-Konfigurationen. Die CA wird nicht in Windows installiert; ihr privater Schlüssel wird nach dem Signieren verworfen. Server- und Clientzertifikate sind ein Jahr gültig. `public-server` gehört auf das öffentliche Gerät, `world-host` auf den Weltrechner. **Diese beiden Ordner enthalten private Betreiber-Schlüssel und gehören niemals in ein öffentliches Downloadpaket.** Spieler benötigen nur die Minecraft-Adresse und einen Eintrag in der Minecraft-Zugangsliste.

Die geprüften FRP-Archive enthalten `frps` und `frpc` für Windows bzw. Linux. Entpacke das passende Archiv. Starte aus dem jeweiligen Konfigurationsordner:

```text
frps -c frps.toml
frpc -c frpc.toml
```

`frps verify -c frps.toml` bzw. `frpc verify -c frpc.toml` prüft zunächst die Konfigurationsdatei. Unter Linux sollten private Schlüssel und Konfigurationen nur für den Betreiber lesbar sein (`chmod 600 *.key *.toml`). Wird ein Programm von einer lokalen Anwendungsrichtlinie blockiert, bleibt diese Richtlinie maßgeblich; dieses Paket ändert keine Sicherheitsregeln.

Auf dem öffentlichen Gerät werden nur **7000/TCP** für die abgesicherte Betreiberverbindung, **25565/TCP** für Minecraft und **24454/UDP** für Voice benötigt. Die Konfiguration erlaubt nur die beiden Spiel-/Voice-Proxyports und hat kein offenes Verwaltungs-Dashboard. Für TLS werden Servername, private CA und Clientzertifikat geprüft; es gibt keinen Schalter zum Überspringen der Zertifikatsprüfung.

Freunde verbinden sich anschließend im Multiplayer mit `spiel.example.org:25565`. Der Weltrechner verbindet sich weiterhin mit `localhost:25565`; sein eigener Voice-Host muss für lokale Tests gegebenenfalls `localhost:24454` sein. Die ausgehende Tunnelverbindung allein erzeugt weder einen öffentlichen Rechner noch DNS-Einträge.

**Bereits geöffnete Einzelspielerwelt:** Der Votex-Client aktiviert den eingebauten Relay auch im integrierten LAN-Server. Öffne die Minecraft-Multiplayeroptionen, verwende einen festen LAN-Port und gib ihn mit `-LocalMinecraftPort` an das Tunnelskript weiter. Diese Variante besitzt nicht dieselbe eigenständige Server-Zugangsliste wie die vorbereitete Dedicated-Variante. Für eine dauerhaft privat kontrollierte Welt wird deshalb der Dedicated-Server mit Microsoft-Authentifizierung und Zugangsliste verwendet. Es werden keine vorhandenen Einzelspielerwelten automatisch kopiert.

## 3. Voice und Votex-Spieler

Simple Voice Chat muss sowohl auf Server **als auch** im Minecraft-Profil der Spieler vorhanden sein. Der Votex-Voiceknopf öffnet die echte Voice-Oberfläche mitsamt deren Ersteinrichtung; Mikrofon und Audiogeräte werden dort gewählt. Standardmäßig ist `allow_recording=false`. Eine echte Tonverbindung muss mit zwei Geräten/Spielern geprüft werden. Ein installiertes JAR oder ein grüner Modstatus beweist keine funktionierende UDP- oder Mikrofonverbindung.

Votex-Emotes, gültige ausgewählte Skins/Capes, eigene Emotes und begrenzte eigene Modelle werden zwischen verbundenen Votex-Spielern in derselben Dimension innerhalb 128 Blöcken übertragen. Ein Handschlag erfordert eine Einladung und **G** beim Empfänger, Sichtkontakt und maximal 1,8 Blöcke Abstand. Der Relay versetzt keine Spieler und sendet keine Bewegungspakete. Spieler ohne Votex können weiter normal spielen, sehen die zusätzlichen Votex-Modelle aber nicht.

Die weltweite Freundesoberfläche von Minecraft 26.2 verwendet Mojang/Microsoft und dessen Konto-/Datenschutzeinstellungen. Der Begleiter ersetzt diesen Dienst nicht. Ein Clientkonto ohne abgeschlossene Microsoft-Anmeldung erhält darüber keine erfundene Onlinepräsenz.

## Stand der Nachweise

Am 21.10.2026 wurde der echte Dedicated-Server aus diesen Vorbereitungsskripten isoliert gestartet: Minecraft 26.2, Loader 0.19.5, Fabric API 0.161.0, Votex-Relay und Simple Voice Chat 2.6.24. Die tatsächliche Minecraft-Statusabfrage sowie die vom laufenden Server verlangte Microsoft-Sitzungsprüfung wurden über die lokale TCP-Verbindung geprüft. Minecraft lauschte nur auf `127.0.0.1:25565`, Voice nur auf `127.0.0.1:24454/UDP`. Die Zugangsliste war aktiv, leer und verpflichtend. Der Server wurde anschließend mit `stop` sauber beendet und speicherte alle Dimensionen.

Danach wurden zwei echte Minecraft-Prozesse gegen einen **getrennten, nur lokal erreichbaren Testserver** geprüft. Beide empfingen die Skins/Capes und das eigene Modell des anderen Spielers; Winken, ein übertragenes eigenes Emote und die Handschlag-Bestätigung mit **G** liefen über die echte Verbindung. Stoppen, echte Bewegung, zu großer Abstand beim Bestätigen, Ablauf, lokale Ablehnung, Bereichswechsel und Trennen wurden geprüft. Beide Clients und der Testserver bestätigten die echte Voice-UDP-Verbindung. Dabei blieben Audio und Mikrofon aus; ein nur im Test vorhandener Startblocker verhinderte jede Mikrofonaufnahme. Alle drei Prozesse wurden sauber beendet.

Noch getrennt zu prüfen sind: ein Microsoft-authentifizierter Spielerbeitritt, eine Verbindung von außerhalb des LAN, tatsächliches Voice-Audio, Laufzeitprüfungen mit einer sichtversperrenden Wand und die exakte Handberührung bei unterschiedlichen Spielergrößen. Der Zwei-Client-Nachweis verwendet lokale Testidentitäten und getrennte Servereinstellungen; dieses Paket behält Microsoft-Anmeldung und Zugangsliste bei. Die native FRP-Prüfung wurde auf diesem Windows-Gerät durch die Anwendungssteuerungsrichtlinie mit Fehler 4551 blockiert; es gibt deshalb hier keinen erfolgreichen FRP-TCP-/UDP- oder mTLS-Laufzeitnachweis. Die Prüfsummen des offiziellen Archivs und die erzeugten Zertifikate ersetzen diesen Nachweis nicht.

Die Vorbereitungsskripte starten weder Spiel noch Tunnel. Sie schaffen die Komponenten zum Selbsthosten; eine aus dem Internet erreichbare Installation hängt vom tatsächlich vorhandenen Betreibergerät, DNS/Portzugriff und der Zugangsliste ab. Für eine interne Quellprüfung stehen `scripts/verify-selfhost-server.py` (laufender privater Server) und `scripts/verify-selfhost-tunnel.py` (ausschließlich neue Loopback-Tunnelkonfiguration) bereit.

Primärquellen: [Fabric-Serverinstallation](https://fabricmc.net/use/server/), [Simple Voice Chat selbst hosten](https://modrepo.de/minecraft/voicechat/wiki/server_setup_self_hosted), [Voice-Serverkonfiguration](https://modrepo.de/minecraft/voicechat/wiki/server_config), [FRP TLS](https://gofrp.org/en/docs/features/common/network/network-tls/), [FRP-Serverkonfiguration](https://gofrp.org/en/docs/reference/server-configures/), [FRP 0.71.1](https://github.com/fatedier/frp/releases/tag/v0.71.1).
