# In-game acceptance handoff, 25 October 2026

Overall result: **not accepted as a final release; awaiting deferred review and external prerequisites**. This report is a requirements/evidence audit. It does not launch an application, repeat the native tests, publish another installer or imply an authenticated user session.

The authoritative scope is the attached in-game objective, also retained in `work/ingame-goal/objective.md`. Subsequent user decisions defer visible Windows/EXE testing and physical audio. The latest human AppID response is “Noch keine Rückmeldung”. Those decisions remain in force.

## Three distinct artifacts

| Artifact | Identity | Actual acceptance boundary |
| --- | --- | --- |
| Public dated Preview installer | SHA256 `80cf44c2fe31beba5742dc67169ebd575f02e755aca609481943c4fa56bf728b`, 50,675,501 bytes; Core `25af63946d78d957ea6a867262aec34eb0eff433ef7adb48b022c0c22b84f82d` | Website download/hash verified after deployment version11. An intermediate preview, not the latest source or final acceptance. |
| Prepared internal EXE | SHA256 `969ccfc95221c34b5ebce63cdeb3b91ba8ad65a5d2d0f3934ece10f756d8c7e5`; Core `7c98b20a6c9baefba79bf7a9263786f1ec8391fb1c225d9e23da0e5dc10f40d5` | Optimized build and private test data prepared. This particular EXE has not been started; the user postponed its window review. |
| Newer blur Core candidate | SHA256 `3d44d71f54d1d9b6737034962f8c69e889d9281493da3bbe96813010865e43fe` | Compilation, unit tests, 161 production-class matches and target bytecode-path inspection only. Native Mixin activation, GPU/rendering and visual acceptance remain unrun. It is not staged in either EXE. |

## Requirement coverage

These are existing checkpoints, not fresh runtime results for every current source file. Details and failed attempts remain in the individual documents and raw receipts.

| Requirement | Evidence actually available | Remaining limit |
| --- | --- | --- |
| Clean Votex menus, proportional font, opening/closing, hover/light and effects | `HOVER-PARITY-20261025.md`, full-regression07 and notice02 native images/input; real theme-watcher variants at two physical densities | New blur candidate and current packaged EXE review are deferred; no claim that every theme/size is perfect. |
| Right-Shift home, Mod Menu and back/close input | Actual native mapping, B/Shift/Escape input and starter-pack key-conflict repair | Latest candidate packaged regression still pending. |
| More than20 HUD elements and customization | Current source has26 HUD definitions /28 total modules; native toggles, real values, dragged/scaled positions and persistence | All combinations of HUD layout, mods and screen sizes are not exhaustively tested. |
| Movable/resizable menu | Real corner/edge input, saved geometry, compact viewport/maximized-window checks | A new end-to-end desktop session is deferred. |
| Item Physics | Native dropped-item grounded/airborne/toggle checks, including starter pack | Client rendering only; does not change server physics. |
| Correct emotes and first person | Classic/slim real baked matrices, front/back images, eight first-person gestures with held item/camera preservation in modpack06 | Selected gestures/pack and earlier exact Core; not all possible user-authored animation combinations. |
| Skin/cape/nametag/cosmetic selection and previews | Native import/edit-selection/save/reload, orbit/crop and remote appearance checks | Renderer/editor boundaries and arbitrary combinations remain as documented. |
| More than300 free cosmetics | Catalog03 renders314 entries /311 non-cape in neutral attachment review; paired05 selected combinations | Breadth does not prove all simultaneous combinations, all animations or multiplayer load. |
| Launcher-only creator editors; in-game playback/wheel settings | Native wardrobe/wheel slot, speed/loop, stop and custom-definition playback; route audit | No model/emote geometry creation is exposed in game. |
| Remote emotes/cosmetics and consent handshake | Paired05: two real loopback clients, PNG/model reload, G acceptance, palm alignment, stop/movement/distance/expiry/decline/walls/rejoin/disconnect | Offline QA identities; no Microsoft-authenticated public two-network session or load claim. |
| Chat copy/search/reply/duplicates | Native Chat Studio and density checks retain original synthetic GUI signed-message objects, styles and full text | Private test clipboard; not cryptographic signature verification or an authenticated chat session. |
| Four minigames | Native controls/game-state tests for Pong, Tic-Tac-Toe, Flappy Bird and Tetris | Focus-loss fixture simulation is explicitly distinguished from OS focus testing. |
| Modrinth search, own mod/shader import and installation feedback | Actual HTTP graph/hash/download transactions, staged next-launch queue/rollback, real ZIP success/failure and persistent notice input checks | No hot-loading claim. Final packaged lifecycle/UI acceptance remains deferred. |
| CurseForge source | Configurable official adapter, authorization/distribution checks and fixture tests | No authorized API key supplied; authenticated provider access is unverified. Configure through the launcher, never paste credentials into this report. |
| Default/profile packs, shared settings and reverse sync | Actual production backend starter download/title-screen startup, Minecraft FPS save imported into profile/shared Performance class, actual Core update/recovery checks | Current desktop UI refresh, ordinary world-session shutdown and complete packaged flow remain separate acceptance. |
| Voice | Actual SVC UDP authentication/connection and production microphone-consent lifecycle, with an independent device veto | User deferred real microphone/headphone round trip. No physical audio capture occurred. |
| Friends / hosted worlds | Vanilla Friends bridge, LAN actions, authenticated-defaults local server boot and source-only self-host package | AppID approval/account session absent. Operator must provide public endpoint/DNS/network access; tunnel runtime was blocked by AppControl. No public service deployment is claimed. |
| Community public/private/republish | Deployed Worker contract repairs;13 local SQLite tests with explicit profile-response stubs and real launcher payloads/PNG fixtures; live public GET/anonymous rejection | Real authenticated owner publish/private/republish acceptance remains open. Stubs are not account proof. |
| Windows delivery | Existing dated preview download and signing-inspection/build helper | No trusted signing identity exists. Unsigned artifacts are not guaranteed to pass Smart App Control; no security bypass was made. |

