import ResultCard from '../ResultCard'
import InlineError from '../InlineError'
import LoadingRow from '../LoadingRow'
import { useAnalysisStore } from '../../store/analysisStore'

function RunIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8">
      <polygon points="8 5 19 12 8 19 8 5" />
    </svg>
  )
}

function SubmitIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8">
      <path d="M12 3 L14 9.5 L20.5 11.5 L14 13.5 L12 20 L10 13.5 L3.5 11.5 L10 9.5 Z" strokeLinejoin="round" />
    </svg>
  )
}

function StartRow({ icon, action, description }) {
  return (
    <div className="flex items-center gap-3">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-line text-ink-soft">
        {icon}
      </div>
      <p className="text-left text-sm text-ink-soft">
        Click <span className="font-semibold text-ink">&quot;{action}&quot;</span> {description}
      </p>
    </div>
  )
}

function WelcomeEmptyState() {
  return (
    <div className="flex h-full flex-col items-center justify-center px-6 py-10 text-center">
      <h3 className="mb-1 font-mono text-base font-semibold text-ink">Welcome to your workspace</h3>
      <p className="mb-8 max-w-xs text-sm text-ink-soft">Write or paste Java code, then pick an action to get started.</p>
      <div className="w-full max-w-xs space-y-5">
        <StartRow icon={<RunIcon />} action="Run" description="to execute your code and see output" />
        <StartRow
          icon={<SubmitIcon />}
          action="Submit"
          description="to get AI analysis and a step-through visualization"
        />
      </div>
      <p className="mt-6 max-w-xs text-xs text-ink-soft">
        After Submit, open the <span className="font-semibold text-ink">Visualize</span> tab to step through
        execution line by line.
      </p>
    </div>
  )
}

function AnalysisPanel() {
  const currentAnalysis = useAnalysisStore((state) => state.currentAnalysis)
  const isLoading = useAnalysisStore((state) => state.isLoading)
  const error = useAnalysisStore((state) => state.error)

  if (error) {
    return <InlineError>{error}</InlineError>
  }

  if (isLoading) {
    return <LoadingRow>Analyzing your code…</LoadingRow>
  }

  if (!currentAnalysis) {
    return <WelcomeEmptyState />
  }

  return <ResultCard analysis={currentAnalysis} />
}

export default AnalysisPanel
