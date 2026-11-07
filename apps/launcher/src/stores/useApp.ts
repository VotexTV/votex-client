import { useCallback, useEffect, useRef, useState } from "react";
import {
  stateSchema,
  initialState,
  type AppState,
} from "../../../../packages/shared-config/schema";
import { call, native } from "../services/desktop";
import { errorText } from "../services/messages";
import { migrateState } from "../../../../packages/shared-config/migrate";
import { StateDraft, reconcileState } from "./StateDraft";
import { ZodError } from "zod";
export function useApp(onError: (s: string) => void) {
  const [state, setState] = useState<AppState>(initialState);
  const draft = useRef(new StateDraft(initialState));
  const [ready, setReady] = useState(false);
  const [saved, setSaved] = useState(true);
  const [loadError, setLoadError] = useState("");
  const locked = useRef(false);
  const last = useRef("");
  const queue = useRef(Promise.resolve());
  const pending = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const generation = useRef(0);
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const raw = native
          ? await call<unknown>("load_state")
          : JSON.parse(localStorage.getItem("votex-preview-v1") ?? "null");
        const next = migrateState(raw);
        if (!cancelled) {
          draft.current.current = next;
          setState(next);
          last.current = raw ? JSON.stringify(raw) : "";
          setSaved(JSON.stringify(next) === last.current);
        }
      } catch (e) {
        if (cancelled) return;
        locked.current = true;
        setLoadError(errorText(e));
        onError(
          `Settings could not be loaded. The original file was preserved: ${errorText(e)}`,
        );
      } finally {
        if (!cancelled) setReady(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);
  useEffect(() => {
    if (!ready || locked.current) return;
    const serialized = JSON.stringify(state);
    if (serialized === last.current) {
      setSaved(true);
      return;
    }
    setSaved(false);
    const current = ++generation.current;
    const timer = setTimeout(() => {
      queue.current = queue.current.then(async () => {
        if (generation.current !== current) return;
        try {
          stateSchema.parse(state);
          if (native) await call("save_state", { value: state });
          else localStorage.setItem("votex-preview-v1", serialized);
          last.current = serialized;
          setSaved(serialized === JSON.stringify(draft.current.current));
        } catch (e) {
          onError(`Settings were not saved: ${errorText(e)}`);
        }
      });
    }, 300);
    pending.current = timer;
    return () => clearTimeout(timer);
  }, [ready, state]);
  const update = useCallback((fn: (s: AppState) => AppState) => {
    if (locked.current) {
      onError(
        "Settings are protected after a load error. Repair the original file before editing.",
      );
      return false;
    }
    try {
      const next = draft.current.apply(fn);
      setSaved(false);
      setState(next);
      return true;
    } catch (e) {
      onError(
        e instanceof ZodError
          ? `Settings were not updated: ${errorText(e)}`
          : errorText(e),
      );
      return false;
    }
  }, []);
  useEffect(() => {
    if (native || !ready || locked.current) return;
    const save = () =>
      localStorage.setItem("votex-preview-v1", JSON.stringify(state));
    window.addEventListener("beforeunload", save);
    return () => window.removeEventListener("beforeunload", save);
  }, [ready, state]);
  const reload = useCallback(async () => {
    if (native) {
      clearTimeout(pending.current);
      generation.current++;
      await queue.current;
      const raw = await call<unknown>("load_state");
      if (raw) {
        const remote = migrateState(raw);
        const next = reconcileState(
          remote,
          draft.current.current,
          migrateState(last.current ? JSON.parse(last.current) : null),
        );
        draft.current.current = next;
        last.current = JSON.stringify(remote);
        setState(next);
      }
    }
  }, []);
  const flush = useCallback(async () => {
    if (locked.current)
      throw new Error("Settings are protected after a load error");
    clearTimeout(pending.current);
    generation.current++;
    await queue.current;
    const snapshot = draft.current.current;
    if (native) await call("save_state", { value: snapshot });
    else localStorage.setItem("votex-preview-v1", JSON.stringify(snapshot));
    last.current = JSON.stringify(snapshot);
    setSaved(last.current === JSON.stringify(draft.current.current));
  }, []);
  return { state, update, ready, saved, reload, flush, loadError };
}
