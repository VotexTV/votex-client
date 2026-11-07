import { describe, expect, it } from "vitest";
import { PlayerObject } from "skinview3d";
import { Mesh } from "three";
import { segmentedCape } from "../apps/launcher/src/components/CapeMotion";

describe("connected cape preview", () => {
  it("replaces the original slab, keeps atlas ranges continuous and releases its geometry", () => {
    const player = new PlayerObject(), original = player.cape.cape;
    const motion = segmentedCape(player);
    expect(original.visible).toBe(false);
    const strips: Mesh[] = [];
    player.cape.traverse(node => { if (node instanceof Mesh && node !== original) strips.push(node); });
    expect(strips).toHaveLength(4);
    for (let i = 0; i < 4; i++) {
      const positions = strips[i].geometry.getAttribute("position");
      expect(Math.min(...Array.from({length: positions.count}, (_, k) => positions.getY(k)))).toBe(-4);
      expect(Math.max(...Array.from({length: positions.count}, (_, k) => positions.getY(k)))).toBe(0);
      if (i) {
        const upper = strips[i-1].geometry.getAttribute("uv"), lower = strips[i].geometry.getAttribute("uv");
        const v = (attribute: typeof upper) => [0,1,2,3].map(k => attribute.getY(16+k));
        expect(Math.min(...v(upper))).toBeCloseTo(Math.max(...v(lower)), 6);
      }
    }
    motion.frame(2, true);
    expect(original.visible).toBe(false);
    motion.dispose();
    expect(original.visible).toBe(true);
    expect(player.cape.children).toEqual([original]);
  });
});
