const LINKED_LIST_FIELD_NAMES = new Set(['next', 'prev', 'previous'])
const TREE_FIELD_NAMES = new Set(['left', 'right', 'children', 'child'])
const KNOWN_JAVA_COLLECTION_TYPES = new Set([
  'java.util.HashMap',
  'java.util.ArrayList',
  'java.util.LinkedList',
  'java.util.HashSet',
  'java.util.TreeMap',
  'java.util.TreeSet',
  'java.util.LinkedHashMap',
  'java.util.LinkedHashSet',
  'java.util.ArrayDeque',
  'java.util.Vector',
  'java.util.Stack',
  'java.util.PriorityQueue',
])

export function isJavaCollection(objectSummary) {
  const type = objectSummary.type ?? ''
  return type.startsWith('java.util.') || KNOWN_JAVA_COLLECTION_TYPES.has(type)
}

function fieldNames(objectSummary) {
  return new Set(objectSummary.fields.map((f) => f.name))
}

export function isLinkedListNode(objectSummary) {
  if (isJavaCollection(objectSummary)) return false
  const names = fieldNames(objectSummary)
  const hasNextLike = [...names].some((n) => LINKED_LIST_FIELD_NAMES.has(n))
  const hasTreeLike = [...names].some((n) => TREE_FIELD_NAMES.has(n))
  return hasNextLike && !hasTreeLike
}

export function isTreeNode(objectSummary) {
  if (isJavaCollection(objectSummary)) return false
  const names = fieldNames(objectSummary)
  return [...names].some((n) => TREE_FIELD_NAMES.has(n))
}

export function findField(objectSummary, names) {
  return objectSummary.fields.find((f) => names.includes(f.name))
}

const BOXED_PRIMITIVE_TYPES = new Set([
  'java.lang.Integer',
  'java.lang.Long',
  'java.lang.Short',
  'java.lang.Byte',
  'java.lang.Double',
  'java.lang.Float',
  'java.lang.Boolean',
  'java.lang.Character',
])

/**
 * If `objectSummary` is a boxed primitive wrapper (Integer, Long, ...), returns
 * its inner primitive value; otherwise null. Lets the renderer show `3` instead
 * of a collapsed `java.lang.Integer` object — cleaner, and flashable as a leaf.
 */
export function boxedInnerValue(objectSummary) {
  if (objectSummary.valueKind !== 'object' || !BOXED_PRIMITIVE_TYPES.has(objectSummary.type)) return null
  const field = objectSummary.fields?.find((f) => f.name === 'value')
  return field ? field.value : null
}
