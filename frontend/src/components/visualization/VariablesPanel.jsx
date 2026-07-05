import { selectCurrentFrame, useExecutionStore } from '../../store/executionStore'
import ValueRenderer from './ValueRenderer'
import { StepChangesProvider } from './StepChanges'
import { useStepChanges } from './useStepChanges'

function VariablesPanel() {
  const frame = useExecutionStore(selectCurrentFrame)
  const changes = useStepChanges()

  return (
    <div className="rounded-lg border border-line bg-paper-raised p-4">
      <h2 className="mb-3 font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">Variables</h2>
      <StepChangesProvider value={changes}>
        {(() => {
          if (!frame) {
            return <p className="text-sm text-ink-soft">No active frame.</p>
          }

          const hasAny = frame.thisObject || frame.localVariables.length > 0
          if (!hasAny) {
            return <p className="text-sm text-ink-soft">No local variables at this step.</p>
          }

          // Path root = the variable's name, matching stepDiff's frame-scoped
          // paths. `this` uses the literal name "this" (stepDiff does the same).
          return (
            <div className="space-y-2">
              {frame.thisObject && (
                <ValueRenderer
                  name={frame.thisObject.name}
                  declaredType={frame.thisObject.declaredType}
                  value={frame.thisObject.value}
                  path="this"
                />
              )}
              {frame.localVariables.map((variable) => (
                <ValueRenderer
                  key={variable.name}
                  name={variable.name}
                  declaredType={variable.declaredType}
                  value={variable.value}
                  path={variable.name}
                />
              ))}
            </div>
          )
        })()}
      </StepChangesProvider>
    </div>
  )
}

export default VariablesPanel
