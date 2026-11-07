import { Select } from "../components/Select";
import { useEffect, useRef, useState } from "react";
import {
  Cpu,
  Check,
  SlidersHorizontal,
  Globe,
  KeyRound,
  FolderOpen,
  RotateCcw,
} from "lucide-react";
import {
  GlassPanel,
  GlassButton,
  PageHeading,
  Toggle,
} from "../components/Glass";
import { PerformanceCenter } from "../components/PerformanceCenter";
import { ProfileDownloads } from "../components/ProfileDownloads";
import { RangeField } from "../components/RangeField";
import { call, native } from "../services/desktop";
import type { PageProps } from "./types";
import {
  publisherClientId,
  type Profile,
} from "../../../../packages/shared-config/schema";
import {
  gamePresets,
  applyGamePreset,
  matchesGamePreset,
  resetGamePreset,
  saveGamePresetValues,
  rememberGameClass,
} from "../../../../packages/shared-config/performance";
export function Performance({
  state,
  update,
  act,
  busy,
  flush,
  refresh,
}: PageProps) {
  const p = state.profiles.find((x) => x.id === state.activeProfile)!;
  const rememberedFps = useRef<Record<string, number>>({});
  const [memoryInfo, setMemoryInfo] = useState<{
    totalMB: number;
    availableMB: number;
  } | null>(null);
  useEffect(() => {
    if (native)
      void act(async () => setMemoryInfo(await call("system_memory")));
  }, []);
  const [java, setJava] = useState(p.java),
    [result, setResult] = useState("");
  useEffect(() => {
    setJava(p.java);
    setResult("");
  }, [p.id]);
  const change = (patch: Partial<Profile>) =>
    update((s) => ({
      ...s,
      profiles: s.profiles.map((x) => (x.id === p.id ? { ...x, ...patch } : x)),
      gamePresetValues: rememberGameClass(s.gamePresetValues, {
        ...p,
        ...patch,
      }),
    }));
  const tune = (patch: Partial<Profile>) => {
    if (patch.graphics)
      patch = {
        ...patch,
        gameOptions: Object.fromEntries(
          Object.entries(p.gameOptions).filter(
            ([key]) => !["cutoutLeaves", "improvedTransparency"].includes(key),
          ),
        ),
      };
    change({
      ...saveGamePresetValues(p, patch),
      gameOptionsRevision: Object.keys(patch).some((k) =>
        [
          "renderDistance",
          "simulationDistance",
          "maxFps",
          "graphics",
          "vsync",
          "guiScale",
          "fov",
        ].includes(k),
      )
        ? p.gameOptionsRevision + 1
        : p.gameOptionsRevision,
    });
  };
  return (
    <>
      <PageHeading
        eyebrow="SPIELEINSTELLUNGEN"
        title="Leistung"
        description={`Spieleinstellungen für „${p.name}“. Änderungen gelten beim nächsten Start von Minecraft.`}
      />
      <div className="game-overview">
        <div>
          <small>SICHTWEITE</small>
          <strong>
            {p.renderDistance}
            <em>Chunks</em>
          </strong>
          <span>Sichtbare Welt</span>
        </div>
        <div>
          <small>SIMULATION</small>
          <strong>
            {p.simulationDistance}
            <em>Chunks</em>
          </strong>
          <span>Aktive Spielwelt</span>
        </div>
        <div>
          <small>ARBEITSSPEICHER</small>
          <strong>
            {p.memory / 1024}
            <em>GB</em>
          </strong>
          <span>
            {memoryInfo
              ? `${(memoryInfo.totalMB / 1024).toFixed(0)} GB im PC`
              : "Für Minecraft reserviert"}
          </span>
        </div>
        <div>
          <small>BILDRATE</small>
          <strong>
            {p.maxFps || "∞"}
            <em>{p.maxFps ? "FPS" : "Unbegrenzt"}</em>
          </strong>
          <span>
            {p.vsync ? "VSync eingeschaltet" : "Dein gesetztes Limit"}
          </span>
        </div>
      </div>
      <div className="visual-profile-grid game-presets">
        {gamePresets.map((preset) => (
          <button
            disabled={busy}
            key={preset.id}
            className={`glass quality-card luminous-card ${p.preset === preset.id ? "selected" : ""}`}
            onClick={() => change(applyGamePreset(p, preset.id))}
          >
            <Cpu size={23} />
            <strong>{preset.name}</strong>
            <span>{preset.hint}</span>
            <small>{preset.detail}</small>
            {p.preset === preset.id && <Check size={16} />}
          </button>
        ))}
      </div>
      <p className="quality-status">
        {p.preset === "custom"
          ? "Eigene Abstimmung · deine manuellen Werte sind aktiv."
          : !matchesGamePreset(p, p.preset)
            ? `${gamePresets.find((preset) => preset.id === p.preset)?.name ?? "Preset"} · deine angepassten Werte sind für dieses Profil gespeichert.`
            : "Eine Vorlage setzt die unten sichtbaren Spielwerte. Anpassungen werden für dieses Preset gespeichert."}
      </p>
      <div className="settings-grid">
        <GlassPanel className="control-panel">
          <h3>
            <SlidersHorizontal size={17} /> Welt & Darstellung
          </h3>
          <fieldset disabled={busy} className="plain-fieldset">
            <RangeField
              label="Sichtweite"
              min={2}
              max={32}
              value={p.renderDistance}
              unit=" Chunks"
              onChange={(renderDistance) => tune({ renderDistance })}
              hint="Die sichtbare Welt. Cinematic nutzt 32 Chunks für weite Panoramen. Auf Servern kann die tatsächliche Sichtweite begrenzt sein."
            />
            <RangeField
              label="Simulationsdistanz"
              min={2}
              max={32}
              value={p.simulationDistance}
              unit=" Chunks"
              onChange={(simulationDistance) => tune({ simulationDistance })}
              hint="Bereich, in dem die lokale Welt aktiv simuliert wird. Server können eigene Grenzen setzen."
            />
            <div className="fps-control">
              <Toggle
                label="Bildrate begrenzen"
                value={p.maxFps !== 0}
                onChange={(enabled) => {
                  if (!enabled) rememberedFps.current[p.id] = p.maxFps;
                  tune({
                    maxFps: enabled ? rememberedFps.current[p.id] || 144 : 0,
                  });
                }}
              />
              <RangeField
                label="Maximale Bildrate"
                min={10}
                max={250}
                step={1}
                value={p.maxFps || 144}
                unit=" FPS"
                disabled={p.maxFps === 0}
                displayValue={p.maxFps === 0 ? "Unbegrenzt" : undefined}
                onChange={(maxFps) => tune({ maxFps })}
                hint="Wähle auch exakt 120, 144 oder 165 FPS. Ein passendes Limit vermeidet unnötige GPU-Arbeit; ohne Limit rendert Minecraft so schnell wie möglich."
              />
            </div>
            <RangeField
              label="GUI-Skalierung im Spiel"
              min={0}
              max={8}
              step={1}
              value={p.guiScale}
              displayValue={
                p.guiScale === 0 ? "Automatisch" : String(p.guiScale)
              }
              onChange={(guiScale) => tune({ guiScale })}
            />
            <RangeField
              label="Sichtfeld (FOV)"
              min={30}
              max={110}
              step={1}
              value={p.fov}
              unit="°"
              onChange={(fov) => tune({ fov })}
            />
            <label className="form-stack">
              Grafikmodus
              <Select
                value={p.graphics}
                onChange={(e) =>
                  tune({ graphics: e.target.value as Profile["graphics"] })
                }
              >
                <option value="fast">Schnell</option>
                <option value="fancy">Schön</option>
                {p.graphics === "custom" && (
                  <option value="custom">Im Spiel angepasst</option>
                )}
                <option value="fabulous">
                  Fabelhaft (wenn die Version es unterstützt)
                </option>
              </Select>
            </label>
            <Toggle
              label="VSync · Bildrate mit dem Bildschirm synchronisieren"
              value={p.vsync}
              onChange={(vsync) => tune({ vsync })}
            />
          </fieldset>
          <p className="muted">
            Änderungen im Spiel werden nach dem Speichern automatisch hier
            übernommen. Neue Profile übernehmen deine letzte Abstimmung der
            gewählten Leistungsklasse.
          </p>
        </GlassPanel>
        <GlassPanel className="control-panel">
          <h3>Arbeitsspeicher & optionale Pakete</h3>
          {memoryInfo && (
            <p
              className={`memory-readout ${p.memory > memoryInfo.totalMB * 0.6 ? "warning" : ""}`}
            >
              {(memoryInfo.availableMB / 1024).toFixed(1)} GB gerade verfügbar ·{" "}
              {(memoryInfo.totalMB / 1024).toFixed(0)} GB insgesamt.
              {p.memory > memoryInfo.totalMB * 0.6
                ? " Deine Zuweisung lässt wenig Reserve für Windows. Weniger RAM wählen kann sinnvoll sein."
                : " Lass Reserven für Windows und andere Programme frei."}
            </p>
          )}
          <fieldset disabled={busy} className="plain-fieldset">
            <RangeField
              label="Minecraft-RAM"
              min={1024}
              max={16384}
              step={512}
              value={p.memory}
              unit=" MB"
              onChange={(memory) => tune({ memory })}
              hint="Lass RAM für Windows frei. Mehr RAM allein ist kein FPS-Upgrade."
            />
            <Toggle
              label="Performance-Mods vorbereiten"
              value={p.performanceMods}
              disabled={!["fabric", "quilt"].includes(p.loader)}
              onChange={(performanceMods) => tune({ performanceMods })}
            />
            <Toggle
              label={
                p.starterMods
                  ? "Ausgewählte Cinematic-Shader + Iris vorbereiten"
                  : "SoftLight-Shader + Iris vorbereiten"
              }
              value={p.shaderEnabled}
              disabled={p.loader !== "fabric"}
              onChange={(shaderEnabled) =>
                tune({
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
          </fieldset>
          <p className="muted">
            {p.starterMods
              ? "Deine Auswahl aus Basis-Mods, Performance-Paket, SMP-Erweiterungen und Shadern wird für Minecraft und Modloader geprüft. Du kannst sie am Profil bearbeiten. Bereits deaktivierte oder entfernte Pakete werden nicht bei jedem Start neu installiert."
              : "Beim Installieren prüft Votex passende Versionen von Sodium / Lithium und optional Iris mit SoftLight. Installierte Mods kannst du unter Mods einzeln deaktivieren."}
          </p>
          <GlassButton
            disabled={!native || busy}
            onClick={() =>
              void act(async () => {
                await flush?.();
                await call("prepare_profile", { id: p.id });
                setResult("Optionale Pakete wurden vorbereitet.");
              })
            }
          >
            Pakete jetzt vorbereiten
          </GlassButton>
          <ProfileDownloads id={p.id} refresh={refresh} />
        </GlassPanel>
        <GlassPanel className="control-panel">
          <details>
            <summary>Java-Laufzeit</summary>
            <label className="form-stack">
              Pfad zur java.exe
              <input
                disabled={busy}
                value={java}
                placeholder="Leer = passende Java-Version automatisch verwalten"
                onChange={(e) => setJava(e.target.value)}
              />
            </label>
            <div className="toolbar">
              <GlassButton disabled={busy} onClick={() => change({ java })}>
                Pfad speichern
              </GlassButton>
              <GlassButton
                disabled={!native || busy}
                onClick={() =>
                  void act(async () => {
                    if (!java.trim()) {
                      setResult(
                        "Automatische Verwaltung: Votex lädt die passende kostenlose Eclipse-Temurin-Laufzeit bei der Installation / beim ersten Start. System-Java bleibt unverändert.",
                      );
                      return;
                    }
                    const r = await call<{ version: string }>("check_java", {
                      path: java,
                    });
                    setResult(r.version);
                  })
                }
              >
                Java prüfen
              </GlassButton>
            </div>
            <p className="muted">
              Minecraft 26.2 benötigt Java 25. Votex prüft die Anforderung aus
              den offiziellen Versionsdaten.
            </p>
          </details>
          {result && <pre className="log small-log">{result}</pre>}
        </GlassPanel>
        <GlassPanel className="control-panel">
          <h3>Aktivität des Launchers</h3>
          <Toggle
            label="Beim Spielstart minimieren"
            value={state.minimizeOnLaunch}
            onChange={(minimizeOnLaunch) =>
              update((s) => ({ ...s, minimizeOnLaunch }))
            }
          />
          <Toggle
            label="Skin-Vorschau animieren"
            value={state.idleSkin}
            onChange={(idleSkin) => update((s) => ({ ...s, idleSkin }))}
          />
          <p className="muted">
            Unsichtbare 3D-Vorschauen pausieren. Glas, Leuchten und
            Näherungseffekte stellst du unabhängig davon im Design Studio ein.
          </p>
          <GlassButton
            disabled={busy}
            onClick={() =>
              change(
                resetGamePreset(
                  p,
                  p.preset === "custom" ? "balanced" : p.preset,
                ),
              )
            }
          >
            <RotateCcw size={14} /> Aktuelles Spielpreset zurücksetzen
          </GlassButton>
        </GlassPanel>
      </div>
      <PerformanceCenter profileId={p.id} profileName={p.name} act={act} />
    </>
  );
}
export function Settings({ state, update, act }: PageProps) {
  const [client, setClient] = useState(state.clientId);
  const de = state.language === "de";
  const [clientSaved, setClientSaved] = useState(false);
  useEffect(() => {
    setClient(state.clientId);
  }, [state.clientId]);
  return (
    <>
      <PageHeading
        eyebrow="VOTEX CLIENT"
        title="Einstellungen"
        description="Sprache, Darstellung und lokale App-Daten verwalten."
      />
      <div className="settings-grid">
        <GlassPanel className="control-panel">
          <h3>
            <Globe size={18} /> Sprache
          </h3>
          <Select
            aria-label="Sprache"
            value={state.language}
            onChange={(e) =>
              update((s) => ({ ...s, language: e.target.value as "en" | "de" }))
            }
          >
            <option value="en">English</option>
            <option value="de">Deutsch</option>
          </Select>
          <p className="muted">
            Die Oberfläche verwendet deutsche Beschriftungen. Technische Namen
            von Mods und Fehlermeldungen externer Dienste können in deren
            Originalsprache erscheinen.
          </p>
          <h3>Aufbewahrung deiner Sicherungen</h3>
          <RangeField
            label="Sicherungspunkte je Profil"
            min={2}
            max={100}
            value={state.retention}
            unit=""
            onChange={(retention) => update((s) => ({ ...s, retention }))}
          />
          <p className="muted">
            Ein Sicherungspunkt enthält deine Mods und Einstellungen. Deine
            Welten werden nicht zurückgesetzt. Der letzte bestätigte
            funktionierende Stand bleibt geschützt, auch über diese Anzahl
            hinaus. Unter Wiederherstellung siehst du die Inhalte vor dem
            Zurücksetzen.
          </p>
          <GlassButton
            onClick={() =>
              void act(() => call("open_data", { id: state.activeProfile }))
            }
          >
            <FolderOpen size={16} /> Aktiven Spielordner öffnen
          </GlassButton>
        </GlassPanel>
        <GlassPanel className="control-panel">
          <h3>
            <KeyRound size={18} />{" "}
            {de ? "Microsoft-Anmeldung einrichten" : "Microsoft sign-in setup"}
          </h3>
          <p className="muted">
            {publisherClientId
              ? de
                ? "Die Microsoft-App von Votex ist eingerichtet. Öffne oben dein Konto und wähle „Mit Microsoft anmelden“. Du brauchst keine eigene App-Registrierung."
                : "Votex's Microsoft application is configured. Open your account above and choose “Sign in with Microsoft”. You do not need your own app registration."
              : de
                ? "Votex braucht einmalig eine eigene Registrierung bei Microsoft. Die Client-ID ist die öffentliche Kennung des Launchers. Spieler benötigen danach nur ihr Microsoft-Konto."
                : "Votex needs its own Microsoft registration once. The client ID publicly identifies the launcher. After setup, players only need their Microsoft account."}
          </p>
          <details open={!publisherClientId}>
            <summary>
              {de
                ? "Erweiterte App-Konfiguration"
                : "Advanced application configuration"}
            </summary>
            <label className="form-stack">
              {de
                ? "Öffentliche Application-/Client-ID"
                : "Public Application (client) ID"}
              <input
                value={client}
                maxLength={80}
                placeholder="00000000-0000-0000-0000-000000000000"
                onChange={(e) => {
                  setClient(e.target.value);
                  setClientSaved(false);
                }}
              />
            </label>
            <GlassButton
              onClick={() =>
                void act(async () => {
                  const id = client.trim();
                  if (
                    id &&
                    (!/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(
                      id,
                    ) ||
                      id === "00000000-0000-0000-0000-000000000000")
                  )
                    throw new Error(
                      de
                        ? "Bitte eine gültige öffentliche Microsoft-Client-ID eintragen."
                        : "Enter a valid public Microsoft Application ID",
                    );
                  update((s) => ({ ...s, clientId: id || publisherClientId }));
                  setClient(id || publisherClientId);
                  setClientSaved(true);
                })
              }
            >
              {de ? "Client-ID übernehmen" : "Save application ID"}
            </GlassButton>
            {clientSaved && (
              <p role="status">
                {de
                  ? "Client-ID übernommen. Öffne jetzt oben dein Konto und wähle „Mit Microsoft anmelden“."
                  : "Application ID updated. Open your account above and choose “Sign in with Microsoft”."}
              </p>
            )}
            <p>
              <a
                href="https://entra.microsoft.com/"
                target="_blank"
                rel="noreferrer"
                onClick={(e) => {
                  if (native) {
                    e.preventDefault();
                    void act(() => call("open_microsoft_setup"));
                  }
                }}
              >
                {de
                  ? "Microsoft-App-Registrierung öffnen"
                  : "Open Microsoft app registration"}
              </a>
            </p>
            <ol className="auth-setup-steps">
              <li>
                {de
                  ? "App-Registrierungen → Neue Registrierung → Name: Votex Client."
                  : "App registrations → New registration → Name: Votex Client."}
              </li>
              <li>
                {de
                  ? "Persönliche Microsoft-Konten unterstützen und den öffentlichen Client-/Gerätecodefluss aktivieren."
                  : "Support personal Microsoft accounts and enable public-client/device-code flows."}
              </li>
              <li>
                {de
                  ? "Application-/Client-ID von der Übersichtsseite hier eintragen. Kein Client-Secret erstellen."
                  : "Copy the Application (client) ID from Overview here. Do not create a client secret."}
              </li>
            </ol>
            <p className="notice">
              {de
                ? "Die hinterlegte Client-ID ist öffentlich und kein Passwort. Minecraft kann zusätzlich eine API-Freigabe für den Herausgeber verlangen; eine Ablehnung wird beim Login angezeigt."
                : "The configured client ID is public and is not a password. Minecraft may require separate publisher API approval; any rejection is shown during sign-in."}
            </p>
          </details>
        </GlassPanel>
      </div>
    </>
  );
}
