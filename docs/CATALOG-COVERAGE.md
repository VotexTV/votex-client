# Free catalog: content and goal coverage

This is ongoing source work for the in-game objective, not a new release or a completion claim.

The shared launcher/Core source catalog now contains **314** entries: 36 Votex originals and 278 attributed adaptations (nine initial Creator Collection models, 65 Kenney models and 204 additional individually licensed models). Of the 314 entries, **311 are non-cape cosmetics**. Original source animation clips are not advertised as Votex emotes. This document retains the earlier Kenney provenance below and adds the subsequent collection/native evidence.

The reference's numerical breadth is met in source and the native renderer. Source downloads, excluded candidates, alternate textures, repeated normalized geometry and community editor possibilities are not counted as installed cosmetics. This does not mark the overall in-game objective complete or announce an installer.

## Sources and exact identities

All seven official source pages identify the assets as Creative Commons CC0. Each selected source GLB and its external texture is retained under `assets-source/third-party/kenney-curated/`. Its manifest records the exact archive-relative path, source GLB SHA256, original archive SHA256, license URL and fit for every one of the 65 item IDs. `source-provenance.json` records the seven original archive URLs and hashes. Original archives and source-page HTML are retained under `work/catalog-expansion/sources/` for the internal audit and do not enter release packaging.

