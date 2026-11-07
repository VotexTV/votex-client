import { Mesh, type BufferGeometry, type Material } from "three";
import type { SkinViewer } from "skinview3d";

/** Skinview3d.dispose() releases its textures/renderer but not model buffers or
 * compositor targets. Release those owners too before retiring the context. */
export function releaseSkinViewer(viewer: SkinViewer) {
  const geometry = new Set<BufferGeometry>(),
    materials = new Set<Material>();
  viewer.scene.traverse((object) => {
    if (object instanceof Mesh) {
      geometry.add(object.geometry);
      for (const material of Array.isArray(object.material)
        ? object.material
        : [object.material])
        materials.add(material);
    }
  });
  viewer.composer.dispose();
  viewer.dispose();
  for (const value of geometry) value.dispose();
  for (const value of materials) value.dispose();
  viewer.renderer.forceContextLoss();
}
