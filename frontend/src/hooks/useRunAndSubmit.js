import { useEffect, useRef, useState } from 'react'
import { validateSubmission } from '../lib/codeValidation'
import { useAnalysisStore } from '../store/analysisStore'
import { useExecutionStore } from '../store/executionStore'

/**
 * WorkspacePage's Run/Submit/Refresh orchestration: client-side validation,
 * dispatching to executionStore/analysisStore, tracking which action is
 * mid-flight (so only the clicked button shows a spinner), and the
 * Ctrl/Cmd+Enter shortcut for Run (matching LeetCode).
 *
 * @param code current editor source (read fresh on each call via closure -
 *             this hook re-runs every render, so handlers always see the
 *             latest code/language without needing them in a ref).
 * @param language current language selection.
 * @param setExecutedSource workspaceStore setter - called with the
 *             backend's (possibly wrapped) executed source after a
 *             successful run.
 * @param resetWorkspace workspaceStore action clearing the editor back to blank.
 */
export function useRunAndSubmit({ code, language, setActiveTab, setExecutedSource, resetWorkspace }) {
  const [pendingAction, setPendingAction] = useState(null)
  const runRef = useRef(null)

  const analysisSubmit = useAnalysisStore((state) => state.submit)
  const isAnalyzing = useAnalysisStore((state) => state.isLoading)
  const isExecuting = useExecutionStore((state) => state.isLoading)
  const executionSubmit = useExecutionStore((state) => state.submit)

  const isBusy = isAnalyzing || isExecuting

  // Run executes only (fast, no AI). Validates the code is analyzable first.
  const handleRun = () => {
    if (!code.trim() || isBusy) return
    const check = validateSubmission(code, language)
    if (!check.valid) {
      setActiveTab('result')
      useExecutionStore.getState().pause()
      useExecutionStore.setState({ error: check.message, trace: null, isLoading: false, currentStepIndex: 0 })
      return
    }
    setActiveTab('result')
    setPendingAction('run')
    executionSubmit(code, language)
      .then((response) => setExecutedSource(response.executedSourceCode))
      .catch(() => {})
      .finally(() => setPendingAction(null))
  }

  // Submit runs AI analysis AND execution/visualization together, in parallel
  // (Promise.all - both requests fire immediately, neither waits on the other).
  // Lands on the Analysis tab right away; each tab reads its own store's
  // isLoading/data independently, so whichever call finishes first renders
  // immediately instead of both being gated behind the slower one. Execution
  // is typically the faster call (sub-second) against the LLM-backed analysis
  // (several seconds), so in practice the Visualize tab's content is usually
  // ready well before the Analysis tab's is.
  const handleSubmit = async () => {
    if (!code.trim() || isBusy) return
    const check = validateSubmission(code, language)
    if (!check.valid) {
      setActiveTab('analysis')
      useAnalysisStore.setState({ error: check.message, currentAnalysis: null, isLoading: false })
      return
    }
    setActiveTab('analysis')
    setPendingAction('submit')
    try {
      await Promise.all([
        analysisSubmit(code, language),
        executionSubmit(code, language).then((response) => setExecutedSource(response.executedSourceCode)),
      ])
    } catch {
      // Each store already recorded its own error via its own submit() - see
      // executionStore/analysisStore. Nothing further to do here; this catch
      // only exists so a single failing call can't produce an unhandled
      // rejection while the other call is still in flight.
    } finally {
      setPendingAction(null)
    }
  }

  // Refresh clears everything back to a blank workspace.
  const handleRefresh = () => {
    setPendingAction(null)
    resetWorkspace()
    useExecutionStore.getState().reset()
    useAnalysisStore.getState().reset()
  }

  // Ctrl/Cmd+Enter runs (the fast, no-AI path). Registered once with a ref to
  // the latest handler to avoid stale-closure bugs.
  runRef.current = handleRun
  useEffect(() => {
    const onKeyDown = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        e.preventDefault()
        runRef.current?.()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

  return {
    pendingAction,
    isBusy,
    isExecuting,
    handleRun,
    handleSubmit,
    handleRefresh,
  }
}
