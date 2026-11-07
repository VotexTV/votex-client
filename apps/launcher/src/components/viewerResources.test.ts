import { describe, it, expect, vi } from "vitest";
import { Scene, Mesh, BoxGeometry, MeshStandardMaterial } from "three";
import type { SkinViewer } from "skinview3d";
import { releaseSkinViewer } from "./viewerResources";

describe("viewer resource ownership", () => {
  it("releases shared mesh resources once, compositor targets and the retired WebGL context", () => {
    const scene = new Scene(),
      geometry = new BoxGeometry(),
      material = new MeshStandardMaterial();
    scene.add(new Mesh(geometry, material), new Mesh(geometry, material));
    let buffers = 0,
      materials = 0;
    geometry.addEventListener("dispose", () => buffers++);
    material.addEventListener("dispose", () => materials++);
    const composerDispose = vi.fn(),
      viewerDispose = vi.fn(),
      loseContext = vi.fn();
    releaseSkinViewer({
      scene,
      composer: { dispose: composerDispose },
      dispose: viewerDispose,
      renderer: { forceContextLoss: loseContext },
    } as unknown as SkinViewer);
    expect(buffers).toBe(1);
    expect(materials).toBe(1);
    expect(composerDispose).toHaveBeenCalledOnce();
    expect(viewerDispose).toHaveBeenCalledOnce();
    expect(loseContext).toHaveBeenCalledOnce();
  });
});
