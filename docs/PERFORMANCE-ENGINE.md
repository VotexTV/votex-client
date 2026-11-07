# Votex Performance Engine · 0.4.1

## Inspected architecture

The launcher uses Tauri 2 / Rust and React 19 / Three.js. It installs official Minecraft versions and supports separate loader adapters. The **in-game Core supports Fabric Minecraft 26.2 only**, Java 25+, Loom 1.18.4, Fabric Loader 0.19.5 and Fabric API 0.161.0. Other launcher versions do not automatically gain these hooks. The selected production stack includes Sodium 0.9.2, Iris 1.11.4, Lithium 0.25.3 and Fabric API. ImmediatelyFast and Entity Culling are not assumed installed.

Core uses the native 26.2 GUI extraction API, the HUD registry, client ticks, a filesystem theme watcher and four existing player/cape Mixins. It does not own chunk meshing, chunk scheduling or GPU buffer allocation. Cosmetics are parsed into bounded meshes on look revision, then submitted through Minecraft's collector. Emotes blend the local player model only; neither feature sends gameplay packets. Presets/skins/looks sync from the selected launcher's isolated instance directory. Microsoft authentication and offline local identity remain separate native launch paths.

No Git repository existed in this delivered source directory. Before the new Core work, a source checkpoint was created and ZIP-validated at `work/checkpoints/before-vpe-0.4.1-complete.zip`; working launcher fixes are included. No user profile, world, credential or game directory is part of that checkpoint.

## Measurement contract

Frame intervals measure presented-loop pacing including waits; render-thread CPU is a separate thread CPU-time measurement when supported. HUD, emote and cosmetic submission timers cover Votex-owned work. These are not GPU execution timings. Unsupported chunk build/upload, total entity renderer and GPU timings are exported as unavailable rather than inferred from utilization. Process CPU, heap/nonheap, GC counts/durations, direct buffers and render-thread allocation counters use JVM management interfaces during explicit captures. Captures are local and opt-in; there is no analytics upload.

Use the same world, location, camera, runtime, resolution, shader, VSync, FPS cap, render/simulation distance and mods for A/B comparisons. Warm up 10 seconds before collecting at least 30 seconds; repeat three times and report variability. A static-world run does not establish traversal, entity-density or long-session performance. Allocation microbenchmarks do not establish game FPS.

## Scenarios

* A: stationary at a saved location; no menus, fixed direction.
* B: repeat the same 360-degree camera route over 30 seconds.
* C: repeat a saved traversal route through already generated chunks, then separately through new chunks.
* D: fixed local test world with a documented identical entity population.
* E: all Votex HUD displays and one identical look/emote enabled.
* F: 30+ minutes in the same world; record heap trends and GC without calling System.gc().

Chunk scheduling and buffer changes are deliberately deferred to the renderer owner. No competing Sodium scheduler, raw OpenGL state manipulation, forced collection, simulation changes or lower view distance is introduced.

## Implemented architecture and controls

`VotexPerformanceEngine` owns a 120,000-frame primitive capture buffer, scalar module timers, an `OptimizationCoordinator`, validated settings and one bounded eight-job IO queue. Captures warm up for 10 seconds and run 30–600 seconds. Percentile sorting and JSON export execute on its daemon IO worker. CPU/JVM management sampling is at most once per second while capturing or viewing diagnostics. Ordinary gameplay does not run JFR, sort frame samples or write telemetry each frame. JFR is a separate explicit local capture, capped at 64 MiB / five minutes. The worker drains on client shutdown; watcher closure remains in the existing lifecycle hook.

The optimization registry exposes runtime Core, text cache, emote buffer, adaptive-effect and installed Sodium/Iris entries. It records the supported 26.2 adapter and explanatory ownership notes. This is a registry of Votex paths and detected integrations, not a switch that rewrites third-party internals. The bottleneck description reports measured heap/HUD pressure and otherwise remains undetermined. GPU-bound and chunk-bound diagnoses are not guessed from total CPU usage.

