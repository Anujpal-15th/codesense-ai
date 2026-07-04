import CallStackPanel from '../execution/CallStackPanel'
import OutcomeBanner from '../execution/OutcomeBanner'
import PlaybackControls from '../execution/PlaybackControls'
import { selectCurrentFrame, useExecutionStore } from '../../store/executionStore'
import ExecutionNarrative from './ExecutionNarrative'
import MemoryView from './MemoryView'
import RecursionBadge from './RecursionBadge'
import ValueRenderer from './ValueRenderer'

function VariablesView() {
  const frame = useExecutionStore(selectCurrentFrame)

  if (!frame) {
    return <p className="text-sm text-ink-soft">No active frame.</p>
  }

  const hasAny = frame.thisObject || frame.localVariables.length > 0

  if (!hasAny) {
    return <p className="text-sm text-ink-soft">No local variables at this step.</p>
  }

  return (
    <div className="space-y-2">
      {frame.thisObject && (
        <ValueRenderer
          name={frame.thisObject.name}
          declaredType={frame.thisObject.declaredType}
          value={frame.thisObject.value}
        />
      )}
      {frame.localVariables.map((variable) => (
        <ValueRenderer key={variable.name} name={variable.name} declaredType={variable.declaredType} value={variable.value} />
      ))}
    </div>
  )
}

function StructuralVisualizer({ code }) {
  const trace = useExecutionStore((state) => state.trace)
  const wasWrapped = useExecutionStore((state) => state.wasWrapped)

  if (!trace) {
    return (
      <p className="rounded-lg border border-line bg-paper-raised p-6 text-sm text-ink-soft">
        Click "Run &amp; Visualize" to see the live execution here — variables, arrays, trees, linked lists, call
        stack, and memory all update together as you step through.
      </p>
    )
  }

  return (
    <div className="space-y-4">
      {wasWrapped && (
        <p className="rounded-lg border border-line bg-highlight-ink/10 p-3 text-sm text-highlight-ink">
          This snippet had no runnable entry point, so a <code className="font-mono">Main</code> class and a sample
          call were auto-generated to run it. The editor now shows the version that was actually executed.
        </p>
      )}
      <OutcomeBanner />
      <PlaybackControls />
      <RecursionBadge />
      <ExecutionNarrative code={code} />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <CallStackPanel />
        <div className="rounded-lg border border-line bg-paper-raised p-4">
          <h2 className="mb-3 font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">Variables</h2>
          <VariablesView />
        </div>
        <div className="rounded-lg border border-line bg-paper-raised p-4">
          <h2 className="mb-3 font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">Memory</h2>
          <MemoryView />
        </div>
      </div>
    </div>
  )
}

export default StructuralVisualizer
