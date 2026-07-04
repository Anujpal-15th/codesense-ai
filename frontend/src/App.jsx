import { NavLink, Outlet, Route, Routes } from 'react-router-dom'
import AnalysisDetailPage from './pages/AnalysisDetailPage'
import HistoryPage from './pages/HistoryPage'
import LandingPage from './pages/LandingPage'
import WorkspacePage from './pages/WorkspacePage'

const navLinkClass = ({ isActive }) =>
  `font-mono text-sm font-medium px-3 py-2 ${isActive ? 'text-ink' : 'text-ink-soft hover:text-ink'}`

function AppShell() {
  return (
    <div className="min-h-screen bg-paper text-ink">
      <nav className="border-b border-line px-6 py-4">
        <div className="mx-auto flex max-w-6xl items-center gap-6">
          <NavLink to="/" className="flex items-center gap-2 font-mono text-lg font-bold">
            <span className="h-2 w-2 rounded-full bg-approve" />
            codesense <span className="font-normal text-ink-soft">.ai</span>
          </NavLink>
          <NavLink to="/analyze" className={navLinkClass}>
            Workspace
          </NavLink>
          <NavLink to="/history" className={navLinkClass}>
            History
          </NavLink>
        </div>
      </nav>

      <Outlet />
    </div>
  )
}

function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route element={<AppShell />}>
        <Route path="/analyze" element={<WorkspacePage />} />
        <Route path="/history" element={<HistoryPage />} />
        <Route path="/history/:id" element={<AnalysisDetailPage />} />
      </Route>
    </Routes>
  )
}

export default App
