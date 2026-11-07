import { invoke, isTauri } from "@tauri-apps/api/core";
import {
  beginInstallation,
  finishInstallation,
  installationFor,
} from "./installations";
export const native = isTauri();
export async function call<T>(
  command: string,
  args: Record<string, unknown> = {},
  feedbackTitle?: string,
): Promise<T> {
  if (!native)
    throw new Error(
      "Available in the Windows desktop app. This browser preview only stores design, skin and profile settings locally.",
    );
  const title = installationFor(command, args)
    ? (feedbackTitle ?? installationFor(command, args))
    : undefined;
  const id = title
    ? beginInstallation(title, command === "run_game")
    : undefined;
  try {
    const result = await invoke<T>(command, args);
    if (id !== undefined && command !== "run_game") {
      let skipped = 0;
      if (command === "prepare_profile") {
        const report = await invoke<{ results?: { status: string }[] } | null>(
          "profile_downloads",
          { id: args.id },
        ).catch(() => null);
        skipped =
          report?.results?.filter((r) => r.status === "unavailable").length ??
          0;
      }
      finishInstallation(
        id,
        skipped ? "warning" : "success",
        skipped
          ? `${skipped} optionale Pakete konnten nicht installiert werden. Details stehen im Download-Bericht.`
          : `${title} abgeschlossen. Beim nächsten Spielstart verfügbar.`,
      );
    }
    return result;
  } catch (error) {
    if (id !== undefined) finishInstallation(id, "error", String(error));
    throw error;
  }
}
export function exportJson(name: string, value: unknown) {
  const blob = new Blob([JSON.stringify(value, null, 2)], {
    type: "application/json",
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export async function readJson(file: File): Promise<unknown> {
  if (file.size > 10_000_000) throw new Error("JSON file is too large");
  const text = await file.text();
  try {
    return JSON.parse(text) as unknown;
  } catch (error) {
    throw new Error(
      `INVALID_JSON: ${error instanceof Error ? error.message : String(error)}`,
    );
  }
}
export function readData(file: File, max = 5_000_000): Promise<string> {
  if (file.size > max) return Promise.reject(new Error("Image is too large"));
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error("Could not read file"));
    reader.readAsDataURL(file);
  });
}
export async function readVideoData(file: File, max = 5_000_000) {
  if (file.size <= 0 || file.size > max)
    throw new Error("Das Video überschreitet das 5-MB-Limit oder ist leer.");
  const mime = file.type.split(";")[0].toLowerCase();
  if (mime !== "video/mp4" && mime !== "video/webm")
    throw new Error("Unterstützt werden MP4- und WebM-Videos.");
  const head = new Uint8Array(await file.slice(0, 16).arrayBuffer());
  const isMp4 =
    mime === "video/mp4" &&
    head.length >= 8 &&
    String.fromCharCode(...head.slice(4, 8)) === "ftyp";
  const isWebm =
    mime === "video/webm" &&
    head.length >= 4 &&
    head[0] === 0x1a &&
    head[1] === 0x45 &&
    head[2] === 0xdf &&
    head[3] === 0xa3;
  if (!isMp4 && !isWebm)
    throw new Error("Die Videodatei passt nicht zu ihrem MP4-/WebM-Format.");
  return readData(file, max);
}
export function uid() {
  return crypto.randomUUID();
}
export interface Job {
  phase: string;
  message: string;
  completed: number;
  total: number;
}
export interface PlayHistory {
  lastProfileId?: string;
  lastServer?: {
    name: string;
    address: string;
    profileId: string;
    joinedAt?: string;
  };
}
export interface NativeClientStatus {
  installed: boolean;
  version: string | null;
  releaseTag: string | null;
  license: string;
  sourceUrl: string;
  reason: string | null;
}
export interface HorizonRuntimeStatus {
  installed: boolean;
  version: string | null;
  releaseTag: string | null;
  license: string;
  sourceUrl: string;
  reason: string | null;
}
export interface Account {
  id: string;
  name: string;
  skin: string;
  model: string;
}
export interface Accounts {
  selected: string;
  items: Account[];
}
export interface Snapshot {
  id: string;
  created: string;
  profile: { name: string; minecraft: string; loaderVersion: string };
  kind: string;
  validation: string;
  files: Record<string, string>;
}
export interface Mod {
  displayName?: string | null;
  modId?: string | null;
  icon?: string | null;
  filename: string;
  enabled: boolean;
  metadata: null | { name: string; version_number: string; project_id: string };
}
export interface Hit {
  project_id: string;
  slug: string;
  title: string;
  description: string;
  icon_url: string;
  downloads: number;
  author: string;
  versions: string[];
}
