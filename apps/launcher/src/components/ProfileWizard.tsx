import { Select } from "./Select";
import { useEffect, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  Check,
  Search,
  Cloud,
  Gauge,
} from "lucide-react";
import {
  profileSchema,
  type Profile,
} from "../../../../packages/shared-config/schema";
import {
  applyGamePreset,
  starterModDefaults,
} from "../../../../packages/shared-config/performance";
import {
  versionCatalog,
  loadersFor,
  type MinecraftVersion,
  type LoaderChoice,
} from "../services/catalog";
import { Modal, GlassButton, Toggle } from "./Glass";
import { ModTransfer } from "./ModTransfer";
import { RangeField } from "./RangeField";
export function ProfileWizard({
  initial,
  onSave,
  onClose,
  act,
  profiles = [],
  flush,
  onCopied,
  editing = false,
}: {
  initial: Profile;
  profiles?: Profile[];
  flush?: () => Promise<void>;
  onCopied?: (profile: Profile) => Promise<void>;
  editing?: boolean;
  onSave: (p: Profile) => void | Promise<void>;
  onClose: () => void;
  act: <T>(fn: () => Promise<T>) => Promise<T | undefined>;
}) {
  const [p, setP] = useState(initial),
    [step, setStep] = useState(0),
    [versions, setVersions] = useState<MinecraftVersion[]>([]),
    [query, setQuery] = useState(""),
    [snapshots, setSnapshots] = useState(false),
    [latestFirst, setLatestFirst] = useState(true),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(""),
    [loaders, setLoaders] = useState<LoaderChoice[]>([]);
  const [sourceId, setSourceId] = useState("");
  const [review, setReview] = useState(false);
  const source = profiles.find((x) => x.id === sourceId);
  const loadVersions = () => {
    setLoading(true);
    setError("");
    versionCatalog()
      .then(setVersions)
      .catch((e) => setError(String(e)))
      .finally(() => setLoading(false));
  };
  useEffect(loadVersions, []);
  useEffect(() => {
    if (step !== 1 || p.engine === "horizon") return;
    let gone = false;
    setLoading(true);
    setError("");
    loadersFor(p.minecraft)
      .then((v) => {
        if (!gone) setLoaders(v);
      })
      .catch((e) => {
        if (!gone) setError(String(e));
      })
      .finally(() => {
        if (!gone) setLoading(false);
      });
    return () => {
      gone = true;
    };
  }, [step, p.minecraft, p.engine]);
  const filtered = versions.filter(
    (v) =>
      (p.engine === "horizon"
        ? v.id === "26.3"
        : snapshots || v.type === "release") &&
      v.id.toLowerCase().includes(query.toLowerCase()),
  );
  if (!snapshots)
    filtered.sort((a, b) =>
      a.id.localeCompare(b.id, undefined, { numeric: true }),
    );
  if (snapshots)
    filtered.sort((a, b) =>
      (a.releaseTime || "").localeCompare(b.releaseTime || ""),
    );
  if (latestFirst) filtered.reverse();
  const finish = () => {
    if (source && p.engine === "java") setReview(true);
    else void act(async () => onSave(profileSchema.parse(p)));
  };
  if (review && source)
    return (
      <ModTransfer
        source={source}
        target={profileSchema.parse(p)}
        create
        onClose={() => setReview(false)}
        onDone={async () => {
          await onCopied?.(profileSchema.parse(p));
        }}
        flush={flush}
        act={act}
      />
    );
  return (
    <Modal
      title={
        editing
          ? `Profil bearbeiten · ${initial.name}`
          : "Neues Minecraft-Profil"
      }
      onClose={onClose}
    >
      <div className="wizard-body" key={step}>
        <div className="wizard-steps">
          {["Version", "Modloader", "Spielgefühl", "Bereit"].map((s, i) => (
            <span
              key={s}
              className={step === i ? "active" : step > i ? "done" : ""}
            >
              <b>{step > i ? <Check size={12} /> : i + 1}</b>
              {s}
            </span>
          ))}
        </div>
        {error && (
          <p className="notice danger" role="alert">
            {error}
          </p>
        )}
        {editing &&
          (p.minecraft !== initial.minecraft ||
            p.loader !== initial.loader) && (
            <p className="notice">
              Du änderst Version oder Modloader im vorhandenen Spielordner.
              Prüfe danach deine Mods. Für einen getrennten Versionswechsel
              erstelle ein neues Profil und übernimm die passenden Mods aus
              diesem Profil.
            </p>
          )}
        {step === 0 && (
          <>
            <h3>Spiel-Engine wählen</h3>
            <div className="intent-grid">
              <button
                className={`choice-card ${p.engine === "java" ? "selected" : ""}`}
                disabled={editing}
                onClick={() => {
                  setSourceId("");
                  setP({
                    ...p,
                    engine: "java",
                    starterMods: p.starterMods ?? structuredClone(starterModDefaults),
                  });
                }}
              >
                <strong>Java Edition</strong>
                <small>Fabric, Java-Mods, Votex Core und Offline-Modus.</small>
              </button>
              <button
                className={`choice-card ${p.engine === "horizon" ? "selected" : ""}`}
                disabled={editing}
                onClick={() => {
                  setSourceId("");
                  setP({
                    ...p,
                    engine: "horizon",
                    minecraft: "26.3",
                    loader: "vanilla",
                    loaderVersion: "",
                  });
                }}
              >
                <strong>Votex Engine</strong>
                <small>Experimentelle native Alternative; für Minecraft 26.3 mit lokalem Offline-Start.</small>
              </button>
            </div>
            {editing && (
              <p className="muted">
                Die Engine ist fest an dieses Profil gebunden. Für Horizon oder
                Java erstellst du ein eigenes Profil; so bleiben Mods,
                Spielstände und Einstellungen getrennt.
              </p>
            )}
            <h3>Minecraft-Version wählen</h3>
            <p className="muted">
              {p.engine === "horizon"
                ? "Horizon unterstützt aktuell Minecraft 26.3. Dieses Profil bleibt getrennt von Java-Mods und Java-Spielständen. Horizon wird als Nightly-Vorschau bereitgestellt und kann sich häufiger ändern."
                : "Die neuesten Veröffentlichungen zuerst. Suche direkt nach einer Version. Jedes Profil hat einen eigenen Ordner."}
            </p>
            <div className="catalog-tools">
              <label className="search-input">
                <Search size={16} />
                <input
                  aria-label="Version suchen"
                  placeholder="Version suchen …"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                />
              </label>
              <Select
                aria-label="Versionsreihenfolge"
                value={latestFirst ? "new" : "old"}
                onChange={(e) => setLatestFirst(e.target.value === "new")}
              >
                <option value="old">Älteste zuerst</option>
                <option value="new">Neueste zuerst</option>
              </Select>
            </div>
            <label className="check-row">
              <input
                type="checkbox"
                checked={snapshots}
                onChange={(e) => setSnapshots(e.target.checked)}
              />{" "}
              Auch Snapshots und historische Versionen anzeigen
            </label>
            <div className="version-grid">
              {loading ? (
                <p>Katalog wird geladen …</p>
              ) : (
                filtered.map((v) => (
                  <button
                    key={v.id}
                    className={`choice-card ${p.minecraft === v.id ? "selected" : ""}`}
                    onClick={() =>
                      setP({ ...p, minecraft: v.id, loaderVersion: "" })
                    }
                  >
                    <strong>{v.id}</strong>
                    <small>
                      {v.type === "release"
                        ? "Veröffentlichung"
                        : v.type === "snapshot"
                          ? "Entwicklungsversion"
                          : "Historisch"}
                    </small>
                    {p.minecraft === v.id && <Check size={14} />}
                  </button>
                ))
              )}
            </div>
            {!loading && versions.length > 0 && !filtered.length && (
              <p className="notice">
                Keine passende Version gefunden. Prüfe die Suche oder blende
                Snapshots ein.
              </p>
            )}
            {!loading && !versions.length && (
              <GlassButton onClick={loadVersions}>
                Katalog erneut laden
              </GlassButton>
            )}
          </>
        )}
        {step === 1 && p.engine === "horizon" && (
          <>
            <h3>Votex Engine</h3>
            <p className="notice">
              Dieses Profil startet Horizon für Minecraft 26.3. Beim ersten
              Spielen bereitet Votex die Minecraft-Dateien vor. Horizon unterstützt Offline-Spielen
              und unterstützt keine Fabric-, Forge-, Quilt- oder Java-Modloader.
            </p>
          </>
        )}
        {step === 1 && p.engine === "java" && (
          <>
            <h3>Mit oder ohne Mods?</h3>
            <p className="muted">
              Verfügbarkeit für Minecraft {p.minecraft}. Unterschiedliche Loader
              sind nicht beliebig miteinander kombinierbar.
            </p>
            <div className="loader-grid">
              {loading ? (
                <p>Kompatibilität wird geprüft …</p>
              ) : (
                loaders.map((l) => (
                  <button
                    key={l.id}
                    disabled={!l.available}
                    className={`choice-card ${p.loader === l.id ? "selected" : ""}`}
                    onClick={() => {
                      setP({
                        ...p,
                        loader: l.id,
                        loaderVersion:
                          l.id === initial.loader &&
                          p.minecraft === initial.minecraft
                            ? initial.loaderVersion
                            : "",
                        performanceMods:
                          (l.id === "fabric" || l.id === "quilt") &&
                          p.performanceMods,
                        shaderEnabled: l.id === "fabric" && p.shaderEnabled,
                      });
                      setSourceId("");
                    }}
                  >
                    <strong>{l.name}</strong>
                    <small>{l.description}</small>
                    <span className="badge">
                      {l.available
                        ? l.version || "Verfügbar"
                        : l.reason || "Keine passende Version"}
                    </span>
                  </button>
                ))
              )}
            </div>
            <p className="notice">
              Spigot und Paper sind Server-Software. Du kannst ihre Server mit
              einem passenden Vanilla- oder Mod-Client betreten; sie sind kein
              Modloader für dieses Profil.
            </p>
            {(p.loader !== "fabric" || p.minecraft !== "26.2") && (
              <p className="muted">
                Votex Core mit lokaler Kosmetik und Emote-Rad ist derzeit für
                Fabric 26.2 verfügbar. Dieses Profil lässt sich ohne diese
                Core-Funktionen verwalten.
              </p>
            )}
          </>
        )}
        {step === 2 && (
          <>
            <h3>Wie soll sich dein Spiel anfühlen?</h3>
            {p.engine === "horizon" ? (
              <p className="notice">
                Horizon verwendet seine eigene Rust/Vulkan-Engine. Java-
                Performance-Mods, Shader-Mods, Votex Core und Java-Profileinstellungen
                werden in diesem Profil nicht geladen. Die Horizon-Leistung wird
                im Spiel konfiguriert.
              </p>
            ) : (
              <>
            <div className="intent-grid">
              <button
                className={`choice-card ${p.preset === "performance" ? "selected" : ""}`}
                onClick={() =>
                  setP({
                    ...applyGamePreset(p, "performance"),
                  })
                }
              >
                <Gauge size={26} />
                <strong>Performance</strong>
                <small>
                  Schnelle Grafik, geringere Sichtweite und begrenzte Bildrate.
                </small>
              </button>
              <button
                className={`choice-card ${p.preset === "cinematic" ? "selected" : ""}`}
                onClick={() =>
                  setP({
                    ...applyGamePreset(p, "cinematic"),

                    shaderEnabled:
                      p.loader === "fabric" &&
                      (p.presetValues.cinematic?.shaderEnabled ?? true),
                    ...(p.starterMods
                      ? {
                          starterMods: {
                            ...p.starterMods,
                            sodium: true,
                            iris: true,
                          },
                        }
                      : {}),
                  })
                }
              >
                <Cloud size={26} />
                <strong>Quality / Cinematic</strong>
                <small>
                  32 Chunks Sichtweite, fabelhafte Grafik und viel Raum für
                  stimmungsvolle Panoramen.
                </small>
              </button>
            </div>
            <RangeField
              label="Arbeitsspeicher für Minecraft"
              min={1024}
              max={16384}
              step={512}
              value={p.memory}
              unit=" MB"
              onChange={(memory) => setP({ ...p, memory })}
              hint="Lass genug RAM für Windows und andere Programme. Mehr RAM allein bedeutet nicht mehr FPS."
            />
            <Toggle
              label="Performance-Paket · Lithium, FerriteCore, ImmediatelyFast"
              value={p.performanceMods}
              disabled={p.loader !== "fabric" && p.loader !== "quilt"}
              onChange={(performanceMods) => setP({ ...p, performanceMods })}
            />
            <Toggle
              label="Cinematic · Shader herunterladen und aktivieren"
              value={p.shaderEnabled}
              disabled={p.loader !== "fabric"}
              onChange={(shaderEnabled) =>
                setP({
                  ...p,
                  shaderEnabled,
                  ...(shaderEnabled && p.starterMods
                    ? {
                        starterMods: {
                          ...p.starterMods,
                          sodium: true,
                          iris: true,
                        },
                      }
                    : {}),
                })
              }
            />
            {p.starterMods && (
              <>
                <h3>Basis-Mods</h3>
                <p className="muted">
                  Vorausgewählt, einzeln abwählbar. Iris benötigt Sodium;
                  notwendige Bibliotheken werden mitinstalliert.
                </p>
                {(
                  [
                    ["sodium", "Sodium"],
                    ["iris", "Iris Shaders"],
                    ["modmenu", "Mod Menu"],
                    ["zoomify", "Zoomify"],
                  ] as const
                ).map(([key, label]) => (
                  <Toggle
                    key={key}
                    label={label}
                    value={p.starterMods![key]}
                    disabled={p.loader === "vanilla"}
                    onChange={(value) =>
                      setP({
                        ...p,
                        starterMods: {
                          ...p.starterMods!,
                          [key]: value,
                          ...(key === "sodium" && !value
                            ? { iris: false }
                            : key === "iris" && value
                              ? { sodium: true }
                              : {}),
                        },
                        ...((key === "iris" || key === "sodium") && !value
                          ? { shaderEnabled: false }
                          : {}),
                      })
                    }
                  />
                ))}
                <Toggle
                  label="More Mods · SMP-Erweiterungen"
                  value={p.starterMods.more}
                  disabled={p.loader === "vanilla"}
                  onChange={(more) =>
                    setP({ ...p, starterMods: { ...p.starterMods!, more } })
                  }
                />
                {p.starterMods.more &&
                  (
                    [
                      ["xaeros-minimap", "Xaero’s Minimap"],
                      ["xaeros-world-map", "Xaero’s World Map"],
                      ["appleskin", "AppleSkin"],
                      ["3dskinlayers", "3D Skin Layers"],
                      ["voicechat", "Simple Voice Chat"],
                      ["mousetweaks", "Mouse Tweaks"],
                    ] as const
                  ).map(([key, label]) => (
                    <Toggle
                      key={key}
                      label={label}
                      value={p.starterMods!.extras.includes(key)}
                      onChange={(value) =>
                        setP({
                          ...p,
                          starterMods: {
                            ...p.starterMods!,
                            extras: value
                              ? [...p.starterMods!.extras, key]
                              : p.starterMods!.extras.filter((x) => x !== key),
                          },
                        })
                      }
                    />
                  ))}
                {p.shaderEnabled &&
                  (
                    [
                      ["complementary-reimagined", "Complementary Reimagined"],
                      ["bsl-shaders", "BSL Shaders"],
                    ] as const
                  ).map(([key, label]) => (
                    <Toggle
                      key={key}
                      label={label}
                      value={p.starterMods!.shaders.includes(key)}
                      onChange={(value) =>
                        setP({
                          ...p,
                          starterMods: {
                            ...p.starterMods!,
                            iris: true,
                            sodium: true,
                            shaders: value
                              ? [...p.starterMods!.shaders, key]
                              : p.starterMods!.shaders.filter((x) => x !== key),
                          },
                        })
                      }
                    />
                  ))}
              </>
            )}
            <p className="muted">
              Die ausgewählten Pakete werden nach dem Erstellen vorbereitet. Nur
              passende Versionen für Minecraft und Modloader werden installiert.
              Nicht verfügbare Pakete stehen im Download-Bericht.
            </p>
              </>
            )}
          </>
        )}
        {step === 3 && (
          <>
            <h3>Ein Name für dein nächstes Kapitel.</h3>
            <label className="form-stack">
              Profilname
              <input
                aria-label="Profilname"
                maxLength={60}
                value={p.name}
                onChange={(e) => setP({ ...p, name: e.target.value })}
              />
            </label>
            <div className="wizard-review">
              <div>
                <small>ENGINE</small>
                <strong>{p.engine === "horizon" ? "Votex Engine" : "Java Edition"}</strong>
              </div>
              <div>
                <small>VERSION</small>
                <strong>{p.minecraft}</strong>
              </div>
              <div>
                <small>LOADER</small>
                <strong>{p.loader}</strong>
              </div>
              <div>
                <small>SPIELGEFÜHL</small>
                <strong>
                  {p.preset === "cinematic"
                    ? "Cinematic"
                    : p.preset === "performance"
                      ? "Performance"
                      : "Eigene Abstimmung"}
                </strong>
              </div>
              <div>
                <small>ARBEITSSPEICHER</small>
                <strong>{p.memory / 1024} GB</strong>
              </div>
            </div>
            {!editing && (
              <label className="form-stack">
                Mods aus einem anderen Profil übernehmen
                <Select
                  aria-label="Mods beim Erstellen übernehmen"
                  disabled={p.loader === "vanilla" || p.engine === "horizon"}
                  value={sourceId}
                  onChange={(e) => setSourceId(e.target.value)}
                >
                  <option value="">Ohne vorhandene Mods starten</option>
                  {profiles
                    .filter((x) => x.id !== p.id && x.engine === "java")
                    .map((x) => (
                      <option key={x.id} value={x.id}>
                        {x.name} · {x.loader} {x.minecraft}
                      </option>
                    ))}
                </Select>
              </label>
            )}
            {!editing && (
              <p className="muted">
                {source
                  ? "Als Nächstes prüfst du den konkreten Übernahmeplan. Es werden passende Zielversionen gesucht."
                  : "Mods kannst du später direkt am Profil hinzufügen."}
              </p>
            )}
            <p className="muted">
              Deine vorhandenen Welten und Profile bleiben erhalten.
              Einstellungen und optionale Pakete kannst du später unter Leistung
              ändern.
            </p>
          </>
        )}
      </div>
      <div className="wizard-footer">
        <GlassButton disabled={step === 0} onClick={() => setStep(step - 1)}>
          <ArrowLeft size={15} /> Zurück
        </GlassButton>
        <span className="muted">
          {p.minecraft} · {step + 1} / 4
        </span>
        {step === 3 ? (
          <GlassButton
            className="primary"
            disabled={!p.name.trim()}
            onClick={finish}
          >
            <Check size={16} />{" "}
            {editing
              ? "Änderungen speichern"
              : source
                ? "Mods prüfen & fortfahren"
                : "Profil erstellen"}
          </GlassButton>
        ) : (
          <GlassButton
            className="primary"
            disabled={
              loading ||
              !!error ||
              (step === 0 && p.engine === "horizon" && !loading && !versions.some((version) => version.id === "26.3")) ||
              (step === 1 && p.engine === "java" && !loaders.find((l) => l.id === p.loader)?.available)
            }
            onClick={() => setStep(step + 1)}
          >
            Weiter
            <ArrowRight size={15} />
          </GlassButton>
        )}
      </div>
    </Modal>
  );
}
