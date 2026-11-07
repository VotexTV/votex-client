# Microsoft-Anmeldung für Minecraft Java

Stand: 19. Oktober 2026.

## Aktueller Stand

Die native Microsoft-Gerätecode-Anmeldung mit Xbox-/XSTS-Austausch, Minecraft-Java-Berechtigungsprüfung und Java-Profilabfrage ist implementiert. Der Login-Dialog bietet Code kopieren, Microsoft-Seite erneut öffnen, Ablaufanzeige, Abbrechen, Kontowechsel und lokales Abmelden. Die Anmeldung öffnet Microsoft im Systembrowser. Votex enthält kein Passwortformular.

**Votex ist jetzt bei Microsoft registriert.** Der Kontoinhaber hat sein Azure-Verzeichnis eingerichtet und die kostenlose App-Registrierung einschließlich der Microsoft-Plattformrichtlinien ausdrücklich freigegeben. Die App heißt **Votex Client**, unterstützt ausschließlich persönliche Microsoft-Konten und hat die öffentliche Client-ID **53e9fc70-9916-438a-8bb7-45c4f48f5557**. „Öffentliche Clientflows zulassen“ wurde aktiviert und erfolgreich gespeichert. Es wurde kein Client-Secret erstellt.

Die ID ist in `packages/shared-config/microsoft.json` hinterlegt und wird mit Frontend und Desktop-App gebaut. Spieler müssen sie nicht selbst eintragen. Ein echter Aufruf des Microsoft-Gerätecode-Endpunkts mit dieser ID und dem Votex-Scope wurde akzeptiert; die temporäre Anfrage wurde anschließend ohne Kontofreigabe abgebrochen.

**Der anschließende persönliche Kontotest ist bei Minecraft blockiert.** Der Nutzer zeigte am 19. Oktober den nativen Kontodialog mit `MINECRAFT_APP_NOT_APPROVED`. Im aktuellen Code entsteht dieser Text ausschließlich bei HTTP 403 vom Minecraft-Endpunkt `authentication/login_with_xbox`, nach dem Microsoft-/Xbox-/XSTS-Austausch. Die Einordnung des HTTP-Status ist aus diesem Codepfad abgeleitet; die rohe Anbieterantwort wurde nicht protokolliert. Minecraft-Java-Berechtigung, Profil, gespeicherte Anmeldung und Spielstart sind damit nicht bestätigt.

