// Generic, algorithm-agnostic step diffing. Compares the previous trace step's
// captured values to the current step's and reports which leaf values CHANGED
// and which collection/array entries were ADDED — purely from trace data, so it
// works identically for a HashMap solution, a linked list, or a sort.
//
// Paths are stable strings identifying a spot in the current frame's value tree
// (e.g. `slopeMap#s:1/1` for a map entry, `arr[3]` for an array cell). The same
// path helpers are used by the diff and by the views, so a view can ask
// "did my entry at this path change / get added?" without re-deriving anything.

import { valueKey } from './traceValue'

export const EMPTY_CHANGES = { changed: new Set(), added: new Set(), changedHashes: new Set(), tick: 0 }

export function fieldPath(base, fieldName) {
  return `${base}.${fieldName}`
}
export function indexPath(base, i) {
  return `${base}[${i}]`
}
export function mapEntryPath(base, keyValue) {
  return `${base}#${valueKey(keyValue)}`
}
export function setElemPath(base, elemValue) {
  return `${base}∋${valueKey(elemValue)}`
}

/**
 * Computes changes for the transition INTO `trace.steps[index]` (i.e. index-1 ->
 * index). Pure function of (trace, index): navigation-direction independent, so
 * scrubbing shows the same result as stepping. Returns { changed, added,
 * changedHashes, tick }.
 */
export function computeStepChanges(trace, index) {
  if (!trace || index <= 0) return { ...EMPTY_CHANGES, tick: index }
  const prev = trace.steps[index - 1]
  const curr = trace.steps[index]
  if (!prev || !curr) return { ...EMPTY_CHANGES, tick: index }

  const changed = new Set()
  const added = new Set()

  // Variable-level diff is only meaningful when the top frame is the SAME
  // invocation as the previous step's top frame. Match on class+method+depth so
  // a recursive re-entry (same method, different depth) doesn't diff against a
  // different invocation and flash everything spuriously.
  const prevFrame = prev.callStack[0]
  const currFrame = curr.callStack[0]
  if (
    prevFrame &&
    currFrame &&
    prevFrame.className === currFrame.className &&
    prevFrame.methodName === currFrame.methodName &&
    prev.callStack.length === curr.callStack.length
  ) {
    diffFrameVars(prevFrame, currFrame, changed, added)
  }

  const changedHashes = computeChangedHashes(prev, curr)
  return { changed, added, changedHashes, tick: index }
}

function diffFrameVars(prevFrame, currFrame, changed, added) {
  const prevByName = new Map()
  if (prevFrame.thisObject) prevByName.set('this', prevFrame.thisObject.value)
  prevFrame.localVariables.forEach((v) => prevByName.set(v.name, v.value))

  const currVars = []
  if (currFrame.thisObject) currVars.push(['this', currFrame.thisObject.value])
  currFrame.localVariables.forEach((v) => currVars.push([v.name, v.value]))

  for (const [name, currVal] of currVars) {
    diffValue(prevByName.get(name), currVal, name, changed, added)
  }
}

function diffValue(prev, curr, path, changed, added) {
  if (!curr) return
  if (!prev) {
    // Newly in scope this step — treat as changed so it pulses once.
    changed.add(path)
    return
  }
  if (prev.valueKind !== curr.valueKind) {
    changed.add(path)
    return
  }
  switch (curr.valueKind) {
    case 'primitive':
      if (prev.literal !== curr.literal) changed.add(path)
      break
    case 'string':
      if (prev.value !== curr.value) changed.add(path)
      break
    case 'null':
      break
    case 'object': {
      if (prev.identityHash !== curr.identityHash) {
        // Reassigned to a different object — flash the whole variable.
        changed.add(path)
        break
      }
      const prevFields = new Map(prev.fields.map((f) => [f.name, f.value]))
      curr.fields.forEach((f) => diffValue(prevFields.get(f.name), f.value, fieldPath(path, f.name), changed, added))
      break
    }
    case 'array':
    case 'list': {
      const prevEls = prev.elements
      curr.elements.forEach((el, i) => {
        if (i >= prevEls.length) added.add(indexPath(path, i))
        else diffValue(prevEls[i], el, indexPath(path, i), changed, added)
      })
      break
    }
    case 'map': {
      const prevByKey = new Map(prev.entries.map((e) => [valueKey(e.key), e.value]))
      curr.entries.forEach((e) => {
        const ep = mapEntryPath(path, e.key)
        if (!prevByKey.has(valueKey(e.key))) added.add(ep)
        else diffValue(prevByKey.get(valueKey(e.key)), e.value, ep, changed, added)
      })
      break
    }
    case 'set': {
      const prevKeys = new Set(prev.elements.map(valueKey))
      curr.elements.forEach((el) => {
        if (!prevKeys.has(valueKey(el))) added.add(setElemPath(path, el))
      })
      break
    }
    default:
      break
  }
}

/**
 * Identity-hashes of heap objects/collections whose shallow content changed
 * between the two steps — drives the Memory panel's mutated-object flash.
 * Compared by a cheap shallow signature (direct field/element leaf literals).
 */
function computeChangedHashes(prev, curr) {
  const prevSig = signaturesByHash(prev)
  const currSig = signaturesByHash(curr)
  const changed = new Set()
  for (const [hash, sig] of currSig) {
    if (prevSig.has(hash) && prevSig.get(hash) !== sig) changed.add(hash)
  }
  return changed
}

function signaturesByHash(step) {
  const sigs = new Map()
  const walk = (value) => {
    if (!value) return
    if (value.valueKind === 'object') {
      if (!sigs.has(value.identityHash)) {
        sigs.set(value.identityHash, value.fields.map((f) => `${f.name}=${shallow(f.value)}`).join(','))
        value.fields.forEach((f) => walk(f.value))
      }
    } else if (value.valueKind === 'array' || value.valueKind === 'list') {
      value.elements.forEach(walk)
    } else if (value.valueKind === 'map') {
      if (value.identityHash && !sigs.has(value.identityHash)) {
        sigs.set(value.identityHash, value.entries.map((e) => `${shallow(e.key)}:${shallow(e.value)}`).join(','))
      }
      value.entries.forEach((e) => {
        walk(e.key)
        walk(e.value)
      })
    } else if (value.valueKind === 'set') {
      if (value.identityHash && !sigs.has(value.identityHash)) {
        sigs.set(value.identityHash, value.elements.map(shallow).join(','))
      }
      value.elements.forEach(walk)
    }
  }
  step.callStack.forEach((frame) => {
    if (frame.thisObject) walk(frame.thisObject.value)
    frame.localVariables.forEach((v) => walk(v.value))
  })
  return sigs
}

function shallow(value) {
  if (!value) return '∅'
  switch (value.valueKind) {
    case 'primitive':
      return value.literal
    case 'string':
      return value.value
    case 'null':
      return 'null'
    case 'object':
      return `#${value.identityHash}`
    case 'map':
    case 'set':
    case 'list':
      return `${value.valueKind}#${value.identityHash}`
    case 'array':
      return `[${value.length}]`
    default:
      return '?'
  }
}
