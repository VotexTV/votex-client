# Consolidated native regression and real package installation — 2026-10-24

This is an active-development checkpoint, not final acceptance or a new preview EXE.

## Native regression

`work/ingame-goal/full-regression-run-04/launch-result.json` records one real private Fabric26.2 client, PID29028, exit0, and unchanged Java sources throughout that run. Its `clientGameTest/config/votex/goal-evidence/native-goal-proof.json` ends at `regression-native-webp-and-cache`, status `passed`. The integrated server saved all dimensions before shutdown.

This combines the earlier functional suite with the recent UI checks in one process: actual right-Shift/B/Escape bindings; all eight workspaces; compact controls and clipped-row/header input; loaded sans font; HUD layout/styles/docking at several GUI scales; appearance and cape import/export; custom emotes and all nine front/back gestures; four playable arcade games; real Item Physics render submissions and unchanged server trajectories; one integrated Fabric relay; Modrinth HTTPS discovery; local JAR and shader ZIP staging; private chat copy/search/reply fixtures; live synthetic Friends pagination; Voice cancellation; social feedback; Look/Cape transitions; installation feedback; displayed-frame opening input; actual WebP logos and bounded texture eviction/restoration.

The Mod Library previously lacked the shared workspace entrance. It now retains its displayed `WorkspaceMotion` frame and uses that frame's inverse for clicks/releases/drags/hover. Four native motion/static cases at768×480 and1280×720 passed visible-edge Back input, including advancing the clock after rendering. The four original PNGs were inspected individually. All185 evidence PNGs were also reviewed on eight labelled contact sheets under `visual-review`; this is a layout/pose review at thumbnail size, not full-resolution inspection of every pixel. The separate exported-image files are not counted as185 screen captures.

Three failed attempts remain separate evidence:

- Run01 failed because its17-character fixture username could not log into the private world. The runner now validates1–16 ASCII name characters before launching.
- Run02 exposed a stale assertion: a clipped body row correctly yielded to the newly draggable window header. The test now proves a header gesture is consumed, no hidden emote fires, and release clears the gesture without movement when no drag occurs.
- Run03 exposed the missing Mod Library opening frame after the generic click helper began using displayed coordinates. The production transition and matching test helpers were added; run04 then passed.

The cumulative count is42 native client launches, including failed attempts. No new Minecraft process was started for the subsequent downloader/graph checks below.

## Exact in-game queue applied by the launcher

The successful native run left two actual Java-produced staged actions: `mods/votex-native-library-fixture.jar` and `shaderpacks/votex-feedback-fixture.zip`. The explicit Rust test `pending_addons::tests::applies_native_java_mixed_staging_fixture` copied those exact queue/staging bytes into a fresh temporary instance and called production `apply`.

It passed both SHA512 output checks, the mod-only installed metadata check, queue archival and completed-journal removal, and rechecked that the original native evidence remained unchanged. This proves next-launch file application, not hot-loading or a subsequent Minecraft session with these synthetic fixtures. Log: `work/ingame-goal/rust-native-mixed-staging-20261024.log`.

## Real default/SMP/shader downloads and a corrected resolver

The first real download test failed on Zoomify rather than silently accepting a missing baseline mod. The newest listed26.2 release, `D64cvU6G`, advertises both Fabric and NeoForge and requires the NeoForge-only Kotlin project `1vrSzlao`. Its Fabric dependency graph therefore cannot resolve. The older Fabric release `2Qr8jSFc` has a complete graph.

Both launcher and in-game metadata resolution now try at most16 root releases, in their existing release-order policy, when a required graph is incompatible. Every required dependency remains mandatory. Explicit dependency/version pins retain their exact identity. Network/rate-limit failures, malformed metadata and graph-size limits abort rather than trigger version fallback. This is bounded root fallback, not a general dependency-version SAT solver.

The Java graph was extracted into `dev.votex.addons.ModrinthGraph` so these rules can be checked without starting Minecraft. Five new ordinary tests cover failed-branch isolation and immutable metadata, exact dependency pins, network/malformed failures, installed incompatibilities and the16-candidate bound. An explicitly enabled HTTPS test then resolved the actual Zoomify graph to `2Qr8jSFc` with Fabric Kotlin/YACL/Fabric API and without Forge Kotlin. This test fetches metadata only. The service wiring and current Core/gametest/source JARs compile. **The graph correction happened after native run04; the native rendering proof is not being relabelled as a runtime test of that later network change.**

The actual production Rust preparation path was exercised without a desktop progress handle in a fresh temporary profile. It installed the four basics first, then the three Performance choices, all six SMP choices and both selected shaders:15 requested choices, with17 actual mod records after required dependencies and two shader ZIPs. Every installed mod/shader was checked against the official SHA512 metadata. Simple Voice Chat stayed on exact version `Ls232EsW`. Zoomify selected `2Qr8jSFc`; no NeoForge Kotlin was installed. Real `remove`/`toggle` actions followed by a second preparation retained deletion, disabled state and the user's changed Iris choice. Core/game-options bytes stayed unchanged and no microphone permission was created. Full selected version IDs are in `work/ingame-goal/rust-starter-https-20261024.log`.

The existing native-Core capability and first-time pinned Voice HTTPS tests were also explicitly rerun against the then-current built Core and passed; no microphone/device or game was opened. A later fresh Cargo build for the native launch smoke test caused Windows Application Control to reject the new build-script executables with OS error4551. That blocks producing and running the updated Rust test client; no security policy was changed. FRP and packaged-desktop testing are separate.

## Evidence boundaries

- Full regular Rust suite:89 passed,13 opt-in tests skipped. Four of those opt-in checks were then explicitly run and passed: mixed native staging, selected package downloads, current Core guard capability and first-time Voice download/removal.
- Java regular suite:105 passed, one opt-in HTTPS graph test skipped; that HTTPS test was separately enabled and passed. Core/client/gametest compilation and both JAR builds passed.
- Frontend:93 tests passed in21 files earlier in this checkpoint. After the isolated desktop check exposed an incomplete terminal warning bar, `InstallationFeedback` now marks a completed warning as100%; the full93-test suite and production frontend build pass after that edit.
- Desktop smoke check resumed on the existing immutable `0.4.1 Preview 2`, using only `work/ingame-goal/ui-resume-isolated-20261024`. The wizard created `VotexRegQA` (Minecraft26.2/Fabric, Performance, SMP options selected); no Microsoft sign-in or Minecraft process was started. The UI showed a completed warning toast and the profile card showed15 mods. Its receipt records12 of13 selected starter packages installed; Zoomify was skipped by this older preview binary. The current source resolver and opt-in HTTPS check already select the compatible Fabric release `2Qr8jSFc`, so this desktop result does not invalidate the newer source-side check.
- Native run04 uses one private integrated world and synthetic chat/social UI fixtures. It does not replace the previously recorded real paired-run05 transport evidence, establish authenticated identities/signatures, test physical audio, or prove public hosting.
- The new default package combination was downloaded and checked, not launched together inside Minecraft. Desktop profile creation/download feedback was inspected in the isolated preview; in-game launch and real launcher-owned reverse-sync orchestration still need final native acceptance.
- Windows automation resumed only in the isolated test window after the user's explicit approval. No real user profile, account, OS clipboard or audio device was touched. Physical Voice testing remains deferred by the user. No EXE, immutable preview, public download or website was replaced. A current preview rebuild is blocked: Windows Application Control rejects freshly built Cargo build-script executables with error4551; no security setting was changed or bypassed.
