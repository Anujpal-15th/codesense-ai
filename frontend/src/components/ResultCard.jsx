import AIInsightCard from './AIInsightCard'
import BugsEdgeCasesCard from './analysis/BugsEdgeCasesCard'
import LearningTipsCard from './analysis/LearningTipsCard'
import ScoreCard from './analysis/ScoreCard'
import { ratingTone } from './ratingTone'

function Badge({ label, value }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-md border border-line bg-paper px-3 py-1.5 font-mono text-sm">
      <span className="text-ink-soft">{label}</span>
      <span className="font-bold text-ink">{value}</span>
    </span>
  )
}

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
      <div className="rounded-xl border border-line bg-paper-raised p-6">
        {/* Pattern name is the hero element. */}
        <h2 className="font-mono text-2xl font-bold tracking-tight text-ink sm:text-3xl">{analysis.pattern}</h2>

        {/* Complexity + verdict as clean text badges — no chart. */}
        <div className="mt-4 flex flex-wrap items-center gap-2">
          <Badge label="Time" value={analysis.timeComplexity} />
          <Badge label="Space" value={analysis.spaceComplexity} />
          <span className={`rounded-md px-3 py-1.5 font-mono text-sm font-bold uppercase ${verdictClass}`}>
            {analysis.isOptimal ? 'Optimal' : 'Not optimal'}
          </span>
        </div>

        <p className="mt-4 leading-relaxed text-ink-soft">{analysis.explanation}</p>
      </div>

      <div className="grid grid-cols-1 gap-4">
        <AIInsightCard icon={<CodeStyleIcon />} title="Code Style">
          {hasStyleData ? (
            <>
              {analysis.readability != null && (
                <div className="flex justify-between">
                  <span className="text-ink-soft">Readability</span>
                  <span className={`font-semibold ${ratingTone(analysis.readability)}`}>{analysis.readability}</span>
                </div>
              )}
              {analysis.structure != null && (
                <div className="flex justify-between">
                  <span className="text-ink-soft">Structure</span>
                  <span className={`font-semibold ${ratingTone(analysis.structure)}`}>{analysis.structure}</span>
                </div>
              )}
              {analysis.styleSuggestions != null && (
                <div className="pt-1">
                  <div className="text-ink-soft">Suggestions:</div>
                  <p className="text-ink">{analysis.styleSuggestions}</p>
                </div>
              )}
            </>
          ) : (
            <p className="text-ink-soft italic">Not available for this analysis.</p>
          )}
        </AIInsightCard>

        <AIInsightCard
          icon={<EfficiencyIcon />}
          title="Efficiency"
          chart={
            hasEfficiencyData ? (
              <span
                className={`font-mono text-sm font-bold ${analysis.isOptimal ? 'text-approve' : 'text-highlight-ink'}`}
              >
                {analysis.suggestedTimeComplexity ?? analysis.timeComplexity}
              </span>
            ) : null
          }
        >
          {hasEfficiencyData ? (
            <>
              <div className="flex justify-between">
                <span className="text-ink-soft">Current complexity</span>
                <span className="font-semibold text-ink">{analysis.timeComplexity}</span>
              </div>
              {analysis.suggestedTimeComplexity != null && (
                <div className="flex justify-between">
                  <span className="text-ink-soft">Suggested complexity</span>
                  <span className={`font-semibold ${analysis.isOptimal ? 'text-approve' : 'text-highlight-ink'}`}>
                    {analysis.suggestedTimeComplexity}
                  </span>
                </div>
              )}
              {analysis.efficiencySuggestions != null && (
                <div className="pt-1">
                  <div className="text-ink-soft">Suggestions:</div>
                  <p className="text-ink">{analysis.efficiencySuggestions}</p>
                </div>
              )}
            </>
          ) : (
            <p className="text-ink-soft italic">Not available for this analysis.</p>
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
