function line(...tokens) {
  return { tokens }
}

function kw(text) {
  return { text, kind: 'keyword' }
}

function plain(text) {
  return { text, kind: 'plain' }
}

function comment(text) {
  return { text, kind: 'comment' }
}

export const EXAMPLES = [
  {
    id: 'sliding-window',
    pillLabel: 'Sliding Window',
    patternName: 'Sliding Window · Variable Size',
    isOptimal: true,
    timeComplexity: 'O(n)',
    spaceComplexity: 'O(1)',
    explanation:
      'Single pass with a shrinking/expanding window — each element is visited at most twice, so this is already the efficient form for this problem.',
    chartPath: 'M4,44 C 30,42 60,30 96,4',
    code: [
      line(kw('int'), plain(' longestSubarray('), kw('int'), plain('[] nums, '), kw('int'), plain(' k) {')),
      line(plain('  '), kw('int'), plain(' left = 0, sum = 0, best = 0;')),
      line(
        plain('  '),
        kw('for'),
        plain(' ('),
        kw('int'),
        plain(' right = 0; right < nums.length; right++) {'),
      ),
      line(plain('    sum += nums[right];')),
      line(comment('    // shrink while window is invalid')),
      line(plain('    '), kw('while'), plain(' (sum > k) {')),
      line(plain('      sum -= nums[left++];')),
      line(plain('    }')),
      line(plain('    best = Math.max(best, right - left + 1);')),
      line(plain('  }')),
      line(plain('  '), kw('return'), plain(' best;')),
      line(plain('}')),
    ],
  },
  {
    id: 'brute-force',
    pillLabel: 'Nested Loop',
    patternName: 'Brute Force Pair Search',
    isOptimal: false,
    timeComplexity: 'O(n²)',
    spaceComplexity: 'O(1)',
    explanation:
      'Every element is compared against every other element with nested loops — correct, but re-does work a single hash-map pass could avoid.',
    chartPath: 'M4,44 C 20,43 40,42 60,36 C 75,30 85,18 96,4',
    code: [
      line(kw('boolean'), plain(' hasPair('), kw('int'), plain('[] nums, '), kw('int'), plain(' target) {')),
      line(plain('  '), kw('for'), plain(' ('), kw('int'), plain(' i = 0; i < nums.length; i++) {')),
      line(plain('    '), kw('for'), plain(' ('), kw('int'), plain(' j = i + 1; j < nums.length; j++) {')),
      line(plain('      '), kw('if'), plain(' (nums[i] + nums[j] == target) {')),
      line(plain('        '), kw('return'), plain(' '), kw('true'), plain(';')),
      line(plain('      }')),
      line(plain('    }')),
      line(plain('  }')),
      line(plain('  '), kw('return'), plain(' '), kw('false'), plain(';')),
      line(plain('}')),
    ],
  },
  {
    id: 'binary-search',
    pillLabel: 'Binary Search',
    patternName: 'Binary Search · Sorted Array',
    isOptimal: true,
    timeComplexity: 'O(log n)',
    spaceComplexity: 'O(1)',
    explanation:
      'The search space is halved on every step — this is the ceiling for a comparison-based search on a sorted array, nothing left to optimize.',
    chartPath: 'M4,44 C 15,20 25,6 40,4 L96,4',
    code: [
      line(kw('int'), plain(' search('), kw('int'), plain('[] nums, '), kw('int'), plain(' target) {')),
      line(plain('  '), kw('int'), plain(' lo = 0, hi = nums.length - 1;')),
      line(plain('  '), kw('while'), plain(' (lo <= hi) {')),
      line(plain('    '), kw('int'), plain(' mid = lo + (hi - lo) / 2;')),
      line(plain('    '), kw('if'), plain(' (nums[mid] == target) '), kw('return'), plain(' mid;')),
      line(plain('    '), kw('if'), plain(' (nums[mid] < target) lo = mid + 1;')),
      line(plain('    '), kw('else'), plain(' hi = mid - 1;')),
      line(plain('  }')),
      line(plain('  '), kw('return'), plain(' -1;')),
      line(plain('}')),
    ],
  },
]
