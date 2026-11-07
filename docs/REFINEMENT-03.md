# Votex refinement objective — 19 October 2026

Active goal: inspect and redesign the entire launcher until the UI, transitions, cosmetics and game settings form a beautiful coherent product. This supersedes the previous claim that visual polish was finished. No subjective claim of perfection is evidence.

Required outcomes and proof:
- Audit every screen and modal (Play, Profiles, creation/edit/copy/import, content search/installed/details/sources, Skins, Cosmetics, Animations, Design, Performance, Recovery, Settings, Microsoft, notifications). Save rendered proof at relevant desktop widths, exercise primary workflows and fix visual/functional defects.
- Cinematic explicitly uses 32 render chunks. Explain render vs simulation and real shader/loader limits. Test actual options mapping and preserve current user values until they choose a preset.
- Gestures enter/exit with continuous eased blends; interruption, reselection, looping and returning to idle must not snap. One viewer/camera lifecycle rather than recreation on gesture change. Same timing model in launcher and Core with unit and visible runtime evidence.
- Replace voxel-staircase accessories with deliberate smooth surfaces: curved feathers, membranous dragon/butterfly wings, rounded/crafted headwear, pets and other accessories. Shared real mesh geometry for product view, try-on and Core. Existing equipped IDs migrate, own cape import survives. Never show invented product art disconnected from the real models.
- Coherent visual system, readable controls, restrained purposeful motion, useful empty/loading/error/selected states. No huge generic gradients or repeated loud badges in every panel. Retain editable quality tiers and reduced-motion behavior.
- Add useful considered functions (searchable keyboard action launcher, instance-content shortcuts, clearer customization) only when fully functional. Preserve all existing authorized profile/content/recovery/auth behavior.
- Verify source, meaningful tests, production assets, Core compilation and rendering API integration, EXE/installer/portable/source packaging and desktop start without localhost. Native/game runtime gaps must stay explicit; pending Mojang approval must not be bypassed. Respect zero spending and Windows security.

This is a broad goal. Keep it active until the audit has actual proof across the requested scope. Previous goal turn classification: initial goal turn; current work is progress when authoritative files/evidence change.

Current authoritative baseline: 0.2.0 Preview, 37 box/voxel cosmetics, abrupt preview gesture switching, Cinematic=20 chunks. The portable 0.2 EXE is open (verify exact process path before any later replacement). Earlier final Rust test EXE was blocked by Windows OS4551, while ordinary Tauri Debug build and the portable app started. Do not alter/bypass Windows protection.
## Current checkpoint — 0.3.0 Preview, 19 October 2026

Previous goal turn classification: **progress**. The source, real generated assets, native/Java code, tests and UI evidence changed substantially. This is not a waiting/no-progress turn. The older baseline paragraph above is historical; current build is 0.3.0.

Implemented and verified at source/test level:
- Cinematic now explicitly applies 32 view chunks, 12 simulation chunks, 8 GiB RAM, fabulous graphics and 120 FPS. Saved older values remain authoritative until a preset is deliberately chosen. Custom FPS values including 144 are supported and restored after disabling/re-enabling the cap.
- Shared eight-gesture timeline with eased envelopes, damped weights/output, continuous interruption/replay/stop, idle return and loop pause. One preview viewer/camera survives gesture changes. Core uses the same timing resource. Both frontend and Java tests exercise continuity rather than just implementation labels.
- All 37 original cosmetics rebuilt as surface geometry schema 2: curved feathers/membranes, rounded/crafted headwear, pets and equipment. Same validated geometry files feed product thumbnails, studio and Core custom-geometry submission. Equipment IDs and custom capes remain compatible. No commercial client assets were copied. In-game appearance is still unverified.
- Unified styling throughout all pages, original Afterglow Valley artwork, original neutral studio skin, corrected color management/tone mapping, quieter controls, custom sliders, readable light theme treatment, portal-based dialogs and reduced-motion handling.
- Ctrl+K keyboard action palette, explicit profile-content targets, content-folder shortcuts, physical RAM snapshot, improved edit/create wizard with latest-first search, pointer-driven profile mod drag/drop, transparent studio PNG capture with native bounded file export.

