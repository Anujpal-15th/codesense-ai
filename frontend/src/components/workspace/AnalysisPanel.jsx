import ResultCard from '../ResultCard'
import { useAnalysisStore } from '../../store/analysisStore'

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
    return (
      <p className="rounded-lg border border-line bg-paper-raised p-6 text-sm text-ink-soft">
        Click "Submit" to get an AI analysis of your code.
      </p>
    )
  }

  return <ResultCard analysis={currentAnalysis} />
}

export default AnalysisPanel
