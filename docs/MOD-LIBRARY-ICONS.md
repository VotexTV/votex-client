# Native library logos and opening input — 2026-10-23

The installed view now actually refreshes when reached from discovery/Voice setup. Its last page is clamped after a viewport change. Previously the UI08 assertion accepted an empty installed list; UI08 is not evidence for this repair.

Catalogue logos accept bounded static PNG, JPEG and WebP, detected by bytes. IconImage decodes on the existing IO workers, checks dimensions before decoding and normalizes to metadata-free RGBA PNG, at most128 pixels per edge. The512KiB encoded limit and1024-pixel source-dimension limit remain enforced. Animations, SVG and unknown formats are rejected; skin/cape/network PNG validators are unchanged. The decoder uses a direct WebP provider without changing global ImageIO plugins or native code.

TwelveMonkeys3.15.3 is pinned with six explicitly nested non-transitive libraries. The BSD3 license and notice are included. Exact source: https://github.com/haraldk/TwelveMonkeys/blob/twelvemonkeys-3.15.3/LICENSE.txt . Dependency hashes/export are under work/ingame-goal/imageio-runtime-dependencies-20261023.json and icon-runtime-files-20261023.json. The release JAR excludes all gametest classes and fixtures.

Installed logos are registered only for visible cards. Bulk registering the entire installed list into an80-texture LRU previously evicted visible first-page images. Visible entries now reload after eviction; removing the library releases textures. Remote concurrency/queue/CDN restrictions are unchanged. Smooth sampling is scoped to logo draws and never changes world/skin sampling.

The main workspace now uses one sampled WorkspaceMotion frame for rendering and inverse input coordinates: hover, clicks, scroll positions, drag and popup hit testing use the same transform. AvatarPreview keeps its actual screen-space bounds. Previously only density was inverted, so controls could miss during opening/closing transforms. The animation can still be disabled.

## Evidence and limits

- Java92 tests pass, including3 image tests and2 motion tests. They cover lossless/lossy/alpha WebP, PNG/JPEG, metadata/aspect normalization, rejected animation/oversize data and motion inversion across densities/times.
- Native library-icons-run-01 failed because processGametestResources had not copied a new checker fixture. Its real HTTPS logos and route screenshots were captured, but the run is not a successful full cache check. The launcher script now checks the fixture before creating a client.
- Native library-icons-run-02 exited0 and saved all dimensions. The installed-route assertion requires more than2 actual entries. Accepted Modrinth search metadata after resize is recorded. ImmediatelyFast, YACL and Fabric Language Kotlin each supplied actual96x96 WebP logos, decoded and displayed as real GPU textures. Their PNG captures were visually inspected.
- A separate read-only120-entry synthetic list exercised28 actual native pagination clicks. The cache stayed at most80, evicted the original first page, restored fresh visible textures and emptied on close. These are original checker fixtures, not downloaded or installed mods. Last/first-page images were inspected.
- An actual native HOME frame was held at opening progress0 using a test-only future timestamp. A visible button-edge click outside the old untransformed hit area opened MODULES. This proves the native inverse input path at a controlled rendered frame; it does not claim subjective motion acceptance or every transient interaction.
- After run02 review, rectangular logo rendering was adjusted to use a centered uniform scale inside its40x40 slot. This small rendering change compiles and retains the unit-tested thumbnail aspect ratio, but no additional native launch was spent on a rectangular fixture. The three live logos above were square.

Exact proof: work/ingame-goal/library-icons-run-02/clientGameTest/config/votex/goal-evidence/native-library-icons-proof.json and launch-result.json. Only public catalogue/icon requests occurred: no account login, mod downloads/installations or microphone access. All test clients are stopped. This source is newer than the immutable2026-10-22 EXE preview and has not replaced it. Full objective remains ACTIVE, including remaining chat density, paired remote/wall cases and deferred hardware audio.
