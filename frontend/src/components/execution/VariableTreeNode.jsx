import { useState } from 'react'

function renderLeafValue(value) {
  switch (value.valueKind) {
    case 'primitive':
      return <span className="text-ink-soft">{value.literal}</span>
    case 'string':
      return (
        <span className="text-ink">
          "{value.value}"{value.truncated ? '…' : ''}
        </span>
      )
    case 'null':
      return <span className="text-ink-soft italic">null</span>
    case 'truncated':
      return <span className="text-ink-soft italic">&lt;{value.reason}&gt;</span>
    default:
      return null
  }
}

function VariableTreeNode({ name, declaredType, value, depth }) {
  const isExpandable = value.valueKind === 'array' || value.valueKind === 'object'
  const [isExpanded, setIsExpanded] = useState(depth < 1)

  if (!isExpandable) {
    return (
      <div className="py-0.5 font-mono text-sm" style={{ paddingLeft: `${depth * 16 + 8}px` }}>
        <span className="text-ink-soft">{name}</span>
        <span className="text-line">: {declaredType} = </span>
        {renderLeafValue(value)}
      </div>
    )
  }

  const isArray = value.valueKind === 'array'
  const children = isArray ? value.elements : value.fields
  const headerLabel = isArray ? `${value.componentType}[${value.length}]` : value.type

  return (
    <div className="py-0.5" style={{ paddingLeft: `${depth * 16}px` }}>
      <button
        type="button"
        onClick={() => setIsExpanded((prev) => !prev)}
        className="flex items-center gap-1 font-mono text-sm text-ink-soft hover:text-ink"
      >
        <span className="text-ink-soft">{isExpanded ? '▾' : '▸'}</span>
        <span className="text-ink-soft">{name}</span>
        <span className="text-line">: {headerLabel}</span>
      </button>

      {isExpanded && (
        <div>
          {children.map((child, index) =>
            isArray ? (
              <VariableTreeNode
                key={index}
                name={`[${index}]`}
                declaredType={value.componentType}
                value={child}
                depth={depth + 1}
              />
            ) : (
              <VariableTreeNode
                key={child.name}
                name={child.name}
                declaredType={child.declaredType}
                value={child.value}
                depth={depth + 1}
              />
            ),
          )}
          {value.truncated && (
            <div
              className="py-0.5 text-sm text-ink-soft italic"
              style={{ paddingLeft: `${(depth + 1) * 16 + 8}px` }}
            >
              … truncated (more entries or a cyclic reference)
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default VariableTreeNode
