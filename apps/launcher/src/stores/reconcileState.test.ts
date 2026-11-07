import { it, expect } from "vitest";
import { initialState } from "../../../../packages/shared-config/schema";
import { reconcileState } from "./StateDraft";
it("keeps a pending personal design while accepting saved in-game FPS and FOV", () => {
  const base = structuredClone(initialState),
    local = structuredClone(base),
    remote = structuredClone(base);
  local.themes[0].radius = 24;
  remote.profiles[0].fov = 90;
  remote.profiles[0].maxFps = 165;
  const result = reconcileState(remote, local, base);
  expect(result.themes[0].radius).toBe(24);
  expect(result.profiles[0]).toMatchObject({ fov: 90, maxFps: 165 });
  expect(base.themes[0].radius).not.toBe(24);
  expect(base.profiles[0].maxFps).toBe(144);
});
