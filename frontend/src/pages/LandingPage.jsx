import { useState } from 'react'
import { Link } from 'react-router-dom'
import CodeBlock from '../components/landing/CodeBlock'
import { EXAMPLES } from '../components/landing/examples'
import MiniChart from '../components/landing/MiniChart'
import PatternCard from '../components/landing/PatternCard'

const PATTERNS = [
  { name: 'Sliding Window', complexity: 'O(n) time · O(1) space', rising: true },
  { name: 'Two Pointers', complexity: 'O(n) time · O(1) space', rising: true },
  { name: 'Binary Search', complexity: 'O(log n) time · O(1) space', rising: true },
  { name: 'Dynamic Programming', complexity: 'O(n²) → O(n) with memo', rising: true },
  { name: 'DFS / BFS', complexity: 'O(V + E) time · O(V) space', rising: true },
  { name: 'Brute Force Nested Loop', complexity: 'O(n²) time · O(1) space', rising: false },
]

const STEPS = [
  {
    label: '01 — INPUT',
    title: 'Paste your function',
    body: 'Java, Python, or JavaScript. No project setup, no config — just the snippet you’re stuck on.',
  },
  {
    label: '02 — ANALYZE',
    title: 'Claude reads the logic',
    body: 'It matches your approach against known interview patterns and works out how it actually scales.',
  },
  {
    label: '03 — VERDICT',
    title: 'Get the real complexity',
    body: 'Time, space, and a plain answer to the only question that matters: is this good enough to say out loud?',
  },
]

function Eyebrow({ children }) {
  return (
    <p className="mb-4 flex items-center gap-2 font-mono text-xs font-semibold tracking-widest text-highlight-ink uppercase">
      <span className="inline-block w-5 border-t border-highlight-ink" />
      {children}
    </p>
  )
}

