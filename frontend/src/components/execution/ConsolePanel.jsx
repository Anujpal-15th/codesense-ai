import { useMemo } from 'react'
import { useExecutionStore } from '../../store/executionStore'

function ConsolePanel() {
  const trace = useExecutionStore((state) => state.trace)
  const currentStepIndex = useExecutionStore((state) => state.currentStepIndex)

  const consoleOutput = useMemo(() => {
    if (!trace) return ''
    let acc = ''
    for (let i = 0; i <= currentStepIndex && i < trace.steps.length; i++) {
      acc += trace.steps[i].consoleOutputDelta ?? ''
    }
    return acc
  }, [trace, currentStepIndex])

  return (
    <div className="rounded-lg border border-line bg-paper-raised p-4">
      <h2 className="mb-3 font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">
        Console Output
      </h2>
      {consoleOutput ? (
        <pre className="max-h-48 overflow-auto rounded-lg bg-paper p-3 font-mono text-sm whitespace-pre-wrap text-ink">
          {consoleOutput}
        </pre>
      ) : (
        <p className="text-sm text-ink-soft">No output.</p>
      )}
    </div>
  )
}

export default ConsolePanel
