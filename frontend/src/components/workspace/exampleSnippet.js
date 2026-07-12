import { EXAMPLES } from '../landing/examples'

function reconstructMethodBody(example) {
  return example.code.map((line) => line.tokens.map((t) => t.text).join('')).join('\n')
}

// `landing/examples.js`'s `code` arrays are display-only data for the marketing page's
// syntax-highlighted snippet — a bare method with no enclosing class, which isn't valid
// standalone Java. Loading that text directly into the workspace would have no
// `public class Main`, forcing it through the backend's LLM auto-wrap path for every
// "Example" click — an unreliable, extra round-trip for something that should just work
// instantly. Instead we wrap EXAMPLES[0]'s method body in a real, directly-runnable
// `Main` class with a synthesized sample call matching its exact signature
// (`longestSubarray(int[] nums, int k)`), so `hasMainClass()` is true and no LLM/network
// call is ever needed for this button.
// Python twin of the Java example below - same sliding-window algorithm, with
// a top-level driver call so it runs directly (no wrapping needed).
export const PYTHON_EXAMPLE = `def longest_subarray(nums, k):
    left = 0
    total = 0
    best = 0
    for right in range(len(nums)):
        total += nums[right]
        while total > k:
            total -= nums[left]
            left += 1
        best = max(best, right - left + 1)
    return best


print(longest_subarray([2, 1, 3, 4, 1, 2, 1, 5, 4], 8))
`

export function reconstructExamplePlainText() {
  const method = reconstructMethodBody(EXAMPLES[0])
  const indented = method
    .split('\n')
    .map((line) => `    ${line}`)
    .join('\n')

  return [
    'public class Main {',
    `    static ${indented.trimStart()}`,
    '',
    '    public static void main(String[] args) {',
    '        int[] nums = {2, 1, 3, 4, 1, 2, 1, 5, 4};',
    '        int k = 8;',
    '        System.out.println(longestSubarray(nums, k));',
    '    }',
    '}',
    '',
  ].join('\n')
}
