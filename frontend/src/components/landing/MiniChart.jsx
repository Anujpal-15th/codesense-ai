function MiniChart({ d, color }) {
  return (
    <svg viewBox="0 0 100 48" className="h-full w-full" preserveAspectRatio="none">
      <path d={d} fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

export default MiniChart
