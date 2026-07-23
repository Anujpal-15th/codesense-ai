// Shared by executionApi.js, analysisApi.js, and userId.js (which needs its
// own fetch() to /api/identity outside the axios instances, to avoid those
// instances' request interceptor recursively calling back into getUserId()).
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
