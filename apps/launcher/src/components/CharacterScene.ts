import {
  Box3,
  BoxGeometry,
  CylinderGeometry,
  Group,
  Mesh,
  MeshStandardMaterial,
  TorusGeometry,
  type BufferGeometry,
  type ColorRepresentation,
} from "three";
import type { CharacterView } from "../../../../packages/shared-config/schema";

export const lightingPresets = {
  studio: {
    sky: 0xdde8ff,
    ground: 0x20232f,
    key: 0xffead5,
    rim: 0xa9c8ff,
    exposure: 0.88,
    keyPower: 1,
    fill: 0.65,
  },
  daylight: {
    sky: 0xf3fbff,
    ground: 0x53625c,
    key: 0xfff1d8,
    rim: 0xc8ecff,
    exposure: 1.05,
    keyPower: 1.3,
    fill: 0.9,
  },
  moonlight: {
    sky: 0x8799dc,
    ground: 0x15192d,
    key: 0xb4c8ff,
    rim: 0xe4a9ff,
    exposure: 0.8,
    keyPower: 1.2,
    fill: 0.5,
  },
} as const;

export interface CharacterSceneHandle {
  group: Group;
  bounds: Box3;
  frame: (seconds: number, animate: boolean) => void;
  dispose: () => void;
}

/** Small original geometry scenes. No network assets, particle loop or texture allocation. */
export function createCharacterScene(
  preset: CharacterView["scene"],
): CharacterSceneHandle {
  const group = new Group();
  group.name = `votex-scene-${preset}`;
  // Skinview3d's neutral 32-pixel model has its sole at world Y = -16.
  group.position.y = -16;
  const geometries = new Set<BufferGeometry>();
  const materials = new Set<MeshStandardMaterial>();
  const floats: { mesh: Group; y: number; phase: number }[] = [];
  const material = (color: ColorRepresentation, emissive = false) => {
    const result = new MeshStandardMaterial({
      color,
      roughness: 0.82,
      metalness: 0.05,
      emissive: emissive ? color : 0,
      emissiveIntensity: emissive ? 0.45 : 0,
    });
    materials.add(result);
    return result;
  };
  const add = (
    geometry: BufferGeometry,
    mat: MeshStandardMaterial,
    x: number,
    y: number,
    z: number,
  ) => {
    geometries.add(geometry);
    const mesh = new Mesh(geometry, mat);
    mesh.position.set(x, y, z);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    group.add(mesh);
    return mesh;
  };
  const block = (
    w: number,
    h: number,
    d: number,
    mat: MeshStandardMaterial,
    x: number,
    y: number,
    z: number,
  ) => add(new BoxGeometry(w, h, d), mat, x, y, z);
  if (preset === "studio") {
    const slate = material(0x263448),
      silver = material(0x8597ac),
      accent = material(0x67d8f2, true);
    add(new CylinderGeometry(23, 25, 3, 48), slate, 0, -1.5, 0);
    const ring = add(new TorusGeometry(23, 0.18, 6, 64), accent, 0, 0.12, 0);
    ring.rotation.x = Math.PI / 2;
    for (let i = 0; i < 5; i++)
      block(3, 9 + i * 3, 3, silver, -19 + i * 9, (9 + i * 3) / 2, -21);
  } else if (preset === "cloud-island") {
    const rock = material(0x526479),
      grass = material(0x94b9a7),
      cloud = material(0xe7f1fa);
    block(40, 4, 30, rock, 0, -3, 0);
    block(42, 1, 32, grass, 0, -0.5, 0);
    block(28, 5, 22, rock, 2, -7, 0);
    block(12, 6, 10, rock, -3, -12, 1);
    for (const [x, y, z, size] of [
      [-25, 4, -10, 7],
      [24, 9, -13, 6],
      [-18, 15, -23, 5],
      [20, -7, 12, 5],
    ]) {
      const puff = new Group();
      puff.position.set(x, y, z);
      puff.add(block(size * 1.8, size * 0.65, size, cloud, 0, 0, 0));
      puff.add(
        block(size, size * 0.7, size, cloud, size * 0.25, size * 0.35, 0),
      );
      group.add(puff);
      floats.push({ mesh: puff, y, phase: floats.length * 1.7 });
    }
  } else {
    const paving = material(0x2b3045),
      stone = material(0x464760),
      leaf = material(0x647b91),
      glow = material(0x95b9ff, true);
    block(42, 2, 34, paving, 0, -1, 0);
    for (const x of [-19, 19]) {
      block(2, 12, 2, stone, x, 6, -13);
      block(3, 3, 3, glow, x, 13.5, -13);
      for (let i = 0; i < 3; i++)
        block(4, 3 + i * 2, 4, leaf, x, 1.5 + i, -6 + i * 7);
    }
    for (let i = 0; i < 4; i++) block(6, 0.15, 3, stone, 0, 0.1, 5 + i * 5);
  }
  group.updateMatrixWorld(true);
  const bounds = new Box3().setFromObject(group);
  let disposed = false;
  return {
    group,
    bounds,
    frame(seconds, animate) {
      for (const { mesh, y, phase } of floats)
        mesh.position.y =
          y + (animate ? Math.sin(seconds * 0.65 + phase) * 0.55 : 0);
    },
    dispose() {
      if (disposed) return;
      disposed = true;
      group.removeFromParent();
      for (const geometry of geometries) geometry.dispose();
      for (const mat of materials) mat.dispose();
      group.clear();
    },
  };
}
