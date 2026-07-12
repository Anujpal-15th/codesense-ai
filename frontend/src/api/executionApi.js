import axios from 'axios'
import { getUserId } from '../lib/userId'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
})

// See analysisApi.js - same reasoning. Executions aren't persisted/scoped
// today, but the header travels alongside every request so it's already
// there once execution history exists.
api.interceptors.request.use((config) => {
  config.headers['X-User-Id'] = getUserId()
  return config
})

export async function submitExecution(sourceCode, language = 'java') {
  const { data } = await api.post('/executions', { sourceCode, language })
  return data
}

export default api
