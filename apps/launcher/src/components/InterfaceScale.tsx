import { useEffect, useState } from "react";
import { RangeField } from "./RangeField";

export const interfaceScales = [75, 90, 100, 125, 150] as const;
const names = ["Sehr kompakt", "Kompakt", "Standard", "Groß", "Sehr groß"];

/** Selection stays local until Apply, so a pointer never has to chase a moving track. */
export function InterfaceScale({
  value,
  onApply,
}: {
  value: number;
  onApply: (scale: number) => void;
}) {
  const [draft, setDraft] = useState(value);
  useEffect(() => setDraft(value), [value]);
  const selected = interfaceScales.indexOf(
    draft as (typeof interfaceScales)[number],
  );
  return (
    <div className="interface-scale">
      <RangeField
        label="Oberflächengröße"
        value={selected < 0 ? 3 : selected + 1}
        min={1}
        max={5}
        step={1}
        displayValue={
          selected < 0
            ? `${draft} % · Eigene Größe`
            : `Stufe ${selected + 1} · ${draft} %`
        }
        onChange={(level) => setDraft(interfaceScales[level - 1])}
      />
      <div className="scale-stops" aria-label="Größenstufen">
        {interfaceScales.map((scale, i) => (
          <button
            key={scale}
            aria-label={`Stufe ${i + 1}: ${names[i]}, ${scale} Prozent`}
            aria-pressed={draft === scale}
            onClick={() => setDraft(scale)}
          >
            {i + 1}
          </button>
        ))}
      </div>
      <p className="muted">
        {selected < 0
          ? "Deine bisherige Größe bleibt erhalten."
          : names[selected]}
        . Erst „Größe übernehmen“ verändert die Oberfläche.
      </p>
      <div className="toolbar">
        <button
          className="btn primary"
          disabled={draft === value}
          onClick={() => onApply(draft)}
        >
          Größe übernehmen
        </button>
        <button
          className="btn"
          disabled={value === 100 && draft === 100}
          onClick={() => {
            setDraft(100);
            onApply(100);
          }}
        >
          Standardgröße
        </button>
      </div>
    </div>
  );
}
