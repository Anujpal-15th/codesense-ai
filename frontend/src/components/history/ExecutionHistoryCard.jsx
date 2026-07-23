import { Link } from 'react-router-dom'

function previewLines(execution) {
  const source = execution.codePreview
  if (!source) return []
  return source
    .replace(/\r\n/g, '\n')
    .split('\n')
    .filter((line) => line.trim().length > 0)
    .slice(0, 3)
}

function formatDate(createdAt) {
  if (!createdAt) return ''
  const d = new Date(createdAt)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}

function OutcomeBadge({ outcome }) {
  const isNormal = outcome === 'NORMAL'
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 font-mono text-xs font-bold uppercase tracking-wide ${
        isNormal ? 'bg-approve/10 text-approve' : 'bg-highlight-ink/10 text-highlight-ink'
      }`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${isNormal ? 'bg-approve' : 'bg-highlight-ink'}`} />
      {outcome}
    </span>
  )
}

function ExecutionHistoryCard({ execution }) {
  const preview = previewLines(execution)

  return (
    <Link
      to={`/history/executions/${execution.id}`}
      className="group block rounded-xl border border-line bg-paper-raised p-5 transition-all hover:-translate-y-0.5 hover:border-ink/30 hover:shadow-[0_6px_16px_rgba(0,0,0,0.06)]"
    >
      <div className="flex items-start justify-between gap-4">
        <h2 className="font-mono text-lg font-bold text-ink capitalize group-hover:text-primary">
          {execution.language}
        </h2>
        <span className="shrink-0 pt-1 text-xs whitespace-nowrap text-ink-soft">
          {formatDate(execution.createdAt)}
        </span>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <OutcomeBadge outcome={execution.outcome} />
        <span className="inline-flex items-center gap-1.5 rounded-md border border-line bg-paper px-2.5 py-1 font-mono text-xs text-ink">
          <span className="text-ink-soft">Steps</span>
          <span className="font-semibold">{execution.totalStepsCaptured}</span>
        </span>
      </div>

      {preview.length > 0 && (
        <pre className="mt-4 overflow-x-auto rounded-lg border border-line bg-paper px-3 py-2.5 font-mono text-[13px] leading-6 text-ink">
          {preview.map((line, i) => (
            <div key={i} className="truncate">
              {line}
            </div>
          ))}
        </pre>
      )}
    </Link>
  )
}

export default ExecutionHistoryCard
