import { useEffect, useState, type ComponentType } from "react";
import {
  Search,
  ArrowUpRight,
  ArrowUp,
  ArrowDown,
  CornerDownLeft,
} from "lucide-react";
import { Modal } from "./Glass";
export interface QuickAction {
  id: string;
  name: string;
  detail: string;
  group: string;
  icon: ComponentType<{ size?: number }>;
  keywords?: string;
  disabled?: boolean;
  run: () => void;
}
export function CommandPalette({
  actions,
  onClose,
}: {
  actions: QuickAction[];
  onClose: () => void;
}) {
  const [query, setQuery] = useState(""),
    [active, setActive] = useState(0);
  const tokens = query.toLocaleLowerCase().trim().split(/\s+/).filter(Boolean);
  const found = actions
    .filter((a) =>
      tokens.every((t) =>
        `${a.name} ${a.detail} ${a.keywords || ""}`
          .toLocaleLowerCase()
          .includes(t),
      ),
    )
    .slice(0, 18);
  useEffect(() => setActive(0), [query]);
  useEffect(() => {
    document
      .getElementById(`command-result-${found[active]?.id}`)
      ?.scrollIntoView({ block: "nearest" });
  }, [active]);
  const execute = (i: number) => {
    const action = found[i];
    if (action && !action.disabled) {
      onClose();
      action.run();
    }
  };
  return (
    <Modal title="Wohin soll's gehen?" onClose={onClose}>
      <div
        className="command-palette"
        onKeyDown={(e) => {
          if (e.key === "ArrowDown") {
            e.preventDefault();
            setActive((i) => Math.min(found.length - 1, i + 1));
          }
          if (e.key === "ArrowUp") {
            e.preventDefault();
            setActive((i) => Math.max(0, i - 1));
          }
          if (e.key === "Enter") {
            e.preventDefault();
            execute(active);
          }
        }}
      >
        <label className="command-search">
          <Search size={19} />
          <input
            autoFocus
            role="combobox"
            aria-expanded="true"
            aria-controls="votex-commands"
            aria-activedescendant={
              found[active] ? `command-result-${found[active].id}` : undefined
            }
            aria-autocomplete="list"
            aria-label="Aktionen und Profile suchen"
            placeholder="Profile, Mods, Einstellungen …"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <kbd>ESC</kbd>
        </label>
        <div
          className="command-results"
          role="listbox"
          id="votex-commands"
          aria-label="Schnellaktionen"
        >
          {found.map((a, i) => {
            const Icon = a.icon;
            return (
              <button
                key={a.id}
                id={`command-result-${a.id}`}
                role="option"
                aria-selected={i === active}
                disabled={a.disabled}
                className={i === active ? "selected" : ""}
                onMouseEnter={() => setActive(i)}
                onClick={() => execute(i)}
              >
                <span className="command-icon">
                  <Icon size={17} />
                </span>
                <span>
                  <small>{a.group}</small>
                  <strong>{a.name}</strong>
                  <em>{a.detail}</em>
                </span>
                <ArrowUpRight size={15} />
              </button>
            );
          })}
          {!found.length && (
            <div className="command-empty">
              Keine passende Aktion. Versuche einen Profilnamen oder „Mods“.
            </div>
          )}
        </div>
        <div className="command-help">
          <span>
            <ArrowUp size={11} />
            <ArrowDown size={11} /> Auswählen
          </span>
          <span>
            <CornerDownLeft size={11} /> Öffnen
          </span>
          <span>Nur in Votex · keine Windows-Tastenkürzel</span>
        </div>
      </div>
    </Modal>
  );
}
