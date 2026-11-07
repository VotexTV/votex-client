import type { AppState } from "../../../../packages/shared-config/schema";
import { readData } from "./desktop";
export type LocalSkin = AppState["skins"][number];

export function skinPngBlob(data: string): Blob {
  const prefix="data:image/png;base64,";
  if(!data.startsWith(prefix)||data.length>1_500_000) throw new Error("Ungültige Skin-PNG-Daten.");
  const decoded=atob(data.slice(prefix.length));
  return new Blob([Uint8Array.from(decoded,char=>char.charCodeAt(0))],{type:"image/png"});
}

export async function checkSkinFile(file: Blob) {
  if (!file.size || file.size > 1_000_000)
    throw new Error("Wähle ein Skin-PNG bis 1 MB.");
  const bytes = new Uint8Array(await file.slice(0, 33).arrayBuffer());
  const signature = [137, 80, 78, 71, 13, 10, 26, 10];
  if (
    bytes.length < 33 ||
    signature.some((v, i) => bytes[i] !== v) ||
    String.fromCharCode(...bytes.slice(12, 16)) !== "IHDR"
  )
    throw new Error("Diese Datei enthält kein gültiges PNG-Bild.");
  const header = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (header.getUint32(16) !== 64 || ![32, 64].includes(header.getUint32(20)))
    throw new Error(
      "Bitte einen Minecraft-Skin als PNG mit 64 × 64 oder 64 × 32 Pixeln wählen.",
    );
}
export async function importSkin(file: File, id: string): Promise<LocalSkin> {
  await checkSkinFile(file);
  const data = await readData(
    file.slice(0, file.size, "image/png") as File,
    1_000_000,
  );
  const image = new Image();
  await new Promise<void>((resolve, reject) => {
    image.onload = () => resolve();
    image.onerror = () =>
      reject(
        new Error("Das Skin-PNG konnte nicht vollständig gelesen werden."),
      );
    image.src = data;
  });
  if (image.width !== 64 || ![32, 64].includes(image.height))
    throw new Error("Das Skin-Bild hat keine unterstützten Abmessungen.");
  return {
    id,
    name:
      file.name
        .replace(/\.png$/i, "")
        .trim()
        .slice(0, 60) || "Skin",
    data,
    model: "default",
  };
}
export function addSkin(
  state: AppState,
  skin: LocalSkin,
  activate = true,
): AppState {
  if (state.skins.length >= 100)
    throw new Error(
      "Deine Bibliothek enthält bereits 100 Skins. Entferne zuerst einen lokalen Skin.",
    );
  if (state.skins.some((s) => s.id === skin.id))
    throw new Error("Diese Skin-ID ist bereits in der Bibliothek vorhanden.");
  return {
    ...state,
    skins: [...state.skins, skin],
    selectedSkin: activate ? skin.id : state.selectedSkin,
  };
}
export function duplicateSkin(
  state: AppState,
  id: string,
  newId: string,
): AppState {
  const source = state.skins.find((s) => s.id === id);
  if (!source)
    throw new Error("Der ursprüngliche Skin ist nicht mehr vorhanden.");
  return addSkin(
    state,
    { ...source, id: newId, name: `${source.name.slice(0, 52)} (Kopie)` },
    false,
  );
}
export function removeSkin(state: AppState, id: string): AppState {
  return {
    ...state,
    skins: state.skins.filter((s) => s.id !== id),
    selectedSkin: state.selectedSkin === id ? "" : state.selectedSkin,
    cosmetics: state.cosmetics.map((look) =>
      look.skin === id ? { ...look, skin: "" } : look,
    ),
  };
}
