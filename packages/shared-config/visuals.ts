import type { Theme, VisualLevel } from "./schema";
const effectKeys = [
  "transparency",
  "blur",
  "wobble",
  "speed",
  "saturation",
  "glow",
  "reflection",
  "shadow",
  "stiffness",
  "damping",
  "hover",
  "reducedMotion",
  "proximity",
  "lift",
  "tilt",
  "ambientMotion",
  "glassEffect",
  "glowEffect",
  "reflectionEffect",
  "proximityEffect",
  "wobbleEffect",
] as const;
export type Effects = Pick<Theme, (typeof effectKeys)[number]>;
export const visualProfiles: {
  id: VisualLevel;
  name: string;
  hint: string;
  detail: string;
  activity: string;
  values: Effects;
}[] = [
  {
    id: "ultra-performance",
    name: "Ultra Performance",
    hint: "Nur das Wesentliche",
    detail:
      "Deckende Flächen, kein Hintergrund-Blur, kein Leuchten und keine laufende Bewegung. Die Oberfläche bleibt vollständig bedienbar.",
    activity: "Keine Näherungs- oder Hintergrundanimation",
    values: {
      transparency: 100,
      blur: 0,
      wobble: 0,
      speed: 80,
      saturation: 25,
      glow: 0,
      reflection: 0,
      shadow: 8,
      stiffness: 65,
      damping: 80,
      hover: false,
      reducedMotion: true,
      proximity: 0,
      lift: 0,
      tilt: 0,
      ambientMotion: false,
      glassEffect: false,
      glowEffect: false,
      reflectionEffect: false,
      proximityEffect: false,
      wobbleEffect: false,
    },
  },
  {
    id: "performance",
    name: "Performance",
    hint: "Ruhig & ausgewogen",
    detail:
      "Der vertraute Votex-Look: zurückhaltendes Glas und kurze direkte Hover-Übergänge. Ohne Näherungstracking.",
    activity: "Dezente Effekte bei direkter Bedienung",
    values: {
      transparency: 32,
      blur: 28,
      wobble: 24,
      speed: 65,
      saturation: 55,
      glow: 20,
      reflection: 28,
      shadow: 35,
      stiffness: 55,
      damping: 70,
      hover: true,
      reducedMotion: false,
      proximity: 0,
      lift: 18,
      tilt: 0,
      ambientMotion: false,
      glassEffect: true,
      glowEffect: true,
      reflectionEffect: true,
      proximityEffect: false,
      wobbleEffect: true,
    },
  },
  {
    id: "high",
    name: "High",
    hint: "Glas, Licht & Tiefe",
    detail:
      "Weichere Glasflächen, Licht an der Maus und sanft angehobene Karten, sobald du dich näherst. Für ein lebendigeres Desktop-Erlebnis.",
    activity: "Ereignisgesteuertes Näherungslicht",
    values: {
      transparency: 23,
      blur: 56,
      wobble: 42,
      speed: 56,
      saturation: 67,
      glow: 50,
      reflection: 60,
      shadow: 55,
      stiffness: 48,
      damping: 62,
      hover: true,
      reducedMotion: false,
      proximity: 48,
      lift: 50,
      tilt: 24,
      ambientMotion: true,
      glassEffect: true,
      glowEffect: true,
      reflectionEffect: true,
      proximityEffect: true,
      wobbleEffect: true,
    },
  },
  {
    id: "ultra",
    name: "Ultra High Core",
    hint: "Design ohne Zurückhaltung",
    detail:
      "Starkes Glas, weite Lichtfelder, räumliche Karten und bewegte Atmosphäre. Das aufwendigste Erscheinungsbild; keine Zusage für unveränderte GPU-Last.",
    activity: "Maximale Gestaltung, begrenzte Effektreichweite",
    values: {
      transparency: 16,
      blur: 86,
      wobble: 65,
      speed: 45,
      saturation: 80,
      glow: 86,
      reflection: 90,
      shadow: 80,
      stiffness: 42,
      damping: 55,
      hover: true,
      reducedMotion: false,
      proximity: 90,
      lift: 84,
      tilt: 55,
      ambientMotion: true,
      glassEffect: true,
      glowEffect: true,
      reflectionEffect: true,
      proximityEffect: true,
      wobbleEffect: true,
    },
  },
];
export function effectValues(theme: Theme): Effects {
  return Object.fromEntries(effectKeys.map((k) => [k, theme[k]])) as Effects;
}
export function selectVisualProfile(theme: Theme, level: VisualLevel): Theme {
  const saved = {
    ...theme.effectProfiles,
    [theme.renderProfile]: effectValues(theme),
  };
  const values =
    saved[level] ?? visualProfiles.find((p) => p.id === level)!.values;
  return { ...theme, ...values, renderProfile: level, effectProfiles: saved };
}
export function resetVisualProfile(theme: Theme): Theme {
  const values = visualProfiles.find(
    (p) => p.id === theme.renderProfile,
  )!.values;
  return {
    ...theme,
    ...values,
    effectProfiles: {
      ...theme.effectProfiles,
      [theme.renderProfile]: { ...values },
    },
  };
}
export function effectActivity(theme: Theme) {
  if (theme.reducedMotion || !theme.hover || !theme.proximityEffect || theme.proximity === 0)
    return { proximity: false, maxCards: 0, radius: 0 };
  return {
    proximity: true,
    maxCards: theme.renderProfile === "ultra" ? 32 : 20,
    radius: 40 + theme.proximity * 1.6,
  };
}

export interface MagneticState {
  x: number;
  y: number;
  velocityX: number;
  velocityY: number;
}
export function interactionResponse(theme: Pick<Theme, "speed" | "stiffness" | "damping">): number {
  return Math.max(8, Math.min(32, (12 + theme.speed * .10 + theme.stiffness * .055) / (.75 + theme.damping * .006)));
}

export function advanceMagneticState(
  state: MagneticState,
  target: { x: number; y: number },
  deltaSeconds: number,
  maxDisplacement = 4,
  response = 18,
): MagneticState {
  const dt = Math.max(0, Math.min(deltaSeconds, 0.05));
  const limit = Math.max(0, maxDisplacement);
  const omega = Number.isFinite(response) ? Math.max(8, Math.min(32, response)) : 18;
  const decay = Math.exp(-omega * dt);
  const axis = (position: number, velocity: number, destination: number) => {
    const boundedTarget = Math.max(-limit, Math.min(limit, destination));
    const offset = position - boundedTarget;
    const derivative = velocity + omega * offset;
    const nextPosition = boundedTarget + (offset + derivative * dt) * decay;
    const nextVelocity = (velocity - omega * derivative * dt) * decay;
    const boundedPosition = Math.max(-limit, Math.min(limit, nextPosition));
    return {
      position: boundedPosition,
      velocity: boundedPosition === nextPosition ? nextVelocity : 0,
    };
  };
  const x = axis(state.x, state.velocityX, target.x);
  const y = axis(state.y, state.velocityY, target.y);
  return {
    x: x.position,
    y: y.position,
    velocityX: x.velocity,
    velocityY: y.velocity,
  };
}