function LandingPage() {
  const [activeIndex, setActiveIndex] = useState(0)
  const active = EXAMPLES[activeIndex]
  const verdictColor = active.isOptimal ? 'text-approve' : 'text-correct'
  const badgeClass = active.isOptimal ? 'bg-approve/10 text-approve' : 'bg-correct/10 text-correct'
  const chartColor = active.isOptimal ? 'var(--color-approve)' : 'var(--color-correct)'

  return (
    <div className="landing-grid-bg min-h-screen font-sans text-ink">
      <header className="mx-auto flex max-w-6xl items-center justify-between px-6 py-6 sm:px-10">
        <Link to="/" className="flex items-center gap-2 font-mono text-lg font-bold">
          <span className="h-2 w-2 rounded-full bg-approve" />
          codesense <span className="font-normal text-ink-soft">.ai</span>
        </Link>
        <nav className="hidden items-center gap-8 text-sm text-ink-soft sm:flex">
          <a href="#how-it-works" className="hover:text-ink">
            How it works
          </a>
          <a href="#patterns" className="hover:text-ink">
            Patterns
          </a>
          <Link to="/history" className="hover:text-ink">
            History
          </Link>
        </nav>
        <Link
          to="/analyze"
          className="rounded-full border border-ink bg-paper-raised px-4 py-2 font-mono text-sm font-semibold whitespace-nowrap"
        >
          Analyze code &rarr;
        </Link>
      </header>

      <section className="mx-auto max-w-6xl px-6 pt-10 pb-20 sm:px-10">
        <Eyebrow>For technical interview prep</Eyebrow>
        <h1 className="max-w-2xl text-4xl leading-tight font-extrabold sm:text-5xl">
          Every DSA solution has a shape.
          <br />
          <span className="text-approve">See yours.</span>
        </h1>
        <p className="mt-6 max-w-xl text-ink-soft">
          Paste a function. CodeSense names the pattern, plots the real time and space complexity, and tells you
          straight whether it&rsquo;s the optimal approach &mdash; before an interviewer does.
        </p>

        <div className="mt-12 grid grid-cols-1 overflow-hidden rounded-2xl border border-line bg-paper-raised shadow-[0_1px_2px_rgba(0,0,0,0.04)] md:grid-cols-2">
          <div className="border-b border-line p-6 md:border-r md:border-b-0">
            <div className="mb-4 flex items-center justify-between font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">
              <span>Your code</span>
              <span>Java</span>
            </div>
            <div className="mb-4 flex flex-wrap gap-2">
              {EXAMPLES.map((example, i) => (
                <button
                  key={example.id}
                  type="button"
                  onClick={() => setActiveIndex(i)}
                  className={`rounded-full px-3 py-1.5 font-mono text-xs uppercase tracking-widest transition-colors ${
                    i === activeIndex
                      ? 'bg-ink font-semibold text-paper-raised'
                      : 'border border-line text-ink-soft hover:text-ink'
                  }`}
                >
                  {example.pillLabel}
                </button>
              ))}
            </div>
            <CodeBlock lines={active.code} />
          </div>

          <div className="p-6">
            <div className="mb-4 font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">
              The verdict
            </div>
            <div className="mb-4 flex flex-wrap items-center gap-2">
              <span className={`font-mono font-semibold ${verdictColor}`}>{active.patternName}</span>
              <span className={`rounded px-2 py-0.5 font-mono text-xs font-bold uppercase ${badgeClass}`}>
                {active.isOptimal ? 'Optimal' : 'Not optimal'}
              </span>
            </div>
            <div className="relative h-28 rounded-lg border border-line bg-paper px-3 py-3">
              <span className="absolute top-2 left-2 font-mono text-[10px] text-ink-soft">t</span>
              <span className="absolute right-2 bottom-2 font-mono text-[10px] text-ink-soft">n</span>
              <MiniChart d={active.chartPath} color={chartColor} />
            </div>
            <div className="mt-4 grid grid-cols-2 gap-3">
              <div className="rounded-lg border border-line p-3">
                <div className="text-xs text-ink-soft">Time</div>
                <div className="font-mono font-bold">{active.timeComplexity}</div>
              </div>
              <div className="rounded-lg border border-line p-3">
                <div className="text-xs text-ink-soft">Space</div>
                <div className="font-mono font-bold">{active.spaceComplexity}</div>
              </div>
            </div>
            <p className="mt-4 text-sm text-ink-soft">{active.explanation}</p>
          </div>
        </div>
      </section>

      <section id="how-it-works" className="mx-auto max-w-6xl px-6 pt-4 pb-20 sm:px-10">
        <Eyebrow>The process</Eyebrow>
        <h2 className="mb-10 text-3xl font-extrabold">Three steps, no setup</h2>
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
          {STEPS.map((step) => (
            <div key={step.label} className="rounded-xl border border-line bg-paper-raised p-6">
              <div className="mb-3 font-mono text-xs font-semibold tracking-widest text-ink-soft uppercase">
                {step.label}
              </div>
              <div className="mb-2 font-mono font-semibold text-highlight-ink">{step.title}</div>
              <p className="text-sm text-ink-soft">{step.body}</p>
            </div>
          ))}
        </div>
      </section>

      <section id="patterns" className="mx-auto max-w-6xl px-6 pt-4 pb-24 sm:px-10">
        <Eyebrow>Recognized patterns</Eyebrow>
        <h2 className="mb-10 text-3xl font-extrabold">What CodeSense knows to look for</h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {PATTERNS.map((pattern) => (
            <PatternCard key={pattern.name} {...pattern} />
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-3xl px-6 pb-24 text-center sm:px-10">
        <h2 className="text-3xl font-extrabold sm:text-4xl">Bring the snippet you&rsquo;re not sure about</h2>
        <p className="mx-auto mt-4 max-w-xl text-ink-soft">
          The one you wrote at 1am, that passed the test cases but felt like it shouldn&rsquo;t. That&rsquo;s the
          one to paste.
        </p>
        <Link
          to="/analyze"
          className="mt-8 inline-block rounded-full bg-ink px-6 py-3 font-mono text-sm font-semibold text-paper-raised"
        >
          Analyze your code &rarr;
        </Link>
      </section>

      <footer className="mx-auto flex max-w-6xl items-center justify-between border-t border-line px-6 py-6 text-sm text-ink-soft sm:px-10">
        <span className="font-mono">codesense.ai</span>
        <span>Built for interview prep, not production code.</span>
      </footer>
    </div>
  )
}

export default LandingPage
