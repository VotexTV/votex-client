import type { Job } from "./desktop";

export interface Installation {
  id: number;
  title: string;
  detail: string;
  state: "working" | "success" | "warning" | "error";
  progress?: number;
  background?: boolean;
  warning?: string;
}
let serial = 0;
let rows: Installation[] = [];
const listeners = new Set<() => void>();
let background: number | undefined;
const emit = () => listeners.forEach((f) => f());
export const installationSnapshot = () => rows;
export function subscribeInstallations(f: () => void) {
  listeners.add(f);
  return () => {
    listeners.delete(f);
  };
}
export function beginInstallation(title: string, backgroundJob = false) {
  const id = ++serial;
  rows = [
    ...rows.filter((r) => r.state === "working").slice(-3),
    {
      id,
      title,
      detail: "Dateien und Abhängigkeiten werden geprüft …",
      state: "working" as const,
      background: backgroundJob,
    },
  ];
  if (backgroundJob) background = id;
  emit();
  return id;
}
export function updateInstallation(
  id: number,
  detail: string,
  progress?: number,
) {
  rows = rows.map((r) =>
    r.id === id && r.state === "working"
      ? {
          ...r,
          detail,
          progress:
            progress === undefined || !Number.isFinite(progress)
              ? undefined
              : Math.max(0, Math.min(1, progress)),
        }
      : r,
  );
  emit();
}
export function finishInstallation(
  id: number,
  state: "success" | "warning" | "error",
  detail: string,
) {
  rows = rows.map((r) =>
    r.id === id && r.state === "working"
      ? {
          ...r,
          state: state === "success" && r.warning ? "warning" : state,
          detail:
            state === "success" && r.warning
              ? `${detail} ${r.warning}`
              : detail,
          // A warning can still mean the requested work is finished (for
          // example, when an optional starter mod has no compatible release).
          // Keep the completion animation honest instead of leaving a partial
          // progress bar on a terminal warning state.
          progress: state === "error" ? undefined : 1,
        }
      : r,
  );
  if (background === id) background = undefined;
  emit();
}
export function dismissInstallation(id: number) {
  rows = rows.filter((r) => r.id !== id || r.state === "working");
  emit();
}
export function installationFor(
  command: string,
  args: Record<string, unknown>,
): string | undefined {
  const names: Record<string, string> = {
    install_content:
      args.kind === "shader"
        ? "Shader installieren"
        : args.kind === "resourcepack"
          ? "Ressourcenpaket installieren"
          : args.kind === "modpack"
            ? "Modpack installieren"
            : "Mods installieren",
    install_mod: "Mods installieren",
    install_core: "Votex Core installieren",
    install_native_client: "Nativen Rust-Client installieren",
    import_modpack: "Modpack importieren",
    prepare_profile: "Profilpakete vorbereiten",
  };
  return command === "run_game" && args.action === "install"
    ? "Minecraft installieren / prüfen"
    : names[command];
}
/** IPC progress does not certify completion of an awaited command or an intermediate launch step. */
export function installationJob(job: Job) {
  const active = [...rows].reverse().find((r) => r.state === "working");
  const transfer = ["installing", "mods"].includes(job.phase);
  let id = active?.id;
  if (id === undefined && transfer)
    id = beginInstallation("Spielpakete vorbereiten", true);
  if (id === undefined) return;
  if (transfer)
    updateInstallation(
      id,
      job.message,
      job.total > 0 ? job.completed / job.total : undefined,
    );
  else if (job.phase.endsWith("-warning")) {
    rows = rows.map((r) =>
      r.id === id ? { ...r, detail: job.message, warning: job.message } : r,
    );
    emit();
  } else if (id === background) {
    if (job.phase === "error") finishInstallation(id, "error", job.message);
    else if (["installed", "starting", "running", "idle"].includes(job.phase))
      finishInstallation(id, "success", "Dateien geprüft und bereit.");
  }
}
