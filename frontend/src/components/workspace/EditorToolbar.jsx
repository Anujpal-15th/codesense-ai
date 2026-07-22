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

function RefreshIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="15"
      height="15"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M21 12a9 9 0 1 1-2.64-6.36" />
      <polyline points="21 3 21 9 15 9" />
    </svg>
  )
}

function EditorToolbar({
  onFormat,
  onRefresh,
  onExample,
  onCopy,
  copyStatus = 'idle',
  language = 'java',
  onLanguageChange,
}) {
  const copyLabel =
    copyStatus === 'copied' ? 'Copied! ✓' : copyStatus === 'failed' ? 'Copy failed — copy manually' : 'Copy'
  return (
    <div className="flex shrink-0 items-center justify-between gap-3 border-b border-line bg-paper-raised px-4 py-2">
      <select
        value={language}
        onChange={(e) => onLanguageChange?.(e.target.value)}
        aria-label="Language"
        className="cursor-pointer rounded-md border border-line bg-paper px-2.5 py-1 font-mono text-xs font-semibold text-ink-soft hover:text-ink"
      >
        <option value="java">Java</option>
        <option value="python">Python</option>
      </select>

      <div className="flex items-center gap-3">
        {/* Monaco only has a Java formatter registered (formatJava.js) - no
            Python formatting yet, so the button is Java-only. */}
        <ToolbarButton onClick={onFormat} disabled={language !== 'java'} label="Format" />
        <ToolbarButton onClick={onExample} label="Example" />
        <button
          type="button"
          onClick={onCopy}
          className={`rounded-md px-2.5 py-1.5 font-mono text-xs font-semibold transition-colors ${
            copyStatus === 'copied'
              ? 'text-approve'
              : copyStatus === 'failed'
                ? 'text-correct'
                : 'text-ink-soft hover:bg-paper hover:text-ink'
          }`}
        >
          {copyLabel}
        </button>
        {/* Refresh resets everything back to a blank workspace. */}
        <button
          type="button"
          onClick={onRefresh}
          title="Refresh — reset the editor and clear all results"
          aria-label="Refresh"
          className="flex items-center gap-1.5 rounded-md px-2.5 py-1.5 font-mono text-xs font-semibold text-ink-soft hover:bg-paper hover:text-ink"
        >
          <RefreshIcon />
          Refresh
        </button>
      </div>
    </div>
  )
}

export default EditorToolbar
