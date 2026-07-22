import { selectCurrentFrame, selectCurrentStep, useExecutionStore } from '../../store/executionStore'
import ValueRenderer from './ValueRenderer'

// Selects tracedSource - not workspaceStore's executedSource - deliberately.
// tracedSource is frozen at the moment the trace arrived, so this narration
// keeps quoting the line that actually ran even if the user goes on editing
// the code shown alongside it on the Visualize tab.
function selectTracedSource(state) {
  return state.tracedSource
}

function formatLiteral(value) {
  if (!value) return '?'
  switch (value.valueKind) {
    case 'primitive':
      return value.literal
    case 'string':
      return `"${value.value}"${value.truncated ? '…' : ''}`
    case 'null':
      return 'null'
    case 'array':
      return `${value.componentType}[${value.length}]`
    case 'object':
      return value.type
    case 'map':
    case 'set':
    case 'list':
      return `${value.type}(${value.size})`
    default:
      return '?'
  }
}

function buildNarration(step, frame, code) {
  if (!step || !frame) return null

  if (step.eventType === 'METHOD_ENTRY') {
    const args = frame.localVariables.map((v) => `${v.name}=${formatLiteral(v.value)}`)
    return {
      text:
        args.length > 0
          ? `Entered ${frame.className}.${frame.methodName}() — called with ${args.join(', ')}`
          : `Entered ${frame.className}.${frame.methodName}() — no arguments`,
    }
  }

  if (step.eventType === 'LINE') {
    const sourceLine = code?.split('\n')[frame.lineNumber - 1]?.trim()
    return {
      text: sourceLine ? `Executing line ${frame.lineNumber}: ${sourceLine}` : `Executing line ${frame.lineNumber}`,
    }
  }

  if (step.eventType === 'METHOD_EXIT') {
    return { text: `Returned from ${frame.className}.${frame.methodName}()` }
  }

  return null
}

function ExecutionNarrative() {
  const step = useExecutionStore(selectCurrentStep)
  const frame = useExecutionStore(selectCurrentFrame)
  const tracedSource = useExecutionStore(selectTracedSource)

  if (!step || !frame) return null

  const narration = buildNarration(step, frame, tracedSource)
  const showReturnValue = step.eventType === 'METHOD_EXIT' && step.returnValue != null

  return (
    <div className="rounded-lg border border-line bg-paper-raised p-4">
      <h2 className="mb-3 font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">
        What's Happening
      </h2>
      <div className="space-y-3">
        <p className="font-mono text-sm text-ink">{narration?.text}</p>
        {showReturnValue && (
          <div className="space-y-1">
            <div className="font-mono text-xs text-ink-soft">Returned</div>
            <ValueRenderer name="" declaredType="" value={step.returnValue} />
          </div>
        )}
      </div>
    </div>
  )
}

export default ExecutionNarrative
