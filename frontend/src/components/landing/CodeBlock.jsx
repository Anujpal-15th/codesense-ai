const KIND_CLASS = {
  keyword: 'text-ink font-semibold',
  comment: 'text-ink-soft italic',
  plain: 'text-ink',
}

function CodeBlock({ lines }) {
  return (
    <pre className="rounded-lg bg-paper p-4 font-mono text-[13px] leading-6 text-ink">
      {lines.map((line, i) => (
        <div key={i} className="flex">
          <span className="mr-4 w-4 shrink-0 select-none text-right text-line">{i + 1}</span>
          <span>
            {line.tokens.map((token, j) => (
              <span key={j} className={KIND_CLASS[token.kind] ?? KIND_CLASS.plain}>
                {token.text}
              </span>
            ))}
          </span>
        </div>
      ))}
    </pre>
  )
}

export default CodeBlock
