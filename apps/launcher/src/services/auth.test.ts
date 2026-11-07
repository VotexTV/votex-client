import { describe, expect, it } from "vitest";
import { authMessage, codeTime } from "./auth";

describe("Microsoft sign-in guidance", () => {
  it("distinguishes missing Minecraft ownership from missing publisher approval", () => {
    expect(authMessage("MINECRAFT_NOT_OWNED: upstream", true)).toContain(
      "Java-Berechtigung",
    );
    expect(authMessage("MINECRAFT_APP_NOT_APPROVED: upstream", true)).toContain(
      "Herausgeber",
    );
    expect(authMessage("LOGIN_EXPIRED: upstream", true)).toContain("erneut");
  });
  it("translates known network failures and keeps English messages", () => {
    expect(authMessage(new Error("Network unavailable"), true)).toBe(
      "Die Netzwerkverbindung ist nicht verfügbar. Prüfe deine Verbindung und versuche es erneut.",
    );
    expect(authMessage("LOGIN_DECLINED: Sign-in declined", false)).toBe(
      "LOGIN_DECLINED: Sign-in declined",
    );
  });
  it("displays a bounded code countdown", () => {
    expect(codeTime(900)).toBe("15:00");
    expect(codeTime(61)).toBe("1:01");
    expect(codeTime(-2)).toBe("0:00");
  });
});
