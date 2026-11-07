import { useEffect, useRef, useState, type PointerEvent } from "react";
import { Check, Eraser, Pipette, Redo2, Undo2 } from "lucide-react";
import { GlassButton } from "./Glass";

type Tool = "paint" | "erase" | "pick";
const palette = [
  "#111827",
  "#ffffff",
  "#3478ff",
  "#45d8ff",
  "#cfb5ff",
  "#ff6b85",
  "#ffbd59",
  "#62d896",
];

export function SkinPixelEditor({
  source,
  onDraft,
  onSave,
}: {
  source: string;
  onDraft?: (data: string) => void;
  onSave: (data: string) => void;
}) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const undo = useRef<ImageData[]>([]);
  const redo = useRef<ImageData[]>([]);
  const drawing = useRef(false);
  const previous = useRef<{ x: number; y: number } | null>(null);
  const [ready, setReady] = useState(false);
  const [size, setSize] = useState({ width: 64, height: 64 });
  const [tool, setTool] = useState<Tool>("paint");
  const [color, setColor] = useState("#3478ff");
  const [history, setHistory] = useState({ undo: 0, redo: 0 });
  const [notice, setNotice] = useState("");

  useEffect(() => {
    let cancelled = false;
    const image = new Image();
    image.onload = () => {
      if (cancelled || !canvas.current) return;
      const width = image.naturalWidth,
        height = image.naturalHeight;
      if (width !== 64 || (height !== 64 && height !== 32)) {
        setNotice("Der Skin muss 64 × 64 oder 64 × 32 Pixel groß sein.");
        return;
      }
      const context = canvas.current.getContext("2d", { willReadFrequently: true });
      if (!context) {
        setNotice("Der Pixel-Editor konnte keine Zeichenfläche anlegen.");
        return;
      }
      canvas.current.width = width;
      canvas.current.height = height;
      context.imageSmoothingEnabled = false;
      context.clearRect(0, 0, width, height);
      context.drawImage(image, 0, 0);
      undo.current = [];
      redo.current = [];
      setHistory({ undo: 0, redo: 0 });
      setSize({ width, height });
      setReady(true);
      setNotice("");
    };
    image.onerror = () => setNotice("Das Skin-Bild konnte nicht geladen werden.");
    image.src = source;
    return () => {
      cancelled = true;
      image.onload = null;
      image.onerror = null;
    };
  }, [source]);

  const context = () => canvas.current?.getContext("2d", { willReadFrequently: true });
  const snapshot = () => {
    const ctx = context(),
      surface = canvas.current;
    if (!ctx || !surface) return;
    undo.current = [...undo.current.slice(-39), ctx.getImageData(0, 0, surface.width, surface.height)];
    redo.current = [];
    setHistory({ undo: undo.current.length, redo: 0 });
  };
  const pixelAt = (event: PointerEvent<HTMLCanvasElement>) => {
    const bounds = event.currentTarget.getBoundingClientRect();
    return {
      x: Math.max(0, Math.min(size.width - 1, Math.floor(((event.clientX - bounds.left) / bounds.width) * size.width))),
      y: Math.max(0, Math.min(size.height - 1, Math.floor(((event.clientY - bounds.top) / bounds.height) * size.height))),
    };
  };
  const paint = (x: number, y: number) => {
    const ctx = context();
    if (!ctx) return;
    if (tool === "erase") ctx.clearRect(x, y, 1, 1);
    else if (tool === "paint") {
      ctx.fillStyle = color;
      ctx.fillRect(x, y, 1, 1);
    }
  };
  const onPointerDown = (event: PointerEvent<HTMLCanvasElement>) => {
    if (!ready || event.button !== 0) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    const point = pixelAt(event);
    if (tool === "pick") {
      const sample = context()?.getImageData(point.x, point.y, 1, 1).data;
      if (sample && sample[3] > 0)
        setColor(`#${[sample[0], sample[1], sample[2]].map((value) => value.toString(16).padStart(2, "0")).join("")}`);
      return;
    }
    snapshot();
    drawing.current = true;
    previous.current = point;
    paint(point.x, point.y);
  };
  const onPointerMove = (event: PointerEvent<HTMLCanvasElement>) => {
    if (!drawing.current) return;
    const current = pixelAt(event),
      start = previous.current ?? current;
    let x = start.x,
      y = start.y;
    const dx = Math.abs(current.x - x),
      sx = x < current.x ? 1 : -1,
      dy = -Math.abs(current.y - y),
      sy = y < current.y ? 1 : -1;
    let error = dx + dy;
    for (;;) {
      paint(x, y);
      if (x === current.x && y === current.y) break;
      const twice = error * 2;
      if (twice >= dy) { error += dy; x += sx; }
      if (twice <= dx) { error += dx; y += sy; }
    }
    previous.current = current;
  };
  const endStroke = () => {
    const changed = drawing.current;
    drawing.current = false;
    previous.current = null;
    if (changed && canvas.current) onDraft?.(canvas.current.toDataURL("image/png"));
  };
  const restore = (from: ImageData[], to: ImageData[]) => {
    const surface = canvas.current,
      ctx = context(),
      frame = from.pop();
    if (!surface || !ctx || !frame) return;
    to.push(ctx.getImageData(0, 0, surface.width, surface.height));
    ctx.putImageData(frame, 0, 0);
    setHistory({ undo: undo.current.length, redo: redo.current.length });
    onDraft?.(surface.toDataURL("image/png"));
  };
  const save = () => {
    const surface = canvas.current;
    if (!surface) return;
    const data = surface.toDataURL("image/png");
    if (data.length > 1_400_000) {
      setNotice("Der bearbeitete Skin ist zu groß zum Speichern.");
      return;
    }
    onSave(data);
    setNotice("Skin-Textur gespeichert.");
  };

  return (
    <section className="skin-pixel-editor" aria-label="Skin-Pixel-Editor">
      <div className="skin-pixel-toolbar">
        <button type="button" className={`btn ${tool === "paint" ? "primary" : ""}`} aria-pressed={tool === "paint"} onClick={() => setTool("paint")}>Pinsel</button>
        <button type="button" className={`btn ${tool === "erase" ? "primary" : ""}`} aria-pressed={tool === "erase"} onClick={() => setTool("erase")}><Eraser size={14}/> Radierer</button>
        <button type="button" className={`btn ${tool === "pick" ? "primary" : ""}`} aria-pressed={tool === "pick"} onClick={() => setTool("pick")}><Pipette size={14}/> Farbe aufnehmen</button>
        <button type="button" className="icon-button" aria-label="Letzten Pinselstrich rückgängig" disabled={!history.undo} onClick={() => restore(undo.current, redo.current)}><Undo2 size={15}/></button>
        <button type="button" className="icon-button" aria-label="Pinselstrich wiederholen" disabled={!history.redo} onClick={() => restore(redo.current, undo.current)}><Redo2 size={15}/></button>
      </div>
      <div className="skin-pixel-canvas-frame">
        <canvas
          ref={canvas}
          aria-label={`Minecraft-Skin-Textur ${size.width} mal ${size.height} Pixel`}
          className={ready ? "ready" : ""}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={endStroke}
          onPointerCancel={endStroke}
          onLostPointerCapture={endStroke}
        />
        {!ready && <span>{notice || "Skin-Textur wird geladen …"}</span>}
      </div>
      <div className="skin-pixel-colors">
        {palette.map((swatch) => <button key={swatch} type="button" className={color.toLowerCase() === swatch ? "selected" : ""} aria-label={`Farbe ${swatch}`} style={{ background: swatch }} onClick={() => { setColor(swatch); setTool("paint"); }}/>) }
        <label className="skin-pixel-current-color">Eigene Farbe<input aria-label="Eigene Mal-Farbe" type="color" value={color} onChange={(event) => { setColor(event.target.value); setTool("paint"); }}/></label>
        <span>{size.width} × {size.height} Pixel · jede Zelle ist ein Minecraft-Pixel</span>
      </div>
      {notice && ready && <small className="skin-pixel-notice" role="status">{notice}</small>}
      <GlassButton className="primary" disabled={!ready} onClick={save}><Check size={14}/> Änderungen speichern</GlassButton>
    </section>
  );
}
