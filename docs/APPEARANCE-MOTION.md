# Look and cape transitions — 2026-10-24

Appearance and Cape Crop now use the same displayed WorkspaceMotion frame as the main Votex workspace. The panel, controls, crop image/scissor and actual avatar viewport move together. Button, text-field, hover and crop input use the inverse of that displayed frame; elapsed animation time between render and input cannot move the hit region away from the visible control. Disabling menu motion uses identity coordinates.

Avatar orbit deltas and crop drag deltas now normalize the physical UI density and animation scale. Preview orbit/zoom still changes only its private render-state camera, never the player's world orientation. Removing Appearance ends a preview drag. Crop wheel input outside the visible image is no longer consumed and cannot change zoom. Crop release reports the handled gesture, including when the pointer ended outside the image. Source PNG import/export policy is unchanged.

## Native proof

One isolated client, appearance-motion-run-01, exited 0 and saved every world dimension. Four cases cover 768×480 and 1280×720 with motion enabled and disabled. The test holds a real rendered entrance frame, then advances the animation clock before clicking a visible save-button edge that an untransformed hit test would miss. It verifies the saved nametag and actual avatar viewport coordinates.

The same native screen events exercise orbit/zoom, unchanged world yaw/pitch, normalized drag distance, an unsaved Unicode nametag surviving the scale-control rebuild and return from Cape Crop, rejected wheel input outside the image, valid wheel/drag inside the moving crop, and four real local 64×32 cape exports. The private source PNG remains byte-for-byte unchanged. All 12 final screenshots were inspected: four Look views, four crop views and four panned views.

Exact proof: work/ingame-goal/appearance-motion-run-01/clientGameTest/config/votex/goal-evidence/native-appearance-motion-proof.json. That run's launch-result.json records the tested production/source hashes and process exit. Java 100 tests remain passing; production and gametest compilation plus JAR/source JAR succeed. The existing comprehensive gametest's generic click helper was subsequently updated to use displayed-frame coordinates too; that test-helper-only refinement compiles but is not represented as another full native regression run.

The fixture uses QA PNGs inside its own clientGameTest directory. It opens no system file picker, reads no user's assets, publishes nothing, and performs no account login or microphone capture. This does not add public/community or operating-system input proof. The objective's native-launch tally is now 38, including earlier failed attempts. Source is newer than immutable Preview2; no EXE, resource staging or website was replaced. Final objective acceptance remains open.
