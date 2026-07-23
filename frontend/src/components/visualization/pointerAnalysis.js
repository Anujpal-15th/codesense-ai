// Cross-references a frame's plain int/long locals against its array/list
// locals: for each array or list, which sibling variables currently hold a
// value that's a valid index into it right now. This is what lets the
// visualizer mark where `left`/`right`/`i` currently sit on the array for
// two-pointer and sliding-window code, instead of leaving them as
// disconnected numbers in the Variables panel.
//
// Deliberately makes no claim about WHICH variables are "really" pointers -
// that would mean guessing intent from a name or from parsing the source,
// and getting it wrong would be worse than not showing anything. Instead it
// states a plain fact the trace already proves: this variable's value
// equals this index, right now. Scoped to the current frame's own top-level
// locals only - not fields, not array elements, not variables in other
// frames - since that's the shape every two-pointer/sliding-window solution
// actually has (the pointers and the array they walk are always siblings in
// the same frame).

const INTEGRAL_TYPES = new Set(['int', 'long', 'short', 'byte'])

function arrayLength(value) {
  if (value.valueKind === 'array') return value.length
  if (value.valueKind === 'list') return value.size
  return null
}

/**
 * @param frame a StackFrameSnapshot (thisObject + localVariables)
 * @returns { [arrayVarName]: { [index]: string[] } } - only entries with at
 *          least one pointer are included.
 */
export function buildPointerMap(frame) {
  if (!frame) return {}
  const vars = [...(frame.thisObject ? [frame.thisObject] : []), ...frame.localVariables]

  const arrays = vars.filter((v) => v.value?.valueKind === 'array' || v.value?.valueKind === 'list')
  if (arrays.length === 0) return {}

  const pointerCandidates = vars.filter(
    (v) => v.value?.valueKind === 'primitive' && INTEGRAL_TYPES.has(v.value.primitiveType),
  )
  if (pointerCandidates.length === 0) return {}

  const map = {}
  for (const arr of arrays) {
    const length = arrayLength(arr.value)
    const byIndex = {}
    for (const p of pointerCandidates) {
      if (p.name === arr.name) continue // never mark an array against itself
      const idx = Number(p.value.literal)
      if (Number.isInteger(idx) && idx >= 0 && idx < length) {
        ;(byIndex[idx] ??= []).push(p.name)
      }
    }
    if (Object.keys(byIndex).length > 0) {
      map[arr.name] = byIndex
    }
  }
  return map
}
