import { useEffect } from "react";
import type { Theme } from "../../../../packages/shared-config/schema";
import {
  advanceMagneticState,
  effectActivity,
  interactionResponse,
  type MagneticState,
} from "../../../../packages/shared-config/visuals";

type MagneticMotion = {
  state: MagneticState;
  targetX: number;
  targetY: number;
  lastFrame: number;
};

const magneticCards =
  ".quick-card,.cosmetic-product,.choice-card,.news-card";

// One event-driven coordinator. It animates only while the pointer moves or a card settles.
export function useLuminous(theme: Theme, page: string, reduced: boolean) {
  useEffect(() => {
    const policy = effectActivity(theme);
    if (!policy.proximity || reduced) return;
    let frame = 0;
    let x = 0;
    let y = 0;
    let dirty = true;
    let pointerPresent = false;
    let bounds: { el: HTMLElement; r: DOMRect }[] = [];
    const active = new Set<HTMLElement>();
    const magnets = new Map<HTMLElement, MagneticMotion>();
    const registered = new Set<HTMLElement>();

    const schedule = () => {
      if (!frame) frame = requestAnimationFrame(draw);
    };
    const clear = (immediate = false) => {
      for (const el of active) el.style.setProperty("--near", "0");
      active.clear();
      for (const [el, motion] of magnets) {
        motion.targetX = 0;
        motion.targetY = 0;
        if (immediate) {
          el.style.setProperty("--magnet-x", "0px");
          el.style.setProperty("--magnet-y", "0px");
        }
      }
      if (immediate) {
        if (frame) cancelAnimationFrame(frame);
        frame = 0;
        magnets.clear();
      } else if (magnets.size) {
        schedule();
      }
    };
    function draw(now: number) {
      frame = 0;
      if (document.hidden) {
        pointerPresent = false;
        clear(true);
        return;
      }
      if (dirty) {
        bounds = Array.from(
          document.querySelectorAll<HTMLElement>(
            "main .glass,main .luminous-card,.account-button,.modal .luminous-card,.select-trigger,button:not(:disabled)",
          ),
        )
          .map((el) => ({ el, r: el.getBoundingClientRect() }))
          .filter(
            ({ r }) => r.bottom > 0 && r.top < innerHeight && r.width > 0,
          );
        dirty = false;
        for (const el of registered) if (!el.isConnected) { el.classList.remove("luminous-target"); registered.delete(el); }
        for (const { el } of bounds) if (el.tagName === "BUTTON") { el.classList.add("luminous-target"); registered.add(el); }
      }
      const near = pointerPresent
        ? bounds
            .map((item) => ({
              ...item,
              d: Math.hypot(
                Math.max(item.r.left - x, 0, x - item.r.right),
                Math.max(item.r.top - y, 0, y - item.r.bottom),
              ),
            }))
            .filter((item) => item.d < policy.radius)
            .sort((a, b) => a.d - b.d)
            .slice(0, policy.maxCards)
        : [];
      const nearElements = new Set(near.map((item) => item.el));
      for (const el of active) el.style.setProperty("--near", "0");
      active.clear();

      for (const [el, motion] of magnets) {
        if (!nearElements.has(el)) {
          motion.targetX = 0;
          motion.targetY = 0;
        }
      }
      for (const { el, r, d } of near) {
        const influence = 1 - d / policy.radius;
        el.style.setProperty("--near", String(influence));
        el.style.setProperty(
          "--light-x",
          `${(x - r.left) / (theme.scale / 100)}px`,
        );
        el.style.setProperty(
          "--light-y",
          `${(y - r.top) / (theme.scale / 100)}px`,
        );
        el.style.setProperty(
          "--tilt-x",
          `${Math.max(-1, Math.min(1, (y - r.top) / r.height - 0.5))}`,
        );
        el.style.setProperty(
          "--tilt-y",
          `${Math.max(-1, Math.min(1, (x - r.left) / r.width - 0.5))}`,
        );
        active.add(el);
        if (el.matches(magneticCards)) {
          let motion = magnets.get(el);
          if (!motion) {
            motion = {
              state: { x: 0, y: 0, velocityX: 0, velocityY: 0 },
              targetX: 0,
              targetY: 0,
              lastFrame: now,
            };
            magnets.set(el, motion);
          }
          const dx = Math.max(
            -1,
            Math.min(
              1,
              (x - (r.left + r.width / 2)) / Math.max(1, r.width / 2),
            ),
          );
          const dy = Math.max(
            -1,
            Math.min(
              1,
              (y - (r.top + r.height / 2)) / Math.max(1, r.height / 2),
            ),
          );
          const maximum = 4 * influence * (theme.proximity / 100);
          motion.targetX = dx * maximum;
          motion.targetY = dy * maximum;
        }
      }

      let settling = false;
      for (const [el, motion] of magnets) {
        const deltaSeconds = Math.min((now - motion.lastFrame) / 1000, 0.05);
        motion.lastFrame = now;
        motion.state = advanceMagneticState(
          motion.state,
          { x: motion.targetX, y: motion.targetY },
          deltaSeconds,
          4,
          interactionResponse(theme),
        );
        el.style.setProperty("--magnet-x", `${motion.state.x}px`);
        el.style.setProperty("--magnet-y", `${motion.state.y}px`);
        const settled =
          Math.abs(motion.targetX - motion.state.x) < 0.015 &&
          Math.abs(motion.targetY - motion.state.y) < 0.015 &&
          Math.abs(motion.state.velocityX) < 0.015 &&
          Math.abs(motion.state.velocityY) < 0.015;
        if (settled && motion.targetX === 0 && motion.targetY === 0) {
          el.style.setProperty("--magnet-x", "0px");
          el.style.setProperty("--magnet-y", "0px");
          magnets.delete(el);
        } else if (!settled) {
          settling = true;
        }
      }
      if (settling) schedule();
    }

    const move = (event: PointerEvent) => {
      x = event.clientX;
      y = event.clientY;
      pointerPresent = true;
      schedule();
    };
    const leave = () => {
      pointerPresent = false;
      clear();
    };
    const invalidate = () => {
      dirty = true;
      clear();
      if (pointerPresent) schedule();
    };
    const hidden = () => {
      if (document.hidden) {
        pointerPresent = false;
        clear(true);
      }
    };
    document.addEventListener("pointermove", move, { passive: true });
    document.addEventListener("scroll", invalidate, true);
    window.addEventListener("resize", invalidate);
    document.addEventListener("pointerleave", leave);
    document.addEventListener("visibilitychange", hidden);
    const mutation = new MutationObserver(invalidate);
    const main = document.querySelector("main");
    if (main) mutation.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ["disabled", "hidden", "aria-expanded"] });
    return () => {
      clear(true);
      for (const el of registered) { el.classList.remove("luminous-target"); for (const key of ["--near","--light-x","--light-y","--tilt-x","--tilt-y"]) el.style.removeProperty(key); }
      mutation.disconnect();
      document.removeEventListener("pointermove", move);
      document.removeEventListener("scroll", invalidate, true);
      window.removeEventListener("resize", invalidate);
      document.removeEventListener("pointerleave", leave);
      document.removeEventListener("visibilitychange", hidden);
    };
  }, [
    theme.proximity,
    theme.proximityEffect,
    theme.speed,
    theme.stiffness,
    theme.damping,
    theme.renderProfile,
    theme.hover,
    theme.reducedMotion,
    theme.scale,
    page,
    reduced,
  ]);
}
