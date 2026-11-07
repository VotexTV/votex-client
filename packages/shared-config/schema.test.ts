import { describe, it, expect } from "vitest";
import {
  initialState,
  stateSchema,
  themeSchema,
  profileSchema,
  baseTheme,
  defaultCharacterView,
} from "./schema";
import { migrateState } from "./migrate";
describe("configuration boundaries", () => {
  it("accepts the shipped default state", () =>
    expect(stateSchema.safeParse(initialState).success).toBe(true));
  it("adds the character view to older states without changing profiles, skins or looks", () => {
    const legacy = structuredClone(initialState);
    delete (legacy as Partial<typeof legacy>).characterView;
    const next = migrateState(legacy);
    expect(next.characterView).toEqual(defaultCharacterView);
    expect(next.profiles).toEqual(legacy.profiles);
    expect(next.skins).toEqual(legacy.skins);
    expect(next.cosmetics).toEqual(legacy.cosmetics);
    expect(
      stateSchema.parse({
        ...next,
        characterView: {
          mode: "scene",
          scene: "night-garden",
          lighting: "moonlight",
        },
      }).characterView,
    ).toMatchObject({
      mode: "scene",
      scene: "night-garden",
      lighting: "moonlight",
    });
  });
  it("rejects unsupported scene and lighting imports", () => {
    for (const characterView of [
      null,
      { ...defaultCharacterView, scene: "https://example.test/scene.glb" },
      { ...defaultCharacterView, mode: "webgl-script" },
      { ...defaultCharacterView, lighting: "unbounded" },
    ]) {
      expect(
        stateSchema.safeParse({ ...initialState, characterView }).success,
      ).toBe(false);
    }
  });
  it("rejects unsafe imported background sources", () => {
    for (const url of [
      "javascript:alert(1)",
      "https://tracker.test/a.png",
      "file:///C:/Windows/a.png",
      "data:image/svg+xml;base64,PHN2Zz4=",
    ])
      expect(
        themeSchema.safeParse({ ...baseTheme, backgroundImage: url }).success,
      ).toBe(false);
  });
  it("bounds local video backgrounds and keeps them opt-in", () => {
    expect(baseTheme.backgroundVideo).toBe("");
    expect(
      themeSchema.safeParse({
        ...baseTheme,
        backgroundMode: "video",
        backgroundVideo: "data:video/mp4;base64,AAAABGZ0eXBpc29t",
      }).success,
    ).toBe(true);
    expect(
      themeSchema.safeParse({ ...baseTheme, backgroundMode: "video" }).success,
    ).toBe(false);
    for (const backgroundVideo of [
      "https://example.test/video.mp4",
      "data:video/quicktime;base64,AAAA",
      `data:video/mp4;base64,${"A".repeat(7_000_000)}`,
    ])
      expect(
        themeSchema.safeParse({ ...baseTheme, backgroundVideo }).success,
      ).toBe(false);
  });
  it("bounds background framing controls", () => {
    expect(
      themeSchema.safeParse({ ...baseTheme, backgroundBlur: 25 }).success,
    ).toBe(false);
    expect(
      themeSchema.safeParse({ ...baseTheme, backgroundZoom: 161 }).success,
    ).toBe(false);
    expect(
      themeSchema.safeParse({ ...baseTheme, backgroundPositionX: -1 }).success,
    ).toBe(false);
  });
  it("migrates older themes with safe local-background defaults", () => {
    const legacy = structuredClone(initialState) as typeof initialState;
    for (const theme of legacy.themes) {
      delete (theme as Partial<typeof theme>).backgroundVideo;
      delete (theme as Partial<typeof theme>).backgroundBlur;
      delete (theme as Partial<typeof theme>).backgroundPositionX;
      delete (theme as Partial<typeof theme>).backgroundPositionY;
      delete (theme as Partial<typeof theme>).backgroundZoom;
    }
    const migrated = stateSchema.parse(legacy);
    expect(migrated.themes[0]).toMatchObject({
      backgroundVideo: "",
      backgroundBlur: 0,
      backgroundPositionX: 50,
      backgroundPositionY: 50,
      backgroundZoom: 100,
    });
  });
  it("rejects paths in profile IDs and versions", () => {
    for (const id of ["../main", "C:\\Windows", "main/test", ""])
      expect(
        profileSchema.safeParse({ ...initialState.profiles[0], id }).success,
      ).toBe(false);
  });
  it("rejects duplicate instance identities", () =>
    expect(
      stateSchema.safeParse({
        ...initialState,
        profiles: [...initialState.profiles, initialState.profiles[0]],
      }).success,
    ).toBe(false));
  it("rejects missing active references", () =>
    expect(
      stateSchema.safeParse({ ...initialState, activeProfile: "missing" })
        .success,
    ).toBe(false));
  it("rejects future schemas without migration", () =>
    expect(
      stateSchema.safeParse({ ...initialState, schemaVersion: 2 }).success,
    ).toBe(false));
  it("bounds memory, wobble and retention", () => {
    expect(
      profileSchema.safeParse({ ...initialState.profiles[0], memory: 0 })
        .success,
    ).toBe(false);
    expect(themeSchema.safeParse({ ...baseTheme, wobble: 101 }).success).toBe(
      false,
    );
    expect(
      stateSchema.safeParse({ ...initialState, retention: 0 }).success,
    ).toBe(false);
  });
  it("preserves independent game and launcher designs", () => {
    const s = stateSchema.parse({
      ...initialState,
      syncDesign: false,
      gameThemeId: "minimal",
    });
    expect(s.themeId).toBe("votex");
    expect(s.gameThemeId).toBe("minimal");
  });
});
