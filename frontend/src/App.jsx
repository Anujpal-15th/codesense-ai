import { NavLink, Outlet, Route, Routes } from 'react-router-dom'
import AnalysisDetailPage from './pages/AnalysisDetailPage'
import ExecutionHistoryLoaderPage from './pages/ExecutionHistoryLoaderPage'
import HistoryPage from './pages/HistoryPage'
import LandingPage from './pages/LandingPage'
import WorkspacePage from './pages/WorkspacePage'
import ErrorBoundary from './components/ErrorBoundary'
import { navLinkClass } from './lib/navLinkClass'

function AppShell() {
  return (
    <div className="flex h-screen flex-col bg-paper text-ink">
      <nav className="shrink-0 border-b border-line bg-paper-raised px-6 py-3">
        <div className="mx-auto flex max-w-[1800px] items-center justify-between">
          <NavLink to="/" className="flex items-center gap-2 font-mono text-lg font-bold">
            <span className="h-2 w-2 rounded-full bg-approve" />
            codesense <span className="font-normal text-ink-soft">.ai</span>
          </NavLink>
          <div className="flex items-center gap-2">
            <NavLink to="/analyze" className={navLinkClass}>
              Workspace
            </NavLink>
            <NavLink to="/history" className={navLinkClass}>
              History
            </NavLink>
          </div>
        </div>
      </nav>

      <main className="min-h-0 flex-1">
        <Outlet />
      </main>
    </div>
  )
}

function App() {
  return (
    <ErrorBoundary>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        {/* Workspace is a full-viewport LeetCode-style layout with its own top bar. */}
        <Route path="/analyze" element={<WorkspacePage />} />
        <Route element={<AppShell />}>
          <Route path="/history" element={<HistoryPage />} />
          <Route path="/history/:id" element={<AnalysisDetailPage />} />
          <Route path="/history/executions/:id" element={<ExecutionHistoryLoaderPage />} />
        </Route>
      </Routes>
    </ErrorBoundary>
  )
}

export default App
