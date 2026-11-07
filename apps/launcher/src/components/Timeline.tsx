import { useState } from "react";
import { Select } from "./Select";
import { frameAt, moveFrame, timelineTime } from "../data/timeline";
type Props = {
  times: number[];
  duration: number;
  cursor: number;
  selected: number;
  onCursor: (time: number) => void;
  onSelect: (index: number) => void;
  onMove: (index: number, time: number) => void;
  onInsert: (time: number) => void;
};
/** Time is chosen by the playhead, never by an implicit midpoint. Stable handles keep pointer capture during drags. */
export function Timeline({
  times,
  duration,
  cursor,
  selected,
  onCursor,
  onSelect,
  onMove,
  onInsert,
}: Props) {
  const [zoom, setZoom] = useState(1);
  const width = Math.max(260, times.length * 40) * zoom;
  const setSeconds = (n: number) => onCursor(timelineTime(n / duration));
  return (
    <section
      className="precision-timeline"
      aria-label="Präzise Keyframe-Zeitleiste"
    >
      <div className="timeline-timecode">
        <label>
          Abspielkopf{" "}
          <span>
            <input
              aria-label="Abspielkopf in Sekunden"
              type="number"
              min="0"
              max={duration}
              step="0.01"
              value={Number((cursor * duration).toFixed(2))}
              onChange={(e) => setSeconds(Number(e.target.value))}
            />{" "}
            s
          </span>
        </label>
        <label>
          Zoom
          <Select
            aria-label="Zeitleisten-Zoom"
            value={zoom}
            onChange={(e) => setZoom(Number(e.target.value))}
          >
            <option value={1}>1×</option>
            <option value={2}>2×</option>
            <option value={4}>4×</option>
          </Select>
        </label>
      </div>
      <div
        className="timeline-scroll"
        tabIndex={0}
        aria-label="Zeitleiste horizontal scrollen"
      >
        <div
          className="timeline-track"
          style={{ minWidth:width, width:'100%' }}
          onPointerDown={(e) => {
            if (e.button !== 0) return;
            const r = e.currentTarget.getBoundingClientRect();
            onCursor(timelineTime((e.clientX - r.left) / r.width));
            e.currentTarget.setPointerCapture(e.pointerId);
          }}
          onPointerMove={(e) => {
            if (
              e.buttons !== 1 ||
              !e.currentTarget.hasPointerCapture(e.pointerId)
            )
              return;
            const r = e.currentTarget.getBoundingClientRect();
            onCursor(timelineTime((e.clientX - r.left) / r.width));
          }}
          onPointerUp={(e) => {
            if (e.currentTarget.hasPointerCapture(e.pointerId))
              e.currentTarget.releasePointerCapture(e.pointerId);
          }}
          onDoubleClick={(e) => {
            const r = e.currentTarget.getBoundingClientRect();
            onInsert(timelineTime((e.clientX - r.left) / r.width));
          }}
        >
          {Array.from({ length: 6 }, (_, i) => (
            <span
              className="timeline-tick"
              key={i}
              style={{ left: `${i * 20}%` }}
            >
              <span>{Number(((duration * i) / 5).toFixed(2))}s</span>
            </span>
          ))}
          <div className="timeline-line" />
          <div
            className="timeline-playhead"
            style={{ left: `${cursor * 100}%` }}
            aria-hidden="true"
          >
            <i />
          </div>
          {times.map((time, index) => (
            <button
              key={index}
              type="button"
              className={`timeline-key ${selected === index ? "selected" : ""}`}
              style={{ left: `${time * 100}%` }}
              aria-pressed={selected === index}
              aria-label={`Keyframe ${index + 1} bei ${(time * duration).toFixed(2)} Sekunden`}
              title={`${(time * duration).toFixed(2)} s${index === 0 || index === times.length - 1 ? " · fester Endpunkt" : " · ziehen oder Pfeiltasten"}`}
              onClick={(e) => {
                e.stopPropagation();
                onSelect(index);
              }}
              onDoubleClick={(e) => e.stopPropagation()}
              onPointerDown={(e) => {
                e.stopPropagation();
                onSelect(index);
                e.currentTarget.setPointerCapture(e.pointerId);
              }}
              onPointerMove={(e) => {
                if (
                  e.buttons !== 1 ||
                  !e.currentTarget.hasPointerCapture(e.pointerId)
                )
                  return;
                const r =
                  e.currentTarget.parentElement!.getBoundingClientRect();
                onMove(
                  index,
                  moveFrame(times, index, (e.clientX - r.left) / r.width),
                );
              }}
              onPointerUp={(e) => {
                e.stopPropagation();
                if (e.currentTarget.hasPointerCapture(e.pointerId))
                  e.currentTarget.releasePointerCapture(e.pointerId);
              }}
              onKeyDown={(e) => {
                if (e.key === "ArrowLeft" || e.key === "ArrowRight") {
                  e.preventDefault();
                  onMove(
                    index,
                    moveFrame(
                      times,
                      index,
                      time +
                        ((e.key === "ArrowRight" ? 1 : -1) *
                          (e.shiftKey ? 0.1 : 0.01)) /
                          duration,
                    ),
                  );
                }
              }}
            >
              <i />
              <span>{index + 1}</span>
            </button>
          ))}
        </div>
      </div>
      <p>
        Klicke in die Spur oder gib die Sekunde ein. Doppelklick setzt dort
        einen Keyframe. Ziehen und Pfeiltasten verschieben vorhandene Frames.
      </p>
      <button
        type="button"
        className="btn timeline-add"
        disabled={times.length >= 24 || frameAt(times, cursor) >= 0}
        onClick={() => onInsert(cursor)}
      >
        {frameAt(times, cursor) >= 0
          ? "An dieser Position liegt ein Keyframe"
          : `Keyframe bei ${(cursor * duration).toFixed(2)} s setzen`}
      </button>
    </section>
  );
}
