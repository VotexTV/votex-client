import { useEffect } from "react";
let origin: {
  left: number;
  top: number;
  width: number;
  height: number;
  time: number;
} | null = null;
export function interactionOrigin() {
  return origin && performance.now() - origin.time < 1600 ? origin : null;
}
export function useUITransitions(reduced: boolean, page: string) {
  useEffect(() => {
    const running = new Map<
      HTMLDetailsElement,
      { animation: Animation; open: boolean }
    >();
    const expand = (event: MouseEvent) => {
      const summary = (event.target as Element)?.closest("summary");
      const details = summary?.parentElement;
      if (!(details instanceof HTMLDetailsElement) || reduced || !summary)
        return;
      event.preventDefault();
      const next = !(running.get(details)?.open ?? details.open),
        start = details.offsetHeight;
      running.get(details)?.animation.cancel();
      details.open = true;
      const css = getComputedStyle(details),
        closed =
          (summary as HTMLElement).offsetHeight +
          parseFloat(css.paddingTop) +
          parseFloat(css.paddingBottom) +
          parseFloat(css.borderTopWidth) +
          parseFloat(css.borderBottomWidth);
      const full = details.offsetHeight;
      details.style.overflow = "hidden";
      const animation = details.animate(
        [{ height: `${start}px` }, { height: `${next ? full : closed}px` }],
        { duration: next ? 420 : 260, easing: "cubic-bezier(.2,.8,.2,1)" },
      );
      running.set(details, { animation, open: next });
      animation.onfinish = () => {
        details.open = next;
        details.style.overflow = "";
        running.delete(details);
      };
      if (next)
        Array.from(details.children)
          .filter((e) => e !== summary)
          .forEach((el, i) =>
            el.animate(
              [
                { opacity: 0, transform: "translateY(-6px)" },
                { opacity: 1, transform: "translateY(0)" },
              ],
              {
                duration: 300,
                delay: Math.min(i, 6) * 25,
                easing: "ease-out",
                fill: "backwards",
              },
            ),
          );
    };
    document.addEventListener("click", expand, true);
    return () => {
      document.removeEventListener("click", expand, true);
      running.forEach(({ animation, open }, el) => {
        animation.cancel();
        el.open = open;
        el.style.overflow = "";
      });
    };
  }, [reduced]);
  useEffect(() => {
    const capture = (event: PointerEvent) => {
      const target = (event.target as Element)?.closest<HTMLElement>(
        "button,a,summary,[role=button]",
      );
      if (target) {
        const r = target.getBoundingClientRect();
        origin = {
          left: r.left,
          top: r.top,
          width: r.width,
          height: r.height,
          time: performance.now(),
        };
      }
    };
    document.addEventListener("pointerdown", capture, true);
    return () => document.removeEventListener("pointerdown", capture, true);
  }, []);
  useEffect(() => {
    const root = document.querySelector(".app-shell");
    if (!root) return;
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            (entry.target as HTMLElement).dataset.reveal = "shown";
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.04 },
    );
    const register = (element: Element) => {
      const items = [
        ...(element.matches(".mod-card,.installed-row") ? [element] : []),
        ...element.querySelectorAll(".mod-card,.installed-row"),
      ];
      items.forEach((item, index) => {
        const node = item as HTMLElement;
        if (node.dataset.reveal) return;
        node.dataset.reveal = reduced ? "shown" : "waiting";
        node.style.setProperty("--reveal-delay", `${(index % 4) * 28}ms`);
        if (!reduced) observer.observe(node);
      });
    };
    register(root);
    const mutations = new MutationObserver((records) =>
      records.forEach((record) =>
        record.addedNodes.forEach((node) => {
          if (node instanceof Element) register(node);
        }),
      ),
    );
    mutations.observe(root, { childList: true, subtree: true });
    return () => {
      observer.disconnect();
      mutations.disconnect();
      root.querySelectorAll<HTMLElement>("[data-reveal]").forEach((node) => {
        delete node.dataset.reveal;
      });
    };
  }, [reduced, page]);
}
