import { beforeEach, describe, expect, it } from "vitest";
import {
  beginInstallation,
  dismissInstallation,
  finishInstallation,
  installationFor,
  installationJob,
  installationSnapshot,
  updateInstallation,
} from "./installations";
beforeEach(() => {
  for (const row of installationSnapshot()) {
    finishInstallation(row.id, "error", "cleanup");
    dismissInstallation(row.id);
  }
});
describe("installation lifecycle", () => {
  it("does not turn intermediate or rejected operations into successful installations", () => {
    const id = beginInstallation("Mods");
    installationJob({
      phase: "idle",
      message: "Native idle",
      completed: 0,
      total: 0,
    });
    expect(installationSnapshot()[0].state).toBe("working");
    finishInstallation(id, "error", "Checksum mismatch");
    finishInstallation(id, "success", "late callback");
    expect(installationSnapshot()[0].state).toBe("error");
  });
  it("keeps unknown totals indeterminate and reports real per-step progress", () => {
    const id = beginInstallation("Shader");
    installationJob({
      phase: "mods",
      message: "Download",
      completed: 2,
      total: 8,
    });
    expect(installationSnapshot()[0].progress).toBe(0.25);
    updateInstallation(id, "Checking", NaN);
    expect(installationSnapshot()[0].progress).toBeUndefined();
    finishInstallation(id, "warning", "One optional package unavailable");
    expect(installationSnapshot()[0].state).toBe("warning");
  });
  it("waits for background game install to finish and survives navigation without dismissing work", () => {
    const id = beginInstallation("Minecraft", true);
    dismissInstallation(id);
    expect(installationSnapshot()).toHaveLength(1);
    installationJob({
      phase: "installing",
      message: "Files",
      completed: 1,
      total: 3,
    });
    expect(installationSnapshot()[0].state).toBe("working");
    installationJob({
      phase: "installed",
      message: "Verified",
      completed: 3,
      total: 3,
    });
    expect(installationSnapshot()[0].state).toBe("success");
  });
  it("covers all existing installation IPC entrypoints without celebrating reads", () => {
    for (const command of [
      "install_content",
      "install_mod",
      "install_core",
      "import_modpack",
      "prepare_profile",
    ])
      expect(installationFor(command, {})).toBeTruthy();
    expect(installationFor("run_game", { action: "install" })).toBeTruthy();
    expect(installationFor("installed_mods", {})).toBeUndefined();
    expect(installationFor("run_game", { action: "launch" })).toBeUndefined();
  });
});