| Official source | Selected models | License |
| --- | ---: | --- |
| [Cube Pets](https://kenney.nl/assets/cube-pets) | 24 | [CC0-1.0](https://creativecommons.org/publicdomain/zero/1.0/) |
| [Food Kit](https://kenney.nl/assets/food-kit) | 20 | CC0-1.0 |
| [Holiday Kit](https://kenney.nl/assets/holiday-kit) | 6 | CC0-1.0 |
| [Mini Dungeon](https://kenney.nl/assets/mini-dungeon) | 4 | CC0-1.0 |
| [Pirate Kit](https://kenney.nl/assets/pirate-kit) | 3 | CC0-1.0 |
| [Space Kit](https://kenney.nl/assets/space-kit) | 4 | CC0-1.0 |
| [Graveyard Kit](https://kenney.nl/assets/graveyard-kit) | 4 | CC0-1.0 |

## Conversion and review

`scripts/import-kenney-cosmetics.mjs` verifies pinned source hashes, transforms source nodes, preserves UV textures, recomputes normals and winding after fitting, and writes the existing bounded shared mesh format. Running it without arguments stages candidates; `--install` requires the exact JSON hashes recorded by the fitted projection review.

New headpieces sit entirely above head-space Y=-8.3. Shoulder companions stay outside X=-4.45 beside the head. Back attachments start behind torso-space Z=2.4. These checks concern the default unscaled player; combinations, movement and arbitrary user scaling still require native visual inspection.

Color-independent triangle fingerprints ignore vertex ordering, translation and uniform scale. All 65 additions have distinct source and fitted geometry; copied or recolored identical meshes are rejected. This establishes distinct geometry, not independent artistic quality by itself.

Every product thumbnail projects the actual source mesh and original UV texture. Coincident palette UVs are sampled to their actual palette color. Five contact sheets show every fitted model beside a common gray Minecraft-sized reference; all five were inspected, including back views of back attachments. They remain under `work/catalog-expansion/staged/contact-1.png` through `contact-5.png`.

The 65 JSON models contain 56–1376 triangles and at most 18 parts per item. Combined JSON size is approximately 3.90 MB. All remain within existing native mesh and texture budgets.

Native neutral attachment/framing review now covers every installed entry; see the follow-up below. Arbitrary simultaneous outfit, animation, user-scale and camera combinations are not exhaustively verified. Paired multiplayer appearance/emote evidence covers selected real looks, not every model under multiplayer load.

## Offline reproduction

Selected original GLBs/textures and provenance are included with source. Conversion does not require a live downloader. Preview generation uses Python with Pillow and the workspace Sharp runtime; these are authoring tools rather than game dependencies. From the project root:

```powershell
node scripts/import-kenney-cosmetics.mjs
python -X utf8 scripts/preview-kenney-cosmetics.py
node scripts/render-kenney-previews.mjs <absolute-path-to-sharp-dist/index.mjs>
node scripts/import-kenney-cosmetics.mjs --install
pnpm exec vitest run tests/cosmetics.test.ts tests/curated-cosmetics.test.ts
```

The importer rejects installation after a fitting change until the generated JSON hashes have been reviewed again. It does not update an installer, website, profile, account or saved world. Native dynamic rendering remains a separate acceptance gate from projection approval.

If regenerating the original Atelier models with `generate-cosmetics.py`, regenerate the original Creator Collection next and restore this curated collection last. The base generator intentionally writes its base roster and does not itself recreate licensed source adaptations.

## Additional 204 individually licensed adaptations

The 235 retrieved source candidates yielded 204 accepted identities: 160 CC0 and 44 CC BY 3.0. Every original author, source model URL, individual license and adaptation notice is listed in `assets-source/third-party/poly-curated/ATTRIBUTION.md`, the web catalog's `POLY-ATTRIBUTION.md` and Core's `licenses/Poly-Catalog.md`. Source GLBs and publisher-page records retain exact hashes in the source manifest. The Votex software MIT license does not replace their asset licenses. NoRisk assets/code were not used.

`scripts/import-poly-cosmetics.mjs` preserves original colors and UV materials, converts bounded textures to PNG, and bakes source skin weights into the original rest pose. Head ornaments, facewear, shoulder companions and back accessories have separate fits. Books are upright; spectacle temples wrap outside the cuboid head. Rounded footwear, unsuitable helmets/headsets/gas masks, multi-hat source grids, indistinct necklaces, unfinished white variants and close palette/proportion variants were excluded.

Source and fitted triangle fingerprints ignore colors, vertex order, translation and uniform scale. Fitted duplicates are rejected against both the new collection and the prior catalog. The final staged index, not the number of files in the source or staging directory, defines installation. `--install` requires exact approved JSON hashes from the fitted projection review.

Offline conversion uses the same preview scripts with optional stage `work/catalog-expansion/poly-staged`; the Poly importer uses the retained originals without a live downloader. Its accepted source hashes, approval hashes, safe coordinates/indices/UV/texture budgets and distinct geometry pass the catalog regression tests.

## Actual native review, 2026-10-22

`work/ingame-goal/catalog-run-03/evidence/native-catalog-proof.json` records a real Java25/Fabric/GPU client equipping all 314 entries through `AvatarPreview`, `AvatarRenderer` and `WearableLayer`. Each item has an actual native PNG and geometry/material-group checks. All twenty contact sheets were visually inspected, including wings, tall crowns, facewear and rear attachments. Independent pixel analysis found no viewport-edge clipping in any of the 314 frames. Automatic preview framing now includes equipped geometry.

`assets-source/third-party/poly-curated/native-reviewed.json` binds all 314 exact model hashes to the native PNG hashes, twenty inspected sheets and proof digest. The recorder confirmed byte-identical web, Core source and actual built runtime resources. This proves neutral attachment/framing, not every possible outfit/emote/scale/camera combination or a multiplayer FPS benchmark.

The final client exited zero and logged saved chunks for every dimension. A **test-source-only** MC26.2/Fabric scheduler mixin pumped Fabric phases while the actual integrated-server halt job stayed on its server thread. It is absent from the distributed Core. Run02 completed model checks but hung in the test framework's shutdown barrier; it is not described as a clean successful build.

Parsed Core model data and launcher model promises are bounded to 16 entries. The GPU wardrobe preview cache remains bounded to 32 and releases on leaving the screen. Eviction and reload are tested, so the 314-item catalog is not kept as 314 parsed models or live previews in memory.
