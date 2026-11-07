import { describe, expect, it } from "vitest";
import { readJson, readVideoData } from "./desktop";

function asFile(type: string, bytes: number[]): File {
  return new Blob([new Uint8Array(bytes)], { type }) as File;
}

describe("local video background import", () => {
  it("rejects unsupported containers and mislabeled headers", async () => {
    const validMp4Header = [
      0, 0, 0, 12, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6f, 0x6d,
    ];
    const validWebmHeader = [0x1a, 0x45, 0xdf, 0xa3, 0x93, 0x42, 0x82, 0x88];

    await expect(
      readVideoData(asFile("video/mp4", validWebmHeader)),
    ).rejects.toThrow("passt nicht zu ihrem MP4-/WebM-Format");
    await expect(
      readVideoData(asFile("video/quicktime", validMp4Header)),
    ).rejects.toThrow("MP4- und WebM-Videos");
    await expect(
      readVideoData(asFile("video/webm", [0, 1, 2, 3, 4, 5])),
    ).rejects.toThrow("passt nicht zu ihrem MP4-/WebM-Format");
  });

  it("rejects empty and oversized files before reading their contents", async () => {
    await expect(readVideoData(asFile("video/mp4", []))).rejects.toThrow(
      "5-MB-Limit oder ist leer",
    );
    await expect(
      readVideoData(
        new Blob([new Uint8Array(5_000_001)], { type: "video/mp4" }) as File,
      ),
    ).rejects.toThrow("5-MB-Limit oder ist leer");
  });
});

describe("JSON import feedback", () => {
  it("keeps the parse failure identifiable without misreporting successful import", async () => {
    await expect(readJson(new Blob(['{"themes":']) as File)).rejects.toThrow(
      "INVALID_JSON:",
    );
    expect(await readJson(new Blob(['{"name":"Test"}']) as File)).toEqual({
      name: "Test",
    });
  });
  it("rejects oversized JSON before reading it", async () => {
    const file = {
      size: 10_000_001,
      text: () => {
        throw new Error("must not read");
      },
    } as unknown as File;
    await expect(readJson(file)).rejects.toThrow("JSON file is too large");
  });
});
