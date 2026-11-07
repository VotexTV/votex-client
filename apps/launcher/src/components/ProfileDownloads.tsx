import { useEffect, useState } from "react";
import { call, native } from "../services/desktop";
const names: Record<string, string> = {
  sodium: "Sodium",
  iris: "Iris Shaders",
  modmenu: "Mod Menu",
  zoomify: "Zoomify",
  lithium: "Lithium",
  "ferrite-core": "FerriteCore",
  immediatelyfast: "ImmediatelyFast",
  "xaeros-minimap": "Xaero’s Minimap",
  "xaeros-world-map": "Xaero’s World Map",
  appleskin: "AppleSkin",
  "cloth-config": "Cloth Config · AppleSkin-Einstellungen",
  "3dskinlayers": "3D Skin Layers",
  voicechat: "Simple Voice Chat",
  mousetweaks: "Mouse Tweaks",
  "complementary-reimagined": "Complementary Reimagined",
  "bsl-shaders": "BSL Shaders",
};
export function ProfileDownloads({
  id,
  refresh,
}: {
  id: string;
  refresh: number;
}) {
  const [rows, setRows] = useState<
      { project: string; status: string; reason?: string }[]
    >([]),
    [error, setError] = useState("");
  useEffect(() => {
    let gone = false;
    setRows([]);
    setError("");
    if (native)
      void call<{ results: typeof rows } | null>("profile_downloads", { id })
        .then((r) => {
          if (!gone) setRows(r?.results ?? []);
        })
        .catch((e) => {
          if (!gone) setError(String(e));
        });
    return () => {
      gone = true;
    };
  }, [id, refresh]);
  if (!rows.length && !error) return null;
  return (
    <details className="profile-download-report">
      <summary>
        Download-Bericht ·{" "}
        {rows.filter((r) => r.status === "unavailable").length} ausgelassen
      </summary>
      {error && <p role="alert">{error}</p>}
      <ul>
        {rows.map((row) => (
          <li key={row.project}>
            <strong>{names[row.project] ?? row.project}</strong> ·{" "}
            {row.status === "installed"
              ? "Installiert"
              : row.status === "preserved"
                ? "Bereits vorbereitet; deine Auswahl bleibt erhalten"
                : "Nicht installiert"}
            {row.reason && <p className="muted">{row.reason}</p>}
          </li>
        ))}
      </ul>
    </details>
  );
}
