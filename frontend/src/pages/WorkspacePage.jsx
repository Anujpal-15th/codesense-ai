import { useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
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
import { useAnalysisStore } from '../store/analysisStore'
import { useExecutionStore } from '../store/executionStore'

const DEFAULT_CODE = `public class Main {\n    public static void main(String[] args) {\n        \n    }\n}\n`
const ANALYSIS_DEBOUNCE_MS = 1200

function WorkspacePage() {
  const [code, setCode] = useState(DEFAULT_CODE)
  const [hasEdited, setHasEdited] = useState(false)
  const [theme, setTheme] = useState('vs')
  const editorApiRef = useRef(null)

  const analysisSubmit = useAnalysisStore((state) => state.submit)
  const trace = useExecutionStore((state) => state.trace)
  const wasWrapped = useExecutionStore((state) => state.wasWrapped)
  const isVisualizing = useExecutionStore((state) => state.isLoading)
  const executionError = useExecutionStore((state) => state.error)
  const executionSubmit = useExecutionStore((state) => state.submit)
  const resetExecution = useExecutionStore((state) => state.reset)

  const isLocked = !!trace

  const handleCodeChange = (value) => {
    setHasEdited(true)
    setCode(value)
  }

  useEffect(() => {
    if (!hasEdited || !code.trim()) return

    const timerId = setTimeout(() => {
      analysisSubmit(code).catch(() => {})
    }, ANALYSIS_DEBOUNCE_MS)

    return () => clearTimeout(timerId)
  }, [code, hasEdited, analysisSubmit])

  const handleVisualize = () => {
    if (!code.trim()) return
    executionSubmit(code)
      .then((response) => {
        setHasEdited(true)
        setCode(response.executedSourceCode)
      })
      .catch(() => {})
  }

  const handleEditorMount = (editor, monaco) => {
    editorApiRef.current = { editor, monaco }
  }

  const handleFormat = () => {
    editorApiRef.current?.editor.getAction('editor.action.formatDocument')?.run()
  }

  const handleClear = () => handleCodeChange('')

  const handleExample = () => handleCodeChange(reconstructExamplePlainText())

  const handleCopy = () => {
    navigator.clipboard.writeText(code).catch(() => {})
  }

  return (
    <div className="mx-auto max-w-[1800px] space-y-6 p-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-xl font-extrabold text-ink">Workspace</h1>
        <div className="flex items-center gap-3">
          {trace && (
            <button
              type="button"
              onClick={resetExecution}
              className="rounded-full border border-line px-4 py-2 font-mono text-sm font-semibold text-ink"
            >
              Edit Code
            </button>
          )}
          <button
            type="button"
            onClick={handleVisualize}
            disabled={isVisualizing}
            className="rounded-full bg-ink px-5 py-2 font-mono text-sm font-semibold text-paper-raised disabled:opacity-50"
          >
            {isVisualizing ? 'Running…' : 'Run & Visualize'}
          </button>
        </div>
      </div>

      {executionError && <p className="text-sm text-correct">{executionError}</p>}

      {/*
        Fixed, permanent Execute/Visualize structure - identical in shape
        regardless of what the code does or how much data it produces (each
        panel below is self-null-safe and renders its own "no data yet" state
        pre-run, so there is no separate empty-state branch here).

        Grid: 3 columns (~40% / 30% / 30%, via fr units) x 2 rows on lg+.
          col 1, both rows : editor (toolbar, wrap notice, Monaco, playback
                              controls at the bottom of this same column)
          col 2-3, row 1   : outcome/recursion/narrative strip, spanning only
                              the call-stack + memory columns' combined width
          col 2, row 2     : Call Stack
          col 3, row 2     : Memory
        Below the grid, full width : Variables (never inside the 3-col row).
        Collapses to a single stacked column below `lg`, same convention used
        elsewhere on this page.
      */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,4fr)_minmax(0,3fr)_minmax(0,3fr)] lg:items-start">
        <div className="space-y-3 lg:col-start-1 lg:row-start-1 lg:row-span-2">
          <EditorToolbar
            theme={theme}
            onThemeChange={setTheme}
            onFormat={handleFormat}
            onClear={handleClear}
            onExample={handleExample}
            onCopy={handleCopy}
            disabled={isLocked}
          />
          {wasWrapped && (
            <p className="rounded-lg border border-line bg-highlight-ink/10 p-3 text-sm text-highlight-ink">
              This snippet had no runnable entry point, so a <code className="font-mono">Main</code> class and a
              sample call were auto-generated to run it. The editor now shows the version that was actually executed.
            </p>
          )}
          <WorkspaceEditor
            code={code}
            onCodeChange={handleCodeChange}
            height="360px"
            theme={theme}
            onEditorMount={handleEditorMount}
          />
          <PlaybackControls />
        </div>

        <div className="space-y-3 lg:col-start-2 lg:col-span-2 lg:row-start-1">
          <OutcomeBanner />
          <RecursionBadge />
          <ExecutionNarrative code={code} />
        </div>

        <div className="lg:col-start-2 lg:row-start-2">
          <CallStackPanel />
        </div>

        <div className="lg:col-start-3 lg:row-start-2">
          <div className="rounded-lg border border-line bg-paper-raised p-4">
            <h2 className="mb-3 font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">Memory</h2>
            <MemoryView />
          </div>
        </div>
      </div>

      <VariablesPanel />

      <motion.section
        layout
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.25, delay: 0.05 }}
        className="space-y-3"
      >
        <ConsoleOutputPanel />
      </motion.section>

      <motion.section
        layout
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.25, delay: 0.1 }}
        className="space-y-3"
      >
        <AnalysisPanel hasEdited={hasEdited} />
      </motion.section>
    </div>
  )
}

export default WorkspacePage
