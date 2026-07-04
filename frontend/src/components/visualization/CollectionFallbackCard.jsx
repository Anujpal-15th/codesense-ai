import ValueRenderer from './ValueRenderer'

function CollectionFallbackCard({ name, value }) {
  return (
    <div className="rounded-lg border border-highlight-ink/30 bg-highlight-ink/10 p-3">
      <div className="font-mono text-xs text-highlight-ink">
        {name}: {value.type} — raw internal representation, semantic view not yet supported
      </div>
      <div className="mt-2 space-y-1">
        {value.fields.map((f) => (
          <ValueRenderer key={f.name} name={f.name} declaredType={f.declaredType} value={f.value} depth={1} />
        ))}
      </div>
    </div>
  )
}

export default CollectionFallbackCard
