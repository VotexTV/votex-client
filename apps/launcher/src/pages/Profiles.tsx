import { Select } from "../components/Select";
import {
  useEffect,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
} from "react";
import { createPortal } from "react-dom";
import {
  Plus,
  Copy,
  Trash2,
  Download,
  Upload,
  Check,
  FolderOpen,
  Pencil,
  PackagePlus,
  FolderInput,
  ArrowRight,
  Play,
} from "lucide-react";
import {
  profileSchema,
  type Profile,
} from "../../../../packages/shared-config/schema";
import {
  GlassButton,
  GlassPanel,
  Modal,
  PageHeading,
} from "../components/Glass";
import { ModTransfer } from "../components/ModTransfer";
import { call, native, exportJson, readJson, uid } from "../services/desktop";
import type { PageProps } from "./types";
import { ProfileWizard } from "../components/ProfileWizard";
import {
  newGameProfile,
  rememberGameClass,
  saveGamePresetValues,
} from "../../../../packages/shared-config/performance";
export function Profiles({
  state,
  update,
  act,
  busy,
  refresh,
  manageMods,
  flush,
  reload,
  launchProfile,
}: PageProps) {
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [covers, setCovers] = useState<Record<string, string>>({});
  const [dragId, setDragId] = useState("");
  const [ghostName, setGhostName] = useState("");
  const pointer = useRef<{
    id: number;
    source: string;
    x: number;
    y: number;
    moved: boolean;
  } | null>(null);
  const ghost = useRef<HTMLDivElement>(null),
    lastPoint = useRef({ x: 0, y: 0 }),
    suppressClick = useRef(false);
  const clearDrag = () => {
    pointer.current = null;
    setDragId("");
    setDropId("");
    setGhostName("");
  };
  const targetAt = (x: number, y: number) =>
    document.elementFromPoint(x, y)?.closest<HTMLElement>("[data-profile-id]")
      ?.dataset.profileId;
  const dragStart = (e: ReactPointerEvent<HTMLButtonElement>, p: Profile) => {
    if (busy || e.button !== 0) return;
    pointer.current = {
      id: e.pointerId,
      source: p.id,
      x: e.clientX,
      y: e.clientY,
      moved: false,
    };
    e.currentTarget.setPointerCapture(e.pointerId);
  };
  const dragMove = (e: ReactPointerEvent<HTMLButtonElement>) => {
    const d = pointer.current;
    if (!d || d.id !== e.pointerId) return;
    if (!d.moved && Math.hypot(e.clientX - d.x, e.clientY - d.y) < 7) return;
    e.preventDefault();
    d.moved = true;
    lastPoint.current = { x: e.clientX, y: e.clientY };
    setDragId(d.source);
    setGhostName(state.profiles.find((x) => x.id === d.source)?.name || "Mods");
    if (ghost.current)
      ghost.current.style.transform = `translate3d(${e.clientX + 16}px,${e.clientY + 16}px,0)`;
    const target = targetAt(e.clientX, e.clientY);
    setDropId(target && target !== d.source ? target : "");
  };
  const dragEnd = (e: ReactPointerEvent<HTMLButtonElement>) => {
    const d = pointer.current;
    if (!d) return;
    const target = targetAt(e.clientX, e.clientY);
    if (e.currentTarget.hasPointerCapture(e.pointerId))
      e.currentTarget.releasePointerCapture(e.pointerId);
    clearDrag();
    if (d.moved) {
      suppressClick.current = true;
      window.setTimeout(() => {
        suppressClick.current = false;
      }, 0);
      const source = state.profiles.find((x) => x.id === d.source),
        destination = state.profiles.find((x) => x.id === target);
      if (source && destination && source.id !== destination.id)
        setTransfer({ source, target: destination });
    }
  };
  useEffect(() => {
    const cancel = (e: KeyboardEvent) => {
      if (e.key === "Escape") clearDrag();
    };
    window.addEventListener("keydown", cancel);
    return () => window.removeEventListener("keydown", cancel);
  }, []);

  const [dropId, setDropId] = useState("");
  const [transfer, setTransfer] = useState<{
    source: Profile;
    target: Profile;
  } | null>(null);
  const [copyTarget, setCopyTarget] = useState<Profile | null>(null);
  const [sourceId, setSourceId] = useState("");
  useEffect(() => {
    if (native)
      void act(async () => {
        await flush?.();
        const [nextCounts, nextCovers] = await Promise.all([
          call<Record<string, number>>("profile_mod_counts"),
          call<Record<string, string>>("profile_covers"),
        ]);
        setCounts(nextCounts);
        setCovers(nextCovers);
      });
  }, [state.profiles, refresh]);
  const copied = async () => {
    await reload?.();
    setTransfer(null);
    setCounts(await call("profile_mod_counts"));
  };
  const [editing, setEditing] = useState<Profile | null>(null);
  const [remove, setRemove] = useState<Profile | null>(null);
  const [wizard, setWizard] = useState<Profile | null>(null);
  const add = () =>
    setWizard(
      newGameProfile(
        state.profiles.find((p) => p.id === state.activeProfile) ??
          state.profiles[0],
        uid(),
        state.gamePresetValues,
      ),
    );
  return (
    <>
      <PageHeading
        eyebrow="PROFILE"
        title="Spielprofile"
        description="Minecraft-Version, Mod-Loader, Mods und Einstellungen je Profil."
        action={
          <GlassButton className="primary" disabled={busy} onClick={add}>
            <Plus size={17} /> Neues Profil
          </GlassButton>
        }
      />
      <div className="toolbar">
        <label className="btn file-btn">
          <Upload size={15} /> Einstellungen importieren
          <input
            type="file"
            accept="application/json,.json"
            disabled={busy}
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f)
                void act(async () => {
                  const raw = (await readJson(f)) as {
                    schemaVersion?: number;
                    profile?: unknown;
                  };
                  if (raw.schemaVersion !== 1)
                    throw new Error("Unsupported profile file");
                  const p = profileSchema.parse(raw.profile);
                  update((s) => ({
                    ...s,
                    profiles: [
                      ...s.profiles,
                      {
                        ...p,
                        id: uid(),
                        java: "",
                        name: `${p.name} importiert`.slice(0, 60),
                      },
                    ],
                  }));
                });
              e.currentTarget.value = "";
            }}
          />
        </label>
        <span className="muted">
          Der Export enthält Einstellungen. Welten und Spieldateien bleiben auf
          diesem PC.
        </span>
      </div>
      <div className="profile-grid">
        {state.profiles.map((p, i) => (
          <GlassPanel
            className={`profile-card ${dragId === p.id ? "drag-source" : ""} ${dropId === p.id ? "drop-target" : ""} ${state.activeProfile === p.id ? "selected" : ""}`}
            key={p.id}
            data-profile-id={p.id}
          >
            <div
              className={`profile-art art-${i % 3} ${covers[p.id] ? "has-cover" : ""}`}
              style={
                covers[p.id]
                  ? {
                      backgroundImage: `linear-gradient(90deg, rgba(5, 10, 22, .34), rgba(5, 10, 22, .05)), url(${covers[p.id]})`,
                    }
                  : undefined
              }
              title={
                covers[p.id]
                  ? "Letzter Spielbildschirm, nur lokal gespeichert"
                  : undefined
              }
            >
              <span>{p.name.slice(0, 1).toUpperCase()}</span>
              <span className="tag">{p.engine === "horizon" ? "VOTEX ENGINE" : p.loader.toUpperCase()}</span>
            </div>
            <div className="profile-body">
              <div className="split">
                <h3>{p.name}</h3>
                {state.activeProfile === p.id && (
                  <Check size={18} className="accent" />
                )}
              </div>
              <p>
                {p.engine === "horizon" ? "Votex Engine" : "Minecraft"} {p.minecraft}
                {p.engine === "java" && <> <b>·</b> {p.memory / 1024} GB RAM</>}
              </p>
              <div className="profile-content-actions">
                <GlassButton
                  className="primary profile-launch"
                  disabled={busy || !launchProfile}
                  onClick={() => launchProfile?.(p.id)}
                >
                  <Play size={18} fill="currentColor" /> Dieses Profil starten
                </GlassButton>
                <GlassButton disabled={busy || p.engine === "horizon"} onClick={() => manageMods?.(p.id)}>
                  <PackagePlus size={18} /> Mods verwalten{" "}
                  <ArrowRight size={16} />
                </GlassButton>
                <button
                  className="mod-folder"
                  draggable={false}
                  disabled={busy || p.engine === "horizon"}
                  title={p.engine === "horizon" ? "Horizon lädt keine Java-Mods" : "Mods auf ein anderes Profil ziehen oder Übernahme auswählen"}
                  onPointerDown={(e) => dragStart(e, p)}
                  onPointerMove={dragMove}
                  onPointerUp={dragEnd}
                  onPointerCancel={clearDrag}
                  onDragStart={(e) => e.preventDefault()}
                  onClick={() => {
                    if (suppressClick.current) {
                      suppressClick.current = false;
                      return;
                    }
                    setCopyTarget(p);
                    setSourceId(
                      state.profiles.find((x) => x.id !== p.id)?.id || "",
                    );
                  }}
                >
                  <FolderInput size={25} />
                  <span>
                    <b>
                      {counts[p.id] === undefined
                        ? "Mod-Ordner"
                        : `${counts[p.id]} Mods`}
                    </b>
                    <small>Ziehen zum Kopieren</small>
                  </span>
                </button>
              </div>
              <div className="profile-actions">
                <GlassButton
                  disabled={busy || state.activeProfile === p.id}
                  onClick={() => update((s) => ({ ...s, activeProfile: p.id }))}
                >
                  {state.activeProfile === p.id ? "Ausgewählt" : "Auswählen"}
                </GlassButton>
                <button
                  title="Profil bearbeiten"
                  disabled={busy}
                  className="icon-button"
                  onClick={() => setEditing({ ...p })}
                >
                  <Pencil size={16} />
                </button>
                <button
                  title="Einstellungen kopieren"
                  disabled={busy}
                  className="icon-button"
                  onClick={() =>
                    setEditing({
                      ...p,
                      id: uid(),
                      name: `${p.name} Kopie`.slice(0, 60),
                    })
                  }
                >
                  <Copy size={16} />
                </button>
                <button
                  title="Einstellungen exportieren"
                  className="icon-button"
                  onClick={() =>
                    exportJson("votex-profile.json", {
                      schemaVersion: 1,
                      profile: p,
                    })
                  }
                >
                  <Download size={16} />
                </button>
                <button
                  title="Spielordner öffnen"
                  className="icon-button"
                  onClick={() =>
                    void act(() => call("open_data", { id: p.id }))
                  }
                >
                  <FolderOpen size={16} />
                </button>
                <button
                  title="Profil entfernen"
                  disabled={busy || state.profiles.length === 1}
                  className="icon-button danger"
                  onClick={() => setRemove(p)}
                >
                  <Trash2 size={16} />
                </button>
              </div>
            </div>
          </GlassPanel>
        ))}
      </div>
      {ghostName &&
        createPortal(
          <div
            className="profile-drag-ghost"
            ref={ghost}
            style={{
              transform: `translate3d(${lastPoint.current.x + 16}px,${lastPoint.current.y + 16}px,0)`,
            }}
          >
            <FolderInput size={22} />
            <div>
              <strong>{ghostName}</strong>
              <span>Mods übernehmen · Zielprofil wählen</span>
            </div>
            <ArrowRight size={15} />
          </div>,
          document.body,
        )}
      {wizard && (
        <ProfileWizard
          initial={wizard}
          profiles={state.profiles}
          flush={flush}
          onCopied={async (p) => {
            await reload?.();
            update((s) => ({
              ...s,
              gamePresetValues: rememberGameClass(s.gamePresetValues, p),
            }));
            await flush?.();
            setWizard(null);
            if (native && p.loader !== "vanilla")
              await call("prepare_profile", { id: p.id });
          }}
          act={act}
          onClose={() => setWizard(null)}
          onSave={async (draft) => {
            const p = saveGamePresetValues(draft, {});
            update((s) => ({
              ...s,
              profiles: [...s.profiles, p],
              gamePresetValues: rememberGameClass(s.gamePresetValues, p),
              activeProfile: p.id,
            }));
            setWizard(null);
            await flush?.();
            if (native && p.loader !== "vanilla")
              await call("prepare_profile", { id: p.id });
          }}
        />
      )}
      {transfer && (
        <ModTransfer
          source={transfer.source}
          target={transfer.target}
          flush={flush}
          act={act}
          onClose={() => setTransfer(null)}
          onDone={copied}
        />
      )}
      {copyTarget && (
        <Modal
          title={`Mods für ${copyTarget.name} übernehmen`}
          onClose={() => setCopyTarget(null)}
        >
          <p>
            Wähle das Quellprofil. Du siehst danach passende Updates, benötigte
            Abhängigkeiten und ausgelassene Dateien.
          </p>
          <label className="form-stack">
            Aus diesem Profil
            <Select
              aria-label="Quellprofil"
              value={sourceId}
              onChange={(e) => setSourceId(e.target.value)}
            >
              {state.profiles
                .filter((x) => x.id !== copyTarget.id)
                .map((x) => (
                  <option key={x.id} value={x.id}>
                    {x.name} · {x.loader} {x.minecraft}
                  </option>
                ))}
            </Select>
          </label>
          <div className="wizard-footer">
            <GlassButton onClick={() => setCopyTarget(null)}>
              Abbrechen
            </GlassButton>
            <GlassButton
              className="primary"
              disabled={!sourceId}
              onClick={() => {
                const source = state.profiles.find((x) => x.id === sourceId);
                if (source) {
                  setTransfer({ source, target: copyTarget });
                  setCopyTarget(null);
                }
              }}
            >
              Übernahme prüfen <ArrowRight size={16} />
            </GlassButton>
          </div>
        </Modal>
      )}
      {editing && (
        <ProfileWizard
          initial={editing}
          editing={state.profiles.some((p) => p.id === editing.id)}
          profiles={state.profiles}
          act={act}
          flush={flush}
          onClose={() => setEditing(null)}
          onCopied={async () => {
            await reload?.();
            setEditing(null);
          }}
          onSave={(draft) => {
            const p = saveGamePresetValues(draft, {});
            update((s) => ({
              ...s,
              gamePresetValues: rememberGameClass(s.gamePresetValues, p),
              profiles: s.profiles.some((x) => x.id === p.id)
                ? s.profiles.map((x) => (x.id === p.id ? p : x))
                : [...s.profiles, p],
            }));
            setEditing(null);
          }}
        />
      )}
      {remove && (
        <Modal
          title="Profil aus der Liste entfernen?"
          onClose={() => setRemove(null)}
        >
          <p>
            „{remove.name}“ aus Votex entfernen? Spielordner, Welten, Mods und
            Sicherungspunkte bleiben auf dem Datenträger erhalten.
          </p>
          <div className="toolbar">
            <GlassButton onClick={() => setRemove(null)}>
              Profil behalten
            </GlassButton>
            <GlassButton
              className="danger"
              onClick={() => {
                update((s) => ({
                  ...s,
                  profiles: s.profiles.filter((p) => p.id !== remove.id),
                  activeProfile:
                    s.activeProfile === remove.id
                      ? s.profiles.find((p) => p.id !== remove.id)!.id
                      : s.activeProfile,
                }));
                setRemove(null);
              }}
            >
              Profil entfernen
            </GlassButton>
          </div>
        </Modal>
      )}
    </>
  );
}
