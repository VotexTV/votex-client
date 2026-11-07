import {
  createContext,
  useContext,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
  type HTMLAttributes,
  type ButtonHTMLAttributes,
} from "react";
import { motion, useReducedMotion } from "motion/react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";
import { interactionOrigin } from "./UITransitions";
import {
  baseTheme,
  type Theme,
} from "../../../../packages/shared-config/schema";
export const ThemeContext = createContext<Theme>(baseTheme);
export function GlassPanel({
  children,
  className = "",
  ...props
}: HTMLAttributes<HTMLElement>) {
  return (
    <section
      {...props}
      className={`glass ${/profile-card|mini-preview/.test(className) ? "luminous-card" : ""} ${className}`}
    >
      {children}
    </section>
  );
}
export function GlassButton({
  children,
  className = "",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement>) {
  const theme = useContext(ThemeContext);
  const reduced = useReducedMotion() || theme.reducedMotion;
  return (
    <motion.div
      className={`button-wrap ${props.disabled ? "disabled" : ""}`}
      whileTap={
        !reduced && !props.disabled && theme.wobbleEffect && theme.wobble > 0
          ? { scale: 1 - theme.wobble / 2500 }
          : undefined
      }
      transition={{
        type: "spring",
        stiffness: 100 + theme.stiffness * 5,
        damping: 10 + theme.damping * 0.4,
      }}
    >
      <button {...props} className={`btn luminous-card ${className}`}>
        {children}
      </button>
    </motion.div>
  );
}
export function Toggle({
  value,
  onChange,
  label,
  disabled = false,
}: {
  value: boolean;
  onChange: (v: boolean) => void;
  label: string;
  disabled?: boolean;
}) {
  return (
    <label className="toggle-row">
      <span>{label}</span>
      <button
        disabled={disabled}
        className={`toggle ${value ? "on" : ""}`}
        role="switch"
        aria-checked={value}
        aria-label={label}
        onClick={() => onChange(!value)}
      >
        <i />
      </button>
    </label>
  );
}
export function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLElement>(null);
  const modalTheme = useContext(ThemeContext);
  const reduced = useReducedMotion() || modalTheme.reducedMotion;
  const [leaving, setLeaving] = useState(false);
  const opening = useRef<Animation | null>(null);
  const originFrame = useRef({ transform: "translate3d(0,18px,0) scale(.94)" });
  const closeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const close = () => {
    if (leaving) return;
    setLeaving(true);
    const el = dialog.current;
    if (el && !reduced) {
      const transform = getComputedStyle(el).transform;
      opening.current?.cancel();
      el.animate(
        [
          { transform, opacity: 1, filter: "blur(0px)" },
          { ...originFrame.current, opacity: 0, filter: "blur(5px)" },
        ],
        { duration: 220, easing: "cubic-bezier(.4,0,.8,.3)", fill: "forwards" },
      );
    }
    closeTimer.current = setTimeout(onClose, reduced ? 0 : 220);
  };
  useEffect(
    () => () => {
      if (closeTimer.current) clearTimeout(closeTimer.current);
    },
    [],
  );
  useLayoutEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const element = dialog.current;
    if (element && !reduced) {
      const bounds = element.getBoundingClientRect(),
        trigger =
          interactionOrigin() ??
          (previous && previous !== document.body
            ? previous.getBoundingClientRect()
            : null),
        scale = modalTheme.scale / 100;
      const dx = trigger
          ? (trigger.left +
              trigger.width / 2 -
              bounds.left -
              bounds.width / 2) /
            scale
          : 0,
        dy = trigger
          ? (trigger.top +
              trigger.height / 2 -
              bounds.top -
              bounds.height / 2) /
            scale
          : 18;
      const sx = trigger
          ? Math.max(0.12, Math.min(0.7, trigger.width / bounds.width))
          : 0.94,
        sy = trigger
          ? Math.max(0.08, Math.min(0.6, trigger.height / bounds.height))
          : 0.94;
      originFrame.current = {
        transform: `translate3d(${dx}px,${dy}px,0) scale(${sx},${sy})`,
      };
      opening.current = element.animate(
        [
          {
            ...originFrame.current,
            opacity: 0,
            filter: "blur(7px)",
            borderRadius: "30px",
            offset: 0,
          },
          {
            transform: "translate3d(0,0,0) scale(1.012,1.008)",
            opacity: 1,
            filter: "blur(0px)",
            offset: 0.78,
          },
          {
            transform: "translate3d(0,0,0) scale(1)",
            opacity: 1,
            filter: "blur(0px)",
            offset: 1,
          },
        ],
        {
          duration: Math.max(360, 700 - modalTheme.speed * 3),
          easing: "cubic-bezier(.2,.8,.2,1)",
        },
      );
      Array.from(element.children)
        .filter((child) => child.tagName !== "HEADER")
        .forEach((child, index) =>
          child.animate(
            [
              { opacity: 0, transform: "translateY(10px)" },
              { opacity: 1, transform: "translateY(0px)" },
            ],
            {
              duration: 300,
              delay: 100 + Math.min(index, 5) * 32,
              easing: "cubic-bezier(.2,.8,.2,1)",
              fill: "backwards",
            },
          ),
        );
    }
    (
      dialog.current?.querySelector<HTMLElement>(
        "input:not(:disabled),textarea:not(:disabled),select:not(:disabled)",
      ) ?? dialog.current?.querySelector<HTMLElement>("button:not(:disabled)")
    )?.focus();
    return () => {
      opening.current?.cancel();
      previous?.focus({ preventScroll: true });
    };
  }, []);
  return createPortal(
    <div
      className={`modal-backdrop ${reduced ? "motion-off" : ""} ${leaving ? "leaving" : ""}`}
      onClick={close}
    >
      <section
        ref={dialog}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="glass modal"
        onClick={(e) => e.stopPropagation()}
        onKeyDown={(e) => {
          if (e.key === "Escape") {
            e.stopPropagation();
            close();
          }
          if (e.key === "Tab") {
            const focusable = Array.from(
              dialog.current?.querySelectorAll<HTMLElement>(
                'button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href],summary,[tabindex="0"]',
              ) ?? [],
            ).filter((el) => el.getClientRects().length > 0);
            const first = focusable[0],
              last = focusable[focusable.length - 1];
            if (e.shiftKey && document.activeElement === first) {
              e.preventDefault();
              last?.focus();
            } else if (!e.shiftKey && document.activeElement === last) {
              e.preventDefault();
              first?.focus();
            }
          }
        }}
      >
        <header>
          <h2>{title}</h2>
          <button
            className="icon-button"
            onClick={close}
            aria-label="Dialog schließen"
          >
            <X size={20} />
          </button>
        </header>
        {children}
      </section>
    </div>,
    document.querySelector(".app-shell") ?? document.body,
  );
}
export function PageHeading({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <span className="eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {action}
    </div>
  );
}
export function Empty({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <div className="empty">
      <h3>{title}</h3>
      <p>{children}</p>
    </div>
  );
}
