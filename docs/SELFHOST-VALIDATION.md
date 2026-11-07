# Self-host component: evidence and remaining boundaries

Scope: preparing the user-requested self-hostable server component. This is part of the active in-game objective, not a launcher release, external deployment or public-service claim.

## Real Dedicated Server run, 2026-10-21

- Source: `minecraft/votex-selfhost/Prepare-Server.ps1`; fresh work directory `work/ingame-goal/selfhost-proof/server`.
- Official Fabric Installer 1.1.2 was originally verified against SHA-1 `74685b5a0178c4dabd4be741a276d35c59b5ec96`, then independently matched against the publisher's SHA-256 `61e035bf7bf70153e127440ce34de47c9036f0a2d0c65d1529454bd35ceefe4f`. Preparation now requires that SHA-256 pin.
- Minecraft 26.2, protocol 776; Fabric Loader 0.19.5; Fabric API 0.161.0+26.2; Simple Voice Chat 2.6.24+26.2.
- Real Relay source build succeeded separately; the client source/build output was not rebuilt by the companion build.
- Actual TCP status round trip returned version 26.2 / protocol 776.
- A test LoginStart received the server's RSA encryption challenge with `shouldAuthenticate=true`. The probe closed before authentication: no account or valid player session was supplied.
- Whitelist and enforce-whitelist remained enabled. `whitelist list` returned no allowed players; `list` returned zero online players. RCON and Query disabled.
- Native process socket inventory: PID 5824 listened only on 127.0.0.1:25565/TCP and 127.0.0.1:24454/UDP. Voice logged its server startup.
- Server was closed through `stop`, exited zero, and logged saved chunks for all three dimensions.

Machine-readable record: `work/ingame-goal/selfhost-proof/server-proof-results.json`. Preserved runtime log: `work/ingame-goal/selfhost-proof/dedicated-authenticated-defaults.log`. These work artifacts are internal and never added to the self-host package.

The JVM's Windows performance-counter lookup emitted an OSHI/Perflib warning; server, networking and Voice subsequently initialized successfully. No registry or security settings were changed.

## Tunnel status

`Prepare-Tunnel.ps1` creates a private CA, a server certificate with DNS/IP SAN, a client certificate, fresh 256-bit token and configs requiring certificate verification in both directions. The CA private key is disposed after signing. No system trust-store changes are made. Ports are restricted to the configured Minecraft/Voice routes; no administration dashboard is enabled.

The official FRP 0.71.1 Windows archive SHA-256 was verified as `b90074b2b6a1d792a7a40f085ac6d276bcab2d1bc60201f8a86ca53ce65ce821`. Attempting `frps.exe verify` returned **WinError 4551**, Windows Application Control blocked execution. The process did not start. This policy was not bypassed. Therefore TCP/UDP echo, mTLS rejection and invalid-token/port rejection are **not runtime proven on this host**. The loopback-only verification harness is prepared for a permitted operator environment; it does not make an Internet connection.

Generated certificates, private keys, auth tokens, config files containing them and verified binary archives remain only in private work paths. `scripts/package-selfhost.py` uses an explicit source-file allowlist and does not read any generated tunnel directory.

## Separate paired-client QA

The paired-client server is a different work directory, `work/ingame-goal/paired-server`, and was bound only to **127.0.0.1:25566/TCP** with Voice **127.0.0.1:24455/UDP**. It used test-only offline UUIDs for exactly `VotexPeerA` / `VotexPeerB`, with those two identities whitelisted and op permissions for controlled fixture commands. Its properties are labelled local QA only. These test settings are never copied into `minecraft/votex-selfhost`, a package or existing user profiles/worlds.

The actual paired run completed on 2026-10-21. Exactly two separate Minecraft processes were launched once, PID 25480 / 29100; both exited **zero**. Launcher record: `work/ingame-goal/paired-run-01/paired-launch-result.json`. Each role wrote its own `coordination/evidence-A/paired-proof.json` or `evidence-B/paired-proof.json`. Five actual native PNG captures per role show the two real connected entities, received looks, built-in/custom emotes, and recipient invitation. The comparison screen renders the connected player entities through Minecraft's real renderer; it does not substitute preview poses. It places them in separate panels, so those images do not by themselves measure world-space palm contact.

Proven through the actual Fabric TCP connection:

