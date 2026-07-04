import { motion } from 'framer-motion'
import { findField } from './nodeShape'

function collectChain(rootValue) {
  const nodes = []
  const seenHashes = new Set()
  let current = rootValue

  while (current && current.valueKind === 'object') {
    if (seenHashes.has(current.identityHash)) {
      return { nodes, cyclic: true }
    }
    seenHashes.add(current.identityHash)
    nodes.push(current)

    const nextField = findField(current, ['next'])
    if (!nextField || nextField.value.valueKind !== 'object') break
    current = nextField.value
  }

  return { nodes, cyclic: false }
}

function renderNodeValue(value) {
  if (!value) return '?'
  switch (value.valueKind) {
    case 'primitive':
      return value.literal
    case 'string':
      return `"${value.value}"`
    case 'null':
      return 'null'
    default:
      return value.type ?? '?'
  }
}

function LinkedListView({ rootName, rootValue }) {
  const { nodes, cyclic } = collectChain(rootValue)

  return (
    <div className="space-y-1">
      <div className="font-mono text-xs text-ink-soft">{rootName}: linked list</div>
      <div className="flex items-center gap-1 overflow-x-auto py-2">
        {nodes.map((node, i) => {
          const valField = findField(node, ['val', 'value', 'data'])
          return (
            <motion.div
              key={node.identityHash}
              layout
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.2 }}
              className="flex shrink-0 items-center gap-1"
            >
              <div className="rounded-lg border border-line bg-paper-raised px-3 py-2 font-mono text-sm">
                <div className="text-xs text-ink-soft">node {i}</div>
                <div className="text-ink">{renderNodeValue(valField?.value)}</div>
              </div>
              {i < nodes.length - 1 && <span className="text-ink-soft">→</span>}
            </motion.div>
          )
        })}
        {cyclic && (
          <span className="ml-1 shrink-0 rounded-md border border-highlight-ink/30 bg-highlight-ink/10 px-2 py-1 font-mono text-xs text-highlight-ink">
            ↻ cycle back to node 0
          </span>
        )}
      </div>
    </div>
  )
}

export default LinkedListView
