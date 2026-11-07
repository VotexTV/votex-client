import { describe, it, expect } from "vitest";
import {
  primitiveSurface,
  parseCustomCosmeticFile,
  type CustomPrimitive,
} from "./customModels";
import { samplePartMotion } from "./partMotion";
const part: CustomPrimitive = {
  id: "part-12345678",
  type: "cube",
  joint: "head",
  position: [0, -10, 0],
  size: [5, 2, 5],
  rotation: [0, 0, 0],
  color: "#000000",
  animated: false,
};
describe("creator assets shared with Minecraft", () => {
  it("bakes both endpoints of a gradient into the actual exported mesh", () => {
    const model = primitiveSurface({
      ...part,
      gradient: { axis: "y", color: "#ffffff" },
    });
    expect(Math.min(...model.colors)).toBe(0);
    expect(Math.max(...model.colors)).toBe(1);
    expect(model.colors.length).toBe(model.positions.length);
  });
  it("imports and bakes a painted cube inside the per-part renderer budget", () => {
    const pixels = Array.from({ length: 64 }, (_, i) =>
      i % 2 ? "#ff0000" : "#0000ff",
    );
    const imported = parseCustomCosmeticFile({
      format: "votex-cosmetic",
      schemaVersion: 1,
      name: "Textured Hat",
      slot: "head",
      primitives: [{ ...part, paint: pixels }],
    });
    expect(imported.primitives[0].paint).toEqual(pixels);
    const model = primitiveSurface(imported.primitives[0]);
    expect(model.positions.length).toBeLessThanOrEqual(6000);
    expect(model.quads.length).toBeLessThanOrEqual(2400);
    expect(model.colors.slice(0, 3)).toEqual([0, 0, 1]);
    expect(model.colors.slice(12, 15)).toEqual([1, 0, 0]);
  });
  it("samples keyframe wings at exact keys and a continuous loop boundary", () => {
    const motion = {
      amplitude: 0.2,
      speed: 2,
      phase: 0,
      duration: 2,
      keyframes: [
        { time: 0, angle: 0 },
        { time: 0.5, angle: 1 },
        { time: 1, angle: 0 },
      ],
    };
    expect(samplePartMotion(motion, 0)).toBe(0);
    expect(samplePartMotion(motion, 0.5)).toBeCloseTo(0.5);
    expect(samplePartMotion(motion, 1)).toBe(1);
    expect(samplePartMotion(motion, 2)).toBe(0);
    expect(samplePartMotion(motion, 2 - 1e-5)).toBeCloseTo(0, 7);
  });
  it("rejects nonordered part animation keys before a model can be saved", () => {
    expect(() =>
      parseCustomCosmeticFile({
        format: "votex-cosmetic",
        schemaVersion: 1,
        name: "Invalid",
        slot: "head",
        primitives: [
          {
            ...part,
            animated: true,
            animation: {
              axis: "y",
              amplitude: 0.2,
              speed: 2,
              phase: 0,
              keyframes: [
                { time: 0, angle: 0 },
                { time: 0, angle: 1 },
              ],
            },
          },
        ],
      }),
    ).toThrow();
  });
});
