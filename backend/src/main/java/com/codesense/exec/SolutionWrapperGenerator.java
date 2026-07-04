package com.codesense.exec;

import com.codesense.analysis.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Turns a bare LeetCode-style snippet (a class with one or more methods, no
 * {@code main}, sometimes referencing undefined helper types like {@code
 * ListNode}/{@code TreeNode}) into a complete, compilable {@code public
 * class Main} with a synthesized example call - so a user can paste a raw
 * solution and get a runnable trace without writing any driver code
 * themselves.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class SolutionWrapperGenerator {

    private static final String SYSTEM_PROMPT = """
            You are given a raw Java code snippet - typically a LeetCode-style solution: \
            a class with one or more methods, no `main` method, and sometimes referencing \
            common LeetCode helper types (ListNode, TreeNode, Node, etc.) that aren't \
            defined in the snippet.

            Produce a SINGLE complete, compilable Java source file satisfying ALL of these:

            1. The top-level class MUST be named exactly `Main` and MUST be `public class Main`.
            2. Preserve the original method(s) exactly as given - do not alter their logic. \
            If the original class is not named Main, fold its methods into Main as static \
            methods when they don't depend on instance fields; otherwise keep the original \
            class as a static nested class inside Main and instantiate it in main().
            3. If the snippet references common LeetCode helper types not defined in the \
            snippet (ListNode, TreeNode, Node, etc.), define minimal standard versions of \
            them as static nested classes inside Main, using their conventional LeetCode \
            field names and constructors.
            4. Add `public static void main(String[] args)` that builds ONE small, valid, \
            illustrative example input for the primary method's parameters (keep arrays/\
            strings/lists short, 5-10 elements), calls the method with it, and prints the \
            result via System.out.println (use Arrays.toString(...) for array results).

            Respond with ONLY the raw Java source code for the complete file. No markdown \
            code fences, no explanation, no commentary before or after - the response must \
            be directly compilable as-is, starting with any necessary import statements \
            followed by `public class Main { ... }`.""";

    private final LlmClient llmClient;

    String wrap(String snippet) {
        try {
            return llmClient.completeRaw(SYSTEM_PROMPT, snippet);
        } catch (RuntimeException e) {
            log.warn("Failed to auto-generate a runnable wrapper for submitted snippet", e);
            throw new ExecutionFailedException(
                    "Could not automatically generate a runnable entry point for this snippet: " + e.getMessage(), e);
        }
    }
}
