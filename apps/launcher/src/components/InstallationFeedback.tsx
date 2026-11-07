import { useContext, useEffect, useSyncExternalStore } from "react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { ArrowDownToLine, Check, CircleAlert, X } from "lucide-react";
import { ThemeContext } from "./Glass";
import {
  dismissInstallation,
  installationSnapshot,
  subscribeInstallations,
  type Installation,
} from "../services/installations";
function InstallCard({ item }: { item: Installation }) {
  const theme = useContext(ThemeContext),
    reduced =
      useReducedMotion() ||
      theme.reducedMotion ||
      theme.renderProfile === "ultra-performance";
  const working = item.state === "working";
  useEffect(() => {
    if (working || item.state === "error") return;
    const timer = setTimeout(
      () => dismissInstallation(item.id),
      item.state === "warning" ? 12_000 : 7_000,
    );
    return () => clearTimeout(timer);
  }, [item.id, item.state, working]);
  return (
    <motion.section
      className={`installation-card ${item.state}`}
      data-reduced={!!reduced}
      initial={reduced ? false : { opacity: 0, y: 22, scale: 0.96 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: reduced ? 0 : 10 }}
      transition={{ type: "spring", stiffness: 340, damping: 28 }}
      aria-label={item.title}
    >
      <div className="installation-emblem" aria-hidden="true">
        <svg className="installation-orbit" viewBox="0 0 48 48">
          <circle cx="24" cy="24" r="21" />
        </svg>
        {working ? (
          <ArrowDownToLine size={20} />
        ) : item.state === "success" ? (
          <Check className="installation-check" size={23} />
        ) : (
          <CircleAlert size={22} />
        )}
      </div>
      <div className="installation-copy">
        <strong>
          {working
            ? item.title
            : item.state === "success"
              ? "Bereit!"
              : item.state === "warning"
                ? "Mit Hinweis abgeschlossen"
                : "Installation fehlgeschlagen"}
        </strong>
        <p>{item.detail}</p>
        <div
          className={`installation-track ${working && item.progress === undefined ? "indeterminate" : ""}`}
          role="progressbar"
          aria-label={item.title}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={
            item.progress === undefined
              ? undefined
              : Math.round(item.progress * 100)
          }
        >
          <span
            style={{
              width:
                item.progress === undefined ? "32%" : `${item.progress * 100}%`,
            }}
          />
        </div>
        {working && item.progress !== undefined && (
          <small>{Math.round(item.progress * 100)} % · aktueller Schritt</small>
        )}
      </div>
      {!working && (
        <button
          className="icon-button installation-dismiss"
          aria-label="Installationsmeldung schließen"
          onClick={() => dismissInstallation(item.id)}
        >
          <X size={15} />
        </button>
      )}
    </motion.section>
  );
}
export function InstallationFeedback() {
  const items = useSyncExternalStore(
    subscribeInstallations,
    installationSnapshot,
  );
  return (
    <div
      className="installation-feedback"
      role="status"
      aria-live="polite"
      aria-atomic="false"
    >
      <AnimatePresence>
        {items.map((item) => (
          <InstallCard key={item.id} item={item} />
        ))}
      </AnimatePresence>
    </div>
  );
}
