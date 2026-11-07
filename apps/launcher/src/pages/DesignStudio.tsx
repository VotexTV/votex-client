import { Select } from "../components/Select";
import { useState } from "react";
import {
  Palette,
  Copy,
  Download,
  RotateCcw,
  Trash2,
  Upload,
  Check,
  Sparkles,
} from "lucide-react";
import {
  baseTheme,
  presets,
  themeSchema,
  type Theme,
} from "../../../../packages/shared-config/schema";
import {
  visualProfiles,
  selectVisualProfile,
  resetVisualProfile,
} from "../../../../packages/shared-config/visuals";
import {
  GlassButton,
  GlassPanel,
  PageHeading,
  Toggle,
  Modal,
} from "../components/Glass";
import { RangeField } from "../components/RangeField";
import { InterfaceScale } from "../components/InterfaceScale";
import {
  exportJson,
  readJson,
  readData,
  readVideoData,
  native,
  uid,
} from "../services/desktop";
import type { PageProps } from "./types";
const groups = [
  {
    title: "Glas & Licht",
    hint: "Bestimme, wie weich, durchsichtig und leuchtend deine Oberfläche wirkt.",
    fields: [
      ["transparency", "Deckkraft"],
      ["blur", "Hintergrund weichzeichnen"],
      ["saturation", "Farbsättigung im Glas"],
      ["reflection", "Reflexionen"],
      ["glow", "Kantenlicht"],
      ["shadow", "Schatten"],
    ],
  },
  {
    title: "Bewegung & Nähe",
    hint: "Näherungslicht reagiert auf die Maus. Bei reduzierter Bewegung bleiben die Karten ruhig.",
    fields: [
      ["proximity", "Reichweite der Näherung"],
      ["lift", "Anheben der Karten"],
      ["tilt", "Räumliche Neigung"],
      ["wobble", "Federnder Druckeffekt"],
      ["speed", "Übergangsgeschwindigkeit"],
      ["stiffness", "Federhärte"],
      ["damping", "Beruhigung der Feder"],
    ],
  },
] as const;
export function DesignStudio({ state, update, act }: PageProps) {
  const [target, setTarget] = useState<"launcher" | "game">("launcher");
  const [mode, setMode] = useState<"" | "rename" | "delete">("");
  const [name, setName] = useState("");
  const id =
    target === "game" && !state.syncDesign ? state.gameThemeId : state.themeId;
  const theme = state.themes.find((t) => t.id === id)!;
  const replace = (fn: (t: Theme) => Theme) =>
    update((s) => ({
      ...s,
      themes: s.themes.map((t) => (t.id === id ? fn(t) : t)),
    }));
  const set = (patch: Partial<Theme>) => replace((t) => ({ ...t, ...patch }));
  const select = (value: string) =>
    update((s) => ({
      ...s,
      ...(target === "game" && !s.syncDesign
        ? { gameThemeId: value }
        : { themeId: value }),
    }));
  const add = (t: Theme) =>
    update((s) => ({
      ...s,
      themes: [...s.themes, t],
      ...(target === "game" && !s.syncDesign
        ? { gameThemeId: t.id }
        : { themeId: t.id }),
    }));
  const current = visualProfiles.find((p) => p.id === theme.renderProfile)!;
  return (
    <>
      <PageHeading
        eyebrow="OBERFLÄCHE"
        title="Design Studio"
        description="Farben, Hintergründe, Bewegung und Darstellung anpassen."
        action={
          <span className="pill">
            <i className="status-dot" /> Änderungen werden direkt gespeichert
          </span>
        }
      />
      <div className="section-title">
        <h3>
          <Sparkles size={17} /> Wie viel Design darf es sein?
        </h3>
        <span className="muted">
          Farben und Layout bleiben beim Wechsel erhalten.
        </span>
      </div>
      <div className="visual-profile-grid">
        {visualProfiles.map((p, i) => (
          <button
            key={p.id}
            className={`glass quality-card luminous-card ${theme.renderProfile === p.id ? "selected" : ""}`}
            onClick={() => replace((t) => selectVisualProfile(t, p.id))}
            aria-pressed={theme.renderProfile === p.id}
          >
            <span className="quality-bars">
              {[0, 1, 2, 3].map((n) => (
                <i key={n} className={n <= i ? "lit" : ""} />
              ))}
            </span>
            <strong>{p.name}</strong>
            <span>{p.hint}</span>
            <small>{p.detail}</small>
            {theme.renderProfile === p.id && <Check size={17} />}
          </button>
        ))}
      </div>
      <div className="quality-status">
        <span>
          <i className="status-dot" /> {current.activity}
        </span>
        <GlassButton onClick={() => replace(resetVisualProfile)}>
          <RotateCcw size={14} /> Diese Designstufe zurücksetzen
        </GlassButton>
      </div>
      <GlassPanel className="control-panel quick-effects">
        <div className="section-title">
          <h3>Effekte schnell schalten</h3>
          <span className="muted">Wird für „{current.name}“ gemerkt</span>
        </div>
        <div className="quick-effect-grid">
          <Toggle label="Liquid Glass" value={theme.glassEffect} onChange={(glassEffect) => set({ glassEffect })} />
          <Toggle label="Licht an der Maus" value={theme.proximityEffect} onChange={(proximityEffect) => set({ proximityEffect })} />
          <Toggle label="Leuchtende Kanten" value={theme.glowEffect} onChange={(glowEffect) => set({ glowEffect })} />
          <Toggle label="Glasreflexionen" value={theme.reflectionEffect} onChange={(reflectionEffect) => set({ reflectionEffect })} />
          <Toggle label="Federnder Knopfdruck" value={theme.wobbleEffect} onChange={(wobbleEffect) => set({ wobbleEffect })} />
          <Toggle label="Bewegte Atmosphäre" value={theme.ambientMotion} onChange={(ambientMotion) => set({ ambientMotion })} />
        </div>
      </GlassPanel>
      <div className="studio-layout">
        <div>
          <GlassPanel className="studio-themes">
            <div className="section-title">
              <h3>
                <Palette size={17} /> Farbwelten
              </h3>
              <div className="segmented">
                <button
                  className={target === "launcher" ? "active" : ""}
                  onClick={() => setTarget("launcher")}
                >
                  Launcher
                </button>
                <button
                  className={target === "game" ? "active" : ""}
                  onClick={() => setTarget("game")}
                >
                  Im Spiel
                </button>
              </div>
            </div>
            <div className="theme-grid">
              {state.themes.map((t) => (
                <button
                  key={t.id}
                  className={`theme-swatch ${id === t.id ? "active" : ""}`}
                  onClick={() => select(t.id)}
                >
                  <span
                    style={{
                      background: `linear-gradient(135deg,${t.background},${t.tint} 50%,${t.accent})`,
                    }}
                  >
                    {id === t.id && <Check size={18} />}
                  </span>
                  <small>{t.name}</small>
                </button>
              ))}
            </div>
            <div className="toolbar">
              <GlassButton
                onClick={() =>
                  add({
                    ...theme,
                    id: uid(),
                    name: `${theme.name} Kopie`.slice(0, 60),
                  })
                }
              >
                <Copy size={14} /> Kopieren
              </GlassButton>
              <GlassButton
                onClick={() => {
                  setName(theme.name);
                  setMode("rename");
                }}
              >
                Umbenennen
              </GlassButton>
              <button
                className="icon-button"
                title="Farbwelt exportieren"
                onClick={() => exportJson("votex-theme.json", theme)}
              >
                <Download size={17} />
              </button>
              <label
                className="icon-button file-btn"
                title="Farbwelt importieren"
              >
                <Upload size={17} />
                <input
                  type="file"
                  accept=".json"
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f)
                      void act(async () =>
                        add({
                          ...themeSchema.parse(await readJson(f)),
                          id: uid(),
                        }),
                      );
                    e.currentTarget.value = "";
                  }}
                />
              </label>
              <button
                className="icon-button danger"
                title="Eigene Farbwelt löschen"
                disabled={presets.some((t) => t.id === id)}
                onClick={() => setMode("delete")}
              >
                <Trash2 size={16} />
              </button>
            </div>
            <Toggle
              label="Launcher-Design mit Votex Core synchronisieren"
              value={state.syncDesign}
              onChange={(syncDesign) => update((s) => ({ ...s, syncDesign }))}
            />
            {target === "game" && (
              <p className="muted">
                Das Ingame-Menü übernimmt auch Hover, Mauslicht, Leuchten,
                Reflexionen und die Bewegungsschalter. Seine Klickflächen bleiben
                dabei fest; reduzierte Bewegung gilt in beiden Oberflächen.
              </p>
            )}
          </GlassPanel>
          {groups.map((group) => (
            <GlassPanel className="control-panel" key={group.title}>
              <details className="control-disclosure">
                <summary>
                  <h3>{group.title}</h3>
                  <span>Fein einstellen</span>
                </summary>
                <p className="muted">{group.hint}</p>
                <div className="slider-grid">
                  {group.fields.map(([key, label]) => (
                    <RangeField
                      key={key}
                      label={label}
                      value={theme[key]}
                      onChange={(v) => set({ [key]: v })}
                    />
                  ))}
                </div>
                {group.title === "Bewegung & Nähe" && (
                  <>
                    <Toggle
                      label="Hover- und Näherungseffekte"
                      value={theme.hover}
                      onChange={(hover) => set({ hover })}
                    />
                    <Toggle
                      label="Bewegung reduzieren"
                      value={theme.reducedMotion}
                      onChange={(reducedMotion) => set({ reducedMotion })}
                    />
                  </>
                )}
              </details>
            </GlassPanel>
          ))}
          <GlassPanel className="control-panel">
            <details className="control-disclosure">
              <summary>
                <h3>Größe & Anordnung</h3>
                <span>Persönlich anpassen</span>
              </summary>
              <div className="slider-grid">
                <RangeField
                  label="Rundungen"
                  value={theme.radius}
                  onChange={(radius) => set({ radius })}
                />
                <InterfaceScale
                  key={`${target}:${id}`}
                  value={theme.scale}
                  onApply={(scale) => set({ scale })}
                />
                <RangeField
                  label="Seitenleiste"
                  value={theme.sidebar}
                  min={160}
                  max={260}
                  unit=" px"
                  onChange={(sidebar) => set({ sidebar })}
                />
                <RangeField
                  label="Abstände"
                  value={theme.spacing}
                  min={8}
                  max={32}
                  unit=" px"
                  onChange={(spacing) => set({ spacing })}
                />
              </div>
              <Toggle
                label="Kompakte Anordnung"
                value={theme.compact}
                onChange={(compact) => set({ compact })}
              />
            </details>
          </GlassPanel>
        </div>
        <div className="studio-preview">
          <GlassPanel className="mini-preview">
            <span className="eyebrow">DEINE VORSCHAU</span>
            <h2>{theme.name}</h2>
            <div
              className="preview-orb"
              style={{
                background: `radial-gradient(circle at 30% 20%,${theme.secondary},${theme.accent} 50%,${theme.background})`,
              }}
            />
            <GlassButton className="primary" onClick={() => {}}>
              <Sparkles size={16} /> Federgefühl ausprobieren
            </GlassButton>
            <p className="muted">
              {current.name} · Deine Anpassungen bleiben für diese Stufe
              gespeichert.
            </p>
          </GlassPanel>
          <GlassPanel className="control-panel">
            <h3>Atmosphäre</h3>
            <Select
              aria-label="Hintergrund"
              value={theme.backgroundMode}
              onChange={(e) =>
                set({
                  backgroundMode: e.target.value as Theme["backgroundMode"],
                })
              }
            >
              <option value="clouds">Cloud Nine · Wolkenmeer</option>
              <option value="landscape">Votex Berglandschaft</option>
              <option value="gradient">Aurora-Farbverlauf</option>
              <option value="solid">Einfarbig</option>
              <option value="image">Eigenes Bild</option>
              <option value="video" disabled={!theme.backgroundVideo}>
                Eigenes Video
              </option>
            </Select>
            <RangeField
              label="Helligkeit"
              value={theme.brightness}
              onChange={(brightness) => set({ brightness })}
            />
            <RangeField
              label="Weichzeichnung"
              value={theme.backgroundBlur}
              min={0}
              max={24}
              unit=" px"
              onChange={(backgroundBlur) => set({ backgroundBlur })}
            />
            <RangeField
              label="Horizontale Position"
              value={theme.backgroundPositionX}
              onChange={(backgroundPositionX) => set({ backgroundPositionX })}
            />
            <RangeField
              label="Vertikale Position"
              value={theme.backgroundPositionY}
              onChange={(backgroundPositionY) => set({ backgroundPositionY })}
            />
            <RangeField
              label="Bildzoom"
              value={theme.backgroundZoom}
              min={100}
              max={160}
              unit="%"
              onChange={(backgroundZoom) => set({ backgroundZoom })}
            />
            <div className="background-file-actions">
              <label className="btn file-btn">
                <Upload size={15} /> Bild wählen
                <input
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f)
                      void act(async () => {
                        if (
                          !["image/png", "image/jpeg", "image/webp"].includes(
                            f.type,
                          )
                        )
                          throw Error("Bitte PNG, JPEG oder WebP wählen.");
                        set({
                          backgroundImage: await readData(f),
                          backgroundMode: "image",
                        });
                      });
                    e.currentTarget.value = "";
                  }}
                />
              </label>
              <label className={`btn file-btn ${native ? "" : "disabled"}`}>
                <Upload size={15} /> Video wählen
                <input
                  type="file"
                  accept="video/mp4,video/webm"
                  disabled={!native}
                  title={
                    native
                      ? "MP4 oder WebM bis 5 MB"
                      : "Videohintergründe gibt es in der Windows-App."
                  }
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f)
                      void act(async () => {
                        if (!native)
                          throw Error(
                            "Videohintergründe sind nur in der Windows-App verfügbar.",
                          );
                        const backgroundVideo = await readVideoData(f);
                        const next = {
                          ...state,
                          themes: state.themes.map((t) =>
                            t.id === id
                              ? { ...t, backgroundMode: "video" as const, backgroundVideo }
                              : t,
                          ),
                        };
                        if (
                          new TextEncoder().encode(JSON.stringify(next)).byteLength >
                          7_500_000
                        )
                          throw Error(
                            "Das Video passt nicht mehr sicher in die Konfiguration. Entferne zuerst ein anderes großes Bild oder Video.",
                          );
                        update(() => next);
                      });
                    e.currentTarget.value = "";
                  }}
                />
              </label>
              <GlassButton
                onClick={() =>
                  set({
                    backgroundMode: "landscape",
                    backgroundImage: "",
                    backgroundVideo: "",
                    backgroundBlur: 0,
                    backgroundPositionX: 50,
                    backgroundPositionY: 50,
                    backgroundZoom: 100,
                  })
                }
              >
                <RotateCcw size={14} /> Hintergrund zurücksetzen
              </GlassButton>
            </div>
            {!native && (
              <small>
                MP4-/WebM-Hintergründe werden direkt in der Windows-App gespeichert.
              </small>
            )}
            <small>
              Videos bis 5 MB. Sie laufen nur auf der Startseite und pausieren,
              wenn Votex minimiert oder reduzierte Bewegung aktiviert ist.
            </small>
          </GlassPanel>
          <GlassPanel className="control-panel">
            <details className="control-disclosure">
              <summary>
                <h3>Eigene Farben</h3>
                <span>6 Farbtöne</span>
              </summary>
              {(
                [
                  ["accent", "Akzent"],
                  ["secondary", "Zweiter Akzent"],
                  ["tint", "Glasfarbe"],
                  ["background", "Hintergrund"],
                  ["text", "Text"],
                  ["muted", "Dezenter Text"],
                ] as const
              ).map(([key, label]) => (
                <label className="color-field" key={key}>
                  <span>{label}</span>
                  <code>{theme[key]}</code>
                  <input
                    type="color"
                    value={theme[key]}
                    onChange={(e) => set({ [key]: e.target.value })}
                  />
                </label>
              ))}
            </details>
          </GlassPanel>
          <GlassButton
            onClick={() =>
              set({
                ...(presets.find((t) => t.id === id) ?? baseTheme),
                id,
                name: theme.name,
              })
            }
          >
            <RotateCcw size={15} /> Ganze Farbwelt zurücksetzen
          </GlassButton>
        </div>
      </div>
      {mode && (
        <Modal
          title={
            mode === "delete" ? "Farbwelt entfernen?" : "Farbwelt benennen"
          }
          onClose={() => setMode("")}
        >
          {mode === "delete" ? (
            <>
              <p>
                „{theme.name}“ entfernen? Über den Export kannst du vorher eine
                Kopie behalten.
              </p>
              <GlassButton
                className="danger"
                onClick={() => {
                  update((s) => ({
                    ...s,
                    themes: s.themes.filter((t) => t.id !== id),
                    themeId: s.themeId === id ? "votex" : s.themeId,
                    gameThemeId: s.gameThemeId === id ? "votex" : s.gameThemeId,
                  }));
                  setMode("");
                }}
              >
                Entfernen
              </GlassButton>
            </>
          ) : (
            <>
              <input
                aria-label="Name der Farbwelt"
                maxLength={60}
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
              <GlassButton
                className="primary"
                disabled={!name.trim()}
                onClick={() => {
                  set({ name: name.trim() });
                  setMode("");
                }}
              >
                Speichern
              </GlassButton>
            </>
          )}
        </Modal>
      )}
    </>
  );
}
