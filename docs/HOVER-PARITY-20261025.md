# Shared hover and effect controls

Later source candidate: [MENU-BLUR-20261025.md](MENU-BLUR-20261025.md) adds actual native blur strength mapping. It is compiled/unit-tested, but its new hook and rendered output await the user-deferred window check. Earlier native results below do not cover that later candidate.

Status at the earlier hover checkpoint: implemented and isolated-tested. The full in-game objective remains ACTIVE. Existing preview/internal EXEs were not replaced, and Windows control remains stopped after the user's Escape.

The game-theme export already retained effect fields, but native theme loading previously discarded most of them. Schema1 now reads hover, mouse proximity/light, secondary light color, glass, glow, reflection, shadows, press wobble, lift, tilt, speed, stiffness, damping and reduced motion. Missing controls retain backward-compatible defaults; invalid flags and out-of-range values are rejected. The real theme-file watcher applies changes without restarting Minecraft.

Native buttons, wardrobe cards and the emote wheel share bounded, elapsed-time spring channels. Visual content/light moves within stable input rectangles, keyboard focus remains visible independently of hover, and disabled/reduced effects clear stale light immediately. Workspace entrance timing follows the theme speed. Glass-off surfaces composite their original translucent color onto the panel rather than becoming a solid accent. Primary labels select a legible foreground; zero shadow avoids shadow draws.

Launcher buttons now have one hover transform instead of a CSS lift competing with a wrapper animation. The light coordinator covers ordinary buttons and portal controls, listens to the proximity switch immediately, uses configured spring response, and removes its tracking state when disabled. Profile cards retain their calm fixed translation. Account and icon presses now honor the press switch and reduced motion too. The light and hover remain bounded; they do not require an idle animation loop after settling.

## Verification

- Java:116 tests, zero failures/errors, one explicit opt-in skip (115 executed passes). Five spring/theme tests cover frame-rate equivalence, reversals, live switches and retained controls. Three color tests cover compositing, preservation of readable preferred text and contrast across4096 accent colors. `test compileGametestJava jar sourcesJar` completes successfully.
- Frontend:99 tests pass, zero failures; TypeScript/Vite production build passes. The existing large-chunk warning remains. One focused actual Rust game-theme-export test passes; this is not a rerun of the full Rust suite.
- `hover-browser-run-04` passes actual production-CSS enter/leave, live light-off, raw navigation light, account/icon press-off, reduced-motion tracking cleanup and reduced account/icon press cases. Both final browser screenshots were visually inspected. The elapsed-time transition stays bounded; maximum measured motion rate is0.024963px/ms. Headless samples include a50ms gap, so these results do not promise a minimum rendering FPS.
- `hover-native-run-02` exits0. Actual Minecraft font, sprites, shared widgets and the real theme-file watcher pass five on/off variants at768×480 and1280×720. Both stable visible-edge click checks pass. All12 final screenshots were reviewed, with compact ultra/flat originals inspected separately. Scripted render-pointer coordinates/native widget events were used; no OS mouse/key automation or microphone was used.

Final native Core SHA256: `25af63946d78d957ea6a867262aec34eb0eff433ef7adb48b022c0c22b84f82d`. All157 production classes match the tested JAR; all recorded Java source hashes remain unchanged. The staging Core matches byte-for-byte and contains the pointer-light sprite, with no gametest classes. The machine-readable receipt is `work/ingame-goal/hover-checkpoint-20261025.json`.

## Retained attempts and limits

The first native hover run passed functionality but its images revealed flat-mode accent saturation and faint primary text. Those findings prompted the compositing/contrast correction; only run02 represents that final source. The native tally is now58, including all earlier failures. During development a test-only checked-exception compile error and two new color assertions failed before correction; none is represented as a passing attempt.

Browser run01 failed before launch because the bundled Chromium executable was absent. Runs02/04 use a fresh, isolated headless installed Chrome context without a personal profile or new browser download. Run02 passed the initial scope. Run03 failed an absolute per-sample jump bound; run04 records time and displacement and checks elapsed-time velocity plus the same final bounds/switch behavior. Its50ms sampling gap is retained above. No minimum frame-rate or physical desktop animation-quality claim follows from that test.

Native background blur uses Minecraft's blur pass; the theme enables/disables it, but its kernel strength is not asserted to match CSS's numeric blur radius. Launcher background media/layout are separate from in-game material controls. Current-source packaged desktop launch/reverse-sync acceptance, authenticated/public community services and the deferred hardware audio test remain separate. The old internal EXE still contains its earlier frontend/Core and was left untouched.
