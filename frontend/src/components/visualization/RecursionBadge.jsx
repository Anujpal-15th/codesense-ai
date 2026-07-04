import { selectCurrentStep, useExecutionStore } from '../../store/executionStore'

function computeRecursionDepths(step) {
  const counts = new Map()
  step.callStack.forEach((frame) => {
    const key = `${frame.className}.${frame.methodName}`
    counts.set(key, (counts.get(key) ?? 0) + 1)
  })
  return [...counts.entries()].filter(([, count]) => count > 1)
}

function RecursionBadge() {
  const step = useExecutionStore(selectCurrentStep)
  if (!step) return null

  const recursing = computeRecursionDepths(step)
  if (recursing.length === 0) return null

  return (
    <div className="flex flex-wrap gap-2">
      {recursing.map(([method, depth]) => (
        <span
          key={method}
          className="rounded-full bg-approve/10 px-2 py-1 font-mono text-xs font-semibold text-approve"
        >
          {method} · recursion depth {depth}
        </span>
      ))}
    </div>
  )
}

export default RecursionBadge
