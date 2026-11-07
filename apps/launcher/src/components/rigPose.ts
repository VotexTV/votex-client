import type { PlayerObject } from "skinview3d";
import { jointNames } from "./emotes";
/** Shared pose placement for the player showcase and the editor; all angles are radians. */
export function applyRigPose(player: PlayerObject, pose: number[]) {
  player.resetJoints();
  player.position.y = pose[18] || 0;
  jointNames.forEach((joint, i) =>
    player.skin[joint].rotation.set(
      pose[i * 3] || 0,
      pose[i * 3 + 1] || 0,
      pose[i * 3 + 2] || 0,
    ),
  );
  const bend = pose[3] || 0,
    roll = pose[5] || 0;
  player.skin.body.position.y = -12 + 6 * Math.cos(bend);
  player.skin.body.position.z = 6 * Math.sin(bend);
  player.skin.head.position.y = -12 + 12 * Math.cos(bend);
  player.skin.head.position.z = 12 * Math.sin(bend);
  player.skin.head.position.x = -Math.sin(roll) * 9;
  player.skin.head.rotation.z += roll * 0.4;
  player.skin.leftArm.position.y =
    -12 + 10 * Math.cos(bend) + Math.sin(roll) * 5;
  player.skin.rightArm.position.y =
    -12 + 10 * Math.cos(bend) - Math.sin(roll) * 5;
  player.skin.leftArm.position.z = player.skin.rightArm.position.z =
    10 * Math.sin(bend);
  player.skin.leftArm.rotation.z += roll * 0.5;
  player.skin.rightArm.rotation.z += roll * 0.5;
}
