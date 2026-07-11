import { useEffect, useRef, useState } from 'react'
import { NavLink } from 'react-router-dom'
import AnalysisPanel from '../components/workspace/AnalysisPanel'
import ConsoleOutputPanel from '../components/workspace/ConsoleOutputPanel'
import EditorToolbar from '../components/workspace/EditorToolbar'
import { reconstructExamplePlainText } from '../components/workspace/exampleSnippet'
import WorkspaceEditor from '../components/workspace/WorkspaceEditor'
import CallStackPanel from '../components/execution/CallStackPanel'
import OutcomeBanner from '../components/execution/OutcomeBanner'
import PlaybackControls from '../components/execution/PlaybackControls'
import ExecutionNarrative from '../components/visualization/ExecutionNarrative'
import MemoryView from '../components/visualization/MemoryView'
import RecursionBadge from '../components/visualization/RecursionBadge'
import VariablesPanel from '../components/visualization/VariablesPanel'
import { validateJavaSubmission } from '../lib/codeValidation'
import { useAnalysisStore } from '../store/analysisStore'
import { useExecutionStore } from '../store/executionStore'
import { useWorkspaceStore } from '../store/workspaceStore'

const TABS = [
  { id: 'analysis', label: 'Analysis' },
  { id: 'visualize', label: 'Visualize' },
  { id: 'result', label: 'Result' },
]

const navLinkClass = ({ isActive }) =>
  `font-mono text-sm font-medium px-3 py-2 ${isActive ? 'text-ink' : 'text-ink-soft hover:text-ink'}`

function Spinner() {
  return (
    <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z" />
    </svg>
  )
}

function EmptyState({ message }) {
  return (
    <div className="flex h-full flex-col items-center justify-center px-6 text-center">
      <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full border border-line text-ink-soft">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8">
          <polyline points="8 9 11 12 8 15" />
          <line x1="13" y1="15" x2="16" y2="15" />
          <rect x="3" y="4" width="18" height="16" rx="2" />
        </svg>
      </div>
      <p className="max-w-xs text-sm text-ink-soft">{message}</p>
    </div>
  )
}

