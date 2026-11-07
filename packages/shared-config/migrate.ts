import {
  stateSchema,
  initialState,
  publisherClientId,
  presets,
} from "./schema";
export function migrateState(raw: unknown) {
  const next = raw ? stateSchema.parse(raw) : structuredClone(initialState);
  if (!next.clientId) next.clientId = publisherClientId;
  if (raw && typeof raw === "object" && !("interfaceRevision" in raw))
    next.language = "de";
  if (!next.cosmetics.length) {
    next.cosmetics = structuredClone(initialState.cosmetics);
    next.selectedCosmetic = next.cosmetics[0].id;
  }
  for (const look of next.cosmetics) {
    if (!look.catalogVersion) {
      const e: Record<string, string> = {};
      if (look.wings !== "none")
        e.wings = {
          angel: "cloud-feathers",
          dragon: "glacier-dragon",
          crystal: "amethyst-prism",
        }[look.wings];
      if (look.halo) e.head = "storm-halo";
      if (look.horns) e.head = "forest-antlers";
      if (look.ears) e.head = "fox-beanie";
      if (look.crown) e.head = "lotus-wreath";
      if (look.headphones) e.head = "orbit-headphones";
      if (look.backpack) e.back = "trail-pack";
      if (look.scarf) e.overlay = "forest-poncho";
      if (look.shoes) e.feet = "cloud-boots";
      if (look.tail) e.back = "comet-tail";
      if (look.shoulderCloud) e.pet = "cloud-sprite";
      look.equipment = e;
      look.catalogVersion = 1;
    }
  }
  if (!next.themes.some((t) => t.id === "clouds") && next.themes.length < 100)
    next.themes.push(structuredClone(presets.find((t) => t.id === "clouds")!));
  return next;
}
