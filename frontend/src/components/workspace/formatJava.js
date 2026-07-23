// Monaco's built-in "java" language mode ships tokenization only — no formatting
// provider — so `editor.action.formatDocument` silently no-ops for Java out of the
// box. This is a deliberately simple, honest two-pass formatter (not a real Java
// parser): first split compact/minified code onto separate lines around `{`/`}`/`;`
// (string/char/comment-aware), then re-indent each line by brace depth. Good enough
// for typical interview-style snippets, not a full pretty-printer.
const INDENT_UNIT = '    '

/** Index of the first non-whitespace character at or after `i` - space, tab,
 * \r, \n only, never crosses into a comment/string (those start with a
 * non-whitespace character, so the scan simply stops there). */
function skipWhitespace(source, i) {
  while (i < source.length && ' \t\r\n'.includes(source[i])) i++
  return i
}

// Splits already-compact/minified code onto separate lines by inserting a newline
// after `{`, before/after `}`, and after `;` — skipped while scanning inside a string
// or char literal (tracked via quote state + escape handling) or a line comment, so we
// don't break code inside quoted text. This runs before the indentation pass below.
//
// Whitespace already present right after one of these split points is consumed
// (via skipWhitespace) rather than left in place - otherwise, for code that's
// ALREADY split across lines (not minified), the original newline and the
// freshly-inserted one both survive, doubling into a blank line after every
// statement and brace. Minified input (no whitespace there to begin with)
// behaves identically either way, so this doesn't change that original case.
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
      i = skipWhitespace(source, i + 1) - 1
      continue
    }
    if (ch === '}') {
      // Only add the leading newline if one isn't already there - the `;`/`{`
      // branches above already end their output in `\n`, so by the time a
      // normal (non-empty-block) `}` is reached, `result` already ends in
      // one; unconditionally adding another produced a blank line before
      // every closing brace.
      if (!result.endsWith('\n')) result += '\n'
      result += ch + '\n'
      i = skipWhitespace(source, i + 1) - 1
      continue
    }
    if (ch === ';') {
      result += ch + '\n'
      i = skipWhitespace(source, i + 1) - 1
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
