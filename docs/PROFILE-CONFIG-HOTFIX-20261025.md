# Preview3: legacy profile configuration repair

The user reported that the public Preview2 installer starts but shows “Konfiguration geschützt”. The screenshot lists `profiles[0..2].starterMods`: expected object, received null. This is independent of Minecraft authentication and Windows signing.

The native `Profile.starter_mods` is `Option<StarterMods>` and previously serialized absence as JSON null. The frontend schema accepted an absent property but rejected that native representation. A regression test with three preserved profiles reproduced the exact three Zod errors before the change. Current parsing normalizes only null/absence to an absent recipe; it does not populate a new-profile package, reset invalid data or enable opted-out mods. Explicit recipes keep every false flag, extra and shader choice. Rust now omits `None` when serializing a Profile while still accepting existing null records. The UI identifies this repair as **0.4.1 Preview3**.

Verification:

- 102 frontend tests pass, including null/missing/explicit opt-out recipes, unchanged multi-profile fields, JSON round trips and continued rejection of malformed recipes. TypeScript and production Vite build pass.
- 92 regular Rust tests pass, zero fail,15 opt-in ignored. The new native serde/validation test covers legacy null, omitted serialization and exact opt-out preservation.
- The actual local `VotexClient/config/launcher.json` was read without modification: two existing profiles with two null recipes loaded successfully, all pre-existing profile fields and the active profile were preserved, and JSON serialization remained stable. The synthetic screenshot regression separately covers three profiles. Only counts/hash evidence is retained; no personal configuration copy is packaged or published.
- The optimized Tauri/NSIS build succeeds. Existing chunk-size and two unused-function warnings remain. No EXE, Minecraft, account session or device was started for this repair; the prior deferred window/audio review remains deferred. Native launch tally stays61.

The installer and portable fallback are in `../releases/0.4.1-preview3-20261025/`. The installer is `VotexClient-0.4.1-preview3-2026-10-25-Setup.exe`,50,663,599 bytes, SHA256 `2fb4f951c75951360678936474a39bb21e1d1961e1c79ad4e98d32bc09c4074e`. Packaged EXE SHA256 `7ceb07fdeb4b0d09990ac697a87203d8b2275777a717be81926dc8884fc2ea1c`. Included resource Core is the native-tested notice02 SHA256 `7c98b20a6c9baefba79bf7a9263786f1ec8391fb1c225d9e23da0e5dc10f40d5`; the later unverified-rendering blur candidate is deliberately not included. The portable ZIP passed CRC. No user runtime data or accounts are included.

The same existing public Site now serves Preview3 as its current download. Final source commit `d60ffdeed8053ed866e9e8b0e172229d485a8561`, saved version13 `appgprj_6ad5e0868dc08191b2ec055813bec724~appgver_cba82d3973a081918a374ade74919319`, deployment `appgdep_6ade1c4189d88191beef320c6f561530` succeeded with environment revision4. The bounded maintenance upload returned201 after byte/hash verification; its runtime secret was removed and the final production route unconditionally returns404 for uploads. D1/BUCKET bindings and public audience were preserved. Prior Preview2/RC artifacts are unchanged. Thirteen Site tests and artifact validation pass; live page link/metadata, HEAD, byte range, complete downloaded installer SHA256, previous download, disabled upload and community GET checks pass.

Public URL: https://votex-client-project.adoredpeach257.chatgpt.site/downloads/VotexClient-0.4.1-preview3-2026-10-25-Setup.exe

User action: close the old launcher, install Preview3 over the existing installation, then open it and check the title says Preview3. Do not delete the configuration or profiles. This is a focused repair preview, not final acceptance of the broader blocked in-game goal. The earlier immutable preview/audit receipts remain historical snapshots.

Evidence under `work/ingame-goal/`: `configfix-frontend-20261025.json`, `configfix-rust-20261025.log`, `configfix-build-20261025.log`, `configfix-actual-settings-20261025.json`, `configfix-package-20261025.json`, `configfix-validation-20261025.json`, `configfix-site-tests-20261025.log`, `configfix-live-download-20261025.log`. Reproduction/test sources and packaging/read-only audit helpers are retained alongside them.
