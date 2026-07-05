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

    /**
     * Shared per-type sample element sequences - the single source List, Set,
     * Map, and custom-class-constructor synthesis all draw from, so sample
     * data stays consistent across every collection shape. Keys are distinct
     * within each list (required for Set/Map key synthesis).
     */
    private static final Map<String, List<String>> ELEMENT_SAMPLES = Map.of(
            "Integer", List.of("4", "2", "7", "1", "9"),
            "Long", List.of("4L", "2L", "7L", "1L", "9L"),
            "Double", List.of("4.0", "2.0", "7.0", "1.0", "9.0"),
            "Float", List.of("4.0f", "2.0f", "7.0f"),
            "Boolean", List.of("true", "false"),
            "Character", List.of("'a'", "'b'", "'c'"),
            "String", List.of("\"alpha\"", "\"beta\"", "\"gamma\""));

    /** Widening-safe mapping from primitive parameter types to the boxed sample table. */
    private static final Map<String, String> PRIMITIVE_TO_SAMPLE_KEY = Map.of(
            "int", "Integer", "byte", "Integer", "short", "Integer",
            "long", "Long", "double", "Double", "float", "Float",
            "boolean", "Boolean", "char", "Character");

    private static final Pattern LIST_TYPE = Pattern.compile("^(?:java\\.util\\.)?(?:Array|Linked)?List<(\\w+)>$");
    // LinkedHashSet/LinkedHashMap are assignable to Set/HashSet and Map/HashMap
    // respectively, but NOT to TreeSet/TreeMap/Sorted* - those fall back to the LLM.
    private static final Pattern SET_TYPE = Pattern.compile("^(?:java\\.util\\.)?(?:Hash|LinkedHash)?Set<(\\w+)>$");
    private static final Pattern MAP_TYPE = Pattern.compile("^(?:java\\.util\\.)?(?:Hash|LinkedHash)?Map<(\\w+),(\\w+)>$");

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
        String masked = JavaSourceScanner.maskNonCode(sourceCode);
        List<JavaSourceScanner.TopLevelClass> classes = JavaSourceScanner.findTopLevelClasses(masked);
        if (classes.isEmpty()) {
            return Optional.empty();
        }

        JavaSourceScanner.TopLevelClass entryClass = null;
        MethodInfo entryMethod = null;
        for (JavaSourceScanner.TopLevelClass tc : classes) {
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

        Map<String, String> customClassSamples = buildCustomClassSamples(masked, classes);

        List<String> args = new ArrayList<>();
        boolean needsListNode = false;
        boolean needsTreeNode = false;
        for (String rawParamType : entryMethod.paramTypes()) {
            String paramType = rawParamType.replaceAll("\\s+", "");
            Optional<String> arg = sampleArgFor(paramType, customClassSamples);
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

    private Optional<String> sampleArgFor(String type, Map<String, String> customClassSamples) {
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

        Matcher listMatcher = LIST_TYPE.matcher(type);
        if (listMatcher.matches() && ELEMENT_SAMPLES.containsKey(listMatcher.group(1))) {
            return Optional.of("new java.util.ArrayList<>(java.util.Arrays.asList("
                    + String.join(", ", ELEMENT_SAMPLES.get(listMatcher.group(1))) + "))");
        }
        Matcher setMatcher = SET_TYPE.matcher(type);
        if (setMatcher.matches() && ELEMENT_SAMPLES.containsKey(setMatcher.group(1))) {
            return Optional.of("new java.util.LinkedHashSet<>(java.util.Arrays.asList("
                    + String.join(", ", ELEMENT_SAMPLES.get(setMatcher.group(1))) + "))");
        }
        Matcher mapMatcher = MAP_TYPE.matcher(type);
        if (mapMatcher.matches()
                && ELEMENT_SAMPLES.containsKey(mapMatcher.group(1))
                && ELEMENT_SAMPLES.containsKey(mapMatcher.group(2))) {
            return Optional.of(mapLiteral(mapMatcher.group(1), mapMatcher.group(2)));
        }

        // Simple custom class declared in the snippet (single non-private
        // constructor, all-scalar/String params) - precomputed instantiation.
        if (customClassSamples.containsKey(type)) {
            return Optional.of(customClassSamples.get(type));
        }
        return Optional.empty();
    }

    /**
     * Single-expression, mutable, deterministic-iteration-order map literal:
     * a LinkedHashMap built via an instance-initializer subclass. Chosen over
     * {@code Map.of(...)} (immutable - user code calling put() on the param
     * would throw) and over a plain LinkedHashMap copy of Map.of (Map.of has
     * unspecified iteration order, which would make traces vary run to run).
     */
    private String mapLiteral(String keyType, String valueType) {
        List<String> keys = ELEMENT_SAMPLES.get(keyType);
        List<String> values = ELEMENT_SAMPLES.get(valueType);
        int entries = Math.min(3, Math.min(keys.size(), values.size()));
        StringBuilder sb = new StringBuilder("new java.util.LinkedHashMap<")
                .append(keyType).append(", ").append(valueType).append(">() {{ ");
        for (int i = 0; i < entries; i++) {
            sb.append("put(").append(keys.get(i)).append(", ").append(values.get(i)).append("); ");
        }
        return sb.append("}}").toString();
    }

    /**
     * For each top-level class in the snippet with exactly one non-private
     * constructor whose parameters are all primitives/boxed/String, builds a
     * ready-to-inline instantiation like {@code new Interval(4, 2)} - sample
     * values vary by parameter position so e.g. a 2-int constructor doesn't
     * get two identical arguments. Classes with zero constructors (fields
     * would stay default-initialized), multiple constructors (ambiguous), or
     * non-scalar constructor params are deliberately excluded - those fall
     * back to the LLM path unchanged. Stateful "design" classes needing
     * sequential method calls (MinStack, LRUCache) are explicitly out of
     * scope here - flagged as a separate future task.
     */
    private Map<String, String> buildCustomClassSamples(String masked, List<JavaSourceScanner.TopLevelClass> classes) {
        Map<String, String> samples = new java.util.HashMap<>();
        for (JavaSourceScanner.TopLevelClass tc : classes) {
            if (tc.name().equals("Main")) {
                continue;
            }
            List<String> ctorParamTypes = singleConstructorParamTypes(masked, tc);
            if (ctorParamTypes == null || ctorParamTypes.isEmpty()) {
                continue;
            }
            List<String> ctorArgs = new ArrayList<>();
            boolean allSupported = true;
            for (int position = 0; position < ctorParamTypes.size(); position++) {
                String arg = positionalScalarSample(ctorParamTypes.get(position), position);
                if (arg == null) {
                    allSupported = false;
                    break;
                }
                ctorArgs.add(arg);
            }
            if (allSupported) {
                samples.put(tc.name(), "new " + tc.name() + "(" + String.join(", ", ctorArgs) + ")");
            }
        }
        return samples;
    }

    /**
     * @return the single non-private constructor's parameter types, or null
     * if the class has zero constructors, more than one, or a private one.
     */
    private List<String> singleConstructorParamTypes(String masked, JavaSourceScanner.TopLevelClass tc) {
        Pattern ctorPattern = Pattern.compile(
                "((?:(?:public|private|protected)\\s+)?)" + Pattern.quote(tc.name())
                        + "\\s*\\(([^)]*)\\)\\s*(?:throws\\s+[\\w.,\\s]+)?\\s*\\{");
        List<String> found = null;
        int count = 0;
        int depth = 0;
        int i = tc.braceOpen() + 1;
        int bodyEnd = tc.braceClose();
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
                Matcher m = ctorPattern.matcher(masked);
                m.region(i, bodyEnd);
                if (m.lookingAt()) {
                    count++;
                    if (m.group(1).contains("private")) {
                        return null;
                    }
                    List<String> paramTypes = splitParamTypes(m.group(2).trim());
                    if (paramTypes == null) {
                        return null;
                    }
                    found = paramTypes;
                    int braceOpen = masked.indexOf('{', m.end() - 1);
                    i = JavaSourceScanner.findMatchingBrace(masked, braceOpen) + 1;
                    continue;
                }
            }
            i++;
        }
        return count == 1 ? found : null;
    }

    private String positionalScalarSample(String rawType, int position) {
        String type = rawType.replaceAll("\\s+", "");
        String sampleKey = PRIMITIVE_TO_SAMPLE_KEY.getOrDefault(type, type);
        List<String> samples = ELEMENT_SAMPLES.get(sampleKey);
        if (samples == null) {
            return null;
        }
        return samples.get(position % samples.size());
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

    private boolean hasParameterizedConstructor(String masked, JavaSourceScanner.TopLevelClass tc) {
        Pattern ctor = Pattern.compile(
                "\\b" + Pattern.quote(tc.name()) + "\\s*\\(\\s*[^)]\\S*[^)]*\\)\\s*\\{");
        Matcher m = ctor.matcher(masked);
        m.region(tc.braceOpen() + 1, tc.braceClose());
        return m.find();
    }

    private String stripPublicModifiers(String sourceCode, List<JavaSourceScanner.TopLevelClass> classes) {
        String result = sourceCode;
        for (JavaSourceScanner.TopLevelClass tc : classes) {
            if (tc.isPublic()) {
                result = result.replaceFirst(
                        "public(\\s+)class(\\s+)" + Pattern.quote(tc.name()) + "\\b", "class$2" + tc.name());
            }
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
                if (JavaSourceScanner.matchesKeywordAt(masked, i, "class")
                        || JavaSourceScanner.matchesKeywordAt(masked, i, "interface")
                        || JavaSourceScanner.matchesKeywordAt(masked, i, "enum")) {
                    int braceOpen = masked.indexOf('{', i);
                    if (braceOpen >= 0 && braceOpen < bodyEnd) {
                        i = JavaSourceScanner.findMatchingBrace(masked, braceOpen) + 1;
                        continue;
                    }
                }
                Matcher m = METHOD_PATTERN.matcher(masked);
                m.region(i, bodyEnd);
                if (m.lookingAt()) {
                    String modifiers = m.group(1) == null ? "" : m.group(1);
                    String methodName = m.group(3);
                    String returnType = m.group(2).trim();
                    String rawParams = m.group(4);
                    int sigEnd = m.end();
                    int braceOpen = masked.indexOf('{', sigEnd);
                    int semi = masked.indexOf(';', sigEnd);
                    if (braceOpen < 0 || (semi >= 0 && semi < braceOpen)) {
                        i = semi >= 0 ? semi + 1 : sigEnd;
                        continue;
                    }
                    int braceClose = JavaSourceScanner.findMatchingBrace(masked, braceOpen);
                    if (!methodName.equals(className) && Pattern.compile("\\bpublic\\b").matcher(modifiers).find()) {
                        boolean isStatic = Pattern.compile("\\bstatic\\b").matcher(modifiers).find();
                        List<String> paramTypes = splitParamTypes(rawParams);
                        if (paramTypes != null && !looksLikeMainMethod(isStatic, methodName, returnType, paramTypes)) {
                            methods.add(new MethodInfo(methodName, isStatic, returnType, paramTypes));
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

    /**
     * Defensive guard: a class's own {@code public static void main(String[])}
     * should never be treated as a "regular method" to synthesize a call for -
     * that produced the confirmed alpha/beta/gamma bug (a real main() got
     * called with a synthesized {@code String[]} argument). {@link
     * EntryPointDetector} is now responsible for recognizing a real entry
     * point before this class ever runs, so this should never actually fire in
     * practice - kept as a second line of defense in case that invariant is
     * ever broken by a future change.
     */
    private boolean looksLikeMainMethod(boolean isStatic, String methodName, String returnType, List<String> paramTypes) {
        return isStatic
                && methodName.equals("main")
                && returnType.replaceAll("\\s+", "").equals("void")
                && paramTypes.size() == 1
                && paramTypes.get(0).replaceAll("\\s+", "").equals("String[]");
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
}
