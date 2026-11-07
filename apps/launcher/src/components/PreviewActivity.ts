import { createContext } from "react";

// Shared by every viewer, including galleries and dialogs, so a running game
// does not leave another mounted preview animating behind the current page.
export const PreviewSuspendedContext = createContext(false);

// Skinview3d schedules its next RAF *after* calling the animation callback.
// Pausing inside that callback cancels only the already-running RAF. Defer until
// draw() returns, then cancel the newly scheduled frame if it is still idle.
export function pauseAfterDraw(
  viewer: { renderPaused: boolean },
  stillIdle: () => boolean,
) {
  queueMicrotask(() => {
    if (stillIdle()) viewer.renderPaused = true;
  });
}
