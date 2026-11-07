import { useContext, useEffect, useRef, useState } from "react";
import { RotateCcw, Camera, Move3D, Maximize2, Download } from "lucide-react";
import { SkinViewer, FunctionAnimation, NameTagObject } from "skinview3d";
import {
  HemisphereLight,
  DirectionalLight,
  PMREMGenerator,
  ACESFilmicToneMapping,
  ColorManagement,
  SRGBColorSpace,
  Vector3,
  Box3,
  Mesh,
} from "three";
import { OutputPass } from "three/examples/jsm/postprocessing/OutputPass.js";
import { RoomEnvironment } from "three/examples/jsm/environments/RoomEnvironment.js";
import { attachWearables, type Look, type WearableHandle } from "./Wearables";
import { segmentedCape } from "./CapeMotion";
import type { CustomCosmetic } from "../data/customModels";
import {
  EmoteMotion,
  jointNames,
  smooth,
  type CustomEmote,
  type EmoteId,
} from "./emotes";
import { ThemeContext, Modal } from "./Glass";
import { call, native } from "../services/desktop";
import { useReducedMotion } from "motion/react";
import {
  defaultCharacterView,
  type CharacterView,
} from "../../../../packages/shared-config/schema";
import {
  createCharacterScene,
  lightingPresets,
  type CharacterSceneHandle,
} from "./CharacterScene";
import { PreviewSuspendedContext, pauseAfterDraw } from "./PreviewActivity";
import { releaseSkinViewer } from "./viewerResources";
import { applyRigPose } from "./rigPose";
import { SkinFallback } from "./SkinFallback";
export function SkinPreview({
  skin,
  model = "default",
  idle = true,
  emotePlaying = idle,
  cape = false,
  look,
  customCosmetics = [],
  customEmotes = [],
  emote,
  emoteLoop = true,
  emoteSpeed = 1,
  replayKey = 0,
  onEmoteEnd,
  presentation = defaultCharacterView,
  minimal = false,
}: {
  skin?: string;
  model?: "default" | "slim";
  idle?: boolean;
  emotePlaying?: boolean;
  cape?: boolean;
  look?: Look;
  customCosmetics?: CustomCosmetic[];
  customEmotes?: CustomEmote[];
  emote?: EmoteId;
  emoteLoop?: boolean;
  emoteSpeed?: number;
  replayKey?: number;
  onEmoteEnd?: () => void;
  presentation?: CharacterView;
  minimal?: boolean;
}) {
  const theme = useContext(ThemeContext),
    osReduced = useReducedMotion(),
    suspended = useContext(PreviewSuspendedContext);
  const animatedIdle =
    idle &&
    !theme.reducedMotion &&
    !osReduced &&
    !suspended &&
    theme.renderProfile !== "ultra-performance";
  const canvas = useRef<HTMLCanvasElement>(null),
    holder = useRef<HTMLDivElement>(null),
    viewer = useRef<SkinViewer | null>(null),
    appearance = useRef<WearableHandle | null>(null),
    motion = useRef(new EmoteMotion());
  const preferences = useRef({
    animatedIdle,
    emote,
    emoteSpeed,
    onEmoteEnd,
    suspended,
  });
  preferences.current = {
    animatedIdle,
    emote,
    emoteSpeed,
    onEmoteEnd,
    suspended,
  };
  const scene = useRef<CharacterSceneHandle | null>(null);
  const lighting = useRef<{
    hemisphere: HemisphereLight;
    key: DirectionalLight;
    rim: DirectionalLight;
  } | null>(null);
  const visible = useRef(true),
    focused = useRef(document.hasFocus()),
    intersecting = useRef(true),
    wake = useRef(() => {});
  const [error, setError] = useState(""),
    [phase, setPhase] = useState("idle");
  const [shot, setShot] = useState(""),
    [savedShot, setSavedShot] = useState("");
  const [shotError, setShotError] = useState("");
  const [lookState, setLookState] = useState("ready");
  const transition = useRef<{
    previous: WearableHandle | null;
    next: WearableHandle;
    start: number | null;
  } | null>(null);
  const phaseRef = useRef("idle");
  const cameraGoal = useRef<Vector3 | null>(null);
  const fitModel = useRef<(immediate?: boolean, force?: boolean) => number>(
    () => 88,
  );
  const signature = JSON.stringify([look, customCosmetics]);
  const emoteSignature = JSON.stringify(customEmotes);
  useEffect(() => {
    if (!canvas.current || !holder.current) return;
    holder.current.dataset.viewerId = crypto.randomUUID();
    let v: SkinViewer;
    try {
      v = new SkinViewer({
        canvas: canvas.current,
        width: 400,
        height: 450,
        model,
        zoom: 0.86,
        fov: 38,
        pixelRatio: Math.min(1.75, window.devicePixelRatio),
        renderPaused: true,
      });
    } catch (failure) {
      console.warn("Votex WebGL initialization failed:",failure instanceof Error ? failure.message : "unknown graphics error");
      setError("Die 3D-Vorschau ist auf diesem Grafikgerät nicht verfügbar.");
      setLookState("error");
      return;
    }
    viewer.current = v;
    v.nameTag = new NameTagObject("Votex Client", {
      font: '600 48px "Segoe UI", sans-serif',
      height: 3.1,
      margin: [8, 16, 8, 16],
      backgroundStyle: "rgba(5, 10, 18, 0.65)",
      repaintAfterLoaded: false,
    });
    ColorManagement.enabled = true;
    v.globalLight.intensity = 0.32;
    const output = new OutputPass();
    v.composer.addPass(output);
    v.renderer.toneMapping = ACESFilmicToneMapping;
    v.renderer.toneMappingExposure = 0.88;
    v.camera.position.set(18, 7, 88);
    v.controls.enablePan = false;
    v.controls.minDistance = 28;
    v.controls.maxDistance = 240;
    v.cameraLight.intensity = 0.35;
    const hemisphere = new HemisphereLight(0xdde8ff, 0x20232f, 0.65),
      key = new DirectionalLight(0xffead5, 1.0),
      rim = new DirectionalLight(0xa9c8ff, 0.9);
    key.position.set(-25, 35, 30);
    rim.position.set(20, 15, -28);
    key.shadow.mapSize.set(512, 512);
    key.shadow.camera.left = key.shadow.camera.bottom = -55;
    key.shadow.camera.right = key.shadow.camera.top = 55;
    key.shadow.camera.near = 1;
    key.shadow.camera.far = 180;
    key.shadow.bias = -0.0004;
    lighting.current = { hemisphere, key, rim };
    v.scene.add(hemisphere, key, rim);
    const environment = new RoomEnvironment(),
      pmrem = new PMREMGenerator(v.renderer),
      env = pmrem.fromScene(environment, 0.025);
    v.scene.environment = env.texture;
    environment.dispose();
    pmrem.dispose();
    let ended = false;
    const showPhase = (p: string) => {
      if (phaseRef.current !== p) {
        phaseRef.current = p;
        setPhase(p);
      }
    };
    const capeMotion = segmentedCape(v.playerObject);
    v.animation = new FunctionAnimation((p, t, delta) => {
      try {
        scene.current?.frame(t, preferences.current.animatedIdle);
        capeMotion.frame(t, preferences.current.animatedIdle);
        key.position.x =
          -25 +
          (scene.current && preferences.current.animatedIdle
            ? Math.sin(t * 0.3) * 3
            : 0);
        if (cameraGoal.current) {
          v.camera.position.lerp(
            cameraGoal.current,
            1 - Math.exp(-8 * Math.min(0.05, delta)),
          );
          v.controls.update();
          if (v.camera.position.distanceTo(cameraGoal.current) < 0.015)
            cameraGoal.current = null;
        }
        const controller = motion.current,
          pose = controller.advance(delta);
        applyRigPose(p,pose);
        p.rotation.z = 0;
        if (controller.settled && preferences.current.animatedIdle) {
          p.skin.leftArm.rotation.z = 0.035 + Math.sin(t * 1.1) * 0.015;
          p.skin.rightArm.rotation.z = -0.035 - Math.sin(t * 1.1) * 0.015;
          p.skin.head.rotation.y = Math.sin(t * 0.38) * 0.035;
          p.rotation.z = Math.sin(t * 0.72) * 0.014;
          p.skin.body.rotation.y = Math.sin(t * 0.58) * 0.018;
          p.position.y = (pose[18] || 0) + Math.sin(t * 1.1) * 0.07;
        }
        appearance.current?.frame(
          t,
          preferences.current.animatedIdle || !controller.settled,
        );
        if (transition.current) {
          const swap = transition.current;
          if (swap.start === null) swap.start = t;
          const blend = smooth(Math.min(1, (t - swap.start) / 0.22));
          swap.next.opacity(blend);
          swap.previous?.opacity(1 - blend);
          swap.previous?.frame(t, preferences.current.animatedIdle);
          if (blend >= 1) {
            swap.previous?.dispose();
            transition.current = null;
            fitModel.current();
            setLookState("ready");
          }
        }
        showPhase(controller.phase);
        if (holder.current)
          holder.current.style.setProperty(
            "--emote-progress",
            String(controller.progress),
          );
        if (controller.complete && !ended) {
          ended = true;
          preferences.current.onEmoteEnd?.();
        } else if (!controller.complete) ended = false;
        if (
          controller.settled &&
          !controller.wantsFrames &&
          !preferences.current.animatedIdle &&
          controller.phase === "idle" &&
          !cameraGoal.current &&
          !transition.current
        )
          pauseAfterDraw(
            v,
            () =>
              controller.settled &&
              !controller.wantsFrames &&
              !preferences.current.animatedIdle &&
              !cameraGoal.current &&
              !transition.current,
          );
      } catch (error) {
        console.error("Votex studio animation failed", error);
        setError(
          "Die 3D-Vorschau wurde wegen eines Darstellungsfehlers angehalten.",
        );
        setLookState("error");
        queueMicrotask(() => {
          v.renderPaused = true;
        });
      }
    });
    wake.current = () => {
      if (
        !document.hidden &&
        focused.current &&
        intersecting.current &&
        !preferences.current.suspended
      )
        v.renderPaused = false;
    };
    fitModel.current = (immediate = false, force = false) => {
      v.playerObject.updateWorldMatrix(true, true);
      const box = new Box3();
      v.playerObject.traverseVisible((o) => {
        if (o instanceof Mesh) {
          o.castShadow = v.renderer.shadowMap.enabled;
          o.geometry.computeBoundingBox();
          if (o.geometry.boundingBox)
            box.union(
              o.geometry.boundingBox.clone().applyMatrix4(o.matrixWorld),
            );
        }
      });
      if (box.isEmpty()) return 88;
      if (v.nameTag) {
        const y = v.nameTag.getWorldPosition(new Vector3()).y;
        v.nameTag.center.y = (y - box.max.y - 3) / v.nameTag.scale.y;
        box.max.y += 3 + v.nameTag.scale.y;
      }
      if (scene.current) box.union(scene.current.bounds);
      const tangent = Math.tan((v.camera.fov * Math.PI) / 360);
      const halfWidth =
        Math.max(Math.abs(box.min.x), Math.abs(box.max.x)) +
        (box.max.z - box.min.z) * 0.15;
      const halfHeight = Math.max(Math.abs(box.min.y), Math.abs(box.max.y));
      const radius = Math.max(
        48,
        Math.max(
          halfHeight / tangent,
          halfWidth / (tangent * v.camera.aspect),
        ) *
          1.12 +
          (box.max.z - box.min.z) * 0.5,
      );
      if (force || v.camera.position.length() < radius) {
        const goal = v.camera.position
          .clone()
          .normalize()
          .multiplyScalar(radius);
        if (immediate) {
          v.camera.position.copy(goal);
          v.controls.update();
        } else {
          cameraGoal.current = goal;
          wake.current();
        }
      }
      return radius;
    };
    const controls = () => {
      if (v.renderPaused) v.render();
    };
    v.controls.addEventListener("start", () => {
      cameraGoal.current = null;
    });
    v.controls.addEventListener("change", controls);
    const observer = new ResizeObserver(([e]) => {
      if (e) {
        v.setSize(e.contentRect.width, e.contentRect.height);
        fitModel.current(true, true);
        v.render();
      }
    });
    observer.observe(holder.current);
    const visibility = () => {
      visible.current = !document.hidden;
      if (
        document.hidden ||
        !focused.current ||
        !intersecting.current ||
        preferences.current.suspended
      )
        v.renderPaused = true;
      else wake.current();
    };
    const blur = () => {
      focused.current = false;
      visibility();
    };
    const focus = () => {
      focused.current = true;
      visibility();
    };
    document.addEventListener("visibilitychange", visibility);
    window.addEventListener("blur", blur);
    window.addEventListener("focus", focus);
    const intersection = new IntersectionObserver(([e]) => {
      intersecting.current = e.isIntersecting;
      visibility();
    });
    intersection.observe(holder.current);
    return () => {
      observer.disconnect();
      intersection.disconnect();
      document.removeEventListener("visibilitychange", visibility);
      window.removeEventListener("blur", blur);
      window.removeEventListener("focus", focus);
      capeMotion.dispose();
      appearance.current?.dispose();
      transition.current?.previous?.dispose();
      transition.current = null;
      appearance.current = null;
      scene.current?.dispose();
      scene.current = null;
      lighting.current = null;
      key.shadow.map?.dispose();
      env.dispose();
      output.dispose();
      v.nameTag?.material.map?.dispose();
      v.nameTag?.material.dispose();
      releaseSkinViewer(v);
      viewer.current = null;
      wake.current = () => {};
    };
  }, []);
  useEffect(() => {
    const v = viewer.current,
      rig = lighting.current;
    if (!v || !rig) return;
    const lights = lightingPresets[presentation.lighting];
    rig.hemisphere.color.setHex(lights.sky);
    rig.hemisphere.groundColor.setHex(lights.ground);
    rig.hemisphere.intensity = lights.fill;
    rig.key.color.setHex(lights.key);
    rig.key.intensity = lights.keyPower;
    rig.rim.color.setHex(lights.rim);
    v.renderer.toneMappingExposure = lights.exposure;
    v.render();
    wake.current();
  }, [presentation.lighting]);
  useEffect(() => {
    const v = viewer.current,
      rig = lighting.current;
    if (!v || !rig) return;
    scene.current?.dispose();
    scene.current =
      presentation.mode === "scene"
        ? createCharacterScene(presentation.scene)
        : null;
    if (scene.current) v.scene.add(scene.current.group);
    v.controls.enablePan = presentation.mode === "scene";
    v.controls.maxPolarAngle =
      Math.PI * (presentation.mode === "scene" ? 0.49 : 0.95);
    v.controls.target.set(0, 0, 0);
    cameraGoal.current = null;
    v.renderer.shadowMap.enabled =
      presentation.mode === "scene" &&
      theme.renderProfile !== "ultra-performance";
    rig.key.castShadow = v.renderer.shadowMap.enabled;
    fitModel.current(true, true);
    v.render();
    wake.current();
  }, [presentation.mode, presentation.scene, theme.renderProfile]);
  useEffect(() => {
    if (viewer.current && suspended) viewer.current.renderPaused = true;
    else wake.current();
  }, [suspended]);
  useEffect(() => {
    const v = viewer.current;
    if (!v) return;
    let gone = false;
    setError("");
    void v
      .loadSkin(skin || "/assets/votex-default-skin.png", { model })
      .then(() => {
        if (!gone) {
          const map = v.playerObject.skin.map;
          if (map) {
            map.colorSpace = SRGBColorSpace;
            map.needsUpdate = true;
          }
          v.render();
          fitModel.current();
          wake.current();
        }
      })
      .catch(() => {
        if (!gone) setError("Dieses Skin-Bild konnte nicht geladen werden.");
      });
    return () => {
      gone = true;
    };
  }, [skin, model]);
  useEffect(() => {
    const v = viewer.current;
    if (!v) return;
    let cancelled = false,
      failed = false;
    transition.current?.previous?.dispose();
    transition.current = null;
    const previous = appearance.current;
    previous?.opacity(1);
    if (!look) {
      previous?.dispose();
      appearance.current = null;
      setLookState("ready");
      v.render();
      return;
    }
    setLookState("loading");
    setError("");
    const next = attachWearables(
      v.playerObject,
      look,
      customCosmetics,
      () => {
        if (cancelled) return;
        appearance.current = next;
        fitModel.current();
        if (failed) {
          previous?.dispose();
          setLookState("error");
        } else {
          if (
            theme.reducedMotion ||
            osReduced ||
            suspended ||
            theme.renderProfile === "ultra-performance"
          ) {
            setLookState("ready");
            next.opacity(1);
            previous?.dispose();
          } else transition.current = { previous, next, start: null };
        }
        v.render();
        wake.current();
      },
      () => {
        failed = true;
        setError("Ein Accessoire konnte nicht geladen werden.");
      },
    );
    next.opacity(0);
    return () => {
      cancelled = true;
      if (appearance.current !== next) next.dispose();
    };
  }, [signature]);
  useEffect(() => {
    const v = viewer.current;
    if (!v) return;
    let gone = false;
    if (cape || look?.cape) {
      void v
        .loadCape(
          look?.capeData ||
            (look?.equipment.cape
              ? `/assets/catalog/capes/${look.equipment.cape}.png`
              : "/assets/votex-cape.png"),
        )
        .then(() => {
          if (!gone) {
            v.render();
            wake.current();
          }
        })
        .catch(() => {
          if (!gone) setError("Dieses Cape konnte nicht geladen werden.");
        });
    } else {
      v.loadCape(null);
      v.render();
    }
    return () => {
      gone = true;
    };
  }, [cape, look?.cape, look?.capeData, look?.equipment.cape]);
  useEffect(() => {
    motion.current.loop = emoteLoop;
    motion.current.speed = emoteSpeed;
    motion.current.request(emote && emotePlaying ? emote : null, customEmotes);
    wake.current();
  }, [emote, emotePlaying, replayKey, emoteSignature]);
  useEffect(() => {
    motion.current.loop = emoteLoop;
    motion.current.speed = emoteSpeed;
    wake.current();
  }, [emoteLoop, emoteSpeed, animatedIdle]);
  const camera = (back = false, fit = false) => {
    const v = viewer.current;
    if (!v) return;
    v.controls.target.set(0, 0, 0);
    const radius = fit
      ? fitModel.current(false, true)
      : Math.max(40, v.camera.position.length());
    const goal = new Vector3(
      back ? -radius * 0.16 : radius * 0.2,
      7,
      back ? -radius * 0.98 : radius * 0.98,
    );
    if (suspended || theme.reducedMotion || osReduced || !focused.current) {
      cameraGoal.current = null;
      v.camera.position.copy(goal);
      v.controls.update();
      v.render();
      return;
    }
    cameraGoal.current = goal;
    wake.current();
  };
  const capture = async () => {
    try {
      const v = viewer.current;
      if (!v || !canvas.current) return;
      v.render();
      const url = canvas.current.toDataURL("image/png");
      setShot(url);
      setSavedShot("");
      setShotError("");
      if (native)
        setSavedShot(await call<string>("save_studio_image", { data: url }));
    } catch {
      setShotError("Die Studioaufnahme konnte nicht gespeichert werden.");
    }
  };
  return (
    <div
      className="skin-stage"
      ref={holder}
      data-motion-phase={phase}
      data-look-state={lookState}
      data-presentation-mode={presentation.mode}
      data-scene={presentation.scene}
      data-lighting={presentation.lighting}
      data-render-suspended={suspended}
      onContextMenu={
        presentation.mode === "scene" ? (e) => e.preventDefault() : undefined
      }
    >
      {presentation.mode !== "scene" && <div className="studio-ground" />}
      <canvas hidden={Boolean(error)}
        aria-label={
          presentation.mode === "scene"
            ? "Interaktive 3D-Szene: ziehen zum Drehen, Mausrad zum Zoomen, rechte Maustaste zum Verschieben"
            : "Interaktive 3D-Anprobe: ziehen zum Drehen, scrollen zum Vergrößern"
        }
        ref={canvas}
      />
      {error && <SkinFallback skin={skin}/>}
      {error && !minimal && (
        <p className="skin-error" role="alert">
          {error}
        </p>
      )}
      {lookState === "loading" && (
        <span className="look-loading" role="status">
          Look wird vorbereitet …
        </span>
      )}
      {suspended && lookState !== "loading" && (
        <span className="look-loading" role="status">
          Vorschau pausiert · Minecraft läuft
        </span>
      )}
      {emote && (
        <div className="motion-phase">
          <span className={`motion-phase-dot ${phase}`} />
          {
            {
              idle: "Ruhe",
              enter: "Einblenden",
              hold: "Geste",
              exit: "Ausblenden",
            }[phase]
          }
          <i />
        </div>
      )}
      {!minimal && <div className="skin-controls">
        <button
          className="icon-button"
          aria-label="Vorderansicht"
          onClick={() => camera()}
        >
          <Camera size={14} />
        </button>
        <button
          className="icon-button"
          aria-label="Rückansicht"
          onClick={() => camera(true)}
        >
          <RotateCcw size={14} />
        </button>
        <button
          className="icon-button"
          aria-label="Modell vollständig einpassen"
          onClick={() => camera(false, true)}
        >
          <Maximize2 size={13} />
        </button>
        <button
          className="icon-button"
          aria-label="Studioaufnahme als PNG speichern"
          title="Diesen Look als PNG aufnehmen"
          disabled={lookState === "loading"}
          onClick={() => void capture()}
        >
          <Download size={13} />
        </button>
        <span>
          <Move3D size={11} />{" "}
          {presentation.mode === "scene"
            ? "DREHEN · ZOOMEN · VERSCHIEBEN"
            : "ZIEHEN ZUM DREHEN"}
        </span>
      </div>}
      {shot && (
        <Modal title="Deine Studioaufnahme" onClose={() => setShot("")}>
          <img
            className="studio-shot"
            src={shot}
            alt="Aufgenommener Votex-Look"
          />
          <p role={shotError ? "alert" : undefined}>
            {shotError ||
              (native
                ? savedShot
                  ? "Gespeichert in VotexClient / studio-shots."
                  : "Bild wird gespeichert …"
                : presentation.mode === "scene"
                  ? "Deine 3D-Szene als PNG."
                  : "Dein Look als PNG mit transparentem Hintergrund.")}
          </p>
          {native && savedShot && (
            <code className="studio-save-path">{savedShot}</code>
          )}
          <div className="toolbar">
            {native ? (
              <button
                className="btn primary"
                disabled={!savedShot}
                onClick={() =>
                  void call("open_studio_folder").catch(() =>
                    setShotError(
                      "Der Aufnahmeordner konnte nicht geöffnet werden.",
                    ),
                  )
                }
              >
                Aufnahmeordner öffnen
              </button>
            ) : (
              <a
                className="btn primary"
                href={shot}
                download="Votex-Studio.png"
              >
                PNG herunterladen
              </a>
            )}
            <button className="btn" onClick={() => setShot("")}>
              Zurück zur Anprobe
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}
