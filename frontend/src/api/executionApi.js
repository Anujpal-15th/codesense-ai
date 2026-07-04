import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
})

export async function submitExecution(sourceCode) {
  const { data } = await api.post('/executions', { sourceCode })
  return data
}

export default api
