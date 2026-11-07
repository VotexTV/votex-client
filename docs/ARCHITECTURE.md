# Votex architecture — 0.4.0 RC1

The Windows application uses Tauri 2 with a Rust backend and a React/TypeScript interface. The browser development view intentionally cannot launch Minecraft or manipulate instance files. It stores its own preview state in localStorage. The desktop uses native commands and atomic JSON files.

## Boundaries

- `apps/launcher/src`: reusable glass components, pages, native-service adapter, schema-validated application state, local news.
- `apps/launcher/src-tauri/src/storage.rs`: validation, safe relative paths, instance isolation, atomic writes. A global operation lease prevents concurrent game/file mutations. Theme writes remain possible while a game runs.
- `network.rs`: HTTPS, fixed download hosts, API redirects disabled; download-only redirects limited to three approved HTTPS hosts, SHA-1/SHA-256/SHA-512 verification and resumable partial downloads. Up to eight game-file transfers run concurrently. Duplicate asset hashes share one transfer.
- `auth.rs`: Microsoft public-client device flow → Xbox Live → XSTS → Minecraft token → entitlement/profile checks. Only refresh tokens persist, in Windows Credential Manager. No token is returned to React.
- `community.rs`: fixed HTTPS endpoint for public contribution feeds and explicit profile-verified publish, visibility, deletion and report actions. Minecraft access tokens remain in Rust and are forwarded only to the Votex Worker for an official profile check; request bodies are bounded and never logged.
- `minecraft.rs`: official version catalog, version JSON, Fabric profile JSON, library rule evaluation, assets, native libraries, Java version checks, `Command` argument arrays, process output and exit monitoring. Fabric library overrides retain native classifiers. A separately selected offline identity supports singleplayer and offline servers; it does not authenticate to online-mode servers or represent a verified Microsoft account. The interface never supplies shell commands.
- `modrinth.rs`: compatible mod installation for the selected supported loader, required dependency graph, explicit incompatibility rejection, hash-checked staging, pre-install snapshots and rollback. Updates preserve replaced files in recovery storage.
- `recovery.rs`: per-instance manifests and content-addressed blobs; pre-restore snapshots; path/content validation before mutation; quarantine for extra files; rollback on failure. Atomicity is per file, with compensating rollback across files, rather than a filesystem-wide transaction.
- `diagnostics.rs`: actual exit-associated log signals and current crash-report excerpts. It does not infer which mod is guilty.
- `minecraft/votex-core`: independently buildable client-only Fabric 26.2 mod. Three real HUD modules, Right Shift settings/menu, B radial selector and an optional LootBrain bridge.
- `packages/shared-config`: version-1 schemas and defaults; `packages/shared-types`: extension interfaces for future module, cosmetic, emote and shader providers.

## Data and synchronization

Mutable desktop data lives at `%LOCALAPPDATA%/VotexClient/`. Configurations, instances, skins/presets, logs, caches and backups live in separate directories. Existing `.minecraft` directories are not modified or imported automatically. Removing a profile removes its launcher entry and retains its files. Launcher background images and bounded MP4/WebM loops are stored in launcher settings only; Core preset exports strip these media fields so each Minecraft profile does not duplicate large launcher artwork.

The launcher is the sole writer of each instance's `config/votex/theme.json` and `presets.json`. Core owns `modules.json` and nonce-bound `startup.json`. Files are replaced atomically. A blocking Java WatchService observes theme changes; it does not poll each game tick. Preset metadata, local PNG assets, custom cosmetic meshes, bounded emote keyframes and the emote-wheel assignment are synchronized separately. Core watches theme and preset files, caches selected textures and wearable meshes, and applies local player pose mixins. The optional community API stores public contributions and private ownership metadata; a user must browse and import an item before it is stored locally. This is not automatic real-time appearance synchronization between players. The launcher and game can select separate design themes.

## Play character rendering

`characterView` is additive launcher-only state with a defaulted mode, scene and lighting preset. Rust accepts old configurations without it and validates new enum values. It does not alter Minecraft profile settings or Core preset formats.

The existing Skinview3d renderer stays mounted across showcase/scene/light changes. `CharacterScene.ts` creates finite original geometry, owns its buffers/materials and disposes them on scene replacement. A single 512-pixel directional shadow map is enabled only for scene mode outside Ultra Performance. Existing local skins, cosmetics and emote controllers are reused. OrbitControls enables panning only in scene mode; view buttons recenter the target. Explicit emote playback is independent of idle animation on Play; the existing animation editor's play/pause behavior remains compatible.

