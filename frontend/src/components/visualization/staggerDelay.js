// Shared stagger-delay math for entrance animations (Call Stack frames, Memory
// object cards). Delay is based on an item's RANK among items new this step
// transition, not its absolute list position — an old, unchanged item sitting
// further down the list shouldn't inherit a large delay just because of where
// it happens to render.
export const STAGGER_STEP_SECONDS = 0.07
export const POP_IN_DURATION_SECONDS = 0.22

/**
 * @param newRank 0-based rank among the items new this transition (0 = first
 *                new item found, in render order), or -1/undefined if this
 *                item isn't new (caller should skip animating entirely).
 */
export function staggerDelaySeconds(newRank) {
  return Math.max(0, newRank) * STAGGER_STEP_SECONDS
}