- Different real connection UUIDs/names; remote PNG skin and cape textures; selected server-validated custom model `custom-a1` and built-in headwear, followed by an appearance revision.
- Built-in wave and a transmitted custom emote definition; the recipient's actual baked model joints changed, then returned to Minecraft's vanilla idle arm after stop.
- Native **G** accepted the recipient invitation near the hotbar; both participants received the handshake, and both native arm matrices were active within one server world tick in the recorded samples.
- Stopping one participant cleared the other's active handshake within 1.5 seconds. Real **D-key** movement cancelled both participants within 1.5 seconds. Leaving during a handshake cleared the remaining player's emote and remote texture state within two seconds.
- Acceptance after moving beyond reach was rejected; expired G and locally declining started no animation; moving beyond the 128-block relay range removed peer looks, and returning retransferred them.
- Successful JOIN history recorded exactly `127.0.0.1:25566`, the actual timestamp and the correct `paired-a` / `paired-b` profile ID in each client's local `config/votex/last-server.json`.

Simple Voice Chat was present in both isolated clients with its official pinned JAR. Both clients and the server proved completed UDP authentication/initialization and connection. The Votex bridge reported the actual connection. To ensure **no microphone capture**, the test process intercepted and cancelled SVC's microphone-start event before connecting; both native microphone-thread and audio-recorder objects remained absent, onboarding stayed unfinished, and audio remained disabled/muted. This test-only hook is absent from the distributed Core source set. **No microphone, audible playback or audio round trip was attempted.**

One fixture correction was necessary without restarting either client: Minecraft centers integer teleport coordinates at half a block, so the console changed role A's initial Z from 0.5 to explicit decimal 0.0. The fixture source now uses that decimal value. The correction is preserved in `operator-fixture-correction.json` and the actual server log; it did not change the loaded client code, production settings or users' profiles. The QA platform was created only after force-loading 25 local test chunks. Forced chunks were removed before `stop`; the server then exited zero and saved every dimension.

The emitted role JSON uses an imprecise limitation phrase about "separate rule checks" for line of sight. The actual evidence is narrower: the server source calls `hasLineOfSight` for both players, but this paired run and current pure rule tests do **not** exercise an obstructing wall. Treat runtime LOS rejection as open. The run also does not prove packet-loss recovery, Microsoft-authenticated identity, Internet reachability or exact hand-palm contact across all player scales.

## What is not implied

Follow-up on2026-10-22: paired-run02 used two further actual clients, both exit0. The real world/native renderer measured the palms below0.001 blocks apart on both clients, and the side-view native world image was inspected (HANDSHAKE-CONTACT.md). The network/appearance/consent/interruption lifecycle checks passed again. Its own loopback QA server exited0 through a private scoped JVM shutdown helper after console stdin was unavailable. Unlike the first run, the second run's retained log does not establish final saved chunks in every dimension; do not claim that it does. No test helper or offline QA properties enter the self-host source package.

- The authenticated-defaults server boot is not proof of a Microsoft-authenticated player session; the successful paired run explicitly used local QA identities.
- Actual UDP initialization is proven, while microphone capture, audible playback and router reachability remain untested.
- Offline QA UUIDs are not verified Microsoft identities.
- A tunnel config is not a public host or DNS record.
- No external server, paid resource, account, router forwarding or firewall rule has been created.

Primary references: [Fabric server installation](https://fabricmc.net/use/server/), [Voice server configuration](https://modrepo.de/minecraft/voicechat/wiki/server_config), [FRP TLS verification](https://gofrp.org/en/docs/features/common/network/network-tls/), [FRP pinned release](https://github.com/fatedier/frp/releases/tag/v0.71.1).

##314-entry Relay checkpoint,2026-10-24

The older pending-wall note above applies to paired01 only. Paired05 later passed real-connection opaque-wall rejection both before invitation and after invitation on G acceptance, with two independent native clients exit0 and exact314-entry Relay catalog. Both actual native world palm gaps were below0.001 blocks. Its server was stopped through its console, exited0 and saved all dimensions. See HANDSHAKE-CONTACT.md and work/ingame-goal/paired05-server-20261024.json; offline loopback identities still do not prove public/authenticated hosting or audible audio.

The refreshed private review archive is work/ingame-goal/Votex-Selfhost-2026-10-24.zip (98,675 bytes, SHA256 a908c5ee947b40b243e444b894ab2bfd783d0fea54563c7c458420e61e8e0a15). It passes ZIP CRC and every manifest file hash, contains the current server-only Relay and explicit source/template allowlist, and retains online-mode/whitelist/enforce-whitelist plus loopback defaults. It contains no QA world or generated tunnel credentials. No new public deployment is implied. FRP AppControl and deferred hardware audio boundaries remain unchanged.
