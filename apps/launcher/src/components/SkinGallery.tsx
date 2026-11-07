import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useReducedMotion } from "motion/react";
import { ThemeContext } from "./Glass";
import { PreviewSuspendedContext } from "./PreviewActivity";
import { SkinGalleryRenderer, type GalleryStatus } from "./SkinGalleryRenderer";
import {checkSkinFile,skinPngBlob} from "../services/skins";
import {Download} from "lucide-react";
const GalleryContext = createContext<{
  renderer: SkinGalleryRenderer | null;
  failed: boolean;
}>({ renderer: null, failed: false });

export function SkinPngDownload({source,name}:{source:string;name:string}) {
  const [href,setHref]=useState(""),[error,setError]=useState(false);
  useEffect(()=>{
    let cancelled=false,url="";setError(false);setHref("");
    void (async()=>{try{if(!source.startsWith("data:")){setHref(source);return;}const blob=skinPngBlob(source);await checkSkinFile(blob);if(cancelled)return;url=URL.createObjectURL(blob);setHref(url);}catch{if(!cancelled)setError(true);}})();
    return()=>{cancelled=true;if(url)URL.revokeObjectURL(url);};
  },[source]);
  return href?<a className="btn" href={href} download={`${name.replace(/[^\p{L}\p{N}._ -]/gu,"").slice(0,60)||"Skin"}.png`}><Download size={14}/>PNG exportieren</a>:<button className="btn" disabled>{error?"Skin-PNG nicht lesbar":"Export wird vorbereitet …"}</button>;
}

export function SkinGalleryProvider({
  idle,
  children,
}: {
  idle: boolean;
  children: ReactNode;
}) {
  const [renderer, setRenderer] = useState<SkinGalleryRenderer | null>(null),
    [failed, setFailed] = useState(false);
  const theme = useContext(ThemeContext),
    suspended = useContext(PreviewSuspendedContext),
    reduced = useReducedMotion();
  useEffect(() => {
    let instance: SkinGalleryRenderer;
    try {
      instance = new SkinGalleryRenderer();
      setRenderer(instance);
    } catch {
      setFailed(true);
      return;
    }
    return () => instance.dispose();
  }, []);
  useEffect(
    () =>
      renderer?.configure(
        idle,
        suspended,
        Boolean(reduced || theme.reducedMotion),
        theme.renderProfile === "ultra-performance",
      ),
    [
      renderer,
      idle,
      suspended,
      reduced,
      theme.reducedMotion,
      theme.renderProfile,
    ],
  );
  return (
    <GalleryContext.Provider value={{ renderer, failed }}>
      {children}
    </GalleryContext.Provider>
  );
}
export function SkinCardPreview({
  id,
  source,
  model,
  name,
  onOpen,
}: {
  id: string;
  source: string;
  model: "default" | "slim";
  name: string;
  onOpen: () => void;
}) {
  const { renderer, failed } = useContext(GalleryContext),
    canvas = useRef<HTMLCanvasElement>(null);
  const [status, setStatus] = useState<GalleryStatus>("loading");
  const drag = useRef<{
    x: number;
    y: number;
    lastX: number;
    lastY: number;
    moved: boolean;
  } | null>(null);
  useEffect(() => {
    if (!canvas.current || !renderer) return;
    return renderer.register(id, canvas.current, source, model, setStatus);
  }, [renderer, id]);
  useEffect(
    () => renderer?.update(id, source, model),
    [renderer, id, source, model],
  );
  useEffect(() => {
    const element = canvas.current;
    if (!element || !renderer) return;
    const wheel = (e: WheelEvent) => {
      if (!e.ctrlKey) return;
      e.preventDefault();
      renderer.zoom(
        id,
        Math.exp(Math.max(-100, Math.min(100, e.deltaY)) * 0.002),
      );
    };
    element.addEventListener("wheel", wheel, { passive: false });
    return () => element.removeEventListener("wheel", wheel);
  }, [renderer, id]);
  return (
    <div
      className="skin-card-viewport"
      data-gallery-state={failed ? "error" : status}
    >
      <canvas
        ref={canvas}
        tabIndex={0}
        role="img"
        aria-label={`${name}: interaktive 3D-Vorschau`}
        aria-describedby="skin-gallery-help"
        onPointerEnter={() => renderer?.activate(id)}
        onPointerLeave={() => {
          if (document.activeElement !== canvas.current && !drag.current)
            renderer?.activate("");
        }}
        onFocus={() => renderer?.activate(id)}
        onBlur={() => renderer?.activate("")}
        onPointerDown={(e) => {
          if (e.button !== 0) return;
          e.currentTarget.focus();
          e.currentTarget.setPointerCapture(e.pointerId);
          drag.current = {
            x: e.clientX,
            y: e.clientY,
            lastX: e.clientX,
            lastY: e.clientY,
            moved: false,
          };
        }}
        onPointerMove={(e) => {
          const d = drag.current;
          if (!d) return;
          if (Math.hypot(e.clientX - d.x, e.clientY - d.y) > 5) d.moved = true;
          if (d.moved)
            renderer?.rotate(id, e.clientX - d.lastX, e.clientY - d.lastY);
          d.lastX = e.clientX;
          d.lastY = e.clientY;
        }}
        onPointerUp={(e) => {
          const d = drag.current;
          drag.current = null;
          if (e.currentTarget.hasPointerCapture(e.pointerId))
            e.currentTarget.releasePointerCapture(e.pointerId);
          if (d && !d.moved) onOpen();
        }}
        onPointerCancel={() => {
          drag.current = null;
        }}
        onKeyDown={(e) => {
          let handled = true;
          switch (e.key) {
            case "Enter":
            case " ":
              onOpen();
              break;
            case "ArrowLeft":
              renderer?.rotate(id, -12, 0);
              break;
            case "ArrowRight":
              renderer?.rotate(id, 12, 0);
              break;
            case "ArrowUp":
              renderer?.rotate(id, 0, -8);
              break;
            case "ArrowDown":
              renderer?.rotate(id, 0, 8);
              break;
            case "+":
            case "=":
              renderer?.zoom(id, 0.9);
              break;
            case "-":
              renderer?.zoom(id, 1.1);
              break;
            case "Home":
              renderer?.reset(id);
              break;
            default:
              handled = false;
          }
          if (handled) e.preventDefault();
        }}
      />
      {failed || status === "error" ? (
        <span className="skin-card-feedback">
          3D-Vorschau nicht verfügbar. Du kannst den Skin weiterhin bearbeiten.
        </span>
      ) : status === "loading" ? (
        <span className="skin-card-feedback" role="status">
          3D-Vorschau wird geladen …
        </span>
      ) : null}
    </div>
  );
}
