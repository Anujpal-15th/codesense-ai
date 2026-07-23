import { motion } from 'framer-motion'
import { selectCurrentStep, useExecutionStore } from '../../store/executionStore'
import InfoToggle from '../visualization/InfoToggle'
import { staggerDelaySeconds, POP_IN_DURATION_SECONDS } from '../visualization/staggerDelay'
import { useStepChanges } from '../visualization/useStepChanges'
import { didExitingFrameGoDeeper, frameStoryState, isRecursiveMethod } from '../visualization/recursionAnalysis'
import { frameQualifier } from '../visualization/nodeShape'

function frameClassName(state) {
  if (state === 'returning') return 'bg-highlight-ink/10 text-highlight-ink'
  if (state === 'active') return 'bg-approve/10 text-approve'
  return 'text-ink-soft'
}

function frameLabel(state, isBaseCase) {
  if (state === 'active') return 'active'
  if (state === 'waiting') return 'waiting'
  return isBaseCase ? 'returning · base case' : 'returning'
}

function CallStackPanel() {
  const step = useExecutionStore(selectCurrentStep)
  const trace = useExecutionStore((s) => s.trace)
  const currentStepIndex = useExecutionStore((s) => s.currentStepIndex)
  const changes = useStepChanges()

  // "Base case" only makes sense for a method that actually recurses
  // somewhere in this run - reserved for real recursion, never misapplied to
  // an ordinary helper call that simply returned without calling anything.
  const topFrame = step?.callStack?.[0]
  const isBaseCase =
    step?.eventType === 'METHOD_EXIT' &&
    didExitingFrameGoDeeper(trace, currentStepIndex) === false &&
    topFrame &&
    isRecursiveMethod(trace, topFrame.className, topFrame.methodName)

  return (
    <div className="rounded-lg border border-line bg-paper-raised p-4">
      <InfoToggle title="Call Stack">
        <p>
          <strong className="text-ink">active</strong> — the top frame, executing right now.
        </p>
        <p>
          <strong className="text-ink">waiting</strong> — a frame below the top: it called into the frame above and
          won&rsquo;t continue until that call returns.
        </p>
        <p>
          <strong className="text-ink">returning</strong> — the top frame is about to pop back to its caller.
          &ldquo;base case&rdquo; means this specific call never went on to call anything deeper itself before
          returning - it's the trace's own evidence, not a guess.
        </p>
        <p>
          <strong className="text-ink">Same method appears twice?</strong> — that&rsquo;s recursion. Each entry is
          its own independent call, with its own separate local variables, even though the method name repeats.
        </p>
      </InfoToggle>
      {!step || step.callStack.length === 0 ? (
        <p className="text-sm text-ink-soft">No active frames.</p>
      ) : (
        <ul className="space-y-1">
          {(() => {
            const total = step.callStack.length
            let newRank = 0
            return step.callStack.map((frame, index) => {
              const depth = total - 1 - index
              const isNew = changes.newFrameDepths.has(depth)
              const rank = isNew ? newRank++ : -1
              const state = frameStoryState(index, step.eventType)
              return (
                <motion.li
                  key={`${frame.className}.${frame.methodName}-${index}`}
                  initial={isNew ? { opacity: 0, y: -6, scale: 0.95 } : false}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  transition={{ duration: POP_IN_DURATION_SECONDS, delay: staggerDelaySeconds(rank) }}
                  className={`flex items-center justify-between gap-2 rounded px-2 py-1 font-mono text-sm ${frameClassName(state)}`}
                >
                  <span>
                    {frameQualifier(frame)}():{frame.lineNumber}
                  </span>
                  <span className="text-[10px] font-semibold tracking-wide uppercase opacity-70">
                    {frameLabel(state, index === 0 && isBaseCase)}
                  </span>
                </motion.li>
              )
            })
          })()}
        </ul>
      )}
    </div>
  )
}

export default CallStackPanel
