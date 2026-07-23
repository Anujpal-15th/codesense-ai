import { useState } from 'react'
import { boxedInnerValue, isJavaCollection, isLinkedListNode, isTreeNode } from './nodeShape'
import LinkedListView from './LinkedListView'
import TreeView from './TreeView'
import MapView from './MapView'
import SetView from './SetView'
import ListView from './ListView'
import CollectionFallbackCard from './CollectionFallbackCard'
import { Flashable, PopIn } from './StepChanges'
import { fieldPath, indexPath } from './stepDiff'
import { shortType } from './traceValue'

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

// `pointers` (optional): { [index]: string[] } - names of sibling int/long
// variables in this same frame whose value currently equals that index. See
// pointerAnalysis.js - a factual "these variables equal this index right
// now" marker, not a guess about which variables are "really" pointers.
// Exported so ListView (a separate box-per-index layout) can reuse it.
export function PointerBadges({ names }) {
  if (!names || names.length === 0) return null
  return (
    <div className="mt-0.5 flex flex-wrap justify-center gap-0.5">
      {names.map((n) => (
        <span
          key={n}
          className="rounded bg-primary/15 px-1 font-mono text-[9px] leading-tight font-semibold text-primary"
        >
          {n}
        </span>
      ))}
    </div>
  )
}

function ArrayBoxes({ name, value, path, pointers }) {
  return (
    <div className="space-y-1">
      <div className="font-mono text-xs text-ink-soft">
        {name}: {value.componentType}[{value.length}]
      </div>
      <div className="flex flex-wrap gap-1">
        {value.elements.map((el, index) => {
          const cellPath = path != null ? indexPath(path, index) : null
          return (
            <PopIn key={index} path={cellPath} className="flex flex-col items-center">
              <Flashable path={cellPath}>
                <div className="rounded-md border border-line bg-paper-raised px-2 py-1.5 font-mono text-sm text-ink">
                  <ValueRenderer name="" declaredType={value.componentType} value={el} depth={1} visited={new Set()} path={cellPath} />
                </div>
              </Flashable>
              <div className="mt-0.5 font-mono text-[10px] text-ink-soft">{index}</div>
              <PointerBadges names={pointers?.[index]} />
            </PopIn>
          )
        })}
        {value.truncated && (
          <div className="self-center font-mono text-xs text-ink-soft italic">… truncated</div>
        )}
      </div>
    </div>
  )
}

// A 2D array/list (int[][], List<List<Integer>>, Python's list-of-lists -
// which is 'list' at both levels, unlike Java) - true when every element is
// itself an array/list, not just "has at least one nested collection".
function isMatrix(value) {
  if ((value.valueKind !== 'array' && value.valueKind !== 'list') || value.elements.length === 0) return false
  return value.elements.every((el) => el.valueKind === 'array' || el.valueKind === 'list')
}

function rowElements(row) {
  return row.valueKind === 'array' || row.valueKind === 'list' ? row.elements : null
}

// Renders a genuine row/column grid instead of ArrayBoxes/ListView's default
// "nested boxes inside boxes" for an array-of-arrays or list-of-lists - the
// shape DP tables (Edit Distance, Unique Paths, Knapsack, LCS...) actually
// have. A row that isn't itself a collection (a ragged/malformed row) still
// renders honestly via the normal ValueRenderer path rather than being
// forced into a grid cell it doesn't fit.
function Matrix({ name, value, path }) {
  const maxCols = value.elements.reduce((max, row) => Math.max(max, rowElements(row)?.length ?? 0), 0)
  // JDI reports a 2D array's componentType as itself an array type ("int[]"),
  // so building "int[]" + "[3]" naively doubles up the brackets - strip the
  // trailing "[]" first so this reads as "int[3][3]", not "int[][3][]".
  const label =
    value.valueKind === 'array'
      ? `${value.componentType.replace(/\[\]$/, '')}[${value.length}][${maxCols}]`
      : `${shortType(value.type)} of lists`

  return (
    <div className="space-y-1">
      <div className="font-mono text-xs text-ink-soft">
        {name}: {label}
      </div>
      <div className="inline-block overflow-x-auto rounded-md border border-line bg-paper-raised p-2">
        <div className="flex">
          <span className="w-6 shrink-0" />
          {Array.from({ length: maxCols }, (_, c) => (
            <span key={c} className="w-8 shrink-0 text-center font-mono text-[10px] text-ink-soft">
              {c}
            </span>
          ))}
        </div>
        {value.elements.map((row, r) => {
          const rowPath = path != null ? indexPath(path, r) : null
          const els = rowElements(row)
          return (
            <div key={r} className="flex items-center">
              <span className="w-6 shrink-0 pr-1 text-right font-mono text-[10px] text-ink-soft">{r}</span>
              {els ? (
                els.map((cell, c) => {
                  const cellPath = rowPath != null ? indexPath(rowPath, c) : null
                  return (
                    <Flashable key={c} path={cellPath}>
                      <div className="flex h-8 w-8 shrink-0 items-center justify-center border border-line font-mono text-xs text-ink">
                        <ValueRenderer name="" declaredType="" value={cell} depth={1} visited={new Set()} path={cellPath} />
                      </div>
                    </Flashable>
                  )
                })
              ) : (
                <ValueRenderer name="" declaredType="" value={row} depth={1} visited={new Set()} path={rowPath} />
              )}
            </div>
          )
        })}
      </div>
      {value.truncated && <div className="font-mono text-xs text-ink-soft italic">… truncated</div>}
    </div>
  )
}

function GenericObjectCard({ name, value, depth, visited, path }) {
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
              path={path != null ? fieldPath(path, field.name) : null}
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

function ValueRenderer({ name, declaredType, value, depth = 0, visited, path = null, pointers }) {
  const seen = visited ?? new Set()

  switch (value.valueKind) {
    case 'primitive':
      return <Flashable path={path}><LeafBadge name={name} text={value.literal} /></Flashable>
    case 'string':
      return <Flashable path={path}><LeafBadge name={name} text={`"${value.value}"${value.truncated ? '…' : ''}`} /></Flashable>
    case 'null':
      return <Flashable path={path}><LeafBadge name={name} text="null" italic /></Flashable>
    case 'truncated':
      return <LeafBadge name={name} text={`<${value.reason}>`} italic />

    case 'array':
      return isMatrix(value) ? (
        <Matrix name={name} value={value} path={path} />
      ) : (
        <ArrayBoxes name={name} value={value} declaredType={declaredType} path={path} pointers={pointers} />
      )

    case 'map':
      return <MapView name={name} value={value} path={path} />
    case 'set':
      return <SetView name={name} value={value} path={path} />
    case 'list':
      return isMatrix(value) ? (
        <Matrix name={name} value={value} path={path} />
      ) : (
        <ListView name={name} value={value} path={path} pointers={pointers} />
      )

    case 'object': {
      // Boxed primitive wrappers (Integer, Long, ...) render as their inner
      // value leaf - cleaner than a collapsed object, and flashable.
      const boxed = boxedInnerValue(value)
      if (boxed) {
        const text = boxed.valueKind === 'string' ? `"${boxed.value}"` : boxed.literal ?? String(boxed.value ?? '')
        return <Flashable path={path}><LeafBadge name={name} text={text} /></Flashable>
      }
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
      return <GenericObjectCard name={name} value={value} depth={depth} visited={nextSeen} path={path} />
    }
    default:
      return null
  }
}

export default ValueRenderer
