// Shared across every store/page that calls the backend directly (executionStore,
// analysisStore, AnalysisDetailPage, ExecutionHistoryLoaderPage) - was
// copy-pasted byte-for-byte in all four places before this extraction.
export function extractErrorMessage(error) {
  return error.response?.data?.error ?? error.message ?? 'Something went wrong'
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
