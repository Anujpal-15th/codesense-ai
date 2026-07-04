import { selectCurrentStep, useExecutionStore } from '../../store/executionStore'

function frameClassName(index, eventType) {
  if (index === 0 && eventType === 'METHOD_EXIT') {
    return 'bg-highlight-ink/10 text-highlight-ink'
  }
  if (index === 0) {
    return 'bg-approve/10 text-approve'
  }
  return 'text-ink-soft'
}

function CallStackPanel() {
  const step = useExecutionStore(selectCurrentStep)

  return (
    <div className="rounded-lg border border-line bg-paper-raised p-4">
      <h2 className="mb-3 font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">Call Stack</h2>
      {!step || step.callStack.length === 0 ? (
        <p className="text-sm text-ink-soft">No active frames.</p>
      ) : (
        <ul className="space-y-1">
          {step.callStack.map((frame, index) => (
            <li
              key={`${frame.className}.${frame.methodName}-${index}`}
              className={`rounded px-2 py-1 font-mono text-sm ${frameClassName(index, step.eventType)}`}
            >
              {frame.className}.{frame.methodName}():{frame.lineNumber}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default CallStackPanel
