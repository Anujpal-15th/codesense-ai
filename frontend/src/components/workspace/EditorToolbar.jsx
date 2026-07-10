function ToolbarButton({ onClick, disabled, label }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="rounded-md px-2.5 py-1.5 font-mono text-xs font-semibold text-ink-soft hover:bg-paper hover:text-ink disabled:opacity-40"
    >
      {label}
    </button>
  )
}

function EditorToolbar({ onFormat, onClear, onExample, onCopy, copied, disabled }) {
  return (
    <div className="flex shrink-0 items-center justify-between gap-3 border-b border-line bg-paper-raised px-4 py-2">
      {/* Non-interactive language badge — Java only, no dropdown. */}
      <span className="rounded-md border border-line bg-paper px-2.5 py-1 font-mono text-xs font-semibold text-ink-soft">
        Java
      </span>

      <div className="flex items-center gap-1">
        <ToolbarButton onClick={onFormat} disabled={disabled} label="Format" />
        <ToolbarButton onClick={onExample} disabled={disabled} label="Example" />
        <ToolbarButton onClick={onClear} disabled={disabled} label="Clear" />
        <button
          type="button"
          onClick={onCopy}
          className={`rounded-md px-2.5 py-1.5 font-mono text-xs font-semibold transition-colors ${
            copied ? 'text-approve' : 'text-ink-soft hover:bg-paper hover:text-ink'
          }`}
        >
          {copied ? 'Copied! ✓' : 'Copy'}
        </button>
      </div>
    </div>
  )
}

export default EditorToolbar
