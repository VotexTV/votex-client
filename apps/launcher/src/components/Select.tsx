import {
  Children,
  isValidElement,
  useEffect,
  useId,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
  type SelectHTMLAttributes,
  type ChangeEvent,
} from "react";
import { createPortal } from "react-dom";
import { Check, ChevronDown } from "lucide-react";
type Entry = {
  value: string;
  label: string;
  disabled: boolean;
  group?: string;
};
function plain(value: ReactNode): string {
  return Children.toArray(value)
    .map((child) =>
      isValidElement<{ children?: ReactNode }>(child)
        ? plain(child.props.children)
        : String(child ?? ""),
    )
    .join("");
}
function entries(children: ReactNode, group?: string): Entry[] {
  return Children.toArray(children).flatMap((child) => {
    if (
      !isValidElement<{
        children?: ReactNode;
        value?: string | number;
        disabled?: boolean;
        label?: string;
      }>(child)
    )
      return [];
    if (child.type === "option")
      return [
        {
          value: String(child.props.value ?? plain(child.props.children)),
          label: child.props.label ?? plain(child.props.children),
          disabled: !!child.props.disabled,
          group,
        },
      ];
    return entries(
      child.props.children,
      child.type === "optgroup" ? child.props.label : group,
    );
  });
}
/** Native value semantics with a rounded, keyboard accessible, viewport-bound listbox. */
export function Select({
  children,
  value,
  defaultValue,
  onChange,
  disabled,
  className = "",
  id,
  ...rest
}: SelectHTMLAttributes<HTMLSelectElement>) {
  const uid = useId(),
    button = useRef<HTMLButtonElement>(null),
    native = useRef<HTMLSelectElement>(null),
    popup = useRef<HTMLDivElement>(null);
  const options = entries(children),
    [local, setLocal] = useState(
      String(defaultValue ?? options[0]?.value ?? ""),
    );
  const selected = String(value ?? local),
    selectedIndex = options.findIndex((item) => item.value === selected);
  const [open, setOpen] = useState(false),
    [active, setActive] = useState(0),
    [position, setPosition] = useState<CSSProperties>({});
  const search = useRef({ value: "", time: 0 });
  const menuId = `select-${uid}`;
  const choose = (index: number) => {
    const option = options[index],
      node = native.current;
    if (!option || option.disabled || !node) return;
    node.value = option.value;
    setLocal(option.value);
    onChange?.({
      target: node,
      currentTarget: node,
      nativeEvent: new Event("change"),
      type: "change",
      bubbles: true,
      cancelable: false,
      defaultPrevented: false,
      eventPhase: 3,
      isTrusted: false,
      timeStamp: Date.now(),
      preventDefault() {},
      stopPropagation() {},
      isDefaultPrevented: () => false,
      isPropagationStopped: () => false,
      persist() {},
    } as ChangeEvent<HTMLSelectElement>);
    setOpen(false);
    button.current?.focus();
  };
  const show = () => {
    if (disabled) return;
    const trigger = button.current;
    if (!trigger) return;
    const rect = trigger.getBoundingClientRect(),
      computed = getComputedStyle(trigger);
    const variables = Object.fromEntries(
      Array.from(computed)
        .filter((key) => key.startsWith("--"))
        .map((key) => [key, computed.getPropertyValue(key)]),
    );
    const height = Math.min(320, Math.max(120, innerHeight - 40)),
      above =
        innerHeight - rect.bottom < Math.min(height, 200) &&
        rect.top > innerHeight - rect.bottom;
    setPosition({
      ...variables,
      position: "fixed",
      left: Math.max(10, Math.min(rect.left, innerWidth - rect.width - 10)),
      width: Math.min(rect.width, innerWidth - 20),
      maxHeight: above
        ? Math.min(height, rect.top - 18)
        : Math.min(height, innerHeight - rect.bottom - 18),
      ...(above
        ? { bottom: innerHeight - rect.top + 6 }
        : { top: rect.bottom + 6 }),
      transformOrigin: above ? "bottom center" : "top center",
    });
    setActive(Math.max(0, selectedIndex));
    setOpen(true);
  };
  useEffect(() => {
    if (!open) return;
    const close = (event: Event) => {
      const target = event.target as Node;
      if (event.type === "scroll" && popup.current?.contains(target)) return;
      if (!button.current?.contains(target) && !popup.current?.contains(target))
        setOpen(false);
    };
    document.addEventListener("pointerdown", close);
    window.addEventListener("resize", close);
    window.addEventListener("scroll", close, true);
    return () => {
      document.removeEventListener("pointerdown", close);
      window.removeEventListener("resize", close);
      window.removeEventListener("scroll", close, true);
    };
  }, [open]);
  useEffect(() => {
    if (open)
      popup.current
        ?.querySelector(`[data-index="${active}"]`)
        ?.scrollIntoView({ block: "nearest" });
  }, [active, open]);
  const move = (direction: number) => {
    let next = open ? active : Math.max(0, selectedIndex);
    for (let i = 0; i < options.length; i++) {
      next = (next + direction + options.length) % options.length;
      if (!options[next].disabled) {
        setActive(next);
        break;
      }
    }
  };
  return (
    <span className="select-field">
      <button
        ref={button}
        type="button"
        id={id}
        className={`select-trigger ${className}`}
        disabled={disabled}
        style={rest.style}
        role="combobox"
        aria-label={rest["aria-label"]}
        aria-labelledby={rest["aria-labelledby"]}
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-controls={open ? menuId : undefined}
        aria-activedescendant={open ? `${menuId}-${active}` : undefined}
        onClick={() => (open ? setOpen(false) : show())}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" || event.key === "ArrowUp") {
            event.preventDefault();
            if (!open) show();
            else move(event.key === "ArrowDown" ? 1 : -1);
          } else if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            if (open) choose(active);
            else show();
          } else if (event.key === "Escape") {
            if (open) {
              event.preventDefault();
              event.stopPropagation();
              setOpen(false);
            }
          } else if (event.key === "Tab") setOpen(false);
          else if (event.key === "Home" || event.key === "End") {
            event.preventDefault();
            if (!open) show();
            setActive(
              event.key === "Home" ? 0 : Math.max(0, options.length - 1),
            );
          } else if (event.key.length === 1) {
            const now = Date.now();
            search.current = {
              value:
                (now - search.current.time < 700 ? search.current.value : "") +
                event.key.toLocaleLowerCase(),
              time: now,
            };
            const index = options.findIndex(
              (item) =>
                !item.disabled &&
                item.label.toLocaleLowerCase().startsWith(search.current.value),
            );
            if (index >= 0) {
              if (open) setActive(index);
              else choose(index);
            }
          }
        }}
      >
        <span>{options[selectedIndex]?.label ?? "Auswählen …"}</span>
        <ChevronDown size={15} />
      </button>
      <select
        {...rest}
        id={id ? `${id}-native` : undefined}
        ref={native}
        value={selected}
        disabled={disabled}
        aria-hidden="true"
        tabIndex={-1}
        style={{ display: "none" }}
        onChange={onChange}
      >
        {children}
      </select>
      {open &&
        createPortal(
          <div
            ref={popup}
            className="select-popover glass"
            id={menuId}
            role="listbox"
            aria-label={rest["aria-label"] || "Auswahl"}
            style={position}
          >
            {options.map((option, index) => (
              <button
                key={`${option.value}-${index}`}
                type="button"
                id={`${menuId}-${index}`}
                data-index={index}
                role="option"
                aria-selected={option.value === selected}
                disabled={option.disabled}
                className={active === index ? "active" : ""}
                onPointerMove={() => setActive(index)}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => choose(index)}
              >
                {option.group && index === 0 && <small>{option.group}</small>}
                <span>{option.label}</span>
                {option.value === selected && <Check size={15} />}
              </button>
            ))}
          </div>,
          document.body,
        )}
    </span>
  );
}
