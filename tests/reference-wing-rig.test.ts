import { expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { Mesh, Vector3 } from "three";
import { buildSurfaceModel } from "../apps/launcher/src/components/Wearables";
import { previewMotionAngle } from "../apps/launcher/src/data/partMotion";
import type { CosmeticModel } from "../apps/launcher/src/data/cosmeticCatalog";

const ids = ["vanilla-feather-wings", "crimson-dread-wings", "autumn-canopy-wings", "ivory-seraph-wings", "obsidian-seraph-wings"];
const unit = (axis: string) => new Vector3(axis === "x" ? 1 : 0, axis === "y" ? 1 : 0, axis === "z" ? 1 : 0);
const basis = (p: Vector3) => new Vector3(p.x, -p.y, -p.z);

it("keeps both wing hinges in the native attachment space, including depth folding", () => {
  for (const id of ids) {
    const model = JSON.parse(readFileSync(`apps/launcher/public/assets/catalog/${id}.json`, "utf8")) as CosmeticModel;
    const built = buildSurfaceModel(model, false);
    let depthHinges = 0;
    for (const root of built.roots) {
      const motion = root.motion;
      if (motion?.type !== "wing" || !root.secondary || !motion.secondaryPivot) continue;
      const part = model.parts.find(p => p.motion === motion)!;
      const primaryAxis = motion.axis ?? "z", secondaryAxis = motion.secondaryAxis ?? primaryAxis;
      const primary = .24 * motion.side, secondary = -.11 * motion.side;
      root.group.rotation[primaryAxis] = previewMotionAngle(primaryAxis, primary);
      root.secondary.rotation[secondaryAxis] = previewMotionAngle(secondaryAxis, secondary);
      root.group.updateMatrixWorld(true);
      const mesh = root.secondary.children[0] as Mesh;
      const p = new Vector3().fromBufferAttribute(mesh.geometry.getAttribute("position"), 0).applyMatrix4(mesh.matrixWorld);
      const pivot = new Vector3(...motion.pivot), second = new Vector3(...motion.secondaryPivot);
      const expected = basis(new Vector3(...part.positions.slice(0, 3) as [number, number, number])
        .sub(second).applyAxisAngle(unit(secondaryAxis), secondary).add(second)
        .sub(pivot).applyAxisAngle(unit(primaryAxis), primary).add(pivot));
      expect(p.distanceTo(expected), id).toBeLessThan(1e-5);
      if (secondaryAxis === "y") depthHinges++;
    }
    expect(depthHinges, id).toBeGreaterThanOrEqual(2);
    built.dispose();
  }
});
