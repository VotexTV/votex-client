import {
  useContext,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
} from "react";
import { ChevronDown, Globe2, Play, Settings2 } from "lucide-react";
import type {
  AppState,
  Profile,
} from "../../../../packages/shared-config/schema";
import { SkinPreview } from "./SkinPreview";
import { ThemeContext, Toggle } from "./Glass";
import { Select } from "./Select";
import { MessageText } from "./MessageText";
import type { HorizonRuntimeStatus, Job, NativeClientStatus, PlayHistory } from "../services/desktop";

const cubes = Array.from({ length: 22 }, (_, i) => ({
  x: (i * 37 + 8) % 98,
  y: (i * 61 + 5) % 96,
  size: 13 + ((i * 17) % 39),
  duration: 24 + (i % 8) * 4,
  delay: -(i * 3.7),
}));

/** A single character and a single launch decision. Extra controls stay in the picker. */
export function PlayStage({
  state,
  profile,
  history,
  accountSkin,
  accountModel,
  accountName,
  nativeClient,
  horizonRuntime,
  busy,
  native,
  job,
  launchMode,
  offlineName,
  onOfflineName,
  onLaunchMode,
  onRuntimeChange,
  onInstallNativeClient,
  onInstallHorizonRuntime,
  onAccount,
  onProfile,
  onIdle,
  onLaunch,
}: {
  state: AppState;
  profile: Profile;
  history: PlayHistory;
  accountSkin: string;
  accountModel: "default" | "slim";
  accountName?: string;
  nativeClient: NativeClientStatus;
  horizonRuntime: HorizonRuntimeStatus;
  busy: boolean;
  native: boolean;
  job: Job;
  launchMode: "microsoft" | "offline";
  offlineName: string;
  onOfflineName: (name: string) => void;
  onLaunchMode: (mode: "microsoft" | "offline") => void;
  onRuntimeChange: (runtime: AppState["gameRuntime"]) => void;
  onInstallNativeClient: () => void;
  onInstallHorizonRuntime: () => void;
  onAccount: () => void;
  onProfile: (id: string) => void;
  onIdle: (idle: boolean) => void;
  onLaunch: (profileId: string, serverAddress?: string) => void;
}) {
  const theme = useContext(ThemeContext),
    de = state.language === "de";
  const [target, setTarget] = useState<"profile" | "server">("profile");
  const [options, setOptions] = useState(false);
  const dock = useRef<HTMLDivElement>(null),
    picker = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!options) return;
    const outside = (event: PointerEvent) => {
      const target = event.target as Element;
      if (!dock.current?.contains(target) && !target.closest(".select-popover"))
        setOptions(false);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOptions(false);
        picker.current?.focus();
      }
    };
    document.addEventListener("pointerdown", outside);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("pointerdown", outside);
      document.removeEventListener("keydown", escape);
    };
  }, [options]);
  const selectedSkin = state.skins.find((s) => s.id === state.selectedSkin);
  const server = history.lastServer;
  const serverProfile = state.profiles.find((p) => p.id === server?.profileId);
  const joining = target === "server";
  const chosen = joining ? serverProfile : profile;
  const usingHorizon = chosen?.engine === "horizon";
  const usingPomme = !usingHorizon && state.gameRuntime === "pomme";
  const chosenEngine = chosen
    ? chosen.engine === "horizon"
      ? "Votex Engine"
      : chosen.loader === "vanilla"
        ? "Minecraft"
        : chosen.loader.charAt(0).toUpperCase() + chosen.loader.slice(1)
    : "Minecraft";
  const canLaunch =
    native &&
    !busy &&
    !!chosen &&
    (!joining || !!server) &&
    (usingHorizon
      ? !!accountName || /^[A-Za-z0-9_]{1,16}$/.test(offlineName.trim())
      : usingPomme
      ? nativeClient.installed && chosen.loader === "vanilla"
      : launchMode === "microsoft"
        ? !!accountName
        : /^[A-Za-z0-9_]{1,16}$/.test(offlineName.trim()));
  const title = joining
    ? server?.name ||
      server?.address ||
      (de ? "Noch kein Server besucht" : "No server visited yet")
    : profile.name;
  const detail = joining
    ? server?.address ||
      (de
        ? "Dein letzter Server erscheint hier nach dem Spielen."
        : "Your last server appears here after playing.")
    : `${chosenEngine} ${chosen?.minecraft ?? ""}${usingHorizon ? " · Rust" : usingPomme ? " · Pomme Native" : " · Java"}`;
  return (
    <section
      className="play-stage"
      aria-label={de ? "Minecraft spielen" : "Play Minecraft"}
      data-motion={
        !theme.reducedMotion &&
        theme.ambientMotion &&
        theme.renderProfile !== "ultra-performance" &&
        !busy
      }
    >
      <div className="play-cube-field" aria-hidden="true">
        {cubes.map((cube, i) => (
          <div
            className="play-cube-drift"
            key={i}
            style={
              {
                "--cube-x": `${cube.x}%`,
                "--cube-y": `${cube.y}%`,
                "--cube-size": `${cube.size}px`,
                "--cube-time": `${cube.duration}s`,
                "--cube-delay": `${cube.delay}s`,
                "--cube-alpha": 0.08 + (i % 4) * 0.045,
              } as CSSProperties
            }
          >
            <div className="play-cube">
              {Array.from({ length: 6 }, (_, face) => (
                <i key={face} />
              ))}
            </div>
          </div>
        ))}
      </div>
      <div className="play-motion-switch">
        <Toggle
          label={de ? "Skin-Animation" : "Skin animation"}
          value={state.idleSkin}
          onChange={onIdle}
        />
      </div>
      <div className="play-character">
        <SkinPreview
          skin={selectedSkin?.data || accountSkin || undefined}
          model={selectedSkin?.model || accountModel}
          idle={state.idleSkin}
          emotePlaying={false}
          look={state.cosmetics.find((x) => x.id === state.selectedCosmetic)}
          customCosmetics={state.customCosmetics}
          customEmotes={state.customEmotes}
          presentation={{ ...state.characterView, mode: "showcase" }}
          minimal
        />
      </div>
      <div className="play-launch-dock" ref={dock}>
        <div
          className="play-target-switch"
          role="group"
          aria-label={de ? "Startziel" : "Launch destination"}
        >
          <button aria-pressed={!joining} onClick={() => setTarget("profile")}>
            {de ? "Spielen" : "Play"}
          </button>
          <button aria-pressed={joining} onClick={() => setTarget("server")}>
            {de ? "Letzter Server" : "Last server"}
          </button>
        </div>
        <div className="play-launch-group">
          <button
            className="play-launch-primary"
            disabled={!canLaunch}
            onClick={() =>
              chosen &&
              onLaunch(chosen.id, joining ? server?.address : undefined)
            }
          >
            {joining ? (
              <Globe2 size={22} />
            ) : (
              <Play size={23} fill="currentColor" />
            )}
            <span>
              <strong>
                {busy
                  ? job.phase === "running"
                    ? de
                      ? "Minecraft läuft"
                      : "Minecraft running"
                    : de
                      ? "Wird vorbereitet …"
                      : "Preparing …"
                  : joining
                    ? "JOIN"
                    : "LAUNCH"}
              </strong>
              <small>{title}</small>
            </span>
          </button>
          <button
            className="play-launch-picker"
            ref={picker}
            aria-label={
              de
                ? "Profil und Startoptionen auswählen"
                : "Choose profile and launch options"
            }
            aria-expanded={options}
            disabled={busy}
            onClick={() => setOptions((x) => !x)}
          >
            <ChevronDown size={19} />
          </button>
        </div>
        <p className="play-launch-detail">{detail}</p>
        {options && (
          <div
            className="play-options"
            role="region"
            aria-label={de ? "Startoptionen" : "Launch options"}
          >
            <header>
              <Settings2 size={15} />
              {de ? "Startoptionen" : "Launch options"}
              <button aria-label="Schließen" onClick={() => setOptions(false)}>
                ×
              </button>
            </header>
            <label>
              {de ? "Minecraft-Profil" : "Minecraft profile"}
              <Select
                value={profile.id}
                onChange={(event) => {
                  onProfile(event.target.value);
                  setTarget("profile");
                }}
              >
                {state.profiles.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} · {p.minecraft}
                  </option>
                ))}
              </Select>
            </label>
            <label>
              {de ? "Spiel-Engine" : "Game runtime"}
              {usingHorizon ? (
                <div className="play-runtime-engine">Votex Engine · Minecraft 26.3</div>
              ) : (
                <Select
                  value={state.gameRuntime}
                  onChange={(event) =>
                    onRuntimeChange(event.target.value as AppState["gameRuntime"])
                  }
                >
                  <option value="java">
                    {de ? "Java · Fabric und Mods" : "Java · Fabric and mods"}
                  </option>
                  <option value="pomme">
                    {de
                      ? "Pomme · nativer Rust/Vulkan-Client"
                      : "Pomme · native Rust/Vulkan client"}
                  </option>
                </Select>
              )}
            </label>
            {usingHorizon ? (
              <div className="play-runtime-card">
                <strong>
                  {horizonRuntime.installed
                    ? `Horizon installiert · ${horizonRuntime.version}`
                  : "Votex Engine einrichten"}
                </strong>
                <p>
                  {de
                    ? "Horizon ist eine eigenständige Rust-Engine für Minecraft 26.3. Votex prüft die signierte Nightly-Version und bereitet beim ersten Start die Originaldateien vor. Ohne Microsoft-Konto startet Horizon mit deinem Offline-Spielernamen."
                    : "Horizon is a separate Rust engine for Minecraft 26.3. Votex verifies the signed nightly and prepares the original game files on first play. Horizon uses your offline player name when you are signed out."}
                </p>
                {horizonRuntime.reason && <p className="play-runtime-warning">{horizonRuntime.reason}</p>}
                {!accountName && (
                  <button className="btn" onClick={onAccount}>
                    {de ? "Microsoft-Konto verbinden" : "Connect Microsoft account"}
                  </button>
                )}
                <div className="play-runtime-actions">
                  <button className="btn" disabled={!native || busy} onClick={onInstallHorizonRuntime}>
                    {horizonRuntime.installed
                      ? de ? "Signierte Version prüfen / aktualisieren" : "Check / update signed version"
                      : de ? "Signiertes Horizon Nightly installieren" : "Install signed Horizon Nightly"}
                  </button>
                  <a href={horizonRuntime.sourceUrl} target="_blank" rel="noreferrer">
                    {de ? "Quellcode und Lizenz" : "Source and license"}{` · ${horizonRuntime.license}`}
                  </a>
                </div>
                {!native && <small>{de ? "Die Rust-Engine lässt sich nur in der installierten Votex-Desktop-App einrichten." : "Set up the Rust engine in the installed Votex desktop app."}</small>}
              </div>
            ) : usingPomme ? (
              <div className="play-runtime-card">
                <strong>
                  {nativeClient.installed
                    ? `${de ? "Pomme installiert" : "Pomme installed"} · ${nativeClient.version}`
                    : de
                      ? "Nativen Client einrichten"
                      : "Set up native client"}
                </strong>
                <p>
                  {de
                    ? "Pomme läuft getrennt von Java und meldet sich im eigenen Fenster mit Microsoft an. Java-Mods, Offline-Anmeldung sowie Votex-Cosmetics und Emotes werden dort nicht geladen."
                    : "Pomme runs separately from Java and signs in with Microsoft in its own window. Java mods, offline sign-in, and Votex cosmetics or emotes are not loaded there."}
                </p>
                {chosen?.loader !== "vanilla" && (
                  <p className="play-runtime-warning">
                    {de
                      ? "Dieses Profil nutzt einen Mod-Loader. Wähle für Pomme ein Vanilla-Profil; dein Java-Profil bleibt unverändert."
                      : "This profile uses a mod loader. Choose a Vanilla profile for Pomme; your Java profile remains unchanged."}
                  </p>
                )}
                {nativeClient.reason && (
                  <p className="play-runtime-warning">{nativeClient.reason}</p>
                )}
                <div className="play-runtime-actions">
                  <button
                    className="btn"
                    disabled={!native || busy}
                    onClick={onInstallNativeClient}
                  >
                    {nativeClient.installed
                      ? de
                        ? "Version prüfen / aktualisieren"
                        : "Check / update version"
                      : de
                        ? "Offizielle Version installieren"
                        : "Install official release"}
                  </button>
                  <a
                    href={nativeClient.sourceUrl}
                    target="_blank"
                    rel="noreferrer"
                  >
                    {de ? "Quellcode und Lizenz" : "Source and license"}
                    {` · ${nativeClient.license}`}
                  </a>
                </div>
                {!native && (
                  <small>
                    {de
                      ? "Die Runtime lässt sich nur in der installierten Votex-Desktop-App einrichten."
                      : "Install the Votex desktop app to set up this runtime."}
                  </small>
                )}
              </div>
            ) : (
              <>
                <label>
                  {de ? "Anmeldung" : "Sign-in"}
                  <Select
                    value={launchMode}
                    onChange={(event) =>
                      onLaunchMode(
                        event.target.value as "microsoft" | "offline",
                      )
                    }
                  >
                    <option value="microsoft">
                      {accountName
                        ? `Microsoft · ${accountName}`
                        : de
                          ? "Microsoft-Konto"
                          : "Microsoft account"}
                    </option>
                    <option value="offline">
                      {de ? "Offline-Modus" : "Offline mode"}
                    </option>
                  </Select>
                </label>
                {launchMode === "offline" ? (
                  <label>
                    {de ? "Spielername" : "Player name"}
                    <input
                      aria-label="Offline-Spielername"
                      value={offlineName}
                      maxLength={16}
                      autoComplete="off"
                      onChange={(event) => onOfflineName(event.target.value)}
                    />
                  </label>
                ) : (
                  !accountName && (
                    <button className="btn" onClick={onAccount}>
                      {de
                        ? "Microsoft-Konto verbinden"
                        : "Connect Microsoft account"}
                    </button>
                  )
                )}
              </>
            )}
          </div>
        )}
        {job.message && job.phase !== "idle" && (
          <div className="play-launch-progress" role="status">
            <MessageText
              value={job.message}
              language={state.language}
              diagnostics={job.phase === "error"}
            />
            {job.total > 0 && (
              <progress value={job.completed} max={job.total} />
            )}
          </div>
        )}
      </div>
    </section>
  );
}
