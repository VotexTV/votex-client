import { Select } from "./Select";
import { Timeline } from "./Timeline";
import { insertionAt, interpolateAt } from "../data/timeline";
import { useMemo, useRef, useState } from "react";
import { Copy, Download, Plus, Redo2, Undo2, Upload, X } from "lucide-react";
import type { AppState } from "../../../../packages/shared-config/schema";
import { GlassButton, Modal, Toggle } from "./Glass";
import { RangeField } from "./RangeField";
import { CreatorViewport } from "./CreatorViewport";
import {
  buildCustomCosmetic,
  parseCustomCosmeticFile,
  type CustomCosmetic,
  type CustomPrimitive,
} from "../data/customModels";
import type { Look } from "./Wearables";
import { exportJson, readJson, uid } from "../services/desktop";
import { CommunityVisibilityField } from "./CommunityVisibility";
import type { CommunityVisibility } from "../../../../packages/shared-config/schema";

type Slot = CustomCosmetic["slot"];
const slots: Array<[Slot, string]> = [
  ["wings", "Flügel"],
  ["head", "Kopf"],
  ["face", "Gesicht"],
  ["back", "Rücken"],
  ["aura", "Aura"],
  ["overlay", "Outfit"],
  ["feet", "Schuhe"],
  ["pet", "Begleiter"],
];
const newPrimitive = (
  type: CustomPrimitive["type"] = "cube",
): CustomPrimitive => ({
  id: `part-${uid()}`,
  type,
  joint: "body",
  position: [0, 0, 0],
  size: type === "pixel" ? [1, 1, 1] : [3, 3, 3],
  rotation: [0, 0, 0],
  color: "#92bfff",
  material: "matte",
  animated: false,
  animation: { axis: "z", amplitude: 0.28, speed: 2, phase: 0 },
});

type PrimitiveHistory = {
  past: CustomPrimitive[][];
  present: CustomPrimitive[];
  future: CustomPrimitive[][];
  lastEdit?: { key: string; at: number };
};

