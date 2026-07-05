#!/usr/bin/env node
/**
 * CodeSense AI regression suite - hits the REAL running backend
 * (Analysis + Execution flows) with a fixed set of cases drawn from this
 * project's actual bug history, and prints a pass/fail report.
 *
 * Run:            node regression-suite.mjs
 * Subset:         node regression-suite.mjs --only analysis
 *                 node regression-suite.mjs --only execution
 * Other backend:  BASE_URL=http://localhost:9090 node regression-suite.mjs
 *
 * Requirements: backend running on BASE_URL (default http://localhost:8080)
 * with a working LLM provider - analysis cases make real LLM calls, so the
 * whole run takes ~2-3 minutes and each analysis case persists a history row.
 *
 * Assertion model (LLM output varies run to run, so labels are matched as
 * case-insensitive substrings against acceptable/rejected sets):
 *   analysis:  patternAnyOf   - pattern must contain at least one of these
 *              patternNoneOf  - pattern must contain none of these (fabrication guard)
 *              timeAnyOf / spaceAnyOf - normalized complexity must equal one of these
 *                                       (null = don't assert; too unstable to pin)
 *   execution: status, outcome, wasWrapped, consoleContains, errorContains
 */

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8080';
const REQUEST_TIMEOUT_MS = 120_000;
const DELAY_BETWEEN_ANALYSIS_MS = 500; // be gentle on the LLM free tier

// ---------------------------------------------------------------------------
// Test cases
// ---------------------------------------------------------------------------

