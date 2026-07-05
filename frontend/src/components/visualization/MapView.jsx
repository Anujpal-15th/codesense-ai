import ValueRenderer from './ValueRenderer'
import { shortType, valueKey } from './traceValue'

// Semantic view of a java.util.Map: real key -> value rows (from the backend's
// MapSummary, produced via entrySet().toArray() on the debuggee). Keyed by the
// entry key's identity so the animation layer can target added/updated entries.
function MapView({ name, value }) {
  const { type, size, entries, truncated } = value

  return (
    <div className="space-y-1">
      <div className="font-mono text-xs text-ink-soft">
        {name}: {shortType(type)} · {size} {size === 1 ? 'entry' : 'entries'}
      </div>
      {entries.length === 0 ? (
        <div className="font-mono text-xs text-ink-soft italic">empty</div>
      ) : (
        <div className="space-y-1">
          {entries.map((entry) => (
            <div
              key={valueKey(entry.key)}
              className="flex items-center gap-2 rounded-md border border-line bg-paper-raised px-2 py-1"
            >
              <ValueRenderer name="" declaredType="" value={entry.key} depth={1} visited={new Set()} />
              <span className="font-mono text-sm text-ink-soft">→</span>
              <ValueRenderer name="" declaredType="" value={entry.value} depth={1} visited={new Set()} />
            </div>
          ))}
          {truncated && (
            <div className="font-mono text-xs text-ink-soft italic">… {size} total, first entries shown</div>
          )}
        </div>
      )}
    </div>
  )
}

export default MapView
