import { selectCurrentStep, useExecutionStore } from '../../store/executionStore'

function collectObjectRefs(step) {
  const refs = new Map()

  function walk(value, label) {
    if (!value) return
    if (value.valueKind === 'object') {
      if (!refs.has(value.identityHash)) {
        refs.set(value.identityHash, { objectSummary: value, referencedBy: new Set() })
      }
      refs.get(value.identityHash).referencedBy.add(label)
      value.fields.forEach((f) => walk(f.value, `${label}.${f.name}`))
    } else if (value.valueKind === 'array') {
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
        return (
          <div key={objectSummary.identityHash} className="rounded-md border border-line bg-paper-raised p-2">
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
