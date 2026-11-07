import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { initialState, stateSchema } from "../packages/shared-config/schema";
import { migrateState } from "../packages/shared-config/migrate";
import catalog from "../apps/launcher/src/data/cosmetic-manifest.json";
import curated from "../assets-source/third-party/kenney-curated/manifest.json";
import poly from "../assets-source/third-party/poly-curated/reviewed.json";
describe("licensed cosmetic catalog and saved looks", () => {
  it("migrates legacy wearables once and preserves skin/cape presets", () => {
    const raw = structuredClone(initialState) as any;
    delete raw.cosmetics[0].catalogVersion;
    delete raw.cosmetics[0].equipment;
    Object.assign(raw.cosmetics[0], {
      wings: "dragon",
      ears: true,
      shoulderCloud: true,
      cape: true,
    });
    const next = migrateState(raw);
    expect(next.cosmetics[0].equipment).toEqual({
      wings: "glacier-dragon",
      head: "fox-beanie",
      pet: "cloud-sprite",
    });
    expect(next.cosmetics[0].cape).toBe(true);
    next.cosmetics[0].equipment = {};
    expect(migrateState(next).cosmetics[0].equipment).toEqual({});
  });
  it("shares bounded geometry and original UV textures identically between launcher and Core", () => {
    expect(
      JSON.parse(
        readFileSync("apps/launcher/public/assets/catalog/index.json", "utf8"),
      ),
    ).toEqual(catalog);
    expect(new Set(catalog.map((x) => x.id)).size).toBe(catalog.length);
    expect(catalog.length).toBe(50 + curated.items.length + poly.approvedIds.length);
    expect(catalog.filter((x) => "creator" in x).length).toBe(9 + curated.items.length + poly.approvedIds.length);
    for (const item of catalog) {
      const web = readFileSync(
        `apps/launcher/public/assets/catalog/${item.id}.json`,
        "utf8",
      );
      const core = readFileSync(
        `minecraft/votex-core/src/main/resources/assets/votex_client/catalog/${item.id}.json`,
        "utf8",
      );
      expect(core).toBe(web);
      const model = JSON.parse(web);
      expect(model.slot).toBe(item.slot);
      expect(model.schemaVersion).toBe(2);
      expect(model.parts.length).toBeGreaterThan(0);
      let faces = 0;
      for (const part of model.parts) {
        expect([
          "head",
          "body",
          "leftArm",
          "rightArm",
          "leftLeg",
          "rightLeg",
        ]).toContain(part.joint);
        expect(part.positions.length % 3).toBe(0);
        expect(part.normals.length).toBe(part.positions.length);
        expect(part.colors.length).toBe(part.positions.length);
        expect(part.quads.length % 4).toBe(0);
        expect(part.positions.length).toBeLessThanOrEqual(60000);
        expect(part.quads.length).toBeLessThanOrEqual(24000);
        if (part.texture) {
          expect(part.texture).toMatch(/^[a-z0-9-]{1,70}\.png$/);
          expect(part.uvs.length).toBe((part.positions.length / 3) * 2);
          expect(
            part.uvs.every(
              (n: number) => Number.isFinite(n) && Math.abs(n) <= 16,
            ),
          ).toBe(true);
          const texture = readFileSync(
            `apps/launcher/public/assets/catalog/textures/${part.texture}`,
          );
          expect(
            texture.equals(
              readFileSync(
                `minecraft/votex-core/src/main/resources/assets/votex_client/textures/cosmetics/${part.texture}`,
              ),
            ),
          ).toBe(true);
          expect(texture.subarray(1, 4).toString()).toBe("PNG");
        }
        expect(
          part.positions.every(
            (v: number) => Number.isFinite(v) && Math.abs(v) < 100,
          ),
          `${item.id}: bounded positions`,
        ).toBe(true);
        let validNormals = true;
        for (let i = 0; i < part.normals.length; i += 3)
          validNormals &&=
            Math.abs(Math.hypot(...part.normals.slice(i, i + 3)) - 1) < 0.005;
        expect(validNormals, `${item.id}: unit normals`).toBe(true);
        expect(
          part.colors.every(
            (v: number) => Number.isFinite(v) && v >= 0 && v <= 1,
          ),
          `${item.id}: vertex colors`,
        ).toBe(true);
        expect(
          part.quads.every(
            (q: number) =>
              Number.isInteger(q) && q >= 0 && q < part.positions.length / 3,
          ),
          `${item.id}: valid indices`,
        ).toBe(true);
        faces += part.quads.length / 4;
      }
      expect(faces).toBeLessThan(10000);
      expect(model.boxes).toBeUndefined();
    }
  });
  it("rejects unknown slots and file paths in equipment IDs", () => {
    const state = structuredClone(initialState) as any;
    state.cosmetics[0].equipment = { wings: "../../external" };
    expect(stateSchema.safeParse(state).success).toBe(false);
    state.cosmetics[0].equipment = { external: "valid" };
    expect(stateSchema.safeParse(state).success).toBe(false);
  });
});
