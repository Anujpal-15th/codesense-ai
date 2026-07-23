import { useRef } from 'react'
import { NavLink } from 'react-router-dom'
import AnalysisPanel from '../components/workspace/AnalysisPanel'
import ConsoleOutputPanel from '../components/workspace/ConsoleOutputPanel'
import EditorToolbar from '../components/workspace/EditorToolbar'
import { PYTHON_EXAMPLE, reconstructExamplePlainText } from '../components/workspace/exampleSnippet'
import WorkspaceEditor from '../components/workspace/WorkspaceEditor'
import CallStackPanel from '../components/execution/CallStackPanel'
import OutcomeBanner from '../components/execution/OutcomeBanner'
import PlaybackControls from '../components/execution/PlaybackControls'
import ExecutionNarrative from '../components/visualization/ExecutionNarrative'
import MemoryView from '../components/visualization/MemoryView'
import RecursionBadge from '../components/visualization/RecursionBadge'
import VariablesPanel from '../components/visualization/VariablesPanel'
import VisualizeLegend from '../components/visualization/VisualizeLegend'
import { navLinkClass } from '../lib/navLinkClass'
import { useExecutionStore } from '../store/executionStore'
import { useWorkspaceStore } from '../store/workspaceStore'
import Spinner from '../components/Spinner'
import InlineError from '../components/InlineError'
import LoadingRow from '../components/LoadingRow'
import TabStrip from '../components/TabStrip'
import { useResizableSplit } from '../hooks/useResizableSplit'
import { useClipboardCopy } from '../hooks/useClipboardCopy'
import { useRunAndSubmit } from '../hooks/useRunAndSubmit'

const TABS = [
  { id: 'analysis', label: 'Analysis' },
  { id: 'visualize', label: 'Visualize' },
  { id: 'result', label: 'Result' },
]

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
  const language = useWorkspaceStore((state) => state.language)
  const setLanguage = useWorkspaceStore((state) => state.setLanguage)
  const activeTab = useWorkspaceStore((state) => state.activeTab)
  const setActiveTab = useWorkspaceStore((state) => state.setActiveTab)
  const executedSource = useWorkspaceStore((state) => state.executedSource)
  const setExecutedSource = useWorkspaceStore((state) => state.setExecutedSource)
  const resetWorkspace = useWorkspaceStore((state) => state.resetWorkspace)

  const editorApiRef = useRef(null)

  const trace = useExecutionStore((state) => state.trace)
  const executionError = useExecutionStore((state) => state.error)

  const { splitRef, dragging, leftPct, leftStyle, handleDragStart, handleDividerKeyDown } = useResizableSplit(40)
  const { status: copyStatus, copy: handleCopy } = useClipboardCopy(() => code)
  const { pendingAction, isBusy, isExecuting, handleRun, handleSubmit, handleRefresh } = useRunAndSubmit({
    code,
    language,
    setActiveTab,
    setExecutedSource,
    resetWorkspace,
  })

  const isVisualizeTab = activeTab === 'visualize'
  // Visualize shows the executed (wrapped) source so line highlighting lines
  // up with the trace - but it's always editable, same as the source on every
  // other tab. An edit made there updates BOTH executedSource (so Monaco's
  // controlled value doesn't get reverted next render) and code (so the next
  // Run/Submit actually sends the edit - code is what gets posted, not
  // executedSource). Without the second half of that, editing the wrapped
  // source on Visualize would look like it worked but silently not affect
  // the next run.
  const showingExecutedSource = isVisualizeTab && executedSource != null
  const editorValue = showingExecutedSource ? executedSource : code
  const handleEditorChange = (value) => {
    setCode(value)
    if (showingExecutedSource) setExecutedSource(value)
  }

  const handleEditorMount = (editor, monaco) => {
    editorApiRef.current = { editor, monaco }
  }

  const handleFormat = () => {
    editorApiRef.current?.editor.getAction('editor.action.formatDocument')?.run()
  }

  const handleExample = () =>
    setCode(language === 'python' ? PYTHON_EXAMPLE : reconstructExamplePlainText())

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
          <TabStrip
            tabs={TABS}
            activeId={activeTab}
            onChange={setActiveTab}
            tabPadding="py-3"
            className="shrink-0 rounded-t-[15px] bg-paper-raised px-4"
          />

          <div className="min-h-0 flex-1 overflow-y-auto rounded-lg p-4">
            {executionError && activeTab !== 'analysis' && (
              <InlineError className="mb-4">{executionError}</InlineError>
            )}

            {activeTab === 'result' &&
              (trace ? (
                <div className="space-y-4">
                  <OutcomeBanner />
                  <ConsoleOutputPanel />
                </div>
              ) : isExecuting ? (
                <LoadingRow>Running your code…</LoadingRow>
              ) : (
                !executionError && (
                  <EmptyState message="Click Run or Submit to see the execution outcome and console output here." />
                )
              ))}

            {activeTab === 'analysis' && <AnalysisPanel />}

            {activeTab === 'visualize' &&
              (trace ? (
                <div className="space-y-4">
                  <VisualizeLegend />
                  <OutcomeBanner />
                  <RecursionBadge />
                  <ExecutionNarrative />
                  <CallStackPanel />
                  <VariablesPanel />
                  <div className="rounded-lg border border-line bg-paper-raised p-4">
                    <MemoryView />
                  </div>
                </div>
              ) : isExecuting ? (
                <LoadingRow>Building visualization…</LoadingRow>
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
          onKeyDown={handleDividerKeyDown}
          className="hidden w-1.5 shrink-0 cursor-col-resize bg-line transition-colors hover:bg-primary focus-visible:bg-primary focus-visible:outline-none lg:block"
          role="separator"
          aria-orientation="vertical"
          aria-label="Resize panels"
          aria-valuenow={Math.round(leftPct)}
          aria-valuemin={25}
          aria-valuemax={75}
          tabIndex={0}
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
            copyStatus={copyStatus}
            language={language}
            onLanguageChange={setLanguage}
          />
          <div className="h-[55vh] min-h-0 lg:h-auto lg:flex-1">
            <WorkspaceEditor
              code={editorValue}
              onCodeChange={handleEditorChange}
              onEditorMount={handleEditorMount}
              highlightActive={isVisualizeTab}
              language={language}
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
