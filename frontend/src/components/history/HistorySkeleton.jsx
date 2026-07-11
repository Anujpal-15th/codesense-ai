// Animated gray placeholder cards, shown on the first load instead of a blank
// page (the history GET can be slow on a cold backend). Mirrors HistoryCard's
// shape so the layout doesn't jump when real data arrives.
function SkeletonCard() {
  return (
    <div className="rounded-xl border border-line bg-paper-raised p-5">
      <div className="flex items-start justify-between gap-4">
        <div className="h-6 w-40 animate-pulse rounded bg-line" />
        <div className="h-4 w-20 animate-pulse rounded bg-line" />
      </div>
      <div className="mt-3 flex gap-2">
        <div className="h-6 w-20 animate-pulse rounded-md bg-line" />
        <div className="h-6 w-20 animate-pulse rounded-md bg-line" />
        <div className="h-6 w-24 animate-pulse rounded-md bg-line" />
      </div>
      <div className="mt-4 space-y-2 rounded-lg border border-line bg-paper px-3 py-2.5">
        <div className="h-3.5 w-3/4 animate-pulse rounded bg-line" />
        <div className="h-3.5 w-1/2 animate-pulse rounded bg-line" />
      </div>
    </div>
  )
}

function HistorySkeleton({ count = 4 }) {
  return (
    <div className="space-y-3" aria-hidden="true">
      {Array.from({ length: count }, (_, i) => (
        <SkeletonCard key={i} />
      ))}
    </div>
  )
}

export default HistorySkeleton
