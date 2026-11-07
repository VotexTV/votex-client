import { Select } from "../components/Select";
import { useEffect, useRef, useState } from "react";
import { motion, useReducedMotion } from "motion/react";
import {
  Upload,
  Trash2,
  Check,
  Pencil,
  Copy,
  Download,
  X,
  Search,
  FolderOpen,
} from "lucide-react";
import { SkinPreview } from "../components/SkinPreview";
import { SkinPixelEditor } from "../components/SkinPixelEditor";
import {
  SkinGalleryProvider,
  SkinCardPreview,
  SkinPngDownload,
} from "../components/SkinGallery";
import {
  GlassPanel,
  GlassButton,
  PageHeading,
  Toggle,
  Modal,
  Empty,
} from "../components/Glass";
import { call, native, readData, uid } from "../services/desktop";
import {
  addSkin,
  duplicateSkin,
  importSkin,
  removeSkin,
} from "../services/skins";
import type { PageProps } from "./types";

export function Skins({
  state,
  update,
  act,
  accountSkin,
  accountModel,
}: PageProps) {
  const [expanded, setExpanded] = useState<string | null>(null),
    [name, setName] = useState(""),
    [remove, setRemove] = useState(""),
    [pixelEditing, setPixelEditing] = useState(false);
  const [pixelDraft, setPixelDraft] = useState<string | null>(null);
  const [query, setQuery] = useState(""),
    [importing, setImporting] = useState(false),
    [exporting, setExporting] = useState(false),
    [exportPath, setExportPath] = useState(""),
    [exportNotice, setExportNotice] = useState("");
  const panel = useRef<HTMLElement | null>(null),
    previousExpanded = useRef<string | null>(null),
    reduced = useReducedMotion();
  useEffect(() => {
    if (!expanded) {
      if (previousExpanded.current)
        document
          .querySelector<HTMLElement>(
            `[data-skin-key="${CSS.escape(previousExpanded.current)}"] .skin-card-title`,
          )
          ?.focus({ preventScroll: true });
      return;
    }
    previousExpanded.current = expanded;
    panel.current?.scrollIntoView({
      block: "nearest",
      behavior: reduced ? "instant" : "smooth",
    });
    panel.current
      ?.querySelector<HTMLElement>(
        'button[aria-label="Skin-Bearbeitung schließen"]',
      )
      ?.focus({ preventScroll: true });
  }, [expanded]);
  const entries = [
    {
      key: "account",
      id: "",
      name: accountSkin ? "Minecraft-Account" : "Votex-Standardskin",
      source: accountSkin || "/assets/votex-default-skin.png",
      model: accountModel || ("default" as "default" | "slim"),
    },
    ...state.skins.map((s) => ({
      key: `skin:${s.id}`,
      id: s.id,
      name: s.name,
      source: s.data,
      model: s.model,
    })),
  ];
  const visible = entries.filter((e) =>
    `${e.name} ${e.model === "slim" ? "Schmal Slim" : "Klassisch Classic"}`
      .toLocaleLowerCase("de")
      .includes(query.toLocaleLowerCase("de")),
  );
  const expand = (key: string, skinName: string) => {
    setExpanded(key);
    setName(skinName);
    setPixelEditing(false);
    setPixelDraft(null);
  };
  const sourceData = async (source: string) => {
    if (source.startsWith("data:image/png;base64,")) return source;
    if (source !== "/assets/votex-default-skin.png")
      throw Error("Diese Skin-Quelle kann nicht exportiert werden.");
    const response = await fetch(source);
    if (!response.ok)
      throw Error("Der Votex-Standardskin konnte nicht gelesen werden.");
    return readData(
      new File([await response.blob()], "Votex-Standardskin.png", {
        type: "image/png",
      }),
      1_000_000,
    );
  };
  const exportSkin = (source: string) =>
    void act(async () => {
      setExporting(true);
      setExportPath("");
      setExportNotice("");
      try {
        const data = await sourceData(source);
        setExportPath(await call<string>("save_skin_image", { data }));
        setExportNotice("Skin-PNG wurde gespeichert.");
      } finally {
        setExporting(false);
      }
    });
  return (
    <>
      <PageHeading
        eyebrow="SKINS"
        title="Skin-Bibliothek"
        description="Skins in 3D ansehen, bearbeiten und für deine lokale Votex-Darstellung auswählen."
        action={
          <label
            className={`btn primary file-btn ${importing ? "disabled" : ""}`}
            aria-disabled={importing || state.skins.length >= 100}
          >
            <Upload size={17} />
            {importing ? "Skin wird geprüft …" : "Skin importieren"}
            <input
              type="file"
              accept="image/png,.png"
              aria-label="Skin-PNG importieren"
              disabled={importing || state.skins.length >= 100}
              onChange={(e) => {
                const file = e.target.files?.[0];
                e.currentTarget.value = "";
                if (!file) return;
                setImporting(true);
                void act(async () => {
                  try {
                    const skin = await importSkin(file, uid());
                    if (update((s) => addSkin(s, skin))) {
                          setQuery("");
                      expand(`skin:${skin.id}`, skin.name);
                    }
                  } finally {
                    setImporting(false);
                  }
                });
              }}
            />
          </label>
        }
      />
      <div className="skin-library-tools">
        <label className="search-input">
          <Search size={17} />
          <input
            aria-label="Skins in der Bibliothek suchen"
            placeholder="Name oder Modell suchen …"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </label>
        <span>{state.skins.length} / 100 lokale Skins</span>
        <Toggle
          label="Ruheanimation bei Bedienung"
          value={state.idleSkin}
          onChange={(idleSkin) => update((s) => ({ ...s, idleSkin }))}
        />
      </div>
      <p id="skin-gallery-help" className="muted skin-gallery-help">
        Klicken zum Bearbeiten · Ziehen zum Drehen · Strg + Mausrad zum Zoomen.
        Mit Tastatur: Pfeile drehen, + / − zoomen, Pos1 setzt zurück, Enter
        öffnet die Bearbeitung.
      </p>
      {exportNotice && (
        <div className="notice skin-export-result" role="status">
          <span>{exportNotice}</span>
          {exportPath && (
            <>
              <code>{exportPath}</code>
              <GlassButton
                onClick={() => void act(() => call("open_skin_exports"))}
              >
                <FolderOpen size={15} />
                Exportordner öffnen
              </GlassButton>
            </>
          )}
        </div>
      )}
      <SkinGalleryProvider idle={state.idleSkin}>
        <div className="skin-gallery-grid">
          {visible.map((entry) => {
            const open = expanded === entry.key,
              active = state.selectedSkin === entry.id;
            const previewSource = pixelEditing && entry.id && pixelDraft
              ? pixelDraft
              : entry.source;
            return (
              <motion.article
                layout
                key={entry.key}
                data-skin-key={entry.key}
                ref={open ? panel : undefined}
                className={`glass skin-gallery-card ${open ? "expanded" : ""} ${active ? "selected" : ""}`}
                transition={{
                  duration: reduced ? 0 : 0.22,
                  ease: [0.22, 1, 0.36, 1],
                }}
              >
                <header>
                  <div>
                    <button
                      className="skin-card-title"
                      onClick={() => expand(entry.key, entry.name)}
                    >
                      {entry.name}
                    </button>
                    <small>
                      {entry.id
                        ? entry.model === "slim"
                          ? "Schmal · 3-Pixel-Arme"
                          : "Klassisch · 4-Pixel-Arme"
                        : accountSkin
                          ? "Offizieller Account-Skin"
                          : "Dein mitgelieferter Standardskin"}
                    </small>
                  </div>
                  {active && (
                    <span className="skin-active-badge">
                      <Check size={12} />
                      Aktiv
                    </span>
                  )}
                  {open && (
                    <button
                      className="icon-button"
                      aria-label="Skin-Bearbeitung schließen"
                      onClick={() => { setExpanded(null); setPixelEditing(false); }}
                    >
                      <X size={17} />
                    </button>
                  )}
                </header>
                <div className={open ? "skin-expanded-body" : ""}>
                  {open ? (
                    <div className="skin-expanded-preview">
                      <SkinPreview
                        skin={previewSource}
                        model={entry.model}
                        idle={state.idleSkin}
                      />
                    </div>
                  ) : (
                    <SkinCardPreview
                      id={entry.key}
                      source={entry.source}
                      model={entry.model}
                      name={entry.name}
                      onOpen={() => expand(entry.key, entry.name)}
                    />
                  )}
                  {open && (
                    <div className="skin-edit-panel">
                      {entry.id ? (
                        <>
                          <form
                            className="form-stack"
                            onSubmit={(e) => {
                              e.preventDefault();
                              update((s) => ({
                                ...s,
                                skins: s.skins.map((skin) =>
                                  skin.id === entry.id
                                    ? { ...skin, name: name.trim() }
                                    : skin,
                                ),
                              }));
                            }}
                          >
                            <label htmlFor="skin-edit-name">Name</label>
                            <input
                              id="skin-edit-name"
                              aria-label="Skin-Name"
                              value={name}
                              maxLength={60}
                              onChange={(e) => setName(e.target.value)}
                            />
                            <GlassButton
                              type="submit"
                              disabled={
                                !name.trim() || name.trim() === entry.name
                              }
                            >
                              <Pencil size={14} />
                              Namen speichern
                            </GlassButton>
                          </form>
                          <label className="form-stack">
                            Spielermodell
                            <Select
                              aria-label="Skin-Spielermodell"
                              value={entry.model}
                              onChange={(e) => {
                                const model = e.target.value as
                                  "default" | "slim";
                                update((s) => ({
                                  ...s,
                                  skins: s.skins.map((skin) =>
                                    skin.id === entry.id
                                      ? { ...skin, model }
                                      : skin,
                                  ),
                                }));
                              }}
                            >
                              <option value="default">
                                Klassisch · 4 Pixel breite Arme
                              </option>
                              <option value="slim">
                                Schmal · 3 Pixel breite Arme
                              </option>
                            </Select>
                          </label>
                          <GlassButton
                            className={pixelEditing ? "" : "primary"}
                            onClick={() => {
                              setPixelDraft(null);
                              setPixelEditing((open) => !open);
                            }}
                          >
                            <Pencil size={14} />
                            {pixelEditing ? "Pixel-Editor schließen" : "Skin-Pixel-Editor öffnen"}
                          </GlassButton>
                          {pixelEditing && (
                            <SkinPixelEditor
                              key={entry.id}
                              source={entry.source}
                              onDraft={setPixelDraft}
                              onSave={(data) => {
                                if (update((s) => ({
                                  ...s,
                                  skins: s.skins.map((skin) =>
                                    skin.id === entry.id ? { ...skin, data } : skin,
                                  ),
                                })))
                                  setPixelEditing(false);
                                setPixelDraft(null);
                              }}
                            />
                          )}
                        </>
                      ) : (
                        <p className="muted">
                          {accountSkin
                            ? "Dieser Skin wird automatisch aus deinem Minecraft-Konto geladen. Speichere eine lokale Kopie, um Namen und Modell in Votex zu bearbeiten."
                            : "Dieser Standardskin bleibt als Fallback erhalten. Speichere eine lokale Kopie, wenn du sie separat verwalten möchtest."}
                        </p>
                      )}
                      <p className="notice">
                        Die Auswahl bleibt lokal und ändert deinen offiziellen
                        Minecraft-Account-Skin nicht. Andere Spieler sehen
                        weiterhin deinen Account-Skin.
                      </p>
                    </div>
                  )}
                </div>
                <div className="skin-card-actions">
                  <GlassButton
                    className={active ? "" : "primary"}
                    disabled={active}
                    onClick={() =>
                      update((s) => ({ ...s, selectedSkin: entry.id }))
                    }
                  >
                    <Check size={14} />
                    {active
                      ? "Auf Startseite aktiv"
                      : "Auf Startseite verwenden"}
                  </GlassButton>
                  {!open && (
                    <GlassButton onClick={() => expand(entry.key, entry.name)}>
                      <Pencil size={14} />
                      Bearbeiten
                    </GlassButton>
                  )}
                  <GlassButton
                    disabled={state.skins.length >= 100}
                    onClick={() =>
                      void act(async () => {
                        const id = uid();
                        if (entry.id) {
                          if (update((s) => duplicateSkin(s, entry.id, id)))
                            expand(
                              `skin:${id}`,
                              `${entry.name.slice(0, 52)} (Kopie)`,
                            );
                        } else {
                          const data = await sourceData(entry.source);
                          const copy = {
                            id,
                            name: entry.name,
                            data,
                            model: entry.model,
                          };
                          if (update((s) => addSkin(s, copy, false)))
                            expand(`skin:${id}`, copy.name);
                        }
                      })
                    }
                  >
                    <Copy size={14} />
                    {entry.id ? "Kopieren" : "Lokale Kopie"}
                  </GlassButton>
                  {native ? (
                    <GlassButton
                      disabled={exporting}
                      onClick={() => exportSkin(entry.source)}
                    >
                      <Download size={14} />
                      PNG exportieren
                    </GlassButton>
                  ) : (
                    <SkinPngDownload source={entry.source} name={entry.name}/>
                  )}
                  {entry.id && (
                    <button
                      className="icon-button danger"
                      aria-label={`${entry.name} entfernen`}
                      onClick={() => setRemove(entry.id)}
                    >
                      <Trash2 size={15} />
                    </button>
                  )}
                </div>
              </motion.article>
            );
          })}
        </div>
      </SkinGalleryProvider>
      {!visible.length && (
        <Empty title="Keine passenden Skins gefunden.">
          Ändere den Suchbegriff oder importiere ein Skin-PNG.
        </Empty>
      )}
      {!state.skins.length && (
        <GlassPanel className="skin-library-empty">
          <h3>Deine lokale Bibliothek</h3>
          <p>
            Importiere einen Skin mit 64 × 64 oder 64 × 32 Pixeln oder speichere
            eine lokale Kopie des angezeigten Skins. Dein Account-Skin bleibt
            dabei erhalten.
          </p>
        </GlassPanel>
      )}
      {remove && (
        <Modal title="Lokalen Skin entfernen?" onClose={() => setRemove("")}>
          <p>
            „{state.skins.find((s) => s.id === remove)?.name}“ wird aus deiner
            lokalen Bibliothek entfernt. Verwendete lokale Verweise wechseln zum
            Account- oder Standardskin. Dein Minecraft-Konto bleibt unverändert.
          </p>
          <div className="toolbar">
            <GlassButton onClick={() => setRemove("")}>Abbrechen</GlassButton>
            <GlassButton
              className="danger"
              onClick={() => {
                update((s) => removeSkin(s, remove));
                if (expanded === `skin:${remove}`) {
                  setExpanded(null);
                  setPixelEditing(false);
                }
                setRemove("");
              }}
            >
              Skin entfernen
            </GlassButton>
          </div>
        </Modal>
      )}
    </>
  );
}
