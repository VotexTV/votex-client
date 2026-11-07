import {
  BufferGeometry,
  Float32BufferAttribute,
  Color,
  DoubleSide,
  Group,
  Matrix4,
  Mesh,
  MeshStandardMaterial,
  type Material,
  TextureLoader,
  SRGBColorSpace,
  NearestFilter,
  NearestMipmapNearestFilter,
  type Texture,
} from "three";
import { mergeGeometries } from "three/examples/jsm/utils/BufferGeometryUtils.js";
import type { PlayerObject } from "skinview3d";
import type { AppState } from "../../../../packages/shared-config/schema";
import type { CustomCosmetic } from "../data/customModels";
import { samplePartMotion, previewMotionAngle } from "../data/partMotion";
import companionProfiles from "../data/companion-profiles.json";
import { companionFit } from "../data/companionFit";
const companions: Record<string, {kind: string; liftScale: number; tiltScale: number}> = companionProfiles;
import {
  cosmeticCatalog,
  cosmeticModel,
  coveringHeadwear,
  type SurfacePart,
  type CosmeticModel,
} from "../data/cosmeticCatalog";
export type Look = AppState["cosmetics"][number];
export interface WearableHandle {
  dispose: () => void;
  frame: (t: number, moving: boolean) => void;
  opacity: (amount: number) => void;
}
const textureCache = new Map<string, Promise<Texture>>();
const readyTextures = new Map<string, Texture>();
const headMasks = new WeakMap<
  PlayerObject,
  { count: number; previous: boolean }
