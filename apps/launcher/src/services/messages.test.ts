import { describe, expect, it } from "vitest";
import { errorText, messageText, presentMessage } from "./messages";

describe("launcher operation messages", () => {
  it("retains the native failure and OS details when settings cannot be saved", () => {
    const raw =
      "Settings were not saved: Write temporary file: Zugriff verweigert. (os error 5)";
    const result = presentMessage(new Error(raw), "de");
    expect(result.text).toBe("Die Einstellungen wurden nicht gespeichert.");
    expect(result.details).toBe(raw);
    expect(messageText(raw, "en")).toBe(raw);
  });

  it("explains rollback and incompatible dependencies without reporting success", () => {
    const raw =
      "Mod installation failed and was rolled back: Modrinth declares an incompatibility with an installed mod";
    const result = presentMessage(raw, "de");
    expect(result.text).toContain("fehlgeschlagen und wurde zurückgesetzt");
    expect(result.text).toContain("bereits installierten Mod unvereinbar");
    expect(result.details).toBe(raw);
    expect(
      messageText(
        "INCOMPATIBLE_MOD_VERSION: A required dependency is incompatible with this Minecraft version.",
        "de",
      ),
    ).toContain("Version des Zielprofils");
  });

  it("gives different Java guidance for a missing executable and the wrong major version", () => {
    expect(
      messageText(
        "INVALID_JAVA_RUNTIME: This Minecraft version requires Java 25. Select that major version in Performance.",
        "de",
      ),
    ).toContain("benötigt Java 25");
    expect(
      messageText(
        "INVALID_JAVA_RUNTIME: Java not found. Set a Java executable in Performance.: No such file (os error 2)",
        "de",
      ),
    ).toContain("Java wurde nicht gefunden");
  });

  it("does not equate a running process with a confirmed successful game start", () => {
    const process =
      "Minecraft process Some(1204) is running. Startup is not confirmed yet.";
    expect(messageText(process, "de")).toContain("noch nicht bestätigt");
    expect(
      messageText(
        "Client Core confirmed the main menu; stable for 45 seconds. Recovery point protected.",
        "de",
      ),
    ).toContain("45 Sekunden stabil");
    expect(
      messageText(
        "Minecraft exited normally after 61 seconds (isolated Safe Mode).",
        "de",
      ),
    ).toBe(
      "Minecraft wurde nach 61 Sekunden regulär beendet (getrennter abgesicherter Start).",
    );
  });

  it("translates a network context and keeps the original HTTP failure available", () => {
    const raw =
      "Modrinth API error: HTTP status client error (429 Too Many Requests) for url (https://api.modrinth.com/v2/search)";
    expect(presentMessage(raw, "de")).toEqual({
      text: "Modrinth hat die Anfrage abgelehnt. Versuche es später erneut.",
      details: raw,
    });
    expect(
      messageText("Download checksum mismatch; file rejected", "de"),
    ).toContain("wurde verworfen");
  });

  it("preserves unknown service messages, filenames and selected English text verbatim", () => {
    for (const raw of [
      "Custom provider: cannot open C:\\Mods\\A:B.jar",
      "toString",
      "constructor: provider detail",
    ]) {
      expect(presentMessage(raw, "de")).toEqual({ text: raw, details: "" });
    }
    expect(messageText("Reading official Minecraft metadata", "en")).toBe(
      "Reading official Minecraft metadata",
    );
    expect(errorText({ message: "A service rejected this project" })).toBe(
      "A service rejected this project",
    );
  });

  it("bounds nested context translation and keeps the complete diagnostic", () => {
    const raw = "Settings were not saved: ".repeat(200) + "Disk full";
    expect(presentMessage(raw, "de").details).toBe(raw);
    expect(messageText(raw, "de").length).toBeLessThan(300);
    expect(messageText(null, "de")).toBe(
      "Ein unbekannter Fehler ist aufgetreten.",
    );
  });
});
