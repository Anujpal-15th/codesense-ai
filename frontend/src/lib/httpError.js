// Shared across every store/page that calls the backend directly (executionStore,
// analysisStore, AnalysisDetailPage, ExecutionHistoryLoaderPage) - was
// copy-pasted byte-for-byte in all four places before this extraction.
//
// Must always return a string: every caller sets it straight into store state
// that gets rendered as a JSX child (<InlineError>{error}</InlineError> and
// similar). The backend's own errors are always {error: "string"} - but when
// the backend is unreachable, Vercel's rewrite returns ITS OWN gateway error
// page instead, shaped {error: {code, message}} - an object, not a string.
// Passing that straight through crashed the whole app (React refuses to
// render an object as text) instead of showing a plain inline message.
export function extractErrorMessage(error) {
  const raw = error.response?.data?.error
  if (typeof raw === 'string' && raw) return raw
  if (raw && typeof raw.message === 'string') return raw.message
  return error.message ?? 'Something went wrong'
}

// Thrown by a store action instead of applying its response when a newer
// request (or a reset()) has superseded it - e.g. the user hit Refresh or
// fired Submit again while a previous submit() was still in flight. Callers
// that already treat a rejected submit()/loadFromHistory() as "nothing to do"
// (WorkspacePage's .catch(() => {})) handle this for free; it exists as a
// distinct type only so a future caller could special-case it if needed.
export class StaleRequestError extends Error {
  constructor() {
    super('Superseded by a newer request')
    this.name = 'StaleRequestError'
  }
}
