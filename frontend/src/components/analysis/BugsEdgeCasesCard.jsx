import AIInsightCard from '../AIInsightCard'

function BugsIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="12" cy="13" r="6" />
      <path d="M9 4 L10.5 7 M15 4 L13.5 7 M2 10 L6 12 M22 10 L18 12 M2 18 L6 16 M22 18 L18 16" />
    </svg>
  )
}

function ListSection({ label, items, hasNewMetrics }) {
  // `items` is an @ElementCollection field — Hibernate always returns an empty array
  // (never null) even for pre-migration rows with no child-table entries, so an empty
  // array alone can't distinguish "LLM found none" from "this row predates the metric
  // entirely." `hasNewMetrics` (derived from a genuinely-nullable scalar sibling field)
  // is the reliable migration marker instead.
  if (!hasNewMetrics) {
    return (
      <div>
        <div className="text-paper-raised/60">{label}</div>
        <p className="text-paper-raised/50 italic">Not available for this analysis.</p>
      </div>
    )
  }

  if (!items || items.length === 0) {
    return (
      <div>
        <div className="text-paper-raised/60">{label}</div>
        <p className="text-paper-raised">No {label.toLowerCase()} found.</p>
      </div>
    )
  }

  return (
    <div>
      <div className="text-paper-raised/60">{label}</div>
      <ul className="list-inside list-disc space-y-1 text-paper-raised">
        {items.map((item, i) => (
          <li key={i}>{item}</li>
        ))}
      </ul>
    </div>
  )
}

function BugsEdgeCasesCard({ bugs, edgeCases, hasNewMetrics }) {
  return (
    <AIInsightCard icon={<BugsIcon />} title="Bugs & Edge Cases">
      <div className="space-y-3">
        <ListSection label="Bugs" items={bugs} hasNewMetrics={hasNewMetrics} />
        <ListSection label="Edge cases" items={edgeCases} hasNewMetrics={hasNewMetrics} />
      </div>
    </AIInsightCard>
  )
}

export default BugsEdgeCasesCard
