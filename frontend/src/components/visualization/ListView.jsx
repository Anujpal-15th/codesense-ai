import ValueRenderer from './ValueRenderer'
import { shortType } from './traceValue'

// Semantic view of a java.util.List / ordered Collection: indexed element boxes
// (from ListSummary). Mirrors the array box layout so lists and arrays read
// alike. Keyed by index (order is the list's identity).
function ListView({ name, value }) {
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
          {elements.map((element, index) => (
            <div key={index} className="flex flex-col items-center">
              <div className="rounded-md border border-line bg-paper-raised px-2 py-1.5">
                <ValueRenderer name="" declaredType="" value={element} depth={1} visited={new Set()} />
              </div>
              <div className="mt-0.5 font-mono text-[10px] text-ink-soft">{index}</div>
            </div>
          ))}
          {truncated && (
            <div className="self-center font-mono text-xs text-ink-soft italic">… {size} total</div>
          )}
        </div>
      )}
    </div>
  )
}

export default ListView
