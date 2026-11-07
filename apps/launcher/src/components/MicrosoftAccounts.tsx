import { useEffect, useRef, useState } from "react";
import {
  Check,
  Copy,
  ExternalLink,
  LogIn,
  LogOut,
  ShieldCheck,
} from "lucide-react";
import { GlassButton, Modal } from "./Glass";
import { call, native, type Accounts, type Account } from "../services/desktop";
import { codeTime, type DeviceLogin } from "../services/auth";
import { errorText } from "../services/messages";
import { MessageText } from "./MessageText";
import { OfflineIdentity } from "./OfflineIdentity";

interface Props {
  offlineName: string;
  onOfflineName: (name: string) => Promise<void>;
  accounts: Accounts;
  clientId: string;
  de: boolean;
  busy: boolean;
  flush: () => Promise<void>;
  onRefresh: () => Promise<void>;
  onSettings: () => void;
  onClose: () => void;
  onBusyChange: (busy: boolean) => void;
  report: (message: string) => void;
}

export function MicrosoftAccounts({
  offlineName,
  onOfflineName,
  accounts,
  clientId,
  de,
  busy,
  flush,
  onRefresh,
  onSettings,
  onClose,
  onBusyChange,
  report,
}: Props) {
  const [session, setSession] = useState<
    (DeviceLogin & { expiresAt: number }) | null
  >(null);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState("");
  const [remaining, setRemaining] = useState(0);
  const [copied, setCopied] = useState(false);
  const [signOut, setSignOut] = useState<Account | null>(null);
  const active = useRef<string | null>(null);
  const generation = useRef(0);
  const mounted = useRef(true);
  const running = useRef(false);

  useEffect(() => {
    mounted.current = true;
    if (native)
      void onRefresh().catch((e) => {
        if (mounted.current) setError(errorText(e));
      });
    return () => {
      mounted.current = false;
      generation.current++;
      onBusyChange(false);
      if (active.current)
        void call("cancel_login", { sessionId: active.current }).catch(
          () => {},
        );
    };
  }, []);
  useEffect(() => {
    if (!session) return;
    const tick = () =>
      setRemaining(
        Math.max(0, Math.ceil((session.expiresAt - Date.now()) / 1000)),
      );
    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [session]);

  const close = async () => {
    try {
      // Acknowledged cancellation prevents a hidden login from saving an account later.
      if (active.current)
        await call("cancel_login", { sessionId: active.current });
      generation.current++;
      active.current = null;
      onBusyChange(false);
      onClose();
    } catch (e) {
      setError(errorText(e));
    }
  };
  const start = async () => {
    if (running.current || busy) return;
    if (!clientId) {
      onSettings();
      return;
    }
    running.current = true;
    const attempt = ++generation.current;
    setWorking(true);
    onBusyChange(true);
    setError("");
    setCopied(false);
    const current = () => mounted.current && attempt === generation.current;
    try {
      await flush();
      if (!current()) return;
      const code = await call<DeviceLogin>("start_login");
      if (!current()) {
        await call("cancel_login", { sessionId: code.sessionId });
        return;
      }
      active.current = code.sessionId;
      setSession({ ...code, expiresAt: Date.now() + code.expiresIn * 1000 });
      try {
        await call("open_login", { sessionId: code.sessionId });
      } catch {
        if (current())
          setError(
            de
              ? "Die Microsoft-Seite konnte nicht geöffnet werden. Nutze den Button erneut oder öffne die angezeigte Adresse selbst."
              : "Could not open Microsoft. Try the button again or open the displayed address yourself.",
          );
      }
      if (!current()) return;
      await call("finish_login", { sessionId: code.sessionId });
      if (!current()) return;
      active.current = null;
      setSession(null);
      await onRefresh();
      if (current()) {
        setError("");
        report(
          de
            ? "Minecraft-Konto mit Microsoft bestätigt und sicher gespeichert."
            : "Microsoft Minecraft account verified and saved securely.",
        );
      }
    } catch (e) {
      if (current()) {
        setSession(null);
        setError(errorText(e));
      }
    } finally {
      if (current()) {
        const id = active.current;
        active.current = null;
        if (id) void call("cancel_login", { sessionId: id }).catch(() => {});
        running.current = false;
        setWorking(false);
        onBusyChange(false);
      }
    }
  };
  const action = async (fn: () => Promise<void>) => {
    if (working || busy) return;
    setWorking(true);
    setError("");
    try {
      await fn();
    } catch (e) {
      if (mounted.current) setError(errorText(e));
    } finally {
      if (mounted.current) setWorking(false);
    }
  };

  return (
    <Modal
      title={
        session
          ? de
            ? "Mit Microsoft anmelden"
            : "Sign in with Microsoft"
          : de
            ? "Minecraft-Konten"
            : "Minecraft accounts"
      }
      onClose={() => void close()}
    >
      {error && (
        <div className="notice auth-error" role="alert">
          <MessageText value={error} language={de ? "de" : "en"} diagnostics />
        </div>
      )}
      {session ? (
        <>
          <p>
            {de
              ? "1. Öffne die Microsoft-Seite im Browser.\n2. Gib dort diesen Code ein.\n3. Melde dich mit dem Microsoft-Konto an, dem Minecraft Java gehört."
              : "1. Open Microsoft in your browser.\n2. Enter this code there.\n3. Sign in with the Microsoft account that owns Minecraft Java."}
          </p>
          <div
            className="device-code"
            aria-label={de ? "Microsoft-Anmeldecode" : "Microsoft sign-in code"}
          >
            {session.userCode}
          </div>
          <div className="toolbar">
            <GlassButton
              onClick={() =>
                void navigator.clipboard
                  .writeText(session.userCode)
                  .then(() => setCopied(true))
                  .catch(() =>
                    setError(
                      de
                        ? "Bitte markiere den Code und kopiere ihn mit Strg+C."
                        : "Select the code and copy it with Ctrl+C.",
                    ),
                  )
              }
            >
              {copied ? <Check size={16} /> : <Copy size={16} />}
              {copied
                ? de
                  ? "Kopiert"
                  : "Copied"
                : de
                  ? "Code kopieren"
                  : "Copy code"}
            </GlassButton>
            <GlassButton
              onClick={() =>
                void call("open_login", { sessionId: session.sessionId }).catch(
                  (e) => setError(errorText(e)),
                )
              }
            >
              <ExternalLink size={16} />
              {de ? "Microsoft-Seite öffnen" : "Open Microsoft"}
            </GlassButton>
          </div>
          <p className="muted auth-address">{session.verificationUri}</p>
          <p role="status">
            {de
              ? "Warte auf Microsoft-Bestätigung. Verbleibende Zeit: "
              : "Waiting for Microsoft approval. Time remaining: "}
            <strong>{codeTime(remaining)}</strong>
          </p>
          <GlassButton onClick={() => void close()}>
            {de ? "Anmeldung abbrechen" : "Cancel sign-in"}
          </GlassButton>
        </>
      ) : signOut ? (
        <>
          <p>
            {de
              ? `${signOut.name} auf diesem Gerät abmelden? Der gespeicherte Microsoft-Anmeldetoken wird entfernt. Deine Spielstände bleiben erhalten.`
              : `Sign out ${signOut.name} on this device? The saved Microsoft credential will be removed. Your game files stay available.`}
          </p>
          <div className="toolbar">
            <GlassButton
              disabled={working || busy}
              className="danger"
              onClick={() =>
                void action(async () => {
                  await call("remove_account", { id: signOut.id });
                  await onRefresh();
                  setSignOut(null);
                  report(
                    de
                      ? "Minecraft-Konto auf diesem Gerät abgemeldet."
                      : "Minecraft account signed out on this device.",
                  );
                })
              }
            >
              <LogOut size={16} />
              {de ? "Abmelden" : "Sign out"}
            </GlassButton>
            <GlassButton disabled={working} onClick={() => setSignOut(null)}>
              {de ? "Zurück" : "Back"}
            </GlassButton>
          </div>
        </>
      ) : (
        <>
          <OfflineIdentity name={offlineName} de={de} onSave={onOfflineName}/>
          <p className="auth-intro">
            <ShieldCheck size={20} />
            {de
              ? "Du meldest dich direkt bei Microsoft im Browser an. Votex übernimmt anschließend dein geprüftes Minecraft-Java-Profil."
              : "Sign in directly with Microsoft in your browser. Votex then verifies and connects your Minecraft Java profile."}
          </p>
          {accounts.items.map((account) => (
            <div className="account-entry" key={account.id}>
              <button
                className="account-row"
                disabled={working || busy}
                onClick={() =>
                  void action(async () => {
                    await call("select_account", { id: account.id });
                    await onRefresh();
                    onClose();
                  })
                }
              >
                <span className="avatar">{account.name[0]}</span>
                <div>
                  <strong>{account.name}</strong>
                  <small>{account.id}</small>
                </div>
                {account.id === accounts.selected && <Check size={17} />}
              </button>
              <button
                className="icon-button"
                disabled={working || busy}
                aria-label={`${de ? "Abmelden" : "Sign out"}: ${account.name}`}
                title={
                  de ? "Auf diesem Gerät abmelden" : "Sign out on this device"
                }
                onClick={() => setSignOut(account)}
              >
                <LogOut size={18} />
              </button>
            </div>
          ))}
          {!native && (
            <p className="notice">
              {de
                ? "Die Anmeldung ist in der Windows-Anwendung verfügbar. Diese Browseransicht ist eine Vorschau."
                : "Sign-in is available in the Windows app. This browser view is a preview."}
            </p>
          )}
          {busy && (
            <p className="notice">
              {de
                ? "Beende Minecraft und laufende Downloads, bevor du Konten änderst."
                : "Stop Minecraft and downloads before changing accounts."}
            </p>
          )}
          <GlassButton
            className="primary"
            disabled={working || busy || (!native && !!clientId)}
            onClick={() => void start()}
          >
            <LogIn size={17} />
            {working
              ? de
                ? "Microsoft-Anmeldung wird vorbereitet…"
                : "Preparing Microsoft sign-in…"
              : !clientId
                ? de
                  ? "Microsoft-Anmeldung einrichten"
                  : "Set up Microsoft sign-in"
                : de
                  ? "Mit Microsoft anmelden"
                  : "Sign in with Microsoft"}
          </GlassButton>
          {!clientId && (
            <p className="notice">
              {de
                ? "Die einmalige Microsoft-App-Registrierung für Votex fehlt noch. Sobald der Herausgeber sie eingerichtet hat, brauchen Spieler nur ihr Microsoft-Konto."
                : "Votex's publisher must finish its one-time Microsoft application registration. Players then only need their Microsoft account."}
            </p>
          )}
          <p className="muted">
            {de
              ? "Votex fragt nicht nach deinem Passwort. Deine Anmeldung wird im Windows-Anmeldeinformationsspeicher gesichert."
              : "Votex never asks for your password. Your saved sign-in uses Windows Credential Manager."}
          </p>
        </>
      )}
    </Modal>
  );
}