const CASES = [
  // ---- Analysis: normal cases with known-correct expectations ----
  {
    id: 'analysis-binary-search',
    flow: 'analysis',
    name: 'Binary search on sorted array',
    source: `class Solution {
    public int search(int[] nums, int target) {
        int lo = 0, hi = nums.length - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (nums[mid] == target) return mid;
            if (nums[mid] < target) lo = mid + 1;
            else hi = mid - 1;
        }
        return -1;
    }
}`,
    expect: {
      patternAnyOf: ['binary search'],
      timeAnyOf: ['o(logn)'],
      spaceAnyOf: ['o(1)'],
    },
  },
  {
    id: 'analysis-two-sum-hashmap',
    flow: 'analysis',
    name: 'Two Sum via HashMap (historical "Two Pointers" mislabel, id 63)',
    source: `import java.util.HashMap;
import java.util.Map;

class Solution {
    public int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) {
                return new int[] { map.get(complement), i };
            }
            map.put(nums[i], i);
        }
        return new int[0];
    }
}`,
    expect: {
      patternAnyOf: ['hash'],
      patternNoneOf: ['two pointer', 'sliding window'],
      timeAnyOf: ['o(n)'],
      spaceAnyOf: ['o(n)'],
    },
  },
  {
    id: 'analysis-factorial',
    flow: 'analysis',
    name: 'Recursive factorial (label unstable historically: ids 67 vs 74)',
    source: `public class Main {
    static int factorial(int n) {
        if (n <= 1) return 1;
        return n * factorial(n - 1);
    }
    public static void main(String[] args) {
        System.out.println(factorial(4));
    }
}`,
    expect: {
      // Both honest labels seen in history are acceptable; fabricated
      // named patterns are not.
      patternAnyOf: ['recurs', 'not a standard', 'factorial'],
      patternNoneOf: ['sliding window', 'two pointer', 'hash', 'binary search'],
      timeAnyOf: ['o(n)'],
      spaceAnyOf: ['o(n)'],
    },
  },
  {
    id: 'analysis-bfs',
    flow: 'analysis',
    name: 'BFS level-order traversal (queue)',
    source: `import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

class TreeNode {
    int val;
    TreeNode left, right;
    TreeNode(int val) { this.val = val; }
}

class Solution {
    public List<Integer> levelOrder(TreeNode root) {
        List<Integer> out = new ArrayList<>();
        if (root == null) return out;
        Queue<TreeNode> queue = new LinkedList<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            TreeNode node = queue.poll();
            out.add(node.val);
            if (node.left != null) queue.add(node.left);
            if (node.right != null) queue.add(node.right);
        }
        return out;
    }
}`,
    expect: {
      patternAnyOf: ['bfs', 'breadth'],
      timeAnyOf: ['o(n)', 'o(v+e)'],
      spaceAnyOf: ['o(n)', 'o(w)', 'o(v)'],
    },
  },
  {
    id: 'analysis-dfs',
    flow: 'analysis',
    name: 'DFS grid flood fill (number of islands)',
    source: `class Solution {
    public int numIslands(char[][] grid) {
        int count = 0;
        for (int r = 0; r < grid.length; r++) {
            for (int c = 0; c < grid[0].length; c++) {
                if (grid[r][c] == '1') {
                    count++;
                    sink(grid, r, c);
                }
            }
        }
        return count;
    }

    private void sink(char[][] grid, int r, int c) {
        if (r < 0 || r >= grid.length || c < 0 || c >= grid[0].length || grid[r][c] != '1') return;
        grid[r][c] = '0';
        sink(grid, r + 1, c);
        sink(grid, r - 1, c);
        sink(grid, r, c + 1);
        sink(grid, r, c - 1);
    }
}`,
    expect: {
      patternAnyOf: ['dfs', 'depth', 'flood'],
      timeAnyOf: ['o(m*n)', 'o(mn)', 'o(n*m)', 'o(nm)', 'o(rows*cols)', 'o(n)'],
      spaceAnyOf: null, // recursion-depth phrasing varies too much to pin
    },
  },
  {
    id: 'analysis-bubble-sort',
    flow: 'analysis',
    name: 'Bubble sort',
    source: `class Solution {
    public void bubbleSort(int[] arr) {
        for (int i = 0; i < arr.length - 1; i++) {
            for (int j = 0; j < arr.length - 1 - i; j++) {
                if (arr[j] > arr[j + 1]) {
                    int tmp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = tmp;
                }
            }
        }
    }
}`,
    expect: {
      patternAnyOf: ['sort', 'bubble'],
      timeAnyOf: ['o(n^2)'],
      spaceAnyOf: ['o(1)'],
    },
  },
  {
    id: 'analysis-two-pointers',
    flow: 'analysis',
    name: 'Two pointers palindrome check',
    source: `class Solution {
    public boolean isPalindrome(String s) {
        int i = 0, j = s.length() - 1;
        while (i < j) {
            if (s.charAt(i) != s.charAt(j)) return false;
            i++;
            j--;
        }
        return true;
    }
}`,
    expect: {
      patternAnyOf: ['two pointer'],
      timeAnyOf: ['o(n)'],
      spaceAnyOf: ['o(1)'],
    },
  },
  {
    id: 'analysis-sliding-window',
    flow: 'analysis',
    name: 'Sliding window longest substring without repeats',
    source: `import java.util.HashSet;
import java.util.Set;

class Solution {
    public int lengthOfLongestSubstring(String s) {
        Set<Character> window = new HashSet<>();
        int left = 0, maxLen = 0;
        for (int right = 0; right < s.length(); right++) {
            while (window.contains(s.charAt(right))) {
                window.remove(s.charAt(left));
                left++;
            }
            window.add(s.charAt(right));
            maxLen = Math.max(maxLen, right - left + 1);
        }
        return maxLen;
    }
}`,
    expect: {
      patternAnyOf: ['sliding window'],
      timeAnyOf: ['o(n)'],
      spaceAnyOf: ['o(min(n,m))', 'o(min(m,n))', 'o(m)', 'o(k)', 'o(n)', 'o(1)', 'o(26)', 'o(128)', 'o(256)'],
    },
  },

  // ---- Analysis: custom data structure (historical ollama "Sliding Window" bias) ----
  {
    id: 'analysis-custom-linkedlist',
    flow: 'analysis',
    name: 'Custom LinkedList class (must NOT get a named algorithmic pattern)',
    source: `class Node {
    int value;
    Node next;
    Node(int value) { this.value = value; }
}

class MyLinkedList {
    Node head;

    void insert(int value) {
        Node node = new Node(value);
        if (head == null) { head = node; return; }
        Node cur = head;
        while (cur.next != null) cur = cur.next;
        cur.next = node;
    }

    boolean contains(int value) {
        for (Node cur = head; cur != null; cur = cur.next) {
            if (cur.value == value) return true;
        }
        return false;
    }
}`,
    expect: {
      patternAnyOf: ['custom data structure', 'linked list'],
      patternNoneOf: ['sliding window', 'two pointer', 'binary search', 'dynamic programming'],
      timeAnyOf: null,
      spaceAnyOf: null,
    },
  },

  // ---- Analysis: empty / near-empty (historical fabrication, id 72) ----
  {
    id: 'analysis-empty-main',
    flow: 'analysis',
    name: 'Empty main (historically fabricated "custom linked list", id 72)',
    source: `public class Main {
    public static void main(String[] args) {

    }
}`,
    expect: {
      patternAnyOf: ['no code', 'not a standard', 'none', 'empty', 'unclear', 'no algorithmic', 'n/a'],
      patternNoneOf: ['custom data structure', 'linked list', 'sliding window', 'two pointer', 'binary search', 'hash', 'sort'],
      timeAnyOf: null,
      spaceAnyOf: null,
    },
  },
  {
    id: 'analysis-near-empty',
    flow: 'analysis',
    name: 'Near-empty trivial method (addOne)',
    source: `class Solution {
    public int addOne(int x) {
        return x + 1;
    }
}`,
    expect: {
      patternAnyOf: ['not a standard', 'no algorithmic', 'unclear', 'simple', 'arithmetic', 'basic'],
      patternNoneOf: ['sliding window', 'two pointer', 'binary search', 'hash', 'dynamic programming', 'custom data structure'],
      timeAnyOf: ['o(1)'],
      spaceAnyOf: ['o(1)'],
    },
  },

  // ---- Analysis: complexity-improvement claims (fabrication guard, ids 97/98) ----
  {
    id: 'analysis-maxpoints-no-improvement',
    flow: 'analysis',
    name: 'Max Points on a Line (O(n^2) IS optimal - must NOT fabricate an improvement)',
    source: `import java.util.HashMap;
import java.util.Map;

class Solution {
    public int maxPoints(int[][] points) {
        int n = points.length;
        if (n <= 2) return n;
        int globalMax = 1;
        for (int i = 0; i < n; i++) {
            Map<String, Integer> slopeMap = new HashMap<>();
            int localMax = 0;
            for (int j = i + 1; j < n; j++) {
                int dx = points[j][0] - points[i][0];
                int dy = points[j][1] - points[i][1];
                int g = gcd(dx, dy);
                dx /= g; dy /= g;
                if (dx < 0) { dx = -dx; dy = -dy; } else if (dx == 0) { dy = Math.abs(dy); }
                String key = dy + "/" + dx;
                slopeMap.put(key, slopeMap.getOrDefault(key, 0) + 1);
                localMax = Math.max(localMax, slopeMap.get(key));
            }
            globalMax = Math.max(globalMax, localMax + 1);
        }
        return globalMax;
    }
    private int gcd(int a, int b) { while (b != 0) { int t = b; b = a % b; a = t; } return a; }
}`,
    expect: {
      // Whether via the prompt fix (model marks it optimal) or the guard
      // (neutralizes an incoherent claim), the result must NOT assert a bogus
      // sub-quadratic improvement. O(n^2) is the accepted optimal for LC 149.
      improvementClaimed: false,
    },
  },
  {
    id: 'analysis-bubble-improvement',
    flow: 'analysis',
    name: 'Bubble sort STILL suggests O(n log n) (guard must NOT suppress a valid claim)',
    source: `class Solution {
    public void bubbleSort(int[] arr) {
        for (int i = 0; i < arr.length - 1; i++) {
            for (int j = 0; j < arr.length - 1 - i; j++) {
                if (arr[j] > arr[j + 1]) {
                    int tmp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = tmp;
                }
            }
        }
    }
}`,
    expect: {
      // Comparison sort -> O(n log n) is a genuine, achievable improvement.
      // This is the "guard didn't become overly aggressive" canary.
      improvementClaimed: true,
      suggestedAnyOf: ['o(nlogn)'],
    },
  },

  // ---- Execution: bare collections with no imports (Fix #1 regression) ----
  {
    id: 'exec-bare-map',
    flow: 'execution',
    name: 'Bare Map<String,Integer> param, NO import (was 422 before LLM fallback fix)',
    source: `class Solution { public int sum(Map<String,Integer> m) { int t=0; for (int v : m.values()) t+=v; return t; } }`,
    expect: { status: 201, outcome: 'NORMAL', wasWrapped: true },
  },
  {
    id: 'exec-bare-list',
    flow: 'execution',
    name: 'Bare List<Integer> param, NO import (was 422 before LLM fallback fix)',
    source: `class Solution { public int sum(List<Integer> nums) { int t=0; for (int v : nums) t+=v; return t; } }`,
    expect: { status: 201, outcome: 'NORMAL', wasWrapped: true },
  },
  {
    id: 'exec-bare-set',
    flow: 'execution',
    name: 'Bare Set<Integer> param, NO import (was 422 before LLM fallback fix)',
    source: `class Solution { public int size(Set<Integer> s) { return s.size(); } }`,
    expect: { status: 201, outcome: 'NORMAL', wasWrapped: true },
  },

  // ---- Execution: entry-point detection shapes (prior entry-point bug history) ----
  {
    id: 'exec-main-in-second-class',
    flow: 'execution',
    name: 'Multi-class file, main NOT in the first class',
    source: `class Helper {
    static int triple(int x) { return x * 3; }
}

public class App {
    public static void main(String[] args) {
        System.out.println(Helper.triple(14));
    }
}`,
    expect: { status: 201, outcome: 'NORMAL', wasWrapped: false, consoleContains: '42' },
  },
  {
    id: 'exec-main-not-named-main',
    flow: 'execution',
    name: 'Runnable class with main, class NOT named "Main"',
    source: `public class Runner {
    public static void main(String[] args) {
        System.out.println("runner-ok");
    }
}`,
    expect: { status: 201, outcome: 'NORMAL', wasWrapped: false, consoleContains: 'runner-ok' },
  },

  // ---- Execution: wrapper + outcome paths ----
  {
    id: 'exec-deterministic-int',
    flow: 'execution',
    name: 'Bare int method (deterministic wrap, no LLM)',
    source: `class Solution { public int square(int n) { return n * n; } }`,
    expect: { status: 201, outcome: 'NORMAL', wasWrapped: true, consoleContains: '49' },
  },
  {
    id: 'exec-custom-class-param',
    flow: 'execution',
    name: 'Simple custom class param (Interval) - deterministic wrap',
    source: `class Interval {
    int start; int end;
    Interval(int start, int end) { this.start = start; this.end = end; }
}
class Solution { public int length(Interval a) { return a.end - a.start; } }`,
    expect: { status: 201, outcome: 'NORMAL', wasWrapped: true },
  },
  {
    id: 'exec-compile-error',
    flow: 'execution',
    name: 'User compile error in own Main must stay 422 with diagnostics',
    source: `public class Main {
    public static void main(String[] args) {
        int x = "oops";
    }
}`,
    expect: { status: 422, errorContains: 'incompatible types' },
  },
  {
    id: 'exec-runtime-exception',
    flow: 'execution',
    name: 'Runtime exception is a normal 201 with EXCEPTION outcome',
    source: `public class Main {
    public static void main(String[] args) {
        int[] a = new int[2];
        System.out.println(a[5]);
    }
}`,
    expect: { status: 201, outcome: 'EXCEPTION', wasWrapped: false },
  },
];

