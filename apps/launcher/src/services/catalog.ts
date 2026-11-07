import { call, native } from "./desktop";
export interface MinecraftVersion {
  id: string;
  type: string;
  releaseTime?: string;
}
export async function versionCatalog(): Promise<MinecraftVersion[]> {
  const data = native
    ? await call<{ versions: MinecraftVersion[] }>("list_versions")
    : await fetch(
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
      ).then((r) => {
        if (!r.ok)
          throw Error("Der Versionskatalog ist gerade nicht erreichbar.");
        return r.json();
      });
  return data.versions.slice().reverse();
}
export interface LoaderChoice {
  id: "vanilla" | "fabric" | "quilt" | "forge" | "neoforge";
  name: string;
  description: string;
  available: boolean;
  version?: string;
  reason?: string;
}
export async function loadersFor(minecraft: string): Promise<LoaderChoice[]> {
  return native
    ? call("loader_choices", { minecraft })
    : [
        {
          id: "vanilla",
          name: "Vanilla",
          description: "Das originale Minecraft ohne Mods.",
          available: true,
        },
        {
          id: "fabric",
          name: "Fabric",
          description:
            "Leichtgewichtiger Loader für Performance- und Client-Mods.",
          version: "Vorschau · Prüfung in der Desktop-App",
          available: true,
        },
        {
          id: "quilt",
          name: "Quilt",
          description: "Offener Modloader mit eigenem Mod-Ökosystem.",
          version: "Vorschau · Prüfung in der Desktop-App",
          available: true,
        },
        {
          id: "forge",
          name: "Forge",
          description: "Für viele umfangreiche Mods und Modpacks.",
          available: false,
          reason: "Verfügbarkeit wird in der Desktop-App geprüft.",
        },
        {
          id: "neoforge",
          name: "NeoForge",
          description: "Moderner Loader für das NeoForge-Ökosystem.",
          available: false,
          reason: "Verfügbarkeit wird in der Desktop-App geprüft.",
        },
      ];
}