The installed Skinview3d draw loop queues its next animation frame after invoking the animation callback. Pausing inside that callback therefore left the next frame running. The idle path now pauses in a microtask after draw; a regression test simulates that scheduling order. A shared context suspends every mounted viewer while Minecraft starts/runs, and visibility/focus listeners stop background loops. These are implemented lifecycle controls, not measured FPS/GPU claims.

## Working-state evidence

Process creation only produces a running-process status. Core writes a unique nonce-bound marker after the actual Minecraft title screen initializes. The process must remain alive for another 45 seconds before its prelaunch snapshot is protected as confirmed working. Without Core, even a clean exit records `initial-checks-only`. A subsequent failure revokes that snapshot's validation. Safe Mode never promotes a normal profile snapshot.

This is startup confidence, not proof that every world/server/mod interaction works. A crash can have causes unrelated to recent mods. No repeated troubleshooting launches occur automatically.

Safe Mode starts the selected loader and official game in a separate empty `safe-sessions/<uuid>` directory. It does not move normal mods or expose normal worlds. Reopening normal Play returns to the ordinary directory. Safe-session files are retained.

## Recovery scope and limits

Snapshots cover profile version/loader/Java/memory settings, top-level mod JARs and their enabled/disabled names, installed-mod metadata and configuration files under `config/` and root `options.txt`. Launcher-owned `config/votex/` synchronization files are excluded. Worlds, screenshots, server lists and credentials are excluded.

Configuration files are limited to 2 MiB each / 32 MiB total, and a mod file to 512 MiB; excess files cause a clear failure, not a silently incomplete recovery point. A blob is stored once per content hash, including mod binaries needed for offline restoration. Retention removes old manifests while protecting the latest confirmed working point. Explicit cleanup removes unreferenced blobs. Quarantine is retained and is not automatically deleted.

Recovery tests cover real temporary-file restores, unchanged worlds, quarantined extras, rejected corrupt blobs, traversal and protected working points. Power-loss recovery of an interrupted multi-file restoration is a future enhancement; the pre-restore snapshot remains available.

## Future adapters

Version 0.3 has no local HTTP control service, automatic mod bisect, custom rendering engine or privileged Windows tuning. Adding a version adapter, IPC transport or cosmetic renderer must preserve the existing filesystem/authentication boundaries. New config versions must be explicitly migrated; unknown schemas are rejected and preserved.

## Preview 0.2 additions

`loaders.rs` resolves official Fabric/Quilt profiles and runs verified Forge/NeoForge installers in isolated caches. `runtime.rs` validates and caches portable Java without system installation. `catalog.rs` normalizes provider search and content installation; CurseForge credentials remain native in Credential Manager. `modpacks.rs` validates archives and downloads before creating a new instance/profile. Optional pack files are skipped; worlds and launcher-owned Votex configuration cannot be injected through overrides.

`game_options.rs` merges only the chosen game settings and tracks an applied revision. Existing options on migration/import and subsequent in-game edits are preserved until a launcher edit requests new values. Recovery captures the previous options file before changes. `core_install.rs` quarantines older Core artifacts and avoids duplicate active IDs.

The shared theme schema retains four independent effect overrides per color theme. `Luminous.tsx` has one event-driven pointer coordinator, one scheduled frame at a time, and a maximum of 20/32 nearby elements; it is disabled for zero proximity and reduced motion. Hidden skin previews pause, meshes/materials are disposed on replacement, and color/layout settings are independent of effect tiers.

Personal authentication and every loader/version combination still require verification. Cosmetic geometry has now been rendered in the isolated Fabric client test; see the current test record for the exact scope.

## Profile content and original cosmetic catalog

Opening Mods from a profile passes an explicit managed profile ID independently of the active Play selection. The global shortcut retains a visible destination selector. The creation wizard defaults to newest-first versions and can review a mod-copy plan before creating its instance.

`transfers.rs` hashes source/target inventories, identifies source files through Modrinth SHA-512 lookup, pins target releases, resolves mandatory dependencies, rejects declared conflicts and filename collisions, and saves an opaque 30-minute review token. Commit rechecks both profile/inventory fingerprints, stages and verifies downloads, snapshots the existing destination, then writes files with compensating rollback. Source instances, worlds and configuration overrides are not copied. Unrecognized local files can only be carried unchanged between identical Minecraft/loader pairs. A new draft is registered only after its file commit succeeds. A queued frontend settings save is invalidated/flushed before native mutations and reload, preventing stale UI state from overwriting native-created profiles.

The earlier 0.2 box catalog is historical. The current 45-entry surface catalog and Creator Collection are described below. Equipped IDs and existing custom capes remain compatible; no entities or network appearance service are introduced.