// ---------------------------------------------------------------------------
// Runner
// ---------------------------------------------------------------------------

const normalizeComplexity = (s) =>
  (s ?? '')
    .toLowerCase()
    .replace(/²/g, '^2')
    .replace(/[\s·×]/g, '')
    .replace(/⋅/g, '*');

const containsAny = (haystack, needles) =>
  needles.some((n) => haystack.toLowerCase().includes(n.toLowerCase()));

async function post(path, body) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    const json = await res.json().catch(() => ({}));
    return { status: res.status, json };
  } finally {
    clearTimeout(timer);
  }
}

function checkAnalysis(expect, status, json) {
  const failures = [];
  if (status !== 201) {
    failures.push(`expected HTTP 201, got ${status} (${JSON.stringify(json).slice(0, 200)})`);
    return failures;
  }
  const pattern = json.pattern ?? '';
  if (expect.patternAnyOf && !containsAny(pattern, expect.patternAnyOf)) {
    failures.push(`pattern "${pattern}" matched none of [${expect.patternAnyOf.join(', ')}]`);
  }
  if (expect.patternNoneOf && containsAny(pattern, expect.patternNoneOf)) {
    failures.push(`pattern "${pattern}" matched a REJECTED label from [${expect.patternNoneOf.join(', ')}]`);
  }
  if (expect.timeAnyOf) {
    const t = normalizeComplexity(json.timeComplexity);
    if (!expect.timeAnyOf.map(normalizeComplexity).includes(t)) {
      failures.push(`timeComplexity "${json.timeComplexity}" not in [${expect.timeAnyOf.join(', ')}]`);
    }
  }
  if (expect.spaceAnyOf) {
    const s = normalizeComplexity(json.spaceComplexity);
    if (!expect.spaceAnyOf.map(normalizeComplexity).includes(s)) {
      failures.push(`spaceComplexity "${json.spaceComplexity}" not in [${expect.spaceAnyOf.join(', ')}]`);
    }
  }
  // A complexity-improvement is "claimed" iff the model marks the code
  // suboptimal AND names a suggested complexity strictly different from the
  // current one. isOptimal:true, a null suggestion, or suggested==current all
  // mean "no improvement claimed".
  if (expect.improvementClaimed !== undefined) {
    const time = normalizeComplexity(json.timeComplexity);
    const suggested = json.suggestedTimeComplexity ? normalizeComplexity(json.suggestedTimeComplexity) : null;
    const claimed = json.isOptimal === false && suggested !== null && suggested !== time;
    if (expect.improvementClaimed === false && claimed) {
      failures.push(`expected NO improvement claim but got suggested="${json.suggestedTimeComplexity}" vs time="${json.timeComplexity}" (isOptimal=${json.isOptimal})`);
    }
    if (expect.improvementClaimed === true && !claimed) {
      failures.push(`expected an improvement claim but none present (isOptimal=${json.isOptimal}, suggested="${json.suggestedTimeComplexity}", time="${json.timeComplexity}")`);
    }
  }
  if (expect.suggestedAnyOf) {
    const s = normalizeComplexity(json.suggestedTimeComplexity);
    if (!expect.suggestedAnyOf.map(normalizeComplexity).includes(s)) {
      failures.push(`suggestedTimeComplexity "${json.suggestedTimeComplexity}" not in [${expect.suggestedAnyOf.join(', ')}]`);
    }
  }
  return failures;
}

