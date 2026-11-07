import type { AppState } from "../../../../packages/shared-config/schema";
import { customPrimitiveSchema } from "../../../../packages/shared-config/schema";
import type { SurfacePart } from "./cosmeticCatalog";
import { uid } from "../services/desktop";

export type CustomPrimitive =
  AppState["customCosmetics"][number]["primitives"][number];
export type CustomCosmetic = AppState["customCosmetics"][number];

type Vec3 = [number, number, number];
type Face = { points: Vec3[]; normal: Vec3; paintIndex?: number };

const cubeFaces: Face[] = [
  {
    normal: [1, 0, 0],
    points: [
      [0.5, -0.5, -0.5],
      [0.5, 0.5, -0.5],
      [0.5, 0.5, 0.5],
      [0.5, -0.5, 0.5],
    ],
  },
  {
    normal: [-1, 0, 0],
    points: [
      [-0.5, -0.5, 0.5],
      [-0.5, 0.5, 0.5],
      [-0.5, 0.5, -0.5],
      [-0.5, -0.5, -0.5],
    ],
  },
  {
    normal: [0, 1, 0],
    points: [
      [-0.5, 0.5, -0.5],
      [-0.5, 0.5, 0.5],
      [0.5, 0.5, 0.5],
      [0.5, 0.5, -0.5],
    ],
  },
  {
    normal: [0, -1, 0],
    points: [
      [-0.5, -0.5, 0.5],
      [-0.5, -0.5, -0.5],
      [0.5, -0.5, -0.5],
      [0.5, -0.5, 0.5],
    ],
  },
  {
    normal: [0, 0, 1],
    points: [
      [-0.5, -0.5, 0.5],
      [0.5, -0.5, 0.5],
      [0.5, 0.5, 0.5],
      [-0.5, 0.5, 0.5],
    ],
  },
  {
    normal: [0, 0, -1],
    points: [
      [0.5, -0.5, -0.5],
      [-0.5, -0.5, -0.5],
      [-0.5, 0.5, -0.5],
      [0.5, 0.5, -0.5],
    ],
  },
];

function shapeFaces(primitive: CustomPrimitive): Face[] {
  if (primitive.type === "cube" || primitive.type === "pixel") return cubeFaces;
  if (primitive.type === "plane")
    return [
      {
        normal: [0, 0, 1],
        points: [
          [-0.5, -0.5, 0],
          [0.5, -0.5, 0],
          [0.5, 0.5, 0],
          [-0.5, 0.5, 0],
        ],
      },
    ];
  const faces: Face[] = [];
  const rings = 4,
    segments = 8;
  for (let r = 0; r < rings; r++) {
    const v0 = -Math.PI / 2 + (Math.PI * r) / rings;
    const v1 = -Math.PI / 2 + (Math.PI * (r + 1)) / rings;
    for (let s = 0; s < segments; s++) {
      const u0 = (Math.PI * 2 * s) / segments;
      const u1 = (Math.PI * 2 * (s + 1)) / segments;
      const point = (u: number, v: number): Vec3 => [
        Math.cos(v) * Math.cos(u),
        Math.sin(v),
        Math.cos(v) * Math.sin(u),
      ];
      const points = [
        point(u0, v0),
        point(u1, v0),
        point(u1, v1),
        point(u0, v1),
      ];
      const normal = point((u0 + u1) / 2, (v0 + v1) / 2);
      faces.push({ points, normal });
    }
  }
  return faces;
}

function rotate(point: Vec3, rotation: Vec3): Vec3 {
  let [x, y, z] = point;
  const [rx, ry, rz] = rotation.map((v) => (v * Math.PI) / 180);
  let c = Math.cos(rx),
    s = Math.sin(rx);
  [y, z] = [y * c - z * s, y * s + z * c];
  c = Math.cos(ry);
  s = Math.sin(ry);
  [x, z] = [x * c + z * s, -x * s + z * c];
  c = Math.cos(rz);
  s = Math.sin(rz);
  [x, y] = [x * c - y * s, x * s + y * c];
  return [x, y, z];
}

function colorTriplet(color: string): number[] {
  const value = color.slice(1);
  return [0, 2, 4].map((i) => Number.parseInt(value.slice(i, i + 2), 16) / 255);
}

