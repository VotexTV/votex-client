import { beforeEach, describe, expect, it, vi } from "vitest";
const ipc = vi.hoisted(() => ({ invoke: vi.fn() }));
vi.mock("@tauri-apps/api/core", () => ({
  isTauri: () => true,
  invoke: ipc.invoke,
}));
import { call } from "./desktop";
import {
  dismissInstallation,
  finishInstallation,
  installationJob,
  installationSnapshot,
} from "./installations";
beforeEach(() => {
  ipc.invoke.mockReset();
  for (const row of installationSnapshot()) {
    finishInstallation(row.id, "error", "cleanup");
    dismissInstallation(row.id);
  }
});
describe("native command feedback connection", () => {
  it("only certifies installation after IPC resolves, retaining a named operation", async () => {
    let resolve!: (value: unknown) => void;
    ipc.invoke.mockImplementationOnce(
      () =>
        new Promise((r) => {
          resolve = r;
        }),
    );
    const pending = call(
      "install_content",
      { id: "qa", project: "example", kind: "shader" },
      "Example Shader installieren",
    );
    expect(installationSnapshot()[0].title).toBe("Example Shader installieren");
    expect(installationSnapshot()[0].state).toBe("working");
    resolve({ installed: true });
    await pending;
    expect(installationSnapshot()[0].state).toBe("success");
    expect(ipc.invoke).toHaveBeenCalledWith("install_content", {
      id: "qa",
      project: "example",
      kind: "shader",
    });
  });
  it("rejects with error feedback and distinguishes skipped optional packages", async () => {
    ipc.invoke.mockRejectedValueOnce(new Error("SHA512 failed"));
    await expect(call("install_mod", {})).rejects.toThrow("SHA512 failed");
    expect(installationSnapshot()[0].state).toBe("error");
    ipc.invoke
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce({ results: [{ status: "unavailable" }] });
    await call("prepare_profile", { id: "qa" });
    expect(installationSnapshot()[0].state).toBe("warning");
    expect(installationSnapshot()[0].detail).toContain("1 optionale");
    expect(installationSnapshot()[0].progress).toBe(1);
  });
  it("does not treat a background command acknowledgement as completed work", async () => {
    ipc.invoke.mockResolvedValueOnce(null);
    await call("run_game", { action: "install" });
    expect(installationSnapshot()[0].state).toBe("working");
    installationJob({
      phase: "mods-warning",
      message: "Optional package skipped",
      completed: 0,
      total: 0,
    });
    installationJob({
      phase: "installed",
      message: "Verified",
      completed: 0,
      total: 0,
    });
    expect(installationSnapshot()[0].state).toBe("warning");
    expect(installationSnapshot()[0].detail).toContain("skipped");
  });
});
