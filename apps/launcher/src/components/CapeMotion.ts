import { Group, Mesh } from "three";
import type { PlayerObject } from "skinview3d";

/** Uses the existing cape atlas/material; the original slab stays hidden. */
export function segmentedCape(player: PlayerObject) {
  const cape = player.cape, original = cape.cape;
  const joints: Group[] = [], meshes: Mesh[] = [];
  let parent: Group = cape;
  for (let strip = 0; strip < 4; strip++) {
    const geometry = original.geometry.clone();
    const positions = geometry.getAttribute("position");
    const uv = geometry.getAttribute("uv");
    for (let vertex = 0; vertex < positions.count; vertex++)
      positions.setY(vertex, positions.getY(vertex) / 4 - 2);
    for (const face of [0, 1, 4, 5]) {
      const start = face * 4;
      const values = [0, 1, 2, 3].map(k => uv.getY(start + k));
      const low = Math.min(...values), high = Math.max(...values);
      for (let k = 0; k < 4; k++) uv.setY(start + k, high - (high - values[k]) / 4 - strip * (high - low) / 4);
    }
    positions.needsUpdate = uv.needsUpdate = true;
    geometry.computeBoundingSphere();
    const joint = new Group();
    joint.position.y = strip ? -4 : 0;
    const mesh = new Mesh(geometry, original.material);
    mesh.position.z = .5;
    joint.add(mesh);parent.add(joint);parent = joint;
    joints.push(joint);meshes.push(mesh);
  }
  original.visible = false;
  return {
    frame(seconds: number, moving: boolean) {
      original.visible = false;
      for (let i = 1; i < joints.length; i++)
        joints[i].rotation.x = moving ? Math.sin(seconds * 1.9 - i * .55) * Math.PI / 150 : 0;
    },
    dispose() {
      joints[0].removeFromParent();
      meshes.forEach(mesh => mesh.geometry.dispose());
      original.visible = true;
    },
  };
}
