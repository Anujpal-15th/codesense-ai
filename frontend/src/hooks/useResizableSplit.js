import { useEffect, useRef, useState } from 'react'

const MIN_PCT = 25
const MAX_PCT = 75
const KEYBOARD_STEP_PCT = 2

/**
 * Drag-to-resize state for WorkspacePage's left/right split, plus a keyboard
 * equivalent for the role="separator" divider (arrow keys nudge, Home/End
 * jump to the extremes) - a mouse-only divider is worse than no ARIA role at
 * all, since a screen reader announces an interactive separator with no way
 * to operate it. `isDesktop` gates whether the % width even applies (below
 * lg, panels stack vertically and a % width would be wrong).
 */
export function useResizableSplit(initialPct = 40) {
  const [leftPct, setLeftPct] = useState(initialPct)
  const [dragging, setDragging] = useState(false)
  const [isDesktop, setIsDesktop] = useState(true)

  const splitRef = useRef(null)
  const draggingRef = useRef(false)

  useEffect(() => {
    const mq = window.matchMedia('(min-width: 1024px)')
    const update = () => setIsDesktop(mq.matches)
    update()
    mq.addEventListener('change', update)
    return () => mq.removeEventListener('change', update)
  }, [])

  useEffect(() => {
    const onMove = (e) => {
      if (!draggingRef.current || !splitRef.current) return
      const rect = splitRef.current.getBoundingClientRect()
      const pct = ((e.clientX - rect.left) / rect.width) * 100
      setLeftPct(Math.min(MAX_PCT, Math.max(MIN_PCT, pct)))
    }
    const onUp = () => {
      if (draggingRef.current) {
        draggingRef.current = false
        setDragging(false)
      }
    }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
    return () => {
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
    }
  }, [])

  const handleDragStart = () => {
    draggingRef.current = true
    setDragging(true)
  }

  const handleDividerKeyDown = (e) => {
    if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
      e.preventDefault()
      setLeftPct((pct) => Math.max(MIN_PCT, pct - KEYBOARD_STEP_PCT))
    } else if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
      e.preventDefault()
      setLeftPct((pct) => Math.min(MAX_PCT, pct + KEYBOARD_STEP_PCT))
    } else if (e.key === 'Home') {
      e.preventDefault()
      setLeftPct(MIN_PCT)
    } else if (e.key === 'End') {
      e.preventDefault()
      setLeftPct(MAX_PCT)
    }
  }

  return {
    splitRef,
    dragging,
    leftPct,
    leftStyle: isDesktop ? { width: `${leftPct}%` } : undefined,
    handleDragStart,
    handleDividerKeyDown,
  }
}
