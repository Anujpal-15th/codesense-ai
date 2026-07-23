import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import HistoryCard from '../components/history/HistoryCard'
import HistorySkeleton from '../components/history/HistorySkeleton'
import ExecutionHistoryCard from '../components/history/ExecutionHistoryCard'
import InlineError from '../components/InlineError'
import TabStrip from '../components/TabStrip'
import { useAnalysisStore } from '../store/analysisStore'
import { useExecutionStore } from '../store/executionStore'

const TABS = [
  { id: 'analyses', label: 'Analyses' },
  { id: 'runs', label: 'Runs' },
]

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <circle cx="11" cy="11" r="7" />
      <line x1="21" y1="21" x2="16.65" y2="16.65" />
    </svg>
  )
}

function EmptyState({ tab }) {
  const isRuns = tab === 'runs'
  return (
    <div className="rounded-xl border border-dashed border-line bg-paper-raised px-6 py-16 text-center">
      <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-full border border-line text-ink-soft">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" strokeWidth="1.6">
          <path d="M3 3v18h18" />
          <path d="M7 15l3-4 3 2 4-6" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </div>
      <p className="font-mono text-base font-semibold text-ink">{isRuns ? 'No runs yet' : 'No analyses yet'}</p>
      <p className="mx-auto mt-2 max-w-sm text-sm text-ink-soft">
        {isRuns
          ? 'Go to the Workspace and Run or Submit some code — every execution shows up here, ready to revisit.'
          : 'Go to the Workspace and submit your first solution — CodeSense will name the pattern, estimate its complexity, and it’ll show up here.'}
      </p>
      <Link
        to="/analyze"
        className="mt-6 inline-block rounded-full bg-ink px-5 py-2.5 font-mono text-sm font-semibold text-paper-raised transition-transform hover:-translate-y-0.5"
      >
        Go to Workspace →
      </Link>
    </div>
  )
}

function AnalysesTab({ query }) {
  const history = useAnalysisStore((state) => state.history)
  const isLoading = useAnalysisStore((state) => state.isLoading)
  const error = useAnalysisStore((state) => state.error)
  const fetchHistory = useAnalysisStore((state) => state.fetchHistory)

  useEffect(() => {
    fetchHistory()
  }, [fetchHistory])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return history
    return history.filter((a) => (a.pattern ?? '').toLowerCase().includes(q))
  }, [history, query])

  // Skeleton only on the very first load. On return visits the store still
  // holds the cached list, so it renders instantly and refreshes silently.
  const showSkeleton = isLoading && history.length === 0

  return (
    <>
      {error && !showSkeleton && <InlineError className="mb-4">{error}</InlineError>}
      {showSkeleton ? (
        <HistorySkeleton />
      ) : history.length === 0 && !error ? (
        <EmptyState tab="analyses" />
      ) : filtered.length === 0 ? (
        <p className="rounded-xl border border-dashed border-line bg-paper-raised px-6 py-10 text-center text-sm text-ink-soft">
          No patterns match &ldquo;{query}&rdquo;.
        </p>
      ) : (
        <div className="space-y-3">
          {filtered.map((analysis) => (
            <HistoryCard key={analysis.id} analysis={analysis} />
          ))}
        </div>
      )}
    </>
  )
}

function RunsTab() {
  const history = useExecutionStore((state) => state.history)
  const isLoading = useExecutionStore((state) => state.isHistoryLoading)
  const error = useExecutionStore((state) => state.historyError)
  const fetchHistory = useExecutionStore((state) => state.fetchHistory)

  useEffect(() => {
    fetchHistory()
  }, [fetchHistory])

  const showSkeleton = isLoading && history.length === 0

  return (
    <>
      {error && !showSkeleton && <InlineError className="mb-4">{error}</InlineError>}
      {showSkeleton ? (
        <HistorySkeleton />
      ) : history.length === 0 && !error ? (
        <EmptyState tab="runs" />
      ) : (
        <div className="space-y-3">
          {history.map((execution) => (
            <ExecutionHistoryCard key={execution.id} execution={execution} />
          ))}
        </div>
      )}
    </>
  )
}

function HistoryPage() {
  const [tab, setTab] = useState('analyses')
  const [query, setQuery] = useState('')
  const analysesCount = useAnalysisStore((state) => state.history.length)
  const showSearch = tab === 'analyses' && analysesCount > 3

  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-4xl space-y-6 p-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="text-2xl font-extrabold text-ink">History</h1>
            <p className="mt-1 text-sm text-ink-soft">Your past analyses and code runs</p>
          </div>

          {showSearch && (
            <div className="relative">
              <span className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-ink-soft">
                <SearchIcon />
              </span>
              <input
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Filter by pattern…"
                aria-label="Filter history by pattern"
                className="w-56 rounded-lg border border-line bg-paper-raised py-2 pr-3 pl-9 font-mono text-sm text-ink placeholder:text-ink-soft focus:border-primary focus:outline-none"
              />
            </div>
          )}
        </div>

        <TabStrip tabs={TABS} activeId={tab} onChange={setTab} />

        {tab === 'analyses' ? <AnalysesTab query={query} /> : <RunsTab />}
      </div>
    </div>
  )
}

export default HistoryPage
