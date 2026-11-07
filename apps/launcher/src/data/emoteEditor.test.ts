import { describe, expect, it } from "vitest";
import {
  buildEmoteKeyframes,
  prepareCustomEmoteSave,
  readEmoteEditorDraft,
  type CustomEmote,
} from "./emoteEditor";

const sample = (overrides: Partial<CustomEmote> = {}): CustomEmote => ({
  id: "custom-emote-01234567-89ab-cdef",
  name: "Aufstieg",
  duration: 3.2,
  keyframes: [
    {
      time: 0,
      pose: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
    },
    {
      time: 0.45,
      pose: [0.2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -2.1, 0, 0, 0, 0, 0, 0, 1.25],
    },
    {
      time: 1,
      pose: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
    },
  ],
  communityId: "",
  visibility: "private",
  communityShareUrl: "",
  published: false,
  authorName: "",
  communityOwned: true,
  ...overrides,
});

describe("custom emote editor", () => {
  it("round-trips keyframe times, rotations and vertical motion", () => {
    const original = sample();
    const draft = readEmoteEditorDraft(original);
    const rebuilt = buildEmoteKeyframes(draft);

    expect(draft.keyframeTimes).toEqual([0, 0.45, 1]);
    expect(draft.verticalOffsets).toEqual([0, 1.25, 0]);
    expect(rebuilt.map((frame) => frame.time)).toEqual(
      original.keyframes.map((frame) => frame.time),
    );
    rebuilt.forEach((frame, frameIndex) => {
      frame.pose.forEach((value, poseIndex) =>
        expect(value).toBeCloseTo(
          original.keyframes[frameIndex].pose[poseIndex],
          5,
        ),
      );
    });
  });

  it("updates a private local emote while retaining its identity", () => {
    const original = sample({ authorName: "Creator" });
    const saved = prepareCustomEmoteSave(
      { name: "Neue Geste", duration: 2.4, keyframes: original.keyframes },
      original,
      "custom-emote-new-id",
    );

    expect(saved.forked).toBe(false);
    expect(saved.item.id).toBe(original.id);
    expect(saved.item.name).toBe("Neue Geste");
    expect(saved.item.authorName).toBe("Creator");
    expect(saved.item.communityId).toBe("");
    expect(saved.item.visibility).toBe("private");
  });

  it("turns an edited imported emote into a manageable owned copy", () => {
    const imported = sample({
      authorName: "Original Creator",
      communityOwned: false,
    });
    const saved = prepareCustomEmoteSave(
      {
        name: "Meine Variante",
        duration: imported.duration,
        keyframes: imported.keyframes,
      },
      imported,
      "custom-emote-fedcba98-7654-3210",
    );

    expect(saved.item.id).toBe(imported.id);
    expect(saved.item.communityId).toBe("");
    expect(saved.item.authorName).toBe("");
    expect(saved.item.communityOwned).toBe(true);
  });

  it("forks a published emote to private without changing the original", () => {
    const original = sample({
      communityId: "community-original",
      communityShareUrl: "https://votex.example/share/original",
      published: true,
      visibility: "public",
    });
    const saved = prepareCustomEmoteSave(
      { name: "Private Kopie", duration: 4, keyframes: original.keyframes },
      original,
      "custom-emote-fedcba98-7654-3210",
    );

    expect(saved.forked).toBe(true);
    expect(saved.item.id).not.toBe(original.id);
    expect(saved.item.communityId).toBe("");
    expect(saved.item.visibility).toBe("private");
    expect(saved.item.published).toBe(false);
    expect(saved.item.communityShareUrl).toBe("");
    expect(saved.item.communityOwned).toBe(true);
    expect(original.communityId).toBe("community-original");
    expect(original.visibility).toBe("public");
  });

  it("rejects a published edit that reuses its original identity", () => {
    const original = sample({
      communityId: "community-original",
      published: true,
      visibility: "public",
    });
    expect(() =>
      prepareCustomEmoteSave(
        {
          name: "Überschrieben",
          duration: original.duration,
          keyframes: original.keyframes,
        },
        original,
        original.id,
      ),
    ).toThrow("neue ID");
  });
});
