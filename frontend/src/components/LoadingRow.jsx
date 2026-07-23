import Spinner from './Spinner'

// The "flex items-center gap-3 rounded-lg border ..." loading box (spinner +
// message) was repeated verbatim across WorkspacePage, AnalysisPanel, and
// ConsoleOutputPanel.
function LoadingRow({ children }) {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-line bg-paper-raised p-6 text-sm text-ink-soft">
      <Spinner />
      {children}
    </div>
  )
}

export default LoadingRow
