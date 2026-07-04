import { useEffect } from 'react'
import { Link, useParams } from 'react-router-dom'
import ResultCard from '../components/ResultCard'
import { useAnalysisStore } from '../store/analysisStore'

function AnalysisDetailPage() {
  const { id } = useParams()
  const currentAnalysis = useAnalysisStore((state) => state.currentAnalysis)
  const isLoading = useAnalysisStore((state) => state.isLoading)
  const error = useAnalysisStore((state) => state.error)
  const fetchAnalysisById = useAnalysisStore((state) => state.fetchAnalysisById)

  useEffect(() => {
    fetchAnalysisById(id).catch(() => {})
  }, [id, fetchAnalysisById])

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6">
      <Link to="/history" className="text-sm text-ink-soft hover:text-ink hover:underline">
        ← Back to history
      </Link>

      {isLoading && <p className="text-ink-soft">Loading...</p>}
      {error && <p className="text-correct">{error}</p>}
      {!isLoading && !error && <ResultCard analysis={currentAnalysis} />}
    </div>
  )
}

export default AnalysisDetailPage
