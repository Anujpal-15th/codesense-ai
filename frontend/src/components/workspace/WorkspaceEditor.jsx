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

  const currentLine = useExecutionStore(selectCurrentLine)

  function handleMount(editor, monaco) {
    editorRef.current = editor
    monacoRef.current = monaco
    decorationsRef.current = editor.createDecorationsCollection([])
    registerJavaFormatter(monaco)
    onEditorMount?.(editor, monaco)
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
        onChange={(value) => onCodeChange(value ?? '')}
        onMount={handleMount}
        options={{ readOnly, automaticLayout: true }}
      />
    </div>
  )
}

export default WorkspaceEditor
