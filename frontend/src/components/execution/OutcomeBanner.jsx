import { useExecutionStore } from '../../store/executionStore'

function OutcomeBanner() {
  const trace = useExecutionStore((state) => state.trace)

  if (!trace || trace.outcome === 'NORMAL') return null

  if (trace.outcome === 'EXCEPTION' && trace.exceptionInfo) {
    return (
      <div className="rounded-lg border border-correct/30 bg-correct/10 p-4">
        <p className="font-mono font-medium text-correct">
          {trace.exceptionInfo.exceptionClassName}: {trace.exceptionInfo.message}
        </p>
        {trace.exceptionInfo.stackTraceLines.length > 0 && (
          <pre className="mt-2 font-mono text-sm whitespace-pre-wrap text-correct">
            {trace.exceptionInfo.stackTraceLines.join('\n')}
          </pre>
        )}
      </div>
    )
  }

  if (trace.outcome === 'TIMED_OUT') {
    return (
      <div className="rounded-lg border border-highlight-ink/30 bg-highlight-ink/10 p-4 text-highlight-ink">
        Execution did not finish in time and was stopped. Steps captured before the timeout are still browsable
        below.
      </div>
    )
  }

  if (trace.outcome === 'TRUNCATED' || trace.truncated) {
    return (
      <div className="rounded-lg border border-highlight-ink/30 bg-highlight-ink/10 p-4 text-highlight-ink">
        <strong className="font-semibold">
          Trace capped at {trace.totalStepsCaptured} steps
        </strong>{' '}
        — showing the first {trace.totalStepsCaptured} steps only. The step limit was reached before execution
        finished; steps captured so far are still browsable below.
      </div>
    )
  }

  return null
}

export default OutcomeBanner
