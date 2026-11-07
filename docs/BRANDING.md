# Votex-Pixelwolke

Die Pixelwolke ersetzt das bisherige Blitzsymbol. Sie wird im Launcher, im eingebetteten Windows-App-Icon, im Installer, in der Desktop-Verknüpfung und auf der Projektseite verwendet.

Die ursprüngliche Form ist ein 24 × 14-Raster in `packages/brand/pixel-cloud.json`. Vier helle Blau-/Weißtöne geben ihr Tiefe; die Kanten bleiben eckig. Das Motiv verwendet keine Minecraft-Spielassets oder heruntergeladene Bilder.

`scripts/build-brand-assets.py` exportiert mit Pillow das transparente SVG, ein 512-Pixel-App-Symbol sowie Windows PNG/ICO. Das ICO enthält 16-, 24-, 32-, 48-, 64-, 128- und 256-Pixel-Größen. Generierte Assets liegen bereits im Quellpaket; normale Builds benötigen keinen erneuten Export.

```powershell
python scripts/build-brand-assets.py
# Optional mit vorhandener Website-Ausgabe:
python scripts/build-brand-assets.py --site C:\Pfad\zu\votex-project-site\dist
pnpm desktop:build --debug
```

Der Paketierer kopiert das ICO zusätzlich als `resources/votex-cloud.ico` in den portablen Ordner. Die Desktop-Verknüpfung nutzt diese neue Icon-Datei; ihr Ziel und Arbeitsverzeichnis bleiben die portable Anwendung. Der neue Dateiname vermeidet die Wiederverwendung des bisherigen Blitz-Icons über den alten EXE-Icon-Pfad. Ein sichtbarer Explorer-Desktop wurde nicht automatisiert geprüft.

Das Rust-Buildskript beobachtet ICO und PNG ausdrücklich. Damit erneuert ein weiterer Logoexport auch die eingebettete Windows-Ressource im inkrementellen Build. Das Icon wurde aus einer Kopie der endgültigen EXE an einem frischen Dateipfad extrahiert und als Pixelwolke visuell geprüft.

Die Website nutzt Glasflächen, eine schwebende Navigation, drei lokale Farbstimmungen und dezente Bewegung. Das ist ein eigenständiges, von der gewünschten iOS-Glasästhetik inspiriertes Design. Es besteht keine Verbindung zu Apple. Die Website führt keine Microsoft-Anmeldung aus.
