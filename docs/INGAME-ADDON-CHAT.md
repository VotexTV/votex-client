# In-game addon library and chat audit

This is source work for the active in-game objective, not a released version. Native evidence is required in addition to compilation and the checks below.

## Addon changes

The native Mod Library enumerates actual Fabric containers, disabled JARs, shader ZIPs and resource packs. Modrinth queries require Minecraft 26.2 and Fabric (Iris for shaders). Required dependencies are resolved before staging, runtime/version predicates are checked, and downloaded files are checked against Modrinth's SHA-512. No loaded mod JAR is changed while Minecraft runs.

Imports use the same parser and staging queue. The source remains intact. A native file chooser and file drop are available. A user can review or cancel pending installs, activation changes and removals. CurseForge now has a configured official API adapter using the launcher's existing Windows Credential Manager entry. Without that user-supplied key, the native screen explains setup rather than presenting a fake catalogue. See `INGAME-CURSEFORGE.md`; authenticated end-to-end behavior remains unverified because no key is available.

Important boundaries:

- HTTP/CDN origins, redirects, timeouts, response sizes and package filenames are restricted.
- JAR/ZIP imports and streamed hashes have a 128 MiB per-file bound. A single download plan is limited to 512 MiB; queues contain at most 100 actions and 512 KiB of the actual written JSON.
- Profile paths and existing parent directories must remain inside the profile, including links/junctions. Native icons are limited to 32 in-flight requests and an 80-entry LRU. Each uploaded icon is at most 128 by 128 pixels.
- Minecraft, Java, Fabric Loader and Votex Core IDs cannot be imported as ordinary mods.
- The launcher validates the whole plan, including case-insensitive Windows overlap and source hashes, before modifying addon files. It journals SHA-512-protected originals, limits recovery copies and verifies every recovery source before restoring any file. Modified or oversized recovery backups cause an explicit error and retain the journal.
- Original addon bytes and the applied queue are retained in a per-transaction backup directory. A failed or interrupted transaction restores the original addon files and metadata. Resulting `installed.json` must remain within 4 MiB.

## Chat behavior

The existing Minecraft history remains authoritative. Duplicate grouping changes the rendered presentation only; original message objects, signatures, source and styled content remain in history. The context menu copies a message/name or prepares an unsent `/msg` draft. CTRL+F searches the original history, including visually grouped duplicates. Reply names are constrained to Minecraft profile-shaped names.

## Verification

`cargo test pending_addons` currently passes 12 cases. The additional native-fixture integration case is explicitly ignored until its actual Java fixture exists. These cases cover installation/backups, corrupt downloads, changed originals, toggle staging, profile-junction escape, case overlap, interrupted recovery, tampered/oversized recovery sources and duplicate journal paths.

`compileGametestJava` and the Java unit suite pass. `LibraryChatGameChecks`, enabled with `VOTEX_LIBRARY_CHAT_TEST=1` in the consolidated isolated game run, exercises actual native buttons, live Modrinth HTTP search, file-drop import, review/cancel/restage, ChatScreen right-click and CTRL+F, Unicode search, real clipboard copy/name, unsent reply draft and duplicate presentation/signature preservation. It restores the clipboard and existing test chat history. Synthetic signed-message objects prove GUI preservation, not cryptographic authenticity or authenticated multiplayer delivery.

The native run writes `library-chat-proof.json`, screenshots and exactly one synthetic import queue into its isolated `clientGameTest` game directory. After that run, the exact produced queue can be applied by the launcher implementation in a temporary clone:

```powershell
$env:VOTEX_ADDON_NATIVE_FIXTURE = '<isolated clientGameTest game directory from library-chat-proof.json>'
cargo test applies_native_java_staging_fixture --manifest-path apps/launcher/src-tauri/Cargo.toml -- --ignored --nocapture
```

That check refuses a non-test fixture path and never applies the queue to user profiles. It compares the resulting JAR SHA-512 and launcher metadata to the original native import. Until the native run and this follow-up complete, native search/import/chat and cross-language application remain pending proof.
