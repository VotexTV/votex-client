import { PlayStage } from "./components/PlayStage";
import type { HorizonRuntimeStatus, NativeClientStatus, PlayHistory } from "./services/desktop";
import { ScrollRail } from "./components/ScrollRail";
import { buildLabel } from "./data/buildLabel";
import { InstallationFeedback } from "./components/InstallationFeedback";
import { installationJob } from "./services/installations";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
} from "react";
import { motion, AnimatePresence, useReducedMotion } from "motion/react";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWindow } from "@tauri-apps/api/window";
import {
  Play,
  Layers,
  Package,
  Shirt,
  Sparkles,
  PersonStanding,
  Activity,
  Settings as SettingsIcon,
  Palette,
  ShieldCheck,
  ChevronDown,
  ArrowUpRight,
  Bell,
  Minus,
  Square,
  X,
  Check,
  FolderOpen,
  LogIn,
  ArrowRight,
  Info,
  Search,
  Camera,
} from "lucide-react";
import { CommandPalette, type QuickAction } from "./components/CommandPalette";
import { useApp } from "./stores/useApp";
import { ThemeContext, GlassButton, Modal } from "./components/Glass";

import { PreviewSuspendedContext } from "./components/PreviewActivity";

import { useLuminous } from "./components/Luminous";
import { interactionResponse } from "../../../packages/shared-config/visuals";
import { useUITransitions } from "./components/UITransitions";
import { MicrosoftAccounts } from "./components/MicrosoftAccounts";
import { withOfflineUsername } from "./services/offlineIdentity";
import { MessageText } from "./components/MessageText";
import { errorText, messageText } from "./services/messages";
import { Profiles } from "./pages/Profiles";
import { DesignStudio } from "./pages/DesignStudio";
import { Mods } from "./pages/Mods";
import { Skins } from "./pages/Skins";
import { Cosmetics, Animations } from "./pages/Cosmetics";
import { Settings, Performance } from "./pages/Settings";
import { Recovery } from "./pages/Recovery";
import { call, native, type Accounts, type Job } from "./services/desktop";

