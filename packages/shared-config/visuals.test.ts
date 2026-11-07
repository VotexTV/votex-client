import { describe, it, expect } from "vitest";
import { baseTheme, initialState } from "./schema";
import { migrateState } from "./migrate";
import {
  selectVisualProfile,
  resetVisualProfile,
  effectActivity,
  advanceMagneticState,
} from "./visuals";
import { applyGamePreset, matchesGamePreset } from "./performance";
describe("visual profile persistence and migration", () => {
  it("remembers edits to each tier without changing the color story or layout", () => {
    let t = { ...baseTheme, accent: "#123456", scale: 125, blur: 19 };
    t = selectVisualProfile(t, "high");
    t = { ...t, blur: 77 };
    t = selectVisualProfile(t, "performance");
    expect(t.blur).toBe(19);
    t = selectVisualProfile(t, "high");
    expect(t.blur).toBe(77);
    expect(t.accent).toBe("#123456");
    expect(t.scale).toBe(125);
  });
  it("resets only the current effect tier", () => {
    let t = selectVisualProfile({ ...baseTheme, blur: 17 }, "high");
    t = resetVisualProfile({ ...t, blur: 99, accent: "#123456" });
    expect(t.blur).toBe(56);
    expect(t.accent).toBe("#123456");
    expect(selectVisualProfile(t, "performance").blur).toBe(17);
  });
  it("removes all proximity work for reduced motion and minimal mode", () => {
    expect(
      effectActivity(selectVisualProfile(baseTheme, "ultra-performance"))
        .proximity,
    ).toBe(false);
    expect(
      effectActivity({
        ...selectVisualProfile(baseTheme, "ultra"),
        reducedMotion: true,
      }).maxCards,
    ).toBe(0);
    expect(
      effectActivity(selectVisualProfile(baseTheme, "ultra")).maxCards,
    ).toBe(32);
  });
  it("upgrades old profiles and adds cloud theme once without overwriting custom values", () => {
    const old = structuredClone(initialState) as any;
    delete old.interfaceRevision;
    delete old.themes[0].effectProfiles;
    delete old.profiles[0].renderDistance;
    old.themes = old.themes.filter((t: any) => t.id !== "clouds");
    old.themes[0].accent = "#123456";
    old.language = "en";
    const next = migrateState(old);
    expect(next.language).toBe("de");
    expect(next.profiles[0].renderDistance).toBe(12);
    expect(next.themes[0].accent).toBe("#123456");
    expect(
      migrateState(next).themes.filter((t) => t.id === "clouds"),
    ).toHaveLength(1);
  });
  it("keeps the chosen language after the one-time migration", () => {
    expect(migrateState({ ...initialState, language: "en" }).language).toBe(
      "en",
    );
  });
  it("game presets preserve instance identity, loader and Java", () => {
    const p = {
      ...initialState.profiles[0],
      java: "C:/Java/bin/java.exe",
      loaderVersion: "0.19.5",
    };
    const next = applyGamePreset(p, "cinematic");
    expect(next.id).toBe(p.id);
    expect(next.java).toBe(p.java);
    expect(next.loaderVersion).toBe(p.loaderVersion);
    expect(next.renderDistance).toBe(32);
    expect(next.simulationDistance).toBe(12);
    expect(next.graphics).toBe("fabulous");
    expect(next.shaderEnabled).toBe(false);
  });
  it("does not present old Cinematic values as the newly applied 32-chunk preset", () => {
    const old = {
      ...applyGamePreset(initialState.profiles[0], "cinematic"),
      renderDistance: 20,
    };
    expect(matchesGamePreset(old, "cinematic")).toBe(false);
    expect(old.renderDistance).toBe(20);
    expect(
      matchesGamePreset(applyGamePreset(old, "cinematic"), "cinematic"),
    ).toBe(true);
  });
});

describe("magnetic card motion", () => {
  it("eases toward the pointer and settles inside the displacement limit", () => {
    let state = { x: 0, y: 0, velocityX: 0, velocityY: 0 };
    for (let frame = 0; frame < 90; frame++) {
      state = advanceMagneticState(state, { x: 3.5, y: -2.5 }, 1 / 60, 4);
      expect(Math.abs(state.x)).toBeLessThanOrEqual(4);
      expect(Math.abs(state.y)).toBeLessThanOrEqual(4);
    }
    expect(state.x).toBeCloseTo(3.5, 2);
    expect(state.y).toBeCloseTo(-2.5, 2);
    for (let frame = 0; frame < 90; frame++) {
      state = advanceMagneticState(state, { x: 0, y: 0 }, 1 / 60, 4);
    }
    expect(state.x).toBeCloseTo(0, 2);
    expect(state.y).toBeCloseTo(0, 2);
  });
  it("clamps targets, displacement and elapsed frame time", () => {
    let state = { x: 0, y: 0, velocityX: 0, velocityY: 0 };
    for (let frame = 0; frame < 30; frame++) {
      state = advanceMagneticState(state, { x: 100, y: -100 }, 1, 4);
      expect(Math.abs(state.x)).toBeLessThanOrEqual(4);
      expect(Math.abs(state.y)).toBeLessThanOrEqual(4);
      expect(Object.values(state).every(Number.isFinite)).toBe(true);
    }
  });
});
