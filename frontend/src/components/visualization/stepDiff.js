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

export const EMPTY_CHANGES = {
  changed: new Set(),
  added: new Set(),
  changedHashes: new Set(),
  newFrameDepths: new Set(),
  newObjectHashes: new Set(),
  removedObjects: new Map(),
  tick: 0,
}

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
 * changedHashes, newFrameDepths, newObjectHashes, tick }.
 *
 * At index 0 (the very first step), there is no previous step to diff against -
 * everything visible (the initial frame, any already-allocated objects) is
 * treated as newly-appeared, so the entrance stagger also plays on first mount
 * rather than only on later transitions.
 */
export function computeStepChanges(trace, index) {
  if (!trace) return { ...EMPTY_CHANGES, tick: index }
  const curr = trace.steps[index]
  if (!curr) return { ...EMPTY_CHANGES, tick: index }

  if (index === 0) {
    return {
      changed: new Set(),
      added: new Set(),
      changedHashes: new Set(),
      newFrameDepths: allFrameDepths(curr),
      newObjectHashes: new Set(objectInfoByHash(curr).keys()),
      removedObjects: new Map(),
      tick: index,
    }
  }

  const prev = trace.steps[index - 1]
  if (!prev) return { ...EMPTY_CHANGES, tick: index }

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
  const newFrameDepths = computeNewFrameDepths(prev, curr)
  const newObjectHashes = computeNewObjectHashes(prev, curr)
  const removedObjects = computeRemovedObjects(prev, curr)
  return { changed, added, changedHashes, newFrameDepths, newObjectHashes, removedObjects, tick: index }
}

function allFrameDepths(step) {
  const n = step.callStack.length
  return new Set(Array.from({ length: n }, (_, i) => n - 1 - i))
}

/**
 * Depths-from-the-bottom (main() = depth 0) of frames in `curr` that are
 * genuinely new this step (just pushed by a METHOD_ENTRY). Depth-from-bottom,
 * not raw array index, is what's stable across a push: pushing a frame on top
 * shifts every existing frame's array INDEX by +1, but its depth-from-bottom
 * (distance from main()) doesn't change - so comparing by depth correctly
 * flags only the newly-entered frame, including across a multi-step scrub
 * where more than one frame may have appeared since the last-rendered step.
 */
function computeNewFrameDepths(prev, curr) {
  const prevByDepth = new Map()
  const prevLen = prev.callStack.length
  prev.callStack.forEach((frame, i) => {
    prevByDepth.set(prevLen - 1 - i, `${frame.className}.${frame.methodName}`)
  })

  const newDepths = new Set()
  const currLen = curr.callStack.length
  curr.callStack.forEach((frame, i) => {
    const depth = currLen - 1 - i
    const key = `${frame.className}.${frame.methodName}`
    if (prevByDepth.get(depth) !== key) newDepths.add(depth)
  })
  return newDepths
}

/** identityHashes present in curr's heap walk but absent from prev's - i.e. objects allocated since the last-rendered step. */
function computeNewObjectHashes(prev, curr) {
  const prevHashes = new Set(objectInfoByHash(prev).keys())
  const currHashes = objectInfoByHash(curr)
  const fresh = new Set()
  for (const hash of currHashes.keys()) {
    if (!prevHashes.has(hash)) fresh.add(hash)
  }
  return fresh
}

/**
 * The mirror image of computeNewObjectHashes: objects reachable from prev's
 * call stack that are NOT reachable from curr's - most often because a
 * method that held the last reference to them just returned. Without this,
 * an object simply vanishes from the Memory panel with no explanation,
 * which reads as a bug rather than the normal "went out of scope" it
 * actually is. Returns identityHash -> type (from prev, since curr no
 * longer has it) so the panel can say WHAT disappeared, not just that
 * something did.
 */
function computeRemovedObjects(prev, curr) {
  const prevInfo = objectInfoByHash(prev)
  const currHashes = new Set(objectInfoByHash(curr).keys())
  const removed = new Map()
  for (const [hash, info] of prevInfo) {
    if (!currHashes.has(hash)) removed.set(hash, info.type)
  }
  return removed
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
  const prevInfo = objectInfoByHash(prev)
  const currInfo = objectInfoByHash(curr)
  const changed = new Set()
  for (const [hash, info] of currInfo) {
    if (prevInfo.has(hash) && prevInfo.get(hash).sig !== info.sig) changed.add(hash)
  }
  return changed
}

/** identityHash -> { sig, type } for every heap object/collection reachable
 * from a step's call stack. `sig` is a cheap shallow signature (direct
 * field/element leaf literals) used to detect mutation; `type` is carried
 * alongside so callers that need to describe an object (e.g. "a ListNode
 * just went out of scope") don't have to re-walk the tree a second time. */
function objectInfoByHash(step) {
  const info = new Map()
  const walk = (value) => {
    if (!value) return
    if (value.valueKind === 'object') {
      if (!info.has(value.identityHash)) {
        const sig = value.fields.map((f) => `${f.name}=${shallow(f.value)}`).join(',')
        info.set(value.identityHash, { sig, type: value.type })
        value.fields.forEach((f) => walk(f.value))
      }
    } else if (value.valueKind === 'array' || value.valueKind === 'list') {
      value.elements.forEach(walk)
    } else if (value.valueKind === 'map') {
      if (value.identityHash && !info.has(value.identityHash)) {
        const sig = value.entries.map((e) => `${shallow(e.key)}:${shallow(e.value)}`).join(',')
        info.set(value.identityHash, { sig, type: value.type })
      }
      value.entries.forEach((e) => {
        walk(e.key)
        walk(e.value)
      })
    } else if (value.valueKind === 'set') {
      if (value.identityHash && !info.has(value.identityHash)) {
        const sig = value.elements.map(shallow).join(',')
        info.set(value.identityHash, { sig, type: value.type })
      }
      value.elements.forEach(walk)
    }
  }
  step.callStack.forEach((frame) => {
    if (frame.thisObject) walk(frame.thisObject.value)
    frame.localVariables.forEach((v) => walk(v.value))
  })
  return info
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
