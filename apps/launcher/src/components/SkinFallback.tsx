import { useEffect, useRef } from "react";

/** An honest 2D fallback when the host cannot create a WebGL context. */
export function SkinFallback({ skin }: { skin?: string }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const image = new Image();
    let active = true;
    image.onload = () => {
      if (!active || !canvas.current) return;
      const g = canvas.current.getContext("2d");
      if (!g) return;
      g.imageSmoothingEnabled = false;
      g.clearRect(0, 0, 16, 32);
      for (const [sx, sy, w, h, x, y] of [
        [8, 8, 8, 8, 4, 0],
        [20, 20, 8, 12, 4, 8],
        [44, 20, 4, 12, 0, 8],
        [36, 52, 4, 12, 12, 8],
        [4, 20, 4, 12, 4, 20],
        [20, 52, 4, 12, 8, 20],
        [40, 8, 8, 8, 4, 0],
        [20, 36, 8, 12, 4, 8],
        [44, 36, 4, 12, 0, 8],
        [52, 52, 4, 12, 12, 8],
        [4, 36, 4, 12, 4, 20],
        [4, 52, 4, 12, 8, 20],
      ])
        g.drawImage(image, sx, sy, w, h, x, y, w, h);
    };
    image.src = skin || "/assets/votex-default-skin.png";
    return () => {
      active = false;
      image.onload = null;
    };
  }, [skin]);
  return (
    <div className="skin-fallback">
      <span>Votex Client</span>
      <small>2D-Vorschau · 3D ist hier nicht verfügbar</small>
      <canvas
        ref={canvas}
        width={16}
        height={32}
        aria-label="2D-Vorschau deines Minecraft-Skins"
      />
    </div>
  );
}
