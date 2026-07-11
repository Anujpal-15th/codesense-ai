import { useEffect } from 'react'
import { Link, useParams } from 'react-router-dom'
import ResultCard from '../components/ResultCard'
import { useAnalysisStore } from '../store/analysisStore'

function SubmittedCode({ code }) {
  if (!code) return null
  return (
    <div className="overflow-hidden rounded-[15px] border border-line bg-paper-raised">
      <div className="border-b border-line px-4 py-2.5 font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">
        Submitted code
      </div>
      <pre className="max-h-96 overflow-auto bg-paper p-4 font-mono text-[13px] leading-6 text-ink">
        {code.replace(/\r\n/g, '\n')}
      </pre>
    </div>
  )
}

function AnalysisDetailPage() {
  const { id } = useParams()
  const currentAnalysis = useAnalysisStore((state) => state.currentAnalysis)
  const isLoading = useAnalysisStore((state) => state.isLoading)
  const error = useAnalysisStore((state) => state.error)
  const fetchAnalysisById = useAnalysisStore((state) => state.fetchAnalysisById)

  useEffect(() => {
    fetchAnalysisById(id).catch(() => {})
  }, [id, fetchAnalysisById])

  // The list view and detail view share the store's `currentAnalysis` slot, so
  // a stale record from a previous card can flash before this id's data loads.
  // Only render the analysis once the loaded record actually matches the route.
  const analysisForThisRoute =
    currentAnalysis && String(currentAnalysis.id) === String(id) ? currentAnalysis : null

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6">
      <Link to="/history" className="text-sm text-ink-soft hover:text-ink hover:underline">
        ← Back to history
      </Link>

      {isLoading && !analysisForThisRoute && <p className="text-ink-soft">Loading…</p>}
      {error && <p className="text-correct">{error}</p>}
      {analysisForThisRoute && (
        <>
          <SubmittedCode code={analysisForThisRoute.codeSnippet} />
          <ResultCard analysis={analysisForThisRoute} />
        </>
      )}
    </div>
  )
}

export default AnalysisDetailPage
