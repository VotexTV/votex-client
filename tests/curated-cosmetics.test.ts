import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import catalog from "../apps/launcher/src/data/cosmetic-manifest.json";
import sources from "../assets-source/third-party/kenney-curated/manifest.json";
import reviewed from "../assets-source/third-party/kenney-curated/reviewed.json";

const hash = (data: string | Buffer) => createHash("sha256").update(data).digest("hex");
const model = (id: string) => JSON.parse(readFileSync(`apps/launcher/public/assets/catalog/${id}.json`, "utf8"));

/** Ignores vertex order, material colors, normals, UVs, uniform scale and translation. */
function geometryFingerprint(parts: any[]) {
  const min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
  for (const part of parts) for (let i = 0; i < part.positions.length; i++) {
    const axis = i % 3;
    min[axis] = Math.min(min[axis], part.positions[i]);
    max[axis] = Math.max(max[axis], part.positions[i]);
  }
  const center = min.map((v, i) => (v + max[i]) / 2);
  const scale = Math.max(...min.map((v, i) => max[i] - v));
  const triangles = new Set<string>();
  for (const part of parts) for (let i = 0; i < part.quads.length; i += 4) {
    const vertices = part.quads.slice(i, i + 3).map((index: number) => [0, 1, 2].map(axis => Math.round((part.positions[index * 3 + axis] - center[axis]) / scale * 10000)).join(","));
    triangles.add(vertices.sort().join("/"));
  }
  return hash([...triangles].sort().join(";"));
}

describe("curated CC0 cosmetic source identities and attachment bounds", () => {
  it("keeps an exact licensed source and reviewed geometry for every installed adaptation", () => {
    for (const item of sources.items) {
      const bytes = readFileSync(`assets-source/third-party/kenney-curated/${item.sourceFile}`);
      expect(hash(bytes)).toBe(item.sourceSha256);
      expect(bytes.subarray(0, 4).toString()).toBe("glTF");
      expect(item.license).toBe("CC0-1.0");
      expect(item.sourceUrl).toBe(`https://kenney.nl/assets/${item.pack}`);
      const license = readFileSync(`assets-source/third-party/kenney-curated/${item.pack}/LICENSE.txt`, "utf8");
      expect(license).toContain("CC0");
      const installed = catalog.find(entry => entry.id === item.id);
      expect(installed?.slot).toBe(item.slot);
      expect(hash(readFileSync(`apps/launcher/public/assets/catalog/${item.id}.json`))).toBe(reviewed.sha256ById[item.id as keyof typeof reviewed.sha256ById]);
      expect(model(item.id).sourceAsset.sha256).toBe(item.sourceSha256);
    }
  });

  it("counts actual different geometry instead of palette swaps or renamed duplicates", () => {
    const fingerprints = new Set<string>();
    for (const item of sources.items) {
      const fingerprint = geometryFingerprint(model(item.id).parts);
      expect(fingerprints.has(fingerprint), `${item.id}: duplicate fitted geometry`).toBe(false);
      fingerprints.add(fingerprint);
    }
    expect(fingerprints.size).toBe(sources.items.length);
  });

  it("fits head pieces, shoulder companions and back attachments outside the default player volume", () => {
    for (const item of sources.items) {
      const value = model(item.id);
      for (const part of value.parts) {
        expect(part.joint).toBe(item.slot === "head" ? "head" : "body");
        for (let i = 0; i < part.positions.length; i += 3) {
          if (item.slot === "head") expect(part.positions[i + 1], `${item.id}: above head`).toBeLessThanOrEqual(-8.299);
          if (item.slot === "pet") expect(part.positions[i], `${item.id}: beside head`).toBeLessThanOrEqual(-4.449);
          if (item.slot === "back") expect(part.positions[i + 2], `${item.id}: behind torso`).toBeGreaterThanOrEqual(2.399);
        }
        // The static adaptation does not misrepresent source animal rig clips as Votex emotes.
        expect(part.motion).toBeUndefined();
      }
      const preview = readFileSync(`minecraft/votex-core/src/main/resources/assets/votex_client/textures/previews/${item.id}.png`);
      expect(preview.subarray(0, 8)).toEqual(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]));
      expect([preview.readUInt32BE(16), preview.readUInt32BE(20)]).toEqual([384, 300]);
    }
  });
});
