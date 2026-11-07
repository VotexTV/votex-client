import { describe, expect, it } from "vitest";
import { buildCustomCosmetic, parseCustomCosmeticFile, prepareCustomCosmeticSave, type CustomPrimitive } from "./customModels";

const primitive: CustomPrimitive = {
  id: "part-01234567-89ab-cdef-0123-456789abcdef",
  type: "cube",
  joint: "body",
  position: [0, 2, -1],
  size: [3, 4, 2],
  rotation: [0, 15, 0],
  color: "#73aaff",
  material: "metal",
  animated: false,
  animation: { axis: "z", amplitude: .3, speed: 2, phase: 0 },
};

describe("portable custom cosmetics", () => {
  it("imports only the versioned model data and rebuilds its mesh", () => {
    const draft = parseCustomCosmeticFile({
      format: "votex-cosmetic",
      schemaVersion: 1,
      name: "  Kristallflügel  ",
      slot: "wings",
      primitives: [primitive],
    });
    expect(draft.name).toBe("Kristallflügel");
    expect(draft.primitives).toHaveLength(1);
    expect(draft.primitives[0].id).not.toBe(primitive.id);
    const model = buildCustomCosmetic("custom-01234567-89ab-cdef-0123-456789abcdef", draft.name, draft.slot, draft.primitives);
    expect(model.model.parts[0].material).toMatchObject({ metalness: .82, roughness: .2 });
    expect(model.model.parts[0].positions.length).toBeGreaterThan(0);
  });

  it("reopens a private model in place and clears no local ownership metadata", () => {
    const id = "custom-01234567-89ab-cdef-0123-456789abcdef";
    const original = {
      ...buildCustomCosmetic(id, "Privat", "wings", [primitive]),
      authorName: "Creator",
      communityOwned: false,
    };
    const draft = buildCustomCosmetic(id, "Überarbeitet", "back", [primitive]);
    const saved = prepareCustomCosmeticSave(draft, original);

    expect(saved.forked).toBe(false);
    expect(saved.item).toMatchObject({
      id,
      name: "Überarbeitet",
      slot: "back",
      visibility: "private",
      authorName: "Creator",
      communityOwned: false,
    });
    expect(saved.item.model.id).toBe(id);
  });

  it("forks a published model to a fresh private identity without mutating its source", () => {
    const id = "custom-01234567-89ab-cdef-0123-456789abcdef";
    const original = {
      ...buildCustomCosmetic(id, "Öffentlich", "wings", [primitive]),
      communityId: "7d53923c-b91e-4bba-90df-52bbdb8e03ae",
      visibility: "public" as const,
      published: true,
      communityShareUrl: "https://example.invalid/share/token",
    };
    const saved = prepareCustomCosmeticSave(
      buildCustomCosmetic(id, "Bearbeitete Kopie", "wings", [primitive]),
      original,
    );

    expect(saved.forked).toBe(true);
    expect(saved.item.id).not.toBe(id);
    expect(saved.item.model.id).toBe(saved.item.id);
    expect(saved.item).toMatchObject({
      visibility: "private",
      communityId: "",
      communityShareUrl: "",
      published: false,
    });
    expect(original).toMatchObject({
      id,
      communityId: "7d53923c-b91e-4bba-90df-52bbdb8e03ae",
      visibility: "public",
      published: true,
    });
  });

  it("rejects unsupported formats and geometry outside the launcher limits", () => {
    expect(() => parseCustomCosmeticFile({ format: "obj", schemaVersion: 1 })).toThrow("Dateiformat");
    expect(() => parseCustomCosmeticFile({
      format: "votex-cosmetic", schemaVersion: 1, name: "Übermaß", slot: "wings",
      primitives: Array.from({ length: 25 }, () => primitive),
    })).toThrow("1 bis 24");
    expect(() => parseCustomCosmeticFile({
      format: "votex-cosmetic", schemaVersion: 1, name: "Ungültig", slot: "wings",
      primitives: [{ ...primitive, size: [1000, 1, 1] }],
    })).toThrow("ungültige Geometrie");
  });
});