export function CustomCosmeticEditor({
  skin,
  model,
  initialModel,
  onClose,
  onSave,
}: {
  skin?: string;
  model?: "default" | "slim";
  look: Look;
  savedModels: AppState["customCosmetics"];
  initialModel?: CustomCosmetic;
  onClose: () => void;
  onSave: (
    item: CustomCosmetic,
    visibility: CommunityVisibility,
  ) => Promise<void> | void;
}) {
  const [name, setName] = useState(
      initialModel?.name ?? "Mein Community-Modell",
    ),
    [slot, setSlot] = useState<Slot>(initialModel?.slot ?? "wings");
  const [playing, setPlaying] = useState(false),
    [paintColor, setPaintColor] = useState("#92bfff");
  const [partCursor, setPartCursor] = useState(0.5);
  const [animationMode, setAnimationMode] = useState(false),
    [keyframeIndex, setKeyframeIndex] = useState(1);
  const [history, setHistory] = useState<PrimitiveHistory>(() => ({
    past: [],
    present: initialModel ? structuredClone(initialModel.primitives) : [],
    future: [],
  }));
  const primitives = history.present;
  const [selected, setSelected] = useState(0),
    [visibility, setVisibility] = useState<CommunityVisibility>(() =>
      initialModel?.communityId
        ? "private"
        : (initialModel?.visibility ??
          (initialModel?.published
            ? "public"
            : initialModel
              ? "private"
              : "public")),
    ),
    [saving, setSaving] = useState(false);
  const [snapToGrid, setSnapToGrid] = useState(false),
    [fileNotice, setFileNotice] = useState("");
  const importInput = useRef<HTMLInputElement>(null);
  const id = useMemo(
    () => initialModel?.id ?? `custom-${uid()}`,
    [initialModel?.id],
  );
  const active = primitives[selected];
  const draft = useMemo(
    () =>
      buildCustomCosmetic(id, name.trim() || "Mein Modell", slot, primitives),
    [id, name, slot, primitives],
  );
  const vertexCount = draft.model.parts.reduce(
    (sum, p) => sum + p.positions.length / 3,
    0,
  );
  const commitPrimitives = (
    change: (items: CustomPrimitive[]) => CustomPrimitive[],
    batchKey?: string,
  ) =>
    setHistory((current) => {
      const next = change(current.present);
      if (next === current.present) return current;
      const now = Date.now();
      const merge =
        !!batchKey &&
        current.lastEdit?.key === batchKey &&
        now - current.lastEdit.at < 500;
      return {
        past: merge
          ? current.past
          : [...current.past.slice(-59), current.present],
        present: next,
        future: [],
        ...(batchKey ? { lastEdit: { key: batchKey, at: now } } : {}),
      };
    });
  const updatePart = (patch: Partial<CustomPrimitive>, batchKey?: string) =>
    commitPrimitives((items) => {
      if (!items[selected]) return items;
      return items.map((part, index) =>
        index === selected ? ({ ...part, ...patch } as CustomPrimitive) : part,
      );
    }, batchKey);
  const animatePart = (index = selected) => {
    setSelected(index);
    setAnimationMode(true);
    setPlaying(false);
    setKeyframeIndex(1);
    setPartCursor(primitives[index]?.animation?.keyframes?.[1]?.time ?? 0.5);
    commitPrimitives((items) =>
      items.map((part, i) =>
        i === index
          ? {
              ...part,
              animated: true,
              animation: {
                axis: "z",
                amplitude: 0.28,
                speed: 2,
                phase: 0,
                ...part.animation,
                duration: part.animation?.duration ?? 2,
                keyframes: part.animation?.keyframes ?? [
                  { time: 0, angle: 0 },
                  { time: 0.5, angle: 0.5 },
                  { time: 1, angle: 0 },
                ],
              },
            }
          : part,
      ),
    );
  };
  const previewParts = useMemo(
    () =>
      primitives.map((part, index) => {
        if (
          !animationMode ||
          playing ||
          index !== selected ||
          !part.animation?.keyframes
        )
          return part;
        const rotation = [...part.rotation] as [number, number, number],
          axis = { x: 0, y: 1, z: 2 }[part.animation.axis];
        rotation[axis] +=
          (interpolateAt(
            part.animation.keyframes.map((f) => f.time),
            part.animation.keyframes.map((f) => f.angle),
            partCursor,
          ) *
            180) /
          Math.PI;
        return { ...part, rotation };
      }),
    [primitives, selected, animationMode, playing, keyframeIndex, partCursor],
  );
  const acceptTransform = (index: number, patch: Partial<CustomPrimitive>) => {
    setPlaying(false);
    setSelected(index);
    const part = primitives[index];
    if (animationMode && part.animation?.keyframes && patch.rotation) {
      const frames = [...part.animation.keyframes],
        at = insertionAt(
          frames.map((f) => f.time),
          partCursor,
        );
      if (!at.existing) {
        if (frames.length >= 24) return;
        frames.splice(at.index, 0, { time: at.time, angle: 0 });
      }
      setKeyframeIndex(at.index);
      const axis = { x: 0, y: 1, z: 2 }[part.animation.axis],
        angle = Math.max(
          -3,
          Math.min(
            3,
            ((patch.rotation[axis] - part.rotation[axis]) * Math.PI) / 180,
          ),
        );
      commitPrimitives((items) =>
        items.map((item, i) =>
          i === index
            ? {
                ...item,
                animation: {
                  ...item.animation!,
                  keyframes: frames.map((frame, k) =>
                    k === at.index ? { ...frame, angle } : frame,
                  ),
                },
              }
            : item,
        ),
      );
    } else
      commitPrimitives((items) =>
        items.map((item, i) => (i === index ? { ...item, ...patch } : item)),
      );
  };
  const undo = () => {
    const previous = history.past.at(-1);
    if (!previous) return;
    setHistory((current) => ({
      past: current.past.slice(0, -1),
      present: previous,
      future: [...current.future, current.present],
    }));
    setSelected((index) => Math.min(index, previous.length - 1));
  };
  const redo = () => {
    const next = history.future.at(-1);
    if (!next) return;
    setHistory((current) => ({
      past: [...current.past, current.present],
      present: next,
      future: current.future.slice(0, -1),
    }));
    setSelected((index) => Math.min(index, next.length - 1));
  };
  const duplicatePart = () => {
    if (!active || primitives.length >= 24) return;
    const clone: CustomPrimitive = {
      ...active,
      id: `part-${uid()}`,
      position: [
        active.position[0] + 1,
        active.position[1],
        active.position[2],
      ],
      ...(active.animation ? { animation: { ...active.animation } } : {}),
    };
    commitPrimitives((items) => [...items, clone]);
    setSelected(primitives.length);
  };
  const removePart = (index: number) => {
    if (primitives.length === 0) return;
    commitPrimitives((items) => items.filter((_, i) => i !== index));
    setSelected((current) =>
      Math.max(
        0,
        index < current
          ? current - 1
          : Math.min(current, primitives.length - 2),
      ),
    );
  };
  const mirrorPart = () => {
    if (!active || primitives.length >= 24) return;
    const mirrored = structuredClone(active);
    mirrored.id = `part-${uid()}`;
    mirrored.name = `${active.name || "Teil"} gespiegelt`.slice(0, 40);
    mirrored.position[0] *= -1;
    mirrored.rotation[1] *= -1;
    mirrored.rotation[2] *= -1;
    if (mirrored.animation && mirrored.animation.axis !== "x") {
      mirrored.animation.phase =
        ((mirrored.animation.phase + Math.PI + Math.PI) % (Math.PI * 2)) -
        Math.PI;
      if (mirrored.animation.keyframes)
        mirrored.animation.keyframes = mirrored.animation.keyframes.map(
          (frame) => ({ ...frame, angle: -frame.angle }),
        );
    }
    commitPrimitives((items) => [...items, mirrored]);
    setSelected(primitives.length);
  };
  const importModel = async (file: File) => {
    try {
      if (file.size > 256_000)
        throw new Error("Die Modell-Datei ist zu groß (maximal 256 KB).");
      const imported = parseCustomCosmeticFile(await readJson(file));
      setName(imported.name);
      setSlot(imported.slot);
      setHistory({ past: [], present: imported.primitives, future: [] });
      setSelected(0);
      setFileNotice(`${imported.name} wurde geladen.`);
    } catch (error) {
      setFileNotice(
        error instanceof Error
          ? error.message
          : "Die Modell-Datei konnte nicht gelesen werden.",
      );
    }
  };
  const exportModel = () => {
    const fileName =
      name
        .trim()
        .toLocaleLowerCase("de-DE")
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/^-|-$/g, "") || "mein-modell";
    exportJson(`${fileName}.votex-cosmetic.json`, {
      format: "votex-cosmetic",
      schemaVersion: 1,
      name: name.trim() || "Mein Modell",
      slot,
      primitives,
    });
    setFileNotice("Das geprüfte Votex-Modell wurde exportiert.");
  };
  const updateVector = (
    key: "position" | "size" | "rotation",
    axis: 0 | 1 | 2,
    value: number,
  ) => {
    if (!active) return;
    const next = [...active[key]] as [number, number, number];
    const grid = key === "rotation" ? 15 : 0.5;
    next[axis] = snapToGrid ? Math.round(value / grid) * grid : value;
    updatePart({ [key]: next }, `part:${active.id}:${key}:${axis}`);
  };
  const save = async () => {
    setSaving(true);
    setFileNotice("");
    try {
      await onSave(draft, visibility);
    } catch (error) {
      setFileNotice(
        error instanceof Error
          ? error.message
          : "Das Modell konnte nicht gespeichert werden.",
      );
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal title="Kosmetik-Atelier · eigenes Modell" onClose={onClose}>
      <div className="custom-editor">
        <div className="custom-editor-preview">
          <div className="creator-mode-tabs">
            <button
              className={!animationMode ? "active" : ""}
              onClick={() => {
                setAnimationMode(false);
                setPlaying(false);
              }}
            >
              Modellieren
            </button>
            <button
              className={animationMode ? "active" : ""}
              onClick={() => animatePart()}
            >
              Animieren
            </button>
          </div>
          <CreatorViewport
            skin={skin}
            model={model}
            parts={previewParts}
            selected={selected}
            onSelect={(index) =>
              animationMode ? animatePart(index) : setSelected(index)
            }
            playing={playing}
            rotationOnly={animationMode}
            animationAxis={active?.animation?.axis ?? "z"}
            onPartChange={acceptTransform}
          />
          <div className="creator-playbar">
            <GlassButton onClick={() => setPlaying(!playing)}>
              {playing ? "Vorschau pausieren" : "Animation abspielen"}
            </GlassButton>
            <span>
              {primitives.length} Teile · {vertexCount.toLocaleString("de-DE")}{" "}
              / 12.000 Eckpunkte
            </span>
          </div>
        </div>
        <div className="custom-editor-controls">
          {initialModel?.communityId && (
            <p className="notice">
              Dieses Modell ist bereits geteilt. Deine Änderungen werden als
              private Kopie gespeichert; der Online-Beitrag bleibt unverändert.
            </p>
          )}
          <label className="form-stack">
            Name
            <input
              maxLength={60}
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
          <label className="form-stack">
            Kategorie
            <Select
              value={slot}
              onChange={(e) => setSlot(e.target.value as Slot)}
            >
              {slots.map(([id, label]) => (
                <option key={id} value={id}>
                  {label}
                </option>
              ))}
            </Select>
          </label>
          <div
            className="custom-editor-toolbar"
            aria-label="Modell bearbeiten und übertragen"
          >
            <GlassButton
              disabled={!history.past.length}
              title="Letzte Änderung rückgängig machen"
              onClick={undo}
            >
              <Undo2 size={14} />
              Rückgängig
            </GlassButton>
            <GlassButton
              disabled={!history.future.length}
              title="Änderung wiederherstellen"
              onClick={redo}
            >
              <Redo2 size={14} />
              Wiederholen
            </GlassButton>
            <GlassButton
              disabled={!active || primitives.length >= 24}
              onClick={duplicatePart}
            >
              <Copy size={14} />
              Teil duplizieren
            </GlassButton>
            <GlassButton
              disabled={!active || primitives.length >= 24}
              onClick={mirrorPart}
            >
              An X spiegeln
            </GlassButton>
            <GlassButton onClick={exportModel}>
              <Download size={14} />
              Export
            </GlassButton>
            <GlassButton onClick={() => importInput.current?.click()}>
              <Upload size={14} />
              Import
            </GlassButton>
            <input
              ref={importInput}
              className="custom-import-input"
              type="file"
              accept="application/json,.votex-cosmetic.json"
              aria-label="Votex-Modell importieren"
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) void importModel(file);
                event.target.value = "";
              }}
            />
          </div>
          <Toggle
            label="Rasterfang · 0,5 px / 15°"
            value={snapToGrid}
            onChange={setSnapToGrid}
          />
          <div className="custom-part-list">
            {primitives.map((part, index) => (
              <button
                key={part.id}
                className={selected === index ? "selected" : ""}
                onClick={() =>
                  animationMode ? animatePart(index) : setSelected(index)
                }
              >
                <span>
                  {part.type === "sphere"
                    ? "●"
                    : part.type === "plane"
                      ? "▱"
                      : part.type === "pixel"
                        ? "▦"
                        : "⬚"}
                </span>
                {part.name || `Teil ${index + 1}`} · {part.type}
                <X
                  size={12}
                  onClick={(event) => {
                    event.stopPropagation();
                    removePart(index);
                  }}
                />
              </button>
            ))}
          </div>
          <div className="custom-shapes" aria-label="Form hinzufügen">
            {(["cube", "sphere", "plane", "pixel"] as const).map((type) => (
              <GlassButton
                key={type}
                disabled={primitives.length >= 24}
                onClick={() => {
                  commitPrimitives((items) => [...items, newPrimitive(type)]);
                  setSelected(primitives.length);
                }}
              >
                <Plus size={13} />
                {type === "cube"
                  ? "Würfel"
                  : type === "sphere"
                    ? "Kugel"
                    : type === "plane"
                      ? "Scheibe"
                      : "Pixel"}
              </GlassButton>
            ))}
          </div>
          {active && (
            <>
              <label className="form-stack">
                Teilname
                <input
                  maxLength={40}
                  value={active.name ?? ""}
                  placeholder={`Teil ${selected + 1}`}
                  onChange={(event) => updatePart({ name: event.target.value })}
                />
              </label>
              <label className="form-stack">
                An Körperteil befestigen
                <Select
                  value={active.joint}
                  onChange={(e) =>
                    updatePart({
                      joint: e.target.value as CustomPrimitive["joint"],
                    })
                  }
                >
                  {[
                    ["head", "Kopf"],
                    ["body", "Körper"],
                    ["leftArm", "Linker Arm"],
                    ["rightArm", "Rechter Arm"],
                    ["leftLeg", "Linkes Bein"],
                    ["rightLeg", "Rechtes Bein"],
                  ].map(([id, label]) => (
                    <option key={id} value={id}>
                      {label}
                    </option>
                  ))}
                </Select>
              </label>
              <label className="form-stack">
                Farbe
                <input
                  type="color"
                  value={active.color}
                  onChange={(e) => updatePart({ color: e.target.value })}
                />
              </label>
              <details className="creator-paint-panel">
                <summary>Textur & Farbverlauf</summary>
                <Toggle
                  label="Farbverlauf"
                  value={!!active.gradient}
                  onChange={(enabled) =>
                    updatePart({
                      gradient: enabled
                        ? { color: "#ffffff", axis: "y" }
                        : undefined,
                      paint: undefined,
                    })
                  }
                />
                {active.gradient && (
                  <>
                    <label className="form-stack">
                      Zweite Farbe
                      <input
                        type="color"
                        value={active.gradient.color}
                        onChange={(e) =>
                          updatePart({
                            gradient: {
                              ...active.gradient!,
                              color: e.target.value,
                            },
                          })
                        }
                      />
                    </label>
                    <label className="form-stack">
                      Richtung
                      <Select
                        value={active.gradient.axis}
                        onChange={(e) =>
                          updatePart({
                            gradient: {
                              ...active.gradient!,
                              axis: e.target.value as "x" | "y" | "z",
                            },
                          })
                        }
                      >
                        <option value="x">Links → rechts</option>
                        <option value="y">Oben → unten</option>
                        <option value="z">Vorne → hinten</option>
                      </Select>
                    </label>
                  </>
                )}
                <Toggle
                  label="Pixeltextur auf den Flächen"
                  value={!!active.paint}
                  onChange={(enabled) =>
                    updatePart({
                      paint: enabled ? Array(64).fill(active.color) : undefined,
                    })
                  }
                />
                {active.paint && (
                  <>
                    <label className="form-stack">
                      Pinselfarbe
                      <input
                        type="color"
                        value={paintColor}
                        onChange={(e) => setPaintColor(e.target.value)}
                      />
                    </label>
                    <div
                      className="creator-texture-grid"
                      aria-label="8 mal 8 Pixeltextur"
                    >
                      {active.paint.map((color, index) => (
                        <button
                          key={index}
                          style={{ background: color }}
                          aria-label={`Texturpixel ${(index % 8) + 1}, ${Math.floor(index / 8) + 1}`}
                          onClick={(e) => {
                            if (e.detail === 0)
                              updatePart({
                                paint: active.paint!.map((v, i) =>
                                  i === index ? paintColor : v,
                                ),
                              });
                          }}
                          onPointerDown={(e) => {
                            e.preventDefault();
                            updatePart(
                              {
                                paint: active.paint!.map((v, i) =>
                                  i === index ? paintColor : v,
                                ),
                              },
                              `paint:${active.id}`,
                            );
                          }}
                          onPointerEnter={(e) => {
                            if (e.buttons === 1)
                              updatePart(
                                {
                                  paint: active.paint!.map((v, i) =>
                                    i === index ? paintColor : v,
                                  ),
                                },
                                `paint:${active.id}`,
                              );
                          }}
                        />
                      ))}
                    </div>
                    <GlassButton
                      onClick={() =>
                        updatePart({ paint: Array(64).fill(paintColor) })
                      }
                    >
                      Mit Pinselfarbe füllen
                    </GlassButton>
                    <p className="muted">
                      Male mit gedrückter Maustaste. Das 8×8-Muster wird auf
                      jede Fläche gelegt und ist auch im Spiel sichtbar.
                    </p>
                  </>
                )}
              </details>
              <label className="form-stack">
                Material
                <Select
                  value={active.material ?? "matte"}
                  onChange={(e) =>
                    updatePart({
                      material: e.target.value as NonNullable<
                        CustomPrimitive["material"]
                      >,
                    })
                  }
                >
                  <option value="matte">Matt · weich</option>
                  <option value="polished">Poliert · klar</option>
                  <option value="metal">Metall · reflektierend</option>
                </Select>
              </label>
              {(["position", "size", "rotation"] as const).map((key) => (
                <fieldset className="custom-vector" key={key}>
                  <legend>
                    {key === "position"
                      ? "Position"
                      : key === "size"
                        ? "Größe"
                        : "Drehung"}
                  </legend>
                  {(["X", "Y", "Z"] as const).map((label, axis) => (
                    <RangeField
                      key={label}
                      label={label}
                      min={
                        key === "rotation" ? -180 : key === "size" ? 0.2 : -32
                      }
                      max={key === "rotation" ? 180 : 32}
                      step={key === "rotation" ? 1 : 0.1}
                      unit={key === "rotation" ? "°" : " px"}
                      value={active[key][axis]}
                      onChange={(value) =>
                        updateVector(key, axis as 0 | 1 | 2, value)
                      }
                    />
                  ))}
                </fieldset>
              ))}
              <Toggle
                label="Animation aktiv"
                value={active.animated}
                onChange={(animated) => updatePart({ animated })}
              />
              {active.animated && active.animation && (
                <>
                  <label className="form-stack">
                    Bewegungsachse
                    <Select
                      value={active.animation.axis}
                      onChange={(e) =>
                        updatePart({
                          animation: {
                            ...active.animation!,
                            axis: e.target.value as "x" | "y" | "z",
                          },
                        })
                      }
                    >
                      <option value="x">X · vor/zurück</option>
                      <option value="y">Y · drehen</option>
                      <option value="z">Z · Flügel schlagen</option>
                    </Select>
                  </label>
                  {!active.animation.keyframes ? (
                    <>
                      <RangeField
                        label="Bewegungsweite"
                        min={0.05}
                        max={1.2}
                        step={0.05}
                        unit=" rad"
                        value={active.animation.amplitude}
                        onChange={(amplitude) =>
                          updatePart({
                            animation: { ...active.animation!, amplitude },
                          })
                        }
                      />
                      <RangeField
                        label="Tempo"
                        min={0.1}
                        max={8}
                        step={0.1}
                        unit="×"
                        value={active.animation.speed}
                        onChange={(speed) =>
                          updatePart({
                            animation: { ...active.animation!, speed },
                          })
                        }
                      />
                      <GlassButton onClick={() => animatePart()}>
                        Keyframe-Animation anlegen
                      </GlassButton>
                    </>
                  ) : (
                    <div className="part-keyframe-editor">
                      <RangeField
                        label="Schleifendauer"
                        min={1}
                        max={10}
                        step={0.1}
                        unit=" s"
                        value={active.animation.duration ?? 2}
                        onChange={(duration) =>
                          updatePart({
                            animation: { ...active.animation!, duration },
                          })
                        }
                      />
                      <Timeline
                        times={active.animation.keyframes.map((f) => f.time)}
                        duration={active.animation.duration ?? 2}
                        cursor={partCursor}
                        selected={keyframeIndex}
                        onCursor={(time) => {
                          setPartCursor(time);
                          setPlaying(false);
                        }}
                        onSelect={(index) => {
                          setKeyframeIndex(index);
                          setPartCursor(
                            active.animation!.keyframes![index].time,
                          );
                          setAnimationMode(true);
                          setPlaying(false);
                        }}
                        onMove={(index, time) => {
                          updatePart({
                            animation: {
                              ...active.animation!,
                              keyframes: active.animation!.keyframes!.map(
                                (f, i) => (i === index ? { ...f, time } : f),
                              ),
                            },
                          });
                          setKeyframeIndex(index);
                          setPartCursor(time);
                          setPlaying(false);
                        }}
                        onInsert={(time) => {
                          const frames = active.animation!.keyframes!,
                            at = insertionAt(
                              frames.map((f) => f.time),
                              time,
                            );
                          if(!at.existing && frames.length>=24)return;
                          if (!at.existing && frames.length < 24) {
                            const next = [...frames];
                            next.splice(at.index, 0, {
                              time: at.time,
                              angle: interpolateAt(
                                frames.map((f) => f.time),
                                frames.map((f) => f.angle),
                                at.time,
                              ),
                            });
                            updatePart({
                              animation: {
                                ...active.animation!,
                                keyframes: next,
                              },
                            });
                          }
                          setKeyframeIndex(at.index);
                          setPartCursor(at.time);
                          setAnimationMode(true);
                          setPlaying(false);
                        }}
                      />
                      <RangeField
                        label={`Drehung · Keyframe ${keyframeIndex + 1}`}
                        min={-170}
                        max={170}
                        step={1}
                        unit="°"
                        value={
                          ((active.animation.keyframes[keyframeIndex]?.angle ??
                            0) *
                            180) /
                          Math.PI
                        }
                        onChange={(degrees) => {setPartCursor(active.animation!.keyframes![keyframeIndex].time);setPlaying(false);
                          updatePart({
                            animation: {
                              ...active.animation!,
                              keyframes: active.animation!.keyframes!.map(
                                (frame, index) =>
                                  index === keyframeIndex
                                    ? {
                                        ...frame,
                                        angle: (degrees * Math.PI) / 180,
                                      }
                                    : frame,
                              ),
                            },
                          });}
                        }
                      />
                      <GlassButton
                        disabled={
                          keyframeIndex === 0 ||
                          keyframeIndex >= active.animation.keyframes.length - 1
                        }
                        onClick={() => {
                          updatePart({
                            animation: {
                              ...active.animation!,
                              keyframes: active.animation!.keyframes!.filter(
                                (_, i) => i !== keyframeIndex,
                              ),
                            },
                          });
                          setPartCursor(
                            active.animation!.keyframes![
                              Math.max(0, keyframeIndex - 1)
                            ].time,
                          );
                          setKeyframeIndex(Math.max(0, keyframeIndex - 1));
                        }}
                      >
                        Keyframe entfernen
                      </GlassButton>
                      <p className="muted">
                        Im Modus „Animieren“ zeichnen Drehungen am Achsengriff
                        den gewählten Keyframe auf. Die eingestellte
                        Bewegungsachse wird gespeichert.
                      </p>
                    </div>
                  )}
                </>
              )}
            </>
          )}
          <div className="community-consent glass">
            <CommunityVisibilityField
              value={visibility}
              onChange={setVisibility}
            />
            <p>
              Öffentliche Modelle zeigen deinen Minecraft-Namen. Freigabelinks
              kannst du später erneuern oder widerrufen.
            </p>
          </div>
          {fileNotice && (
            <p className="custom-file-notice" role="status" aria-live="polite">
              {fileNotice}
            </p>
          )}
          {vertexCount > 12000 && (
            <p role="alert">
              Das Modell überschreitet das Budget. Entferne Pixeltexturen oder
              einzelne Teile, bis es unter 12.000 Eckpunkten liegt.
            </p>
          )}
          <div className="toolbar">
            <GlassButton onClick={onClose}>Abbrechen</GlassButton>
            <GlassButton
              className="primary"
              disabled={
                !name.trim() ||
                saving ||
                primitives.length === 0 ||
                vertexCount > 12000
              }
              onClick={() => void save()}
            >
              {saving
                ? "Wird gespeichert …"
                : visibility === "private"
                  ? "Nur lokal speichern"
                  : "Erstellen & teilen"}
            </GlassButton>
          </div>
        </div>
      </div>
    </Modal>
  );
}
