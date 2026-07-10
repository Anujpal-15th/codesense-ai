import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useAnalysisStore } from '../store/analysisStore'

function HistoryPage() {
  const history = useAnalysisStore((state) => state.history)
  const isLoading = useAnalysisStore((state) => state.isLoading)
  const error = useAnalysisStore((state) => state.error)
  const fetchHistory = useAnalysisStore((state) => state.fetchHistory)

  useEffect(() => {
    fetchHistory()
  }, [fetchHistory])

  return (
    <div className="mx-auto h-full max-w-4xl space-y-4 overflow-y-auto p-6">
      <h1 className="text-xl font-extrabold text-ink">History</h1>

      {isLoading && <p className="text-ink-soft">Loading...</p>}
      {error && <p className="text-correct">{error}</p>}
      {!isLoading && !error && history.length === 0 && (
        <div className="rounded-lg border border-dashed border-line bg-paper-raised p-10 text-center">
          <p className="font-mono text-sm font-semibold text-ink">No analyses yet</p>
          <p className="mx-auto mt-2 max-w-md text-sm text-ink-soft">
            Run your first analysis in the Workspace — paste a function and CodeSense will name the pattern and
            estimate its complexity. Your results show up here.
          </p>
          <Link
            to="/analyze"
            className="mt-6 inline-block rounded-full bg-ink px-5 py-2.5 font-mono text-sm font-semibold text-paper-raised"
          >
            Go to Workspace →
          </Link>
        </div>
      )}

      <ul className="space-y-2">
        {history.map((analysis) => (
          <li key={analysis.id}>
            <Link
              to={`/history/${analysis.id}`}
              className="block rounded-md border border-line bg-paper-raised p-4 hover:border-ink"
            >
              <div className="flex items-center justify-between gap-4">
                <span className="font-mono font-medium text-ink">{analysis.pattern}</span>
                <span className="text-sm text-ink-soft">{new Date(analysis.createdAt).toLocaleString()}</span>
              </div>
              <div className="text-sm text-ink-soft">
                Time: {analysis.timeComplexity} · Space: {analysis.spaceComplexity}
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}

export default HistoryPage