Der Nutzer hat die öffentliche Projektseite mit Herausgeber/Kontakt und die Übermittlung von E-Mail, App-ID und Tenant-ID an die Minecraft-Prüfstelle freigegeben. Auf die separate Frage zur Erklärung im Formular antwortete er ausdrücklich: „Ja, gelesen und verstanden“. Danach wurde der Antrag über das [offizielle AppID-Reviewformular](https://aka.ms/mce-reviewappid) am 19. Oktober abgesendet. Die Seite bestätigte: „Thank you for contacting Mojang Studios.“ Die [Projektseite](https://votex-client-project.adoredpeach257.chatgpt.site) ist öffentlich ohne ChatGPT-Anmeldung erreichbar. **Dies bestätigt den Antragseingang, keine Minecraft-API-Genehmigung.** Siehe [Antragsstand](MINECRAFT-APP-REVIEW.md). Erneute Anmeldeversuche ersetzen die externe Freigabe nicht.

Die Windows-Signierung der EXE ist eine getrennte Voraussetzung; siehe [WINDOWS-SIGNIERUNG.md](WINDOWS-SIGNIERUNG.md).

## Einmalige Einrichtung durch den Herausgeber

Eine **Client-ID** ist die öffentliche Kennung des Launchers bei Microsoft. Sie ist weder ein Minecraft-Benutzername noch ein Passwort, Client-Secret oder Windows-Signierungszertifikat. Die folgenden Herausgeberschritte sind für Votex bereits erledigt; sie dienen als Dokumentation für eigene Forks. Spieler können direkt **Mit Microsoft anmelden** wählen.

1. Im [Microsoft Entra Admin Center](https://entra.microsoft.com/) mit einem Konto anmelden, das in einem geeigneten Verzeichnis Apps registrieren darf. Zeigt Microsoft den Hinweis zu Anwendungen außerhalb eines Verzeichnisses, muss der Kontoinhaber zuerst ein geeignetes Verzeichnis einrichten. Eine Registrierung des Launchers ist kein Grund, einen kostenpflichtigen Cloud-Dienst bereitzustellen.
2. **Entra ID → App-Registrierungen → Neue Registrierung** öffnen, Name **Votex Client**. Als unterstützte Kontotypen **Persönliche Microsoft-Konten** oder die entsprechende Variante einschließlich persönlicher Konten wählen.
3. Unter **Authentifizierung → Erweiterte Einstellungen** **Öffentliche Clientflows zulassen / Allow public client flows** aktivieren. Der implementierte Gerätecodefluss benötigt keinen Redirect-Listener und kein Client-Secret. Ein zusätzliches Client-Secret wird nicht erstellt oder in der App abgelegt.
4. Die **Application (client) ID** von der Übersichtsseite kopieren. Für diese lokale Alpha kann sie unter **Settings → Microsoft-Anmeldung einrichten** eingetragen werden.
5. Für weitergegebene Builds dieselbe öffentliche ID in `packages/shared-config/microsoft.json` eintragen und Frontend sowie Desktop-App neu bauen. Beide verwenden diese gemeinsame Herausgeberkonfiguration. Bestehende Konfigurationen ohne eigene ID übernehmen die mitgelieferte ID. Ein expliziter lokaler Wert hat Vorrang.
6. Den Zugang der neuen App zu den Minecraft-Diensten klären. Die [Dokumentation von minecraft-launcher-lib](https://minecraft-launcher-lib.readthedocs.io/en/stable/tutorial/microsoft_login.html#apply-for-permission) verweist auf das [Minecraft-Antragsformular für App-IDs](https://aka.ms/mce-reviewappid). Eine Entra-Registrierung allein belegt keine Minecraft-API-Freigabe. Für Votex wurde der Antrag eingereicht und der Empfang bestätigt; eine Genehmigung liegt noch nicht vor.

## Anmeldung im Launcher

1. Oben auf **Anmelden → Mit Microsoft anmelden** klicken.
2. Den angezeigten Code auf der Microsoft-Seite im Systembrowser eingeben und dort das Konto wählen, dem Minecraft Java gehört. Microsoft zeigt die angefragte App und den Zugriff zur Prüfung an. Die Kontobestätigung führt der Nutzer selbst durch.
3. Votex wartet automatisch innerhalb der von Microsoft angegebenen Gültigkeit. **Code kopieren** und **Microsoft-Seite öffnen** helfen beim Wechsel zum Browser. **Anmeldung abbrechen**, Escape und das Schließen des Dialogs beenden die lokale Prüfung. Eine bereits abgeschlossene Anmeldung kann anschließend über **Abmelden** entfernt werden.
4. Erst nach Xbox-/XSTS-Anmeldung, Minecraft-Berechtigung und Java-Profilprüfung wird das Minecraft-Konto gespeichert. Fehlende Java-Berechtigung, fehlendes Xbox-Profil, Familienanforderungen, abgelaufene Codes und abgelehnte App-Anfragen werden als Fehler dargestellt.
5. Für einen Microsoft-Spielstart muss ein gespeichertes Konto ausgewählt sein. Vor dem Start wird seine Anmeldung erneuert und die Berechtigung erneut geprüft. Der ausdrücklich getrennte Offline-Modus verwendet einen lokalen Namen für Einzelspieler und Offline-Server. Er meldet sich nicht an Online-Servern oder Realms an und schaltet die gesperrte Microsoft-Verbindung nicht frei.

Der Browser unter `127.0.0.1:1420` ist nur die Entwicklungsvorschau. Er zeigt die Einrichtung und Oberfläche, führt aber keinen nativen Microsoft-/Minecraft-Login aus.

## Speicherung und Abmelden

Nur der Microsoft-Refresh-Token wird im Windows-Anmeldeinformationsspeicher gesichert, unter Dienst `VotexClient` und einem Schlüssel aus Client-ID plus Minecraft-UUID. Öffentliche Profilmetadaten einschließlich der zugehörigen App-ID stehen in `accounts/metadata.json`. Zugangstokens und der private Gerätecode werden nicht an React zurückgegeben oder in die Launcher-Konfiguration geschrieben. Minecraft erhält den benötigten Spieltoken in seinen Prozessargumenten; Launcher-Logs und erfasste Spielausgaben redigieren ihn.

Die Bindung an die Client-ID verhindert die Verwendung eines gespeicherten Refresh-Tokens mit einer nachträglich anders konfigurierten App. Ältere Alpha-Kontoeinträge ohne App-Bindung benötigen eine erneute Anmeldung. Eine Tokenrotation wird vor nachfolgenden Xbox-/Minecraft-Anfragen gespeichert, damit ein späterer Netzwerkfehler die erneuerte Anmeldung nicht verwirft.

**Abmelden** entfernt den gespeicherten Anmeldetoken und den lokalen Kontoeintrag. Es meldet den Nutzer nicht global auf anderen Geräten oder im Microsoft-Browser ab. Spielstände werden dabei nicht entfernt. Für einen vollständigen Entzug erteilter Microsoft-Zustimmungen gilt die Microsoft-Kontoverwaltung.

## Prüfgrenze

13 neue Rust-Authentifizierungstests prüfen unter anderem echte lokale HTTP-Anfragen für wartende/erfolgreiche/abgelehnte Gerätecodeantworten, die zusätzliche Wartezeit bei `slow_down`, Abbruch und Ablauf während einer laufenden Anfrage, die Trennung alter/neuer Versuche, URL-Prüfung, Geheimnisredaktion und App-Bindung. Die Testwerte sind erfundene lokale Fixtures. Es wurden keine fremden Launcher-IDs verwendet.

Zusätzlich wurde der explizite Online-Test `live_registered_device_authorization` ausgeführt: `MICROSOFT_DEVICE_AUTHORIZATION_PASS`. Er ruft den echten Microsoft-Endpunkt mit der registrierten Votex-ID auf, prüft die Gerätecodeantwort und bricht den Versuch ab. Er zeigt keine Codes oder Tokens im Testprotokoll und legt kein Konto an.

Die Tests ersetzen keinen vollständig bestätigten Microsoft-/Xbox-/Minecraft-Login. Der persönliche Versuch erreichte inzwischen den beschriebenen Minecraft-Fehler; ein erfolgreicher End-to-End-Test bleibt offen. Die Windows-Anwendungssteuerung wurde nicht verändert.

## Kostenrahmen

Der Nutzer hat ein Budget von **0 Euro** vorgegeben. Für diese Einrichtung wurde ausschließlich die App-Registrierung im vorhandenen **Microsoft Entra ID Free** verwendet. Es wurden weder Azure-Compute-/Speicherressourcen, kostenpflichtige Lizenzen oder Supportpläne bereitgestellt noch ein Abo-Upgrade durchgeführt. Die App läuft lokal auf Windows. Die zuvor gelesene Abrechnungsübersicht zeigte 0,00 Euro fällig und keine gemeldeten Gebühren; dies ist keine Prüfung der Bankumsätze oder Zusage über spätere manuelle Änderungen am Azure-Konto.

## Quellen

- [Microsoft: Gerätecodefluss, Pflichtparameter und Antworten](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code)
- [Microsoft: App registrieren und persönliche Konten unterstützen](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app)
- [Microsoft: Öffentliche Clientflows konfigurieren](https://learn.microsoft.com/en-us/entra/identity-platform/scenario-desktop-app-configuration#enable-public-client-flow)
- [minecraft-launcher-lib: Minecraft-App-Freigabe](https://minecraft-launcher-lib.readthedocs.io/en/stable/tutorial/microsoft_login.html#apply-for-permission)
