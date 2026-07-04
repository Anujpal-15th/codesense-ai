import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
})

export async function submitAnalysis(codeSnippet) {
  const { data } = await api.post('/analyses', { codeSnippet })
  return data
}

export async function getHistory() {
  const { data } = await api.get('/analyses')
  return data
}

export async function getAnalysisById(id) {
  const { data } = await api.get(`/analyses/${id}`)
  return data
}

export default api