Current authoritative checks: 26 frontend tests passed; 41 native tests passed and 8 intentional online tests ignored; Core build with 8 tests passed; strict frontend production build passed; ordinary Tauri Debug EXE and NSIS 0.3.0 installer built successfully. Evidence files use the `refinement-` prefix. No security settings changed.

Rendered browser evidence in `docs/evidence/refinement/`: profile cards, real source/target drag review, Cinematic 32 settings, create/loader/review flow, skins, design, cosmetics, settings and recovery. Actual UI checks confirmed Ctrl+K search, independent managed/Play profile, pointer drag review, newest version 26.3 first, searching/selecting a version, copy-source choice at creation, gesture phases and stable viewer, exact 144 FPS with keyboard and cap memory. The PNG was produced from the real rendered canvas and inspected as an RGBA file. IAB did not deliver a download event for the data-URL link; native export is separately implemented and file-tested.

Still required in this pass: final visual proof after latest light-theme fix; complete small-screen/layout and modal audit using available UI controls; inspect remaining cosmetics/animation behavior; update release docs; package/verify 0.3.0 files and desktop start without Vite. Native UI interaction and full owned-account Minecraft/Core rendering remain unproven because native computer control is unavailable and Mojang approval has not been confirmed. Do not relabel a process/window-handle check as a visual/gameplay test. Do not mark this broad goal complete based on these partial results.
## Second checkpoint — source and browser audit completed, native review pending

This continuation is **progress**: completed the 1000 × 650 audit, fixed the clipped navigation and hero contrast, added accessory crossfades/loading states, moved the generated manifest to a proper source module, ran all 37 real model selections and built/started the 0.3 desktop Debug preview. The generated catalog is deterministic and its source/public/Core manifests agree. No model errors were shown in the 37-entry selection trace.

Actual owned process start: `evidence/refinement-native-smoke.json` records 0.3.0, responding window, correct portable path and shortcut, no listener on 1420, three profiles with all IDs preserved. The previous assumption of two current native profiles was stale; the file read before/after this start authoritatively showed three. Configuration was copied to `launcher-before-0.3-*` before launch. A native visible-window question was sent to the user because native UI control is unavailable here.

The source implementation and broad browser audit are substantially complete. No claim of perfection or complete gameplay is made. Native visual/interaction confirmation, Microsoft/Minecraft approval and a real Core-render/gameplay test remain required evidence. These external/runtime limits are not yet a three-turn repeated impasse; keep the goal active while independent release packaging work remains.

A normal optimized Tauri Release build is now running in the verified build session. It is the ordinary build mode, not a change to Windows protections. Packaging now requires `target/release` and cannot silently ship a Debug fallback. The currently opened Debug preview will be replaced by the Release executable only after that build actually succeeds.


## Third checkpoint — owner skin, reliable scale and Creator Collection

This continuation is **progress**. The latest user correction has been implemented: exact supplied default PNG, 3D Votex Client nametag, five explicit GUI levels with Apply, removal of double scaling, 800 × 560 responsive layouts, fixed wizard actions and per-step scroll reset. All nine actual pages were measured at all five levels after waiting for the inner route and checking its real heading. Forty-five combinations have no horizontal page overflow. The earlier timing-ambiguous measurements were replaced.

Nine genuinely imported, licensed models now lead the free catalog, including original UV textures shared with Core. The old Atelier entries and saved IDs remain reachable. All nine creator try-ons and real studio PNGs were inspected, and a new actual Minecraft run exercised all 45 models/eight gestures, producing 54 fresh images. Fitting was corrected from those pictures. The native optimized Release revision was rebuilt and started without localhost; the current exact hash and responding titled window are in refinement-native-smoke.json. A prior Windows denial remains historical evidence; no security settings were changed and no paid services/assets were used.

The complete source, production build, tests and evidence are being packaged under 0.3.0. The broad goal remains active: final native PNG-file confirmation and the personal Microsoft/Minecraft launch are still open. The owner previously reported a save, but no native studio PNG was found; the native dialog now exposes the exact returned filename. No subjective perfection claim and no unconfirmed account approval are substituted for runtime proof. No repeated three-turn impasse has occurred in this continuation.

The owner explicitly confirmed the later change from three test profiles to two Mein Abenteuer profiles while using the opened app. This is intentional user activity, not an inferred migration failure. Current user data is preserved; the native evidence records the changed IDs and confirmation separately.
