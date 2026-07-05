import { useMemo } from 'react'
import { useExecutionStore } from '../../store/executionStore'
import { computeStepChanges } from './stepDiff'

/**
 * The current step's change-set (from diffing step index-1 -> index), memoized
 * on (trace, index). Kept in its own file so StepChanges.jsx exports only
 * components (React fast-refresh requirement).
 */
export function useStepChanges() {
  const trace = useExecutionStore((state) => state.trace)
  const index = useExecutionStore((state) => state.currentStepIndex)
  return useMemo(() => computeStepChanges(trace, index), [trace, index])
}
