import { selectCurrentFrame, selectCurrentStep, useExecutionStore } from '../../store/executionStore'
import ValueRenderer from './ValueRenderer'
import { useStepChanges } from './useStepChanges'
import { didExitingFrameGoDeeper, isRecursiveMethod } from './recursionAnalysis'

// Selects tracedSource - not workspaceStore's executedSource - deliberately.
// tracedSource is frozen at the moment the trace arrived, so this narration
// keeps quoting the line that actually ran even if the user goes on editing
// the code shown alongside it on the Visualize tab.
function selectTracedSource(state) {
  return state.tracedSource
}

// A bare identifier - "sum", "right", "this" - never "arr[3]", "map#k",
// "obj.field". Matches stepDiff's path grammar for a frame-local variable
// exactly (see fieldPath/indexPath/mapEntryPath/setElemPath in stepDiff.js) -
// deliberately scoped to top-level locals for this pass, not every nested
// field/element the diff can theoretically point at.
const TOP_LEVEL_VAR_PATH = /^[A-Za-z_$][\w$]*$/

function resolveTopLevelVar(frame, name) {
  if (name === 'this') return frame.thisObject?.value
  return frame.localVariables.find((v) => v.name === name)?.value
}

/**
 * Turns the step's already-computed value-change set (the same data that
 * drives the flash animations elsewhere) into plain-English deltas - "sum =
 * 8, right = 3" - instead of asking the model to guess at what a line of
 * arbitrary code "means". Real, already-verified data; never fabricated.
 */
function buildChangeSummary(frame, changes) {
  if (!frame || !changes) return []
  const names = [...changes.changed].filter((p) => TOP_LEVEL_VAR_PATH.test(p))
  return names
    .map((name) => {
      const value = resolveTopLevelVar(frame, name)
      return value ? `${name} = ${formatLiteral(value)}` : null
    })
    .filter(Boolean)
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

function buildNarration(step, frame, code, wentDeeper) {
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
    // wentDeeper === false means this specific call never went on to call
    // anything deeper before returning - the generic, trace-derived stand-in
    // for "hit the base case" (works for any recursion shape, not just pure
    // self-recursion). null means "not applicable" (e.g. non-recursive code)
    // and stays silent rather than claiming something the trace can't show.
    const suffix =
      wentDeeper === false
        ? ' — hit the base case'
        : wentDeeper === true
          ? ' — done after its own deeper call(s) resolved'
          : ''
    return { text: `Returned from ${frame.className}.${frame.methodName}()${suffix}` }
  }

  return null
}

function ExecutionNarrative() {
  const step = useExecutionStore(selectCurrentStep)
  const frame = useExecutionStore(selectCurrentFrame)
  const tracedSource = useExecutionStore(selectTracedSource)
  const trace = useExecutionStore((s) => s.trace)
  const currentStepIndex = useExecutionStore((s) => s.currentStepIndex)
  const changes = useStepChanges()

  if (!step || !frame) return null

  // "Base case" / "done after its own deeper call(s)" is reserved for
  // methods that actually recurse somewhere in this run - an ordinary
  // non-recursive return stays plain, same as before this feature existed.
  const wentDeeper =
    step.eventType === 'METHOD_EXIT' && isRecursiveMethod(trace, frame.className, frame.methodName)
      ? didExitingFrameGoDeeper(trace, currentStepIndex)
      : null
  const narration = buildNarration(step, frame, tracedSource, wentDeeper)
  const showReturnValue = step.eventType === 'METHOD_EXIT' && step.returnValue != null
  const changeSummary = buildChangeSummary(frame, changes)

  return (
    <div className="rounded-lg border border-line bg-paper-raised p-4">
      <h2 className="mb-3 font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">
        What's Happening
      </h2>
      <div className="space-y-3">
        <p className="font-mono text-sm text-ink">{narration?.text}</p>
        {changeSummary.length > 0 && (
          <p className="font-mono text-xs text-ink-soft">
            <span className="text-ink-soft/70">Since last step:</span> {changeSummary.join(', ')}
          </p>
        )}
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
