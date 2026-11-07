# Launcher Play history and direct Join

`get_play_history` is a read-only Tauri command returning:

```json
{
  "lastProfileId": "main",
  "lastServer": {
    "name": "My server",
    "address": "play.example.org:25565",
    "profileId": "main",
    "joinedAt": "2026-10-21T20:46:57.189Z"
  }
}
```

Either field can be absent. Querying history never changes the selected profile. The launcher records `lastProfileId` in its own `config/play-history.json` only after successfully creating the Minecraft process. It describes the last launched profile, even if that game subsequently crashes; it is not a claim of successful gameplay.

Core records the server only in Fabric's actual `ClientPlayConnectionEvents.JOIN` callback. Failed connection attempts, singleplayer worlds and arbitrary UI selections are not recorded. The profile comes from the launcher's `votex.profileId` property; the endpoint/name come from Minecraft's current connected `ServerData`. The existing bounded diagnostics IO worker atomically writes `config/votex/last-server.json` in that instance. Only schema version, profile ID, display name, normalized address and UTC join time are stored; no account tokens, chat or server secrets are included.

The backend scans only existing configured profile IDs and chooses their newest valid successful-join record. Reads are bounded to 16 KiB, reject symlinks/out-of-root targets, validate schema/profile/timestamp/address, and omit malformed records. An absent or deleted profile cannot become the last launched profile through this query.

`run_game` accepts an additional optional frontend parameter **`serverAddress?: string`**. Normal launches omit it. A Join launch uses `action: "launch"`; install-only and safe-mode actions reject a supplied endpoint. DNS/IPv4/bracketed IPv6 and ports 1–65535 are validated. URLs, whitespace, controls, paths and command-like values are rejected. The normalized address is appended as one separate process argument after `--quickPlayMultiplayer`; there is no shell command construction. The official Minecraft version metadata must advertise that flag, otherwise the backend explains that this version requires joining through its own Multiplayer menu.

The launcher accepts a matching startup nonce at either `title-screen` or `world-joined`, then retains the existing 45-second stability window before protecting its recovery snapshot. QuickPlay can enter a server without remaining on the title screen, so the Core's real JOIN callback also acknowledges `world-joined` through the same bounded IO worker.

Validation: two Rust tests cover optional/separate Join arguments, malformed endpoints/ports, IPv6, newest existing-profile history and unchanged launcher state. Two pure Java tests cover JOIN-record metadata, host normalization, Unicode IDN and invalid values. The full Java suite passed 71 tests. The actual two-client run wrote the real `127.0.0.1:25566` endpoint, distinct correct `paired-a`/`paired-b` IDs and UTC JOIN times; these records are included in each role's paired proof. The paired harness connected through native `ConnectScreen`, so it does not constitute a runtime test of the QuickPlay command-line path or the launcher's 45-second world-joined monitor branch.
