# Current internal EXE and real Core update checkpoint,25October2026

Later source note: MENU-BLUR-20261025.md identifies the newer unverified-rendering blur candidate. The prepared EXE/resource and all hashes below remain unchanged; they do not contain that later candidate.

The current optimized Windows EXE is prepared in `work/ingame-goal/current-review-20261025/app/` with an explicit `resources/` directory and a separately copied private offline profile/world/cache under `store/`. It has not been started. The user chose “Fensterprüfung später” after being asked to resume the earlier Escape-stopped Windows test. Window control and all packaged EXE/game runtime acceptance remain deferred; no alternative window-control route or hidden EXE initialization was used.

Artifact identity:

- EXE SHA256: `969ccfc95221c34b5ebce63cdeb3b91ba8ad65a5d2d0f3934ece10f756d8c7e5`,37,415,936 bytes.
- Core SHA256: `7c98b20a6c9baefba79bf7a9263786f1ec8391fb1c225d9e23da0e5dc10f40d5`. Built, staged and copied Core bytes agree with successful native `notice-native-run-02` and all159 production classes. The JAR passes CRC and contains no GameChecks/GameTest classes.
- The release/no-bundle Tauri build, TypeScript and Vite pass. Existing large-frontend-chunk and two unused-Rust-function advisories remain visible. Frontend99 tests pass. `prepared.json` retains per-asset hashes, the exact executable/Core, fixture provenance and unstarted state. The only subsequent Rust source addition is a test-only module function; production runtime code is unchanged from the build.

The new opt-in Rust check `core_install::tests::real_same_version_update_preserves_settings_disable_choice_and_recovery` executes production `core_install::apply` and `recovery::restore` using the two actual Core0.4.1 artifacts: previous native-verified SHA25625af63946d78d957ea6a867262aec34eb0eff433ef7adb48b022c0c22b84f82d and current native-verified SHA2567c98b20a6c9baefba79bf7a9263786f1ec8391fb1c225d9e23da0e5dc10f40d5. Both must identify as the same version and have different bytes.

In temporary Stores the test proves byte-exact installation and preservation of the previous JAR, disabled-Core preference retained on automatic update, explicit reactivation, no duplicate work/snapshot on an identical update, and actual restoration of the old artifact. HUD/appearance/design fixtures, game options and a synthetic unopened world file remain unchanged. Core update does not rewrite launcher configuration. Explicit recovery may normalize missing legacy Profile fields to their existing defaults, while preserving all original values; the initial strict raw-byte comparison of that normalized launcher JSON was a test-fixture error, retained in `real-core-update-20261025-attempt01.log`. The corrected test separately checks byte preservation during update and full semantic state preservation during recovery.

Executed results:

- Regular Rust suite:91 passed, zero failed,15 opt-in ignored.
- Explicit actual-artifact Core update/recovery test:one passed.
- Explicit current actual-JAR microphone-consent capability/adapter check:one passed; this reads the JAR and opens no device.
- No network dependency download, account login, microphone, EXE launch or Minecraft process was used for those checks. Native launch tally remains61.

Logs: `internal-notice-build-20261025.log`, `frontend-notice-20261025.json`, `real-core-update-20261025.log`, `current-review-rust-20261025.log`, `current-review-core-guard-20261025.log`, all under `work/ingame-goal/`. Machine-readable record: `current-review-20261025/validation.json`.

The internal artifact stays unsigned and is not a new preview handoff. The published website installer remains immutable. Current packaged visual/launch/settings-refresh acceptance is postponed by the user. Authenticated/public-service acceptance and the explicitly deferred physical audio test remain separate. The overall in-game goal stays ACTIVE.
