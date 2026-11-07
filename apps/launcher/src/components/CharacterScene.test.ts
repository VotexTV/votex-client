import { describe, it, expect } from "vitest";
import { Mesh, Scene, type BufferGeometry, type Material } from "three";
import { createCharacterScene } from "./CharacterScene";
import { pauseAfterDraw } from "./PreviewActivity";

describe("character scenes and rendering lifecycle", () => {
  it.each(["cloud-island", "studio", "night-garden"] as const)(
    "bounds %s and disposes its geometry and shared materials exactly once",
    (preset) => {
      const scene = new Scene(),
        handle = createCharacterScene(preset);
      scene.add(handle.group);
      const geometry = new Set<BufferGeometry>(),
        materials = new Set<Material>();
      let meshes = 0;
      handle.group.traverse((object) => {
        if (object instanceof Mesh) {
          meshes++;
          geometry.add(object.geometry);
          for (const mat of Array.isArray(object.material)
            ? object.material
            : [object.material])
            materials.add(mat);
        }
      });
      expect(meshes).toBeGreaterThan(5);
      expect(meshes).toBeLessThan(25);
      expect(handle.bounds.isEmpty()).toBe(false);
      for (const number of [
        ...handle.bounds.min.toArray(),
        ...handle.bounds.max.toArray(),
      ])
        expect(Number.isFinite(number) && Math.abs(number) < 80).toBe(true);
      let disposed = 0;
      for (const item of geometry)
        item.addEventListener("dispose", () => disposed++);
      for (const item of materials)
        item.addEventListener("dispose", () => disposed++);
      handle.dispose();
      handle.dispose();
      expect(disposed).toBe(geometry.size + materials.size);
      expect(scene.children).toHaveLength(0);
    },
  );
  it("resets optional cloud movement to rest when animation is disabled", () => {
    const handle = createCharacterScene("cloud-island");
    const positions = () =>
      handle.group.children.map((child) => child.position.toArray());
    const rest = positions();
    handle.frame(4, true);
    expect(positions()).not.toEqual(rest);
    handle.frame(9, false);
    expect(positions()).toEqual(rest);
    handle.dispose();
  });
  it("cancels the frame queued after the renderer animation callback", async () => {
    let pending: number | null = null;
    const cancelled: number[] = [];
    const viewer = {
      get renderPaused() {
        return pending === null;
      },
      set renderPaused(value: boolean) {
        if (value && pending !== null) {
          cancelled.push(pending);
          pending = null;
        }
      },
    };
    pauseAfterDraw(viewer, () => true);
    // Equivalent to Skinview3d.draw() queuing the next frame after animation.update().
    pending = 52;
    await Promise.resolve();
    expect(cancelled).toEqual([52]);
    expect(pending).toBe(null);
    let idle = true;
    pauseAfterDraw(viewer, () => idle);
    pending = 53;
    idle = false;
    await Promise.resolve();
    expect(pending).toBe(53);
  });
});
