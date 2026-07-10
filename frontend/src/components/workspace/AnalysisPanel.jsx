import ResultCard from '../ResultCard'
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

function VisualizeIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8">
      <path d="M2 12 C4 6.5 8.5 4.5 12 4.5 C15.5 4.5 20 6.5 22 12 C20 17.5 15.5 19.5 12 19.5 C8.5 19.5 4 17.5 2 12 Z" />
      <circle cx="12" cy="12" r="3" />
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
        <StartRow icon={<SubmitIcon />} action="Submit" description="to get AI analysis" />
        <StartRow icon={<VisualizeIcon />} action="Visualize" description="to step through execution" />
      </div>
    </div>
  )
}

function AnalysisPanel() {
  const currentAnalysis = useAnalysisStore((state) => state.currentAnalysis)
  const isLoading = useAnalysisStore((state) => state.isLoading)
  const error = useAnalysisStore((state) => state.error)

  if (error) {
    return <p className="rounded-lg border border-correct/30 bg-correct/10 p-3 text-sm text-correct">{error}</p>
  }

  if (isLoading) {
    return (
      <div className="flex items-center gap-3 rounded-lg border border-line bg-paper-raised p-6 text-sm text-ink-soft">
        <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
        Analyzing your code…
      </div>
    )
  }

  if (!currentAnalysis) {
    return <WelcomeEmptyState />
  }

  return <ResultCard analysis={currentAnalysis} />
}

export default AnalysisPanel
