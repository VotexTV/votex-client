# Native Minecraft runtime in Votex 6.0

## Runtime choice

Votex keeps its existing Java launcher as the default. It remains the path for Fabric, Forge, Quilt, NeoForge, Java mods, and Votex's Java-side cosmetics and emotes. The optional **Pomme** choice starts the upstream native Rust/Vulkan client as a separate process. Pomme currently advertises vanilla multiplayer from Minecraft 1.20 through 26.3; it does not load Java mod jars or Votex's Java client features. A Pomme launch uses its own Microsoft sign-in window, so the Votex offline name and Votex account session are not passed to it.

## Install and launch flow

- The launcher starts in Java mode and persists the user's runtime choice in `gameRuntime`.
- When the user selects Pomme, Votex queries the official GitHub Releases API and offers the latest Windows x64 archive for explicit installation.
- The installer accepts only the `pomme-client-windows-x64.zip` asset from the official `PommeMC/Client` release URL, requires GitHub's published SHA-256 digest, verifies the archive layout and PE x64 architecture, then records a local integrity receipt.
- The upstream executable is stored outside Java instances under Votex's data root and is launched unchanged as a separate process. The game directory and assets are isolated per Minecraft profile under `native-data/pomme/<profile-id>`.
- Pomme launches are restricted to Vanilla profiles with no Java `.jar` mods. The Java install, Java accounts, and Java profile files are left intact.
- Votex does not bundle or redistribute the Pomme executable. The UI links to its upstream source and identifies its GPL-3.0-or-later license.

## Current boundary

Pomme's upstream release currently requires the player to sign in with Microsoft inside Pomme and says its Windows builds are not code-signed. A consumer SmartScreen warning is different from a managed App Control policy block. Votex does not weaken, disable, or work around either Windows protection. If an administrator-managed policy blocks the build or runtime, the administrator must review the exact Code Integrity event and decide whether to authorize it. A Windows VM with an approved development policy is another legitimate test environment.

The current Votex machine blocked Cargo's generated build helper with Code Integrity events 3077 and 3033. Therefore the native Rust build and interactive launch have not been verified on this machine. No runtime success, compatibility beyond upstream claims, or performance comparison is asserted. The UI and launch integration are source changes only until a permitted Windows build and user-visible play test are completed.

## Upstream references

- [Pomme source and README](https://github.com/PommeMC/Client)
- [Pomme releases](https://github.com/PommeMC/Client/releases)
- [Microsoft App Control troubleshooting](https://learn.microsoft.com/windows/security/application-security/application-control/app-control-for-business/operations/appcontrol-debugging-and-troubleshooting)
