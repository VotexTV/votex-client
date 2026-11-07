import { useEffect, useRef, useState, type RefObject } from "react";
export function ScrollRail({
  target,
}: {
  target: RefObject<HTMLElement | null>;
}) {
  const rail = useRef<HTMLDivElement>(null),
    [metrics, setMetrics] = useState({ ratio: 1, position: 0 }),
    [active, setActive] = useState(false);
  useEffect(() => {
    const el = target.current;
    if (!el) return;
    let timer: ReturnType<typeof setTimeout>;
    const update = () => {
      setMetrics({
        ratio: Math.min(1, el.clientHeight / Math.max(1, el.scrollHeight)),
        position: el.scrollTop / Math.max(1, el.scrollHeight - el.clientHeight),
      });
    };
    const scroll = () => {
      update();
      setActive(true);
      clearTimeout(timer);
      timer = setTimeout(() => setActive(false), 750);
    };
    const resize = new ResizeObserver(update);
    resize.observe(el);
    if (el.firstElementChild) resize.observe(el.firstElementChild);
    el.addEventListener("scroll", scroll, { passive: true });
    update();
    return () => {
      clearTimeout(timer);
      resize.disconnect();
      el.removeEventListener("scroll", scroll);
    };
  }, [target]);
  if (metrics.ratio >= 0.999) return null;
  const seek = (y: number) => {
    const el = target.current,
      bounds = rail.current?.getBoundingClientRect();
    if (!el || !bounds) return;
    el.scrollTop =
      Math.max(0, Math.min(1, (y - bounds.top) / bounds.height)) *
      (el.scrollHeight - el.clientHeight);
  };
  return (
    <div
      ref={rail}
      className={`liquid-scroll-rail ${active ? "active" : ""}`}
      role="scrollbar"
      aria-label="Modliste scrollen"
      aria-controls="page-scroll"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(metrics.position * 100)}
      aria-orientation="vertical"
      tabIndex={0}
      onPointerDown={(e) => {
        e.currentTarget.setPointerCapture(e.pointerId);
        seek(e.clientY);
        setActive(true);
      }}
      onPointerMove={(e) => {
        if (e.buttons === 1 && e.currentTarget.hasPointerCapture(e.pointerId))
          seek(e.clientY);
      }}
      onPointerUp={(e) => {
        if (e.currentTarget.hasPointerCapture(e.pointerId))
          e.currentTarget.releasePointerCapture(e.pointerId);
      }}
      onKeyDown={(e) => {
        const el = target.current;
        if (!el) return;
        if (
          [
            "ArrowDown",
            "ArrowUp",
            "PageDown",
            "PageUp",
            "Home",
            "End",
          ].includes(e.key)
        ) {
          e.preventDefault();
          if (e.key === "Home") el.scrollTop = 0;
          else if (e.key === "End") el.scrollTop = el.scrollHeight;
          else
            el.scrollTop +=
              (e.key === "ArrowUp" || e.key === "PageUp" ? -1 : 1) *
              (e.key.startsWith("Page") ? el.clientHeight * 0.8 : 60);
        }
      }}
    >
      <span
        style={{
          height: `${Math.max(5, metrics.ratio * 100)}%`,
          top: `${metrics.position * (100 - Math.max(5, metrics.ratio * 100))}%`,
        }}
      />
    </div>
  );
}
