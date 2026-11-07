/** Deterministic offline conversion of licensed FBX/GLB sources. No downloaded code is executed. */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  Matrix4,
  Matrix3,
  Vector3,
  Quaternion,
  Color,
  Texture,
  TextureLoader,
} from "three";
import { FBXLoader } from "three/examples/jsm/loaders/FBXLoader.js";
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const source = path.join(root, "assets-source/third-party");
const web = path.join(root, "apps/launcher/public/assets/catalog");
const core = path.join(
  root,
  "minecraft/votex-core/src/main/resources/assets/votex_client",
);
const read = (p) => fs.readFileSync(p);
const json = (p) => JSON.parse(read(p));
const round = (n) => Math.round(n * 100000) / 100000;
const cc0 = "https://creativecommons.org/publicdomain/zero/1.0/";
const by = "https://creativecommons.org/licenses/by/3.0/";
function texture(bytes, id) {
  if (
    !bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))
  )
    throw Error("Only PNG source textures supported");
  if (bytes.readUInt32BE(16) > 2048 || bytes.readUInt32BE(20) > 2048)
    throw Error("Texture budget exceeded");
  const name = `${id}.png`;
  for (const dir of [
    path.join(web, "textures"),
    path.join(core, "textures/cosmetics"),
  ]) {
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, name), bytes);
  }
  return name;
}
function glb(file, id) {
  const b = read(path.join(source, file));
  if (b.toString("ascii", 0, 4) !== "glTF" || b.readUInt32LE(4) !== 2)
    throw Error("Expected GLB 2");
  const end = 20 + b.readUInt32LE(12),
    j = JSON.parse(b.toString("utf8", 20, end)),
    bin = b.subarray(end + 8);
  if (
    j.extensionsRequired?.some(
      (x) => !["KHR_materials_unlit", "KHR_texture_transform"].includes(x),
    )
  )
    throw Error("Unsupported GLB extension");
  function accessor(i) {
    const a = j.accessors[i],
      v = j.bufferViews[a.bufferView];
    if (a.sparse || v.buffer !== 0)
      throw Error("Unsupported sparse/external accessor");
    const sizes = { SCALAR: 1, VEC2: 2, VEC3: 3, VEC4: 4, MAT4: 16 },
      types = {
        5126: [4, "readFloatLE"],
        5125: [4, "readUInt32LE"],
        5123: [2, "readUInt16LE"],
        5121: [1, "readUInt8"],
      };
    const [bytes, method] = types[a.componentType] || [];
    if (!bytes) throw Error("Unsupported accessor type");
    const count = sizes[a.type],
      stride = v.byteStride || bytes * count,
      start = (v.byteOffset || 0) + (a.byteOffset || 0);
    return Array.from({ length: a.count }, (_, n) =>
      Array.from({ length: count }, (_, k) =>
        bin[method](start + n * stride + k * bytes),
      ),
    );
  }
  const worlds = [],
    parents = new Map();
  j.nodes?.forEach((n, i) => n.children?.forEach((c) => parents.set(c, i)));
  function world(i) {
    if (worlds[i]) return worlds[i];
    const n = j.nodes[i],
      m = n.matrix
        ? new Matrix4().fromArray(n.matrix)
        : new Matrix4().compose(
            new Vector3().fromArray(n.translation || [0, 0, 0]),
            new Quaternion().fromArray(n.rotation || [0, 0, 0, 1]),
            new Vector3().fromArray(n.scale || [1, 1, 1]),
          );
    worlds[i] = parents.has(i) ? world(parents.get(i)).clone().multiply(m) : m;
    return worlds[i];
  }
  const textures = new Map();
  function image(i) {
    if (textures.has(i)) return textures.get(i);
    const im = j.images[i];
    let bytes;
    if (im.bufferView !== undefined) {
      const v = j.bufferViews[im.bufferView];
      bytes = bin.subarray(
        v.byteOffset || 0,
        (v.byteOffset || 0) + v.byteLength,
      );
    } else {
      const base = path.dirname(path.join(source, file)),
        p = path.resolve(base, im.uri);
      if (!p.startsWith(base + path.sep))
        throw Error("External texture escapes source directory");
      bytes = read(p);
    }
    const name = texture(bytes, `${id}-${i}`);
    textures.set(i, name);
    return name;
  }
  const parts = [];
  j.nodes.forEach((node, ni) => {
    if (node.mesh === undefined) return;
    const mat = world(ni),
      nm = new Matrix3().getNormalMatrix(mat);
    for (const primitive of j.meshes[node.mesh].primitives) {
      if (primitive.mode !== undefined && primitive.mode !== 4)
        throw Error("Only triangle primitives supported");
      const p = accessor(primitive.attributes.POSITION).map((v) =>
        new Vector3().fromArray(v).applyMatrix4(mat).toArray(),
      );
      const normals =
        primitive.attributes.NORMAL !== undefined
          ? accessor(primitive.attributes.NORMAL).map((v) =>
              new Vector3().fromArray(v).applyNormalMatrix(nm).toArray(),
            )
          : null;
      const triangles =
        primitive.indices !== undefined
          ? accessor(primitive.indices).flat()
          : p.map((_, i) => i);
      const material = j.materials?.[primitive.material],
        pbr = material?.pbrMetallicRoughness || {},
        linear = pbr.baseColorFactor || [1, 1, 1, 1],
        color = new Color().fromArray(linear).convertLinearToSRGB().toArray();
      const part = {
        p,
        normals,
        triangles,
        color,
        roughness: pbr.roughnessFactor ?? 0.8,
      };
      if (pbr.baseColorTexture) {
        const tex = pbr.baseColorTexture;
        if (tex.texCoord && tex.texCoord !== 0)
          throw Error("Unsupported UV channel");
        part.texture = image(j.textures[tex.index].source);
        part.uvs = accessor(primitive.attributes.TEXCOORD_0);
      }
      parts.push(part);
    }
  });
  return parts;
}
function fbx() {
  // FBXLoader is used only for geometry conversion. Textures are read explicitly from the licensed archive.
  const original = TextureLoader.prototype.load;
  TextureLoader.prototype.load = function () {
    return new Texture();
  };
  const bytes = read(path.join(source, "angel-wing/angel_wing_low_poly.fbx"));
  let object;
  try {
    object = new FBXLoader().parse(
      bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength),
      "",
    );
  } finally {
    TextureLoader.prototype.load = original;
  }
  object.updateMatrixWorld(true);
  const parts = [];
  object.traverse((o) => {
    if (!o.isMesh) return;
    const g = o.geometry,
      normalMatrix = new Matrix3().getNormalMatrix(o.matrixWorld);
    parts.push({
      p: Array.from({ length: g.attributes.position.count }, (_, i) =>
        new Vector3()
          .fromBufferAttribute(g.attributes.position, i)
          .applyMatrix4(o.matrixWorld)
          .toArray(),
      ),
      normals: Array.from({ length: g.attributes.normal.count }, (_, i) =>
        new Vector3()
          .fromBufferAttribute(g.attributes.normal, i)
          .applyNormalMatrix(normalMatrix)
          .toArray(),
      ),
      triangles: g.index
        ? Array.from(g.index.array)
        : Array.from({ length: g.attributes.position.count }, (_, i) => i),
      uvs: Array.from({ length: g.attributes.uv.count }, (_, i) => [
        g.attributes.uv.getX(i),
        1 - g.attributes.uv.getY(i),
      ]),
      texture: texture(
        read(path.join(source, "angel-wing/textures/wing_lp_BaseMap.png")),
        "devmops-angel-wing",
      ),
      color: [1, 1, 1],
      roughness: 0.88,
    });
  });
  return parts;
}
function bounds(parts) {
  const min = [Infinity, Infinity, Infinity],
    max = [-Infinity, -Infinity, -Infinity];
  for (const p of parts)
    for (const v of p.p)
      for (let i = 0; i < 3; i++) {
        min[i] = Math.min(min[i], v[i]);
        max[i] = Math.max(max[i], v[i]);
      }
  return {
    min,
    max,
    size: max.map((v, i) => v - min[i]),
    center: min.map((v, i) => (v + max[i]) / 2),
  };
}
function surface(raw, transform, joint, motion) {
  const p = raw.p.map(transform),
    normals = Array.from({ length: p.length }, () => [0, 0, 0]);
  // Recompute area-weighted normals after the fitted transform, including reflection for the opposite wing.
  const zero = new Vector3().fromArray(transform([0, 0, 0]));
  const basis = [
    [1, 0, 0],
    [0, 1, 0],
    [0, 0, 1],
  ].map((v) => new Vector3().fromArray(transform(v)).sub(zero));
  const mirrored = basis[0].dot(basis[1].clone().cross(basis[2])) < 0;
  const indices = raw.triangles.slice();
  if (mirrored)
    for (let i = 0; i < indices.length; i += 3)
      [indices[i + 1], indices[i + 2]] = [indices[i + 2], indices[i + 1]];
  for (let i = 0; i < indices.length; i += 3) {
    const [a, b, c] = indices.slice(i, i + 3),
      n = new Vector3()
        .fromArray(p[b])
        .sub(new Vector3().fromArray(p[a]))
        .cross(new Vector3().fromArray(p[c]).sub(new Vector3().fromArray(p[a])))
        .normalize();
    for (const k of [a, b, c])
      for (let d = 0; d < 3; d++) normals[k][d] += n.getComponent(d);
  }
  const part = {
    joint,
    positions: p.flat().map(round),
    normals: normals.flatMap((n) =>
      (Math.hypot(...n) > 1e-8
        ? new Vector3().fromArray(n).normalize().toArray()
        : [0, 0, 1]
      ).map(round),
    ),
    colors: p.flatMap(() => raw.color.map(round)),
    quads: [],
    material: {
      color: "#ffffff",
      metalness: 0,
      roughness: raw.roughness,
      opacity: 1,
    },
  };
  for (let i = 0; i < indices.length; i += 3)
    part.quads.push(indices[i], indices[i + 1], indices[i + 2], indices[i + 2]);
  if (raw.texture) {
    part.texture = raw.texture;
    part.uvs = raw.uvs.flat().map(round);
  }
  if (motion) part.motion = motion;
  if (
    part.positions.length > 60000 ||
    part.quads.length > 24000 ||
    p.some((v) => v.some((n) => !Number.isFinite(n) || Math.abs(n) > 100))
  )
    throw Error("Model exceeds Core mesh bounds");
  return part;
}
const entries = [];
function add(id, name, slot, raw, fit, creator, description) {
  const b = bounds(raw),
    parts = fit(raw, b);
  const model = {
    id,
    name,
    slot,
    description,
    collection: "Creator Collection",
    creator,
    schemaVersion: 2,
    parts,
  };
  for (const dir of [web, path.join(core, "catalog")])
    fs.writeFileSync(path.join(dir, id + ".json"), JSON.stringify(model));
  entries.push(model);
  console.log(
    id,
    parts.reduce((n, p) => n + p.quads.length / 4, 0),
    "triangles",
    JSON.stringify(b),
  );
}
const creator = (name, url, license) => ({
  name,
  url,
  license,
  licenseUrl: license === "CC0" ? cc0 : by,
  changes:
    "Für Votex skaliert, ausgerichtet und an Spieler-Gelenke gebunden; Geometrieformat konvertiert.",
});
add(
  "cloud-feathers",
  "Seraph Wings",
  "wings",
  fbx(),
  (raw, b) =>
    [-1, 1].flatMap((side) =>
      raw.map((p) =>
        surface(
          p,
          (v) => [
            side * (2 + (v[0] - b.min[0]) * 0.255),
            -7 + (b.max[1] - v[1]) * 0.255,
            3 + (v[2] - b.center[2]) * 0.255,
          ],
          "body",
          { type: "wing", side, pivot: [side * 2, 1, 3] },
        ),
      ),
    ),
  creator(
    "DevMops",
    "https://opengameart.org/content/low-poly-angel-wing",
    "CC0",
  ),
  "Texturierte Engelsflügel von DevMops: einzeln geformte Federn und eine natürliche, geschwungene Silhouette.",
);
add(
  "creator-swan-wings",
  "Swan Wings",
  "wings",
  glb("michael-fuchs-wings.glb", "fuchs-wings"),
  (raw, b) => {
    const s = 48 / b.size[0];
    return raw.map((p) =>
      surface(
        p,
        (v) => [
          (v[0] - b.center[0]) * s,
          -6 - (v[1] - b.center[1]) * s,
          4 - (v[2] - b.center[2]) * s,
        ],
        "body",
      ),
    );
  },
  creator("Michael Fuchs", "https://poly.pizza/m/dw-IMS0xk71", "CC BY 3.0"),
  "Ein eigenständiges, gefächertes Flügelmodell von Michael Fuchs mit ausgearbeiteten Federlagen.",
);
add(
  "creator-bat-wings",
  "Nocturne Wings",
  "wings",
  glb("bat.glb", "google-bat"),
  (raw, b) => {
    const s = 48 / b.size[0];
    return raw.map((p) =>
      surface(
        p,
        (v) => [
          (v[0] - b.center[0]) * s,
          1 - (v[1] - b.center[1]) * s,
          4 - (v[2] - b.center[2]) * s,
        ],
        "body",
      ),
    );
  },
  creator("Poly by Google", "https://poly.pizza/m/ci_ZOYQ5fRM", "CC BY 3.0"),
  "Eine dunkle Fledermaus-Silhouette mit klarer Membranform; ursprüngliches Modell von Poly by Google.",
);
for (const [id, name, width, base, url] of [
  ["fox-hat", "Fox Beanie", 11.4, -1.5, "https://poly.pizza/m/vIhmiBqBtr"],
  ["frog-hat", "Frog Bucket Hat", 12, -6, "https://poly.pizza/m/IBL2UX2eOZ"],
  [
    "headphones",
    "Studio Headphones",
    11.6,
    0.9,
    "https://poly.pizza/m/EwlPidEswV",
  ],
]) {
  const raw = glb(id + ".glb", id);
  if (id === "headphones" || id === "frog-hat")
    for (const p of raw) for (const v of p.p) [v[0], v[2]] = [v[2], -v[0]];
  add(
    "creator-" + id,
    name,
    "head",
    raw,
    (raw, b) => {
      const s = width / b.size[0],
        sy =
          id === "headphones"
            ? 9.6 / b.size[1]
            : id === "fox-hat"
              ? 10 / b.size[1]
              : s;
      return raw.map((p) =>
        surface(
          p,
          (v) => [
            (v[0] - b.center[0]) * s,
            base - (v[1] - b.min[1]) * sy,
            -(v[2] - b.center[2]) * s,
          ],
          "head",
        ),
      );
    },
    creator("J-Toastie", url, "CC BY 3.0"),
    `Ein eigenständiges Modell von J-Toastie. Für den Minecraft-Kopf angepasst, ohne Votex-Neumodellierung.`,
  );
}
const fox = glb("fox.glb", "quaternius-fox"),
  foxTurn = (55 * Math.PI) / 180;
