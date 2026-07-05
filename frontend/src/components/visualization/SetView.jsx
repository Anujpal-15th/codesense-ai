import ValueRenderer from './ValueRenderer'
import { shortType, valueKey } from './traceValue'
import { PopIn } from './StepChanges'
import { setElemPath } from './stepDiff'

// Semantic view of a java.util.Set: real elements as chips (from SetSummary).
// Keyed by element identity; a newly-added element pops in.
function SetView({ name, value, path = null }) {
  const { type, size, elements, truncated } = value

  return (
    <div className="space-y-1">
      <div className="font-mono text-xs text-ink-soft">
        {name}: {shortType(type)} · {size} {size === 1 ? 'element' : 'elements'}
      </div>
      {elements.length === 0 ? (
        <div className="font-mono text-xs text-ink-soft italic">empty</div>
      ) : (
        <div className="flex flex-wrap gap-1">
          {elements.map((element) => (
            <PopIn
              key={valueKey(element)}
              path={path != null ? setElemPath(path, element) : null}
              className="rounded-md border border-line bg-paper-raised px-2 py-1"
            >
              <ValueRenderer name="" declaredType="" value={element} depth={1} visited={new Set()} />
            </PopIn>
          ))}
          {truncated && (
            <div className="self-center font-mono text-xs text-ink-soft italic">… {size} total</div>
          )}
        </div>
      )}
    </div>
  )
}

export default SetView
