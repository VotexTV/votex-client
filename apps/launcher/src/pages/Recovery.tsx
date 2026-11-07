import { useEffect, useState } from "react";
import {
  ShieldCheck,
  Plus,
  RotateCcw,
  FileText,
  Trash2,
  HardDrive,
  Archive,
} from "lucide-react";
import {
  GlassPanel,
  GlassButton,
  PageHeading,
  Empty,
  Modal,
} from "../components/Glass";
import { call, native, type Snapshot } from "../services/desktop";
import type { PageProps } from "./types";
export function Recovery({
  state,
  act,
  busy,
  refresh,
  onRestored,
  onSafe,
}: { onRestored: () => Promise<void>; onSafe: () => void } & PageProps) {
  const [items, setItems] = useState<Snapshot[]>([]);
  const [bytes, setBytes] = useState(0);
  const [restore, setRestore] = useState<{
    item: Snapshot;
    changes: string[];
  } | null>(null);
  const [remove, setRemove] = useState<Snapshot | null>(null);
  const [log, setLog] = useState<string | null>(null);
  const p = state.activeProfile;
  const reload = async () => {
    const [a, b] = await Promise.all([
      call<Snapshot[]>("list_snapshots", { id: p }),
      call<{ bytes: number }>("backup_usage"),
    ]);
    setItems(a);
    setBytes(b.bytes);
  };
  useEffect(() => {
    if (native) void act(reload);
  }, [p, refresh]);
  const working = items.find((s) => s.validation === "confirmed-working");
  return (
    <>
      <PageHeading
        eyebrow="SICHERUNGEN"
        title="Wiederherstellung"
        description="Sicherungspunkte bewahren deine Mods und Einstellungen. Deine Spielwelten werden dabei nicht zurückgesetzt."
        action={
          <GlassButton
            className="primary"
            disabled={!native || busy}
            onClick={() =>
              void act(async () => {
                await call("create_snapshot", { id: p });
                await reload();
              })
            }
          >
            <Plus size={16} /> Jetzt Sicherungspunkt erstellen
          </GlassButton>
        }
      />
      <div className="recovery-guide">
        <article>
          <b>01</b>
          <h3>Stand sichern</h3>
          <p>
            Votex merkt sich deine Mods, Spieloptionen und Konfigurationen.
            Dateien werden gemeinsam platzsparend gespeichert.
          </p>
        </article>
        <article>
          <b>02</b>
          <h3>In Ruhe ausprobieren</h3>
          <p>
            Installiere Mods oder ändere Einstellungen. Bei einem Fehler kannst
            du zunächst ohne externe Mods testen.
          </p>
        </article>
        <article>
          <b>03</b>
          <h3>Änderungen prüfen</h3>
          <p>
            Wähle einen Punkt im Verlauf. Vor dem Wiederherstellen siehst du die
            Unterschiede und dein aktueller Stand wird gesichert.
          </p>
        </article>
      </div>
      <div className="recovery-summary">
        <GlassPanel>
          <ShieldCheck size={27} />
          <div>
            <h3>
              {working
                ? "Letzter funktionierender Stand geschützt"
                : "Noch kein bestätigter Spielstart"}
            </h3>
            <p>
              {working
                ? new Date(working.created).toLocaleString()
                : "Ein Stand gilt erst als bestätigt, wenn Core das Hauptmenü erkennt und Minecraft weitere 45 Sekunden läuft."}
            </p>
          </div>
        </GlassPanel>
        <GlassPanel>
          <HardDrive size={25} />
          <div>
            <h3>{(bytes / 1024 / 1024).toFixed(1)} MB</h3>
            <p>
              Sicherungsspeicher · gleiche Dateien werden nur einmal gespeichert
            </p>
          </div>
        </GlassPanel>
      </div>
      <GlassPanel className="recovery-actions">
        <div>
          <h3>Ein Mod verursacht Probleme?</h3>
          <p>
            Der sichere Teststart öffnet einen getrennten Spielordner ohne
            externe Mods. Dein normales Profil und deine Welten bleiben in ihrem
            bisherigen Ordner.
          </p>
        </div>
        <GlassButton disabled={!native || busy} onClick={onSafe}>
          Getrennten Teststart öffnen
        </GlassButton>
        <GlassButton
          disabled={!native}
          onClick={() =>
            void act(async () =>
              setLog(await call<string>("launch_log", { id: p })),
            )
          }
        >
          <FileText size={15} /> Startprotokoll
        </GlassButton>
      </GlassPanel>
      <div className="toolbar spread">
        <h3>
          <Archive size={17} /> Deine Sicherungspunkte
        </h3>
        <GlassButton
          disabled={!native || busy}
          onClick={() =>
            void act(async () => {
              await call("cleanup_backups");
              await reload();
            })
          }
        >
          Ungenutzten Sicherungsspeicher aufräumen
        </GlassButton>
      </div>
      {items.length ? (
        <GlassPanel className="snapshot-table">
          <table>
            <thead>
              <tr>
                <th>Zeitpunkt</th>
                <th>Version / Loader</th>
                <th>Inhalt</th>
                <th>Anlass & Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {items.map((s) => (
                <tr key={s.id}>
                  <td>
                    {new Date(s.created).toLocaleDateString()}
                    <small>{new Date(s.created).toLocaleTimeString()}</small>
                  </td>
                  <td>
                    {s.profile.minecraft}
                    <small>
                      {s.profile.loaderVersion ||
                        "Vanilla / noch nicht ermittelt"}
                    </small>
                  </td>
                  <td>
                    {
                      Object.keys(s.files).filter(
                        (n) => n.startsWith("mods/") && n.endsWith(".jar"),
                      ).length
                    }{" "}
                    Mods<small>{Object.keys(s.files).length} Dateien</small>
                  </td>
                  <td>
                    <span
                      className={`badge ${s.validation === "confirmed-working" ? "success" : ""}`}
                    >
                      {s.validation === "confirmed-working"
                        ? "Funktioniert · geschützt"
                        : s.validation === "failed"
                          ? "Start fehlgeschlagen"
                          : "Noch nicht bestätigt"}
                    </span>
                    <small>
                      {(
                        {
                          manual: "Von dir erstellt",
                          automatic: "Vor Spielstart",
                          "pre-mod-installation": "Vor Mod-Installation",
                          "pre-mod-transfer": "Vor Mod-Übernahme",
                          "pre-mod-change": "Vor Mod-Änderung",
                          "pre-mod-removal": "Vor Mod-Entfernung",
                          "pre-restore": "Vor Wiederherstellung",
                          "pre-core-installation": "Vor Core-Installation",
                        } as Record<string, string>
                      )[s.kind] ?? s.kind}
                    </small>
                  </td>
                  <td>
                    <button
                      disabled={busy}
                      className="icon-button"
                      title="Wiederherstellung prüfen"
                      onClick={() =>
                        void act(async () =>
                          setRestore({
                            item: s,
                            changes: await call<string[]>("preview_restore", {
                              id: p,
                              snapshot: s.id,
                            }),
                          }),
                        )
                      }
                    >
                      <RotateCcw size={16} />
                    </button>
                    <button
                      disabled={busy || working?.id === s.id}
                      className="icon-button danger"
                      title="Sicherungspunkt entfernen"
                      onClick={() => setRemove(s)}
                    >
                      <Trash2 size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </GlassPanel>
      ) : (
        <Empty title="Noch keine Sicherungspunkte vorhanden.">
          Erstelle einen Sicherungspunkt. Bei Mod-Änderungen und Spielstarts
          legt Votex auch automatisch welche an.
        </Empty>
      )}
      <p className="muted">
        Welten, Screenshots, Serverlisten und Zugangsdaten sind nicht
        Bestandteil dieser Sicherung. Entfernte oder ersetzte Mods bleiben im
        Sicherungsspeicher erhalten.
      </p>
      {restore && (
        <Modal
          title="Vor dem Wiederherstellen prüfen"
          onClose={() => setRestore(null)}
        >
          <p>
            Diese Dateien oder Einstellungen unterscheiden sich vom gewählten
            Sicherungspunkt:
          </p>
          <pre className="log">
            {restore.changes.length
              ? restore.changes.join("\n")
              : "Keine Unterschiede in der Konfiguration."}
          </pre>
          <p className="muted">
            Votex sichert zuerst den aktuellen Stand. Zusätzliche Dateien werden
            in den Sicherungsspeicher verschoben. Deine Welten bleiben
            unberührt.
          </p>
          <GlassButton
            className="primary"
            disabled={busy || !restore.changes.length}
            onClick={() =>
              void act(async () => {
                await call("restore_snapshot", {
                  id: p,
                  snapshot: restore.item.id,
                });
                await onRestored();
                await reload();
                setRestore(null);
              })
            }
          >
            Aktuellen Stand sichern & wiederherstellen
          </GlassButton>
        </Modal>
      )}
      {remove && (
        <Modal
          title="Sicherungspunkt entfernen?"
          onClose={() => setRemove(null)}
        >
          <p>
            Diesen Sicherungspunkt vom{" "}
            {new Date(remove.created).toLocaleString()} entfernen? Die
            Dateiinhalte bleiben bis zum Aufräumen erhalten. Der geschützte
            funktionierende Stand lässt sich nicht entfernen.
          </p>
          <GlassButton
            className="danger"
            onClick={() =>
              void act(async () => {
                await call("delete_snapshot", { id: p, snapshot: remove.id });
                await reload();
                setRemove(null);
              })
            }
          >
            Sicherungspunkt entfernen
          </GlassButton>
        </Modal>
      )}
      {log !== null && (
        <Modal title="Minecraft-Startprotokoll" onClose={() => setLog(null)}>
          <pre className="log">{log}</pre>
        </Modal>
      )}
    </>
  );
}
