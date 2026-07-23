import { API_BASE_URL } from './apiBase'

const STORAGE_KEY = 'codesense-user-id'

// Session-based identity with no login: the backend mints a signed id
// (see UserIdentityService/IdentityController on the server) the first time
// this tab needs one, this caches it in localStorage, and every request
// after that sends it back as the X-User-Id header so the backend can scope
// history to "whoever submitted it."
//
// Previously this file generated the id itself (crypto.randomUUID()) and the
// backend trusted whatever string arrived verbatim - meaning any string
// typed into the header worked exactly as well as a real id. Now the id is
// always something the server actually issued and signed; an old, pre-this-
// change value in localStorage (no signature - detected by the missing ".")
// is treated as stale and replaced.
let cachedId = null
let pendingFetch = null

function readCached() {
  try {
    const existing = window.localStorage?.getItem(STORAGE_KEY)
    // A signed id always contains "<uuid>.<signature>" - a bare UUID from
    // before this change has no ".", so it's recognized as stale rather than
    // sent to a backend that will just reject it anyway.
    return existing && existing.includes('.') ? existing : null
  } catch {
    return null
  }
}

function writeCache(id) {
  try {
    window.localStorage?.setItem(STORAGE_KEY, id)
  } catch {
    // localStorage unavailable (privacy mode, disabled storage) - the id
    // still works for this page load via `cachedId`, it just won't persist.
  }
}

async function issueUserId() {
  try {
    const res = await fetch(`${API_BASE_URL}/identity`)
    if (!res.ok) throw new Error(`identity request failed: ${res.status}`)
    const data = await res.json()
    writeCache(data.userId)
    return data.userId
  } catch {
    // Backend unreachable at startup (offline, cold start). Falls back to an
    // unsigned local id so requests still carry *something* rather than
    // throwing - the backend's UserIdentityFilter treats an unsigned/invalid
    // id exactly like no id at all (anonymous), so this just means "no
    // history persisted this session" instead of a broken UI.
    return `unsigned-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
  }
}

export async function getUserId() {
  if (cachedId) return cachedId

  const existing = readCached()
  if (existing) {
    cachedId = existing
    return cachedId
  }

  // Concurrent callers (both api instances' interceptors can fire around the
  // same time on first load) share one in-flight fetch instead of each
  // minting/requesting their own id.
  if (!pendingFetch) {
    pendingFetch = issueUserId().finally(() => {
      pendingFetch = null
    })
  }
  cachedId = await pendingFetch
  return cachedId
}
