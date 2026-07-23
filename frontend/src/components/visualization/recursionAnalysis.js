// Turns raw trace steps into the "story" of a call stack - which frame is
// active, which are waiting, which is returning, and whether a returning
// frame hit a base case - without fabricating anything the trace doesn't
// actually show. Shared between CallStackPanel and ExecutionNarrative so
// both tell the same story from the same data.

/**
 * Per-frame state for the call stack panel, derived purely from its position
 * in the CURRENT step's call stack (index 0 = innermost/top, per the trace's
 * "innermost-first" convention) and the step's event type:
 *  - 'active'    top frame, currently executing a line or just entered.
 *  - 'returning' top frame, about to pop (this step IS its exit).
 *  - 'waiting'   any frame below the top - it called into the frame above
 *                and is suspended until that call returns.
 */
export function frameStoryState(index, eventType) {
  if (index !== 0) return 'waiting'
  return eventType === 'METHOD_EXIT' ? 'returning' : 'active'
}

/**
 * For a METHOD_EXIT step, did the exiting invocation ever push a deeper
 * frame before returning? `false` is the generic, trace-derived stand-in for
 * "hit the base case" (this call never recursed/called deeper - it just
 * computed something and returned); `true` means it resolved after at least
 * one nested call finished first.
 *
 * Traces don't carry an explicit per-call identity (JDI/`sys.settrace` give
 * class+method+depth, not a call id), so "this exact invocation" is
 * reconstructed by matching its METHOD_ENTRY via a depth-aware backward scan
 * - the same idea as matching balanced parentheses: walking backward, an
 * EXIT at the same depth means "skip one prior sibling invocation at this
 * depth" (it already closed), so only the first *unmatched* ENTRY at that
 * depth is genuinely this invocation's start. Seeing any step strictly
 * deeper than that, before the matching entry is found, means this
 * invocation went on to call something deeper first.
 *
 * Returns null if `trace`/`exitStepIndex` don't point at a METHOD_EXIT step,
 * or if a well-formed trace somehow has no matching entry (defensive only -
 * shouldn't happen for a real trace).
 */
/**
 * Whether `className.methodName` ever appears more than once in the SAME
 * call stack anywhere in the trace - i.e. this method genuinely recurses
 * somewhere in this run, not just "called something deeper" once. Gates the
 * "hit the base case" / "done after its own deeper call(s)" narration so
 * that vocabulary is reserved for actual recursion, never misapplied to an
 * ordinary non-recursive helper call that simply happened to return without
 * calling anything else.
 */
export function isRecursiveMethod(trace, className, methodName) {
  if (!trace) return false
  const key = `${className}.${methodName}`
  return trace.steps.some((step) => {
    let count = 0
    for (const frame of step.callStack) {
      if (`${frame.className}.${frame.methodName}` === key) {
        count++
        if (count > 1) return true
      }
    }
    return false
  })
}

export function didExitingFrameGoDeeper(trace, exitStepIndex) {
  if (!trace) return null
  const steps = trace.steps
  const exitStep = steps[exitStepIndex]
  if (!exitStep || exitStep.eventType !== 'METHOD_EXIT') return null
  const exitDepth = exitStep.callStack.length

  let pendingExits = 0
  let wentDeeper = false
  for (let i = exitStepIndex - 1; i >= 0; i--) {
    const depth = steps[i].callStack.length
    if (depth > exitDepth && pendingExits === 0) {
      wentDeeper = true
    }
    if (depth === exitDepth) {
      if (steps[i].eventType === 'METHOD_EXIT') {
        pendingExits++
      } else if (steps[i].eventType === 'METHOD_ENTRY') {
        if (pendingExits > 0) {
          pendingExits--
        } else {
          return wentDeeper
        }
      }
    }
  }
  return wentDeeper
}
