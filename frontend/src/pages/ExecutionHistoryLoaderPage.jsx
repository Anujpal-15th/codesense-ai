import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useExecutionStore } from '../store/executionStore'
import { useWorkspaceStore } from '../store/workspaceStore'
import { extractErrorMessage } from '../lib/httpError'

// Deliberately not its own rendering path - a past execution is a trace, and
// the Visualize tab already renders traces. This page's only job is to fetch
// the stored record, populate the SAME live state Run/Submit populate
// (executionStore for the trace, workspaceStore for the editor/language/tab),
// then hand off to the Workspace. Reusing the real Visualize UI means there's
// exactly one place that ever needs to know how to render a trace.
function ExecutionHistoryLoaderPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const loadFromHistory = useExecutionStore((state) => state.loadFromHistory)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setError(null)

    loadFromHistory(id)
      .then((detail) => {
        if (cancelled) return
        useWorkspaceStore.setState({
          code: detail.sourceCode,
          language: detail.language,
          executedSource: detail.executedSourceCode,
          activeTab: 'visualize',
        })
        navigate('/analyze', { replace: true })
      })
      .catch((err) => {
        if (!cancelled) setError(extractErrorMessage(err))
      })

    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- id-keyed, deliberately not re-running on navigate/loadFromHistory identity
  }, [id])

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6">
      <Link to="/history" className="text-sm text-ink-soft hover:text-ink hover:underline">
        ← Back to history
      </Link>
      {error ? (
        <p className="text-correct">{error}</p>
      ) : (
        <p className="text-ink-soft">Loading…</p>
      )}
    </div>
  )
}

export default ExecutionHistoryLoaderPage
