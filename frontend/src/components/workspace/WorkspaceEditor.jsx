import { useEffect, useRef } from 'react'
import Editor from '@monaco-editor/react'
import { selectCurrentLine, useExecutionStore } from '../../store/executionStore'
import { registerJavaFormatter } from './formatJava'

function WorkspaceEditor({
  code,
  onCodeChange,
  height = '100%',
  theme = 'vs',
  language = 'java',
  onEditorMount,
  readOnly = false,
  highlightActive = true,
}) {
  const editorRef = useRef(null)
  const monacoRef = useRef(null)
  const decorationsRef = useRef(null)

  // Kept in a ref (updated during render, before any effect runs) so the
  // onChange handler below always sees the *current* readOnly, even when
  // Monaco's onChange subscription fires from a stale-closure render. A plain
  // `readOnly` prop read would be stale: @monaco-editor/react's value-prop sync
  // effect can fire onChange before it re-subscribes to the new handler.
  const readOnlyRef = useRef(readOnly)
  readOnlyRef.current = readOnly

  const currentLine = useExecutionStore(selectCurrentLine)

  function handleMount(editor, monaco) {
    editorRef.current = editor
    monacoRef.current = monaco
    decorationsRef.current = editor.createDecorationsCollection([])
    registerJavaFormatter(monaco)
    onEditorMount?.(editor, monaco)
  }

  // Monaco's onChange fires for programmatic model updates too, not just user
  // typing. On the Visualize tab the `value` prop is the executed (wrapped)
  // source, so without this guard that sync would call onCodeChange and
  // overwrite the user's original code. Read-only means "showing executed
  // source, not the user's code" - never propagate those changes upward.
  function handleChange(value) {
    if (readOnlyRef.current) return
    onCodeChange(value ?? '')
  }

  useEffect(() => {
    const collection = decorationsRef.current
    if (!collection) return

    // The current-line highlight only makes sense in the Visualize view, where
    // the editor shows the exact executed source. Gated so Run (which keeps the
    // editor editable on the user's own code) never paints a stale highlight.
    if (!highlightActive || currentLine == null) {
      collection.clear()
      return
    }

    const monaco = monacoRef.current
    collection.set([
      {
        range: new monaco.Range(currentLine, 1, currentLine, 1),
        options: {
          isWholeLine: true,
          className: 'execution-current-line',
          linesDecorationsClassName: 'execution-current-line-gutter',
        },
      },
    ])
    editorRef.current.revealLineInCenterIfOutsideViewport(currentLine)
  }, [currentLine, highlightActive])

  return (
    <div className="h-full overflow-hidden">
      <Editor
        height={height}
        language={language}
        theme={theme}
        value={code}
        onChange={handleChange}
        onMount={handleMount}
        options={{ readOnly, automaticLayout: true }}
      />
    </div>
  )
}

export default WorkspaceEditor
