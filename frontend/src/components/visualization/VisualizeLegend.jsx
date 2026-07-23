// Always-visible color key for the Visualize tab. The individual panels
// (Call Stack, Memory) already have their own "?" InfoToggle explainers, but
// those are opt-in and a first-time user has no reason to know they're
// there - this puts the same vocabulary in front of them without a click,
// once, at the top of the whole view.
const SWATCHES = [
  { color: 'bg-approve', label: 'active frame' },
  { color: 'bg-highlight-ink', label: 'returning frame' },
  { color: 'bg-ink-soft', label: 'waiting frame' },
  { color: 'bg-highlight-ink', label: 'value just changed', flash: true },
  { color: 'bg-primary', label: 'pointer position' },
]

function Swatch({ color, label, flash }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className={`h-2.5 w-2.5 shrink-0 rounded-full ${color} ${flash ? 'value-flash' : ''}`} />
      <span className="text-ink-soft">{label}</span>
    </span>
  )
}

function VisualizeLegend() {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-lg border border-line bg-paper-raised px-3 py-2 font-mono text-[11px]">
      {SWATCHES.map((s) => (
        <Swatch key={s.label} {...s} />
      ))}
    </div>
  )
}

export default VisualizeLegend
