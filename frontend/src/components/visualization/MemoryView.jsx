import { selectCurrentStep, useExecutionStore } from '../../store/executionStore'
import { useStepChanges } from './useStepChanges'

function collectObjectRefs(step) {
  const refs = new Map()

  function register(value, label) {
    if (!refs.has(value.identityHash)) {
      refs.set(value.identityHash, { objectSummary: value, referencedBy: new Set() })
    }
    refs.get(value.identityHash).referencedBy.add(label)
  }

  function walk(value, label) {
    if (!value) return
    if (value.valueKind === 'object') {
      register(value, label)
      value.fields.forEach((f) => walk(f.value, `${label}.${f.name}`))
    } else if (value.valueKind === 'array') {
      value.elements.forEach((el, i) => walk(el, `${label}[${i}]`))
    } else if (value.valueKind === 'map') {
      // Maps/sets/lists carry identityHash too, so they appear as heap objects
      // and their contained objects still surface for aliasing detection.
      register(value, label)
      value.entries.forEach((e, i) => {
        walk(e.key, `${label}.key[${i}]`)
        walk(e.value, `${label}.value[${i}]`)
      })
    } else if (value.valueKind === 'set' || value.valueKind === 'list') {
      register(value, label)
      value.elements.forEach((el, i) => walk(el, `${label}[${i}]`))
    }
  }

  step.callStack.forEach((frame) => {
    const frameLabel = `${frame.className}.${frame.methodName}`
    if (frame.thisObject) walk(frame.thisObject.value, `${frameLabel}.this`)
    frame.localVariables.forEach((v) => walk(v.value, `${frameLabel}.${v.name}`))
  })

  return [...refs.values()]
}

function MemoryView() {
  const step = useExecutionStore(selectCurrentStep)
  const changes = useStepChanges()

  if (!step) {
    return <p className="text-sm text-ink-soft">No active frame.</p>
  }

  const entries = collectObjectRefs(step)

  if (entries.length === 0) {
    return <p className="text-sm text-ink-soft">No heap objects at this step.</p>
  }

  return (
    <div className="space-y-2">
      {entries.map(({ objectSummary, referencedBy }) => {
        const aliased = referencedBy.size > 1
        // Flash the card when this object's shallow content changed this step.
        // Keyed remount (`${hash}-${tick}`) replays the CSS animation.
        const mutated = changes.changedHashes.has(objectSummary.identityHash)
        return (
          <div
            key={mutated ? `${objectSummary.identityHash}-${changes.tick}` : objectSummary.identityHash}
            className={`rounded-md border border-line bg-paper-raised p-2 ${mutated ? 'value-flash' : ''}`}
          >
            <div className="flex items-center justify-between gap-2 font-mono text-xs">
              <span className="text-ink">
                {objectSummary.type} <span className="text-ink-soft">#{objectSummary.identityHash}</span>
              </span>
              {aliased && (
                <span className="rounded bg-highlight-ink/10 px-1.5 py-0.5 text-highlight-ink">aliased</span>
              )}
            </div>
            <div className="mt-1 flex flex-wrap gap-1">
              {[...referencedBy].map((label) => (
                <span key={label} className="rounded bg-paper px-1.5 py-0.5 font-mono text-[10px] text-ink-soft">
                  {label}
                </span>
              ))}
            </div>
          </div>
        )
      })}
    </div>
  )
}

export default MemoryView
