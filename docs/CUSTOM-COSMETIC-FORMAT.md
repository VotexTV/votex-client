# Votex-Kosmetikformat v1

Das Atelier exportiert portable Dateien mit der Endung `.votex-cosmetic.json`. Sie enthalten ausschließlich überprüfte Geometrie- und Animationswerte; der Import führt weder Skripte noch Dateipfade oder URLs aus. Die App begrenzt die Datei auf 256 KB, jedes Modell auf 1–24 Teile und baut daraus das Mesh selbst neu auf.

```json
{
  "format": "votex-cosmetic",
  "schemaVersion": 1,
  "name": "Kristallflügel",
  "slot": "wings",
  "primitives": [
    {
      "id": "part-example",
      "type": "plane",
      "joint": "body",
      "position": [0, 2, 0],
      "size": [8, 10, 0.2],
      "rotation": [0, 0, 0],
      "color": "#73aaff",
      "material": "polished",
      "animated": false
    }
  ]
}
```

`type` ist `cube`, `sphere`, `plane` oder `pixel`. `slot` ist `wings`, `head`, `face`, `pet`, `back`, `aura`, `overlay` oder `feet`. `joint` ist `head`, `body`, `leftArm`, `rightArm`, `leftLeg` oder `rightLeg`. Positionen liegen zwischen −32 und 32 Modellpixeln, Kantenlängen zwischen 0,2 und 32 und Drehungen zwischen −180° und 180°. Farben verwenden `#RRGGBB`. `material` ist optional und kann `matte`, `polished` oder `metal` sein.

Für `animated: true` ist zusätzlich ein `animation`-Objekt erforderlich:

```json
{"axis":"z","amplitude":0.28,"speed":2,"phase":0}
```

Achsen sind `x`, `y`, `z`; die Amplitude ist auf 0–1,2 Radiant, das Tempo auf 0,1–8× und die Phase auf −6,3 bis 6,3 begrenzt. Das Modell kann im Launcher-Studio und mit dem passenden Votex Core für Fabric 26.2 dargestellt werden. Materialwirkung unterscheidet sich: der Launcher nutzt Studio-PBR, Minecraft seine Entity-Materialien. Geometrie, Form, Position und Vertex-Farbe verwenden beide Darstellungen.

## Erweiterungen ab 0.4.0

Ein Teil kann optional `name` (maximal 40 Zeichen), `gradient: {"color":"#ffffff","axis":"y"}` und `paint` enthalten. `paint` ist ein Array aus genau 64 Farben im Format `#RRGGBB`. Das wiederholte 8×8-Muster wird pro Fläche in echte farbige Geometrie übersetzt und überschreibt dort den Verlauf. Es sind keine externen Texturpfade nötig. Kugeln verwenden ein vereinfachtes Muster auf ihren Flächen. Ein exportiertes Modell darf insgesamt höchstens 12.000 Eckpunkte enthalten.

Für frei gesetzte Rotations-Keyframes wird das vorhandene `animation`-Objekt um `duration` (1–10 Sekunden) und `keyframes` erweitert:

```json
{"axis":"z","amplitude":0.28,"speed":2,"phase":0,"duration":2,"keyframes":[{"time":0,"angle":0},{"time":0.5,"angle":0.8},{"time":1,"angle":0}]}
```

Es gelten 2–24 streng aufsteigend sortierte Keyframes. Der erste Zeitpunkt ist 0, der letzte 1; Winkel liegen zwischen −3 und 3 Radiant. Launcher und Core verwenden dieselbe weich interpolierte Schleife. Ein Animationskanal dreht ein Teil um eine gewählte Achse. Mehrere Teile können unabhängig animiert werden. Das ist ein begrenzter eingebauter Editor, kein vollständiges Blockbench-Dateiformat oder frei programmierbares Rig.

## Änderung 0.4.1

Pixel-Teile besitzen drei unabhängige Kantenlängen. Die frühere Begrenzung der Z-Tiefe auf 0,35 Modellpixel wurde aufgehoben; bestehende gespeicherte Größen bleiben erhalten. Körper- und Teil-Keyframes werden am expliziten Abspielkopf eingefügt; doppelte Zeitpunkte wählen den vorhandenen Frame. Die Grenzen bleiben 2–24 streng geordnete Frames mit festen Endpunkten.
