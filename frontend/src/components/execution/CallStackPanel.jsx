import { motion } from 'framer-motion'
import { selectCurrentStep, useExecutionStore } from '../../store/executionStore'
import InfoToggle from '../visualization/InfoToggle'
import { staggerDelaySeconds, POP_IN_DURATION_SECONDS } from '../visualization/staggerDelay'
import { useStepChanges } from '../visualization/useStepChanges'

function frameClassName(index, eventType) {
  if (index === 0 && eventType === 'METHOD_EXIT') {
    return 'bg-highlight-ink/10 text-highlight-ink'
  }
  if (index === 0) {
    return 'bg-approve/10 text-approve'
  }
  return 'text-ink-soft'
}

function CallStackPanel() {
  const step = useExecutionStore(selectCurrentStep)
  const changes = useStepChanges()

  return (
    <div className="rounded-lg border border-line bg-paper-raised p-4">
      <InfoToggle title="Call Stack">
        <p>
          <strong className="text-ink">Top frame (highlighted)</strong> — this is where execution is right now.
        </p>
        <p>
          <strong className="text-ink">Frames below</strong> — each one is &ldquo;on hold&rdquo;: it called into the
          frame above and won&rsquo;t continue until that call returns.
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
              return (
                <motion.li
                  key={`${frame.className}.${frame.methodName}-${index}`}
                  initial={isNew ? { opacity: 0, y: -6, scale: 0.95 } : false}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  transition={{ duration: POP_IN_DURATION_SECONDS, delay: staggerDelaySeconds(rank) }}
                  className={`rounded px-2 py-1 font-mono text-sm ${frameClassName(index, step.eventType)}`}
                >
                  {frame.className}.{frame.methodName}():{frame.lineNumber}
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
