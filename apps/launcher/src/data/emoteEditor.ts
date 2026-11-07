import type { AppState } from "../../../../packages/shared-config/schema";
import { insertionAt, interpolateAt } from "./timeline";

export type CustomEmote = AppState["customEmotes"][number];
export type EmoteJoint =
  "head" | "body" | "leftArm" | "rightArm" | "leftLeg" | "rightLeg";
export type EmoteAxis = "x" | "y" | "z";
export type DraftTrack = {
  joint: EmoteJoint;
  axis: EmoteAxis;
  values: number[];
};
export type EmoteEditorDraft = {
  name: string;
  duration: number;
  keyframeTimes: number[];
  tracks: DraftTrack[];
  verticalOffsets: number[];
};

const joints: EmoteJoint[] = [
  "head",
  "body",
  "leftArm",
  "rightArm",
  "leftLeg",
  "rightLeg",
];
const axes: EmoteAxis[] = ["x", "y", "z"];
export function insertEmoteFrame(
  draft: Pick<EmoteEditorDraft, "keyframeTimes" | "tracks" | "verticalOffsets">,
  time: number,
) {
  const at = insertionAt(draft.keyframeTimes, time);
  if (at.existing || draft.keyframeTimes.length >= 24)
    return { ...draft, index: at.index };
  const insert = (values: number[]) => [
    ...values.slice(0, at.index),
    interpolateAt(draft.keyframeTimes, values, at.time),
    ...values.slice(at.index),
  ];
  return {
    keyframeTimes: [
      ...draft.keyframeTimes.slice(0, at.index),
      at.time,
      ...draft.keyframeTimes.slice(at.index),
    ],
    tracks: draft.tracks.map((t) => ({ ...t, values: insert(t.values) })),
    verticalOffsets: insert(draft.verticalOffsets),
    index: at.index,
  };
}

/** Convert stored radians to editor degrees without dropping root movement. */
export function readEmoteEditorDraft(item: CustomEmote): EmoteEditorDraft {
  const tracks: DraftTrack[] = [];
  for (const [jointIndex, joint] of joints.entries()) {
    for (const [axisIndex, axis] of axes.entries()) {
      const values = item.keyframes.map(
        (frame) => (frame.pose[jointIndex * 3 + axisIndex] * 180) / Math.PI,
      );
      if (values.some((value) => Math.abs(value) > 0.01))
        tracks.push({ joint, axis, values });
    }
  }
  if (tracks.length === 0)
    tracks.push({
      joint: "rightArm",
      axis: "z",
      values: item.keyframes.map(() => 0),
    });
  return {
    name: item.name,
    duration: item.duration,
    keyframeTimes: item.keyframes.map((frame) => frame.time),
    tracks,
    verticalOffsets: item.keyframes.map((frame) => frame.pose[18]),
  };
}

export function buildEmoteKeyframes(
  draft: Pick<EmoteEditorDraft, "keyframeTimes" | "tracks" | "verticalOffsets">,
) {
  const jointIndex: Record<EmoteJoint, number> = {
    head: 0,
    body: 1,
    leftArm: 2,
    rightArm: 3,
    leftLeg: 4,
    rightLeg: 5,
  };
  const axisIndex: Record<EmoteAxis, number> = { x: 0, y: 1, z: 2 };
  return draft.keyframeTimes.map((time, frameIndex) => {
    const pose = Array<number>(19).fill(0);
    for (const track of draft.tracks) {
      pose[jointIndex[track.joint] * 3 + axisIndex[track.axis]] =
        (track.values[frameIndex] * Math.PI) / 180;
    }
    pose[18] = draft.verticalOffsets[frameIndex] ?? 0;
    return { time, pose };
  });
}

export function prepareCustomEmoteSave(
  draft: Pick<CustomEmote, "name" | "duration" | "keyframes">,
  existing: CustomEmote | undefined,
  newId: string,
): { item: CustomEmote; forked: boolean } {
  const forked = Boolean(existing?.communityId);
  if (forked && newId === existing!.id)
    throw new Error(
      "Eine veröffentlichte Geste braucht für die Kopie eine neue ID.",
    );
  const id = existing && !forked ? existing.id : newId;
  return {
    forked,
    item: {
      ...(existing && !forked ? existing : {}),
      ...draft,
      id,
      communityId: "",
      visibility: "private",
      communityShareUrl: "",
      published: false,
      authorName:
        forked || existing?.communityOwned === false
          ? ""
          : (existing?.authorName ?? ""),
      communityOwned: true,
    },
  };
}