>();
function maskHead(player: PlayerObject) {
  const current = headMasks.get(player) ?? {
    count: 0,
    previous: player.skin.head.outerLayer.visible,
  };
  current.count++;
  headMasks.set(player, current);
  player.skin.head.outerLayer.visible = false;
  return () => {
    current.count--;
    if (current.count === 0) {
      player.skin.head.outerLayer.visible = current.previous;
      headMasks.delete(player);
    }
  };
}
async function prepareTextures(model: CosmeticModel) {
  await Promise.all(
    model.parts
      .filter((p) => p.texture)
      .map((p) => {
        const name = p.texture!;
        if (!/^[a-z0-9-]{1,70}\.png$/.test(name))
          throw Error("Invalid cosmetic texture");
        let promise = textureCache.get(name);
        if (!promise) {
          promise = new TextureLoader()
            .loadAsync(`/assets/catalog/textures/${name}`)
            .then((t) => {
              t.colorSpace = SRGBColorSpace;
              t.flipY = false;
              t.anisotropy = 2;
              if (name.endsWith("-wings-feather.png")) {
                t.magFilter = NearestFilter;
                t.minFilter = NearestMipmapNearestFilter;
              }
              readyTextures.set(name, t);
              return t;
            });
          textureCache.set(name, promise);
          promise.catch(() => textureCache.delete(name));
        }
        return promise;
      }),
  );
  return model;
}
export function surfaceGeometry(p: SurfacePart, bodyOffset = 0) {
  const g = new BufferGeometry(),
    pos = p.positions.slice(),
    normal = p.normals.slice(),
    colors = p.colors.slice();
  const pivot = p.motion?.pivot || [0, 0, 0];
  for (let i = 0; i < pos.length; i += 3) {
    pos[i] -= pivot[0];
    pos[i + 1] = -pos[i + 1] + pivot[1] + bodyOffset;
    pos[i + 2] = -pos[i + 2] + pivot[2];
    normal[i + 1] *= -1;
    normal[i + 2] *= -1;
  }
  const color = new Color();
  for (let i = 0; i < colors.length; i += 3) {
    color
      .setRGB(colors[i], colors[i + 1], colors[i + 2])
      .convertSRGBToLinear()
      .toArray(colors, i);
  }
  const indices: number[] = [];
  for (let i = 0; i < p.quads.length; i += 4) {
    const [a, b, c, d] = p.quads.slice(i, i + 4);
    indices.push(a, b, c, a, c, d);
  }
  g.setAttribute("position", new Float32BufferAttribute(pos, 3));
  g.setAttribute("normal", new Float32BufferAttribute(normal, 3));
  g.setAttribute("color", new Float32BufferAttribute(colors, 3));
  if (p.texture && p.uvs)
    g.setAttribute("uv", new Float32BufferAttribute(p.uvs, 2));
  g.setIndex(indices);
  return g;
}
/** Merge same-material surfaces by joint/hinge; curve complexity does not multiply draw calls. */
export function buildSurfaceModel(model: CosmeticModel, forPlayer = true, placement?: {scale:number;x:number;y:number}) {
  const groups = new Map<
    string,
    { part: SurfacePart; geometries: BufferGeometry[] }
  >();
  for (const p of model.parts) {
    const key = JSON.stringify([
      p.joint,
      p.material,
      p.motion || null,
      p.texture || null,
    ]);
    const b = groups.get(key) || { part: p, geometries: [] };
    b.geometries.push(
      surfaceGeometry(p, forPlayer && p.joint === "body" && !p.motion ? 6 : 0),
    );
    groups.set(key, b);
  }
  const roots: {
    group: Group;
    joint: SurfacePart["joint"];
    motion?: SurfacePart["motion"];
    secondary?: Group;
    companion?: Group;
    fit?: Group;
    companionProfile?: {kind: string; liftScale: number; tiltScale: number};
  }[] = [];
  const owned: BufferGeometry[] = [];
  const materials: Material[] = [];
  const originalOpacities: number[] = [];
  for (const { part: p, geometries } of groups.values()) {
    const geometry = mergeGeometries(geometries);
    geometries.forEach((g) => g.dispose());
    if (!geometry) continue;
    const material = new MeshStandardMaterial({
      ...p.material,
      map: p.texture ? (readyTextures.get(p.texture) ?? null) : null,
      vertexColors: true,
      side: DoubleSide,
      transparent: p.material.opacity < 1,
      depthWrite: p.material.opacity >= 1,
    });
    const group = new Group();
    let secondary: Group | undefined;
    if (p.motion) {
      const [x, y, z] = p.motion.pivot;
      group.position.set(x, -y + (forPlayer && p.joint === "body" ? 6 : 0), -z);
    }
    const mesh = new Mesh(geometry, material);
    if (p.motion?.type === "wing" && p.motion.secondary && p.motion.secondaryPivot) {
      const [sx, sy, sz] = p.motion.secondaryPivot,
        [px, py, pz] = p.motion.pivot;
      secondary = new Group();
      secondary.position.set(sx - px, -sy + py, -sz + pz);
      mesh.position.set(-secondary.position.x, -secondary.position.y, -secondary.position.z);
      secondary.add(mesh);
      group.add(secondary);
    } else group.add(mesh);
    owned.push(geometry);
    materials.push(material);
    originalOpacities.push(p.material.opacity);
    let companion: Group | undefined;
    if (forPlayer && model.slot === "pet" && Object.hasOwn(companions, model.id)) {
      companion = new Group();
      companion.position.set(-6, 6, 0);
      group.position.x += 6;
      group.position.y -= 6;
      companion.add(group);
    }
    let fit: Group | undefined;
    if (forPlayer && placement) {
      fit = new Group();fit.matrixAutoUpdate=false;
      fit.matrix.makeTranslation(placement.x-6,6-placement.y,0).multiply(new Matrix4().makeScale(placement.scale,placement.scale,placement.scale)).multiply(new Matrix4().makeTranslation(6,-6,0));
      fit.add(companion || group);
    }
    roots.push({ group, joint: p.joint, motion: p.motion, secondary, companion, fit, companionProfile: companions[model.id] });
  }
  return {
    roots,
    opacity(amount: number) {
      materials.forEach((m, i) => {
        const material = m as MeshStandardMaterial;
        const original = originalOpacities[i];
        const transparent = original < 1 || amount < 0.999;
        if (material.transparent !== transparent) {
          material.transparent = transparent;
          material.needsUpdate = true;
        }
        material.opacity = original * amount;
        material.depthWrite = !transparent;
      });
    },
    dispose() {
      roots.forEach((r) => (r.fit || r.companion || r.group).removeFromParent());
      owned.forEach((g) => g.dispose());
      materials.forEach((m) => m.dispose());
    },
  };
}
export function attachWearables(
  player: PlayerObject,
  look: Look,
  customModels: CustomCosmetic[] = [],
  onLoaded: () => void = () => {},
  onError: (e: unknown) => void = () => {},
): WearableHandle {
  let disposed = false;
  const assets: ReturnType<typeof buildSurfaceModel>[] = [];
  let opacity = 1;
  const ids = Object.entries(look.equipment)
    .filter(
      ([slot, id]) =>
        slot !== "cape" &&
        (cosmeticCatalog.some((i) => i.id === id && i.slot === slot) ||
          customModels.some((i) => i.id === id && i.slot === slot)),
    )
    .map(([slot, id]) => ({ id: id!, slot }));
  const releaseMask = ids.some(
    ({ slot, id }) => slot === "head" && coveringHeadwear.has(id),
  )
    ? maskHead(player)
    : () => {};
  void Promise.all(
    ids.map(async ({ id }) => {
      const custom = customModels.find((item) => item.id === id);
      const model = custom
        ? {
            ...custom.model,
            description: "Community-Modell",
            collection: "Community Atelier",
          }
        : await cosmeticModel(id);
      return prepareTextures(model);
    }),
  )
    .then((models) => {
      if (disposed) return;
      const headset = models.some(model => model.id === "creator-headphones" || model.id === "orbit-headphones");
      for (const model of models) {
        const placement = model.slot === "pet" && Object.hasOwn(companions, model.id) ? companionFit(model.parts, headset) : undefined;
        const asset = buildSurfaceModel(model, true, placement);
        asset.opacity(opacity);
        assets.push(asset);
        for (const r of asset.roots) {
          const scale = look.scale / 100;
          if (model.slot === "wings") {
            const attachment = new Group();
            attachment.position.z = -(1 - scale) * 2;
            attachment.scale.setScalar(scale);
            attachment.add(r.group);
            r.fit = attachment;
          } else r.group.scale.setScalar(scale);
          player.skin[r.joint].add(r.fit || r.companion || r.group);
        }
      }
      onLoaded();
    })
    .catch((e) => {
      if (!disposed) {
        onError(e);
        onLoaded();
      }
    });
  return {
    opacity(amount) {
      opacity = amount;
      assets.forEach((a) => a.opacity(amount));
    },
    dispose() {
      disposed = true;
      releaseMask();
      assets.forEach((a) => a.dispose());
    },
    frame(t, moving) {
      for (const a of assets)
        for (const r of a.roots) {
          if (r.companion) {
            const profile = r.companionProfile!;
            const cycle = ((t % 17) + 17) % 17;
            const hop = cycle > 12 && cycle < 13 ? Math.pow(Math.sin((cycle - 12) * Math.PI), 2) * 1.35 : 0;
            const shake = cycle > 5 && cycle < 6.2 ? Math.sin((cycle - 5) * Math.PI / 1.2) * Math.sin((cycle - 5) * 32) * .085 : 0;
            const organic = profile.kind === "organic";
            r.companion.position.x = -6 + (moving && organic && cycle > 12 && cycle < 13 ? -Math.pow(Math.sin((cycle - 12) * Math.PI), 2) * 2.5 : 0);
            r.companion.position.y = 6 + (moving ? (organic ? Math.sin(t * 1.8) * .08 + hop : Math.sin(t * (profile.kind === "mechanical" ? 1.1 : 1.7)) * .25) * profile.liftScale : 0);
            r.companion.rotation.z = moving ? -(organic ? Math.sin(t * 1.2) * .012 + shake : Math.sin(t * 1.2) * .035) * profile.tiltScale : 0;
          }
          if (r.motion) {
            if (r.motion.type === "wing") {
              const wave = t * (r.motion.speed ?? 1.35) + (r.motion.phase ?? 0);
              const flap = moving ? 0.018 + Math.sin(wave) * (r.motion.amplitude ?? .045) * .25 : 0;
              const axis = r.motion.axis || "z";
              const sweep = moving ? Math.sin(t * 2.3) * .27 : 0;
              r.group.rotation.order = "YXZ";
              r.group.rotation.y = previewMotionAngle("y", r.motion.side * sweep);
              r.group.rotation[axis] = previewMotionAngle(axis, r.motion.side * (flap + (axis === "y" ? sweep : 0)));
              if (r.secondary)
                r.secondary.rotation[r.motion.secondaryAxis ?? axis] =
                  previewMotionAngle(r.motion.secondaryAxis ?? axis, moving ? r.motion.side * (flap * .35 + Math.sin(t * 2.3 + (r.motion.phase ?? 0) - .75) * (r.motion.secondary || 0) * .21) : 0);
            } else {
              const angle = moving ? samplePartMotion(r.motion, t) : 0;
              r.group.rotation[r.motion.axis] = previewMotionAngle(r.motion.axis, angle);
            }
          }
        }
    },
  };
}
