import { expect, it } from "vitest";
import { Vector3 } from "three";
import { previewMotionAngle } from "../apps/launcher/src/data/partMotion";

it("moves converted mesh points to the same position in both renderers", () => {
  const basis = (p: Vector3) => new Vector3(p.x, -p.y, -p.z);
  for (const axis of ["x", "y", "z"] as const) for (const angle of [-.7, .2, .6]) {
    const unit = new Vector3(axis === "x" ? 1 : 0, axis === "y" ? 1 : 0, axis === "z" ? 1 : 0);
    const point = new Vector3(-3, 7, 4);
    const native = basis(point.clone().applyAxisAngle(unit, angle));
    const preview = basis(point).applyAxisAngle(unit, previewMotionAngle(axis, angle));
    expect(preview.distanceTo(native)).toBeLessThan(1e-10);
  }
});
