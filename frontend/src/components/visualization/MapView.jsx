import ValueRenderer from './ValueRenderer'
import { shortType, valueKey } from './traceValue'
import { PopIn } from './StepChanges'
import { mapEntryPath } from './stepDiff'

// Semantic view of a java.util.Map: real key -> value rows (from MapSummary).
// Keyed by the entry key's identity; a newly-put key pops in, and an updated
// value flashes (the value's ValueRenderer leaf carries the same entry path).
function MapView({ name, value, path = null }) {
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
          {entries.map((entry) => {
            const entryPath = path != null ? mapEntryPath(path, entry.key) : null
            return (
              <PopIn
                key={valueKey(entry.key)}
                path={entryPath}
                className="flex items-center gap-2 rounded-md border border-line bg-paper-raised px-2 py-1"
              >
                <ValueRenderer name="" declaredType="" value={entry.key} depth={1} visited={new Set()} />
                <span className="font-mono text-sm text-ink-soft">→</span>
                <ValueRenderer name="" declaredType="" value={entry.value} depth={1} visited={new Set()} path={entryPath} />
              </PopIn>
            )
          })}
          {truncated && (
            <div className="font-mono text-xs text-ink-soft italic">… {size} total, first entries shown</div>
          )}
        </div>
      )}
    </div>
  )
}

export default MapView
