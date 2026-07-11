// `animated` normalizes the path length so a stroke draw-in reads the same
// regardless of the curve, and tags it with a class the CSS animates. Callers
// that want the line to re-draw on change should give the chart a React `key`
// so it remounts (used by the landing hero's auto-cycling demo).
function MiniChart({ d, color, animated = false }) {
  return (
    <svg viewBox="0 0 100 48" className="h-full w-full" preserveAspectRatio="none">
      <path
        d={d}
        fill="none"
        stroke={color}
        strokeWidth="2"
        strokeLinecap="round"
        {...(animated ? { pathLength: 1, className: 'landing-chart-draw' } : {})}
      />
    </svg>
  )
}

export default MiniChart
