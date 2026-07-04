import { useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import AnalysisPanel from '../components/workspace/AnalysisPanel'
import ConsoleOutputPanel from '../components/workspace/ConsoleOutputPanel'
import EditorToolbar from '../components/workspace/EditorToolbar'
import { reconstructExamplePlainText } from '../components/workspace/exampleSnippet'
import WorkspaceEditor from '../components/workspace/WorkspaceEditor'
import StructuralVisualizer from '../components/visualization/StructuralVisualizer'
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
    <div className="mx-auto max-w-6xl space-y-6 p-6">
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

      <div>
        <EditorToolbar
          theme={theme}
          onThemeChange={setTheme}
          onFormat={handleFormat}
          onClear={handleClear}
          onExample={handleExample}
          onCopy={handleCopy}
          disabled={isLocked}
        />
        <WorkspaceEditor
          code={code}
          onCodeChange={handleCodeChange}
          height="360px"
          theme={theme}
          onEditorMount={handleEditorMount}
        />
      </div>

      <motion.section
        layout
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.25 }}
        className="space-y-3"
      >
        <h2 className="font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">
          Live Visualization
        </h2>
        <StructuralVisualizer code={code} />
      </motion.section>

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
