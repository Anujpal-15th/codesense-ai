// Shared between App.jsx's AppShell nav and WorkspacePage's own top bar - was
// an identical function defined twice before this extraction.
export function navLinkClass({ isActive }) {
  return `font-mono text-sm font-medium px-3 py-2 ${isActive ? 'text-ink' : 'text-ink-soft hover:text-ink'}`
}
