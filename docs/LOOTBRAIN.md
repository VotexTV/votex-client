# Optional LootBrain integration

The existing source was inspected at `C:/Users/wieti/Documents/Codex/2026-10-10/er/outputs/lootbrain-chestfinder`. Its current Gradle project identifies itself as LootBrain + ChestFinder `3.8.0-preview.16`, targeting Fabric 26.2, Loader 0.19.5 and Fabric API 0.161.0+26.2.

The inspected client entry point is `dev.lootbrain.client.LootBrainClient`; it exposes `instance()`. Its public settings screen has a constructor `LootBrainSettingsScreen(LootBrainClient)`. Core's optional `LootBrainBridge` checks Fabric Loader's mod list before looking up these API points reflectively. The Votex Home page can open those original settings when a compatible independent LootBrain JAR is installed. Missing or changed APIs produce a visible diagnostic.

LootBrain is not copied, rebuilt, bundled or rewritten as part of Votex. Install its independently supplied compatible JAR into the active instance's mods folder. It retains its own configuration, keybinds, inventory logic and storage. The Votex bridge has compiled, but the combined in-game settings flow has not been live tested in this run.

Future integration should use a small stable public API for screen opening, capabilities, version negotiation and settings ownership. Do not couple Votex to LootBrain's internal inventory executor or modify inventory behavior implicitly. Module categories and availability contracts are defined in `packages/shared-types/contracts.ts`.
