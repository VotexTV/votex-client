import { Select } from "../components/Select";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  Search,
  Download,
  Package,
  Trash2,
  RefreshCw,
  ShieldCheck,
  Upload,
  KeyRound,
  ExternalLink,
  ArrowLeft,
  Plus,
  FolderOpen,
  Camera,
} from "lucide-react";
import {
  GlassPanel,
  GlassButton,
  PageHeading,
  Empty,
  Toggle,
  Modal,
} from "../components/Glass";
import {
  call,
  native,
  readData,
  type Hit,
  type Mod,
} from "../services/desktop";
import type { PageProps } from "./types";
import { MessageText } from "../components/MessageText";
import { errorText } from "../services/messages";
type Kind = "mod" | "modpack" | "shader" | "resourcepack";
interface Results {
  hits: Hit[];
  total: number;
  offset: number;
}
export function Mods({
  state,
  act,
  busy,
  refresh,
  reload: reloadState,
  flush,
  profileId,
  onTarget,
  onBack,
}: PageProps & {
  profileId?: string;
  onTarget?: (id: string) => void;
  onBack?: () => void;
}) {
  const [tab, setTab] = useState(profileId ? "installed" : "browse"),
    [provider, setProvider] = useState("modrinth"),
    [kind, setKind] = useState<Kind>("mod"),
    [query, setQuery] = useState(""),
    [sort, setSort] = useState("downloads"),
    [category, setCategory] = useState(""),
    [hits, setHits] = useState<Hit[]>([]),
    [mods, setMods] = useState<Mod[]>([]),
    [loading, setLoading] = useState(false),
    [total, setTotal] = useState(0),
    [error, setError] = useState(""),
    [searchTick, setSearchTick] = useState(0),
    [remove, setRemove] = useState<Mod | null>(null),
    [detail, setDetail] = useState<Hit | null>(null),
    [sources, setSources] = useState(false),
    [key, setKey] = useState(""),
    [configured, setConfigured] = useState(false),
    [keyMessage, setKeyMessage] = useState("");
  const p = state.profiles.find(
    (p) => p.id === (profileId || state.activeProfile),
  )!;
  const sentinel = useRef<HTMLDivElement>(null),
    generation = useRef(0),
    next = useRef(0),
    end = useRef(false),
    inFlight = useRef<number | null>(null);
  const reloadMods = useCallback(
    async () => setMods(await call<Mod[]>("installed_mods", { id: p.id })),
    [p.id],
  );
  useEffect(() => {
    setMods([]);
    if (native) {
      void act(reloadMods);
      void act(async () =>
        setConfigured(await call<boolean>("curseforge_status")),
      );
    }
  }, [p.id, refresh]);
  const loadPage = useCallback(
    async (gen: number) => {
      if (inFlight.current === gen || end.current || generation.current !== gen)
        return;
      inFlight.current = gen;
      setLoading(true);
      setError("");
      const offset = next.current;
      try {
        let r: Results;
        if (native)
          r = await call("search_catalog", {
            id: p.id,
            provider,
            kind,
            query,
            offset,
            sort,
            category: provider === "modrinth" ? category : "",
          });
        else {
          if (provider !== "modrinth")
            throw Error("CurseForge wird in der Windows-App sicher verbunden.");
          const facets = [
            [`project_type:${kind}`],
            [`versions:${p.minecraft}`],
          ];
          if (p.loader !== "vanilla" && (kind === "mod" || kind === "modpack"))
            facets.push([`categories:${p.loader}`]);
          if (category) facets.push([`categories:${category}`]);
          const params = new URLSearchParams({
            query,
            facets: JSON.stringify(facets),
            offset: String(offset),
            limit: "30",
            index: sort,
          });
          const response = await fetch(
            `https://api.modrinth.com/v2/search?${params}`,
          );
          if (!response.ok)
            throw Error(`Modrinth-Suche: HTTP ${response.status}`);
          const data = await response.json();
          r = { hits: data.hits, total: data.total_hits, offset };
        }
        if (generation.current !== gen) return;
        next.current = offset + r.hits.length;
        end.current = !r.hits.length || next.current >= r.total;
        setTotal(r.total);
        setHits((old) => {
          const known = new Set(old.map((h) => h.project_id));
          return [...old, ...r.hits.filter((h) => !known.has(h.project_id))];
        });
      } catch (e) {
        if (generation.current === gen) {
          end.current = true;
          setError(errorText(e));
        }
      } finally {
        if (generation.current === gen) {
          inFlight.current = null;
          setLoading(false);
        }
      }
    },
    [p.id, p.minecraft, p.loader, provider, kind, query, sort, category],
  );
  useEffect(() => {
    const gen = ++generation.current;
    next.current = 0;
    end.current = false;
    inFlight.current = null;
    setHits([]);
    setTotal(0);
    setError("");
    setLoading(true);
    setDetail(null);
    const timer = setTimeout(() => void loadPage(gen), 350);
    return () => {
      clearTimeout(timer);
      generation.current++;
    };
  }, [loadPage, searchTick]);
  useEffect(() => {
    const el = sentinel.current;
    if (!el || tab !== "browse") return;
    const observer = new IntersectionObserver(
      ([e]) => {
        if (e.isIntersecting && !loading && !error && hits.length)
          void loadPage(generation.current);
      },
      { root: document.querySelector(".page-content"), rootMargin: "350px" },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [loadPage, loading, hits.length, error, tab]);
  const install = (hit: Hit) =>
    void act(async () => {
      await flush?.();
      await call(
        "install_content",
        {
          id: p.id,
          provider,
          kind,
          project: hit.project_id,
        },
        `${hit.title} installieren`,
      );
      if (kind === "modpack") await reloadState?.();
      await reloadMods();
      setDetail(null);
    });
  const sourceURL = (hit: Hit) =>
    provider === "modrinth"
      ? `https://modrinth.com/${kind}/${encodeURIComponent(hit.slug)}`
      : `https://www.curseforge.com/minecraft/${kind === "mod" ? "mc-mods" : kind === "modpack" ? "modpacks" : kind === "shader" ? "shaders" : "texture-packs"}/${encodeURIComponent(hit.slug)}`;
  return (
    <>
      <PageHeading
        eyebrow="PROFILINHALTE"
        title={p.name}
        description={`Inhalte dieses Profils · ${p.loader} · Minecraft ${p.minecraft}. Installationen landen in diesem Spielordner.`}
        action={
          <span className="pill">
            <ShieldCheck size={15} /> Downloads mit Prüfsumme
          </span>
        }
      />
      <GlassPanel className="profile-target">
        <GlassButton onClick={onBack}>
          <ArrowLeft size={16} /> Profile
        </GlassButton>
        <label>
          Zielprofil
          <Select
            aria-label="Zielprofil für Installationen"
            value={p.id}
            disabled={busy}
            onChange={(e) => onTarget?.(e.target.value)}
          >
            {state.profiles.map((x) => (
              <option key={x.id} value={x.id}>
                {x.name} · {x.loader} {x.minecraft}
              </option>
            ))}
          </Select>
        </label>
        <span className="muted">
          {state.activeProfile === p.id
            ? "Auch auf der Startseite ausgewählt"
            : "Die Startseite behält ihr ausgewähltes Profil."}
        </span>
        <GlassButton
          className="primary"
          disabled={busy}
          onClick={() => {
            setKind("mod");
            setTab("browse");
          }}
        >
          <Plus size={16} /> Mods hinzufügen
        </GlassButton>
      </GlassPanel>
      <div className="content-shortcuts">
        {[
          { folder: "mods", name: "Mod-Ordner", icon: FolderOpen },
          { folder: "shaderpacks", name: "Shader", icon: FolderOpen },
          { folder: "resourcepacks", name: "Ressourcen", icon: FolderOpen },
          { folder: "screenshots", name: "Screenshots", icon: Camera },
        ].map((x) => {
          const Icon = x.icon;
          return (
            <button
              key={x.folder}
              className="btn"
              disabled={!native}
              onClick={() =>
                void act(() =>
                  call("open_content_folder", { id: p.id, folder: x.folder }),
                )
              }
            >
              <Icon size={13} />
              {x.name}
            </button>
          );
        })}
        <span>Eigene Dateien dieses Profils</span>
      </div>
      <div className="toolbar spread">
        <div className="segmented">
          <button
            className={tab === "browse" ? "active" : ""}
            onClick={() => setTab("browse")}
          >
            Entdecken
          </button>
          <button
            className={tab === "installed" ? "active" : ""}
            onClick={() => setTab("installed")}
          >
            Installierte Mods · {mods.length}
          </button>
        </div>
        <div className="toolbar">
          <GlassButton onClick={() => setSources(true)}>
            <KeyRound size={14} /> Quellen
          </GlassButton>
          <label className="btn file-btn">
            <Upload size={14} /> Modpack importieren
            <input
              type="file"
              accept=".mrpack,.zip"
              disabled={!native || busy}
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f)
                  void act(async () => {
                    await flush?.();
                    const data = await readData(f, 67_108_864);
                    await call("import_modpack", { id: p.id, data });
                    await reloadState?.();
                    await reloadMods();
                  });
                e.currentTarget.value = "";
              }}
            />
          </label>
        </div>
      </div>
      {tab === "browse" ? (
        <>
          <div className="catalog-filter">
            <div className="segmented">
              {["modrinth", "curseforge"].map((s) => (
                <button
                  key={s}
                  className={provider === s ? "active" : ""}
                  onClick={() => {
                    setProvider(s);
                    setCategory("");
                  }}
                >
                  {s === "modrinth" ? "Modrinth" : "CurseForge"}
                </button>
              ))}
            </div>
            <Select
              aria-label="Inhaltstyp"
              value={kind}
              onChange={(e) => {
                setKind(e.target.value as Kind);
                setCategory("");
              }}
            >
              <option value="mod">Mods</option>
              <option value="modpack">Modpacks</option>
              <option value="shader">Shader</option>
              <option value="resourcepack">Ressourcenpakete</option>
            </Select>
            <Select
              aria-label="Sortierung"
              value={sort}
              onChange={(e) => setSort(e.target.value)}
            >
              <option value="downloads">Beliebteste zuerst</option>
              <option value="updated">Kürzlich aktualisiert</option>
              <option value="relevance">Beste Suchtreffer</option>
            </Select>
            {provider === "modrinth" && kind === "mod" && (
              <Select
                aria-label="Kategorie"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
              >
                <option value="">Alle Kategorien</option>
                <option value="optimization">Performance</option>
                <option value="adventure">Abenteuer</option>
                <option value="decoration">Dekoration</option>
                <option value="technology">Technik</option>
                <option value="utility">Werkzeuge</option>
                <option value="worldgen">Weltgenerierung</option>
              </Select>
            )}
          </div>
          <form
            className="search-bar glass"
            onSubmit={(e) => {
              e.preventDefault();
              setSearchTick((n) => n + 1);
            }}
          >
            <Search size={19} />
            <input
              aria-label="Inhalte suchen"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Suchen – von kleinen Helfern bis zu ganzen Welten …"
            />
            <span className="pill">
              {hits.length} / {total.toLocaleString("de")}
            </span>
          </form>
          {kind === "modpack" && (
            <p className="notice">
              Modpacks werden vollständig geprüft und als eigenes Profil
              angelegt. Vorhandene Welten werden nicht überschrieben. Optionale
              Pack-Dateien werden beim Import ausgelassen.
            </p>
          )}
          {error && (
            <div className="notice" role="alert">
              <MessageText
                value={error}
                language={state.language}
                diagnostics
              />
              <div className="toolbar">
                <GlassButton onClick={() => setSearchTick((n) => n + 1)}>
                  Erneut versuchen
                </GlassButton>
                {provider === "curseforge" && (
                  <GlassButton onClick={() => setSources(true)}>
                    CurseForge einrichten
                  </GlassButton>
                )}
              </div>
            </div>
          )}
          <div className="mod-grid">
            {hits.map((hit) => (
              <GlassPanel
                className="mod-card luminous-card"
                key={hit.project_id}
              >
                <button className="mod-title" onClick={() => setDetail(hit)}>
                  {hit.icon_url ? (
                    <img
                      alt=""
                      src={hit.icon_url}
                      loading="lazy"
                      onError={(e) => (e.currentTarget.hidden = true)}
                    />
                  ) : (
                    <Package size={32} />
                  )}
                  <span>
                    <h3>{hit.title}</h3>
                    <small>von {hit.author}</small>
                  </span>
                </button>
                <p>{hit.description}</p>
                <div className="split">
                  <span className="muted">
                    <Download size={13} />{" "}
                    {Intl.NumberFormat("de", { notation: "compact" }).format(
                      hit.downloads,
                    )}
                  </span>
                  <GlassButton
                    disabled={
                      !native ||
                      busy ||
                      (kind === "mod" && p.loader === "vanilla")
                    }
                    onClick={() => install(hit)}
                  >
                    <Download size={14} />
                    {kind === "modpack" ? "Als Profil" : "Installieren"}
                  </GlassButton>
                </div>
              </GlassPanel>
            ))}
          </div>
          <div className="catalog-sentinel" ref={sentinel} role="status">
            {loading ? (
              <>
                <span className="loading-dots" />{" "}
                {hits.length
                  ? "Weitere Inhalte werden geladen …"
                  : "Katalog wird durchsucht …"}
              </>
            ) : !error && hits.length ? (
              end.current ? (
                "Du hast das Ende dieser Suche erreicht."
              ) : (
                "Beim Weiterscrollen lädt Votex automatisch nach."
              )
            ) : null}
          </div>
          {!loading && !error && !hits.length && (
            <Empty title="Keine passenden Inhalte gefunden.">
              Keine passenden Treffer. Wähle eine andere Version, einen
              Modloader oder einen weiteren Suchbegriff.
            </Empty>
          )}
        </>
      ) : (
        <>
          <div className="toolbar">
            <GlassButton
              disabled={!native || busy}
              onClick={() => void act(reloadMods)}
            >
              <RefreshCw size={15} /> Dateien neu einlesen
            </GlassButton>
            <GlassButton
              disabled={
                !native ||
                busy ||
                p.loader !== "fabric" ||
                p.minecraft !== "26.2"
              }
              onClick={() =>
                void act(async () => {
                  await flush?.();
                  await call("install_mod", {
                    id: p.id,
                    project: "fabric-api",
                  });
                  await call("install_core", { id: p.id });
                  await reloadMods();
                })
              }
            >
              Votex Core + Fabric API installieren
            </GlassButton>
          </div>
          <p className="muted">
            Änderungen gelten beim nächsten Spielstart. Shader und
            Ressourcenpakete liegen in ihren eigenen Unterordnern. Core für
            Minecraft 26.2 öffnest du im Spiel mit der rechten Umschalttaste.
          </p>
          <div className="installed-grid">
            {mods.map((m) => (
              <GlassPanel className="installed-row" key={m.filename}>
                {m.icon || m.modId === "votex_client" ? (
                  <img
                    className="installed-icon"
                    alt=""
                    src={m.icon || "/assets/votex-cloud.svg"}
                    onError={(e) => {
                      e.currentTarget.style.visibility = "hidden";
                    }}
                  />
                ) : (
                  <span className="installed-fallback" aria-hidden="true">
                    {(m.displayName || m.metadata?.name || m.filename)
                      .slice(0, 2)
                      .toUpperCase()}
                  </span>
                )}
                <div className="installed-description">
                  <h3>
                    {m.displayName ??
                      m.metadata?.name ??
                      m.filename.replace(/\.jar(\.disabled)?$/, "")}
                  </h3>
                  <small>
                    {m.metadata?.version_number ?? "Lokale Datei"} ·{" "}
                    {m.filename}
                  </small>
                </div>
                <Toggle
                  label={m.enabled ? "Aktiv" : "Aus"}
                  value={m.enabled}
                  disabled={busy}
                  onChange={() =>
                    void act(async () =>
                      setMods(
                        await call("toggle_mod", {
                          id: p.id,
                          filename: m.filename,
                        }),
                      ),
                    )
                  }
                />
                <button
                  className="icon-button danger"
                  title="Mod entfernen"
                  disabled={busy}
                  onClick={() => setRemove(m)}
                >
                  <Trash2 size={16} />
                </button>
              </GlassPanel>
            ))}
          </div>
          {!mods.length && (
            <Empty title="Noch keine Mods installiert.">
              In diesem Profil sind noch keine externen Mod-Dateien installiert.
            </Empty>
          )}
        </>
      )}
      {detail && (
        <Modal title={detail.title} onClose={() => setDetail(null)}>
          <p className="notice">
            Installationsziel: <strong>{p.name}</strong> · {p.loader} ·
            Minecraft {p.minecraft}.{" "}
            {kind === "modpack"
              ? "Modpacks erhalten ein neues, getrenntes Profil."
              : "Die Dateien landen ausschließlich im Spielordner dieses Profils."}
          </p>
          <p>{detail.description}</p>
          <p className="muted">
            von {detail.author} · {detail.downloads.toLocaleString("de")}{" "}
            Downloads. Die passende Version wird vor der Installation erneut
            geprüft.
          </p>
          <div className="toolbar">
            <GlassButton
              className="primary"
              disabled={!native || busy}
              onClick={() => install(detail)}
            >
              {kind === "modpack"
                ? "Als neues Profil importieren"
                : "Kompatible Version installieren"}
            </GlassButton>
            <a
              className="btn"
              href={sourceURL(detail)}
              onClick={(e) => {
                if (native) {
                  e.preventDefault();
                  void act(() =>
                    call("open_catalog", { provider, kind, slug: detail.slug }),
                  );
                }
              }}
              target="_blank"
              rel="noreferrer"
            >
              <ExternalLink size={14} /> Projektseite
            </a>
          </div>
        </Modal>
      )}
      {remove && (
        <Modal
          title="Mod aus diesem Profil entfernen?"
          onClose={() => setRemove(null)}
        >
          <p>
            {remove.filename} wird in den Sicherungsspeicher verschoben. Ein
            Sicherungspunkt bewahrt den aktuellen Stand.
          </p>
          <GlassButton
            className="danger"
            onClick={() =>
              void act(async () => {
                setMods(
                  await call("remove_mod", {
                    id: p.id,
                    filename: remove.filename,
                  }),
                );
                setRemove(null);
              })
            }
          >
            Sichern & entfernen
          </GlassButton>
        </Modal>
      )}
      {sources && (
        <Modal
          title="Deine Inhaltsquellen"
          onClose={() => {
            setSources(false);
            setKey("");
          }}
        >
          <div className="source-status">
            <ShieldCheck size={24} />
            <div>
              <h3>Modrinth ist bereit</h3>
              <p className="muted">
                Öffentlicher Katalog. Kein API-Schlüssel erforderlich.
              </p>
            </div>
          </div>
          <div className="source-status">
            <KeyRound size={24} />
            <div>
              <h3>
                CurseForge ·{" "}
                {configured
                  ? "Schlüssel hinterlegt"
                  : "Eigener API-Schlüssel erforderlich"}
              </h3>
              <p className="muted">
                Votex verwendet ausschließlich deinen eigenen Schlüssel. Er
                liegt in der Windows-Anmeldeinformationsverwaltung, nicht in
                Profil-Exporten.
              </p>
            </div>
          </div>
          <label className="form-stack">
            CurseForge-API-Schlüssel
            <input
              aria-label="CurseForge-API-Schlüssel"
              type="password"
              autoComplete="off"
              value={key}
              onChange={(e) => setKey(e.target.value)}
              placeholder="Nur hier in der Desktop-App eintragen"
            />
          </label>
          <div className="toolbar">
            <GlassButton
              disabled={!native || !key.trim()}
              onClick={() =>
                void act(async () => {
                  await call("save_curseforge_key", { key: key.trim() });
                  setKey("");
                  setConfigured(true);
                  setKeyMessage("Schlüssel sicher gespeichert.");
                  setSearchTick((n) => n + 1);
                })
              }
            >
              Sicher speichern
            </GlassButton>
            <GlassButton
              disabled={!native || !configured}
              onClick={() =>
                void act(async () => {
                  await call("save_curseforge_key", { key: "" });
                  setConfigured(false);
                  setKeyMessage("Schlüssel entfernt.");
                })
              }
            >
              Schlüssel entfernen
            </GlassButton>
          </div>
          {keyMessage && <p role="status">{keyMessage}</p>}
          <p className="notice">
            Ohne eigenen CurseForge-Zugang bleibt die Modrinth-Suche vollständig
            nutzbar. Gesperrte Drittanbieter-Downloads werden respektiert. Votex
            schließt kein Abo ab und erstellt keine kostenpflichtigen
            Ressourcen.
          </p>
          <a
            href="https://console.curseforge.com/"
            onClick={(e) => {
              if (native) {
                e.preventDefault();
                void act(() =>
                  call("open_catalog", {
                    provider: "curseforge-console",
                    kind: "mod",
                    slug: "setup",
                  }),
                );
              }
            }}
            target="_blank"
            rel="noreferrer"
          >
            Offizielle CurseForge-Entwicklerkonsole öffnen
          </a>
        </Modal>
      )}
    </>
  );
}
