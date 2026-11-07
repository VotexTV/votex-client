import {
  stateSchema,
  type AppState,
} from "../../../../packages/shared-config/schema";

/** Validates immutable UI updates before passing a value to React's render queue. */
export class StateDraft {
  constructor(public current: AppState) {}
  apply(update: (state: AppState) => AppState) {
    const next = stateSchema.parse(update(this.current));
    this.current = next;
    return next;
  }
}

/** A game-option refresh must not discard an unrelated pending design edit. */
export function reconcileState(
  remote: AppState,
  local: AppState,
  baseline: AppState,
): AppState {
  const merged = { ...remote };
  for (const key of Object.keys(local) as (keyof AppState)[]) {
    if (JSON.stringify(local[key]) !== JSON.stringify(baseline[key]))
      Object.assign(merged, { [key]: local[key] });
  }
  return stateSchema.parse(merged);
}
