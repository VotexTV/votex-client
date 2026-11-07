import { describe, expect, it } from "vitest";
import { initialState, profileSchema, stateSchema } from "./schema";
import { migrateState } from "./migrate";

describe("native legacy starter-mod profile compatibility", () => {
  it("loads the three null recipes returned by the native backend without replacing profiles or preferences", () => {
    const raw = {
      ...structuredClone(initialState),
      profiles: ["main", "older", "third"].map((id, i) => ({
        ...structuredClone(initialState.profiles[0]),
        id,
        name: `Existing profile ${i}`,
        starterMods: null,
        maxFps: 120 + i,
        fov: 80 + i,
        guiScale: i + 1,
        performanceMods: false,
        shaderEnabled: false,
      })),
      activeProfile: "older",
    };
    const original = JSON.stringify(raw);
    const migrated = migrateState(raw);
    expect(migrated.activeProfile).toBe("older");
    expect(migrated.profiles).toHaveLength(3);
    migrated.profiles.forEach((profile, i) => {
      const { starterMods: _legacyNull, ...unchanged } = raw.profiles[i];
      expect(profile).toEqual(unchanged);
      expect(profile.starterMods).toBeUndefined();
    });
    expect(migrated.skins).toEqual(raw.skins);
    expect(migrated.cosmetics).toEqual(raw.cosmetics);
    expect(migrated.themes).toEqual(raw.themes);
    expect(JSON.stringify(raw)).toBe(original);
    expect(migrateState(JSON.parse(JSON.stringify(migrated)))).toEqual(migrated);
    expect(stateSchema.parse(migrated)).toEqual(migrated);
  });

  it("preserves explicit opt-outs and selected extras across serialization", () => {
    const starterMods = {
      sodium: false, iris: false, modmenu: true, zoomify: false, more: true,
      extras: ["appleskin", "voicechat"], shaders: [],
    };
    const profile = profileSchema.parse({ ...initialState.profiles[0], starterMods });
    expect(profileSchema.parse(JSON.parse(JSON.stringify(profile))).starterMods).toEqual(starterMods);
    const missing = { ...initialState.profiles[0] };
    delete missing.starterMods;
    expect(profileSchema.parse(missing).starterMods).toBeUndefined();
  });

  it("still rejects malformed recipes instead of resetting them to defaults", () => {
    for (const starterMods of [false, [], "null", {}, { sodium: "false" }]) {
      const profile = { ...initialState.profiles[0], starterMods };
      expect(profileSchema.safeParse(profile).success).toBe(false);
      expect(() => migrateState({ ...initialState, profiles: [profile] })).toThrow();
    }
  });
});
