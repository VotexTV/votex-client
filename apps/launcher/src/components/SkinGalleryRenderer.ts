import { SkinViewer } from "skinview3d";
import {
  CanvasTexture,
  DirectionalLight,
  HemisphereLight,
  NearestFilter,
  SRGBColorSpace,
  type Texture,
} from "three";
import { OutputPass } from "three/examples/jsm/postprocessing/OutputPass.js";
import { releaseSkinViewer } from "./viewerResources";

export type GalleryStatus = "loading" | "ready" | "error";
type View = { yaw: number; pitch: number; distance: number };
type Entry = {
  id: string;
  canvas: HTMLCanvasElement;
  source: string;
  model: "default" | "slim";
  status: (state: GalleryStatus) => void;
  visible: boolean;
  dirty: boolean;
  width: number;
  height: number;
  view: View;
};
const initialView = (): View => ({ yaw: 0.24, pitch: 0.06, distance: 65 });

/** One WebGL context and player geometry for every gallery card. Each output
 * canvas receives a freshly rendered camera view, including during dragging. */
export class SkinGalleryRenderer {
  private viewer: SkinViewer;
  private output = new OutputPass();
  private entries = new Map<string, Entry>();
  private canvases = new Map<HTMLCanvasElement, Entry>();
  private textures = new Map<string, Texture>();
  private pending = new Map<string, Promise<Texture | null>>();
  private images = new Set<HTMLImageElement>();
  private views = new Map<string, View>();
  private resize: ResizeObserver;
  private intersection: IntersectionObserver;
  private frame: number | null = null;
  private disposed = false;
  private active = "";
  private idle = true;
  private suspended = false;
  private reduced = false;
  private focused = document.hasFocus();
  private ratio = Math.min(1.5, window.devicePixelRatio);
  constructor() {
    this.viewer = new SkinViewer({
      width: 220,
      height: 250,
      pixelRatio: this.ratio,
      renderPaused: true,
      enableControls: false,
      fov: 38,
      zoom: 0.9,
    });
    this.viewer.composer.addPass(this.output);
    this.viewer.globalLight.intensity = 0.3;
    this.viewer.cameraLight.intensity = 0.35;
    const key = new DirectionalLight(0xffeddc, 1.15),
      rim = new DirectionalLight(0xb9d5ff, 0.8);
    key.position.set(-25, 35, 30);
    rim.position.set(25, 10, -25);
    this.viewer.scene.add(
      new HemisphereLight(0xddeaff, 0x242a36, 0.8),
      key,
      rim,
    );
    this.resize = new ResizeObserver((records) => {
      for (const record of records) {
        const e = this.canvases.get(record.target as HTMLCanvasElement);
        if (e) {
          e.width = Math.max(1, Math.round(record.contentRect.width));
          e.height = Math.max(1, Math.round(record.contentRect.height));
          e.dirty = true;
        }
      }
      this.wake();
    });
    this.intersection = new IntersectionObserver(
      (records) => {
        for (const record of records) {
          const e = this.canvases.get(record.target as HTMLCanvasElement);
          if (e) {
            e.visible = record.isIntersecting;
            if (e.visible) {
              e.dirty = true;
              this.ensureTexture(e);
            }
          }
        }
        this.wake();
      },
      { root: document.querySelector(".page-content"), rootMargin: "40px" },
    );
    document.addEventListener("visibilitychange", this.visibility);
    window.addEventListener("focus", this.focus);
    window.addEventListener("blur", this.blur);
  }
  configure(
    idle: boolean,
    suspended: boolean,
    reduced: boolean,
    lowPower: boolean,
  ) {
    this.idle = idle && !lowPower;
    this.suspended = suspended;
    this.reduced = reduced;
    const ratio = lowPower ? 1 : Math.min(1.5, window.devicePixelRatio);
    if (ratio !== this.ratio) {
      this.ratio = ratio;
      this.viewer.renderer.setPixelRatio(ratio);
      for (const e of this.entries.values()) e.dirty = true;
    }
    const active = this.entries.get(this.active);
    if (active) active.dirty = true;
    this.cancel();
    this.wake();
  }
  register(
    id: string,
    canvas: HTMLCanvasElement,
    source: string,
    model: Entry["model"],
    status: Entry["status"],
  ) {
    const entry: Entry = {
      id,
      canvas,
      source,
      model,
      status,
      visible: false,
      dirty: true,
      width: 220,
      height: 250,
      view: this.views.get(id) || initialView(),
    };
    this.entries.set(id, entry);
    this.canvases.set(canvas, entry);
    this.resize.observe(canvas);
    this.intersection.observe(canvas);
    status("loading");
    return () => {
      if (this.entries.get(id) !== entry) return;
      this.views.set(id, { ...entry.view });
      this.resize.unobserve(canvas);
      this.intersection.unobserve(canvas);
      this.canvases.delete(canvas);
      this.entries.delete(id);
      if (this.active === id) this.active = "";
      this.prune();
    };
  }
  update(id: string, source: string, model: Entry["model"]) {
    const e = this.entries.get(id);
    if (!e) return;
    const changed = e.source !== source;
    e.source = source;
    e.model = model;
    e.dirty = true;
    if (changed) e.status("loading");
    if (e.visible) this.ensureTexture(e);
    this.wake();
  }
  activate(id: string) {
    const previous = this.entries.get(this.active);
    if (previous) previous.dirty = true;
    this.active = id;
    this.wake();
  }
  rotate(id: string, x: number, y: number) {
    const e = this.entries.get(id);
    if (!e) return;
    e.view.yaw = (e.view.yaw + x * 0.012) % (Math.PI * 2);
    e.view.pitch = Math.max(-0.6, Math.min(0.6, e.view.pitch + y * 0.01));
    e.dirty = true;
    this.wake();
  }
  zoom(id: string, factor: number) {
    const e = this.entries.get(id);
    if (!e) return;
    e.view.distance = Math.max(48, Math.min(110, e.view.distance * factor));
    e.dirty = true;
    this.wake();
  }
  reset(id: string) {
    const e = this.entries.get(id);
    if (!e) return;
    e.view = initialView();
    e.dirty = true;
    this.wake();
  }
  private canAnimate() {
    return (
      this.idle &&
      !this.suspended &&
      !this.reduced &&
      this.focused &&
      !document.hidden
    );
  }
  private ensureTexture(e: Entry) {
    if (this.textures.has(e.source)) {
      e.status("ready");
      return;
    }
    const source = e.source;
    if (!this.pending.has(source)) {
      const promise = new Promise<Texture | null>((resolve) => {
        const image = new Image();
        this.images.add(image);
        image.onload = () => {
          this.images.delete(image);
          if (this.disposed) {
            resolve(null);
            return;
          }
          try {
            // The installed loader normalizes both legacy 64x32 and modern 64x64 skins.
            this.viewer.loadSkin(image, { model: "default" });
            const normalized = this.viewer.playerObject.skin.map!
              .image as HTMLCanvasElement;
            const copy = document.createElement("canvas");
            copy.width = normalized.width;
            copy.height = normalized.height;
            const context = copy.getContext("2d");
            if (!context) throw Error("No canvas output");
            context.drawImage(normalized, 0, 0);
            const texture = new CanvasTexture(copy);
            texture.magFilter = texture.minFilter = NearestFilter;
            texture.colorSpace = SRGBColorSpace;
            this.textures.set(source, texture);
            resolve(texture);
          } catch {
            resolve(null);
          }
        };
        image.onerror = () => {
          this.images.delete(image);
          resolve(null);
        };
        image.src = source;
      });
      this.pending.set(source, promise);
      void promise.then(() => {
        this.pending.delete(source);
        this.prune();
      });
    }
    void this.pending.get(source)!.then((texture) => {
      if (this.disposed || this.entries.get(e.id) !== e || e.source !== source)
        return;
      e.status(texture ? "ready" : "error");
      e.dirty = Boolean(texture);
      this.wake();
    });
  }
  private prune() {
    for (const [source, texture] of this.textures) {
      if (this.textures.size <= 16) break;
      if (
        [...this.entries.values()].some((e) => e.visible && e.source === source)
      )
        continue;
      texture.dispose();
      this.textures.delete(source);
    }
  }
  private wake() {
    if (!this.disposed && !document.hidden && this.frame === null)
      this.frame = requestAnimationFrame(this.draw);
  }
  private cancel() {
    if (this.frame !== null) cancelAnimationFrame(this.frame);
    this.frame = null;
  }
  private visibility = () => {
    this.cancel();
    if (!document.hidden) this.wake();
  };
  private focus = () => {
    this.focused = true;
    this.wake();
  };
  private blur = () => {
    this.focused = false;
    this.cancel();
  };
  private draw = (time: number) => {
    this.frame = null;
    if (this.disposed || document.hidden) return;
    const animate = this.canAnimate();
    let continuous = false;
    for (const e of this.entries.values()) {
      if (!e.visible) continue;
      const animated = animate && this.active === e.id;
      if (animated) continuous = true;
      if (!e.dirty && !animated) continue;
      const texture = this.textures.get(e.source);
      if (!texture) continue;
      try {
        const v = this.viewer;
        if (v.width !== e.width || v.height !== e.height)
          v.setSize(e.width, e.height);
        v.playerObject.resetJoints();
        v.playerObject.skin.map = texture;
        v.playerObject.skin.modelType = e.model;
        v.playerObject.skin.visible = true;
        if (animated) {
          v.playerObject.skin.leftArm.rotation.z =
            0.03 + Math.sin(time * 0.0012) * 0.02;
          v.playerObject.skin.rightArm.rotation.z =
            -0.03 - Math.sin(time * 0.0012) * 0.02;
        }
        const { yaw, pitch, distance } = e.view;
        v.camera.position.set(
          Math.sin(yaw) * Math.cos(pitch) * distance,
          Math.sin(pitch) * distance,
          Math.cos(yaw) * Math.cos(pitch) * distance,
        );
        v.camera.lookAt(0, 0, 0);
        v.render();
        const w = Math.round(e.width * this.ratio),
          h = Math.round(e.height * this.ratio);
        if (e.canvas.width !== w || e.canvas.height !== h) {
          e.canvas.width = w;
          e.canvas.height = h;
        }
        const context = e.canvas.getContext("2d");
        if (!context) throw Error("No gallery canvas");
        context.clearRect(0, 0, w, h);
        context.drawImage(v.canvas, 0, 0, w, h);
        e.dirty = false;
      } catch {
        e.status("error");
        e.dirty = false;
        continuous = false;
      }
    }
    if (continuous) this.wake();
  };
  dispose() {
    if (this.disposed) return;
    this.disposed = true;
    this.cancel();
    this.resize.disconnect();
    this.intersection.disconnect();
    document.removeEventListener("visibilitychange", this.visibility);
    window.removeEventListener("focus", this.focus);
    window.removeEventListener("blur", this.blur);
    for (const image of this.images) {
      image.onload = image.onerror = null;
      image.src = "";
    }
    this.images.clear();
    for (const texture of this.textures.values()) texture.dispose();
    this.textures.clear();
    this.pending.clear();
    this.entries.clear();
    this.canvases.clear();
    this.views.clear();
    this.output.dispose();
    releaseSkinViewer(this.viewer);
  }
}
