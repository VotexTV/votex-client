import { Select } from "./Select";
import type {
  AppState,
  CharacterView,
} from "../../../../packages/shared-config/schema";
import { emotes, type EmoteId } from "./emotes";
import { Toggle } from "./Glass";

export function CharacterControls({
  value,
  onChange,
  idle,
  onIdle,
  emote,
  onEmote,
  customEmotes,
  onBackground,
  language,
}: {
  value: CharacterView;
  onChange: (patch: Partial<CharacterView>) => void;
  idle: boolean;
  onIdle: (enabled: boolean) => void;
  emote: EmoteId | "";
  onEmote: (id: EmoteId | "") => void;
  customEmotes: AppState["customEmotes"];
  onBackground: () => void;
  language: "de" | "en";
}) {
  const de = language === "de";
  return (
    <details className="character-settings glass">
      <summary>
        {de ? "Charakterdarstellung" : "Character display"}{" "}
        <span>
          {value.mode === "scene"
            ? de
              ? "3D-Szene"
              : "3D scene"
            : de
              ? "Charakterstudio"
              : "Showcase"}
        </span>
      </summary>
      <div className="character-settings-body">
        <div
          className="segmented"
          role="group"
          aria-label={de ? "Darstellungsmodus" : "Display mode"}
        >
          <button
            className={value.mode === "showcase" ? "active" : ""}
            aria-pressed={value.mode === "showcase"}
            onClick={() => onChange({ mode: "showcase" })}
          >
            {de ? "Charakterstudio" : "Showcase"}
          </button>
          <button
            className={value.mode === "scene" ? "active" : ""}
            aria-pressed={value.mode === "scene"}
            onClick={() => onChange({ mode: "scene" })}
          >
            {de ? "3D-Szene" : "3D scene"}
          </button>
        </div>
        <div className="character-selects">
          {value.mode === "scene" && (
            <label>
              {de ? "Szene" : "Scene"}
              <Select
                aria-label={de ? "3D-Szene wählen" : "Choose 3D scene"}
                value={value.scene}
                onChange={(e) =>
                  onChange({ scene: e.target.value as CharacterView["scene"] })
                }
              >
                <option value="cloud-island">
                  {de ? "Wolkeninsel" : "Cloud island"}
                </option>
                <option value="studio">
                  {de ? "Lichtstudio" : "Light studio"}
                </option>
                <option value="night-garden">
                  {de ? "Nachtgarten" : "Night garden"}
                </option>
              </Select>
            </label>
          )}
          <label>
            {de ? "Beleuchtung" : "Lighting"}
            <Select
              aria-label={de ? "Charakterbeleuchtung" : "Character lighting"}
              value={value.lighting}
              onChange={(e) =>
                onChange({
                  lighting: e.target.value as CharacterView["lighting"],
                })
              }
            >
              <option value="studio">{de ? "Studiolicht" : "Studio"}</option>
              <option value="daylight">{de ? "Tageslicht" : "Daylight"}</option>
              <option value="moonlight">
                {de ? "Mondlicht" : "Moonlight"}
              </option>
            </Select>
          </label>
          <label>
            {de ? "Emote-Vorschau" : "Emote preview"}
            <Select
              aria-label={
                de ? "Emote auf der Startseite" : "Home emote preview"
              }
              value={emote}
              onChange={(e) => onEmote(e.target.value as EmoteId | "")}
            >
              <option value="">{de ? "Keine Geste" : "No emote"}</option>
              {[...emotes, ...customEmotes].map((e) => (
                <option key={e.id} value={e.id}>
                  {e.name}
                </option>
              ))}
            </Select>
          </label>
        </div>
        <div className="character-settings-actions">
          <Toggle
            label={de ? "Ruheanimation" : "Idle animation"}
            value={idle}
            onChange={onIdle}
          />
          <button className="btn" onClick={onBackground}>
            {de ? "Hintergrund anpassen" : "Customize background"}
          </button>
        </div>
        <p>
          {de
            ? "Ziehen zum Drehen · Mausrad zum Zoomen. In der 3D-Szene verschiebst du die Kamera mit der rechten Maustaste. Die Ansichtsbuttons unter dem Modell zentrieren sie wieder."
            : "Drag to orbit · scroll to zoom. In a 3D scene, drag with the right mouse button to pan. Use the view buttons below the model to recenter."}
        </p>
      </div>
    </details>
  );
}
