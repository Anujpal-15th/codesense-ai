import { motion } from 'framer-motion'
import { findField } from './nodeShape'

const LEAF_SLOT = 40 // horizontal space per leaf - wide enough that two adjacent r=14 circles never touch
const ROW_HEIGHT = 50
const NODE_RADIUS = 14 // ~0.75cm diameter on screen
const TOP_MARGIN = 20
const SIDE_MARGIN = 20

function renderLeafText(value) {
  if (!value) return '?'
  switch (value.valueKind) {
    case 'primitive':
      return value.literal
    case 'string':
      return value.value
    default:
      return '?'
  }
}

function isObjectNode(value) {
  return value?.valueKind === 'object'
}

/**
 * Two-pass layout instead of the old single-pass "halve the horizontal
 * spread every level regardless of how many siblings actually need room
 * there" approach - that scheme only accounted for DEPTH, so a wide/bushy
 * tree (many branches, not necessarily very deep) got squeezed into
 * ever-narrower slices and its nodes started visually overlapping. This pass
 * assigns each node an x purely from tree shape: a leaf claims the next free
 * horizontal slot, an internal node centers over its own children (or takes
 * a single child's x if it only has one) - the standard "equal-width leaves"
 * tree-layout approach. A second pass (renderPositioned) just draws using
 * the already-computed positions.
 *
 * `seen` is the ancestor path from the root, not a shared/mutated set across
 * siblings - matches the previous implementation's cycle semantics exactly:
 * only a node linking back to one of its OWN ancestors counts as a cycle. A
 * node legitimately reachable from two unrelated branches is drawn twice,
 * which is correct (that's two real edges to inspect, not a bug).
 */
function assignPositions(node, depth, seen, ctx) {
  if (!isObjectNode(node) || seen.has(node.identityHash)) return null
  const nextSeen = new Set(seen)
  nextSeen.add(node.identityHash)

  const leftVal = findField(node, ['left'])?.value
  const rightVal = findField(node, ['right'])?.value
  const leftPos = isObjectNode(leftVal) ? assignPositions(leftVal, depth + 1, nextSeen, ctx) : null
  const rightPos = isObjectNode(rightVal) ? assignPositions(rightVal, depth + 1, nextSeen, ctx) : null

  let x;
  if (leftPos && rightPos) x = (leftPos.x + rightPos.x) / 2
  else if (leftPos) x = leftPos.x
  else if (rightPos) x = rightPos.x
  else {
    x = ctx.nextX
    ctx.nextX += LEAF_SLOT
  }

  return { node, x, y: TOP_MARGIN + depth * ROW_HEIGHT, depth, leftPos, rightPos }
}

function renderPositioned(pos, seen, elements, occurrenceCounts) {
  if (!pos) return
  const { node, x, y, depth, leftPos, rightPos } = pos
  const alreadyDrawn = seen.has(node.identityHash) && depth > 0
  const nextSeen = new Set(seen)
  nextSeen.add(node.identityHash)

  if (leftPos) {
    elements.push(
      <line
        key={`edge-l-${node.identityHash}-${depth}`}
        x1={x}
        y1={y}
        x2={leftPos.x}
        y2={leftPos.y}
        stroke="var(--color-line)"
        strokeWidth="2"
      />,
    )
    renderPositioned(leftPos, nextSeen, elements, occurrenceCounts)
  }
  if (rightPos) {
    elements.push(
      <line
        key={`edge-r-${node.identityHash}-${depth}`}
        x1={x}
        y1={y}
        x2={rightPos.x}
        y2={rightPos.y}
        stroke="var(--color-line)"
        strokeWidth="2"
      />,
    )
    renderPositioned(rightPos, nextSeen, elements, occurrenceCounts)
  }

  const valField = findField(node, ['val', 'value'])

  // Keyed by identityHash alone for a node's primary occurrence so framer-motion's
  // `layout` animation can track the same element across steps as its x/y shifts
  // (e.g. after a rotation) instead of remounting it. Only a shared/cyclic re-visit
  // of the same object gets a suffixed key, since that's a genuinely distinct draw.
  const occurrence = occurrenceCounts.get(node.identityHash) ?? 0
  occurrenceCounts.set(node.identityHash, occurrence + 1)
  const key = occurrence === 0 ? `${node.identityHash}` : `${node.identityHash}::${occurrence}`

  elements.push(
    <motion.g key={key} layout transition={{ duration: 0.3 }}>
      <circle cx={x} cy={y} r={NODE_RADIUS} fill={alreadyDrawn ? 'var(--color-highlight-ink)' : 'var(--color-ink)'} />
      <text x={x} y={y + 3} textAnchor="middle" fill="var(--color-paper-raised)" fontSize="9" fontFamily="monospace">
        {renderLeafText(valField?.value)}
      </text>
    </motion.g>,
  )
}

/** Number of leaf slots the tree needs - i.e. how many nodes have neither a
 * left nor a right object child. Determines the SVG's total width. */
function countLeafSlots(node, seen) {
  if (!isObjectNode(node) || seen.has(node.identityHash)) return 0
  const nextSeen = new Set(seen)
  nextSeen.add(node.identityHash)
  const leftVal = findField(node, ['left'])?.value
  const rightVal = findField(node, ['right'])?.value
  const leftHas = isObjectNode(leftVal)
  const rightHas = isObjectNode(rightVal)
  if (!leftHas && !rightHas) return 1
  return (leftHas ? countLeafSlots(leftVal, nextSeen) : 0) + (rightHas ? countLeafSlots(rightVal, nextSeen) : 0)
}

/** Determines the SVG's total height - was previously a fixed constant that
 * clipped anything past ~4 levels; now grows with the tree's real depth. */
function maxDepth(node, seen) {
  if (!isObjectNode(node) || seen.has(node.identityHash)) return 0
  const nextSeen = new Set(seen)
  nextSeen.add(node.identityHash)
  const leftVal = findField(node, ['left'])?.value
  const rightVal = findField(node, ['right'])?.value
  const l = isObjectNode(leftVal) ? maxDepth(leftVal, nextSeen) : 0
  const r = isObjectNode(rightVal) ? maxDepth(rightVal, nextSeen) : 0
  return 1 + Math.max(l, r)
}

function TreeView({ rootName, rootValue }) {
  const leafSlots = Math.max(1, countLeafSlots(rootValue, new Set()))
  const depth = Math.max(1, maxDepth(rootValue, new Set()))
  const width = leafSlots * LEAF_SLOT + SIDE_MARGIN * 2
  const height = TOP_MARGIN + depth * ROW_HEIGHT + NODE_RADIUS + 10

  const ctx = { nextX: SIDE_MARGIN + LEAF_SLOT / 2 }
  const rootPos = assignPositions(rootValue, 0, new Set(), ctx)

  const elements = []
  renderPositioned(rootPos, new Set(), elements, new Map())

  return (
    <div className="space-y-1">
      <div className="font-mono text-xs text-ink-soft">{rootName}: tree</div>
      <div className="overflow-x-auto rounded-lg border border-line bg-paper-raised">
        {/* Fixed pixel width/height (not w-full) - w-full would stretch the
            viewBox to fill the container, magnifying the small nodes right
            back up regardless of NODE_RADIUS. maxWidth still lets it shrink
            on narrow viewports without ever growing past its natural size. */}
        <svg viewBox={`0 0 ${width} ${height}`} width={width} height={height} style={{ maxWidth: '100%' }}>
          {elements}
        </svg>
      </div>
    </div>
  )
}

export default TreeView
