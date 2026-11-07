import { useEffect, useRef, useState } from "react";
import { SkinViewer } from "skinview3d";
import {
  BoxHelper,
  DoubleSide,
  GridHelper,
  Group,
  Mesh,
  MeshStandardMaterial,
  Raycaster,
  Vector2,
  type Object3D,
} from "three";
import { TransformControls } from "three/examples/jsm/controls/TransformControls.js";
import { Move3D, Rotate3D, Scaling, Grid3X3, UserRound } from "lucide-react";
import type { CustomPrimitive } from "../data/customModels";
import { primitiveSurface } from "../data/customModels";
import { surfaceGeometry } from "./Wearables";
import { jointNames } from "./emotes";
import { releaseSkinViewer } from "./viewerResources";
import { samplePartMotion } from "../data/partMotion";
import { applyRigPose } from "./rigPose";

type Joint = (typeof jointNames)[number];
type Mode = "translate" | "rotate" | "scale";
type Props = {
  skin?: string;
  model?: "default" | "slim";
  parts?: CustomPrimitive[];
  selected?: number;
  onSelect?: (index: number) => void;
  onPartChange?: (index: number, patch: Partial<CustomPrimitive>) => void;
  pose?: number[];
  frames?: { time: number; pose: number[] }[];
  duration?: number;
  playing?: boolean;
  rotationOnly?: boolean;
  animationAxis?: "x" | "y" | "z";
  onPoseChange?: (joint: Joint, rotation: [number, number, number]) => void;
};
const clamp = (n: number, min: number, max: number) =>
  Math.round(Math.max(min, Math.min(max, n)) * 100) / 100;