The native Performance page can start a benchmark or JFR recording. The launcher's Performance Center reads the selected instance's fixed local report and changes validated Core settings; the running Core watches that file. Empty reports display an explanation, not decorative measurements. The report includes the frame distribution, raw intervals, frame percentiles, supported JVM counters, Votex-owned timers, Java/mod/resolution/view-distance settings and explicitly unavailable measurements. Effective FPS throttling and window activity are included in new captures.

Adaptive effects are **off by default**. Conservative / Balanced / Aggressive use separate thresholds, five consecutive bad observations, ten recovery observations and a 15-second cooldown. Their only current intervention is sampling optional cosmetic motion every two/three game ticks; geometry, player motion, hitboxes, packets and server ticks are unchanged. Intentional Minecraft FPS limits and VSync refresh limits contribute to the target budget. Inactive windows do not drive a quality downgrade. Disabling adaptive mode restores full animation sampling. Target FPS is not a promise to achieve that FPS.

HUD labels update at most five times per second and retain their widths; rendering does not rebuild strings each frame. Dynamic labels bypass the shared width cache. Static menu labels use a bounded 256-entry LRU keyed by font identity, exact text and clipping width. Widths are logical font units; later GUI transforms do not change these values. Language changes, resource reloads and world unload invalidate the cache. Minecraft retains ownership of glyphs, Unicode, formatting and actual drawing. The cache does not retain screens, players or worlds. A reusable lookup key avoids allocating a key on a cache hit.

Emotes have a borrowed, thread-confined `stepInto` output for immediate application to the local model. The public owned `step` API still returns an independent snapshot. Scratch/accumulation arrays are reused and zero-weight gesture calculations skipped. Model-part lookup no longer creates a joint array each draw. The idle state resets once on world departure instead of allocating a complete new timeline every title-screen tick. Existing eight gestures and authored custom timelines remain supported.

Native HUD has ten actual modules: FPS, coordinates, clock, memory, compass, speed, keys, connection latency, armor durability and active effects. New modules default off; a new profile shows only the compact FPS pill. Existing enabled states and x/y values migrate unchanged. Individual saved positions can be dragged in game. The menu uses native keyboard/narration-capable buttons, filtered module cards, local skin selection, appearance switches, an actual player preview and the eight-slot emote wheel. Cosmetic visibility and skin choices persist locally. Theme motion preferences still apply.

## Measurements recorded on 21 October 2026

* **Emote allocation probe:** three runs of 500,000 steps after four warm-up runs on the same Java 27 installation. Original method: 1,680.07–1,680.13 bytes/step and 369.99–426.99 ns/step. Reusable path: 0.07–0.14 bytes/step (amortized harness/request overhead) and 166.09–167.91 ns/step. Median method time fell from 374.39 to 167.81 ns; this is not a game-FPS multiplier. Machine-readable records: `evidence/0.4.1/emote-before.json` and `emote-after.json`; the original source is in the checkpoint.
* **Actual Minecraft font-width probe:** three interleaved uncached/cached runs, 100,000 lookups each after warm-up, Latin/German/Japanese labels. Uncached 159.07–193.28 ns and about 48 bytes/lookup; cached 10.20–12.71 ns and about 0.00016 bytes/lookup. Four misses and 109,996 hits including warm-up per cached run. A 400-label stress validates the 256-entry bound; explicit invalidation empties it. This tests repeated width work, not all text rendering or whole-game FPS. Record: `evidence/0.4.1/native-ui/text-cache-probe.json`.
* **Six static/HUD game captures:** three interleaved buffer/cache off/on pairs ran in one isolated Fabric test world. Windows 11, RTX 4060 Ti, 20 logical processors, Java 27, 854×480, view distance 5 and simulation distance 12 (test-world defaults only). The first run transitioned from active rendering to background throttling; later runs were approximately 30 FPS. These captures are marked **invalid for comparative FPS claims**. Raw intervals, GC/JVM counters and Votex timings were retained under `evidence/0.4.1/performance-ab/`. The flags are not a reconstruction of the entire old client. No competitive/Vanilla/Sodium FPS advantage is claimed.
* **Rendering:** the full native Fabric test rendered all 45 bundled cosmetics, eight gestures and a custom keyframe model. A second targeted run validated the final menus, player preview, cache behavior and lifecycle. Native 26.2 API calls are used; no browser is embedded in Minecraft.

