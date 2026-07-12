import { create } from 'zustand'
import { getAnalysisById, getHistorySummaries, submitAnalysis } from '../api/analysisApi'

function extractErrorMessage(error) {
  return error.response?.data?.error ?? error.message ?? 'Something went wrong'
}

export const useAnalysisStore = create((set) => ({
  currentAnalysis: null,
  history: [],
  isLoading: false,
  error: null,

  submit: async (codeSnippet, language = 'java') => {
    set({ isLoading: true, error: null })
    try {
      const analysis = await submitAnalysis(codeSnippet, language)
      set({ currentAnalysis: analysis, isLoading: false })
      return analysis
    } catch (error) {
      set({ error: extractErrorMessage(error), isLoading: false })
      throw error
    }
  },

  fetchHistory: async () => {
    set({ isLoading: true, error: null })
    try {
      const history = await getHistorySummaries()
      set({ history, isLoading: false })
    } catch (error) {
      set({ error: extractErrorMessage(error), isLoading: false })
    }
  },

  fetchAnalysisById: async (id) => {
    set({ isLoading: true, error: null })
    try {
      const analysis = await getAnalysisById(id)
      set({ currentAnalysis: analysis, isLoading: false })
      return analysis
    } catch (error) {
      set({ error: extractErrorMessage(error), isLoading: false })
      throw error
    }
  },

  clearError: () => set({ error: null }),

  // Clears the current workspace analysis (used by the Refresh button). Leaves
  // `history` alone — that's list data, not part of the active workspace.
  reset: () => set({ currentAnalysis: null, error: null, isLoading: false }),
}))
