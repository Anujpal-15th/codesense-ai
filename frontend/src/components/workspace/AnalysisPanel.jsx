import ResultCard from '../ResultCard'
import { useAnalysisStore } from '../../store/analysisStore'

function AnalysisPanel({ hasEdited }) {
  const currentAnalysis = useAnalysisStore((state) => state.currentAnalysis)
  const isLoading = useAnalysisStore((state) => state.isLoading)
  const error = useAnalysisStore((state) => state.error)

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">AI Analysis</h2>
        {isLoading && <span className="font-mono text-xs text-ink-soft">Analyzing…</span>}
      </div>

      {error && <p className="text-sm text-correct">{error}</p>}

      {!hasEdited && !currentAnalysis && !error && (
        <p className="rounded-lg border border-line bg-paper-raised p-6 text-sm text-ink-soft">
          Start typing to get an analysis.
        </p>
      )}

      {hasEdited && !currentAnalysis && !error && (
        <p className="rounded-lg border border-line bg-paper-raised p-6 text-sm text-ink-soft">
          {isLoading ? 'Analyzing your code…' : 'Waiting for you to pause typing…'}
        </p>
      )}

      <ResultCard analysis={currentAnalysis} />
    </div>
  )
}

export default AnalysisPanel
