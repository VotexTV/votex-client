# Full starter-pack world and emote interoperability, 2026-10-24

This is a development checkpoint for the active objective, not final acceptance or a new preview. The successful final run uses current production classes byte-matched to `minecraft/votex-core/build/libs/votex-client-core-0.4.1.jar`, SHA256 `793217e718412ff27d47b1cfb24cc477b125f78cebdcd2a049e1d369f2e31e3c`. All153 production classes match; the QA classes remain outside that JAR. No user profile, immutable preview EXE or website was replaced.

## Production findings and fixes

- Xaero's default minimap and the default Votex HUD occupied the same upper-left space. When Xaero Minimap is loaded, unsaved default HUD positions now start on the right. Explicitly saved positions are preserved.
- Xaero's new-waypoint default B conflicts with Votex's emote wheel. The startup adapter checks whether that waypoint preference existed before loading. It repairs only a missing preference with the unchanged conflicting default, choosing a free N/J/F7/F8 key. In this actual pack N is occupied, so J is selected and Voice's N remains intact. Existing saved custom keys are retained. A conditional Settings button offers explicit repair for older saved conflicts, with the proposed key visible before clicking.
- First-person emote frame submission alone had masked an inverted camera basis. Run05 passed functional controls, but its images showed inward waving, pointing behind the near plane and separating clapping hands. The private arm matrix now maps native player-right/down/forward onto camera-right/up/forward correctly. The original item/camera and normal rendering after fade remain intact. A headless check uses the same production placement method and actual Minecraft classic/slim baked models, requiring wave outward/up, point forward/up, clapping palm distance below0.2 and palms in front of the camera. It passed before the final game launch; final images were separately inspected.

## Final executed native check

`work/ingame-goal/modpack-compatibility-run-06/launch-result.json`: native PID26496, exit0, no Java sources changed during the run. Both current Iris shader pipelines rendered a real isolated world with no fallback/stored error: Complementary Reimagined5.9.3 and BSL10.1.8. The real18 downloaded non-Core roots and shader ZIPs matched their publisher SHA512 receipts before launch. Loaded identities/versions are retained in `runtime-manifest.json` and the native proof.

The loaded pack includes Sodium, Iris, Mod Menu, Zoomify, Lithium, FerriteCore, ImmediatelyFast, Xaero Minimap/World Map, AppleSkin, 3D Skin Layers, Simple Voice Chat, Mouse Tweaks, Cloth Config and the required Fabric/Kotlin/YACL/Placeholder dependencies. It is one installed combination, not every possible mod/version combination.

The native proof is `clientGameTest/config/votex/goal-evidence/native-modpack-proof.json`. It covers:

- actual native key events through rightShift/Escape and held/released B; all eight Votex workspaces;
- missing-default key repair, preservation of a saved custom waypoint key, and an actual Settings-button repair of a simulated old saved B conflict;
- four-corner resize, title drag, persisted floating geometry, file reload and smaller viewport clamping;
- eight actual first-person gestures,758 submitted frames, retained diamond inventory/camera and no emote renderer after fade; corrected directional geometry for both baked player models;
- front/back wave/salute/clap/celebrate with the user's local skin, Votex wings and actual 3D Skin Layers loaded;
- AppleSkin's real Mod Menu factory opening its Cloth Config screen, without editing its settings;
- each real shader's world, Votex glass module menu and first-person wave;
- dropped-item render physics, toggle returning to vanilla and unchanged30-step server item trajectories;
- no microphone permission, capture thread or recorder, with an independent QA-only hardware veto.

All46 final PNGs were reviewed on six labelled contact sheets in `review/`; the actual wave/point/clap, saved-key repair and BSL module originals were also inspected separately. Their source hashes are retained in `review/captures.json`. The final log records the player disconnect, all three dimensions saved, worker termination and normal Minecraft stop. The two passing clients and the earlier failed clients are stopped.

## Failed attempts and test-specific boundary

Six native launches were used here, including failed attempts, bringing the full recorded tally from45 to51:

1. Run01/PID15268 found the real B conflict and timed out waiting for the wheel.
2. Run02/PID23688 failed because N was occupied by another binding; that candidate must not replace Voice's preference.
3. Run03/PID5260 and run04/PID356 found an in-memory B mapping despite a genuine production-saved J. Fabric's gametest runner restores the Options-constructor snapshot before each test, which predates CLIENT_STARTED. Recognizing this fixture behavior earlier would have avoided the second repeat.
4. Run05/PID27616 exited0 with functional checks but its images exposed the first-person camera-basis defect. It is not final visual acceptance. Its Core hash is different from run06.
5. Run06/PID26496 exited0 after the matrix correction and directional checks, with final current classes and reviewed images.

The final fixture first reads the production-saved J from options.txt and fails if it is absent. It then reapplies only that actual saved value to undo Fabric's snapshot reset. It does not re-invoke the production initializer. The proof separately records the saved J, the harness-reset B and the restored saved key. Subsequent B and Settings tests use native Minecraft events. This is not an operating-system mouse/key automation claim.

Log warnings remain explicit: development classpath/reference-map/absent optional integration warnings; Windows performance-counter registry errors; offline Realms/profile-key authentication failures; a shader compile/load stall warning and BSL's ignored old block-map property; and Minecraft/Sodium's anisotropic-filtering default0 warning. Neither the launcher nor Votex source writes that filtering default in this fixture. No shader fallback or fatal render exception occurred. FPS values in captures are not a performance benchmark. No registry, driver, trust policy or authentication behavior was changed.

## Build and acceptance limits

Current Java ordinary tests:105 passed, zero failures/errors, one explicit opt-in skip. Production/test/gametest compilation, JAR and source JAR pass. The separate headless classic/slim camera-matrix check passed. Existing Rust91 and frontend93 results remain in their earlier checkpoint; these unchanged subsystems were not rerun merely to repeat results.

This closes the specifically tested installed-pack/world/shader/first-person interaction gaps. The native runtime is the development harness with exact current production classes, not an updated packaged desktop wizard or an authenticated Internet session. Remaining full-goal acceptance includes current packaged frontend/IPC refresh, community identity/publishing, final overall visual/regression review and operator-provided public/authenticated services. The user explicitly deferred physical audio. The self-host component is prepared separately; no server is deployed here. Windows automation remains stopped after the user's Escape. The full objective remains ACTIVE and existing preview artifacts remain immutable.

## Follow-up launch 07: native GLFW failure

The separately authorized offline test-profile launch `work/ingame-goal/modpack-compatibility-run-07/launch-result.json` was not a clean completion. It ran for about179 seconds and then the JVM crashed outside Java with `EXCEPTION_ACCESS_VIOLATION` in `glfw.dll+0x10fa1`, from `RenderSystem.pollEvents -> Minecraft.run`; `minecraft/votex-core/hs_err_pid14792.log` has no Votex Java frame on that crash stack. The client had initialized its 99-mod pack before the crash. This is retained as a failed native attempt and does not establish final visual acceptance. No new Minecraft launch is needed to record it.