function checkExecution(expect, status, json) {
  const failures = [];
  if (status !== expect.status) {
    failures.push(`expected HTTP ${expect.status}, got ${status} (${JSON.stringify(json).slice(0, 200)})`);
    return failures;
  }
  if (expect.errorContains) {
    const err = json.error ?? '';
    if (!err.toLowerCase().includes(expect.errorContains.toLowerCase())) {
      failures.push(`error "${err}" does not contain "${expect.errorContains}"`);
    }
  }
  if (expect.outcome) {
    const outcome = json.trace?.outcome;
    if (outcome !== expect.outcome) {
      failures.push(`outcome ${outcome} !== ${expect.outcome}`);
    }
  }
  if (expect.wasWrapped !== undefined && json.wasWrapped !== expect.wasWrapped) {
    failures.push(`wasWrapped ${json.wasWrapped} !== ${expect.wasWrapped}`);
  }
  if (expect.consoleContains) {
    const out = json.trace?.consoleOutput ?? '';
    if (!out.includes(expect.consoleContains)) {
      failures.push(`consoleOutput ${JSON.stringify(out.slice(0, 80))} does not contain "${expect.consoleContains}"`);
    }
  }
  return failures;
}

function summarizeActual(flow, status, json) {
  if (flow === 'analysis') {
    return status === 201
      ? `pattern="${json.pattern}" time=${json.timeComplexity} space=${json.spaceComplexity} opt=${json.isOptimal} sugg=${JSON.stringify(json.suggestedTimeComplexity)}`
      : `HTTP ${status} ${JSON.stringify(json).slice(0, 120)}`;
  }
  return status === 201
    ? `outcome=${json.trace?.outcome} wrapped=${json.wasWrapped} console=${JSON.stringify((json.trace?.consoleOutput ?? '').slice(0, 40))}`
    : `HTTP ${status} error=${JSON.stringify(json.error ?? '').slice(0, 100)}`;
}

