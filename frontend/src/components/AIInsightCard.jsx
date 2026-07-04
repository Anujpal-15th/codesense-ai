function AIInsightCard({ icon, title, chart, children }) {
  return (
    <div className="relative space-y-3 overflow-hidden rounded-lg bg-ink p-5 text-paper-raised">
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-center gap-2">
          <span className="text-paper-raised/80">{icon}</span>
          <h3 className="font-mono text-sm font-semibold tracking-wide">{title}</h3>
        </div>
        {chart}
      </div>
      <div className="space-y-2 font-sans text-sm">{children}</div>
    </div>
  )
}

export default AIInsightCard
