import AIInsightCard from '../AIInsightCard'
import { ratingTone } from '../ratingTone'

function ScoreIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7 L12 12 L15.5 14" />
    </svg>
  )
}

function scoreTone(score) {
  if (score >= 70) return 'text-approve'
  if (score >= 40) return 'text-highlight-ink'
  return 'text-correct'
}

function ScoreCard({ overallScore, codeQuality, maintainability }) {
  const hasData = overallScore != null || codeQuality != null || maintainability != null

  return (
    <AIInsightCard
      icon={<ScoreIcon />}
      title="Overall Score"
      chart={overallScore != null ? <span className={`font-mono text-2xl font-bold ${scoreTone(overallScore)}`}>{overallScore}</span> : null}
    >
      {hasData ? (
        <>
          {codeQuality != null && (
            <div className="flex justify-between">
              <span className="text-ink-soft">Code Quality</span>
              <span className={`font-semibold ${ratingTone(codeQuality)}`}>{codeQuality}</span>
            </div>
          )}
          {maintainability != null && (
            <div className="flex justify-between">
              <span className="text-ink-soft">Maintainability</span>
              <span className={`font-semibold ${ratingTone(maintainability)}`}>{maintainability}</span>
            </div>
          )}
        </>
      ) : (
        <p className="text-ink-soft italic">Not available for this analysis.</p>
      )}
    </AIInsightCard>
  )
}

export default ScoreCard