Profile dragging uses Pointer Events, pointer capture and a 7-pixel movement threshold. Hit testing uses viewport coordinates. The drag ghost is portaled to document.body, outside the scaled app shell, so its coordinates remain in viewport units. Escape/cancel releases the operation. The accessible copy-dialog alternative remains available.

## Preview 0.3 refinement

The launcher and Core share 45 indexed surface-geometry models (schema 2), positions, smoothed normals, per-vertex colors, quads, optional UV texture coordinates and wing pivots. Python generates original Atelier meshes and projects those exact faces into SVG product thumbnails. `Wearables.ts` merges compatible geometry by joint/material/pivot, then uses studio PBR lighting. `WearableLayer.java` validates the bundled data and batches custom geometry through the Minecraft 26.2 submit collector. Minecraft uses its own entity materials; identical geometry does not claim identical PBR lighting. Model promises are bounded by the finite catalog. Previous meshes remain visible until replacement assets load; the preview fades both for 220 ms and disposes retired buffers/materials.

`EmoteMotion` and `EmoteTimeline` are render-independent controllers with the same shared timing resource, continuous quintic envelopes, critically damped weights and inertial output. The same eight movement formulas are adapted to the viewer and player-model coordinate systems. The viewer is created once per mounted preview, preserves the camera and uses targeted effects for skin, cape, equipment and gesture changes. Hidden previews pause; explicit one-shot actions can run without continuous idle motion.

`CommandPalette.tsx` offers local in-app keyboard navigation, profile targeting and fixed content-folder shortcuts. Native commands whitelist known subfolders of a validated instance. `system_memory` uses the documented Windows memory-status ABI once per Performance view. `studio.rs` accepts bounded PNG data, checks header/dimensions and atomically writes a UUID-named `.png` under the fixed application studio folder. It accepts no caller-provided filesystem path. The public browser preview cannot invoke these commands.


## GUI size and imported assets

Five scale stops are local draft state until Apply. Existing saved in-between values remain unchanged. CSS zoom performs its own viewport layout compensation; the old inverse width/height and duplicate font multiplier were removed. The workspace is an inline-size query container, and a scale-aware logical width selects compact navigation. Pages retain a scrollable bounded workspace. The profile wizard uses a fixed header/footer with its own scrolling body, reset per step. Hover light coordinates are converted from viewport units to scaled element units; hit-test ratios remain dimensionless.

`import-creator-cosmetics.mjs` reads licensed local FBX/GLB data without executing downloaded code, preserves UVs and normalizes placement into the common bounded format. Browser and Core use the same PNG bytes. Texture names are finite catalog entries, not caller paths. `generate-creator-thumbnails.py` projects those exact faces and UV textures into SVG product views. The nine Creator models retain attribution in metadata, the launcher and the Core credit file. Sources/licenses are in CREATOR-CREDITS.md. Regeneration order is the original base generator, Creator importer, then Creator thumbnails; normal builds use the committed generated assets.

## Preview 0.3.11 additions

The launcher sends only the selected profile ID as a JVM property. On a normal client shutdown, the Core reads the current OpenGL viewport, flips and scales the captured frame to at most 960×540, then writes `config/votex/profile-cover.png` inside that profile instance using an atomic rename. The launcher reads only that fixed file for each known profile, checks the PNG signature and a 2 MiB size limit, and displays it locally. A forced process termination or a shutdown after the graphics context closes can leave the previous cover unchanged.

Offline launch derives Minecraft's standard local `OfflinePlayer:<name>` UUID and supplies the legacy local user type with no access token or XUID. It does not refresh or store Microsoft credentials. Local launch does not provide an online identity to servers that require Microsoft authentication. The per-profile `presetValues` map stores independent RAM, view distance, simulation, FPS, graphics, VSync and shader choices; resetting a preset clears that entry before restoring its shipped defaults.

The Skin Pixel Editor modifies real 64×64 and 64×32 UV textures in a canvas. Completed strokes and undo/redo refresh the adjacent 3D skin source; saving updates the local skin data only. This preview requires WebGL. The cosmetic atelier remains a bounded Votex editor for cubes, spheres, planes and pixel pieces, transforms, materials, and safe sine-wave part motion. It is not a native Blockbench project editor, and cosmetic parts do not yet have a frame-by-frame keyframe timeline. Custom emotes do have a separate bounded keyframe editor.

The six Design Studio switches directly control glass, proximity lighting, glow, reflections, press spring and ambient motion. Effect values are written to the active named visual profile when another level is selected. Ultra Performance disables these effects, blur, proximity tracking and ambient movement; the higher tiers enable more CSS filters and event-driven motion. FPS, energy use and GPU load have not been measured on a native Windows run.
