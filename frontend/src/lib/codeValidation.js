// Client-side submission guard, run before ANY call to /api/analyses or
// /api/executions. Mirrored on the backend by
// com.codesense.validation.CodeSubmissionValidator — keep the two in sync.
//
// Two rejections:
//   1. Non-Java code (Python / JavaScript / anything not Java-shaped).
//   2. Natural-language instructions ("create a merge sort") — CodeSense
//      analyzes code, it doesn't generate it (prompt-injection guard).

export const LANGUAGE_ERROR =
  "This doesn't look like Java. Switch the language selector if you're writing Python, or paste Java code."

export const INSTRUCTION_ERROR =
  "Please paste actual Java code, not instructions. CodeSense analyzes code — it doesn't generate it."

export const PYTHON_INSTRUCTION_ERROR =
  "Please paste actual Python code, not instructions. CodeSense analyzes code — it doesn't generate it."

export const JAVA_IN_PYTHON_ERROR =
  'This looks like Java code. Switch the language selector to Java, or paste Python code.'

// Replace the contents of string/char literals and comments with spaces
// (length preserved, newlines kept) so a `=>` or `print(` inside a Java string
// or comment can't trip the language signals below.
function maskLiteralsAndComments(code) {
  let out = ''
  let state = 'code' // code | line | block | string | char
  for (let i = 0; i < code.length; i++) {
    const c = code[i]
    const next = code[i + 1]
    if (state === 'code') {
      if (c === '/' && next === '/') { state = 'line'; out += '  '; i++; continue }
      if (c === '/' && next === '*') { state = 'block'; out += '  '; i++; continue }
      if (c === '"') { state = 'string'; out += ' '; continue }
      if (c === "'") { state = 'char'; out += ' '; continue }
      out += c
    } else if (state === 'line') {
      if (c === '\n') { state = 'code'; out += '\n' } else out += ' '
    } else if (state === 'block') {
      if (c === '*' && next === '/') { state = 'code'; out += '  '; i++; continue }
      out += c === '\n' ? '\n' : ' '
    } else if (state === 'string') {
      if (c === '\\') { out += '  '; i++; continue }
      if (c === '"') { state = 'code'; out += ' '; continue }
      out += c === '\n' ? '\n' : ' '
    } else if (state === 'char') {
      if (c === '\\') { out += '  '; i++; continue }
      if (c === "'") { state = 'code'; out += ' '; continue }
      out += ' '
    }
  }
  return out
}

// Concrete code punctuation or a Java type/method declaration.
function hasCodeSyntax(masked) {
  return (
    /[{}()[\];]/.test(masked) ||
    /\b(class|interface|enum)\s+\w+/.test(masked) ||
    /\b(public|private|protected|static|void)\b/.test(masked)
  )
}

const INSTRUCTION_VERBS =
  /^(create|write|implement|generate|make|build|code|develop|design|produce|give|show|provide|explain|solve|add|help|please|convert|reverse|sort|find|calculate|compute)\b/i

// Reads like prose telling us what to do, not code.
function looksLikeInstruction(rawCode) {
  const t = rawCode.trim()
  if (INSTRUCTION_VERBS.test(t)) return true
  const words = t.split(/\s+/)
  if (words.length < 2) return false
  const alphaWords = words.filter((w) => /^[A-Za-z][A-Za-z'-]*$/.test(w))
  return alphaWords.length / words.length > 0.6
}

const NON_JAVA_SIGNALS = [
  /\bdef\s+\w+\s*\(/, // Python function
  /\belif\b/, // Python
  /\bfrom\s+[\w.]+\s+import\b/, // Python from-import
  /\bimport\s+(numpy|pandas|os|sys|re|math|random|collections|json|typing|itertools)\b/, // Python modules
  /(^|\n)[ \t]*(if|elif|else|for|while|def|class|try|except|finally|with)\b[^\n{;]*:[ \t]*$/m, // Python colon block
  /(?<![.\w])print\s*\(/, // Python print()
  /\bconsole\s*\.\s*(log|error|warn|info)\s*\(/, // JS console
  /\bfunction\s+\w*\s*\(/, // JS function decl
  /=>/, // JS arrow
  /\b(const|let)\s+\w+\s*=/, // JS declarations
]

function usesNonJavaSyntax(masked) {
  return NON_JAVA_SIGNALS.some((re) => re.test(masked))
}

function looksLikeJava(masked) {
  return (
    /\b(class|interface|enum)\s+\w+/.test(masked) ||
    (/\b(public|private|protected|static|void|int|long|double|float|boolean|char|String)\b/.test(masked) &&
      /[{};]/.test(masked))
  )
}

// Returns { valid: true } or { valid: false, reason, message }.
export function validateJavaSubmission(rawCode) {
  const code = (rawCode ?? '').trim()
  if (!code) return { valid: false, reason: 'instruction', message: INSTRUCTION_ERROR }

  const masked = maskLiteralsAndComments(code)

  // 1. Prompt-injection / prose: no real code, reads like an instruction.
  if (!hasCodeSyntax(masked) && looksLikeInstruction(code)) {
    return { valid: false, reason: 'instruction', message: INSTRUCTION_ERROR }
  }

  // 2. Wrong language: explicit Python/JS syntax, or nothing Java-shaped at all.
  if (usesNonJavaSyntax(masked) || !looksLikeJava(masked)) {
    return { valid: false, reason: 'language', message: LANGUAGE_ERROR }
  }

  return { valid: true }
}

// Anything that reads as executable Python rather than prose. Mirrors the
// backend's PYTHON_CODE_SIGNAL in CodeSubmissionValidator.
const PYTHON_CODE_SIGNAL =
  /(^|\n)[ \t]*(def|class|import|from|for|while|if|return|print|with|try)\b|[=()[\]:]/

// Signals that clearly identify Java pasted while Python is selected.
const JAVA_IN_PYTHON_SIGNALS = [
  /\b(public|private|protected)\s+(static\s+)?[\w<>[\]]+\s+\w+\s*\(/,
  /\bSystem\s*\.\s*out\s*\./,
  /\b(public|private)\s+(class|interface|enum)\s+\w+/,
  /\bnew\s+\w+\s*(<[^>]*>)?\s*\(/,
]

export function validatePythonSubmission(rawCode) {
  const code = (rawCode ?? '').trim()
  if (!code) return { valid: false, reason: 'instruction', message: PYTHON_INSTRUCTION_ERROR }

  // Prose guard: "create a merge sort" has no code signal at all.
  if (!PYTHON_CODE_SIGNAL.test(code) && looksLikeInstruction(code)) {
    return { valid: false, reason: 'instruction', message: PYTHON_INSTRUCTION_ERROR }
  }

  // Java pasted while Python is selected would only produce a confusing
  // SyntaxError from the interpreter - name the actual problem instead.
  if (
    JAVA_IN_PYTHON_SIGNALS.some((re) => re.test(code)) &&
    code.includes(';') &&
    code.includes('{')
  ) {
    return { valid: false, reason: 'language', message: JAVA_IN_PYTHON_ERROR }
  }

  return { valid: true }
}

// Language-dispatching entry point used by the workspace.
export function validateSubmission(rawCode, language) {
  return language === 'python' ? validatePythonSubmission(rawCode) : validateJavaSubmission(rawCode)
}
