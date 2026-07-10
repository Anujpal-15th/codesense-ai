// A small (max 120px) growth-curve chart for the Analysis tab. Draws the common
// complexity classes as faint reference curves and highlights the one the current
// solution falls on. Complements the Time/Space text badges — it does not replace
// them (the big panel-dominating chart was removed on purpose).
//
// The backend only returns complexity as a Big-O string, so the active class is
// derived by matching that string (most-specific tokens first).

const PLOT = { left: 6, right: 258, top: 12, bottom: 104 }
const W = PLOT.right - PLOT.left
const H = PLOT.bottom - PLOT.top

const CURVES = [
  { key: 'o1', label: 'O(1)', fn: () => 0.04 },
  { key: 'ologn', label: 'O(log n)', fn: (t) => Math.log2(1 + t * 1023) / Math.log2(1024) },
  { key: 'on', label: 'O(n)', fn: (t) => t },
  { key: 'onlogn', label: 'O(n log n)', fn: (t) => (t === 0 ? 0 : (t * Math.log2(1 + t * 1023)) / Math.log2(1024)) },
  { key: 'on2', label: 'O(n²)', fn: (t) => t * t },
  { key: 'oexp', label: 'O(2ⁿ)', fn: (t) => (Math.pow(2, t * 8) - 1) / (Math.pow(2, 8) - 1) },
]

function activeKey(complexity) {
  const c = (complexity || '').toLowerCase().replace(/\s+/g, '')
  if (c.includes('2^n') || c.includes('2ⁿ') || c.includes('^n') || c.includes('!')) return 'oexp'
  if (c.includes('n²') || c.includes('n^2') || c.includes('n³') || c.includes('n^3')) return 'on2'
  if (c.includes('nlogn') || (c.includes('n') && c.includes('log'))) return 'onlogn'
  if (c.includes('log')) return 'ologn'
  if (c.includes('(1)')) return 'o1'
  if (c.includes('n')) return 'on'
  return null
}

function buildPath(fn) {
  const N = 48
  let d = ''
  for (let i = 0; i <= N; i++) {
    const t = i / N
    const y = Math.min(1, Math.max(0, fn(t)))
    const px = PLOT.left + t * W
    const py = PLOT.bottom - y * H
    d += (i === 0 ? 'M' : ' L') + px.toFixed(1) + ',' + py.toFixed(1)
  }
  return d
}

function ComplexityGraph({ complexity, isOptimal }) {
  const active = activeKey(complexity)
  const highlight = isOptimal ? 'var(--color-approve)' : 'var(--color-correct)'
  const activeCurve = CURVES.find((c) => c.key === active)

  return (
    <div className="rounded-lg border border-line bg-paper p-3">
      <svg viewBox="0 0 264 120" className="h-[120px] w-full" preserveAspectRatio="none">
        {/* baseline axis */}
        <line
          x1={PLOT.left}
          y1={PLOT.bottom}
          x2={PLOT.right}
          y2={PLOT.bottom}
          stroke="var(--color-line)"
          strokeWidth="1"
          vectorEffect="non-scaling-stroke"
        />
        {/* faint reference curves */}
        {CURVES.filter((c) => c.key !== active).map((c) => (
          <path
            key={c.key}
            d={buildPath(c.fn)}
            fill="none"
            stroke="var(--color-ink-soft)"
            strokeWidth="1.25"
            strokeOpacity="0.22"
            vectorEffect="non-scaling-stroke"
          />
        ))}
        {/* highlighted current curve, drawn on top */}
        {activeCurve && (
          <path
            d={buildPath(activeCurve.fn)}
            fill="none"
            stroke={highlight}
            strokeWidth="2.5"
            strokeLinecap="round"
            vectorEffect="non-scaling-stroke"
          />
        )}
      </svg>

      {/* legend — the class the current solution falls on is highlighted */}
      <div className="mt-2 flex flex-wrap gap-x-2.5 gap-y-1 font-mono text-[10px]">
        {CURVES.map((c) => (
          <span
            key={c.key}
            className={c.key === active ? 'font-bold' : 'text-ink-soft/45'}
            style={c.key === active ? { color: highlight } : undefined}
          >
            {c.label}
          </span>
        ))}
      </div>
    </div>
  )
}

export default ComplexityGraph
