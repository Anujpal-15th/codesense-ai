import axios from 'axios'
import { getUserId } from '../lib/userId'
import { API_BASE_URL } from '../lib/apiBase'

const api = axios.create({
  baseURL: API_BASE_URL,
})

// Attaches X-User-Id to every request through this instance - one place, so
// no call site can forget it (the backend scopes history by this header;
// omitting it means "no history" rather than an error, but should never
// happen from this client).
api.interceptors.request.use(async (config) => {
  config.headers['X-User-Id'] = await getUserId()
  return config
})

export async function submitAnalysis(codeSnippet, language = 'java') {
  const { data } = await api.post('/analyses', { codeSnippet, language })
  return data
}

export async function getHistory() {
  const { data } = await api.get('/analyses')
  return data
}

// Lightweight list for the History page — only id/pattern/complexity/optimal/
// date + a truncated code preview and explanation excerpt. Full records are
// fetched per-row via getAnalysisById when a card is opened.
export async function getHistorySummaries() {
  const { data } = await api.get('/analyses/summary')
  return data
}

export async function getAnalysisById(id) {
  const { data } = await api.get(`/analyses/${id}`)
  return data
}

export default api