async function main() {
  const onlyArg = process.argv.indexOf('--only');
  const only = onlyArg >= 0 ? process.argv[onlyArg + 1] : null;
  const cases = only ? CASES.filter((c) => c.flow === only) : CASES;
  if (cases.length === 0) {
    console.error(`No cases match --only ${only} (use "analysis" or "execution")`);
    process.exit(2);
  }

  // Health check
  try {
    const health = await fetch(`${BASE_URL}/api/analyses`, { signal: AbortSignal.timeout(5000) });
    if (!health.ok) throw new Error(`HTTP ${health.status}`);
  } catch (e) {
    console.error(`Backend not reachable at ${BASE_URL} (${e.message}) - start it first.`);
    process.exit(2);
  }

  console.log(`CodeSense regression suite - ${cases.length} cases against ${BASE_URL}\n`);
  const results = [];

  for (let i = 0; i < cases.length; i++) {
    const c = cases[i];
    const started = Date.now();
    let failures;
    let actual;
    try {
      const { status, json } =
        c.flow === 'analysis'
          ? await post('/api/analyses', { codeSnippet: c.source })
          : await post('/api/executions', { sourceCode: c.source });
      failures = c.flow === 'analysis' ? checkAnalysis(c.expect, status, json) : checkExecution(c.expect, status, json);
      actual = summarizeActual(c.flow, status, json);
    } catch (e) {
      failures = [`request failed: ${e.message}`];
      actual = '(no response)';
    }
    const secs = ((Date.now() - started) / 1000).toFixed(1);
    const pass = failures.length === 0;
    results.push({ c, pass, failures, actual });

    const tag = pass ? 'PASS' : 'FAIL';
    console.log(`[${String(i + 1).padStart(2)}/${cases.length}] ${tag}  ${c.flow.padEnd(9)} ${c.id.padEnd(32)} (${secs}s)  ${actual}`);
    if (!pass) {
      for (const f of failures) console.log(`         - ${f}`);
    }

    if (c.flow === 'analysis' && i < cases.length - 1) {
      await new Promise((r) => setTimeout(r, DELAY_BETWEEN_ANALYSIS_MS));
    }
  }

  const passed = results.filter((r) => r.pass).length;
  const failed = results.length - passed;
  console.log(`\n==== ${passed}/${results.length} passed, ${failed} failed ====`);
  if (failed > 0) {
    console.log('\nFailed cases:');
    for (const r of results.filter((r) => !r.pass)) {
      console.log(`  - ${r.c.id}: ${r.c.name}`);
    }
  }
  process.exit(failed > 0 ? 1 : 0);
}

main();
