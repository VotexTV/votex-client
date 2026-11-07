# Third-party components

Votex's original code and artwork are provided under the root MIT license. Third-party components and Creator Collection models retain their respective licenses; the project owner's supplied default skin is identified separately in `docs/CREATOR-CREDITS.md`. Dependency versions are pinned in `pnpm-lock.yaml` and `apps/launcher/src-tauri/Cargo.lock`.

The launcher includes React/React DOM (MIT), Three.js and skinview3d (MIT), Motion (MIT), Lucide icons (ISC), Zod (MIT), Tauri (MIT OR Apache-2.0) and Rust dependencies whose original notices are collected under `docs/licenses/`. The Fabric mod uses Fabric Loader/Fabric API as separately installed dependencies; the Gradle wrapper uses Apache-2.0 licensing. Build-only dependencies may also appear in the collected notices.

The Minecraft game, Microsoft/Xbox services and Modrinth remain their respective owners' products/services. Minecraft game binaries, account tokens and third-party Minecraft mods are not redistributed in this project. Runtime game files are downloaded from their official sources. Modrinth downloads require an explicit install action. LootBrain is an optional independent mod and is not bundled here.

The Microsoft compiler/SDK and NSIS build tools were used in the development workspace and are excluded from these deliverables. WebView2 and Windows system libraries are installed and licensed separately.

Portable Java runtimes are downloaded on demand from Eclipse Adoptium/Temurin, with official Azul Zulu Community Availability builds as a fallback. They are not bundled in the Votex archives. Both OpenJDK distributions retain their GPLv2 with Classpath Exception license and accompanying legal notices. Package identity, SHA-256 and the executable's Java major version are checked. Source and release information: https://adoptium.net/docs/faq, https://github.com/adoptium and https://docs.azul.com/core/install/metadata-api. Forge/NeoForge installers and Quilt metadata retain their own licenses and come from their official repositories. CurseForge access uses the user's own authorized API key and honors distribution restrictions. Votex SoftLight and the original Atelier assets use MIT; the imported Creator Collection is separate.

The Creator Collection contains adaptations of DevMops's Angel Wing (CC0), Michael Fuchs's wings (CC BY 3.0), Poly by Google's Bat wing (CC BY 3.0), J-Toastie's Fox Hat, Frog Hat and Headphones (CC BY 3.0), Quaternius's Fox (CC0), and Kenney's Mini Characters glasses/sunglasses (CC0). Full source links, license links and modification notices are in [CREATOR-CREDITS.md](docs/CREATOR-CREDITS.md), embedded model metadata and the launcher source dialog. The Core JAR includes its own copy of `CREATOR-CREDITS.md`. No commercial client assets were extracted or purchased.

## TwelveMonkeys ImageIO catalogue decoder

The in-progress Core uses TwelveMonkeys ImageIO 3.15.3, copyright Harald Kuhr and contributors, under BSD-3-Clause for static WebP thumbnails. The explicitly included runtime modules are imageio-webp, imageio-core, imageio-metadata, common-lang, common-io and common-image. The unmodified release license is retained in the Core at `licenses/TwelveMonkeys-BSD-3-Clause.txt`. Project/source: https://github.com/haraldk/TwelveMonkeys/tree/twelvemonkeys-3.15.3 . PNG/JPEG conversion uses the Java desktop runtime. Third-party catalogue pictures retain their respective owners' rights; decoding a thumbnail does not relicense it.

## Curated Kenney CC0 models

The in-progress in-game catalog contains 65 specific CC0-1.0 adaptations from Kenney Cube Pets, Food Kit, Holiday Kit, Mini Dungeon, Pirate Kit, Space Kit and Graveyard Kit. `docs/CATALOG-COVERAGE.md` lists official source links. `assets-source/third-party/kenney-curated/manifest.json` records each exact original source identity and SHA256. The Core includes `licenses/Kenney-CC0.txt`; per-pack notices remain beside the original GLBs/textures. CC0 is not replaced by the project MIT license.

