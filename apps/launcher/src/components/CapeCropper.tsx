import { useEffect, useRef, useState } from "react";
import { GlassButton, Modal } from "./Glass";
import { RangeField } from "./RangeField";
import { readData } from "../services/desktop";
export function CapeCropper({
  onSave,
  onClose,
  act,
}: {
  onSave: (png: string) => void;
  onClose: () => void;
  act: <T>(fn: () => Promise<T>) => Promise<T | undefined>;
}) {
  const [source, setSource] = useState<HTMLImageElement>();
  const [zoom, setZoom] = useState(100);
  const [x, setX] = useState(50);
  const [y, setY] = useState(50);
  const [texture, setTexture] = useState(false);
  const canvas = useRef<HTMLCanvasElement>(null);
  const draw = (context: CanvasRenderingContext2D, w: number, h: number) => {
    if (!source) return;
    const scale = (Math.max(w / source.width, h / source.height) * zoom) / 100;
    const sw = w / scale,
      sh = h / scale;
    context.drawImage(
      source,
      ((source.width - sw) * x) / 100,
      ((source.height - sh) * y) / 100,
      sw,
      sh,
      0,
      0,
      w,
      h,
    );
  };
  useEffect(() => {
    const c = canvas.current;
    if (!c || !source) return;
    const ctx = c.getContext("2d")!;
    ctx.clearRect(0, 0, c.width, c.height);
    ctx.imageSmoothingEnabled = true;
    if (texture) ctx.drawImage(source, 1, 1, 10, 16, 0, 0, c.width, c.height);
    else draw(ctx, c.width, c.height);
  }, [source, zoom, x, y, texture]);
  const save = () => {
    if (!source) return;
    const out = document.createElement("canvas");
    out.width = 64;
    out.height = 32;
    const c = out.getContext("2d")!;
    c.imageSmoothingEnabled = true;
    if (texture) c.drawImage(source, 0, 0, 64, 32);
    else {
      const front = document.createElement("canvas");
      front.width = 10;
      front.height = 16;
      draw(front.getContext("2d")!, 10, 16);
      c.drawImage(front, 1, 1);
      c.drawImage(front, 12, 1);
      c.drawImage(front, 0, 0, 1, 16, 0, 1, 1, 16);
      c.drawImage(front, 9, 0, 1, 16, 11, 1, 1, 16);
      c.drawImage(front, 0, 0, 10, 1, 1, 0, 10, 1);
      c.drawImage(front, 0, 15, 10, 1, 11, 0, 10, 1);
    }
    onSave(out.toDataURL("image/png"));
    onClose();
  };
  return (
    <Modal title="Dein eigenes Cape" onClose={onClose}>
      <p className="muted">
        Lade ein Bild und wähle den Ausschnitt. Votex erstellt daraus eine 64 ×
        32 Cape-Textur. Vorder- und Rückseite verwenden dein Motiv.
      </p>
      <div className="cape-crop-layout">
        <div className="cape-crop-stage">
          <canvas
            width={200}
            height={320}
            ref={canvas}
            aria-label="Vorschau des Cape-Ausschnitts"
          />
          {!source && <span>DEIN MOTIV</span>}
        </div>
        <div className="form-stack">
          <label className="btn file-btn">
            Bild auswählen
            <input
              type="file"
              accept="image/png,image/jpeg,image/webp"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f)
                  void act(async () => {
                    if (
                      !["image/png", "image/jpeg", "image/webp"].includes(
                        f.type,
                      )
                    )
                      throw Error(
                        "Bitte ein PNG-, JPEG- oder WebP-Bild wählen.",
                      );
                    const data = await readData(f, 6_000_000);
                    const img = new Image();
                    await new Promise<void>((resolve, reject) => {
                      img.onload = () => resolve();
                      img.onerror = () =>
                        reject(Error("Das Bild ist nicht lesbar."));
                      img.src = data;
                    });
                    if (
                      img.width > 8192 ||
                      img.height > 8192 ||
                      img.width < 10 ||
                      img.height < 16
                    )
                      throw Error(
                        "Bildgröße: mindestens 10 × 16 und höchstens 8192 × 8192 Pixel.",
                      );
                    setSource(img);
                    setZoom(100);
                    setX(50);
                    setY(50);
                    setTexture(img.width === 64 && img.height === 32);
                  });
                e.currentTarget.value = "";
              }}
            />
          </label>
          {source && (
            <>
              {source.width === 64 && source.height === 32 && (
                <label className="check-row">
                  <input
                    type="checkbox"
                    checked={texture}
                    onChange={(e) => setTexture(e.target.checked)}
                  />{" "}
                  Fertige Cape-Textur direkt verwenden
                </label>
              )}
              {!texture && (
                <>
                  <RangeField
                    label="Vergrößerung"
                    min={100}
                    max={400}
                    value={zoom}
                    onChange={setZoom}
                  />
                  <RangeField
                    label="Ausschnitt horizontal"
                    value={x}
                    onChange={setX}
                  />
                  <RangeField
                    label="Ausschnitt vertikal"
                    value={y}
                    onChange={setY}
                  />
                </>
              )}
              <small>Das Bild bleibt lokal auf deinem PC.</small>
            </>
          )}
        </div>
      </div>
      <GlassButton className="primary" disabled={!source} onClick={save}>
        Cape übernehmen
      </GlassButton>
    </Modal>
  );
}
