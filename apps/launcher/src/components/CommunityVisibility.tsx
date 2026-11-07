import { useState } from "react";
import { Check, Copy, Link2 } from "lucide-react";
import type { CommunityVisibility as Visibility } from "../../../../packages/shared-config/schema";

const choices: Array<{ value: Visibility; label: string; detail: string }> = [
  { value: "public", label: "Öffentlich", detail: "In der Community-Suche" },
  { value: "unlisted", label: "Nicht gelistet", detail: "Nur mit Freigabelink" },
  { value: "private", label: "Privat", detail: "Nur für dich" },
];

export function CommunityVisibilityField({
  label = "Sichtbarkeit",
  value,
  onChange,
}: {
  label?: string;
  value: Visibility;
  onChange: (value: Visibility) => void;
}) {
  return (
    <fieldset className="community-visibility-field">
      <legend>{label}</legend>
      <div className="community-visibility" role="group" aria-label={label}>
        {choices.map((choice) => (
          <button
            key={choice.value}
            type="button"
            className={value === choice.value ? "active" : ""}
            aria-pressed={value === choice.value}
            onClick={() => onChange(choice.value)}
          >
            <strong>{choice.label}</strong>
            <small>{choice.detail}</small>
          </button>
        ))}
      </div>
    </fieldset>
  );
}

export function CommunityShareLink({ value }: { value: string }) {
  const [status, setStatus] = useState("");

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setStatus("Freigabelink kopiert.");
    } catch {
      setStatus("Link markieren und mit Strg+C kopieren.");
    }
  };

  return (
    <div className="community-share-link">
      <label>
        <Link2 size={14} />
        <span>Freigabelink</span>
        <input
          readOnly
          value={value}
          aria-label="Freigabelink"
          onFocus={(event) => event.currentTarget.select()}
        />
      </label>
      <button type="button" className="btn" onClick={() => void copy()}>
        {status === "Freigabelink kopiert." ? <Check size={14} /> : <Copy size={14} />}
        {status === "Freigabelink kopiert." ? "Kopiert" : "Link kopieren"}
      </button>
      {status && <small role="status">{status}</small>}
    </div>
  );
}
