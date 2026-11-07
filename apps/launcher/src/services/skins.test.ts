import { describe, it, expect } from "vitest";
import { initialState } from "../../../../packages/shared-config/schema";
import {
  addSkin,
  checkSkinFile,
  duplicateSkin,
  removeSkin,
  skinPngBlob,
  type LocalSkin,
} from "./skins";
import { StateDraft } from "../stores/StateDraft";
const skin: LocalSkin = {
  id: "test-skin",
  name: "Mein Skin",
  data: "data:image/png;base64,AAAA",
  model: "slim",
};

describe("skin library state and import boundaries", () => {
  it("exports local image bytes through a bounded PNG blob without rewriting pixels",async()=>{
    const bytes=new Uint8Array([137,80,78,71,13,10,26,10,0,1,2,3]);
    const data=`data:image/png;base64,${btoa(String.fromCharCode(...bytes))}`;
    const blob=skinPngBlob(data);
    expect(blob.type).toBe("image/png");expect(new Uint8Array(await blob.arrayBuffer())).toEqual(bytes);
    expect(()=>skinPngBlob("data:text/html;base64,AAAA")).toThrow();
  });
  it("accepts supported PNG header dimensions and rejects other formats before image decoding", async () => {
    const bytes = new Uint8Array(33);
    bytes.set([137, 80, 78, 71, 13, 10, 26, 10]);
    bytes.set([73, 72, 68, 82], 12);
    new DataView(bytes.buffer).setUint32(16, 64);
    new DataView(bytes.buffer).setUint32(20, 64);
    await expect(checkSkinFile(new Blob([bytes]))).resolves.toBeUndefined();
    const bad = bytes.slice();
    new DataView(bad.buffer).setUint32(16, 4096);
    await expect(checkSkinFile(new Blob([bad]))).rejects.toThrow(
      "64 × 64 oder 64 × 32",
    );
    await expect(checkSkinFile(new Blob(["<svg/>"]))).rejects.toThrow("PNG");
    await expect(checkSkinFile({ size: 1_000_001 } as Blob)).rejects.toThrow(
      "1 MB",
    );
  });
  it("duplicates the exact PNG and arm model without changing the active skin or source", () => {
    const original = addSkin(structuredClone(initialState), skin);
    const next = duplicateSkin(original, skin.id, "test-copy");
    expect(next.selectedSkin).toBe(skin.id);
    expect(next.skins[1]).toEqual({
      ...skin,
      id: "test-copy",
      name: "Mein Skin (Kopie)",
    });
    expect(original.skins).toEqual([skin]);
    expect(() => duplicateSkin(original, skin.id, skin.id)).toThrow("bereits");
  });
  it("clears only references to a removed skin and preserves other looks and profiles", () => {
    const original = addSkin(
      addSkin(structuredClone(initialState), skin),
      { ...skin, id: "other" },
      false,
    );
    original.cosmetics[0].skin = skin.id;
    original.cosmetics.push({
      ...original.cosmetics[0],
      id: "second-look",
      skin: "other",
    });
    const next = removeSkin(original, skin.id);
    expect(next.selectedSkin).toBe("");
    expect(next.skins.map((s) => s.id)).toEqual(["other"]);
    expect(next.cosmetics.map((s) => s.skin)).toEqual(["", "other"]);
    expect(next.profiles).toEqual(original.profiles);
    expect(original.cosmetics[0].skin).toBe(skin.id);
  });
  it("rejects capacity overflow without discarding any existing skin", () => {
    const original = structuredClone(initialState);
    original.skins = Array.from({ length: 100 }, (_, i) => ({
      ...skin,
      id: `skin-${i}`,
    }));
    expect(() => addSkin(original, skin)).toThrow("100 Skins");
    expect(original.skins).toHaveLength(100);
  });
  it("applies multiple pending edits to the latest draft and retains the last valid state on failure", () => {
    const draft = new StateDraft(addSkin(structuredClone(initialState), skin));
    draft.apply((s) => ({
      ...s,
      skins: s.skins.map((v) => ({ ...v, name: "Neu" })),
    }));
    draft.apply((s) => ({
      ...s,
      skins: s.skins.map((v) => ({ ...v, model: "default" })),
    }));
    expect(draft.current.skins[0]).toMatchObject({
      name: "Neu",
      model: "default",
    });
    const valid = draft.current;
    expect(() =>
      draft.apply((s) => ({
        ...s,
        skins: [...s.skins, { ...skin, id: "invalid", name: "" }],
      })),
    ).toThrow();
    expect(draft.current).toBe(valid);
    expect(draft.current.skins).toHaveLength(1);
  });
});
