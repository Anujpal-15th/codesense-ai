import { useEffect, useRef } from 'react'
import Editor from '@monaco-editor/react'
import { selectCurrentLine, useExecutionStore } from '../../store/executionStore'
import { registerJavaFormatter } from './formatJava'

function WorkspaceEditor({ code, onCodeChange, height = '360px', theme = 'vs', language = 'java', onEditorMount }) {
  const editorRef = useRef(null)
  const monacoRef = useRef(null)
  const decorationsRef = useRef(null)

  const currentLine = useExecutionStore(selectCurrentLine)
  const trace = useExecutionStore((state) => state.trace)

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

    if (currentLine == null) {
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
  }, [currentLine])

  return (
    <div className="overflow-hidden rounded-b-lg border border-t-0 border-line">
      <Editor
        height={height}
        language={language}
        theme={theme}
        value={code}
        onChange={(value) => onCodeChange(value ?? '')}
        onMount={handleMount}
        options={{ readOnly: !!trace }}
      />
    </div>
  )
}

export default WorkspaceEditor
