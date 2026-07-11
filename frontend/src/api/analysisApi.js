import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
})

export async function submitAnalysis(codeSnippet) {
  const { data } = await api.post('/analyses', { codeSnippet })
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
