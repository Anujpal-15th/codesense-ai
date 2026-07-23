import { create } from 'zustand'
import { getAnalysisById, getHistorySummaries, submitAnalysis } from '../api/analysisApi'
import { extractErrorMessage, StaleRequestError } from '../lib/httpError'

// Bumped by submit()/fetchAnalysisById() and by reset() - mirrors
// executionStore's executionRequestSeq. Without this, a stale submit()
// response landing after Refresh silently repopulates currentAnalysis with
// data the user already cleared.
let analysisRequestSeq = 0

export const useAnalysisStore = create((set) => ({
  currentAnalysis: null,
  history: [],
  isLoading: false,
  error: null,

  submit: async (codeSnippet, language = 'java') => {
    const seq = ++analysisRequestSeq
    set({ isLoading: true, error: null })
    try {
      const analysis = await submitAnalysis(codeSnippet, language)
      if (seq !== analysisRequestSeq) throw new StaleRequestError()
      set({ currentAnalysis: analysis, isLoading: false })
      return analysis
    } catch (error) {
      if (seq === analysisRequestSeq) {
        set({ error: extractErrorMessage(error), isLoading: false })
      }
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
    const seq = ++analysisRequestSeq
    set({ isLoading: true, error: null })
    try {
      const analysis = await getAnalysisById(id)
      if (seq !== analysisRequestSeq) throw new StaleRequestError()
      set({ currentAnalysis: analysis, isLoading: false })
      return analysis
    } catch (error) {
      if (seq === analysisRequestSeq) {
        set({ error: extractErrorMessage(error), isLoading: false })
      }
      throw error
    }
  },

  clearError: () => set({ error: null }),

  // Clears the current workspace analysis (used by the Refresh button). Leaves
  // `history` alone — that's list data, not part of the active workspace.
  reset: () => {
    analysisRequestSeq++ // invalidate any submit()/fetchAnalysisById() still in flight
    set({ currentAnalysis: null, error: null, isLoading: false })
  },
}))
