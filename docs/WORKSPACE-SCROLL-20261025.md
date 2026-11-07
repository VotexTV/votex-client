# Native workspace flow and compact fitting

Status: tested source checkpoint, followed by one compiled Performance layout correction. The overall in-game objective stays active; this is not an EXE release.

## Behavior

Modules, Cosmetics, Skins, Emotes and Friends use continuous lists. Search, filters, wheel slots and footer actions stay fixed. Offscreen list widgets are hidden before rendering. Only Modules shows a thin temporary scroll indicator. A compact wardrobe keeps one complete preview/name/status card visible and places its three actions in one row. Model thumbnails preserve their original aspect ratio.

## Retained native attempts

1. `full-regression-run-05` exited1. Its old clipping fixture scrolled the selected row completely above the window and then expected that off-window click to start a titlebar drag. The fixture now uses actual wheel input to place that row behind the titlebar before testing interception. Its captures also exposed a real compact wardrobe defect: the preview and name could not fit together. The production card/footer layout was corrected.
2. `full-regression-run-06` exited1 after the earlier responsive/secondary/chat/HUD/Arcade/wheel/library phase. The new list check reached the final module card, then failed because it expected the search EditBox at the decorative frame's Y rather than its actual nine-pixel text inset. That assertion was corrected. All81 retained PNGs were inspected on four sheets; this attempt is not recorded as a complete pass.
3. `workspace-flow-run-01` exited0 with `VOTEX_WORKSPACE_FLOW_PASS`. Its29 native PNGs were inspected on two sheets, and the compact wardrobe, module-list end and Friends-list end were inspected individually. All world dimensions saved before normal shutdown. No source changed during the run; all153 production class files matched Core SHA256 `56effca7c01bf1ef705f689bc236d67bf6d2507fc7650f5157488f521d279098`, also used by the preceding run06.

The successful proof is `work/ingame-goal/workspace-flow-run-01/clientGameTest/config/votex/goal-evidence/native-workspace-flow-proof.json`. It covers all28 module cards in one list, native wheel input to the last card, fixed search/filter controls, real toggle/search persistence, authored catalog equip/unequip, real PNG skin selection, custom emote slot/playback/speed/loop/stop persistence, full compact wardrobe cards and category access. The social fixture reached all24 synthetic peers fully through wheel scrolling, retained footer actions, deferred refresh during a window gesture and clamped after departure. Eight Voice Setup/Consent cases retained displayed-frame hit mapping and unchanged microphone permission.

These are native Minecraft screen events and synthetic social packets in a private integrated world. They do not establish24 network players, a new paired transport run, OS input automation, Microsoft authentication or physical voice playback. The previous broad regression and two-client proofs retain their own precise scope. No game was launched again merely to repeat them.

## Later visual finding

Reviewing the run06 Performance capture exposed status text over the compact measurement buttons. Production now reserves a fixed bottom status area and clips/scrolls the controls above it; the scroll extent ends at the actual last JFR/target-FPS row. A corresponding native last-control check and capture were added. This later correction compiles and passes the ordinary suite, but was not followed by another Minecraft launch. Do not relabel the successful flow images as proof of that later change.

Current internal Core SHA256 is `f05650165bea64a5057c8bdd0f4dd7ec231a0999a8813526ea943da56f33c677`. `test compileGametestJava jar sourcesJar` succeeds:108 ordinary tests, zero failures/errors, one opt-in skip. The later native Performance check remains pending the next consolidated run.

All three test clients from this follow-up are terminal. The total native-client tally is55: the previously recorded51, the separately retained normal startup07 GLFW failure, and these three attempts. The user's existing Java game was left alone. No account, real profile, microphone permission, immutable preview, EXE or website was changed. Remaining packaged-current-source, community/authenticated/public-service and final acceptance gates remain open; hardware audio is still deferred by the user.

## Current native input and Performance follow-up

The later Performance finding is now covered by `workspace-flow-run-02`, exit0. Its compact top/end captures show a distinct fixed status area and fully reachable JFR/target-FPS controls. A further input audit found that a partially visible list row could extend its widget hit rectangle over a fixed footer even though its pixels were clipped. Click dispatch now masks only the affected content widgets outside their rendered viewport, delegates the actual native event, then restores their visibility. Sidebar, popup and titlebar handling retain their own existing paths.

The native regression deliberately places an active `Testen` row behind the visible edge of `Beenden`, confirms that their widget rectangles overlap, clicks that footer edge and requires the actual active emote to stop. It passed, as did all compact pages, continuous module/catalog/emote lists, selection/persistence and24-peer scroll/dialog checks from the earlier flow run. All31 new PNGs were inspected on two contact sheets; Performance and the footer-isolation frame were inspected individually. Every dimension saved before normal exit. No Java source changed during the run, and153 production classes exactly matched Core SHA256 `6c7a7c236ac95abe415678d81e1e4a28473c7951df898461011ec6d0e7f12f59`. The current source/build report now binds that same hash to this successful proof. The total native tally is56; this update used one additional consolidated client.

The identical Core has been staged into the internal desktop resource directory. Frontend97 tests pass. Both actual Core update/preservation tests pass; the opt-in native-JAR microphone-guard artifact check passes once with the correct `prepare::voice_tests` filter. A preceding `--exact prepare::tests` invocation matched zero tests and is not counted as coverage. Internal desktop build/initialization acceptance remains separate from the in-game proof. No user preview or public download was replaced.
