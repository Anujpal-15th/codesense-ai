import ConsolePanel from '../execution/ConsolePanel'
import { useExecutionStore } from '../../store/executionStore'

function ConsoleOutputPanel() {
  const trace = useExecutionStore((state) => state.trace)

  if (!trace) {
    return (
      <p className="rounded-lg border border-line bg-paper-raised p-6 text-sm text-ink-soft">
        Click "Run" to execute your code and see the output here.
      </p>
    )
  }

  return <ConsolePanel />
}

export default ConsoleOutputPanel
