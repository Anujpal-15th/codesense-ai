const THEMES = [
  { value: 'vs', label: 'Light' },
  { value: 'vs-dark', label: 'Dark' },
  { value: 'hc-black', label: 'High Contrast' },
]

function ToolbarButton({ onClick, disabled, label }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="rounded-md border border-line px-3 py-1.5 font-mono text-xs font-semibold text-ink-soft hover:text-ink disabled:opacity-40"
    >
      {label}
    </button>
  )
}

function EditorToolbar({ theme, onThemeChange, onFormat, onClear, onExample, onCopy, disabled }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-t-lg border border-b-0 border-line bg-paper-raised px-4 py-2">
      <div className="flex items-center gap-3">
        <select
          value="java"
          disabled
          title="More languages coming soon — execution engine is currently Java-only"
          className="rounded-md border border-line bg-paper px-3 py-1.5 font-mono text-xs font-semibold text-ink-soft"
        >
          <option value="java">Java</option>
        </select>
        <select
          value={theme}
          onChange={(e) => onThemeChange(e.target.value)}
          className="rounded-md border border-line bg-paper px-3 py-1.5 font-mono text-xs font-semibold text-ink"
        >
          {THEMES.map((t) => (
            <option key={t.value} value={t.value}>
              {t.label}
            </option>
          ))}
        </select>
      </div>

      <div className="flex items-center gap-2">
        <ToolbarButton onClick={onFormat} disabled={disabled} label="Format" />
        <ToolbarButton onClick={onExample} disabled={disabled} label="Example" />
        <ToolbarButton onClick={onClear} disabled={disabled} label="Clear" />
        <ToolbarButton onClick={onCopy} label="Copy" />
      </div>
    </div>
  )
}

export default EditorToolbar
