package com.codesense.analysis;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, pre-LLM heuristic: detects code that defines its own
 * self-referential data structure (a custom class with a Node/next/left/
 * head-style field pointing at another user-defined type) without using any
 * java.util collection - the exact shape of code the LLM was observed
 * mislabeling with a named algorithmic pattern ("Sliding Window", "Two
 * Pointers") on two separate runs of the identical snippet when no such
 * pattern applies at all. Used to ground {@link AnalysisService}'s prompt
 * with a hint, not to override the LLM's answer outright - a plain custom
 * linked list is common, but a class that happens to have a "next" field
 * could still legitimately implement a real pattern internally, so the LLM
 * keeps final judgment.
 */
@Component
class CustomDataStructureDetector {

    private static final Set<String> LINK_FIELD_NAMES = Set.of(
            "next", "prev", "previous", "left", "right", "child", "head", "tail", "parent", "sibling");

    private static final Pattern CLASS_DECLARATION = Pattern.compile("\\bclass\\s+(\\w+)");

    private static final Pattern JAVA_UTIL_USAGE = Pattern.compile(
            "java\\.util|\\b(List|ArrayList|LinkedList|Map|HashMap|TreeMap|Set|HashSet|TreeSet|"
                    + "Queue|Deque|ArrayDeque|Stack|PriorityQueue)\\s*<");

    boolean looksLikeCustomDataStructure(String code) {
        if (JAVA_UTIL_USAGE.matcher(code).find()) {
            return false;
        }

        for (String className : declaredClassNames(code)) {
            for (String linkFieldName : LINK_FIELD_NAMES) {
                Pattern fieldPattern = Pattern.compile(
                        "\\b" + Pattern.quote(className) + "\\s+" + Pattern.quote(linkFieldName) + "\\s*;");
                if (fieldPattern.matcher(code).find()) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> declaredClassNames(String code) {
        Set<String> names = new HashSet<>();
        Matcher matcher = CLASS_DECLARATION.matcher(code);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
