# Votex refinement — current status

The requested feature scope was captured on 19 October 2026. This is an implementation and verification status, not a claim that the current source has a distributable installer.

- [x] Four editable/resettable visual quality profiles, independent themes, grouped controls, localized Recovery explanations and clearer performance settings.
- [x] Bounded pointer lighting, hover lift, edge glow, smooth controls and reduced-motion behavior; hidden previews pause.
- [x] Cloud Play hero, supplied default skin, account-skin fallback and local skin chooser.
- [x] Both Play character modes: existing showcase and a rendered scene, with three geometry scenes, three light presets, orbit/zoom/pan, reset and independent emote/idle playback. Preferences migrate without changing profiles/skins/looks. Browser checks covered rendering, orbit/reset, persistence and an emote with idle disabled; native camera/pan checks and resource measurements remain open.
- [x] Fix idle rendering stop order against the installed Skinview3d draw loop. A post-draw pause cancels the next frame; a regression test covers this ordering and a viewer waking before the pause. All viewers share game-start/running suspension and pause on window blur. Scene buffers/materials are disposed on replacement; runtime GPU/FPS measurements remain open.
- [x] Guided profile setup with newest-first searchable versions, loader compatibility, performance/quality choices, optional packs, RAM and mod-copy review.
- [x] Modrinth pagination and content categories; native CurseForge integration and secure key setup; validated modpack imports. CurseForge account/API access still needs the user's own API key.
- [x] Skin library, cape image cropper, 45 wearable models, Creator credits, 3D previews, persistence and local Fabric rendering.
- [x] Eight built-in emotes, editable wheel and custom keyframe editor backed by Fabric Core gestures.
- [x] Recovery history and retention labels with protected working points, explicit restore review and world preservation.
- [x] Play background choices: built-in landscapes/clouds/Aurora/solid color, local images and bounded MP4/WebM, plus brightness, blur, position and zoom. Video pauses off the Play page, when hidden/reduced-motion is active, and while Minecraft starts or runs. Media stays out of per-profile Core exports.
- [x] Repeated frontend/Rust checks, small-window scale audit, profile/mod-transfer exercises, Core build and isolated Minecraft model/gesture renders. See [TEST-RESULTS.md](TEST-RESULTS.md).
- [ ] Complete a final German UI audit. Window controls and known authentication/storage/network/download/mod/Java messages now have German guidance; expandable original diagnostics remain available. An actual rejected JSON import and keyboard access to its details were verified. Unknown provider text and the remaining page-specific labels/empty states still need a full sweep; English localization is incomplete.
- [x] Repair standard dialog width accidentally overridden by the responsive CSS layer. Normal dialogs use 620 px, profile setup uses 780 px and creator tools use 1080 px, clamped to available space. Browser checks at 1280 px and keyboard access to diagnostic disclosures passed; small native-window checks remain part of final QA.
- [ ] Produce and verify the optimized installer. Windows App Control blocked the `vswhom-sys` build script; do not change or bypass security policy. The currently verified desktop installer remains 0.3.8.
- [ ] Verify sign-in, account ownership and launch after Minecraft API approval. The publisher approval is external and remains pending.
- [ ] Manually select and play a real MP4/WebM in the native launcher, then verify the pause/resume triggers across navigation, minimize, reduced motion and Minecraft launch.
- [ ] Verify authenticated CurseForge search with the user's own API key, then test all supported version/loader combinations, installer upgrade/uninstall, and real FPS/RAM measurements.
- [ ] Authenticode signing requires an approved certificate; none is available and no purchase is authorized.

Constraints: zero purchases/paid services, no security-policy changes, no foreign Microsoft IDs, no claims of unmeasured performance or successful owned-account launch. No new public website update is requested in this turn. Reference: https://storm-attack.adoredpeach257.chatgpt.site/ (light and hover feel only; own Votex identity). User has no CurseForge API key; native secure setup is implemented, authenticated CurseForge verification remains external.


Implementation checkpoint (not final validation): shared config migrations, four editable visual tiers, grouped Design Studio, event-driven luminous coordinator, cloud hero/account-skin chooser, wardrobe/cape cropper/3D wearable previews, eight emote previews and editable wheel, profile wizard, real game options/performance presets, localized Recovery, Modrinth/CurseForge catalog and infinite scroll, isolated archive imports, official Fabric/Quilt/Forge/NeoForge loader paths are implemented. Core now compiles with local wearable layer and pose mixins. Browser and native validation still in progress. Initial passes: frontend 17 tests, Rust 30 offline tests (3 online ignored), core build/test. An ordinary Tauri debug build succeeded; standalone cargo check's build-script executable was blocked by Windows application control, so use the successful normal desktop build path, with security unchanged.
