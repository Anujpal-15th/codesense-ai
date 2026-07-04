import { motion } from 'framer-motion'
import { findField } from './nodeShape'

const SVG_WIDTH = 700
const SVG_HEIGHT = 320
const ROW_HEIGHT = 70

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

function renderNode(node, x, y, spread, seen, depth, elements, occurrenceCounts) {
  if (!node || node.valueKind !== 'object') return

  const alreadyDrawn = seen.has(node.identityHash) && depth > 0
  const nextSeen = new Set(seen)
  nextSeen.add(node.identityHash)

  const left = findField(node, ['left'])?.value
  const right = findField(node, ['right'])?.value
  const valField = findField(node, ['val', 'value'])

  if (left?.valueKind === 'object') {
    const childX = x - spread
    const childY = y + ROW_HEIGHT
    elements.push(
      <line
        key={`edge-l-${node.identityHash}-${depth}`}
        x1={x}
        y1={y}
        x2={childX}
        y2={childY}
        stroke="var(--color-line)"
        strokeWidth="2"
      />,
    )
    renderNode(left, childX, childY, spread / 2, nextSeen, depth + 1, elements, occurrenceCounts)
  }
  if (right?.valueKind === 'object') {
    const childX = x + spread
    const childY = y + ROW_HEIGHT
    elements.push(
      <line
        key={`edge-r-${node.identityHash}-${depth}`}
        x1={x}
        y1={y}
        x2={childX}
        y2={childY}
        stroke="var(--color-line)"
        strokeWidth="2"
      />,
    )
    renderNode(right, childX, childY, spread / 2, nextSeen, depth + 1, elements, occurrenceCounts)
  }

  // Keyed by identityHash alone for a node's primary occurrence so framer-motion's
  // `layout` animation can track the same element across steps as its x/y shifts
  // (e.g. after a rotation) instead of remounting it. Only a shared/cyclic re-visit
  // of the same object gets a suffixed key, since that's a genuinely distinct draw.
  const occurrence = occurrenceCounts.get(node.identityHash) ?? 0
  occurrenceCounts.set(node.identityHash, occurrence + 1)
  const key = occurrence === 0 ? `${node.identityHash}` : `${node.identityHash}::${occurrence}`

  elements.push(
    <motion.g key={key} layout transition={{ duration: 0.3 }}>
      <circle cx={x} cy={y} r="20" fill={alreadyDrawn ? 'var(--color-highlight-ink)' : 'var(--color-ink)'} />
      <text x={x} y={y + 4} textAnchor="middle" fill="var(--color-paper-raised)" fontSize="12" fontFamily="monospace">
        {renderLeafText(valField?.value)}
      </text>
    </motion.g>,
  )
}

function TreeView({ rootName, rootValue }) {
  const elements = []
  renderNode(rootValue, SVG_WIDTH / 2, 30, SVG_WIDTH / 4, new Set(), 0, elements, new Map())

  return (
    <div className="space-y-1">
      <div className="font-mono text-xs text-ink-soft">{rootName}: tree</div>
      <div className="overflow-x-auto rounded-lg border border-line bg-paper-raised">
        <svg viewBox={`0 0 ${SVG_WIDTH} ${SVG_HEIGHT}`} className="h-auto w-full min-w-[500px]">
          {elements}
        </svg>
      </div>
    </div>
  )
}

export default TreeView