for (const p of fox)
  for (const v of p.p)
    [v[0], v[2]] = [
      v[0] * Math.cos(foxTurn) + v[2] * Math.sin(foxTurn),
      -v[0] * Math.sin(foxTurn) + v[2] * Math.cos(foxTurn),
    ];
add(
  "creator-fox",
  "Woodland Fox",
  "pet",
  fox,
  (raw, b) => {
    const s = 10 / Math.max(...b.size);
    return raw.map((p) =>
      surface(
        p,
        (v) => [
          -7 + (v[0] - b.center[0]) * s,
          -1 - (v[1] - b.min[1]) * s,
          -(v[2] - b.center[2]) * s,
        ],
        "body",
      ),
    );
  },
  creator("Quaternius", "https://poly.pizza/m/Bc97C66HKi", "CC0"),
  "Ein kleiner Fuchs von Quaternius als geformter Schulterbegleiter. Ruhige Pose; die Original-Animationen werden nicht als Launcher-Gesten ausgegeben.",
);
for (const [file, id, name] of [
  ["aid-glasses", "creator-glasses", "Round Glasses"],
  ["aid-sunglasses", "creator-sunglasses", "Midnight Shades"],
])
  add(
    id,
    name,
    "face",
    glb(`kenney-mini/${file}.glb`, id),
    (raw, b) => {
      const s = 9.4 / b.size[0];
      return raw.map((p) =>
        surface(
          p,
          (v) => [
            (v[0] - b.center[0]) * s,
            -4.2 - (v[1] - b.center[1]) * s,
            -4.3 + (b.max[2] - v[2]) * s,
          ],
          "head",
        ),
      );
    },
    creator("Kenney", "https://kenney.nl/assets/mini-characters", "CC0"),
    "Aus Kenneys Mini Characters: echte Brillengeometrie und Original-Farbtextur, an den Votex-Spieler angepasst.",
  );
const manifest = [
  ...entries.map(({ parts, ...m }) => m),
  ...json(path.join(web, "index.json")).filter(
    (m) => !entries.some((x) => x.id === m.id),
  ),
];
for (const p of [
  path.join(web, "index.json"),
  path.join(core, "catalog/index.json"),
  path.join(root, "apps/launcher/src/data/cosmetic-manifest.json"),
])
  fs.writeFileSync(p, JSON.stringify(manifest, null, 2) + "\n");
fs.writeFileSync(
  path.join(source, "converted-models.json"),
  JSON.stringify(
    entries.map(({ parts, ...m }) => m),
    null,
    2,
  ) + "\n",
);
