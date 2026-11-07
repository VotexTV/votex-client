import { useEffect, useState } from "react";
import { ArrowRight, Check, Copy, AlertCircle } from "lucide-react";
import type { Profile } from "../../../../packages/shared-config/schema";
import { call, native } from "../services/desktop";
import { Modal, GlassButton } from "./Glass";
export interface TransferReview {
  token: string;
  target: Profile;
  files: number;
  skipped: number;
  rows: { name: string; action: string; detail: string; enabled?: boolean }[];
}
export function ModTransfer({
  source,
  target,
  create = false,
  onClose,
  onDone,
  flush,
  act,
}: {
  source: Profile;
  target: Profile;
  create?: boolean;
  onClose: () => void;
  onDone: () => Promise<void>;
  flush?: () => Promise<void>;
  act: <T>(fn: () => Promise<T>) => Promise<T | undefined>;
}) {
  const [plan, setPlan] = useState<TransferReview | null>(null),
    [loading, setLoading] = useState(false),
    [working, setWorking] = useState(false),
    [error, setError] = useState("");
  const check = async () => {
    setLoading(true);
    setPlan(null);
    setError("");
    try {
      await flush?.();
      setPlan(
        await call("plan_mod_transfer", { source: source.id, target, create }),
      );
    } catch (e) {
      setError(String(e).replace(/^Error:\s*/, ""));
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    if (native) void check();
  }, []);
  return (
    <Modal
      title={
        create
          ? "Profil mit Mods erstellen"
          : "Mods in ein anderes Profil übernehmen"
      }
      onClose={() => {
        if (!working) onClose();
      }}
    >
      <div className="transfer-route">
        <div>
          <small>QUELLE · BLEIBT ERHALTEN</small>
          <strong>{source.name}</strong>
          <span>
            {source.loader} · {source.minecraft}
          </span>
        </div>
        <ArrowRight />
        <div>
          <small>{create ? "NEUES PROFIL" : "ZIELPROFIL"}</small>
          <strong>{target.name}</strong>
          <span>
            {target.loader} · {target.minecraft}
          </span>
        </div>
      </div>
      <p className="muted">
        Votex sucht passende Modrinth-Versionen und benötigte Abhängigkeiten.
        Nur Mod-Dateien werden übernommen. Welten und eigene Konfigurationen
        bleiben in ihrem Profil.
      </p>
      {!native && (
        <p className="notice">
          Die Datei- und Versionsprüfung läuft in der Windows-App. Im Browser
          werden keine installierten Mods vorgetäuscht.
        </p>
      )}
      {loading && (
        <p role="status">Dateien erkennen und Zielversionen prüfen …</p>
      )}
      {error && (
        <p className="notice danger" role="alert">
          {error}
        </p>
      )}
      {plan && (
        <>
          <div className="transfer-summary">
            <b>{plan.files} Dateien vorbereitet</b>
            <span>{plan.skipped} ausgelassen</span>
          </div>
          <div className="transfer-list">
            {plan.rows.map((r, i) => (
              <div
                className={`transfer-row ${r.action === "skip" ? "skipped" : ""}`}
                key={i}
              >
                {r.action === "skip" ? (
                  <AlertCircle size={16} />
                ) : (
                  <Check size={16} />
                )}
                <div>
                  <strong>{r.name}</strong>
                  <small>
                    {r.detail}
                    {r.enabled === false ? " · bleibt deaktiviert" : ""}
                  </small>
                </div>
                <span className="badge">
                  {
                    {
                      copy: "Passend",
                      update: "Update",
                      local: "Lokale Kopie",
                      dependency: "Abhängigkeit",
                      skip: "Ausgelassen",
                    }[r.action]
                  }
                </span>
              </div>
            ))}
          </div>
          {!plan.rows.length && <p>Das Quellprofil enthält noch keine Mods.</p>}
          <p className="notice">
            {create
              ? "Ein eigener Spielordner wird erstellt."
              : "Vor Änderungen wird ein Sicherungspunkt des Zielprofils erstellt."}{" "}
            Bei geänderten Dateien muss die Prüfung wiederholt werden. Die
            Prüfung bestätigt veröffentlichte Kompatibilitätsangaben; ein
            Spielstart bleibt der praktische Test.
          </p>
        </>
      )}
      <div className="wizard-footer">
        <GlassButton disabled={working} onClick={onClose}>
          Abbrechen
        </GlassButton>
        {native && !plan && !loading && (
          <GlassButton onClick={() => void check()}>Erneut prüfen</GlassButton>
        )}
        <GlassButton
          className="primary"
          disabled={!plan || loading || working || (!create && !plan.files)}
          onClick={() =>
            void act(async () => {
              setWorking(true);
              try {
                await call("commit_mod_transfer", { token: plan!.token });
                await onDone();
              } catch (e) {
                setError(String(e));
                setPlan(null);
              } finally {
                setWorking(false);
              }
            })
          }
        >
          <Copy size={16} />
          {working
            ? "Wird sicher übernommen …"
            : create
              ? "Profil & Mods erstellen"
              : "Geprüfte Mods übernehmen"}
        </GlassButton>
      </div>
    </Modal>
  );
}
