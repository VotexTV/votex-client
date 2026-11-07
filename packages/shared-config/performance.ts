import type { Profile } from "./schema";
export const gamePresets = [
  {
    id: "extreme",
    name: "Maximale Entlastung",
    hint: "Für kleine Systeme",
    detail:
      "6 Chunks Sichtweite, 4 Simulation, schnelle Grafik, 60 FPS-Limit. Weniger Welt und weniger parallele Simulation.",
    values: {
      memory: 2048,
      performanceMods: true,
      renderDistance: 6,
      simulationDistance: 4,
      maxFps: 60,
      graphics: "fast",
      vsync: false,
      guiScale: 0,
      fov: 70,
      shaderEnabled: false,
    },
  },
  {
    id: "performance",
    name: "Performance",
    hint: "Flüssig spielen",
    detail:
      "10 Chunks Sichtweite, 6 Simulation, schnelle Grafik und 144 FPS-Limit. Shader bleiben aus.",
    values: {
      memory: 3072,
      performanceMods: true,
      renderDistance: 10,
      simulationDistance: 6,
      maxFps: 144,
      graphics: "fast",
      vsync: false,
      guiScale: 0,
      fov: 70,
      shaderEnabled: false,
    },
  },
  {
    id: "balanced",
    name: "Ausgewogen",
    hint: "Ein guter Ausgangspunkt",
    detail:
      "12 Chunks Sichtweite, 8 Simulation, schöne Grafik und 144 FPS-Limit. Danach nach deinem PC abstimmen.",
    values: {
      memory: 4096,
      performanceMods: false,
      renderDistance: 12,
      simulationDistance: 8,
      maxFps: 144,
      graphics: "fancy",
      vsync: false,
      guiScale: 0,
      fov: 70,
      shaderEnabled: false,
    },
  },
  {
    id: "cinematic",
    name: "Cinematic",
    hint: "Mehr Welt, mehr Atmosphäre",
    detail:
      "32 Chunks Sichtweite, 12 Simulation, fabelhafte Grafik und 120 FPS-Limit. Große Panoramen; Shader bleiben gezielt zuschaltbar.",
    values: {
      memory: 8192,
      performanceMods: false,
      renderDistance: 32,
      simulationDistance: 12,
      maxFps: 120,
      graphics: "fabulous",
      vsync: false,
      guiScale: 0,
      fov: 70,
      shaderEnabled: false,
    },
  },
] as const;
export function applyGamePreset(
  p: Profile,
  id: (typeof gamePresets)[number]["id"],
): Profile {
  const presetValues = { ...p.presetValues };
  if (p.preset !== "custom" && p.preset !== id) {
    presetValues[p.preset] = {
      memory: p.memory,
      performanceMods: p.performanceMods,
      renderDistance: p.renderDistance,
      simulationDistance: p.simulationDistance,
      maxFps: p.maxFps,
      graphics: p.graphics,
      gameOptions: p.gameOptions,
      vsync: p.vsync,
      guiScale: p.guiScale,
      fov: p.fov,
      engineSettings: p.engineSettings,
      shaderEnabled: p.shaderEnabled,
    };
  }
  const saved = presetValues[id];
  return {
    ...p,
    ...(saved ?? gamePresets.find((x) => x.id === id)!.values),
    performanceMods:
      ["fabric", "quilt"].includes(p.loader) &&
      (saved?.performanceMods ??
        gamePresets.find((x) => x.id === id)!.values.performanceMods),
    shaderEnabled:
      p.loader === "fabric" &&
      (saved?.shaderEnabled ??
        gamePresets.find((x) => x.id === id)!.values.shaderEnabled),
    engineSettings: saved?.engineSettings ?? {
      schemaVersion: 1,
      textCache: true,
      reusableMotion: true,
      adaptive: "DISABLED",
      targetFps: 120,
    },
    gameOptions: saved?.gameOptions ?? {},
    preset: id,
    presetValues,
    gameOptionsRevision: p.gameOptionsRevision + 1,
  };
}

export function resetGamePreset(
  p: Profile,
  id: (typeof gamePresets)[number]["id"],
): Profile {
  const presetValues = { ...p.presetValues };
  delete presetValues[id];
  return {
    ...p,
    ...gamePresets.find((x) => x.id === id)!.values,
    performanceMods:
      ["fabric", "quilt"].includes(p.loader) &&
      gamePresets.find((x) => x.id === id)!.values.performanceMods,
    engineSettings: {
      schemaVersion: 1,
      textCache: true,
      reusableMotion: true,
      adaptive: "DISABLED",
      targetFps: 120,
    },
    gameOptions: {},
    preset: id,
    presetValues,
    gameOptionsRevision: p.gameOptionsRevision + 1,
  };
}

export function saveGamePresetValues(
  p: Profile,
  patch: Partial<Profile>,
): Profile {
  const next = { ...p, ...patch };
  if (p.preset === "custom") return next;
  return {
    ...next,
    presetValues: {
      ...p.presetValues,
      [p.preset]: {
        memory: next.memory,
        performanceMods: next.performanceMods,
        renderDistance: next.renderDistance,
        simulationDistance: next.simulationDistance,
        maxFps: next.maxFps,
        graphics: next.graphics,
        gameOptions: next.gameOptions,
        vsync: next.vsync,
        guiScale: next.guiScale,
        fov: next.fov,
        engineSettings: next.engineSettings,
        shaderEnabled: next.shaderEnabled,
      },
    },
  };
}

export const starterModDefaults: NonNullable<Profile["starterMods"]> = {
  sodium: true,
  iris: true,
  modmenu: true,
  zoomify: true,
  more: false,
  extras: [
    "xaeros-minimap",
    "xaeros-world-map",
    "appleskin",
    "3dskinlayers",
    "voicechat",
    "mousetweaks",
  ],
  shaders: ["complementary-reimagined", "bsl-shaders"],
};

/** New instances inherit the user's saved class, without changing existing instances. */
export function newGameProfile(
  base: Profile,
  id: string,
  bank: Profile["presetValues"],
): Profile {
  const inherited = rememberGameClass(base.presetValues, base);
  if (!base.starterMods && base.preset !== "custom")
    delete inherited[base.preset]?.performanceMods;
  const p = applyGamePreset(
    {
      ...base,
      id,
      name: "Minecraft-Profil",
      loaderVersion: "",
      preset: "custom",
      presetValues: { ...inherited, ...bank },
      starterMods: structuredClone(starterModDefaults),
    },
    "performance",
  );
  return {
    ...p,
    performanceMods:
      ["fabric", "quilt"].includes(p.loader) && p.performanceMods,
    gameOptionsRevision: 1,
  };
}
export function rememberGameClass(
  bank: Profile["presetValues"],
  p: Profile,
): Profile["presetValues"] {
  if (p.preset === "custom") return bank;
  return {
    ...bank,
    [p.preset]: saveGamePresetValues(p, {}).presetValues[p.preset],
  };
}

/** A saved label may refer to an older preset; values remain authoritative until explicitly applied. */
export function matchesGamePreset(
  profile: Profile,
  id: (typeof gamePresets)[number]["id"],
) {
  const values = gamePresets.find((p) => p.id === id)!.values;
  return Object.entries(values).every(
    ([key, value]) => profile[key as keyof Profile] === value,
  );
}