## Engineering status

| Optimization | Implementation status | Benchmark result | Compatibility |
| --- | --- | --- | --- |
| Allocation reduction | Implemented and tested | Emote method allocation/time measured above | Local Core 26.2; owned snapshots preserved |
| Text cache | Implemented and tested | Repeated native font-width lookup measured above | Bounded; language/resource invalidation; Minecraft font API |
| Render-state optimization | Partially implemented | No GPU/driver benefit measured | Existing native collector and pre-grouped cosmetic surfaces retained |
| Entity animation | Implemented and tested for local Votex emotes | Method probe; all eight poses render | Does not alter other entity simulation |
| GPU buffer management | Not implemented | Not tested | Minecraft/Sodium ownership; no competing allocator |
| Native HUD | Implemented and tested | Timers available; no comparative FPS claim | Native Minecraft 26.2 GUI extraction |
| Advanced culling | Not implemented | Not tested | Existing invisible/spectator checks retained; no duplicate culler |
| Memory optimizer | Partially implemented | Bounded cache and allocation probes; no long-session result | No forced GC; resource manager owns textures |
| Chunk scheduler | Not implemented | Not tested | Delegated to Minecraft/Sodium; no safe verified extension used |
| Smart engine | Partially implemented | Counters/export and hysteresis tests pass | Memory/HUD diagnosis; GPU/chunk classification unavailable |
| Adaptive FPS | Implemented, runtime testing pending | Coordinator/cap behavior unit-tested; quality/FPS tradeoff not benchmarked | Opt-in, Votex cosmetic sampling only |
| Worker/IO lifecycle | Implemented and tested at startup/shutdown | No P99 improvement attributed | One bounded IO worker; Minecraft calls stay on render thread |

Not established: fast traversal/chunk upload improvements, high entity-density gains, 30+ minute memory stability, multiplayer/community appearance synchronization, Vulkan, AMD/Intel GPUs, unusual resource packs, or performance with every third-party mod. The old public Core supports 26.2 only; a launcher entry for 26.3 is not a Core compatibility claim. No 2× FPS promise and no claim of complete 21-phase engine coverage is made.

## Reproduce

Build/unit tests: set a Gradle-compatible JDK, then `minecraft/votex-core/gradlew.bat -p minecraft/votex-core build`.

Native visual regression: `runClientGameTest` with `VOTEX_PERFORMANCE_TEST=0`. Set `VOTEX_UI_SMOKE=1` for the short menus/cache-only run; leave it unset for the 45-model/gesture regression. Set `VOTEX_PERFORMANCE_TEST=1` only for six opt-in static/HUD captures. The test run directory is recreated by Fabric, so archive results before another run. Running normal Minecraft and using the in-game benchmark avoids harness-specific pacing; keep the window active and inspect the recorded effective FPS cap before comparison.

Official references: [Fabric rendering ownership](https://docs.fabricmc.net/develop/rendering/basic-concepts), [Fabric automatic testing](https://docs.fabricmc.net/develop/automatic-testing), [NoRisk feature reference](https://norisk.gg/). The reference screenshots informed navigation and HUD organization; Votex uses its own native implementation and existing credited cosmetic assets.

Die reproduzierbaren Emote-Baselinequellen liegen unter `benchmarks/legacy/` außerhalb der Core-Source-Sets. `scripts/run-emote-benchmark.ps1` kompiliert beide Varianten isoliert und schreibt neue Ergebnisse nach `work/`. Eine zweite Reproduktion bestätigte die Größenordnung. Kompakte Messdaten werden als `docs/performance-results.json` mitgeliefert; ausführliche interne Testlogs/Bilder bleiben im Entwicklungsordner und werden nicht ins öffentliche Quellpaket übernommen.

Der Core-Abgleich berücksichtigt die tatsächlichen JAR-Bytes auch bei gleicher Versionsnummer. Ein Vorbereitungsbeleg allein darf eine korrigierte RC-Datei nicht unterdrücken. Identische und bewusst deaktivierte Cores werden von der vorhandenen Update-/Quarantäne-Logik erhalten.
