import { useState } from 'react'
import { isJavaCollection, isLinkedListNode, isTreeNode } from './nodeShape'
import LinkedListView from './LinkedListView'
import TreeView from './TreeView'
import MapView from './MapView'
import SetView from './SetView'
import ListView from './ListView'
import CollectionFallbackCard from './CollectionFallbackCard'

function LeafBadge({ name, text, italic = false }) {
  return (
    <div className="flex items-center gap-2 rounded-md border border-line bg-paper-raised px-2 py-1 font-mono text-sm">
      <span className="text-ink-soft">{name}</span>
      <span className={italic ? 'text-ink-soft italic' : 'text-ink'}>{text}</span>
    </div>
  )
}

function CycleBadge({ name, type, identityHash }) {
  return (
    <div className="rounded-md border border-highlight-ink/30 bg-highlight-ink/10 px-2 py-1 font-mono text-xs text-highlight-ink">
      {name}: ↻ cyclic reference back to {type} (#{identityHash})
    </div>
  )
}

function ArrayBoxes({ name, value }) {
  return (
    <div className="space-y-1">
      <div className="font-mono text-xs text-ink-soft">
        {name}: {value.componentType}[{value.length}]
      </div>
      <div className="flex flex-wrap gap-1">
        {value.elements.map((el, index) => (
          <div key={index} className="flex flex-col items-center">
            <div className="rounded-md border border-line bg-paper-raised px-2 py-1.5 font-mono text-sm text-ink">
              <ValueRenderer name="" declaredType={value.componentType} value={el} depth={1} visited={new Set()} />
            </div>
            <div className="mt-0.5 font-mono text-[10px] text-ink-soft">{index}</div>
          </div>
        ))}
        {value.truncated && (
          <div className="self-center font-mono text-xs text-ink-soft italic">… truncated</div>
        )}
      </div>
    </div>
  )
}

function GenericObjectCard({ name, value, depth, visited }) {
  const [isExpanded, setIsExpanded] = useState(depth < 1)

  return (
    <div className="space-y-1 rounded-md border border-line bg-paper-raised p-2">
      <button
        type="button"
        onClick={() => setIsExpanded((prev) => !prev)}
        className="flex items-center gap-1 font-mono text-sm text-ink-soft hover:text-ink"
      >
        <span>{isExpanded ? '▾' : '▸'}</span>
        <span>{name}</span>
        <span className="text-line">: {value.type}</span>
      </button>
      {isExpanded && (
        <div className="space-y-1 pl-4">
          {value.fields.map((field) => (
            <ValueRenderer
              key={field.name}
              name={field.name}
              declaredType={field.declaredType}
              value={field.value}
              depth={depth + 1}
              visited={visited}
            />
          ))}
          {value.truncated && (
            <div className="font-mono text-xs text-ink-soft italic">… truncated (more fields)</div>
          )}
        </div>
      )}
    </div>
  )
}

function ValueRenderer({ name, declaredType, value, depth = 0, visited }) {
  const seen = visited ?? new Set()

  switch (value.valueKind) {
    case 'primitive':
      return <LeafBadge name={name} text={value.literal} />
    case 'string':
      return <LeafBadge name={name} text={`"${value.value}"${value.truncated ? '…' : ''}`} />
    case 'null':
      return <LeafBadge name={name} text="null" italic />
    case 'truncated':
      return <LeafBadge name={name} text={`<${value.reason}>`} italic />

    case 'array':
      return <ArrayBoxes name={name} value={value} declaredType={declaredType} />

    case 'map':
      return <MapView name={name} value={value} />
    case 'set':
      return <SetView name={name} value={value} />
    case 'list':
      return <ListView name={name} value={value} />

    case 'object': {
      if (isJavaCollection(value)) {
        return <CollectionFallbackCard name={name} value={value} />
      }
      if (seen.has(value.identityHash)) {
        return <CycleBadge name={name} type={value.type} identityHash={value.identityHash} />
      }
      const nextSeen = new Set(seen)
      nextSeen.add(value.identityHash)

      if (isLinkedListNode(value)) {
        return <LinkedListView rootName={name} rootValue={value} />
      }
      if (isTreeNode(value)) {
        return <TreeView rootName={name} rootValue={value} />
      }
      return <GenericObjectCard name={name} value={value} depth={depth} visited={nextSeen} />
    }
    default:
      return null
  }
}

export default ValueRenderer
