const RATING_TONES = {
  Excellent: 'text-approve',
  Good: 'text-paper-raised',
  Fair: 'text-highlight-ink',
  Poor: 'text-correct',
}

export function ratingTone(value) {
  return RATING_TONES[value] ?? 'text-paper-raised/60'
}
