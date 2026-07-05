import { useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'

// Panel-level "what does this mean?" explainer. Collapsed by default (opt-in,
// no permanent clutter) — one toggle teaches the general vocabulary once,
// rather than attaching a tooltip to every individual frame/card that repeats
// the same concept. Mirrors the existing ▾/▸ expand pattern already used by
// GenericObjectCard in ValueRenderer.jsx.
function InfoToggle({ title, children }) {
  const [open, setOpen] = useState(false)

  return (
    <div className="mb-3">
      <div className="flex items-center justify-between">
        <h2 className="font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">{title}</h2>
        <button
          type="button"
          onClick={() => setOpen((prev) => !prev)}
          aria-expanded={open}
          title="What does this mean?"
          className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full border border-line font-mono text-[10px] font-semibold text-ink-soft hover:border-ink-soft hover:text-ink"
        >
          ?
        </button>
      </div>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.2 }}
            className="overflow-hidden"
          >
            <div className="mt-2 space-y-2 rounded-md bg-paper p-3 text-xs text-ink-soft">{children}</div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

export default InfoToggle
