function AIInsightCard({ icon, title, chart, children }) {
  return (
    <div className="relative space-y-3 overflow-hidden rounded-lg border border-line bg-paper-raised p-5 text-ink">
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-center gap-2">
          <span className="text-ink-soft">{icon}</span>
          <h3 className="font-mono text-sm font-semibold tracking-wide text-ink">{title}</h3>
        </div>
        {chart}
      </div>
      <div className="space-y-2 font-sans text-sm">{children}</div>
    </div>
  )
}

export default AIInsightCard
