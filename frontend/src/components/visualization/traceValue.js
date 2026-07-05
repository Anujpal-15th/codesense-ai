// Shared helpers for the TraceValue discriminated union (valueKind field).
// Used by the semantic collection views (Map/Set/List) and — in the animation
// layer — as stable identity/equality keys when diffing steps.

/**
 * A short, stable string for a value — used both as a React key and as the
 * identity for diffing (e.g. matching map entries by key across steps).
 * Primitives/strings use their literal; objects use identityHash; collections
 * use type+size. Never throws on a missing field.
 */
export function valueKey(value) {
  if (!value) return '∅'
  switch (value.valueKind) {
    case 'primitive':
      return `p:${value.literal}`
    case 'string':
      return `s:${value.value}`
    case 'null':
      return 'null'
    case 'object':
      return `o:${value.identityHash}`
    case 'array':
      return `a:${value.componentType}[${value.length}]`
    case 'map':
      return `m:${value.identityHash}`
    case 'set':
      return `st:${value.identityHash}`
    case 'list':
      return `l:${value.identityHash}`
    case 'truncated':
      return `t:${value.reason}`
    default:
      return '?'
  }
}

/**
 * A compact human-facing rendering of a scalar-ish value (used for map keys and
 * set/collection element chips). Objects/collections fall back to a type label.
 */
export function valueLabel(value) {
  if (!value) return '?'
  switch (value.valueKind) {
    case 'primitive':
      return value.literal
    case 'string':
      return `"${value.value}"${value.truncated ? '…' : ''}`
    case 'null':
      return 'null'
    case 'array':
      return `${value.componentType}[${value.length}]`
    case 'object':
      return shortType(value.type)
    case 'map':
    case 'set':
    case 'list':
      return `${shortType(value.type)}(${value.size})`
    case 'truncated':
      return `<${value.reason}>`
    default:
      return '?'
  }
}

/** Drops the package prefix from a fully-qualified type name for display. */
export function shortType(type) {
  if (!type) return '?'
  const lastDot = type.lastIndexOf('.')
  return lastDot >= 0 ? type.slice(lastDot + 1) : type
}
