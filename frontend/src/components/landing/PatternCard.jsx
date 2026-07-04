import MiniChart from './MiniChart'

const RISING_PATH = 'M4,32 C 30,30 55,20 86,8'
const FALLING_PATH = 'M4,34 C 40,32 60,30 86,6'

function PatternCard({ name, complexity, rising = true }) {
  const color = rising ? 'var(--color-approve)' : 'var(--color-correct)'

  return (
    <div className="rounded-xl border border-line bg-paper-raised p-5">
      <div className="mb-4 h-8 w-16">
        <MiniChart d={rising ? RISING_PATH : FALLING_PATH} color={color} />
      </div>
      <div className="font-mono font-semibold text-ink">{name}</div>
      <div className="mt-1 font-mono text-sm text-ink-soft">{complexity}</div>
    </div>
  )
}

export default PatternCard
