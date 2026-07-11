import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export const DEFAULT_CODE = `public class Main {\n    public static void main(String[] args) {\n        \n    }\n}\n`

// Holds the workspace's own view state so it survives client-side navigation
// (Workspace -> History -> back) and even a full reload. `code` and `activeTab`
// are persisted to localStorage; `executedSource` is kept in-memory only (it's
// tied to the execution trace, which lives in the in-memory execution store —
// persisting it without the trace would desync the read-only editor swap).
export const useWorkspaceStore = create(
  persist(
    (set) => ({
      code: DEFAULT_CODE,
      activeTab: 'analysis',
      executedSource: null,

      setCode: (code) => set({ code }),
      setActiveTab: (activeTab) => set({ activeTab }),
      setExecutedSource: (executedSource) => set({ executedSource }),

      // Full reset back to a blank workspace (used by the Refresh button).
      resetWorkspace: () => set({ code: DEFAULT_CODE, activeTab: 'analysis', executedSource: null }),
    }),
    {
      name: 'codesense-workspace',
      partialize: (state) => ({ code: state.code, activeTab: state.activeTab }),
    },
  ),
)
