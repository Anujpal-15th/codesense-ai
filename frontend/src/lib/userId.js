const STORAGE_KEY = 'codesense-user-id'

function generateId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  // Fallback for contexts without crypto.randomUUID (very old browsers, or
  // non-secure-context iframes where the API is unavailable) - doesn't need
  // to be cryptographically strong, just unique enough to not collide.
  return `user-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
}

// Session-based identity with no login: a random id generated once per
// browser and persisted in localStorage, sent as the X-User-Id header on
// every request so the backend can scope history to "whoever submitted it."
// Deliberately module-level (computed once, not inside the function) so every
// caller in this tab session gets the same id even if localStorage access
// races - the value is read/written exactly once per page load.
let cachedId = null

export function getUserId() {
  if (cachedId) return cachedId
  try {
    const existing = window.localStorage?.getItem(STORAGE_KEY)
    if (existing) {
      cachedId = existing
      return cachedId
    }
    const generated = generateId()
    window.localStorage?.setItem(STORAGE_KEY, generated)
    cachedId = generated
    return cachedId
  } catch {
    // localStorage unavailable (privacy mode, disabled storage) - fall back to
    // an in-memory id for this page load so requests still carry *a* header,
    // just one that won't persist across reloads.
    cachedId = generateId()
    return cachedId
  }
}
