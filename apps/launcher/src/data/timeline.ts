export const timelineTime = (time: number) =>
  Math.round(
    Math.max(0, Math.min(1, Number.isFinite(time) ? time : 0)) * 100000,
  ) / 100000;
export function frameAt(times: number[], time: number) {
  return times.findIndex((t) => Math.abs(t - time) < 0.0001);
}
export function insertionAt(times: number[], time: number) {
  const value = timelineTime(time),
    existing = frameAt(times, value);
  if (existing >= 0) return { index: existing, time: value, existing: true };
  const index = times.findIndex((t) => t > value);
  return { index: Math.max(1, index), time: value, existing: false };
}
export function interpolateAt(times: number[], values: number[], time: number) {
  const found = frameAt(times, time);
  if (found >= 0) return values[found] ?? 0;
  const right = Math.max(
      1,
      times.findIndex((t) => t >= time),
    ),
    left = right - 1;
  const t = Math.max(
      0,
      Math.min(1, (time - times[left]) / (times[right] - times[left] || 1)),
    ),
    ease = t * t * (3 - 2 * t);
  return (
    (values[left] ?? 0) + ((values[right] ?? 0) - (values[left] ?? 0)) * ease
  );
}
export function moveFrame(times: number[], index: number, time: number) {
  if (index <= 0 || index >= times.length - 1) return times[index];
  const gap=Math.min(.001,(times[index+1]-times[index-1])/3);
  return timelineTime(
    Math.max(
      times[index - 1] + gap,
      Math.min(times[index + 1] - gap, time),
    ),
  );
}
