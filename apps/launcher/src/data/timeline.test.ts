import { describe, it, expect } from "vitest";
import { insertEmoteFrame } from "./emoteEditor";
import { frameAt, moveFrame, interpolateAt } from "./timeline";
import { primitiveSurface, type CustomPrimitive } from "./customModels";
describe("precise editing", () => {
  it("inserts at the chosen time without moving existing poses", () => {
    const draft = {
      keyframeTimes: [0, 0.5, 1],
      verticalOffsets: [0, 2, 0],
      tracks: [
        { joint: "rightArm" as const, axis: "z" as const, values: [0, 90, 0] },
      ],
    };
    const next = insertEmoteFrame(draft, 0.37);
    expect(next.keyframeTimes).toEqual([0, 0.37, 0.5, 1]);
    expect(next.index).toBe(1);
    expect(next.tracks[0].values[1]).toBeCloseTo(
      interpolateAt(draft.keyframeTimes, draft.tracks[0].values, 0.37),
    );
    expect(next.tracks[0].values.slice(2)).toEqual([90, 0]);
    expect(draft.keyframeTimes).toEqual([0, 0.5, 1]);
  });
  it("selects an existing key instead of piling up duplicates", () => {
    const d = {
      keyframeTimes: [0, 0.5, 1],
      tracks: [],
      verticalOffsets: [0, 1, 0],
    };
    expect(insertEmoteFrame(d, 0.5).keyframeTimes).toEqual(d.keyframeTimes);
    expect(frameAt(d.keyframeTimes, 0.5)).toBe(1);
  });
  it("protects endpoints and chronological ordering while dragging", () => {
    const times = [0, 0.3, 0.7, 1];
    expect(moveFrame(times, 0, 0.4)).toBe(0);
    expect(moveFrame(times, 3, 0.4)).toBe(1);
    expect(moveFrame(times, 1, 0.95)).toBe(0.699);
    expect(moveFrame(times, 2, 0.01)).toBe(0.301);
  });
  it("scales voxel pixels on all three axes in exported geometry", () => {
    const part: CustomPrimitive = {
      id: "part-12345678",
      type: "pixel",
      joint: "body",
      position: [0, 0, 0],
      rotation: [0, 0, 0],
      size: [2, 4, 8],
      color: "#ffffff",
      animated: false,
    };
    const surface = primitiveSurface(part);
    for (const [axis, extent] of [2, 4, 8].entries()) {
      const positions = surface.positions.filter((_, i) => i % 3 === axis);
      expect(Math.max(...positions) - Math.min(...positions)).toBe(extent);
    }
  });
});
