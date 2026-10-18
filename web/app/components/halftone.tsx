// Dot grid whose dots shrink away from one corner, like the app icon and the
// App Store screenshots. Decorative: place it inside a `relative` parent.
const SIZE = 600;
const STEP = 20;

const dots: { x: number; y: number; r: number }[] = [];
for (let y = STEP / 2; y < SIZE; y += STEP) {
  for (let x = STEP / 2; x < SIZE; x += STEP) {
    const r = STEP * 0.46 * (1 - Math.hypot(SIZE - x, SIZE - y) / SIZE);
    if (r > STEP * 0.07) dots.push({ x, y, r: Math.round(r * 100) / 100 });
  }
}

const flip = {
  "bottom-right": "",
  "bottom-left": "-scale-x-100",
  "top-right": "-scale-y-100",
  "top-left": "scale-[-1]",
} as const;

const place = {
  "bottom-right": "right-0 bottom-0",
  "bottom-left": "left-0 bottom-0",
  "top-right": "right-0 top-0",
  "top-left": "left-0 top-0",
} as const;

export default function Halftone({
  corner = "bottom-right",
  className = "",
}: {
  corner?: keyof typeof flip;
  className?: string;
}) {
  return (
    <svg
      viewBox={`0 0 ${SIZE} ${SIZE}`}
      aria-hidden
      className={`pointer-events-none absolute ${place[corner]} ${flip[corner]} fill-current ${className}`}
    >
      {dots.map((d) => (
        <circle key={`${d.x}-${d.y}`} cx={d.x} cy={d.y} r={d.r} />
      ))}
    </svg>
  );
}
