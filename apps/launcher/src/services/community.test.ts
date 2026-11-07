import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("./desktop", () => ({ call: vi.fn() }));
import { call } from "./desktop";
import { saveCapeVisibility } from "./community";

const invoke = vi.mocked(call);
const cape = { id: "", name: "  Mein Flügelcape  ", image: "data:image/png;base64,fixture", visibility: "public" as const };

describe("cape publication commits", () => {
  beforeEach(() => invoke.mockReset());

  it("keeps a private local cape offline and allows later publication", async () => {
    expect(await saveCapeVisibility({ ...cape, visibility: "private" })).toEqual({
      id: "", visibility: "private", published: false, shareUrl: "",
    });
    expect(invoke).not.toHaveBeenCalled();
    const accepted = { id: "owned-cape", visibility: "public", published: true };
    invoke.mockResolvedValueOnce(accepted);
    expect(await saveCapeVisibility(cape)).toEqual(accepted);
    expect(invoke).toHaveBeenCalledWith("community_publish", {
      kind: "cape", name: "Mein Flügelcape", data: { image: cape.image }, visibility: "public", published: true,
    });
  });

  it("does not return a successful state before the upload is acknowledged", async () => {
    let reject!: (reason: Error) => void;
    invoke.mockImplementationOnce(() => new Promise((_, fail) => { reject = fail; }));
    let committed = false;
    const pending = saveCapeVisibility(cape).then(() => { committed = true; });
    await Promise.resolve();
    expect(committed).toBe(false);
    reject(new Error("Account approval unavailable"));
    await expect(pending).rejects.toThrow("Account approval unavailable");
    expect(committed).toBe(false);
  });

  it("changes a remote cape to private and republishes the same item", async () => {
    for (const visibility of ["private", "unlisted", "public"] as const) {
      const accepted = { id: "owned-cape", visibility, published: visibility === "public", shareUrl: visibility === "unlisted" ? "https://example.test/new-link" : "" };
      invoke.mockResolvedValueOnce(accepted);
      expect(await saveCapeVisibility({ ...cape, id: "owned-cape", visibility })).toEqual(accepted);
      expect(invoke).toHaveBeenLastCalledWith("community_visibility", { id: "owned-cape", visibility, published: visibility === "public" });
    }
    expect(invoke).not.toHaveBeenCalledWith("community_publish", expect.anything());
  });

  it("never marks an empty local cape as published", async () => {
    expect(await saveCapeVisibility({ ...cape, image: "" })).toMatchObject({ id: "", published: false });
    expect(invoke).not.toHaveBeenCalled();
  });
});
