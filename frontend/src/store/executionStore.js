import { create } from 'zustand'
import { submitExecution } from '../api/executionApi'

function extractErrorMessage(error) {
  return error.response?.data?.error ?? error.message ?? 'Something went wrong'
}

const AUTO_ADVANCE_INTERVAL_MS = 600

let playbackIntervalId = null

export const useExecutionStore = create((set, get) => ({
  isLoading: false,
  error: null,

  trace: null,
  createdAt: null,
  wasWrapped: false,

  currentStepIndex: 0,
  isPlaying: false,

  submit: async (sourceCode, language = 'java') => {
    get().pause()
    set({
      isLoading: true,
      error: null,
      trace: null,
      createdAt: null,
      wasWrapped: false,
      currentStepIndex: 0,
      isPlaying: false,
    })
    try {
      const response = await submitExecution(sourceCode, language)
      set({
        trace: response.trace,
        createdAt: response.createdAt,
        wasWrapped: response.wasWrapped,
        isLoading: false,
        currentStepIndex: 0,
      })
      return response
    } catch (error) {
      set({ error: extractErrorMessage(error), isLoading: false })
      throw error
    }
  },

  goToStep: (index) => {
    const { trace } = get()
    if (!trace) return
    const clamped = Math.max(0, Math.min(index, trace.steps.length - 1))
    set({ currentStepIndex: clamped })
  },

  stepForward: () => {
    const { trace, currentStepIndex } = get()
    if (!trace) return
    if (currentStepIndex >= trace.steps.length - 1) {
      get().pause()
      return
    }
    set({ currentStepIndex: currentStepIndex + 1 })
  },

  stepBackward: () => {
    const { currentStepIndex } = get()
    set({ currentStepIndex: Math.max(0, currentStepIndex - 1) })
  },

  play: () => {
    const { trace, currentStepIndex, isPlaying } = get()
    if (!trace || isPlaying) return
    if (currentStepIndex >= trace.steps.length - 1) return
    set({ isPlaying: true })
    playbackIntervalId = setInterval(() => {
      const { trace: t, currentStepIndex: idx } = get()
      if (!t || idx >= t.steps.length - 1) {
        get().pause()
        return
      }
      set({ currentStepIndex: idx + 1 })
    }, AUTO_ADVANCE_INTERVAL_MS)
  },

  pause: () => {
    if (playbackIntervalId !== null) {
      clearInterval(playbackIntervalId)
      playbackIntervalId = null
    }
    set({ isPlaying: false })
  },

  reset: () => {
    get().pause()
    set({
      trace: null,
      createdAt: null,
      wasWrapped: false,
      currentStepIndex: 0,
      error: null,
    })
  },

  clearError: () => set({ error: null }),
}))

export function selectCurrentStep(state) {
  return state.trace ? state.trace.steps[state.currentStepIndex] : null
}

export function selectCurrentFrame(state) {
  const step = selectCurrentStep(state)
  return step && step.callStack.length > 0 ? step.callStack[0] : null
}

export function selectCurrentLine(state) {
  const frame = selectCurrentFrame(state)
  return frame ? frame.lineNumber : null
}
