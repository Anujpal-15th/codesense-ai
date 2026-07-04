import { selectCurrentFrame, useExecutionStore } from '../../store/executionStore'
import VariableTreeNode from './VariableTreeNode'

function VariableTree() {
  const frame = useExecutionStore(selectCurrentFrame)

  return (
    <div className="rounded-lg border border-line bg-paper-raised p-4">
      <h2 className="mb-3 font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">Variables</h2>
      {!frame ? (
        <p className="text-sm text-ink-soft">No active frame.</p>
      ) : (
        <div className="space-y-1">
          {frame.thisObject && (
            <VariableTreeNode
              name={frame.thisObject.name}
              declaredType={frame.thisObject.declaredType}
              value={frame.thisObject.value}
              depth={0}
            />
          )}
          {frame.localVariables.length === 0 && !frame.thisObject ? (
            <p className="text-sm text-ink-soft">No local variables at this step.</p>
          ) : (
            frame.localVariables.map((variable) => (
              <VariableTreeNode
                key={variable.name}
                name={variable.name}
                declaredType={variable.declaredType}
                value={variable.value}
                depth={0}
              />
            ))
          )}
        </div>
      )}
    </div>
  )
}

export default VariableTree
