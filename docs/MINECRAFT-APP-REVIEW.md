# Minecraft AppID Review — eingereicht

Aktualisierung25.10.2026: Der Nutzer bestätigt weiterhin „Noch keine Rückmeldung“ im Antrags-Postfach. Eine begrenzte aktuelle Suche im verbundenen Gmail-Postfach ergab ebenfalls keine passende Freigabe; dieses Postfach unterscheidet sich jedoch vom Antragskontakt und kann dessen Mailstand nicht belegen. Es erfolgten keine erneute Einreichung, externe Nachfrage, Anmeldung oder Authentifizierungsprüfung. Die Freigabe ist weiterhin nicht bestätigt. Nachweis: work/ingame-goal/appid-response-check-20261025.json. Die folgenden Formularangaben dokumentieren die tatsächliche Einreichung vom19.10.2026 und sind kein aktueller Funktionskatalog.

Stand: 19. Oktober 2026. **Antrag eingereicht, Empfang bestätigt. Eine API-Genehmigung liegt noch nicht vor.**

Der Nutzer genehmigte die öffentliche Website mit Lasse Wieting / lassewieting@gmail.com als Herausgeber/Kontakt sowie die Übermittlung der genannten App-/Verzeichnisdaten an die Minecraft-Prüfstelle. Auf die gesonderte Frage zur Erklärung antwortete er ausdrücklich: „Ja, gelesen und verstanden“.

Danach wurden die fehlenden Angaben im [offiziellen Formular](https://aka.ms/mce-reviewappid) ergänzt, geprüft und genau einmal abgesendet. Erst zeigte die Seite den laufenden Versand, anschließend **„Thank you for contacting Mojang Studios.“** Es wurde keine zweite Antwort eingereicht. Der [Einreichungsnachweis](evidence/minecraft-review-confirmed.png) dokumentiert die Bestätigung.

## Übermittelte Angaben

| Feld | Wert |
| --- | --- |
| Request type | New AppID for Approval |
| Application Name | Votex Client |
| Application ID | 53e9fc70-9916-438a-8bb7-45c4f48f5557 |
| Associated website | https://votex-client-project.adoredpeach257.chatgpt.site |
| Contact | lassewieting@gmail.com, zuvor ausdrücklich freigegeben |
| Tenant ID | Die vorhandene Azure-Verzeichnis-ID wurde an die Prüfstelle übermittelt; sie wird hier nicht veröffentlicht |
| EULA / Usage Guidelines declaration | Yes, nach ausdrücklicher Bestätigung des Nutzers |

### Justification

Votex Client is an independent Windows desktop launcher with a separate Fabric client mod, currently in version 0.1.0 Alpha. It uses its own registered public Microsoft Application ID. Users authenticate directly with Microsoft through the OAuth 2.0 device authorization flow in their browser; Votex does not collect Microsoft passwords. The launcher implements the Xbox Live/XSTS exchange, Minecraft authentication, Java Edition entitlement verification and profile retrieval before saving a Minecraft account or launching the game. Access is requested so legitimate Java Edition owners can sign in and launch their licensed installation. The distribution contains the launcher and its original client mod, not Minecraft game binaries. Game files are downloaded from official HTTPS sources. Votex has no offline-account mode, uses no other launcher's Application ID, and does not provide functionality to bypass authentication/license checks or disable Minecraft safety features. Refresh tokens are stored in Windows Credential Manager, with no remote Votex sign-in backend.

### Any other information

This is a request for approval of a new AppID. Microsoft device authorization accepts this registration. A personal sign-in attempt reached the Minecraft authentication endpoint (authentication/login_with_xbox) and returned HTTP 403. The implemented OAuth scope is XboxLive.signin offline_access, with personal Microsoft accounts and public-client flows enabled.

## Öffentliche Projektseite

Die überarbeitete Website mit Pixelwolke, Glasnavigation, drei Farbstimmungen, echter Launcher-Abbildung, technischem Ablauf und Herausgeberkontakt wurde erfolgreich bereitgestellt. Danach wurde der Zugriff auf ausdrücklichen Nutzerwunsch öffentlich geschaltet. Eine Browserprüfung bestätigte die Seite ohne ChatGPT-Anmeldeschranke.

- Site-ID: appgprj_6ad5e0868dc08191b2ec055813bec724
- Gepushter Commit: c4bd34b9191998a315ecf9426068a694e735bc75
- Version: appgprj_6ad5e0868dc08191b2ec055813bec724~appgver_22415db5520c81918fa9b936d0ed49b3
- Deployment: appgdep_6ad5e71be130819196715cae798c6816, succeeded
- Öffentlicher Zugriff: public, Revision 2

Die Website enthält keine Anmeldung, Konto-Tokens, Tracker oder Formulare. Ihre lokal gespeicherte Farbauswahl benötigt keine Kontodaten. Die Tenant-ID steht nicht auf der Website. Die kurzfristigen Quellzugangsdaten wurden nicht in Dateien geschrieben.

Browserprüfung: Abschnittsnavigation, FAQ, geladene Assets, Farbumschaltung und Erhalt der Auswahl nach Neuladen. 1280-, 390- und 320-Pixel-Ansichten wurden betrachtet; kein horizontaler Überlauf bei den geprüften Mobilbreiten. Reduzierte Bewegung ist im CSS/JavaScript berücksichtigt; eine reale Betriebssystem-Präferenzänderung wurde nicht getestet.

## Noch ausstehend

Der Eingang ist keine Genehmigung. Nur die Minecraft-Prüfstelle kann den Zugriff freigeben. Das Formular nennt wöchentliche Prüfungen; daraus folgt kein verbindlicher Termin. Es wurden kein erneuter persönlicher Login und kein Minecraft-Spielstart nach dem Antrag durchgeführt. Ein Freigabemonitor wurde nicht eingerichtet.

Es wurden keine kostenpflichtigen Azure-Ressourcen, Lizenzen, Supportpläne, Käufe oder Abo-Upgrades ausgelöst. Die bestehende Windows-Sicherheitskonfiguration blieb unverändert.
