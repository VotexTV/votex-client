# Windows-Start und Signierung — 0.4.1 RC1

## Aktueller Sperrbefund, 25. Oktober 2026

Die aktuellen Code-Integrity-Ereignisse 3033/3077 nennen die bereits installierte `AppData/Local/Votex Client/votex-launcher.exe`: Die Datei erfüllt die Signierungsanforderungen nicht. Ihr SHA256 ist `4a5b123735dad04a96d10d835ff79aa97662f04026d50dd9c4303ebc8b3ebf00`, Datum21.10.2026; sie ist älter als die neue Preview. Auch das neue Setup und der neue Launcher sind derzeit unsigniert. In den persönlichen bzw. Computer-Zertifikatsspeichern wurde kein gültiges Code-Signing-Zertifikat mit privatem Schlüssel gefunden. Ein Malwarefund folgt aus diesen Ereignissen nicht.

Microsoft beschreibt die zuverlässige Entwicklerlösung als Signierung mit einer vertrauenswürdigen Signierungsidentität; selbst erzeugte Zertifikate schaffen diese öffentliche Vertrauensstellung nicht. Quelle: https://learn.microsoft.com/en-us/windows/apps/develop/smart-app-control/code-signing-for-smart-app-control . Es gibt laut Microsoft keine allgemeine Einzel-App-Ausnahme in Smart App Control: https://support.microsoft.com/en-gb/windows/security/threat-malware-protection/smart-app-control-frequently-asked-questions . Die Installation eines unsignierten Setups kann gelingen, während der darin enthaltene Launcher anschließend gesperrt wird.

`scripts/build-signed-preview.ps1 -Mode Inspect` prüft nur die Bereitschaft und ändert keine Einstellung. Der vorbereitete Build-Modus verlangt ein vorhandenes CA-Code-Signing-Zertifikat mit privatem Schlüssel und die HTTPS-RFC3161-Zeitstempeladresse des Ausstellers. Er nutzt Tauri-Signierung für Launcher und NSIS-Setup, verlangt anschließend gültige Zeitstempelsignaturen beider EXEs und exportiert keinen privaten Schlüssel. Dieser Signierablauf konnte mangels Signierungsidentität noch nicht tatsächlich ausgeführt werden. Auch nach erfolgreicher Signierung ist eine reale Smart-App-Control-Prüfung nötig; Veröffentlichung dann unter einem neuen Dateinamen und mit neuen Prüfsummen.

Keine Tarnung, fremde Signatur, Sicherheitsausnahme, Richtlinienänderung, Änderung des Zertifikatvertrauens, Änderung der Dateizonen oder Ausführung über einen anderen Host wurde eingesetzt. Es wurde nichts gekauft oder extern zur Prüfung eingereicht.

Der Nutzer bestätigt, dass noch keine Signierungsidentität vorhanden ist. Eine mögliche kostenlose Route für geeignete Open-Source-Projekte ist die SignPath Foundation: https://signpath.org/ . Ihre Bedingungen unter https://signpath.org/terms verlangen unter anderem nachprüfbare Quellcode-/Build-Herkunft, Open-Source-Lizenzen, Maintainer-Rollen, MFA und manuelle Freigaben. Votex hat eine MIT-Lizenz, aber kein eingerichtetes öffentliches Quellcode-/CI-Signierprojekt. Eine Aufnahme ist nicht zugesagt und wäre keine sofortige Entsperrung. Kein Antrag wurde abgeschickt, kein Account erstellt und kein Quellcode zusätzlich öffentlich gemacht.

## Aktueller Stand, 21. Oktober 2026

Der optimierte Tauri-Build und NSIS-Installer 0.4.1 enthalten Core 0.4.1. Die EXE wurde mit einem neuen getrennten Testordner `VotexClient-QA-VPE041` gestartet. Der komplette kopierte Modsatz erreichte Minecraft 26.2 mit 64 Mods und öffnete die generierte Testwelt. Profile, Konten und Welten aus der normalen Installation wurden dabei nicht verändert. Der aktuelle Hash steht im Releasepaket unter `SHA256.json`.

Die EXE bleibt **unsigniert**. Erfolgreicher Start dieses Builds auf diesem Rechner garantiert keine Zulassung auf anderen Rechnern. Keine Schutzfunktion, App-Control-Richtlinie oder Zertifikatseinstellung wurde verändert. Es wurden keine kostenpflichtigen Dienste genutzt. Ein Excel-Dokument ist zum Starten des Launchers nicht vorgesehen; geliefert wird eine Windows-EXE.

## Historischer Stand 0.3.0

Stand: 19. Oktober 2026. Der optimierte Tauri-Release-Build mit eigenem Standardskin, GUI-Stufen und Creator Collection ist gebaut; der aktuelle konkrete Portable-Start war erfolgreich. Der reagierende Prozess hatte den Titel „Votex Client“ und benötigte keinen localhost-Entwicklungsserver. Dieser Prozessnachweis ersetzt keine direkte native Sicht-/Bedienprüfung.

Aktueller SHA-256 der gestarteten EXE: `6D56A2A33F1E45D23778CA2976F7C40560FFD4AB322847B5AB6B1DE62288ECD7`. Pfad/Prozess und Abgleich mit dem regulären Release-Artefakt stehen in `evidence/refinement-native-smoke.json`. Die Archive erhalten eigene Prüfsummen in `SHA256.json` im Ausgabeverzeichnis.

Die Anwendung ist **unsigniert**. Ein vorheriger Zwischenstand mit Hash `17146ADD254FF9739C33EF512859E548D86AE8A8ADA4A7B9DB1A3646C36477E0` wurde am 19.10.2026 um 22:23:37 durch Code Integrity (Ereignis 3077) abgewiesen. Danach wurden die tatsächlichen beauftragten Skin-/GUI-/Modell-Änderungen gebaut. Keine Schutzfunktion, Richtlinie, Vertrauensstellung oder Zertifikatsspeicher wurde verändert. Historie: `evidence/history-0.3-blocked-release.json`. Ein Start dieses konkreten Builds auf diesem Rechner garantiert keine Zulassung anderer Builds oder Rechner.

Beim ersten Creator-Start blieben drei Profile erhalten. Später änderte der Nutzer sie beim eigenen Test ausdrücklich zu zwei Profilen „Mein Abenteuer“; er bestätigte diese Änderungen. Der aktuelle Nachweis hält beide Zustände und die Bestätigung auseinander. Die aktuellen Benutzereinstellungen werden nicht zurückgesetzt.

Es wurden kein Code-Signing-Zertifikat, Signierungsdienst, Store-Konto, Asset oder Azure-Angebot gekauft. Für eine später signierte Veröffentlichung wird eine vertrauenswürdige Signierungsidentität bzw. geeignete Store-Verteilung benötigt; diese Preview nimmt keine solche Signierung vor.

EXE-Signierung und Minecraft-AppID-Zugriff sind getrennt. Der Minecraft-Antrag ersetzt keine Signatur; eine Signatur erteilt keinen Minecraft-API-Zugriff. Siehe `AUTHENTICATION.md` und `MINECRAFT-APP-REVIEW.md`.
