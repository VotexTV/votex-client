import type { CSSProperties } from "react";
export function RangeField({
  label,
  value,
  onChange,
  min = 0,
  max = 100,
  step = 1,
  unit = "%",
  hint,
  disabled = false,
  displayValue,
}: {
  label: string;
  disabled?: boolean;
  displayValue?: string;
  value: number;
  onChange: (v: number) => void;
  min?: number;
  max?: number;
  step?: number;
  unit?: string;
  hint?: string;
}) {
  return (
    <label className="range-field" data-disabled={disabled}>
      <span>
        <strong>{label}</strong>
        <output>
          {displayValue ?? `${Math.round(value * 100) / 100}${unit}`}
        </output>
      </span>
      <input
        type="range"
        disabled={disabled}
        aria-label={label}
        min={min}
        max={max}
        step={step}
        value={value}
        style={
          {
            "--range-fill": `${((value - min) / (max - min)) * 100}%`,
          } as CSSProperties
        }
        onChange={(e) => onChange(Number(e.target.value))}
      />
      {hint && <small>{hint}</small>}
    </label>
  );
}
