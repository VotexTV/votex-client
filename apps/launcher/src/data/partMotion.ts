/** Native meshes are converted from Y-down/Z-back to the preview's Y-up basis. */
export function previewMotionAngle(axis: "x" | "y" | "z", radians: number) {
  return axis === "x" ? radians : -radians;
}
export function samplePartMotion(
  motion: {
    amplitude: number;
    speed: number;
    phase: number;
    duration?: number;
    keyframes?: { time: number; angle: number }[];
  },
  seconds: number,
) {
  const frames = motion.keyframes;
  if (!frames?.length)
    return Math.sin(seconds * motion.speed + motion.phase) * motion.amplitude;
  const time = (((seconds / (motion.duration ?? 2)) % 1) + 1) % 1;
  const right = Math.max(
    1,
    frames.findIndex((frame) => frame.time >= time),
  );
  const a = frames[right - 1],
    b = frames[right];
  const t = Math.max(0, Math.min(1, (time - a.time) / (b.time - a.time))),
    ease = t * t * (3 - 2 * t);
  return a.angle + (b.angle - a.angle) * ease;
}
