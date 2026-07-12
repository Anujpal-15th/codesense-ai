import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
})

export async function submitExecution(sourceCode, language = 'java') {
  const { data } = await api.post('/executions', { sourceCode, language })
  return data
}

export default api
