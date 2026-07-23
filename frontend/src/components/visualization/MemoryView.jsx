import { motion } from 'framer-motion'
import { selectCurrentStep, useExecutionStore } from '../../store/executionStore'
import InfoToggle from './InfoToggle'
import { staggerDelaySeconds, POP_IN_DURATION_SECONDS } from './staggerDelay'
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

  const explainer = (
    <InfoToggle title="Memory">
      <p>
        This panel shows the actual objects your code creates while running — not just their values, but where they
        live and whether two variables secretly point to the same one.
      </p>
      <p>
        <strong className="text-ink">#55-style numbers</strong> — a unique ID per object, so you can tell whether
        two variables point to the same object or just two different objects that happen to look alike.
      </p>
      <p>
        <strong className="text-ink">&ldquo;aliased&rdquo;</strong> — more than one variable or field points to this
        exact object right now. Change it through one reference and it changes everywhere, because there&rsquo;s
        really only one object, not a copy.
      </p>
      <p>
        <strong className="text-ink">Why &ldquo;Integer&rdquo; instead of a plain number?</strong> — Java&rsquo;s
        collections (Map, List, Set) can only hold objects, not raw numbers. So when a primitive like an int is
        stored in one, Java automatically wraps (&ldquo;boxes&rdquo;) it in a small object — that&rsquo;s the
        Integer you&rsquo;re seeing.
      </p>
      <p>
        <strong className="text-ink">&ldquo;went out of scope&rdquo;</strong> — the method holding the last
        reference to that object just returned, so nothing can reach it anymore. It hasn&rsquo;t crashed or been
        deleted — it just isn&rsquo;t visible from here any longer.
      </p>
    </InfoToggle>
  )

  if (!step) {
    return (
      <>
        {explainer}
        <p className="text-sm text-ink-soft">No active frame.</p>
      </>
    )
  }

  const entries = collectObjectRefs(step)
  const removed = [...changes.removedObjects]

  if (entries.length === 0 && removed.length === 0) {
    return (
      <>
        {explainer}
        <p className="text-sm text-ink-soft">No heap objects at this step.</p>
      </>
    )
  }

  let newRank = 0

  return (
    <>
      {explainer}
      {removed.length > 0 && (
        <div
          key={`removed-${changes.tick}`}
          className="value-flash mb-2 rounded-md border border-highlight-ink/30 bg-highlight-ink/10 p-2 font-mono text-xs text-highlight-ink"
        >
          Went out of scope: {removed.map(([hash, type]) => `${type} #${hash}`).join(', ')}
        </div>
      )}
      <div className="space-y-2">
        {entries.map(({ objectSummary, referencedBy }) => {
          const aliased = referencedBy.size > 1
          // Flash the card when this object's shallow content changed this
          // step. Keyed remount (`${hash}-${tick}`) replays the CSS animation.
          const mutated = changes.changedHashes.has(objectSummary.identityHash)
          const isNew = changes.newObjectHashes.has(objectSummary.identityHash)
          const rank = isNew ? newRank++ : -1
          return (
            <motion.div
              key={mutated ? `${objectSummary.identityHash}-${changes.tick}` : objectSummary.identityHash}
              initial={isNew ? { opacity: 0, scale: 0.9 } : false}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: POP_IN_DURATION_SECONDS, delay: staggerDelaySeconds(rank) }}
              className={`rounded-md border border-line bg-paper-raised p-2 ${mutated ? 'value-flash' : ''}`}
            >
              <div className="flex items-center justify-between gap-2 font-mono text-xs">
                <span className="text-ink">
                  {objectSummary.type} <span className="text-ink-soft">#{objectSummary.identityHash}</span>
                </span>
                {aliased && (
                  <span
                    title="More than one variable currently points to this exact object — changing it through one reference changes it everywhere."
                    className="rounded bg-highlight-ink/10 px-1.5 py-0.5 text-highlight-ink"
                  >
                    aliased
                  </span>
                )}
              </div>
              <div className="mt-1 flex flex-wrap gap-1">
                {[...referencedBy].map((label) => (
                  <span key={label} className="rounded bg-paper px-1.5 py-0.5 font-mono text-[10px] text-ink-soft">
                    {label}
                  </span>
                ))}
              </div>
            </motion.div>
          )
        })}
      </div>
    </>
  )
}

export default MemoryView
