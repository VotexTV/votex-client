> Historical 0.2 checkpoint. Superseded by `REFINEMENT-03.md` and the current `TEST-RESULTS.md`.

# Umsetzung dieses Arbeitsstands abgeschlossen

19. Oktober 2026. Der jüngste Nutzerwunsch wurde in Preview 0.2.0 umgesetzt: größere Profilkarten, profilbezogene Mod-Verwaltung mit eigenem Installationsziel, native Mod-Übernahmeplanung samt kompatiblen Updates/Abhängigkeiten, Fingerprint-Prüfung und Sicherung/Rücksetzung; Übernahme auch während der Profilerstellung; neueste Versionen zuerst und Suche; kostenloser Katalog mit 37 eigenen Modellen und gemeinsamen Geometriedaten in Vorschau/Core.

EXE, NSIS, portable/source ZIP, Core und Quellen sind paketiert. Desktop `Votex Client.lnk` zeigt auf die neue portable EXE. Diese wurde bei gestoppter Entwicklervorschau gestartet und bleibt offen. Native Konfigurationsmigration inklusive Sicherung ist belegt. Siehe TEST-RESULTS.md und evidence/preview-native-smoke.json.

Restliche externe/praktische Prüfungen sind ausdrücklich dokumentiert: Mojang-Appfreigabe und vollständige Anmeldung/Spielstart, neue Render-Mixins im echten Fabric-26.2-Client, natives Drag/Drop, alle Loader/Versionen, CurseForge mit eigenem Schlüssel, Installer-Lebenszyklus und Messungen. Kein Signing-Zertifikat vorhanden. Die neue native Test-EXE wurde von Windows 4551 blockiert; der letzte zusätzlich eingefügte Rücksetztest konnte nicht laufen. Keine Schutzfunktion wurde verändert. Vorheriger nativer Stand 38 Tests bestanden; separate sechs Übernahmetests inklusive echten Downloads erfolgreich.

Keine Käufe, bezahlten Dienste oder neuen Azure-Ressourcen. Keine neue Website-Veröffentlichung, kein erneuter Mojang-Antrag und keine externen Nachrichten in diesem Arbeitsdurchlauf.
