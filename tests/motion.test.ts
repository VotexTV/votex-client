import { describe, it, expect } from "vitest";
import {
  EmoteMotion,
  envelope,
  emotes,
  smooth,
} from "../apps/launcher/src/components/emotes";
import timing from "../packages/shared-config/emote-timing.json";
import { readFileSync } from "node:fs";
const frames = (m: EmoteMotion, seconds: number, fps = 60) => {
  let p: number[] = [];
  for (let t = 0; t < Math.round(seconds * fps); t++) p = m.advance(1 / fps);
  return p;
};
describe("continuous gesture performance", () => {
  it("enters and exits with zero endpoint slope, including the start of the ease", () => {
    expect(envelope(0)).toBe(0);
    expect(envelope(timing.duration)).toBe(0);
    expect(envelope(timing.enter)).toBeCloseTo(1, 10);
    expect(envelope(timing.duration - timing.exit)).toBeCloseTo(1, 10);
    expect(envelope(0.0001) / 0.0001).toBeLessThan(0.0001);
    expect(envelope(timing.duration - 0.0001) / 0.0001).toBeLessThan(0.0001);
    expect(smooth(0.5)).toBe(0.5);
  });
  it("keeps moving poses continuous on switch, same-gesture replay and stop", () => {
    const m = new EmoteMotion();
    m.request("wave");
    let previous = frames(m, 1.3);
    for (const next of ["sit", "sit", "dance", null] as const) {
      m.request(next);
      const after = m.advance(1 / 240);
      expect(
        Math.max(...after.map((v, i) => Math.abs(v - previous[i]))),
      ).toBeLessThan(0.06);
      previous = frames(m, 0.8);
    }
    const idle = frames(m, 2);
    expect(Math.max(...idle.map(Math.abs))).toBeLessThan(0.002);
    expect(m.settled).toBe(true);
    expect(m.wantsFrames).toBe(false);
  });
  it("is stable across frame rates and all gesture shapes finish in neutral pose", () => {
    for (const e of emotes) {
      const a = new EmoteMotion(),
        b = new EmoteMotion();
      a.loop = b.loop = false;
      a.request(e.id);
      b.request(e.id);
      const pa = frames(a, 2, 60),
        pb = frames(b, 2, 120);
      expect(Math.max(...pa.map((v, i) => Math.abs(v - pb[i])))).toBeLessThan(
        0.045,
      );
      const neutral = frames(a, 7);
      expect(neutral.every(Number.isFinite)).toBe(true);
      expect(Math.max(...neutral.map(Math.abs))).toBeLessThan(0.002);
      expect(a.complete).toBe(true);
      expect(a.phase).toBe("idle");
    }
  });
  it("continues through the quiet loop interval without freezing the render loop", () => {
    const m = new EmoteMotion();
    m.request("point");
    frames(m, timing.duration + 0.8);
    expect(m.wantsFrames).toBe(true);
    frames(m, 0.3);
    expect(m.phase).toBe("enter");
    expect(m.wantsFrames).toBe(true);
  });
  it("shares timing with the game Core instead of a different preview timeline", () => {
    const inGame = JSON.parse(
      readFileSync(
        "minecraft/votex-core/src/main/resources/assets/votex_client/emote-timing.json",
        "utf8",
      ).replace(/^\uFEFF/, ""),
    );
    expect(inGame).toEqual(timing);
  });
});
