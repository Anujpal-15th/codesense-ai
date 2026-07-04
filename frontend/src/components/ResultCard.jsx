import AIInsightCard from './AIInsightCard'
import BugsEdgeCasesCard from './analysis/BugsEdgeCasesCard'
import LearningTipsCard from './analysis/LearningTipsCard'
import ScoreCard from './analysis/ScoreCard'
import MiniChart from './landing/MiniChart'
import { ratingTone } from './ratingTone'

function CodeStyleIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
      <polyline points="7 8 3 12 7 16" />
      <polyline points="17 8 21 12 17 16" />
      <line x1="14" y1="4" x2="10" y2="20" />
    </svg>
  )
}

function EfficiencyIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M3 17 L9 11 L13 15 L21 5" />
      <path d="M21 5 L21 11" />
      <path d="M21 5 L15 5" />
    </svg>
  )
}

function EfficiencyChart({ isOptimal }) {
  const chartColor = isOptimal ? 'var(--color-approve)' : 'var(--color-highlight-ink)'
  return (
    <div className="relative h-8 w-16">
      <svg viewBox="0 0 100 48" className="absolute inset-0 h-full w-full" preserveAspectRatio="none">
        <path
          d="M4,40 C 30,38 55,30 86,20"
          fill="none"
          stroke="var(--color-paper-raised)"
          strokeWidth="2"
          strokeLinecap="round"
          opacity="0.15"
        />
        <path
          d="M4,44 C 30,40 55,34 86,26"
          fill="none"
          stroke="var(--color-paper-raised)"
          strokeWidth="2"
          strokeLinecap="round"
          opacity="0.1"
        />
      </svg>
      <MiniChart d="M4,34 C 30,30 55,20 86,8" color={chartColor} />
    </div>
  )
}

function ResultCard({ analysis }) {
  if (!analysis) return null

  const verdictClass = analysis.isOptimal ? 'bg-approve/10 text-approve' : 'bg-correct/10 text-correct'
  const hasStyleData = analysis.readability != null || analysis.structure != null || analysis.styleSuggestions != null
  const hasEfficiencyData = analysis.suggestedTimeComplexity != null || analysis.efficiencySuggestions != null
  // overallScore/codeQuality/maintainability are plain nullable scalar columns (not
  // @ElementCollection), so unlike bugs/edgeCases/learningTips they genuinely stay null
  // for pre-migration rows — a reliable marker for "this row has the new metrics at all".
  const hasNewMetrics = analysis.overallScore != null || analysis.codeQuality != null || analysis.maintainability != null

  return (
    <div className="space-y-4">
      <div className="space-y-3 rounded-lg border border-line bg-paper-raised p-6">
        <div className="flex items-center justify-between gap-4">
          <h2 className="font-mono text-xl font-semibold text-ink">{analysis.pattern}</h2>
          <span className={`rounded-full px-3 py-1 font-mono text-sm font-medium uppercase ${verdictClass}`}>
            {analysis.isOptimal ? 'Optimal' : 'Not optimal'}
          </span>
        </div>

        <div className="flex gap-6 text-sm text-ink-soft">
          <span>
            Time: <code className="font-mono text-ink">{analysis.timeComplexity}</code>
          </span>
          <span>
            Space: <code className="font-mono text-ink">{analysis.spaceComplexity}</code>
          </span>
        </div>

        <p className="text-ink-soft">{analysis.explanation}</p>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <AIInsightCard icon={<CodeStyleIcon />} title="Code Style">
          {hasStyleData ? (
            <>
              {analysis.readability != null && (
                <div className="flex justify-between">
                  <span className="text-paper-raised/60">Readability</span>
                  <span className={`font-semibold ${ratingTone(analysis.readability)}`}>{analysis.readability}</span>
                </div>
              )}
              {analysis.structure != null && (
                <div className="flex justify-between">
                  <span className="text-paper-raised/60">Structure</span>
                  <span className={`font-semibold ${ratingTone(analysis.structure)}`}>{analysis.structure}</span>
                </div>
              )}
              {analysis.styleSuggestions != null && (
                <div className="pt-1">
                  <div className="text-paper-raised/60">Suggestions:</div>
                  <p className="text-paper-raised">{analysis.styleSuggestions}</p>
                </div>
              )}
            </>
          ) : (
            <p className="text-paper-raised/50 italic">Not available for this analysis.</p>
          )}
        </AIInsightCard>

        <AIInsightCard
          icon={<EfficiencyIcon />}
          title="Efficiency"
          chart={
            hasEfficiencyData ? (
              <div className="flex flex-col items-end gap-1">
                <span className="font-mono text-xs font-bold text-paper-raised">
                  {analysis.suggestedTimeComplexity ?? analysis.timeComplexity}
                </span>
                <EfficiencyChart isOptimal={analysis.isOptimal} />
              </div>
            ) : null
          }
        >
          {hasEfficiencyData ? (
            <>
              <div className="flex justify-between">
                <span className="text-paper-raised/60">Current complexity</span>
                <span className="font-semibold text-paper-raised">{analysis.timeComplexity}</span>
              </div>
              {analysis.suggestedTimeComplexity != null && (
                <div className="flex justify-between">
                  <span className="text-paper-raised/60">Suggested complexity</span>
                  <span className={`font-semibold ${analysis.isOptimal ? 'text-approve' : 'text-highlight-ink'}`}>
                    {analysis.suggestedTimeComplexity}
                  </span>
                </div>
              )}
              {analysis.efficiencySuggestions != null && (
                <div className="pt-1">
                  <div className="text-paper-raised/60">Suggestions:</div>
                  <p className="text-paper-raised">{analysis.efficiencySuggestions}</p>
                </div>
              )}
            </>
          ) : (
            <p className="text-paper-raised/50 italic">Not available for this analysis.</p>
          )}
        </AIInsightCard>

        <ScoreCard
          overallScore={analysis.overallScore}
          codeQuality={analysis.codeQuality}
          maintainability={analysis.maintainability}
        />
        <BugsEdgeCasesCard bugs={analysis.bugs} edgeCases={analysis.edgeCases} hasNewMetrics={hasNewMetrics} />
        <LearningTipsCard learningTips={analysis.learningTips} hasNewMetrics={hasNewMetrics} />
      </div>
    </div>
  )
}

export default ResultCard
