import { z } from "zod";
import microsoftApplication from "./microsoft.json";
export const publisherClientId = microsoftApplication.clientId;
export const communityVisibilitySchema = z.enum([
  "private",
  "unlisted",
  "public",
]);
export type CommunityVisibility = z.infer<typeof communityVisibilitySchema>;
const percent = z.number().min(0).max(100);
const color = z.string().regex(/^#[0-9a-fA-F]{6}$/);
const point3 = z.tuple([
  z.number().min(-32).max(32),
  z.number().min(-32).max(32),
  z.number().min(-32).max(32),
]);
const partKeyframes = z
  .array(
    z.object({
      time: z.number().min(0).max(1),
      angle: z.number().min(-3).max(3),
    }),
  )
  .min(2)
  .max(24)
  .refine(
    (frames) =>
      frames[0].time === 0 &&
      frames.at(-1)!.time === 1 &&
      frames.every(
        (frame, index) => index === 0 || frame.time > frames[index - 1].time,
      ),
  );
const joint = z.enum([
  "head",
  "body",
  "leftArm",
  "rightArm",
  "leftLeg",
  "rightLeg",
]);
const surfaceMotionSchema = z.discriminatedUnion("type", [
  z.object({
    type: z.literal("wing"),
    side: z.number().int().min(-1).max(1),
    pivot: point3,
  }),
  z.object({
    type: z.literal("oscillate"),
    axis: z.enum(["x", "y", "z"]),
    amplitude: z.number().min(0).max(1.2),
    speed: z.number().min(0.1).max(8),
    phase: z.number().min(-6.3).max(6.3).default(0),
    keyframes: partKeyframes.optional(),
    duration: z.number().min(1).max(10).optional(),
    pivot: point3,
  }),
]);
const surfacePartSchema = z.object({
  joint,
  positions: z.array(z.number().min(-128).max(128)).min(12).max(6000),
  normals: z.array(z.number().min(-1.01).max(1.01)).min(12).max(6000),
  colors: z.array(z.number().min(0).max(1)).min(12).max(6000),
  quads: z.array(z.number().int().min(0).max(2000)).min(4).max(2400),
  texture: z
    .string()
    .regex(/^[a-z0-9-]{1,70}\.png$/)
    .optional(),
  uvs: z.array(z.number().min(-16).max(16)).max(4000).optional(),
  material: z.object({
    color,
    metalness: z.number().min(0).max(1),
    roughness: z.number().min(0).max(1),
    opacity: z.number().min(0).max(1),
  }),
  motion: surfaceMotionSchema.optional(),
});
export const customPrimitiveSchema = z.object({
  id: z.string().regex(/^part-[a-f0-9-]{1,40}$/),
  name: z.string().trim().max(40).optional(),
  type: z.enum(["cube", "sphere", "plane", "pixel"]),
  joint,
  position: point3,
  size: z.tuple([
    z.number().min(0.2).max(32),
    z.number().min(0.2).max(32),
    z.number().min(0.2).max(32),
  ]),
  rotation: z.tuple([
    z.number().min(-180).max(180),
    z.number().min(-180).max(180),
    z.number().min(-180).max(180),
  ]),
  color,
  material: z.enum(["matte", "polished", "metal"]).optional(),
  gradient: z.object({ color, axis: z.enum(["x", "y", "z"]) }).optional(),
  paint: z.array(color).length(64).optional(),
  animated: z.boolean().default(false),
  animation: z
    .object({
      axis: z.enum(["x", "y", "z"]),
      amplitude: z.number().min(0).max(1.2),
      speed: z.number().min(0.1).max(8),
      phase: z.number().min(-6.3).max(6.3).default(0),
      keyframes: partKeyframes.optional(),
      duration: z.number().min(1).max(10).optional(),
    })
    .optional(),
});
const customCosmeticSchema = z.object({
  id: z.string().regex(/^custom-[a-f0-9-]{1,40}$/),
  name: z.string().trim().min(1).max(60),
  slot: z.enum([
    "wings",
    "head",
    "face",
    "pet",
    "back",
    "aura",
    "overlay",
    "feet",
  ]),
  primitives: z.array(customPrimitiveSchema).min(1).max(24),
  model: z.object({
    id: z.string().regex(/^custom-[a-f0-9-]{1,40}$/),
    name: z.string().trim().min(1).max(60),
    slot: z.enum([
      "wings",
      "head",
      "face",
      "pet",
      "back",
      "aura",
      "overlay",
      "feet",
    ]),
    schemaVersion: z.literal(2),
    parts: z.array(surfacePartSchema).min(1).max(24),
  }),
  communityId: z.string().max(50).default(""),
  visibility: communityVisibilitySchema.optional(),
  communityShareUrl: z.string().max(250).optional(),
  published: z.boolean().default(false),
  authorName: z.string().max(16).default(""),
  communityOwned: z.boolean().default(true),
});
const emoteKeyframeSchema = z.object({
  time: z.number().min(0).max(1),
  pose: z.array(z.number().min(-3).max(3)).length(19),
});
const customEmoteSchema = z.object({
  id: z.string().regex(/^custom-emote-[a-f0-9-]{1,40}$/),
  name: z.string().trim().min(1).max(48),
  duration: z.number().min(1).max(10),
  keyframes: z.array(emoteKeyframeSchema).min(2).max(24),
  communityId: z.string().max(50).default(""),
  visibility: communityVisibilitySchema.optional(),
  communityShareUrl: z.string().max(250).optional(),
  published: z.boolean().default(false),
  authorName: z.string().max(16).default(""),
  communityOwned: z.boolean().default(true),
});
export const visualLevelSchema = z.enum([
  "ultra-performance",
  "performance",
  "high",
  "ultra",
]);
export type VisualLevel = z.infer<typeof visualLevelSchema>;
const effectOverrideSchema = z.object({
  transparency: percent,
  blur: percent,
  wobble: percent,
  speed: percent,
  saturation: percent,
  glow: percent,
  reflection: percent,
  shadow: percent,
  stiffness: percent,
  damping: percent,
  hover: z.boolean(),
  reducedMotion: z.boolean(),
  proximity: percent,
  lift: percent,
  tilt: percent,
  ambientMotion: z.boolean(),
  glassEffect: z.boolean().default(true),
  glowEffect: z.boolean().default(true),
  reflectionEffect: z.boolean().default(true),
  proximityEffect: z.boolean().default(false),
  wobbleEffect: z.boolean().default(true),
});
export const themeSchema = z
  .object({
    schemaVersion: z.literal(1),
    id: z.string().min(1).max(80),
    name: z.string().min(1).max(60),
    transparency: percent,
    blur: percent,
    wobble: percent,
    speed: percent,
    saturation: percent,
    glow: percent,
    radius: percent,
    brightness: percent,
    scale: z.number().min(75).max(150),
    stiffness: percent,
    damping: percent,
    reflection: percent,
    shadow: percent,
    sidebar: z.number().min(160).max(260),
    spacing: z.number().min(8).max(32),
    accent: color,
    secondary: color,
    tint: color,
    background: color,
    text: color,
    muted: color,
    compact: z.boolean(),
    hover: z.boolean(),
    reducedMotion: z.boolean(),
    renderProfile: visualLevelSchema.default("performance"),
    effectProfiles: z
      .object({
        "ultra-performance": effectOverrideSchema.optional(),
        performance: effectOverrideSchema.optional(),
        high: effectOverrideSchema.optional(),
        ultra: effectOverrideSchema.optional(),
      })
      .default({}),
    proximity: percent.default(0),
    lift: percent.default(18),
    tilt: percent.default(0),
    ambientMotion: z.boolean().default(true),
    glassEffect: z.boolean().default(true),
    glowEffect: z.boolean().default(true),
    reflectionEffect: z.boolean().default(true),
    proximityEffect: z.boolean().default(false),
    wobbleEffect: z.boolean().default(true),
    backgroundMode: z.enum([
      "landscape",
      "gradient",
      "solid",
      "image",
      "video",
      "clouds",
    ]),
    backgroundImage: z
      .string()
      .max(8_000_000)
      .refine(
        (s) =>
          s === "" ||
          /^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(s),
      ),
    backgroundVideo: z
      .string()
      .max(7_000_000)
      .refine(
        (s) =>
          s === "" || /^data:video\/(mp4|webm);base64,[A-Za-z0-9+/=]+$/.test(s),
      )
      .default(""),
    backgroundBlur: z.number().min(0).max(24).default(0),
    backgroundPositionX: z.number().min(0).max(100).default(50),
    backgroundPositionY: z.number().min(0).max(100).default(50),
    backgroundZoom: z.number().min(100).max(160).default(100),
  })
  .refine(
    (theme) =>
      theme.backgroundMode !== "video" || theme.backgroundVideo.length > 0,
    { message: "A local video is required for the video background." },
  );
export type Theme = z.infer<typeof themeSchema>;
export const engineSettingsSchema = z.object({
  schemaVersion: z.literal(1),
  textCache: z.boolean(),
  reusableMotion: z.boolean(),
  adaptive: z.enum(["DISABLED", "CONSERVATIVE", "BALANCED", "AGGRESSIVE"]),
  targetFps: z.number().int().min(30).max(360),
});
export const gameOptionKeys = [
  "ao",
  "biomeBlendRadius",
  "chunkSectionFadeInTime",
  "cutoutLeaves",
  "entityDistanceScaling",
  "entityShadows",
  "fovEffectScale",
  "darknessEffectScale",
  "glintSpeed",
  "glintStrength",
  "gamma",
  "maxAnisotropyBit",
  "textureFiltering",
  "mipmapLevels",
  "particles",
  "renderClouds",
  "cloudRange",
  "screenEffectScale",
  "vignette",
  "weatherRadius",
  "improvedTransparency",
  "prioritizeChunkUpdates",
] as const;
export const gameOptionsSchema = z
  .record(
    z
      .string()
      .refine((k) =>
        gameOptionKeys.includes(k as (typeof gameOptionKeys)[number]),
      ),
    z
      .string()
      .max(128)
      .regex(/^[^\r\n]*$/),
  )
  .refine((v) => Object.keys(v).length <= 32)
  .default({});
export const profileSchema = z.object({
  id: z.string().regex(/^[a-zA-Z0-9_-]{1,80}$/),
  name: z.string().trim().min(1).max(60),
  engine: z.enum(["java", "horizon"]).default("java"),
  minecraft: z.string().regex(/^[a-zA-Z0-9_.-]{1,64}$/),
  loader: z.enum(["fabric", "vanilla", "quilt", "forge", "neoforge"]),
  memory: z.number().int().min(1024).max(32768),
  java: z.string().max(1000),
  preset: z.enum(["balanced", "performance", "extreme", "cinematic", "custom"]),
  loaderVersion: z.string().regex(/^[a-zA-Z0-9_.+-]{0,64}$/),
  renderDistance: z.number().int().min(2).max(32).default(12),
  simulationDistance: z.number().int().min(2).max(32).default(8),
  maxFps: z
    .number()
    .int()
    .min(0)
    .max(250)
    .refine((v) => v === 0 || v >= 10)
    .default(144),
  graphics: z.enum(["fast", "fancy", "fabulous", "custom"]).default("fancy"),
  gameOptions: gameOptionsSchema,
  vsync: z.boolean().default(false),
  guiScale: z.number().int().min(0).max(8).default(0),
  fov: z.number().min(30).max(110).default(70),
  engineSettings: engineSettingsSchema.optional(),
  starterMods: z
    .object({
      sodium: z.boolean(),
      iris: z.boolean(),
      modmenu: z.boolean(),
      zoomify: z.boolean(),
      more: z.boolean(),
      extras: z
        .array(
          z.enum([
            "xaeros-minimap",
            "xaeros-world-map",
            "appleskin",
            "3dskinlayers",
            "voicechat",
            "mousetweaks",
          ]),
        )
        .max(6),
      shaders: z
        .array(z.enum(["complementary-reimagined", "bsl-shaders"]))
        .max(2),
    })
    // Older native Profiles serialize an absent Option<StarterMods> as null.
    // Keep absence distinct from a new profile's opt-out package defaults.
    .nullish()
    .transform((value) => value ?? undefined),
  performanceMods: z.boolean().default(false),
  shaderEnabled: z.boolean().default(false),
  gameOptionsRevision: z.number().int().min(0).max(4294967295).default(0),
  presetValues: z
    .object({
      balanced: z
        .object({
          memory: z.number().int().min(1024).max(32768),
          performanceMods: z.boolean().optional(),
          renderDistance: z.number().int().min(2).max(32),
          simulationDistance: z.number().int().min(2).max(32),
          maxFps: z.number().int().min(0).max(250),
          graphics: z.enum(["fast", "fancy", "fabulous", "custom"]),
          gameOptions: gameOptionsSchema,
          vsync: z.boolean(),
          guiScale: z.number().int().min(0).max(8).default(0),
          fov: z.number().min(30).max(110).default(70),
          engineSettings: engineSettingsSchema.optional(),
          shaderEnabled: z.boolean(),
        })
        .optional(),
      performance: z
        .object({
          memory: z.number().int().min(1024).max(32768),
          performanceMods: z.boolean().optional(),
          renderDistance: z.number().int().min(2).max(32),
          simulationDistance: z.number().int().min(2).max(32),
          maxFps: z.number().int().min(0).max(250),
          graphics: z.enum(["fast", "fancy", "fabulous", "custom"]),
          gameOptions: gameOptionsSchema,
          vsync: z.boolean(),
          guiScale: z.number().int().min(0).max(8).default(0),
          fov: z.number().min(30).max(110).default(70),
          engineSettings: engineSettingsSchema.optional(),
          shaderEnabled: z.boolean(),
        })
        .optional(),
      extreme: z
        .object({
          memory: z.number().int().min(1024).max(32768),
          performanceMods: z.boolean().optional(),
          renderDistance: z.number().int().min(2).max(32),
          simulationDistance: z.number().int().min(2).max(32),
          maxFps: z.number().int().min(0).max(250),
          graphics: z.enum(["fast", "fancy", "fabulous", "custom"]),
          gameOptions: gameOptionsSchema,
          vsync: z.boolean(),
          guiScale: z.number().int().min(0).max(8).default(0),
          fov: z.number().min(30).max(110).default(70),
          engineSettings: engineSettingsSchema.optional(),
          shaderEnabled: z.boolean(),
        })
        .optional(),
      cinematic: z
        .object({
          memory: z.number().int().min(1024).max(32768),
          performanceMods: z.boolean().optional(),
          renderDistance: z.number().int().min(2).max(32),
          simulationDistance: z.number().int().min(2).max(32),
          maxFps: z.number().int().min(0).max(250),
          graphics: z.enum(["fast", "fancy", "fabulous", "custom"]),
          gameOptions: gameOptionsSchema,
          vsync: z.boolean(),
          guiScale: z.number().int().min(0).max(8).default(0),
          fov: z.number().min(30).max(110).default(70),
          engineSettings: engineSettingsSchema.optional(),
          shaderEnabled: z.boolean(),
        })
        .optional(),
    })
    .default({}),
});
export type Profile = z.infer<typeof profileSchema>;
export const skinSchema = z.object({
  id: z.string().max(80),
  name: z.string().min(1).max(60),
  data: z
    .string()
    .max(1_500_000)
    .regex(/^data:image\/png;base64,[A-Za-z0-9+/=]+$/),
  model: z.enum(["default", "slim"]),
});
export const characterViewSchema = z.object({
  mode: z.enum(["showcase", "scene"]).default("showcase"),
  scene: z
    .enum(["cloud-island", "studio", "night-garden"])
    .default("cloud-island"),
  lighting: z.enum(["studio", "daylight", "moonlight"]).default("studio"),
});
export type CharacterView = z.infer<typeof characterViewSchema>;
export const defaultCharacterView: CharacterView = {
  mode: "showcase",
  scene: "cloud-island",
  lighting: "studio",
};
export const stateSchema = z
  .object({
    schemaVersion: z.literal(1),
    interfaceRevision: z.literal(2).default(2),
    language: z.enum(["en", "de"]),
    themes: z.array(themeSchema).min(1).max(100),
    themeId: z.string(),
    gameThemeId: z.string(),
    syncDesign: z.boolean(),
    profiles: z.array(profileSchema).min(1).max(100),
    gameRuntime: z.enum(["java", "pomme"]).default("java"),
    gamePresetValues: profileSchema.shape.presetValues.default({}),
    activeProfile: z.string(),
    minimizeOnLaunch: z.boolean(),
    clientId: z.string().max(80),
    offlineUsername: z
      .string()
      .regex(/^[A-Za-z0-9_]{1,16}$/)
      .default("VotexPlayer"),
    retention: z.number().int().min(2).max(100),
    skins: z.array(skinSchema).max(100),
    selectedSkin: z.string(),
    idleSkin: z.boolean(),
    characterView: characterViewSchema.default(defaultCharacterView),
    cosmetics: z
      .array(
        z.object({
          id: z.string().max(80),
          name: z.string().min(1).max(60),
          skin: z.string().max(80),
          cape: z.boolean(),
          capeData: z
            .string()
            .max(1_500_000)
            .refine(
              (s) =>
                s === "" || /^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(s),
            )
            .default(""),
          capeCommunityId: z.string().max(50).default(""),
          capeVisibility: communityVisibilitySchema.optional(),
          capeShareUrl: z.string().max(250).optional(),
          capePublished: z.boolean().default(false),
          catalogVersion: z.number().int().min(0).max(1).default(0),
          equipment: z
            .partialRecord(
              z.enum([
                "wings",
                "head",
                "face",
                "pet",
                "back",
                "aura",
                "overlay",
                "feet",
                "cape",
              ]),
              z.string().regex(/^[a-z0-9-]{0,60}$/),
            )
            .default({}),
          wings: z.enum(["none", "angel", "dragon", "crystal"]).default("none"),
          halo: z.boolean().default(false),
          horns: z.boolean().default(false),
          ears: z.boolean().default(false),
          crown: z.boolean().default(false),
          headphones: z.boolean().default(false),
          backpack: z.boolean().default(false),
          scarf: z.boolean().default(false),
          shoes: z.boolean().default(false),
          tail: z.boolean().default(false),
          shoulderCloud: z.boolean().default(false),
          color: color.default("#92bfff"),
          secondary: color.default("#cfb5ff"),
          scale: z.number().min(50).max(150).default(100),
        }),
      )
      .max(100),
    selectedCosmetic: z.string(),
    customCosmetics: z.array(customCosmeticSchema).max(50).default([]),
    customEmotes: z.array(customEmoteSchema).max(50).default([]),
    emoteWheel: z
      .array(
        z
          .string()
          .regex(
            /^(wave|sit|dance|point|clap|salute|celebrate|bow|custom-emote-[a-f0-9-]{1,40})$/,
          ),
      )
      .length(8)
      .default([
        "wave",
        "sit",
        "dance",
        "point",
        "clap",
        "salute",
        "celebrate",
        "bow",
      ]),
  })
  .superRefine((s, c) => {
    for (const [key, values] of [
      ["profiles", s.profiles],
      ["themes", s.themes],
      ["skins", s.skins],
      ["cosmetics", s.cosmetics],
      ["customCosmetics", s.customCosmetics],
      ["customEmotes", s.customEmotes],
    ] as const) {
      if (new Set(values.map((x) => x.id)).size !== values.length)
        c.addIssue({ code: "custom", message: `Duplicate ${key} IDs` });
    }
    const customModelIds = new Set(s.customCosmetics.map((item) => item.id));
    for (const item of s.customCosmetics) {
      if (item.model.id !== item.id || item.model.slot !== item.slot)
        c.addIssue({
          code: "custom",
          message: "Custom model metadata mismatch",
        });
      if (item.primitives.length === 0 || !item.model.parts.length)
        c.addIssue({ code: "custom", message: "Custom model is empty" });
      if (
        item.primitives.some(
          (primitive) => primitive.animated && !primitive.animation,
        )
      )
        c.addIssue({
          code: "custom",
          message: "Animated parts need a safe motion recipe",
        });
    }
    const customEmoteIds = new Set(s.customEmotes.map((item) => item.id));
    for (const item of s.customEmotes) {
      if (item.keyframes[0]?.time !== 0 || item.keyframes.at(-1)?.time !== 1)
        c.addIssue({
          code: "custom",
          message: "Emote keyframes must cover the full timeline",
        });
      if (
        item.keyframes.some(
          (frame, i, frames) => i > 0 && frame.time <= frames[i - 1].time,
        )
      )
        c.addIssue({
          code: "custom",
          message: "Emote keyframes must be ordered",
        });
    }
    if (
      s.emoteWheel.some(
        (id) => id.startsWith("custom-emote-") && !customEmoteIds.has(id),
      )
    )
      c.addIssue({ code: "custom", message: "Unknown custom emote in wheel" });
    if (
      s.customCosmetics.length > 0 &&
      customModelIds.size !== s.customCosmetics.length
    )
      c.addIssue({ code: "custom", message: "Duplicate custom cosmetic IDs" });
    if (!s.profiles.some((p) => p.id === s.activeProfile))
      c.addIssue({ code: "custom", message: "Unknown active profile" });
    if (
      !s.themes.some((t) => t.id === s.themeId) ||
      !s.themes.some((t) => t.id === s.gameThemeId)
    )
      c.addIssue({ code: "custom", message: "Unknown theme" });
  });
export type AppState = z.infer<typeof stateSchema>;
export const baseTheme: Theme = {
  schemaVersion: 1,
  id: "votex",
  name: "Votex Liquid Blue",
  transparency: 24,
  blur: 42,
  wobble: 35,
  speed: 55,
  saturation: 55,
  glow: 28,
  radius: 55,
  brightness: 60,
  scale: 100,
  stiffness: 55,
  damping: 65,
  reflection: 35,
  shadow: 40,
  sidebar: 190,
  spacing: 20,
  accent: "#3478ff",
  secondary: "#45d8ff",
  tint: "#101729",
  background: "#080b14",
  text: "#f7faff",
  muted: "#98a8c5",
  compact: false,
  hover: true,
  reducedMotion: false,
  renderProfile: "performance",
  effectProfiles: {},
  proximity: 0,
  lift: 18,
  tilt: 0,
  ambientMotion: true,
  glassEffect: true,
  glowEffect: true,
  reflectionEffect: true,
  proximityEffect: false,
  wobbleEffect: true,
  backgroundMode: "landscape",
  backgroundImage: "",
  backgroundVideo: "",
  backgroundBlur: 0,
  backgroundPositionX: 50,
  backgroundPositionY: 50,
  backgroundZoom: 100,
};
export const presets: Theme[] = [
  baseTheme,
  {
    ...baseTheme,
    id: "midnight",
    name: "Midnight Obsidian",
    accent: "#8e9dff",
    tint: "#111021",
    brightness: 30,
  },
  {
    ...baseTheme,
    id: "crystal",
    name: "Crystal Glass",
    accent: "#27b9de",
    transparency: 12,
    blur: 65,
  },
  {
    ...baseTheme,
    id: "white",
    name: "Frosted White",
    tint: "#ffffff",
    background: "#f1f3f6",
    text: "#182230",
    muted: "#596471",
    accent: "#415aa8",
    secondary: "#5366ad",
    transparency: 65,
    brightness: 80,
  },
  {
    ...baseTheme,
    id: "neon",
    name: "Neon Purple",
    accent: "#b173ff",
    secondary: "#f290e5",
    tint: "#20112d",
  },
  {
    ...baseTheme,
    id: "black",
    name: "Pure Black",
    background: "#000000",
    tint: "#050505",
    brightness: 10,
  },
  {
    ...baseTheme,
    id: "clouds",
    name: "Cloud Nine",
    backgroundMode: "clouds",
    accent: "#8eb7ff",
    secondary: "#c5b3ef",
    tint: "#19243b",
    brightness: 76,
  },
  {
    ...baseTheme,
    id: "minimal",
    name: "Minimal Performance",
    blur: 0,
    wobble: 0,
    reflection: 0,
    glow: 0,
    transparency: 95,
    backgroundMode: "solid",
    reducedMotion: true,
  },
];
export const initialState: AppState = {
  schemaVersion: 1,
  interfaceRevision: 2,
  language: "de",
  themes: presets.map((t) => ({ ...t })),
  themeId: "votex",
  gameThemeId: "votex",
  syncDesign: true,
  profiles: [
    {
      id: "main",
      name: "Main",
      engine: "java",
      minecraft: "26.2",
      loader: "fabric",
      memory: 4096,
      java: "",
      preset: "balanced",
      loaderVersion: "",
      renderDistance: 12,
      simulationDistance: 8,
      maxFps: 144,
      graphics: "fancy",
      gameOptions: {},
      vsync: false,
      guiScale: 0,
      fov: 70,
      starterMods: {
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
      },
      performanceMods: false,
      shaderEnabled: false,
      gameOptionsRevision: 0,
      presetValues: {},
    },
  ],
  gameRuntime: "java",
  activeProfile: "main",
  gamePresetValues: {},
  minimizeOnLaunch: true,
  clientId: publisherClientId,
  offlineUsername: "VotexPlayer",
  retention: 12,
  skins: [],
  selectedSkin: "",
  idleSkin: true,
  characterView: { ...defaultCharacterView },
  cosmetics: [
    {
      id: "clean",
      name: "Mein Look",
      skin: "",
      cape: false,
      capeData: "",
      capeCommunityId: "",
      capePublished: false,
      catalogVersion: 1,
      equipment: {},
      wings: "none",
      halo: false,
      horns: false,
      ears: false,
      crown: false,
      headphones: false,
      backpack: false,
      scarf: false,
      shoes: false,
      tail: false,
      shoulderCloud: false,
      color: "#92bfff",
      secondary: "#cfb5ff",
      scale: 100,
    },
  ],
  selectedCosmetic: "clean",
  customCosmetics: [],
  customEmotes: [],
  emoteWheel: [
    "wave",
    "sit",
    "dance",
    "point",
    "clap",
    "salute",
    "celebrate",
    "bow",
  ],
};
