import { create } from 'zustand'

export const DEFAULT_CODE = `public class Main {\n    public static void main(String[] args) {\n        \n    }\n}\n`

export const DEFAULT_PYTHON_CODE = `def main():\n    pass\n\n\nmain()\n`

const DEFAULTS = { java: DEFAULT_CODE, python: DEFAULT_PYTHON_CODE }

// One-time cleanup: this store used to persist via zustand/middleware's
// `persist` under this key. Now that it's in-memory only, a leftover key from
// a pre-upgrade visit would just sit inert - remove it so it doesn't linger.
window.localStorage?.removeItem('codesense-workspace')

// In-memory only (no persist middleware): the editor's code + view state live
// for the lifetime of the JS module. That means it survives client-side
// navigation (Workspace -> History -> back, which never reloads the module) but
// resets to DEFAULT_CODE on a full page refresh or in a new tab — deliberately,
// so nobody reopens the workspace to someone else's (or their own stale) code.
export const useWorkspaceStore = create((set, get) => ({
  code: DEFAULT_CODE,
  language: 'java',
  activeTab: 'analysis',
  executedSource: null,

  setCode: (code) => set({ code }),

  // Switching languages swaps in the new language's skeleton ONLY when the
  // editor still holds an untouched default - never over the user's own code
  // (they may be mid-translation between languages).
  setLanguage: (language) => {
    const { code, language: prev } = get()
    if (language === prev) return
    const untouched = code.trim() === (DEFAULTS[prev] ?? '').trim() || code.trim() === ''
    set({ language, ...(untouched ? { code: DEFAULTS[language] } : {}) })
  },

  setActiveTab: (activeTab) => set({ activeTab }),
  setExecutedSource: (executedSource) => set({ executedSource }),

  // Full reset back to a blank workspace (used by the Refresh button).
  // Keeps the selected language, resets the code to that language's skeleton.
  resetWorkspace: () =>
    set((state) => ({
      code: DEFAULTS[state.language] ?? DEFAULT_CODE,
      activeTab: 'analysis',
      executedSource: null,
    })),
}))
