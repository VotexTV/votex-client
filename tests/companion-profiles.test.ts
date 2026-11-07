import { expect, it } from "vitest";
import { readFileSync } from "node:fs";
import catalog from "../apps/launcher/src/data/cosmetic-manifest.json";
import profiles from "../apps/launcher/src/data/companion-profiles.json";

it("shares a bounded movement profile for every companion in both renderers", () => {
  expect(Object.keys(profiles).sort()).toEqual(catalog.filter(item=>item.slot==="pet").map(item=>item.id).sort());
  const core=readFileSync("minecraft/votex-core/src/main/resources/assets/votex_client/companion-profiles.json");
  expect(core).toEqual(readFileSync("apps/launcher/src/data/companion-profiles.json"));
  for(const profile of Object.values(profiles)) {
    expect(["organic","aquatic","floating","mechanical"]).toContain(profile.kind);
    expect(profile.liftScale).toBeGreaterThan(0);expect(profile.liftScale).toBeLessThanOrEqual(1);
    expect(profile.tiltScale).toBeGreaterThan(0);expect(profile.tiltScale).toBeLessThanOrEqual(1);
  }
});
