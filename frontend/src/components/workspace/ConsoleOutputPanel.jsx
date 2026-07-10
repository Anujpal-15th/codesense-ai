import { useExecutionStore } from '../../store/executionStore'

// Result-tab console view. Shows the FULL program output (trace.consoleOutput),
// not the per-step delta accumulation used during step-through — the backend
// often emits all output in a single late step's delta, so accumulating only
// up to currentStepIndex (0 in the Result tab) would render "No output".
function ConsoleOutputPanel() {
  const trace = useExecutionStore((state) => state.trace)

  if (!trace) {
    return (
      <p className="rounded-lg border border-line bg-paper-raised p-6 text-sm text-ink-soft">
        Click "Run" to execute your code and see the output here.
      </p>
    )
  }

  const output = trace.consoleOutput ?? ''

  return (
    <div className="rounded-[15px] border border-line bg-paper-raised p-4">
      <h2 className="mb-3 font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">
        Console Output
      </h2>
      {output ? (
        <pre className="max-h-72 overflow-auto rounded-[15px] bg-paper p-3 font-mono text-sm whitespace-pre-wrap text-ink">
          {output}
        </pre>
      ) : (
        <p className="text-sm text-ink-soft">No output.</p>
      )}
    </div>
  )
}

export default ConsoleOutputPanel