function WorkspacePage() {
  // Code + view state live in a persisted store so they survive navigation
  // (Workspace -> History -> back) and reloads. See workspaceStore.
  const code = useWorkspaceStore((state) => state.code)
  const setCode = useWorkspaceStore((state) => state.setCode)
  const activeTab = useWorkspaceStore((state) => state.activeTab)
  const setActiveTab = useWorkspaceStore((state) => state.setActiveTab)
  const executedSource = useWorkspaceStore((state) => state.executedSource)
  const setExecutedSource = useWorkspaceStore((state) => state.setExecutedSource)
  const resetWorkspace = useWorkspaceStore((state) => state.resetWorkspace)

  const [copied, setCopied] = useState(false)
  // Which action is mid-flight, so only the clicked button shows a spinner.
  const [pendingAction, setPendingAction] = useState(null)
  const [leftPct, setLeftPct] = useState(40)
  const [dragging, setDragging] = useState(false)
  const [isDesktop, setIsDesktop] = useState(true)

  const editorApiRef = useRef(null)
  const copiedTimerRef = useRef(null)
  const runRef = useRef(null)
  const splitRef = useRef(null)
  const draggingRef = useRef(false)

  const analysisSubmit = useAnalysisStore((state) => state.submit)
  const isAnalyzing = useAnalysisStore((state) => state.isLoading)

  const trace = useExecutionStore((state) => state.trace)
  const isExecuting = useExecutionStore((state) => state.isLoading)
  const executionError = useExecutionStore((state) => state.error)
  const executionSubmit = useExecutionStore((state) => state.submit)

  const isBusy = isAnalyzing || isExecuting
  const isVisualizeTab = activeTab === 'visualize'
  // Editor is only read-only while the Visualize tab is showing a trace - it
  // stays editable everywhere else. Refresh is the escape hatch.
  const isEditorLocked = isVisualizeTab && !!trace
  const editorValue = isVisualizeTab && executedSource != null ? executedSource : code

  // Run executes only (fast, no AI). Validates the code is analyzable Java first.
  const handleRun = () => {
    if (!code.trim() || isBusy) return
    const check = validateJavaSubmission(code)
    if (!check.valid) {
      setActiveTab('result')
      useExecutionStore.getState().pause()
      useExecutionStore.setState({ error: check.message, trace: null, isLoading: false, currentStepIndex: 0 })
      return
    }
    setActiveTab('result')
    setPendingAction('run')
    executionSubmit(code)
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
    const check = validateJavaSubmission(code)
    if (!check.valid) {
      setActiveTab('analysis')
      useAnalysisStore.setState({ error: check.message, currentAnalysis: null, isLoading: false })
      return
    }
    setActiveTab('analysis')
    setPendingAction('submit')
    try {
      await Promise.all([
        analysisSubmit(code),
        executionSubmit(code).then((response) => setExecutedSource(response.executedSourceCode)),
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

  // Ctrl/Cmd+Enter runs (the fast, no-AI path), matching LeetCode. Registered
  // once with a ref to the latest handler to avoid stale-closure bugs.
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

  // The Analysis tab reads analysisStore.currentAnalysis - the same store slot
  // AnalysisDetailPage used to write into (that page now uses local state
  // instead, but this guard stays regardless: anything else that ever sets
  // currentAnalysis outside a workspace submit shouldn't leak in here either).
  // Clearing on mount means arriving at /analyze always starts clean.
  useEffect(() => {
    useAnalysisStore.getState().reset()
  }, [])

  // Track desktop vs mobile so the drag-resized width only applies on lg+
  // (below that the panels stack vertically and a % width would be wrong).
  useEffect(() => {
    const mq = window.matchMedia('(min-width: 1024px)')
    const update = () => setIsDesktop(mq.matches)
    update()
    mq.addEventListener('change', update)
    return () => mq.removeEventListener('change', update)
  }, [])

  // Draggable divider: recompute the left panel width from the pointer's x
  // position within the split container, clamped so neither panel drops below 25%.
  useEffect(() => {
    const onMove = (e) => {
      if (!draggingRef.current || !splitRef.current) return
      const rect = splitRef.current.getBoundingClientRect()
      const pct = ((e.clientX - rect.left) / rect.width) * 100
      setLeftPct(Math.min(75, Math.max(25, pct)))
    }
    const onUp = () => {
      if (draggingRef.current) {
        draggingRef.current = false
        setDragging(false)
      }
    }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
    return () => {
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
    }
  }, [])

  const handleDragStart = () => {
    draggingRef.current = true
    setDragging(true)
  }

  const handleEditorMount = (editor, monaco) => {
    editorApiRef.current = { editor, monaco }
  }

  const handleFormat = () => {
    editorApiRef.current?.editor.getAction('editor.action.formatDocument')?.run()
  }

  const handleExample = () => setCode(reconstructExamplePlainText())

  const handleCopy = () => {
    navigator.clipboard
      .writeText(code)
      .then(() => {
        setCopied(true)
        clearTimeout(copiedTimerRef.current)
        copiedTimerRef.current = setTimeout(() => setCopied(false), 1500)
      })
      .catch(() => {})
  }

  useEffect(() => () => clearTimeout(copiedTimerRef.current), [])

  const leftStyle = isDesktop ? { width: `${leftPct}%` } : undefined
  const runLoading = pendingAction === 'run' && isExecuting
  const submitLoading = pendingAction === 'submit' && isBusy

  return (
    <div className="flex h-screen flex-col bg-paper text-ink">
      {/* TOP BAR: logo left · Run + Submit + nav grouped right */}
      <header className="flex shrink-0 items-center justify-between gap-4 border-b border-line bg-paper-raised px-4 py-2">
        <NavLink to="/" className="flex items-center gap-2 font-mono text-lg font-bold">
          <span className="h-2 w-2 rounded-full bg-approve" />
          codesense <span className="font-normal text-ink-soft">.ai</span>
        </NavLink>

        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleRun}
              disabled={isBusy}
              title="Run — execute and see output (Ctrl+Enter)"
              className="uiverse-button-outline inline-flex items-center gap-2 font-mono text-sm font-semibold disabled:opacity-50"
            >
              {runLoading && <Spinner />}
              {runLoading ? 'Running…' : 'Run'}
            </button>
            <button
              type="button"
              onClick={handleSubmit}
              disabled={isBusy}
              title="Submit — analyze and visualize your code"
              className="uiverse-button-filled inline-flex items-center gap-2 font-mono text-sm font-semibold disabled:opacity-50"
            >
              {submitLoading && <Spinner />}
              {submitLoading ? 'Analyzing…' : 'Submit'}
            </button>
          </div>

          <nav className="flex items-center gap-1 border-l border-line pl-3">
            <NavLink to="/analyze" className={navLinkClass}>
              Workspace
            </NavLink>
            <NavLink to="/history" className={navLinkClass}>
              History
            </NavLink>
          </nav>
        </div>
      </header>

      {/* SPLIT: results (left) | draggable divider | editor (right) */}
      <div
        ref={splitRef}
        className={`flex min-h-0 flex-1 flex-col lg:flex-row ${dragging ? 'select-none' : ''}`}
      >
        {/* LEFT — results/analysis panel with tabs */}
        <section
          style={leftStyle}
          className="flex min-h-0 min-w-0 flex-col overflow-hidden rounded-[15px] border-b border-line bg-paper-raised lg:h-full lg:border-b-0 lg:border-r"
        >
          <div className="flex shrink-0 gap-1 rounded-t-[15px] border-b border-line bg-paper-raised px-4">
            {TABS.map((tab) => (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id)}
                className={`-mb-px border-b-2 px-4 py-3 font-mono text-sm font-semibold ${
                  activeTab === tab.id
                    ? 'border-primary text-ink'
                    : 'border-transparent text-ink-soft hover:text-ink'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto rounded-lg p-4">
            {executionError && activeTab !== 'analysis' && (
              <p className="mb-4 rounded-lg border border-correct/30 bg-correct/10 p-3 text-sm text-correct">
                {executionError}
              </p>
            )}

            {activeTab === 'result' &&
              (trace ? (
                <div className="space-y-4">
                  <OutcomeBanner />
                  <ConsoleOutputPanel />
                </div>
              ) : isExecuting ? (
                <div className="flex items-center gap-3 rounded-lg border border-line bg-paper-raised p-6 text-sm text-ink-soft">
                  <Spinner />
                  Running your code…
                </div>
              ) : (
                !executionError && (
                  <EmptyState message="Click Run or Submit to see the execution outcome and console output here." />
                )
              ))}

            {activeTab === 'analysis' && <AnalysisPanel />}

            {activeTab === 'visualize' &&
              (trace ? (
                <div className="space-y-4">
                  <OutcomeBanner />
                  <RecursionBadge />
                  <ExecutionNarrative code={editorValue} />
                  <CallStackPanel />
                  <VariablesPanel />
                  <div className="rounded-lg border border-line bg-paper-raised p-4">
                    <MemoryView />
                  </div>
                </div>
              ) : isExecuting ? (
                <div className="flex items-center gap-3 rounded-lg border border-line bg-paper-raised p-6 text-sm text-ink-soft">
                  <Spinner />
                  Building visualization…
                </div>
              ) : (
                <EmptyState message="Click Submit (or Run) to build a step-through of your code's execution." />
              ))}
          </div>

          {isVisualizeTab && trace && (
            <div className="shrink-0 border-t border-line bg-paper-raised p-3">
              <PlaybackControls />
            </div>
          )}
        </section>

        {/* DRAGGABLE DIVIDER (desktop only) */}
        <div
          onMouseDown={handleDragStart}
          className="hidden w-1.5 shrink-0 cursor-col-resize bg-line transition-colors hover:bg-primary lg:block"
          role="separator"
          aria-orientation="vertical"
        />

        {/* RIGHT — code editor. min-w-0 lets this flex child shrink below its
            content width so dragging the divider resizes BOTH panels (without
            it, Monaco's intrinsic width blocks the left panel from growing). */}
        <section className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-[15px]">
          <EditorToolbar
            onFormat={handleFormat}
            onRefresh={handleRefresh}
            onExample={handleExample}
            onCopy={handleCopy}
            copied={copied}
            disabled={isEditorLocked}
          />
          <div className="h-[55vh] min-h-0 lg:h-auto lg:flex-1">
            <WorkspaceEditor
              code={editorValue}
              onCodeChange={setCode}
              onEditorMount={handleEditorMount}
              readOnly={isEditorLocked}
              highlightActive={isVisualizeTab}
            />
          </div>
        </section>

        {/* Overlay while dragging so the pointer stays captured over Monaco. */}
        {dragging && <div className="fixed inset-0 z-50 cursor-col-resize" />}
      </div>
    </div>
  )
}

export default WorkspacePage
