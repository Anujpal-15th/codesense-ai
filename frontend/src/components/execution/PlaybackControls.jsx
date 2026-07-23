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
  const jumpToNextChange = useExecutionStore((state) => state.jumpToNextChange)
  const jumpToPrevChange = useExecutionStore((state) => state.jumpToPrevChange)

  if (!trace) return null

  const lastIndex = trace.steps.length - 1

  // Compact size for this row specifically - the shared .uiverse-button-*
  // classes' default padding (0.6em 1.5em) is sized for the prominent
  // Run/Submit buttons in the header, not five buttons packed into one row
  // alongside a scrubber and step counter. At that size the labels wrapped
  // onto two lines inside each button and pushed the step counter text
  // completely off-screen. `!` forces these to win over the shared class's
  // padding without touching it (Run/Submit keep their original size).
  const compactBtn = 'font-mono text-xs !px-2.5 !py-1.5 whitespace-nowrap'

  return (
    <div className="flex items-center gap-1.5 rounded-lg border border-line bg-paper-raised p-3">
      <button
        type="button"
        onClick={jumpToPrevChange}
        disabled={currentStepIndex === 0}
        title="Jump to the previous step where something actually changed"
        className={`uiverse-button-outline ${compactBtn}`}
      >
        ⏮ Change
      </button>

      <button
        type="button"
        onClick={stepBackward}
        disabled={currentStepIndex === 0}
        className={`uiverse-button-outline ${compactBtn}`}
      >
        ← Step
      </button>

      <button
        type="button"
        onClick={isPlaying ? pause : play}
        disabled={currentStepIndex >= lastIndex && !isPlaying}
        className={`uiverse-button-filled ${compactBtn}`}
      >
        {isPlaying ? 'Pause' : 'Play'}
      </button>

      <button
        type="button"
        onClick={stepForward}
        disabled={currentStepIndex >= lastIndex}
        className={`uiverse-button-outline ${compactBtn}`}
      >
        Step →
      </button>

      <button
        type="button"
        onClick={jumpToNextChange}
        disabled={currentStepIndex >= lastIndex}
        title="Jump to the next step where something actually changes"
        className={`uiverse-button-outline ${compactBtn}`}
      >
        Change ⏭
      </button>

      <input
        type="range"
        min={0}
        max={lastIndex}
        value={currentStepIndex}
        onChange={(e) => goToStep(Number(e.target.value))}
        aria-label={`Step ${currentStepIndex + 1} of ${trace.steps.length}`}
        className="min-w-0 flex-1 accent-primary"
      />

      <span className="font-mono text-xs whitespace-nowrap text-ink-soft">
        Step {currentStepIndex + 1} of {trace.steps.length}
      </span>
    </div>
  )
}

export default PlaybackControls