## Concrete gates before a final claim

1. **Deferred window review:** resume only when the user chooses. Run the already compiled focused blur checks, inspect the real captures/Mixin/shader-state behavior, and stage/rebuild only the accepted Core. Then use the isolated prepared desktop profile for install/launch, normal exit, UI refresh and settings persistence. Do not substitute earlier EXE/Core results.
2. **Microsoft approval and account participation:** the user reports no response. Confirm approval and perform a genuine account session when available; an empty connected-mailbox search is not proof about the application's different contact mailbox. No resubmission or message has been sent.
3. **Authenticated external providers/community:** use an authorized CurseForge setup and a real participating account for community ownership/visibility transitions. Current local/stubbed checks cannot close these gates.
4. **Operator infrastructure:** deploy the prepared self-host component in an operator-provided environment, with reachable game/voice endpoints and permitted tunnel execution, then verify two-network access. No new hosted resources or policy exceptions are assumed.
5. **Deferred physical audio:** perform the user-participating microphone/speaker test later, including actual audible round trip and reconnect behavior.
6. **Trusted Windows distribution:** provision an authorized signing identity/service for a signed release and verify the final signed artifacts. Do not disable or evade application control.

The recurring barriers have remained across the deferred-window, real-Core-update, community-contract and blur follow-ups. Independent implementation and bounded offline checks continued during those turns. This audit finds no remaining independent check that can establish the missing runtime, account, infrastructure or hardware acceptance without the corresponding user/external-state change. Further speculative polish or repeating green suites would not close them. The goal should therefore be marked **blocked**, not complete, until a concrete gate changes.

Current test evidence: Java122 passed /one existing opt-in skip, Rust91 passed /15 opt-in ignored plus two explicit actual-artifact checks, frontend99 passed, community13 passed. These are separate suites and exact-scope records; they are not a new combined final-release test. No tests were rerun merely for this audit. Native launch tally remains61, including failed attempts. A read-only process inventory found no Java/launcher/Node process whose command line referenced this private QA root; it says nothing about unrelated user processes.

The accompanying `work/ingame-goal/acceptance-gates-20261025.json` records current local artifact hashes, unchanged blur source hashes and references to the original evidence. `INGAME-GOAL.md` retains the chronological history, including superseded ACTIVE notes.