/** A paused, event-driven editing renderer. Gizmo drags commit once, so undo is one action. */
export function CreatorViewport(props: Props) {
  const canvas = useRef<HTMLCanvasElement>(null),
    holder = useRef<HTMLDivElement>(null);
  const latest = useRef(props);
  latest.current = props;
  const runtime = useRef<{
    viewer: SkinViewer;
    gizmo: TransformControls;
    meshes: Group[];
    box: BoxHelper;
    render: () => void;
    disposeParts: () => void;
  } | null>(null);
  const [ready, setReady] = useState(0),
    [error, setError] = useState("");
  const [mode, setMode] = useState<Mode>(
    props.onPoseChange ? "rotate" : "translate",
  );
  const [joint, setJoint] = useState<Joint>("rightArm");
  const jointRef = useRef(joint);
  jointRef.current = joint;
  const [avatar, setAvatar] = useState(true),
    [grid, setGrid] = useState(true),
    [snap, setSnap] = useState(false);
  const gridRef = useRef<GridHelper | null>(null);
  useEffect(() => {
    const surface = canvas.current,
      container = holder.current;
    if (!surface || !container) return;
    let v: SkinViewer;
    try {
      v = new SkinViewer({
        canvas: surface,
        width: 600,
        height: 520,
        pixelRatio: Math.min(1.5, devicePixelRatio),
        renderPaused: true,
      });
    } catch {
      setError(
        "Die 3D-Arbeitsfläche konnte nicht gestartet werden. Bitte prüfe WebView2 und den Grafiktreiber.",
      );
      return;
    }
    v.camera.position.set(38, 18, 58);
    v.controls.target.set(0, -8, 0);
    v.controls.enablePan = true;
    v.controls.enableDamping = false;
    v.controls.update();
    v.globalLight.intensity = 0.8;
    v.cameraLight.intensity = 0.7;
    const floor = new GridHelper(96, 48, 0x587bab, 0x27374b);
    floor.position.y = -24;
    v.scene.add(floor);
    gridRef.current = floor;
    const gizmo = new TransformControls(v.camera, surface);
    gizmo.setSpace("local");
    gizmo.setSize(0.85);
    v.scene.add(gizmo);
    const box = new BoxHelper(v.playerObject, 0x81b5ff);
    box.visible = false;
    v.scene.add(box);
    const meshes: Group[] = [];
    const render = () => {
      if (latest.current.onPoseChange && gizmo.dragging) {
        const player = v.playerObject,
          roll = player.skin.body.rotation.z,
          values = Array<number>(19).fill(0);
        jointNames.forEach((key, index) => {
          values[index * 3] = player.skin[key].rotation.x;
          values[index * 3 + 1] = player.skin[key].rotation.y;
          values[index * 3 + 2] =
            player.skin[key].rotation.z -
            (key === "head"
              ? roll * 0.4
              : key === "leftArm" || key === "rightArm"
                ? roll * 0.5
                : 0);
        });
        values[18] = player.position.y;
        applyRigPose(player, values);
      }
      if (gizmo.object) {
        box.setFromObject(gizmo.object);
        box.visible = true;
      } else box.visible = false;
      v.render();
    };
    const disposeParts = () => {
      gizmo.detach();
      for (const root of meshes) {
        root.removeFromParent();
        root.traverse((o) => {
          if (o instanceof Mesh) {
            o.geometry.dispose();
            (o.material as MeshStandardMaterial).dispose();
          }
        });
      }
      meshes.length = 0;
    };
    const dragging = (e: { value?: unknown }) => {
      v.controls.enabled = !e.value;
    };
    const commit = () => {
      const object = gizmo.object;
      if (!object) return;
      if (latest.current.onPoseChange) {
        const selectedJoint = jointRef.current,
          roll = v.playerObject.skin.body.rotation.z;
        latest.current.onPoseChange(jointRef.current, [
          clamp(object.rotation.x, -3, 3),
          clamp(object.rotation.y, -3, 3),
          clamp(
            object.rotation.z -
              (selectedJoint === "head"
                ? roll * 0.4
                : selectedJoint === "leftArm" || selectedJoint === "rightArm"
                  ? roll * 0.5
                  : 0),
            -3,
            3,
          ),
        ]);
      } else {
        const index = meshes.indexOf(object as Group),
          part = latest.current.parts?.[index];
        if (!part) return;
        const deg = 180 / Math.PI;
        latest.current.onPartChange?.(index, {
          position: [
            clamp(object.position.x, -32, 32),
            clamp(
              -object.position.y + (part.joint === "body" ? 6 : 0),
              -32,
              32,
            ),
            clamp(-object.position.z, -32, 32),
          ],
          rotation: [
            clamp(object.rotation.x * deg, -180, 180),
            clamp(-object.rotation.y * deg, -180, 180),
            clamp(-object.rotation.z * deg, -180, 180),
          ],
          size: [
            clamp(object.scale.x, 0.2, 32),
            clamp(object.scale.y, 0.2, 32),
            clamp(object.scale.z, 0.2, 32),
          ],
        });
      }
    };
    gizmo.addEventListener("dragging-changed", dragging);
    gizmo.addEventListener("objectChange", render);
    gizmo.addEventListener("mouseUp", commit);
    v.controls.addEventListener("change", render);
    const ray = new Raycaster(),
      point = new Vector2();
    let down = [0, 0];
    const pointerDown = (e: PointerEvent) => {
      down = [e.clientX, e.clientY];
    };
    const pointerUp = (e: PointerEvent) => {
      if (
        e.button !== 0 ||
        gizmo.axis ||
        Math.hypot(e.clientX - down[0], e.clientY - down[1]) > 4
      )
        return;
      const bounds = surface.getBoundingClientRect();
      point.set(
        ((e.clientX - bounds.left) / bounds.width) * 2 - 1,
        (-(e.clientY - bounds.top) / bounds.height) * 2 + 1,
      );
      v.scene.updateMatrixWorld(true);
      ray.setFromCamera(point, v.camera);
      if (latest.current.onPoseChange) {
        const hits = ray.intersectObject(v.playerObject.skin, true);
        let object: Object3D | null = hits[0]?.object ?? null;
        while (object) {
          const name = jointNames.find(
            (key) => v.playerObject.skin[key] === object,
          );
          if (name) {
            setJoint(name);
            return;
          }
          object = object.parent;
        }
      } else {
        const hits = ray.intersectObjects(meshes, true);
        let object: Object3D | null = hits[0]?.object ?? null;
        while (object) {
          const index = meshes.indexOf(object as Group);
          if (index >= 0) {
            latest.current.onSelect?.(index);
            return;
          }
          object = object.parent;
        }
      }
    };
    surface.addEventListener("pointerdown", pointerDown);
    surface.addEventListener("pointerup", pointerUp);
    const resize = new ResizeObserver(([entry]) => {
      const b = entry.contentRect;
      v.width = Math.max(1, b.width);
      v.height = Math.max(1, b.height);
      render();
    });
    resize.observe(container);
    runtime.current = { viewer: v, gizmo, meshes, box, render, disposeParts };
    setReady((n) => n + 1);
    render();
    return () => {
      runtime.current = null;
      resize.disconnect();
      surface.removeEventListener("pointerdown", pointerDown);
      surface.removeEventListener("pointerup", pointerUp);
      v.controls.removeEventListener("change", render);
      disposeParts();
      gizmo.dispose();
      gizmo.removeFromParent();
      box.geometry.dispose();
      box.material.dispose();
      floor.geometry.dispose();
      const materials = Array.isArray(floor.material)
        ? floor.material
        : [floor.material];
      materials.forEach((m) => m.dispose());
      floor.removeFromParent();
      releaseSkinViewer(v);
    };
  }, []);
  useEffect(() => {
    const r = runtime.current;
    if (!r) return;
    let cancelled = false;
    void r.viewer
      .loadSkin(props.skin || "/assets/votex-default-skin.png", {
        model: props.model || "default",
      })
      .then(() => {
        if (!cancelled) r.render();
      })
      .catch(() => {
        if (!cancelled)
          setError("Die Skin-Referenz konnte nicht geladen werden.");
      });
    return () => {
      cancelled = true;
    };
  }, [ready, props.skin, props.model]);
  useEffect(() => {
    const r = runtime.current;
    if (!r) return;
    r.disposeParts();
    for (const part of props.parts ?? []) {
      const local = primitiveSurface({
        ...part,
        type: part.type === "pixel" ? "cube" : part.type,
        position: [0, 0, 0],
        rotation: [0, 0, 0],
        size: [1, 1, 1],
        animated: false,
      });
      const geometry = surfaceGeometry(local),
        material = new MeshStandardMaterial({
          ...local.material,
          vertexColors: true,
          side: DoubleSide,
        });
      const object = new Group();
      object.add(new Mesh(geometry, material));
      object.position.set(
        part.position[0],
        -part.position[1] + (part.joint === "body" ? 6 : 0),
        -part.position[2],
      );
      object.rotation.set(
        (part.rotation[0] * Math.PI) / 180,
        (-part.rotation[1] * Math.PI) / 180,
        (-part.rotation[2] * Math.PI) / 180,
        "ZYX",
      );
      object.scale.set(
        part.size[0],
        part.size[1],
        part.size[2],
      );
      r.viewer.playerObject.skin[part.joint].add(object);
      r.meshes.push(object);
    }
    r.render();
  }, [ready, props.parts]);
  useEffect(() => {
    const r = runtime.current;
    if (!r) return;
    r.gizmo.setMode(props.onPoseChange || props.rotationOnly ? "rotate" : mode);
    r.gizmo.showX = !props.rotationOnly || props.animationAxis === "x";
    r.gizmo.showY = !props.rotationOnly || props.animationAxis === "y";
    r.gizmo.showZ = !props.rotationOnly || props.animationAxis === "z";
    r.gizmo.enabled = !props.playing;
    r.gizmo.visible = !props.playing;
    r.gizmo.setTranslationSnap(snap ? 0.5 : null);
    r.gizmo.setRotationSnap(snap ? Math.PI / 12 : null);
    r.gizmo.setScaleSnap(snap ? 0.5 : null);
    const target = props.onPoseChange
      ? r.viewer.playerObject.skin[joint]
      : r.meshes[props.selected ?? 0];
    if (target) r.gizmo.attach(target);
    else r.gizmo.detach();
    r.render();
  }, [
    ready,
    props.parts,
    props.selected,
    joint,
    mode,
    snap,
    props.onPoseChange,
    props.rotationOnly,
    props.animationAxis,
    props.playing,
  ]);
  useEffect(() => {
    const r = runtime.current;
    if (!r) return;
    r.viewer.playerObject.skin.setInnerLayerVisible(avatar);
    r.viewer.playerObject.skin.setOuterLayerVisible(avatar);
    if (gridRef.current) gridRef.current.visible = grid;
    r.render();
  }, [ready, avatar, grid]);
  useEffect(() => {
    const r = runtime.current;
    if (!r) return;
    let frame = 0,
      start = performance.now();
    const pose = (values: number[]) => {
      applyRigPose(r.viewer.playerObject, values);
    };
    const draw = (now: number) => {
      const seconds = (now - start) / 1000;
      if (props.pose) {
        let values = props.pose;
        if (props.playing && props.frames?.length) {
          const time = (seconds / (props.duration || 2.4)) % 1;
          const right = props.frames.findIndex((f) => f.time >= time);
          const b = props.frames[Math.max(1, right)],
            a = props.frames[Math.max(1, right) - 1];
          const t = clamp((time - a.time) / (b.time - a.time || 1), 0, 1),
            ease = t * t * (3 - 2 * t);
          values = a.pose.map((v, i) => v + (b.pose[i] - v) * ease);
        }
        if (!r.gizmo.dragging) pose(values);
      } else if (!r.gizmo.dragging) {
        (props.parts ?? []).forEach((part, i) => {
          if (r.meshes[i]) {
            r.meshes[i].rotation.set(
              (part.rotation[0] * Math.PI) / 180,
              (-part.rotation[1] * Math.PI) / 180,
              (-part.rotation[2] * Math.PI) / 180,
              "ZYX",
            );
            if (props.playing && part.animated && part.animation) {
              const animation = part.animation;
              const sign = animation.axis === "x" ? 1 : -1;
              const angle = samplePartMotion(animation, seconds);
              r.meshes[i].rotation[animation.axis] += angle * sign;
            }
          }
        });
      }
      if (!document.hidden) r.render();
      if (props.playing && !document.hidden)
        frame = requestAnimationFrame(draw);
    };
    const visibility = () => {
      cancelAnimationFrame(frame);
      if (!document.hidden) draw(performance.now());
    };
    draw(start);
    document.addEventListener("visibilitychange", visibility);
    return () => {
      cancelAnimationFrame(frame);
      document.removeEventListener("visibilitychange", visibility);
    };
  }, [
    ready,
    props.pose,
    props.frames,
    props.duration,
    props.playing,
    props.parts,
  ]);
  const camera = (side: "front" | "back" | "top") => {
    const r = runtime.current;
    if (!r) return;
    r.viewer.camera.position.set(
      ...((side === "top"
        ? [0, 64, 0.01]
        : side === "back"
          ? [0, 0, -70]
          : [0, 0, 70]) as [number, number, number]),
    );
    r.viewer.controls.target.set(0, -8, 0);
    r.viewer.controls.update();
    r.render();
  };
  return (
    <section
      className="creator-stage"
      aria-label={props.onPoseChange ? "3D-Posen-Editor" : "3D-Modell-Editor"}
    >
      <div className="creator-tools" role="toolbar" aria-label="3D-Werkzeuge">
        {!props.onPoseChange && !props.rotationOnly && (
          <>
            <button
              className={mode === "translate" ? "active" : ""}
              onClick={() => setMode("translate")}
              title="Verschieben · Achsen ziehen"
            >
              <Move3D size={16} />
              Verschieben
            </button>
            <button
              className={mode === "scale" ? "active" : ""}
              onClick={() => setMode("scale")}
              title="Größe · Achsen ziehen"
            >
              <Scaling size={16} />
              Skalieren
            </button>
          </>
        )}
        <button
          className={mode === "rotate" ? "active" : ""}
          onClick={() => setMode("rotate")}
        >
          <Rotate3D size={16} />
          Drehen
        </button>
        <button aria-pressed={snap} onClick={() => setSnap(!snap)}>
          Raster {snap ? "an" : "aus"}
        </button>
        <button
          aria-label="Bodenraster umschalten"
          aria-pressed={grid}
          onClick={() => setGrid(!grid)}
        >
          <Grid3X3 size={16} />
        </button>
        {!props.onPoseChange && (
          <button
            aria-label="Skin-Referenz umschalten"
            aria-pressed={avatar}
            onClick={() => setAvatar(!avatar)}
          >
            <UserRound size={16} />
          </button>
        )}
      </div>
      {props.onPoseChange && (
        <div className="creator-joints">
          {jointNames.map((key, i) => (
            <button
              key={key}
              className={joint === key ? "active" : ""}
              onClick={() => setJoint(key)}
            >
              {
                [
                  "Kopf",
                  "Körper",
                  "Arm links",
                  "Arm rechts",
                  "Bein links",
                  "Bein rechts",
                ][i]
              }
            </button>
          ))}
        </div>
      )}
      <div ref={holder} className="creator-canvas">
        <canvas
          ref={canvas}
          aria-label="3D-Ansicht: Teil anklicken und farbige Achsen ziehen"
        />
        {error && (
          <p className="creator-error" role="alert">
            {error}
          </p>
        )}
      </div>
      <footer>
        <span>
          Teil anklicken · Achsen ziehen · Rechts ziehen: Kamera verschieben ·
          Mausrad: Zoom
        </span>
        <div>
          <button onClick={() => camera("front")}>Vorne</button>
          <button onClick={() => camera("back")}>Hinten</button>
          <button onClick={() => camera("top")}>Oben</button>
        </div>
      </footer>
    </section>
  );
}
