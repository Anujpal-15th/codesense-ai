import { Component } from 'react'

// A malformed/unexpected trace shape (a future backend change, a corrupted
// history row) could throw during render anywhere deep in the visualization
// tree (TreeView, MemoryView, stepDiff...) - without a boundary, React
// unmounts the whole app and the user sees a blank page with no way back.
// This catches any render error below it and offers a way out instead.
class ErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { error: null }
  }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error, info) {
    console.error('Unhandled render error', error, info)
  }

  render() {
    if (this.state.error) {
      return (
        <div className="mx-auto max-w-lg space-y-4 p-10 text-center">
          <h1 className="font-mono text-lg font-bold text-ink">Something went wrong</h1>
          <p className="text-sm text-ink-soft">
            This page hit an unexpected error while rendering. Reloading usually clears it.
          </p>
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="uiverse-button-filled font-mono text-sm"
          >
            Reload
          </button>
        </div>
      )
    }
    return this.props.children
  }
}

export default ErrorBoundary
