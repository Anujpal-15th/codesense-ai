const RATING_TONES = {
  Excellent: 'text-approve',
  Good: 'text-ink',
  Fair: 'text-highlight-ink',
  Poor: 'text-correct',
}

export function ratingTone(value) {
  return RATING_TONES[value] ?? 'text-ink-soft'
}
