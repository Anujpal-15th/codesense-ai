// The "rounded-lg border border-correct/30 bg-correct/10 ..." error box was
// repeated verbatim across WorkspacePage, AnalysisPanel, and HistoryPage.
function InlineError({ children, className = '' }) {
  return (
    <p className={`rounded-lg border border-correct/30 bg-correct/10 p-3 text-sm text-correct ${className}`}>
      {children}
    </p>
  )
}

export default InlineError