const navigation = [
  { id: "play", label: "Play", de: "Spielen", icon: Play },
  { id: "profiles", label: "Profiles", de: "Profile", icon: Layers },
  { id: "skins", label: "Skins", de: "Skins", icon: Shirt },
  { id: "cosmetics", label: "Cosmetics", de: "Kosmetik", icon: Sparkles },
  {
    id: "animations",
    label: "Animations",
    de: "Animationen",
    icon: PersonStanding,
  },
  { id: "performance", label: "Performance", de: "Leistung", icon: Activity },
  {
    id: "recovery",
    label: "Recovery",
    de: "Wiederherstellung",
    icon: ShieldCheck,
  },
];
export default function App() {
  const [viewport, setViewport] = useState(() => ({
    width: window.innerWidth,
    height: window.innerHeight,
  }));
  useEffect(() => {
    let frame = 0;
    const resize = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() =>
        setViewport({ width: window.innerWidth, height: window.innerHeight }),
      );
    };
    window.addEventListener("resize", resize);
    return () => {
      window.removeEventListener("resize", resize);
      cancelAnimationFrame(frame);
    };
  }, []);
  const [page, setPage] = useState("play");
  const [playHistory, setPlayHistory] = useState<PlayHistory>({});
  const historyInitialized = useRef(false);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [managedProfileId, setManagedProfileId] = useState("");
  const [notice, setNotice] = useState("");
  const [history, setHistory] = useState<string[]>([]);
  const [showHistory, setShowHistory] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);

  const [accounts, setAccounts] = useState<Accounts>({
    selected: "",
    items: [],
  });
  const [loggingIn, setLoggingIn] = useState(false);
  const [job, setJob] = useState<Job>({
    phase: "idle",
    message: "",
    completed: 0,
    total: 0,
  });
  const [nativeClient, setNativeClient] = useState<NativeClientStatus>({
    installed: false,
    version: null,
    releaseTag: null,
    license: "GPL-3.0-or-later",
    sourceUrl: "https://github.com/PommeMC/Client",
    reason: null,
  });
  const [horizonRuntime, setHorizonRuntime] = useState<HorizonRuntimeStatus>({
    installed: false,
    version: null,
    releaseTag: null,
    license: "MIT",
    sourceUrl: "https://github.com/horizon-rust/horizon",
    reason: null,
  });
  const [refresh, setRefresh] = useState(0);
  const [accountSkin, setAccountSkin] = useState("");
  const [launchPreference, setLaunchPreference] = useState<
    "" | "microsoft" | "offline"
  >("");
  const accountRequest = useRef(0);

  const [crash, setCrash] = useState<{
    profile: string;
    exitCode: number | null;
    changes: string[];
    hasWorking: boolean;
    safe: boolean;
    diagnostics?: {
      signals: string[];
      reportName: string | null;
      reportExcerpt: string;
    };
  } | null>(null);

  const resetPageScroll = useCallback((element: HTMLDivElement | null) => {
    if (element?.parentElement) element.parentElement.scrollTop = 0;
  }, []);
  const report = useCallback((message: string) => {
    setNotice(message);
    setHistory((h) => [message, ...h].slice(0, 30));
  }, []);
  const { state, update, ready, saved, reload, flush, loadError } =
    useApp(report);
  const [offlineUsername, setOfflineUsername] = useState(state.offlineUsername);
  const act = useCallback(
    async <T,>(fn: () => Promise<T>): Promise<T | undefined> => {
      try {
        return await fn();
      } catch (e) {
        report(errorText(e));
        return undefined;
      }
    },
    [report],
  );
  useEffect(() => {
    if (!native || !ready) return;
    let cancelled = false;
    void act(async () => {
      const status = await call<NativeClientStatus>("native_client_status");
      if (!cancelled) setNativeClient(status);
    });
    return () => {
      cancelled = true;
    };
  }, [native, ready, act]);
  useEffect(() => {
    if (!native || !ready) return;
    let cancelled = false;
    void act(async () => {
      const status = await call<HorizonRuntimeStatus>("horizon_runtime_status");
      if (!cancelled) setHorizonRuntime(status);
    });
    return () => { cancelled = true; };
  }, [native, ready, act]);
  const theme = state.themes.find((t) => t.id === state.themeId)!;
  const profile = state.profiles.find((p) => p.id === state.activeProfile)!;

  const account = accounts.items.find((a) => a.id === accounts.selected);
  const launchMode = launchPreference || (account ? "microsoft" : "offline");
  useEffect(
    () => setOfflineUsername(state.offlineUsername),
    [state.offlineUsername],
  );
  const pageScrollRef = useRef<HTMLElement>(null);
  const reduced = useReducedMotion() || theme.reducedMotion;
  const rgb = theme.background
    .slice(1)
    .match(/../g)!
    .map((x) => parseInt(x, 16) / 255);
  const lightTheme = rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722 > 0.6;
  useLuminous(theme, page, !!reduced);
  useUITransitions(!!reduced, page);
  const gameBusy = [
    "installing",
    "authenticating",
    "starting",
    "mods",
  ].includes(job.phase);
  const busy = gameBusy || loggingIn;
  const de = state.language === "de";
  useEffect(() => {
    const handle = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        if (document.querySelector('[role="dialog"]') && !paletteOpen) return;
        e.preventDefault();
        setPaletteOpen((x) => !x);
      }
    };
    window.addEventListener("keydown", handle);
    return () => window.removeEventListener("keydown", handle);
  }, [paletteOpen]);
  const quickActions: QuickAction[] = [
    ...navigation.map((n) => ({
      id: `page-${n.id}`,
      name: n.de,
      detail: "Bereich öffnen",
      group: "Navigation",
      icon: n.icon,
      run: () => setPage(n.id),
    })),
    {
      id: "page-design",
      name: "Design Studio",
      detail: "Oberfläche, Farben und Bewegung",
      group: "Navigation",
      icon: Palette,
      run: () => setPage("design"),
    },
    {
      id: "page-settings",
      name: "Einstellungen",
      detail: "Sprache, Java und Verwaltung",
      group: "Navigation",
      icon: SettingsIcon,
      run: () => setPage("settings"),
    },
    ...state.profiles.flatMap((p) => [
      {
        id: `select-${p.id}`,
        name: p.name,
        detail: `Für die Startseite auswählen · ${p.loader} ${p.minecraft}`,
        group: "Deine Profile",
        icon: Layers,
        disabled: busy,
        run: () => {
          update((s) => ({ ...s, activeProfile: p.id }));
          setPage("play");
        },
      },
      {
        id: `mods-${p.id}`,
        name: `Mods · ${p.name}`,
        detail: `Installierte Mods verwalten · ${p.minecraft}`,
        group: "Profilinhalte",
        icon: Package,
        run: () => {
          setManagedProfileId(p.id);
          setPage("mods");
        },
      },
    ]),
    ...[
      { key: "screenshots", name: "Screenshots", icon: Camera },
      { key: "shaderpacks", name: "Shader-Ordner", icon: Sparkles },
      { key: "resourcepacks", name: "Ressourcenpakete", icon: FolderOpen },
    ].map((f) => ({
      id: `folder-${f.key}`,
      name: f.name,
      detail: `Ordner von ${profile.name} öffnen`,
      group: "Deine Dateien",
      icon: f.icon,
      disabled: !native,
      run: () =>
        void act(() =>
          call("open_content_folder", { id: profile.id, folder: f.key }),
        ),
    })),
  ];

  const readAccounts = async () => {
    const request = ++accountRequest.current;
    const a = await call<Accounts>("get_accounts");
    if (request !== accountRequest.current) return;
    setAccounts(a);
    setAccountSkin("");
    void act(async () => {
      const skin = await call<string | null>("account_skin");
      if (request === accountRequest.current) setAccountSkin(skin ?? "");
    });
  };
  useEffect(() => {
    if (!ready || !native) return;
    void act(async () => {
      await readAccounts();
    });
  }, [ready]);
  useEffect(() => {
    if (!native) return;
    let unlisten: (() => void)[] = [];
    let gone = false;
    void (async () => {
      const a = await listen<Job>("votex:job", (e) => {
        setJob(e.payload);
        installationJob(e.payload);
        if (["running", "exited"].includes(e.payload.phase)) void act(reload);
        if (
          ["error", "installed", "exited", "idle"].includes(e.payload.phase)
        ) {
          setRefresh((n) => n + 1);
          if (e.payload.phase === "error") report(e.payload.message);
        }
      });
      const b = await listen<NonNullable<typeof crash>>("votex:crash", (e) =>
        setCrash(e.payload),
      );
      const c = await listen("votex:game-options", () => void act(reload));
      if (gone) {
        a();
        b();
        c();
      } else unlisten = [a, b, c];
    })();
    return () => {
      gone = true;
      unlisten.forEach((f) => f());
    };
  }, []);
  useEffect(() => {
    if (!notice) return;
    const t = setTimeout(() => setNotice(""), 10_000);
    return () => clearTimeout(t);
  }, [notice]);
  useEffect(() => {
    document.documentElement.lang = state.language;
    document.documentElement.dataset.compact = String(theme.compact);
    document.documentElement.dataset.hover = String(theme.hover);
    document.documentElement.dataset.reduced = String(reduced);
    document.documentElement.dataset.quality = theme.renderProfile;
    document.documentElement.dataset.ambient = String(theme.ambientMotion);
    document.documentElement.dataset.blur = String(theme.blur > 0);
    const hidden = () =>
      (document.documentElement.dataset.hidden = String(document.hidden));
    hidden();
    document.addEventListener("visibilitychange", hidden);
    return () => document.removeEventListener("visibilitychange", hidden);
  }, [theme, state.language, reduced]);
  const vars = {
    "--accent": theme.accent,
    "--secondary": theme.secondary,
    "--accent-ink": lightTheme ? theme.text : theme.secondary,
    "--tint": theme.tint,
    "--bg": theme.background,
    "--text": theme.text,
    "--muted": theme.muted,
    "--glass-opacity": String(
      theme.glassEffect ? 0.08 + theme.transparency * 0.008 : 0.94,
    ),
    "--blur": `${theme.glassEffect ? theme.blur * 0.45 : 0}px`,
    "--saturation": String(0.5 + theme.saturation * 0.02),
    "--radius": `${8 + theme.radius * 0.3}px`,
    "--glow": String(theme.glowEffect ? theme.glow / 100 : 0),
    "--reflection": String(theme.reflectionEffect ? theme.reflection / 100 : 0),
    "--shadow": String(theme.shadow / 100),
    "--sidebar": `${theme.sidebar}px`,
    "--gap": `${theme.spacing}px`,
    "--ui-scale": String(theme.scale / 100),
    "--viewport-height": `${(viewport.height * 100) / theme.scale}px`,
    "--brightness": String(0.25 + theme.brightness * 0.01),
    "--background-blur": `${theme.backgroundBlur}px`,
    "--background-position-x": `${theme.backgroundPositionX}%`,
    "--background-position-y": `${theme.backgroundPositionY}%`,
    "--background-zoom": String(theme.backgroundZoom / 100),
    "--duration": `${0.65 - theme.speed * 0.005}s`,
    "--hover-duration": `${Math.max(140, Math.min(360, 4000 / interactionResponse(theme)))}ms`,
    "--button-lift": `${Math.min(2, theme.lift * 0.025)}px`,
    "--press-scale": String(1 - (theme.wobbleEffect ? theme.wobble / 2500 : 0)),
    "--lift": `${theme.proximityEffect ? theme.lift * 0.085 : 0}px`,
    "--tilt": `${theme.proximityEffect ? theme.tilt * 0.065 : 0}deg`,
  } as CSSProperties;
  useEffect(() => {
    if (!native || !ready || !saved || page !== "play") return;
    let cancelled = false;
    void act(async () => {
      const next = await call<PlayHistory>("get_play_history");
      if (cancelled) return;
      setPlayHistory(next);
      if (!historyInitialized.current) {
        historyInitialized.current = true;
        if (
          next.lastProfileId &&
          state.profiles.some((p) => p.id === next.lastProfileId)
        )
          update((s) => ({ ...s, activeProfile: next.lastProfileId! }));
      }
    });
    return () => {
      cancelled = true;
    };
  }, [ready, saved, refresh, page]);
  const run = (
    action: "launch" | "install" | "safe",
    profileId = profile.id,
    authMode: "microsoft" | "offline" = launchMode,
    serverAddress?: string,
  ) =>
    void act(async () => {
      const selectedProfile = state.profiles.find((item) => item.id === profileId) ?? profile;
      const selectedRuntime = action === "launch" && selectedProfile.engine === "horizon"
        ? "horizon"
        : action === "launch" ? state.gameRuntime : "java";
      const selectedAuthMode =
        selectedRuntime === "pomme" ? "microsoft" : authMode;
      if (action !== "install" && state.activeProfile !== profileId)
        update((s) => ({ ...s, activeProfile: profileId }));
      if (
        action !== "install" &&
        selectedAuthMode === "offline"
      ) {
        const name = offlineUsername.trim();
        if (!/^[A-Za-z0-9_]{1,16}$/.test(name))
          throw new Error(
            "Offline-Namen müssen 1–16 Zeichen haben und nur Buchstaben, Zahlen oder _ enthalten.",
          );
        update((s) => ({ ...s, offlineUsername: name }));
      }
      await flush();
      if (
        action !== "install" &&
        selectedRuntime !== "pomme" &&
        selectedAuthMode === "microsoft" &&
        !account
      )
        throw new Error(
          "Melde dich zuerst mit deinem Microsoft-Minecraft-Konto an.",
        );
      setJob({
        phase:
          action === "install"
            ? "installing"
            : selectedRuntime === "pomme"
              ? "starting"
              : selectedRuntime === "horizon"
                ? "authenticating"
              : authMode === "offline"
                ? "starting"
                : "authenticating",
        message: "Wird vorbereitet …",
        completed: 0,
        total: 0,
      });
      try {
        await call("run_game", {
          id: profileId,
          action,
          authMode: selectedAuthMode,
          runtime: selectedRuntime,
          offlineName: offlineUsername.trim(),
          ...(serverAddress ? { serverAddress } : {}),
        });
      } catch (e) {
        setJob({ phase: "idle", message: "", completed: 0, total: 0 });
        throw e;
      }
    });
  const installNativeClient = () =>
    void act(async () => {
      setJob({
        phase: "installing",
        message: "Pomme wird von der offiziellen Quelle geladen …",
        completed: 0,
        total: 0,
      });
      try {
        const status = await call<NativeClientStatus>("install_native_client");
        setNativeClient(status);
      } finally {
        setJob({ phase: "idle", message: "", completed: 0, total: 0 });
      }
    });
  const installHorizonRuntime = () =>
    void act(async () => {
      setJob({ phase: "installing", message: "Horizon wird von seinem signierten offiziellen Release geladen …", completed: 0, total: 0 });
      try {
        const status = await call<HorizonRuntimeStatus>("install_horizon_runtime");
        setHorizonRuntime(status);
      } finally {
        setJob({ phase: "idle", message: "", completed: 0, total: 0 });
      }
    });
  const pageProps = {
    state,
    update,
    act,
    busy,
    refresh,
    reload,
    flush,
    accountSkin,
    accountModel:
      account?.model === "SLIM" ? ("slim" as const) : ("default" as const),
    launchProfile: (id: string) => {
      update((s) => ({ ...s, activeProfile: id }));
      run("launch", id, launchMode);
    },
  };
  return (
    <ThemeContext.Provider value={theme}>
      <PreviewSuspendedContext.Provider
        value={job.phase === "starting" || job.phase === "running"}
      >
        <div
          className={`app-shell background-${theme.backgroundMode} ${lightTheme ? "theme-light" : ""}`}
          data-page={page}
          data-layout={
            (viewport.width * 100) / theme.scale < 1000 ? "compact" : "full"
          }
          data-short={(viewport.height * 100) / theme.scale < 650}
          style={vars}
        >
          <div
            className="app-atmosphere"
            style={
              theme.backgroundMode === "image" && theme.backgroundImage
                ? {
                    backgroundImage: `url(${theme.backgroundImage})`,
                    backgroundPosition: `${theme.backgroundPositionX}% ${theme.backgroundPositionY}%`,
                  }
                : undefined
            }
          />
          {paletteOpen && (
            <CommandPalette
              actions={quickActions}
              onClose={() => setPaletteOpen(false)}
            />
          )}
          <header className="titlebar">
            <div className="title-drag" data-tauri-drag-region>
              <span>VOTEX CLIENT</span>
              <span className="alpha-tag">{buildLabel}</span>
            </div>
            <span className="title-note">Minecraft Java</span>
            {native && (
              <div className="window-controls">
                <button
                  aria-label={de ? "Minimieren" : "Minimize"}
                  onClick={() => void getCurrentWindow().minimize()}
                >
                  <Minus size={15} />
                </button>
                <button
                  aria-label={
                    de
                      ? "Maximieren oder wiederherstellen"
                      : "Maximize or restore"
                  }
                  onClick={() => void getCurrentWindow().toggleMaximize()}
                >
                  <Square size={12} />
                </button>
                <button
                  aria-label={de ? "Schließen" : "Close"}
                  onClick={() =>
                    void act(async () => {
                      await flush();
                      await getCurrentWindow().close();
                    })
                  }
                >
                  <X size={16} />
                </button>
              </div>
            )}
          </header>
          <aside className="sidebar">
            <button
              className="brand"
              onClick={() => setPage("play")}
              aria-label={de ? "Votex-Startseite" : "Votex home"}
            >
              <img
                className="brand-symbol"
                src="/assets/votex-cloud.svg"
                alt=""
                aria-hidden="true"
              />
              <span>
                votex<span>CLIENT</span>
              </span>
            </button>
            <div className="sidebar-label">NAVIGATION</div>
            <nav>
              {navigation.map(({ id, label, de: localized, icon: Icon }) => (
                <button
                  key={id}
                  aria-label={de ? localized : label}
                  title={de ? localized : label}
                  className={`nav-item ${page === id ? "active" : ""}`}
                  onClick={() => setPage(id)}
                >
                  <Icon size={18} strokeWidth={1.7} />
                  <span>{de ? localized : label}</span>
                  {id === "play" && page === id && <span className="nav-dot" />}
                </button>
              ))}
            </nav>
            <div className="sidebar-bottom">
              <button
                aria-label="Design Studio"
                title="Design Studio"
                className={`studio-nav ${page === "design" ? "active" : ""}`}
                onClick={() => setPage("design")}
              >
                <Palette size={18} />
                <span>
                  {de ? "Design Studio" : "Design Studio"}
                  <small>OBERFLÄCHE</small>
                </span>
                <ArrowUpRight size={14} />
              </button>
              <button
                aria-label={de ? "Einstellungen" : "Settings"}
                title={de ? "Einstellungen" : "Settings"}
                className={`nav-item ${page === "settings" ? "active" : ""}`}
                onClick={() => setPage("settings")}
              >
                <SettingsIcon size={18} />
                {de ? "Einstellungen" : "Settings"}
              </button>
              <div className="sidebar-footer">
                <i className="status-dot" />
                {native ? "DESKTOP" : "BROWSER"}
              </div>
            </div>
          </aside>
          <div className="workspace">
            <div className="topbar">
              <div className="breadcrumb">
                {de ? "Votex Client" : "Votex Client"} <span>/</span>{" "}
                <strong>
                  {page === "design"
                    ? "Design Studio"
                    : page === "settings"
                      ? de
                        ? "Einstellungen"
                        : "Settings"
                      : de
                        ? navigation.find((n) => n.id === page)?.de
                        : navigation.find((n) => n.id === page)?.label}
                </strong>
              </div>
              <div className="topbar-actions">
                <button
                  className="scale-reset"
                  title="Oberflächengröße auf 100 % zurücksetzen"
                  aria-label="Oberflächengröße zurücksetzen"
                  onClick={() =>
                    update((s) => ({
                      ...s,
                      themes: s.themes.map((t) =>
                        t.id === theme.id ? { ...t, scale: 100 } : t,
                      ),
                    }))
                  }
                >
                  {theme.scale} %
                </button>
                <button
                  className="quick-search-trigger"
                  aria-label="Schnellaktionen öffnen"
                  title="Aktionen und Profile suchen (Strg+K)"
                  onClick={() => setPaletteOpen(true)}
                >
                  <Search size={14} />
                  <span>Suchen</span>
                  <kbd>Strg K</kbd>
                </button>
                <span className="active-instance">
                  <i className="status-dot" />
                  {profile.name}
                </span>
                <button
                  className="notification-button icon-button"
                  aria-label="Mitteilungen"
                  onClick={() => setShowHistory(true)}
                >
                  <Bell size={18} />
                  {history.length > 0 && <i />}
                </button>
                <span className="top-divider" />
                <button
                  className="account-button"
                  onClick={() => setAccountOpen(true)}
                >
                  <div className="avatar">
                    {account?.name.slice(0, 1) ?? <LogIn size={16} />}
                  </div>
                  <div>
                    {account?.name ?? (de ? "Anmelden" : "Sign in")}
                    <small>
                      {account ? "MICROSOFT-KONTO" : "MICROSOFT-KONTO"}
                    </small>
                  </div>
                  <ChevronDown size={14} />
                </button>
              </div>
            </div>
            <div className="page-region">
              <main
                ref={pageScrollRef}
                id="page-scroll"
                className={`page-content page-${page}`}
              >
                <AnimatePresence mode="wait">
                  <motion.div
                    ref={resetPageScroll}
                    key={page}
                    initial={
                      reduced ? false : { opacity: 0, y: 18, scale: 0.985 }
                    }
                    animate={{ opacity: 1, y: 0, scale: 1 }}
                    exit={reduced ? undefined : { opacity: 0, y: -4 }}
                    transition={{
                      duration: reduced ? 0 : 0.42,
                      ease: [0.22, 1, 0.36, 1],
                    }}
                    className="page-enter"
                    data-route={page}
                  >
                    {loadError ? (
                      <div className="empty">
                        <h2>
                          {de
                            ? "Konfiguration geschützt"
                            : "Configuration protected"}
                        </h2>
                        <p>
                          {de
                            ? "Die gespeicherte Konfiguration konnte nicht gelesen werden und bleibt erhalten. Prüfe die JSON-Konfiguration und starte Votex erneut. Automatische Änderungen sind bis dahin gesperrt."
                            : "The saved configuration could not be read and has been preserved. Check the JSON configuration and restart Votex. Automatic changes are locked until then."}
                        </p>
                        <MessageText
                          value={loadError}
                          language={state.language}
                          diagnostics
                        />
                      </div>
                    ) : !ready ? (
                      <div className="empty">
                        {de ? "Votex wird geladen …" : "Loading Votex …"}
                      </div>
                    ) : page === "play" ? (
                      <PlayStage
                        state={state}
                        profile={profile}
                        history={playHistory}
                        accountSkin={accountSkin}
                        accountModel={
                          account?.model === "SLIM" ? "slim" : "default"
                        }
                        accountName={account?.name}
                        nativeClient={nativeClient}
                        horizonRuntime={horizonRuntime}
                        native={native}
                        busy={busy}
                        job={job}
                        launchMode={launchMode}
                        offlineName={offlineUsername}
                        onOfflineName={setOfflineUsername}
                        onLaunchMode={setLaunchPreference}
                        onRuntimeChange={(gameRuntime) =>
                          update((s) => ({ ...s, gameRuntime }))
                        }
                        onInstallNativeClient={installNativeClient}
                        onInstallHorizonRuntime={installHorizonRuntime}
                        onAccount={() => setAccountOpen(true)}
                        onProfile={(id) =>
                          update((s) => ({ ...s, activeProfile: id }))
                        }
                        onIdle={(idleSkin) =>
                          update((s) => ({ ...s, idleSkin }))
                        }
                        onLaunch={(id, server) =>
                          run("launch", id, launchMode, server)
                        }
                      />
                    ) : page === "profiles" ? (
                      <Profiles
                        {...pageProps}
                        manageMods={(id) => {
                          setManagedProfileId(id);
                          setPage("mods");
                        }}
                      />
                    ) : page === "design" ? (
                      <DesignStudio {...pageProps} />
                    ) : page === "mods" ? (
                      <Mods
                        {...pageProps}
                        profileId={managedProfileId}
                        onTarget={setManagedProfileId}
                        onBack={() => setPage("profiles")}
                      />
                    ) : page === "skins" ? (
                      <Skins {...pageProps} />
                    ) : page === "cosmetics" ? (
                      <Cosmetics {...pageProps} />
                    ) : page === "animations" ? (
                      <Animations {...pageProps} />
                    ) : page === "performance" ? (
                      <Performance {...pageProps} />
                    ) : page === "recovery" ? (
                      <Recovery
                        {...pageProps}
                        onRestored={reload}
                        onSafe={() => run("safe")}
                      />
                    ) : (
                      <Settings {...pageProps} />
                    )}
                  </motion.div>
                </AnimatePresence>
              </main>
              {page === "mods" && <ScrollRail target={pageScrollRef} />}
            </div>
            <footer className="statusbar">
              <span>
                <span
                  className={`status-dot ${busy || job.phase === "running" ? "" : "neutral"}`}
                />
                {job.phase === "running"
                  ? de
                    ? "Minecraft läuft"
                    : "Minecraft is running"
                  : busy
                    ? job.message
                      ? messageText(job.message, state.language)
                      : de
                        ? "Wird vorbereitet …"
                        : "Preparing …"
                    : native
                      ? de
                        ? "Votex ist bereit"
                        : "Votex is ready"
                      : de
                        ? "Vorschau · Spielstart in der Desktop-App"
                        : "Preview · game launch needs the desktop app"}
              </span>
              <span>
                {saved ? (
                  <>
                    <Check size={12} />
                    {de ? "Gespeichert" : "All changes saved"}
                  </>
                ) : de ? (
                  "Speichern…"
                ) : (
                  "Saving…"
                )}
                <b>VOTEX {buildLabel.toUpperCase()}</b>
              </span>
            </footer>
          </div>
          <InstallationFeedback />
          {notice && (
            <div className="toast" role="alert">
              <Info size={19} />
              <MessageText
                value={notice}
                language={state.language}
                diagnostics
              />
              <button
                className="icon-button"
                aria-label={de ? "Nachricht schließen" : "Dismiss notification"}
                onClick={() => setNotice("")}
              >
                <X size={16} />
              </button>
            </div>
          )}
          {accountOpen && (
            <MicrosoftAccounts
              offlineName={state.offlineUsername}
              onOfflineName={async (name) => {
                if (!update((s) => withOfflineUsername(s, name)))
                  throw new Error(
                    "Der Offline-Name konnte nicht gespeichert werden.",
                  );
                setOfflineUsername(name);
                await flush();
              }}
              accounts={accounts}
              clientId={state.clientId}
              de={de}
              busy={gameBusy}
              flush={flush}
              onRefresh={readAccounts}
              onSettings={() => {
                setAccountOpen(false);
                setPage("settings");
              }}
              onClose={() => setAccountOpen(false)}
              onBusyChange={setLoggingIn}
              report={report}
            />
          )}
          {showHistory && (
            <Modal title="Mitteilungen" onClose={() => setShowHistory(false)}>
              {history.length ? (
                history.map((h, i) => (
                  <div className="notification-item" key={i}>
                    <MessageText
                      value={h}
                      language={state.language}
                      diagnostics
                    />
                  </div>
                ))
              ) : (
                <p>
                  Alles ruhig. Meldungen zu Konto, Downloads und Spielstarts
                  erscheinen hier.
                </p>
              )}
            </Modal>
          )}
          {crash && (
            <Modal title="Votex Safe Launch" onClose={() => setCrash(null)}>
              <p>
                Minecraft wurde beendet. Exit-Code:{" "}
                {crash.exitCode ?? (de ? "nicht verfügbar" : "unavailable")}.
              </p>
              <p>
                {crash.changes.length} Änderungen seit dem letzten bestätigten
                Spielstart. Diese Unterschiede sind Hinweise und beweisen noch
                keine Ursache.
              </p>
              {crash.changes.length > 0 && (
                <pre className="log">{crash.changes.join("\n")}</pre>
              )}
              {crash.diagnostics?.signals.map((signal) => (
                <div className="notice" key={signal}>
                  <MessageText
                    value={signal}
                    language={state.language}
                    diagnostics
                  />
                </div>
              ))}
              {crash.diagnostics?.reportExcerpt && (
                <details>
                  <summary>
                    Absturzbericht: {crash.diagnostics.reportName}
                  </summary>
                  <pre className="log">{crash.diagnostics.reportExcerpt}</pre>
                </details>
              )}
              <div className="form-stack">
                <GlassButton
                  onClick={() => {
                    setCrash(null);
                    setPage("recovery");
                  }}
                >
                  Sicherungspunkte & Startprotokoll ansehen
                </GlassButton>
                <GlassButton
                  disabled={!crash.hasWorking || busy}
                  onClick={() =>
                    void act(async () => {
                      await call("disable_recent", { id: crash.profile });
                      setCrash(null);
                      setRefresh((n) => n + 1);
                    })
                  }
                >
                  Kürzlich hinzugefügte Mods deaktivieren
                </GlassButton>
                <GlassButton
                  disabled={busy}
                  onClick={() => {
                    setCrash(null);
                    run("safe");
                  }}
                >
                  Getrennten Teststart öffnen
                </GlassButton>
              </div>
            </Modal>
          )}
        </div>
      </PreviewSuspendedContext.Provider>
    </ThemeContext.Provider>
  );
}
