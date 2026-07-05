import { createContext, useContext } from 'react'
import { motion } from 'framer-motion'
import { EMPTY_CHANGES } from './stepDiff'

// Context carries the current step's change-set down to leaf renderers so they
// can flash/pop-in without every level threading the data through props. Only
// `path` (positional identity) is threaded; the change-set comes from here.
// The change-set is computed by useStepChanges (separate file) and passed to
// StepChangesProvider by the panels.
const StepChangesContext = createContext(EMPTY_CHANGES)

export function StepChangesProvider({ value, children }) {
  return <StepChangesContext.Provider value={value}>{children}</StepChangesContext.Provider>
}

/**
 * Flashes its children once when the value at `path` changed on the transition
 * into the current step. Keyed on the step tick so the CSS animation replays
 * each time. When not changed (or no path), renders children untouched — zero
 * cost on the common case.
 */
export function Flashable({ path, children }) {
  const changes = useContext(StepChangesContext)
  const active = path != null && changes.changed.has(path)
  if (!active) return children
  return (
    <span key={changes.tick} className="value-flash inline-block rounded-md">
      {children}
    </span>
  )
}

/**
 * Pops its children in (fade + scale) when the entry at `path` was ADDED this
 * step. Existing entries render with no entrance animation, so only genuinely
 * new map entries / set elements / array cells animate.
 */
export function PopIn({ path, className, children }) {
  const changes = useContext(StepChangesContext)
  const isNew = path != null && changes.added.has(path)
  return (
    <motion.div
      className={className}
      initial={isNew ? { opacity: 0, scale: 0.85 } : false}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.25 }}
    >
      {children}
    </motion.div>
  )
}
