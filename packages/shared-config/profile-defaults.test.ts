import { describe, it, expect } from "vitest";
import { initialState, profileSchema } from "./schema";
import {
  newGameProfile,
  rememberGameClass,
  applyGamePreset,
  saveGamePresetValues,
} from "./performance";
describe("new profile choices and class memory", () => {
  it("keeps the shared class authoritative when creating from a differently tuned active profile", () => {
    const active = saveGamePresetValues(
      applyGamePreset(initialState.profiles[0], "cinematic"),
      { maxFps: 120, performanceMods: true },
    );
    const personal = saveGamePresetValues(active, {
      maxFps: 165,
      performanceMods: false,
      guiScale: 3,
      fov: 90,
    });
    const bank = rememberGameClass({}, personal),
      created = newGameProfile(active, "next", bank);
    expect(created.presetValues.cinematic).toMatchObject({
      maxFps: 165,
      performanceMods: false,
      guiScale: 3,
      fov: 90,
    });
    expect(applyGamePreset(created, "cinematic")).toMatchObject({
      maxFps: 165,
      performanceMods: false,
    });
  });
  it("uses modern package defaults for old classes and never enables an unsupported loader package", () => {
    const old = saveGamePresetValues(
      applyGamePreset(initialState.profiles[0], "performance"),
      {},
    );
    delete old.starterMods;
    old.performanceMods = false;
    delete old.presetValues.performance!.performanceMods;
    expect(newGameProfile(old, "next", {}).performanceMods).toBe(true);
    const vanilla = { ...old, loader: "vanilla" as const };
    expect(applyGamePreset(vanilla, "performance").performanceMods).toBe(false);
    expect(newGameProfile(vanilla, "plain", {}).performanceMods).toBe(false);
  });
  it("starts with opt-out basics and separate SMP selection", () => {
    const base = initialState.profiles[0];
    const next = newGameProfile(base, "new", {});
    expect(next.starterMods).toMatchObject({
      sodium: true,
      iris: true,
      modmenu: true,
      zoomify: true,
      more: false,
    });
    next.starterMods!.sodium = false;
    expect(newGameProfile(base, "other", {}).starterMods!.sodium).toBe(true);
    expect(base.starterMods!.sodium).toBe(true);
    expect(next.gameOptionsRevision).toBe(1);
  });
  it("new profiles inherit saved class FOV GUI and FPS without mutating the source", () => {
    const base = applyGamePreset(initialState.profiles[0], "performance");
    const tuned = saveGamePresetValues(base, {
      maxFps: 165,
      guiScale: 3,
      fov: 95,
      renderDistance: 25,
      gameOptions: { mipmapLevels: "2", gamma: "0.7" },
      engineSettings: {
        schemaVersion: 1,
        textCache: false,
        reusableMotion: true,
        adaptive: "BALANCED",
        targetFps: 144,
      },
    });
    const next = newGameProfile(
      initialState.profiles[0],
      "new",
      tuned.presetValues,
    );
    expect(next).toMatchObject({
      preset: "performance",
      maxFps: 165,
      guiScale: 3,
      fov: 95,
      renderDistance: 25,
    });
    expect(next.gameOptions).toEqual({ mipmapLevels: "2", gamma: "0.7" });
    expect(next.engineSettings).toMatchObject({
      textCache: false,
      adaptive: "BALANCED",
      targetFps: 144,
    });
    const roundtrip = profileSchema.parse(JSON.parse(JSON.stringify(next)));
    expect(roundtrip).toEqual(next);
    const cinematic = saveGamePresetValues(applyGamePreset(next, "cinematic"), {
      fov: 80,
      guiScale: 2,
      maxFps: 120,
    });
    expect(applyGamePreset(cinematic, "performance")).toMatchObject({
      maxFps: 165,
      guiScale: 3,
      fov: 95,
    });
  });
});
