import { useExecutionStore } from '../../store/executionStore'

function PlaybackControls() {
  const trace = useExecutionStore((state) => state.trace)
  const currentStepIndex = useExecutionStore((state) => state.currentStepIndex)
  const isPlaying = useExecutionStore((state) => state.isPlaying)
  const stepForward = useExecutionStore((state) => state.stepForward)
  const stepBackward = useExecutionStore((state) => state.stepBackward)
  const play = useExecutionStore((state) => state.play)
  const pause = useExecutionStore((state) => state.pause)
  const goToStep = useExecutionStore((state) => state.goToStep)

  if (!trace) return null

  const lastIndex = trace.steps.length - 1

  return (
    <div className="flex items-center gap-3 rounded-lg border border-line bg-paper-raised p-4">
      <button
        type="button"
        onClick={stepBackward}
        disabled={currentStepIndex === 0}
        className="rounded-md border border-line px-3 py-1.5 font-mono text-sm text-ink disabled:opacity-40"
      >
        ← Step
      </button>

      <button
        type="button"
        onClick={isPlaying ? pause : play}
        disabled={currentStepIndex >= lastIndex && !isPlaying}
        className="rounded-md bg-primary px-3 py-1.5 font-mono text-sm font-semibold text-white hover:bg-primary-hover disabled:opacity-40"
      >
        {isPlaying ? 'Pause' : 'Play'}
      </button>

      <button
        type="button"
        onClick={stepForward}
        disabled={currentStepIndex >= lastIndex}
        className="rounded-md border border-line px-3 py-1.5 font-mono text-sm text-ink disabled:opacity-40"
      >
        Step →
      </button>

      <input
        type="range"
        min={0}
        max={lastIndex}
        value={currentStepIndex}
        onChange={(e) => goToStep(Number(e.target.value))}
        className="flex-1 accent-primary"
      />

      <span className="font-mono text-sm whitespace-nowrap text-ink-soft">
        Step {currentStepIndex + 1} of {trace.steps.length}
      </span>
    </div>
  )
}

export default PlaybackControls
