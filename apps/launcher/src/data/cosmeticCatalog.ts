import index from "./cosmetic-manifest.json";
export const cosmeticSlots = {
  wings: "Flügel",
  head: "Kopf",
  face: "Gesicht",
  pet: "Begleiter",
  back: "Rücken",
  aura: "Aura",
  overlay: "Outfits",
  feet: "Schuhe",
  cape: "Capes",
} as const;
export type CosmeticSlot = keyof typeof cosmeticSlots;
export interface CosmeticItem {
  id: string;
  name: string;
  slot: CosmeticSlot;
  description: string;
  collection: string;
  capeTexture?: string;
  creator?: { name: string; url: string; license: string; licenseUrl: string; changes: string };
}
export interface SurfacePart {
  joint: "head" | "body" | "leftArm" | "rightArm" | "leftLeg" | "rightLeg";
  positions: number[];
  normals: number[];
  colors: number[];
  quads: number[];
  texture?: string;
  uvs?: number[];
  material: {
    color: string;
    metalness: number;
    roughness: number;
    opacity: number;
  };
  motion?:
    | { type: "wing"; side: number; axis?: "x" | "y" | "z"; pivot: [number, number, number]; amplitude?: number; speed?: number; phase?: number; secondary?: number; secondaryAxis?: "x" | "y" | "z"; secondaryPivot?: [number, number, number] }
    | { type: "oscillate"; axis: "x" | "y" | "z"; amplitude: number; speed: number; phase: number; pivot: [number, number, number]; duration?:number;keyframes?:{time:number;angle:number}[] };
}
export interface CosmeticModel extends CosmeticItem {
  schemaVersion: 2;
  parts: SurfacePart[];
}
export const cosmeticCatalog = index as CosmeticItem[];
export const coveringHeadwear = new Set(["axolotl-cap","fox-beanie","red-panda","creator-fox-hat","creator-frog-hat","orbit-helmet","star-witch"]);
const cache = new Map<string, Promise<CosmeticModel>>();
export function cosmeticModel(id: string) {
  if (!cosmeticCatalog.some((x) => x.id === id))
    return Promise.reject(Error("Unknown cosmetic"));
  let promise = cache.get(id);
  if (promise) {
    cache.delete(id);
    cache.set(id, promise);
  }
  if (!promise) {
    promise = fetch(`/assets/catalog/${id}.json`).then((r) => {
      if (!r.ok) throw Error("Modell nicht verfügbar");
      return r.json() as Promise<CosmeticModel>;
    });
    cache.set(id, promise);
    while (cache.size > 16) cache.delete(cache.keys().next().value!);
    const requested = promise;
    promise.catch(() => {
      if (cache.get(id) === requested) cache.delete(id);
    });
  }
  return promise;
}
