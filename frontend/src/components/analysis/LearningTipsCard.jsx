import AIInsightCard from '../AIInsightCard'

function LearningTipsIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M12 3 L12 6" />
      <circle cx="12" cy="13" r="6" />
      <path d="M9.5 21 L14.5 21" />
    </svg>
  )
}

function LearningTipsCard({ learningTips, hasNewMetrics }) {
  // Same reasoning as BugsEdgeCasesCard: `learningTips` is an @ElementCollection field
  // that Hibernate always returns as an empty array (never null), even for pre-migration
  // rows, so emptiness alone can't tell "not available" apart from "LLM returned zero
  // tips." `hasNewMetrics` (a genuinely-nullable sibling scalar) is the real marker.
  const hasTips = hasNewMetrics && learningTips && learningTips.length > 0

  return (
    <AIInsightCard icon={<LearningTipsIcon />} title="Learning Tips">
      {hasNewMetrics ? (
        hasTips ? (
          <ul className="list-inside list-disc space-y-1 text-ink">
            {learningTips.map((tip, i) => (
              <li key={i}>{tip}</li>
            ))}
          </ul>
        ) : (
          <p className="text-ink">No learning tips for this analysis.</p>
        )
      ) : (
        <p className="text-ink-soft italic">Not available for this analysis.</p>
      )}
    </AIInsightCard>
  )
}

export default LearningTipsCard
