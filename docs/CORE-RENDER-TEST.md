# Echter Core-Render-Test — 0.3.0

Der separate Fabric-ClientGameTest wurde mit Java 25, Gradle 9.7.1, Minecraft 26.2, Fabric Loader 0.19.5 und Fabric API 0.161.0+26.2 ausgeführt. Der Quelltext liegt in `minecraft/votex-core/src/gametest/` und wird nicht in die verteilte Core-JAR aufgenommen.

Grundlage ist der offizielle [Fabric-Testablauf](https://docs.fabricmc.net/develop/automatic-testing). Der Test startet Minecraft in einem eigenen Arbeitsverzeichnis unter `minecraft/votex-core/build/run/clientGameTest/`, erstellt eine getrennte flache Welt und nutzt Fabric-Testidentität Player0. Weder normale Launcher-Konten noch vorhandene Welten werden dafür verwendet. Das ist kein Nachweis einer persönlichen Microsoft-Anmeldung und keine Umgehung der Anmeldung im verteilten Launcher.

Ablauf:

1. Tatsächliches Minecraft mit Core und seinen Render-/Pose-Mixins starten; Core und Spieler müssen initialisiert sein.
2. Alle 45 Einträge aus dem gebündelten Katalog laden, echte Geometriedaten prüfen und einzeln ausrüsten. Rückenobjekte und Capes von hinten aufnehmen.
3. Alle acht Gesten nacheinander im tatsächlichen Spielermodell abspielen/wechseln und aufnehmen; anschließend die neutrale Pose aufnehmen.
4. Testwelt sauber schließen. Erfolg erst nach `VOTEX_RENDER_SMOKE_PASS 0.3.0` und erfolgreichem Gradle-Ende.

Ergebnis: **erfolgreich**, 45 Modellbilder, 8 Gestenbilder und eine abschließende Neutralpose. Kontaktbögen und Originalbilder liegen unter `evidence/refinement/creator-core-render/`, vollständiges Log unter `evidence/creator-core-render-test.txt`. Die Bilder wurden visuell geprüft: gekrümmte Federn/Membranen, Kopf-/Gesichtsobjekte, Begleiter, Rückenteile, Auren, Outfits, Schuhe und drei Cape-Texturen werden tatsächlich dargestellt.

Das Log enthält Umgebungswarnungen von Windows-Leistungszählern und Authentifizierungsversuche von Realms/Profilschlüsseln mit der synthetischen Fabric-Identität. Diese sind kein erfolgreicher Login und kein Votex-AppID-Test. Es wurden keine Windows-Zähler oder Sicherheitsregeln verändert. Der automatische Test ersetzt keine längere Spiel-/Serverprüfung, subjektive Animationsabnahme oder Benchmark-Messung; die sichtbare FPS-Zahl in Bildern ist kein Vergleichstest.

Reproduktion mit einem von Gradle unterstützten JDK 25:

```powershell
cd minecraft/votex-core
.\gradlew.bat runClientGameTest --offline --no-daemon
```

`--offline` setzt bereits vorhandene offizielle Abhängigkeiten/Assets voraus. Java 27 konnte den neu konfigurierten Gradle-Build mit `Unsupported class file major version 71` nicht analysieren; der erneute Lauf mit dem vorhandenen JDK 25 war erfolgreich. Ein eventuell laufendes Benutzer-Minecraft ist kein Bestandteil dieses Tests.

Die aktuelle Revision enthält neun importierte Creator-Modelle. Der Test verlangt diese ausdrücklich und prüft vorhandene PNG-Texturressourcen. Er erfasst insgesamt 54 frische Bilder; Datum und Anzahl stehen in `evidence/creator-core-run.json`. Frühere 37-Modell-Aufnahmen bleiben als Historie erhalten. Der feste Katalogzähler im Test wurde von 37 auf 45 aktualisiert, nachdem er den ersten erweiterten Lauf korrekt abgewiesen hatte.
