import { useEffect, useRef, useState } from 'react'

/**
 * Copies `text` (read fresh via `getText()` at call time, not captured once)
 * to the clipboard, with a synchronous execCommand fallback for contexts
 * where the async Clipboard API is blocked (insecure origin, permissions
 * policy, embedded frames) - that fallback only needs the click's user
 * activation, not clipboard-write permission. Returns 'idle' | 'copied' |
 * 'failed', auto-resetting to 'idle' after a short delay, for a button's
 * transient feedback state.
 */
export function useClipboardCopy(getText) {
  const [status, setStatus] = useState('idle')
  const timerRef = useRef(null)

  useEffect(() => () => clearTimeout(timerRef.current), [])

  const copy = () => {
    const text = getText()
    const flash = (s, ms) => {
      setStatus(s)
      clearTimeout(timerRef.current)
      timerRef.current = setTimeout(() => setStatus('idle'), ms)
    }

    const legacyCopy = () => {
      try {
        const ta = document.createElement('textarea')
        ta.value = text
        ta.style.position = 'fixed'
        ta.style.top = '-9999px'
        ta.setAttribute('readonly', '')
        document.body.appendChild(ta)
        ta.select()
        const ok = document.execCommand('copy')
        document.body.removeChild(ta)
        return ok
      } catch {
        return false
      }
    }

    if (navigator.clipboard?.writeText) {
      navigator.clipboard
        .writeText(text)
        .then(() => flash('copied', 1500))
        .catch(() => flash(legacyCopy() ? 'copied' : 'failed', 2500))
    } else {
      flash(legacyCopy() ? 'copied' : 'failed', 2500)
    }
  }

  return { status, copy }
}
