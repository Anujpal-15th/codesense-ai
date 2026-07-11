import { Link } from 'react-router-dom'

// First 2-3 non-blank lines of the submitted snippet, for an at-a-glance
// preview. Normalizes CRLF first - Monaco (and this DB's stored snippets)
// use \r\n, and splitting on \n alone would leave a trailing \r on every line.
function codePreview(snippet) {
  if (!snippet) return []
  return snippet
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

function ComplexityBadge({ label, value }) {
  if (!value) return null
  return (
    <span className="inline-flex items-center gap-1.5 rounded-md border border-line bg-paper px-2.5 py-1 font-mono text-xs text-ink">
      <span className="text-ink-soft">{label}</span>
      <span className="font-semibold">{value}</span>
    </span>
  )
}

function OptimalBadge({ isOptimal }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 font-mono text-xs font-bold uppercase tracking-wide ${
        isOptimal ? 'bg-approve/10 text-approve' : 'bg-correct/10 text-correct'
      }`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${isOptimal ? 'bg-approve' : 'bg-correct'}`} />
      {isOptimal ? 'Optimal' : 'Not optimal'}
    </span>
  )
}

function HistoryCard({ analysis }) {
  const preview = codePreview(analysis.codeSnippet)

  return (
    <Link
      to={`/history/${analysis.id}`}
      className="group block rounded-xl border border-line bg-paper-raised p-5 transition-all hover:-translate-y-0.5 hover:border-ink/30 hover:shadow-[0_6px_16px_rgba(0,0,0,0.06)]"
    >
      <div className="flex items-start justify-between gap-4">
        <h2 className="font-mono text-lg font-bold text-ink group-hover:text-primary">{analysis.pattern}</h2>
        <span className="shrink-0 pt-1 text-xs whitespace-nowrap text-ink-soft">
          {formatDate(analysis.createdAt)}
        </span>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <ComplexityBadge label="Time" value={analysis.timeComplexity} />
        <ComplexityBadge label="Space" value={analysis.spaceComplexity} />
        <OptimalBadge isOptimal={analysis.isOptimal} />
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

export default HistoryCard
