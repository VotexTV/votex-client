# In-game social acceptance

Status: source implementation and local native two-client proof. This is not a release or global-hosting completion claim.

| Requirement | Current authoritative evidence | Boundary |
| --- | --- | --- |
| Other Votex players see built-in and custom looks | Two separate native clients, server-validated custom mesh, received PNG skins/capes and appearance revision; five native images per client | Local loopback identities, not Microsoft account verification; custom textures must be baked to vertex colors |
| Other players see built-in/custom emotes | Real C2S/S2C packets, actual recipient entity and baked model joint checks, wave and custom emote captures | Public packet-loss/reconnection stress not exercised |
| Recipient consents to handshake | Actual near-hotbar invitation and native G press; both local/remote native arm transforms sampled within one server tick | Images show actual entities in comparison panels; exact physical palm contact across different player scales is not measured |
| Safe interruption | Actual stop and D-key movement cancel both partners within 1.5 seconds; disconnect clears counterpart and texture state within two seconds | No forced physical teleport, rotation, hitbox or camera changes in production; console teleport is QA fixture setup only |
| Decline, expiry, reach and presence | Local decline starts no animation; expired G does nothing; acceptance after moving beyond reach rejected; >128 block removal and rejoin pass | Obstructing-wall LOS rejection is implemented by two `hasLineOfSight` calls but remains runtime unverified |
| Integrated Voice | Both clients' real UDP authentication/initialization and connected state, server logs, actual Votex bridge status | Microphone thread and recorder deliberately absent; no audio capture, audible playback or Internet UDP test |
| Own nametags retain player identity | Remote look includes own tag; native world invitation image shows `VotexPeerA · Votex Client` | Global identity comes from Minecraft authentication; offline QA identities are explicitly labelled |
| Last successful server for launcher Join | Both clients wrote the actual endpoint, profile ID and JOIN time after completing their TCP play connection | Direct QuickPlay CLI assembled/validated in backend; not exercised by the paired harness, which connected through Minecraft's native ConnectScreen |
| Self-hosted worldwide access | Pinned Fabric/Voice server scripts, real authenticated-defaults boot, whitelist, private mTLS FRP configs and package allowlist | No public host was provisioned. FRP execution was blocked by Application Control 4551, and Microsoft player/public-network/audio cases remain open |

Evidence: `work/ingame-goal/paired-run-01/paired-launch-result.json`, per-role `coordination/evidence-A/paired-proof.json` and `evidence-B/paired-proof.json`, actual server `dedicated-server.log`, native PNGs and `operator-fixture-correction.json`. Both clients exited zero; the server was stopped cleanly after removing its test-only forced chunks. The original paired result files are preserved; any later fixture-source correction does not retroactively change their recorded source hashes.

Implementation: common `VotexRelay`, `SocialRules`, `HandshakeBook`; client `VotexSocial`, `LocalLooks`, `EmotePlayer`, `LastServerHistory`; test-only `PairedClientGameTest`, `PairedProofScreen`, `PairedVoiceNoCapture`. Test code is in the isolated gametest source set and does not enter the Core JAR. Production self-host templates retain Microsoft session authentication and explicit whitelist enforcement.

See [self-host validation](SELFHOST-VALIDATION.md) for setup/runtime evidence and remaining infrastructure work, and [Play history](PLAY-HISTORY.md) for the launcher API.