export function primitiveSurface(primitive: CustomPrimitive): SurfacePart {
  const baseFaces = shapeFaces(primitive);
  const faces =
    primitive.paint && primitive.type === "sphere"
      ? baseFaces.map((face, i) => ({ ...face, paintIndex: i * 2 }))
      : primitive.paint
        ? baseFaces.flatMap((face) => {
            const point = (u: number, v: number): Vec3 =>
              [0, 1, 2].map(
                (axis) =>
                  face.points[0][axis] * (1 - u) * (1 - v) +
                  face.points[1][axis] * u * (1 - v) +
                  face.points[2][axis] * u * v +
                  face.points[3][axis] * (1 - u) * v,
              ) as Vec3;
            return Array.from({ length: 64 }, (_, i) => {
              const x = i % 8,
                y = Math.floor(i / 8);
              return {
                normal: face.normal,
                paintIndex: i,
                points: [
                  point(x / 8, y / 8),
                  point((x + 1) / 8, y / 8),
                  point((x + 1) / 8, (y + 1) / 8),
                  point(x / 8, (y + 1) / 8),
                ],
              };
            });
          })
        : baseFaces;
  const positions: number[] = [],
    normals: number[] = [],
    colors: number[] = [],
    quads: number[] = [];
  const rgb = colorTriplet(primitive.color);
  const surfaceMaterial = {
    matte: { metalness: 0.02, roughness: 0.96 },
    polished: { metalness: 0.18, roughness: 0.24 },
    metal: { metalness: 0.82, roughness: 0.2 },
  }[primitive.material ?? "matte"];
  for (const face of faces) {
    const start = positions.length / 3;
    for (const point of face.points) {
      const local = rotate(
        [
          point[0] * primitive.size[0],
          point[1] * primitive.size[1],
          point[2] * primitive.size[2],
        ],
        primitive.rotation,
      );
      positions.push(
        local[0] + primitive.position[0],
        local[1] + primitive.position[1],
        local[2] + primitive.position[2],
      );
      const normal = rotate(face.normal, primitive.rotation);
      normals.push(...normal);
      if (face.paintIndex !== undefined && primitive.paint)
        colors.push(...colorTriplet(primitive.paint[face.paintIndex]));
      else if (primitive.gradient) {
        const axis = { x: 0, y: 1, z: 2 }[primitive.gradient.axis];
        const t = Math.max(
          0,
          Math.min(
            1,
            (point[axis] + (primitive.type === "sphere" ? 1 : 0.5)) /
              (primitive.type === "sphere" ? 2 : 1),
          ),
        );
        const end = colorTriplet(primitive.gradient.color);
        colors.push(...rgb.map((v, i) => v + (end[i] - v) * t));
      } else colors.push(...rgb);
    }
    quads.push(start, start + 1, start + 2, start + 3);
  }
  const animation = primitive.animated ? primitive.animation : undefined;
  return {
    joint: primitive.joint,
    positions,
    normals,
    colors,
    quads,
    material: { color: "#ffffff", ...surfaceMaterial, opacity: 1 },
    ...(animation
      ? {
          motion: {
            type: "oscillate" as const,
            ...animation,
            pivot: primitive.position,
          },
        }
      : {}),
  };
}

export function buildCustomCosmetic(
  id: string,
  name: string,
  slot: CustomCosmetic["slot"],
  primitives: CustomPrimitive[],
): CustomCosmetic {
  const model: CustomCosmetic["model"] = {
    id,
    name,
    slot,
    schemaVersion: 2,
    parts: primitives.map(primitiveSurface),
  };
  return {
    id,
    name,
    slot,
    primitives,
    model,
    communityId: "",
    published: false,
    authorName: "",
    communityOwned: true,
  };
}

/** Save a local draft in place, or fork an already-published contribution without changing its remote copy. */
export function prepareCustomCosmeticSave(
  draft: CustomCosmetic,
  existing?: CustomCosmetic,
): { item: CustomCosmetic; forked: boolean } {
  const forked = !!existing?.communityId;
  const id = forked ? `custom-${uid()}` : draft.id;
  return {
    forked,
    item: {
      ...draft,
      id,
      model: { ...draft.model, id },
      communityId: "",
      visibility: "private",
      communityShareUrl: "",
      published: false,
      authorName: existing?.authorName ?? draft.authorName,
      communityOwned: existing?.communityOwned ?? draft.communityOwned,
    },
  };
}

export type CustomCosmeticDraft = Pick<
  CustomCosmetic,
  "name" | "slot" | "primitives"
>;

/** Parse the versioned, portable Votex model format and rebuild all derived mesh data locally. */
export function parseCustomCosmeticFile(value: unknown): CustomCosmeticDraft {
  if (!value || typeof value !== "object" || Array.isArray(value))
    throw new Error("Die Datei enthält kein Votex-Kosmetikmodell.");
  const raw = value as Record<string, unknown>;
  if (raw.format !== "votex-cosmetic" || raw.schemaVersion !== 1)
    throw new Error("Dieses Dateiformat wird nicht unterstützt.");
  if (
    typeof raw.name !== "string" ||
    !raw.name.trim() ||
    raw.name.trim().length > 60
  )
    throw new Error("Der Modellname muss zwischen 1 und 60 Zeichen lang sein.");
  const allowedSlots = [
    "wings",
    "head",
    "face",
    "pet",
    "back",
    "aura",
    "overlay",
    "feet",
  ] as const;
  if (
    typeof raw.slot !== "string" ||
    !allowedSlots.includes(raw.slot as (typeof allowedSlots)[number])
  )
    throw new Error("Die Modellkategorie ist ungültig.");
  if (
    !Array.isArray(raw.primitives) ||
    raw.primitives.length < 1 ||
    raw.primitives.length > 24
  )
    throw new Error("Ein Modell braucht 1 bis 24 geprüfte Teile.");
  const primitives = raw.primitives.map((rawPart) => {
    if (!rawPart || typeof rawPart !== "object" || Array.isArray(rawPart))
      throw new Error("Ein Modellteil ist ungültig.");
    const parsed = customPrimitiveSchema.safeParse({
      ...rawPart,
      id: `part-${uid()}`,
    });
    if (!parsed.success || (parsed.data.animated && !parsed.data.animation))
      throw new Error(
        "Ein Modellteil enthält ungültige Geometrie oder Animation.",
      );
    return parsed.data as CustomPrimitive;
  });
  return {
    name: raw.name.trim(),
    slot: raw.slot as CustomCosmetic["slot"],
    primitives,
  };
}
