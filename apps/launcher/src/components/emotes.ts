import timing from "../../../../packages/shared-config/emote-timing.json";
import type { AppState } from "../../../../packages/shared-config/schema";
export const emotes = [
  {
    id: "wave",
    name: "Winken",
    hint: "Ein lockeres Hallo mit Blickkontakt",
    mood: "Friendly",
  },
  {
    id: "sit",
    name: "Sitzen",
    hint: "Ankommen. Durchatmen. Aussicht genießen.",
    mood: "Relaxed",
  },
  {
    id: "dance",
    name: "Groove",
    hint: "Gewicht verlagern, Schultern mitnehmen",
    mood: "Playful",
  },
  {
    id: "point",
    name: "Da entlang",
    hint: "Mit einem kleinen Blick in die Richtung",
    mood: "Adventure",
  },
  {
    id: "clap",
    name: "Applaus",
    hint: "Drei weiche, rhythmische Bewegungen",
    mood: "Together",
  },
  {
    id: "salute",
    name: "Salut",
    hint: "Aufrecht, ruhig und mit einem Nicken",
    mood: "Ready",
  },
  {
    id: "celebrate",
    name: "Geschafft!",
    hint: "Beide Hände hoch, entspannt zurück",
    mood: "Victory",
  },
  {
    id: "bow",
    name: "Verbeugen",
    hint: "Ein langsamer respektvoller Bogen",
    mood: "Graceful",
  },
] as const;
export type EmoteId = (typeof emotes)[number]["id"] | `custom-emote-${string}`;
export type CustomEmote = AppState["customEmotes"][number];
export const jointNames = [
  "head",
  "body",
  "leftArm",
  "rightArm",
  "leftLeg",
  "rightLeg",
] as const;
export type Pose = number[];
export const smooth = (t: number) => {
  const v = Math.max(0, Math.min(1, t));
  return v * v * v * (v * (v * 6 - 15) + 10);
};
export function envelope(t: number) {
  return t < 0 || t >= timing.duration
    ? 0
    : Math.min(
        smooth(t / timing.enter),
        smooth((timing.duration - t) / timing.exit),
      );
}
export function rawPose(id: EmoteId, t: number): Pose {
  const p = Array<number>(19).fill(0),
    wave = Math.sin(t * 5.5); // six Euler joints + vertical offset
  if (id === "wave") {
    p[9] = -0.18;
    p[11] = -2.35 + Math.sin(t * 7) * 0.24;
    p[1] = -0.09;
    p[8] = 0.08;
  }
  if (id === "sit") {
    p[12] = p[15] = -1.42;
    p[14] = 0.16;
    p[17] = -0.16;
    p[6] = p[9] = -0.18;
    p[8] = 0.12;
    p[11] = -0.12;
    p[18] = -7.5;
    p[0] = 0.04;
  }
  if (id === "dance") {
    const beat = Math.sin(t * 3.7);
    p[5] = beat * 0.12;
    p[6] = -0.2 + Math.sin(t * 3.7 + 0.6) * 0.22;
    p[9] = -0.2 - Math.sin(t * 3.7 + 0.6) * 0.22;
    p[8] = 0.42 + Math.sin(t * 3.7) * 0.2;
    p[11] = -0.42 + Math.sin(t * 3.7) * 0.2;
    p[12] = Math.sin(t * 3.7) * 0.22;
    p[15] = -Math.sin(t * 3.7) * 0.22;
    p[18] = (1 - Math.cos(t * 7.4)) * 0.3;
    p[1] = -beat * 0.12;
  }
  if (id === "point") {
    p[9] = -1.48;
    p[10] = -0.15;
    p[1] = -0.22;
    p[5] = -0.025;
  }
  if (id === "clap") {
    p[6] = p[9] = -1.18;
    p[7] = -0.16;
    p[10] = 0.16;
    const clap = (1 - Math.cos(t * 5.5)) * 0.5;
    p[8] = -0.24 - clap * 0.36;
    p[11] = 0.24 + clap * 0.36;
    p[0] = 0.04;
  }
  if (id === "salute") {
    p[9] = -1.87;
    p[11] = -0.46;
    p[0] = Math.sin(t * 2) * 0.035;
  }
  if (id === "celebrate") {
    p[8] = 2.58 + Math.sin(t * 4.5) * 0.08;
    p[11] = -2.58 - Math.sin(t * 4.5) * 0.08;
    p[18] = (1 - Math.cos(t * 4.5)) * 0.35;
    p[0] = -0.1;
  }
  if (id === "bow") {
    const bend = 0.55 * Math.sin(Math.PI * Math.min(1, t / timing.duration));
    p[3] = bend;
    p[0] = bend * 0.35;
    p[6] = p[9] = -bend * 0.5;
    p[18] = -bend * 0.6;
  }
  return p;
}
/** Critically damped weights preserve position AND velocity on interruption. No viewer reset. */
export class EmoteMotion {
  private time = 0;
  private starts = emotes.map(() => 0);
  private weights = emotes.map(() => 0);
  private velocity = emotes.map(() => 0);
  private active: EmoteId | null = null;
  private clock = 0;
  private rendered = Array<number>(19).fill(0);
  private inertia = Array<number>(19).fill(0);
  private customWeight = 0;
  private customVelocity = 0;
  private customClock = 0;
  private custom: CustomEmote | null = null;
  loop = true;
  speed = 1;
  request(id: EmoteId | null, customEmotes: CustomEmote[] = []) {
    this.active = id && emotes.some((item) => item.id === id) ? id as (typeof emotes)[number]["id"] : null;
    this.custom = id?.startsWith("custom-emote-") ? customEmotes.find((item) => item.id === id) ?? null : null;
    if (id && (this.active || this.custom)) {
      this.clock = 0;
      this.customClock = 0;
      if (this.active) this.starts[emotes.findIndex((e) => e.id === this.active)] = this.time;
    }
  }
  get phase() {
    const duration = this.custom?.duration ?? timing.duration;
    if (!this.active && !this.custom) return this.settled ? "idle" : "exit";
    const t = this.loop
      ? this.clock % (duration + timing.rest)
      : this.clock;
    return t >= duration
      ? this.settled
        ? "idle"
        : "exit"
      : t < timing.enter
        ? "enter"
        : t < duration - Math.min(timing.exit, duration * 0.18)
          ? "hold"
          : t < duration
            ? "exit"
            : "idle";
  }
  get wantsFrames() {
    return (
      (this.active !== null || this.custom !== null) && (this.loop || this.clock < (this.custom?.duration ?? timing.duration) + 1.4)
    );
  }
  get progress() {
    return Math.min(1, this.clock / (this.custom?.duration ?? timing.duration));
  }
  get settled() {
    return (
      this.weights.every(
        (w, i) => Math.abs(w) < 0.0007 && Math.abs(this.velocity[i]) < 0.006,
      ) &&
      this.rendered.every(
        (v, i) => Math.abs(v) < 0.0015 && Math.abs(this.inertia[i]) < 0.008,
      ) && Math.abs(this.customWeight) < 0.0007 && Math.abs(this.customVelocity) < 0.006
    );
  }
  get complete() {
    return !!(this.active || this.custom) && !this.loop && this.clock > (this.custom?.duration ?? timing.duration) + 1;
  }
  advance(delta: number): Pose {
    if (!Number.isFinite(delta) || delta <= 0) return this.rendered.slice();
    // Preserve elapsed animation time on slower machines. The old 50 ms cap
    // made previews run in slow motion below 20 FPS.
    let remaining = Math.min(1, delta), result = this.rendered.slice();
    while (remaining > 1e-8) {
      const step = Math.min(.025, remaining);
      result = this.integrate(step);
      remaining -= step;
    }
    return result;
  }
  private integrate(delta: number): Pose {
    const dt = delta * this.speed;
    this.time += dt;
    this.clock += dt;
    const duration = this.custom?.duration ?? timing.duration;
    if ((this.active || this.custom) && this.loop && this.clock > duration + timing.rest) {
      this.clock %= duration + timing.rest;
      if (this.active) this.starts[emotes.findIndex((e) => e.id === this.active)] = this.time - this.clock;
      this.customClock = this.clock;
    }
    let sum = 0;
    const omega = timing.spring;
    for (let i = 0; i < emotes.length; i++) {
      const goal = emotes[i].id === this.active ? (this.custom ? smooth(this.clock / Math.min(0.22, duration * 0.18)) * smooth((duration - this.clock) / Math.min(0.22, duration * 0.18)) : envelope(this.clock)) : 0;
      const x = this.weights[i] - goal,
        decay = Math.exp(-omega * dt),
        temp = (this.velocity[i] + omega * x) * dt;
      this.velocity[i] = (this.velocity[i] - omega * temp) * decay;
      this.weights[i] = goal + (x + temp) * decay;
      sum += Math.max(0, this.weights[i]);
    }
    const customEdge = Math.min(0.22, duration * 0.18);
    const customGoal = this.custom ? Math.min(smooth(this.clock / customEdge), smooth((duration - this.clock) / customEdge)) : 0;
    const customDelta = this.customWeight - customGoal;
    const customDecay = Math.exp(-timing.spring * dt);
    const customTemp = (this.customVelocity + timing.spring * customDelta) * dt;
    this.customVelocity = (this.customVelocity - timing.spring * customTemp) * customDecay;
    this.customWeight = customGoal + (customDelta + customTemp) * customDecay;
    sum += Math.max(0, this.customWeight);
    const result = Array<number>(19).fill(0),
      scale = sum > 1 ? 1 / sum : 1;
    for (let i = 0; i < emotes.length; i++) {
      const pose = rawPose(emotes[i].id, this.time - this.starts[i]);
      for (let k = 0; k < result.length; k++)
        result[k] += pose[k] * Math.max(0, this.weights[i]) * scale;
    }
    if (this.custom) {
      const t = Math.max(0, Math.min(1, this.clock / this.custom.duration));
      let left = this.custom.keyframes[0], right = this.custom.keyframes.at(-1)!;
      for (let i = 1; i < this.custom.keyframes.length; i++) if (t <= this.custom.keyframes[i].time) { left = this.custom.keyframes[i - 1]; right = this.custom.keyframes[i]; break; }
      const mix = smooth((t - left.time) / Math.max(0.0001, right.time - left.time));
      for (let k = 0; k < result.length; k++) result[k] += (left.pose[k] + (right.pose[k] - left.pose[k]) * mix) * Math.max(0, this.customWeight) * scale;
    }
    for (let i = 0; i < result.length; i++) {
      const x = this.rendered[i] - result[i],
        decay = Math.exp(-22 * dt),
        temp = (this.inertia[i] + 22 * x) * dt;
      this.inertia[i] = (this.inertia[i] - 22 * temp) * decay;
      this.rendered[i] = result[i] + (x + temp) * decay;
    }
    return this.rendered.slice();
  }
}
