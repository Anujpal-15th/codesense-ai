// Monaco's built-in "java" language mode ships tokenization only — no formatting
// provider — so `editor.action.formatDocument` silently no-ops for Java out of the
// box. This is a deliberately simple, honest two-pass formatter (not a real Java
// parser): first split compact/minified code onto separate lines around `{`/`}`/`;`
// (string/char/comment-aware), then re-indent each line by brace depth. Good enough
// for typical interview-style snippets, not a full pretty-printer.
const INDENT_UNIT = '    '

// Splits already-compact/minified code onto separate lines by inserting a newline
// after `{`, before/after `}`, and after `;` — skipped while scanning inside a string
// or char literal (tracked via quote state + escape handling) or a line comment, so we
// don't break code inside quoted text. This runs before the indentation pass below.
function splitStatements(source) {
  let result = ''
  let inString = false
  let inChar = false
  let inLineComment = false
  let inBlockComment = false

  for (let i = 0; i < source.length; i++) {
    const ch = source[i]
    const next = source[i + 1]

    if (inLineComment) {
      result += ch
      if (ch === '\n') inLineComment = false
      continue
    }
    if (inBlockComment) {
      result += ch
      if (ch === '*' && next === '/') {
        result += next
        i++
        inBlockComment = false
      }
      continue
    }
    if (inString) {
      result += ch
      if (ch === '\\') {
        result += next
        i++
      } else if (ch === '"') {
        inString = false
      }
      continue
    }
    if (inChar) {
      result += ch
      if (ch === '\\') {
        result += next
        i++
      } else if (ch === "'") {
        inChar = false
      }
      continue
    }

    if (ch === '/' && next === '/') {
      inLineComment = true
      result += ch
      continue
    }
    if (ch === '/' && next === '*') {
      inBlockComment = true
      result += ch
      continue
    }
    if (ch === '"') {
      inString = true
      result += ch
      continue
    }
    if (ch === "'") {
      inChar = true
      result += ch
      continue
    }

    if (ch === '{') {
      result += ch + '\n'
      continue
    }
    if (ch === '}') {
      result += '\n' + ch + '\n'
      continue
    }
    if (ch === ';') {
      result += ch + '\n'
      continue
    }

    result += ch
  }

  return result
}

export function formatJavaSource(source) {
  const lines = splitStatements(source).split(/\r?\n/)
  let depth = 0
  const result = []

  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (line === '') {
      result.push('')
      continue
    }

    let leadingCloses = 0
    for (const ch of line) {
      if (ch === '}') leadingCloses++
      else break
    }
    const printDepth = Math.max(0, depth - leadingCloses)
    result.push(INDENT_UNIT.repeat(printDepth) + line)

    let opens = 0
    let closes = 0
    for (const ch of line) {
      if (ch === '{') opens++
      else if (ch === '}') closes++
    }
    depth = Math.max(0, depth + opens - closes)
  }

  return result.join('\n')
}

let registered = false

export function registerJavaFormatter(monaco) {
  if (registered) return
  registered = true
  monaco.languages.registerDocumentFormattingEditProvider('java', {
    provideDocumentFormattingEdits(model) {
      return [{ range: model.getFullModelRange(), text: formatJavaSource(model.getValue()) }]
    },
  })
}
