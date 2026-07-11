import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getAnalysisById } from '../api/analysisApi'
import ResultCard from '../components/ResultCard'

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

function extractErrorMessage(error) {
  return error.response?.data?.error ?? error.message ?? 'Something went wrong'
}

// Deliberately local React state, NOT the shared analysisStore - that store is
// also what WorkspacePage's Analysis tab reads, and this page's fetched record
// has nothing to do with the workspace. Keeping it local means it's isolated by
// construction: it lives only as long as this component is mounted, and simply
// disappears (no cleanup needed) the moment the user navigates elsewhere.
function AnalysisDetailPage() {
  const { id } = useParams()
  const [analysis, setAnalysis] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setIsLoading(true)
    setError(null)
    setAnalysis(null)

    getAnalysisById(id)
      .then((data) => {
        if (!cancelled) setAnalysis(data)
      })
      .catch((err) => {
        if (!cancelled) setError(extractErrorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false)
      })

    // Guards against a stale fetch (e.g. navigating detail-of-A -> detail-of-B
    // while A's request is still in flight) from overwriting B's state.
    return () => {
      cancelled = true
    }
  }, [id])

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6">
      <Link to="/history" className="text-sm text-ink-soft hover:text-ink hover:underline">
        ← Back to history
      </Link>

      {isLoading && <p className="text-ink-soft">Loading…</p>}
      {error && <p className="text-correct">{error}</p>}
      {!isLoading && !error && analysis && (
        <>
          <SubmittedCode code={analysis.codeSnippet} />
          <ResultCard analysis={analysis} />
        </>
      )}
    </div>
  )
}

export default AnalysisDetailPage
