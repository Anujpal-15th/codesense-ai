// Shared between HistoryCard (analyses) and ExecutionHistoryCard (runs) - was
// duplicated (formatDate byte-for-byte, previewLines differing only in which
// field it reads) across both card components before this extraction.

/** First 3 non-blank lines of a code preview, for an at-a-glance card excerpt.
 * Normalizes CRLF first - Monaco (and this DB's stored snippets) use \r\n,
 * which would otherwise leave a trailing \r on the last visible line. */
export function previewLines(source) {
  if (!source) return []
  return source
    .replace(/\r\n/g, '\n')
    .split('\n')
    .filter((line) => line.trim().length > 0)
    .slice(0, 3)
}

export function formatDate(createdAt) {
  if (!createdAt) return ''
  const d = new Date(createdAt)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}
