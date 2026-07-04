package com.codesense.exec;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fast, non-LLM alternative to {@link SolutionWrapperGenerator} for the common
 * case: a single LeetCode-style class with a primary method and only
 * well-known parameter/return types. Parses the snippet with lightweight
 * structural scanning (no LLM round-trip), synthesizes sample arguments, and
 * appends a {@code public class Main}. Falls back (returns {@link
 * Optional#empty()}) for anything it isn't confident about - unknown types,
 * ambiguous constructors, unsupported shapes - so {@link ExecutionService}
 * can hand those off to the LLM-based wrapper unchanged.
 */
@Slf4j
@Service
class DeterministicWrapperGenerator {

    private static final Set<String> HELPER_TYPE_NAMES = Set.of("ListNode", "TreeNode", "Node", "TreeLinkNode");
    private static final Set<String> MODIFIER_WORDS =
            Set.of("public", "private", "protected", "static", "final", "abstract", "strictfp");

    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "((?:(?:public|private|protected|static|final|synchronized|abstract|native|strictfp)\\s+)*)"
                    + "(?:<[^>]*>\\s+)?"
                    + "([\\w\\[\\]<>,.]+?)\\s+"
                    + "(\\w+)\\s*"
                    + "\\(([^)]*)\\)"
                    + "\\s*(?:throws\\s+[\\w.,\\s]+)?\\s*");

    private static final Map<String, String> SCALAR_LITERALS = Map.ofEntries(
            Map.entry("int", "7"), Map.entry("Integer", "7"),
            Map.entry("long", "7L"), Map.entry("Long", "7L"),
            Map.entry("double", "7.0"), Map.entry("Double", "7.0"),
            Map.entry("float", "7.0f"), Map.entry("Float", "7.0f"),
            Map.entry("boolean", "true"), Map.entry("Boolean", "true"),
            Map.entry("char", "'a'"), Map.entry("Character", "'a'"),
            Map.entry("byte", "7"), Map.entry("Byte", "7"),
            Map.entry("short", "7"), Map.entry("Short", "7"));

    private static final Map<String, String> ARRAY_1D_LITERALS = Map.of(
            "int", "new int[]{4, 2, 7, 1, 9}",
            "Integer", "new Integer[]{4, 2, 7, 1, 9}",
            "long", "new long[]{4L, 2L, 7L, 1L, 9L}",
            "double", "new double[]{4.0, 2.0, 7.0, 1.0, 9.0}",
            "boolean", "new boolean[]{true, false, true}",
            "char", "new char[]{'a', 'b', 'c'}",
            "String", "new String[]{\"alpha\", \"beta\", \"gamma\"}");

    private static final Map<String, String> ARRAY_2D_LITERALS = Map.of(
            "int", "new int[][]{{1, 2}, {3, 4}, {5, 6}}",
            "char", "new char[][]{{'a', 'b'}, {'c', 'd'}}",
            "String", "new String[][]{{\"a\", \"b\"}, {\"c\", \"d\"}}");

    private static final Set<String> BOXED_LIST_ELEMENT_TYPES =
            Set.of("Integer", "Long", "Double", "String", "Boolean", "Character");

    private static final String LIST_NODE_CLASS = """
            class ListNode {
                int val;
                ListNode next;
                ListNode() {}
                ListNode(int val) { this.val = val; }
                ListNode(int val, ListNode next) { this.val = val; this.next = next; }
            }""";

    private static final String LIST_NODE_HELPERS = """
                static ListNode buildList(int[] values) {
                    ListNode dummy = new ListNode(0);
                    ListNode tail = dummy;
                    for (int v : values) {
                        tail.next = new ListNode(v);
                        tail = tail.next;
                    }
                    return dummy.next;
                }

                static String listToString(ListNode head) {
                    StringBuilder sb = new StringBuilder();
                    while (head != null) {
                        sb.append(head.val);
                        if (head.next != null) sb.append(" -> ");
                        head = head.next;
                    }
                    return sb.toString();
                }
            """;

    private static final String TREE_NODE_CLASS = """
            class TreeNode {
                int val;
                TreeNode left;
                TreeNode right;
                TreeNode() {}
                TreeNode(int val) { this.val = val; }
                TreeNode(int val, TreeNode left, TreeNode right) { this.val = val; this.left = left; this.right = right; }
            }""";

    private static final String TREE_NODE_HELPERS = """
                static TreeNode buildTree(Integer[] values) {
                    if (values.length == 0 || values[0] == null) return null;
                    TreeNode root = new TreeNode(values[0]);
                    java.util.Queue<TreeNode> queue = new java.util.LinkedList<>();
                    queue.add(root);
                    int i = 1;
                    while (!queue.isEmpty() && i < values.length) {
                        TreeNode node = queue.poll();
                        if (i < values.length) {
                            Integer leftVal = values[i++];
                            if (leftVal != null) {
                                node.left = new TreeNode(leftVal);
                                queue.add(node.left);
                            }
                        }
                        if (i < values.length) {
                            Integer rightVal = values[i++];
                            if (rightVal != null) {
                                node.right = new TreeNode(rightVal);
                                queue.add(node.right);
                            }
                        }
                    }
                    return root;
                }
            """;

    record TopLevelClass(String name, boolean isPublic, int braceOpen, int braceClose) {
    }

    record MethodInfo(String name, boolean isStatic, String returnType, List<String> paramTypes) {
    }

    Optional<String> tryWrap(String sourceCode) {
        try {
            return attemptWrap(sourceCode);
        } catch (RuntimeException e) {
            log.debug("Deterministic wrap attempt did not apply, falling back to LLM wrapping", e);
            return Optional.empty();
        }
    }

    private Optional<String> attemptWrap(String sourceCode) {
        String masked = maskNonCode(sourceCode);
        List<TopLevelClass> classes = findTopLevelClasses(masked);
        if (classes.isEmpty()) {
            return Optional.empty();
        }

        TopLevelClass entryClass = null;
        MethodInfo entryMethod = null;
        for (TopLevelClass tc : classes) {
            if (HELPER_TYPE_NAMES.contains(tc.name()) || tc.name().equals("Main")) {
                continue;
            }
            if (hasParameterizedConstructor(masked, tc)) {
                continue;
            }
            List<MethodInfo> methods = findMemberMethods(masked, tc.name(), tc.braceOpen() + 1, tc.braceClose());
            if (!methods.isEmpty()) {
                entryClass = tc;
                entryMethod = methods.get(0);
                break;
            }
        }
        if (entryClass == null) {
            return Optional.empty();
        }

        List<String> args = new ArrayList<>();
        boolean needsListNode = false;
        boolean needsTreeNode = false;
        for (String rawParamType : entryMethod.paramTypes()) {
            String paramType = rawParamType.replaceAll("\\s+", "");
            Optional<String> arg = sampleArgFor(paramType);
            if (arg.isEmpty()) {
                return Optional.empty();
            }
            args.add(arg.get());
            if (paramType.equals("ListNode")) needsListNode = true;
            if (paramType.equals("TreeNode")) needsTreeNode = true;
        }

        String returnType = entryMethod.returnType().trim();
        String returnTypeNormalized = returnType.replaceAll("\\s+", "");
        if (returnTypeNormalized.equals("ListNode")) needsListNode = true;
        if (returnTypeNormalized.equals("TreeNode")) needsTreeNode = true;

        boolean listNodeAlreadyDefined = classes.stream().anyMatch(c -> c.name().equals("ListNode"));
        boolean treeNodeAlreadyDefined = classes.stream().anyMatch(c -> c.name().equals("TreeNode"));

        StringBuilder src = new StringBuilder();
        src.append(stripPublicModifiers(sourceCode, classes));
        src.append("\n\n");
        if (needsListNode && !listNodeAlreadyDefined) {
            src.append(LIST_NODE_CLASS).append("\n\n");
        }
        if (needsTreeNode && !treeNodeAlreadyDefined) {
            src.append(TREE_NODE_CLASS).append("\n\n");
        }

        String callTarget = entryMethod.isStatic()
                ? entryClass.name() + "." + entryMethod.name()
                : "instance." + entryMethod.name();
        String callExpr = callTarget + "(" + String.join(", ", args) + ")";

        src.append("public class Main {\n");
        if (needsListNode) {
            src.append(LIST_NODE_HELPERS).append("\n");
        }
        if (needsTreeNode) {
            src.append(TREE_NODE_HELPERS).append("\n");
        }
        src.append("    public static void main(String[] args) {\n");
        if (!entryMethod.isStatic()) {
            src.append("        ").append(entryClass.name()).append(" instance = new ")
                    .append(entryClass.name()).append("();\n");
        }
        if (returnTypeNormalized.equals("void")) {
            src.append("        ").append(callExpr).append(";\n");
        } else {
            src.append("        ").append(returnType).append(" result = ").append(callExpr).append(";\n");
            src.append("        ").append(printStatement(returnTypeNormalized, "result")).append("\n");
        }
        src.append("    }\n");
        src.append("}\n");

        return Optional.of(src.toString());
    }

    private Optional<String> sampleArgFor(String type) {
        if (SCALAR_LITERALS.containsKey(type)) {
            return Optional.of(SCALAR_LITERALS.get(type));
        }
        if (type.equals("String")) {
            return Optional.of("\"example\"");
        }
        if (type.equals("ListNode")) {
            return Optional.of("buildList(new int[]{1, 2, 3, 4, 5})");
        }
        if (type.equals("TreeNode")) {
            return Optional.of("buildTree(new Integer[]{5, 3, 8, 1, 4, 7, 9})");
        }
        int dims = 0;
        String base = type;
        while (base.endsWith("[]")) {
            dims++;
            base = base.substring(0, base.length() - 2);
        }
        if (dims == 1 && ARRAY_1D_LITERALS.containsKey(base)) {
            return Optional.of(ARRAY_1D_LITERALS.get(base));
        }
        if (dims == 2 && ARRAY_2D_LITERALS.containsKey(base)) {
            return Optional.of(ARRAY_2D_LITERALS.get(base));
        }
        Matcher listMatcher = Pattern.compile("^(?:java\\.util\\.)?List<(\\w+)>$").matcher(type);
        if (listMatcher.matches()) {
            String elementType = listMatcher.group(1);
            if (BOXED_LIST_ELEMENT_TYPES.contains(elementType)) {
                String elements = ARRAY_1D_LITERALS.containsKey(elementType)
                        ? ARRAY_1D_LITERALS.get(elementType).replaceFirst("^new \\w+\\[\\]", "")
                        : "{\"example\"}";
                return Optional.of("new java.util.ArrayList<>(java.util.Arrays.asList"
                        + elements.replace("{", "(").replace("}", ")") + ")");
            }
        }
        return Optional.empty();
    }

    private String printStatement(String returnTypeNormalized, String varName) {
        int dims = 0;
        String base = returnTypeNormalized;
        while (base.endsWith("[]")) {
            dims++;
            base = base.substring(0, base.length() - 2);
        }
        if (dims == 1) {
            return "System.out.println(java.util.Arrays.toString(" + varName + "));";
        }
        if (dims >= 2) {
            return "System.out.println(java.util.Arrays.deepToString(" + varName + "));";
        }
        if (base.equals("ListNode")) {
            return "System.out.println(listToString(" + varName + "));";
        }
        return "System.out.println(" + varName + ");";
    }

    private boolean hasParameterizedConstructor(String masked, TopLevelClass tc) {
        Pattern ctor = Pattern.compile(
                "\\b" + Pattern.quote(tc.name()) + "\\s*\\(\\s*[^)]\\S*[^)]*\\)\\s*\\{");
        Matcher m = ctor.matcher(masked);
        m.region(tc.braceOpen() + 1, tc.braceClose());
        return m.find();
    }

    private String stripPublicModifiers(String sourceCode, List<TopLevelClass> classes) {
        String result = sourceCode;
        for (TopLevelClass tc : classes) {
            if (tc.isPublic()) {
                result = result.replaceFirst(
                        "public(\\s+)class(\\s+)" + Pattern.quote(tc.name()) + "\\b", "class$2" + tc.name());
            }
        }
        return result;
    }

    private List<TopLevelClass> findTopLevelClasses(String masked) {
        List<TopLevelClass> result = new ArrayList<>();
        int depth = 0;
        int n = masked.length();
        int i = 0;
        while (i < n) {
            char c = masked.charAt(i);
            if (c == '{') {
                depth++;
                i++;
                continue;
            }
            if (c == '}') {
                depth--;
                i++;
                continue;
            }
            if (depth == 0 && matchesKeywordAt(masked, i, "class")) {
                Matcher m = Pattern.compile("class\\s+(\\w+)").matcher(masked);
                m.region(i, n);
                if (m.lookingAt()) {
                    String className = m.group(1);
                    int modifiersStart = findModifiersStart(masked, i);
                    boolean isPublic = Pattern.compile("\\bpublic\\b")
                            .matcher(masked.substring(modifiersStart, i)).find();
                    int braceOpen = masked.indexOf('{', m.end());
                    if (braceOpen < 0) {
                        break;
                    }
                    int braceClose = findMatchingBrace(masked, braceOpen);
                    result.add(new TopLevelClass(className, isPublic, braceOpen, braceClose));
                    i = braceClose + 1;
                    continue;
                }
            }
            i++;
        }
        return result;
    }

    private List<MethodInfo> findMemberMethods(String masked, String className, int bodyStart, int bodyEnd) {
        List<MethodInfo> methods = new ArrayList<>();
        int depth = 0;
        int i = bodyStart;
        while (i < bodyEnd) {
            char c = masked.charAt(i);
            if (c == '{') {
                depth++;
                i++;
                continue;
            }
            if (c == '}') {
                depth--;
                i++;
                continue;
            }
            if (depth == 0) {
                if (matchesKeywordAt(masked, i, "class")
                        || matchesKeywordAt(masked, i, "interface")
                        || matchesKeywordAt(masked, i, "enum")) {
                    int braceOpen = masked.indexOf('{', i);
                    if (braceOpen >= 0 && braceOpen < bodyEnd) {
                        i = findMatchingBrace(masked, braceOpen) + 1;
                        continue;
                    }
                }
                Matcher m = METHOD_PATTERN.matcher(masked);
                m.region(i, bodyEnd);
                if (m.lookingAt()) {
                    String modifiers = m.group(1) == null ? "" : m.group(1);
                    String methodName = m.group(3);
                    String rawParams = m.group(4);
                    int sigEnd = m.end();
                    int braceOpen = masked.indexOf('{', sigEnd);
                    int semi = masked.indexOf(';', sigEnd);
                    if (braceOpen < 0 || (semi >= 0 && semi < braceOpen)) {
                        i = semi >= 0 ? semi + 1 : sigEnd;
                        continue;
                    }
                    int braceClose = findMatchingBrace(masked, braceOpen);
                    if (!methodName.equals(className) && Pattern.compile("\\bpublic\\b").matcher(modifiers).find()) {
                        boolean isStatic = Pattern.compile("\\bstatic\\b").matcher(modifiers).find();
                        List<String> paramTypes = splitParamTypes(rawParams);
                        if (paramTypes != null) {
                            methods.add(new MethodInfo(methodName, isStatic, m.group(2).trim(), paramTypes));
                        }
                    }
                    i = braceClose + 1;
                    continue;
                }
            }
            i++;
        }
        return methods;
    }

    private List<String> splitParamTypes(String rawParams) {
        if (rawParams.isBlank()) {
            return List.of();
        }
        List<String> types = new ArrayList<>();
        for (String segment : splitTopLevel(rawParams, ',')) {
            String[] typeAndName = splitTypeAndName(segment);
            if (typeAndName == null) {
                return null;
            }
            types.add(typeAndName[0]);
        }
        return types;
    }

    private String[] splitTypeAndName(String paramDecl) {
        String s = paramDecl.trim().replaceFirst("^final\\s+", "");
        int depth = 0;
        int lastTopLevelSpace = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<' || c == '(' || c == '[') depth++;
            else if (c == '>' || c == ')' || c == ']') depth--;
            else if (Character.isWhitespace(c) && depth == 0) lastTopLevelSpace = i;
        }
        if (lastTopLevelSpace < 0) {
            return null;
        }
        String type = s.substring(0, lastTopLevelSpace).trim();
        String name = s.substring(lastTopLevelSpace + 1).trim();
        if (type.isEmpty() || name.isEmpty() || type.contains("...")) {
            return null;
        }
        return new String[]{type, name};
    }

    private List<String> splitTopLevel(String s, char delimiter) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<' || c == '(' || c == '[') depth++;
            else if (c == '>' || c == ')' || c == ']') depth--;
            else if (c == delimiter && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    private boolean matchesKeywordAt(String s, int i, String keyword) {
        if (!s.startsWith(keyword, i)) {
            return false;
        }
        if (i > 0 && Character.isJavaIdentifierPart(s.charAt(i - 1))) {
            return false;
        }
        int end = i + keyword.length();
        return end >= s.length() || !Character.isJavaIdentifierPart(s.charAt(end));
    }

    private int findModifiersStart(String masked, int keywordIndex) {
        int i = keywordIndex;
        while (i > 0) {
            int back = i;
            while (back > 0 && Character.isWhitespace(masked.charAt(back - 1))) back--;
            int wordEnd = back;
            while (back > 0 && Character.isJavaIdentifierPart(masked.charAt(back - 1))) back--;
            if (back == wordEnd) {
                break;
            }
            String word = masked.substring(back, wordEnd);
            if (MODIFIER_WORDS.contains(word)) {
                i = back;
            } else {
                break;
            }
        }
        return i;
    }

    private int findMatchingBrace(String masked, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return masked.length() - 1;
    }

    private String maskNonCode(String src) {
        StringBuilder masked = new StringBuilder(src.length());
        int n = src.length();
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    masked.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                masked.append("  ");
                i += 2;
                while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) {
                    masked.append(src.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    masked.append("  ");
                    i += 2;
                }
            } else if (c == '"') {
                masked.append(' ');
                i++;
                while (i < n && src.charAt(i) != '"') {
                    if (src.charAt(i) == '\\' && i + 1 < n) {
                        masked.append("  ");
                        i += 2;
                        continue;
                    }
                    masked.append(' ');
                    i++;
                }
                if (i < n) {
                    masked.append(' ');
                    i++;
                }
            } else if (c == '\'') {
                masked.append(' ');
                i++;
                while (i < n && src.charAt(i) != '\'') {
                    if (src.charAt(i) == '\\' && i + 1 < n) {
                        masked.append("  ");
                        i += 2;
                        continue;
                    }
                    masked.append(' ');
                    i++;
                }
                if (i < n) {
                    masked.append(' ');
                    i++;
                }
            } else {
                masked.append(c == '\n' ? '\n' : c);
                i++;
            }
        }
        return masked.toString();
    }
}
