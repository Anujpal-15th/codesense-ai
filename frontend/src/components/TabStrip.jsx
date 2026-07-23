// The "underline tab" pattern was hand-rolled separately in WorkspacePage and
// HistoryPage with slightly different padding (py-3 vs py-2.5, kept here via
// tabPadding so neither page's look changes) and outer wrapper styling (left
// entirely to the caller via className, since the two pages' surrounding
// chrome differs).
function TabStrip({ tabs, activeId, onChange, className = '', tabPadding = 'py-2.5' }) {
  return (
    <div className={`flex gap-1 border-b border-line ${className}`}>
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          onClick={() => onChange(tab.id)}
          className={`-mb-px border-b-2 px-4 ${tabPadding} font-mono text-sm font-semibold ${
            activeId === tab.id ? 'border-primary text-ink' : 'border-transparent text-ink-soft hover:text-ink'
          }`}
        >
          {tab.label}
        </button>
      ))}
    </div>
  )
}

export default TabStrip
