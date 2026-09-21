import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { lazy, Suspense } from 'react'
import { useAuthStore } from './store/authStore.ts'
import { t } from './i18n/index.ts'
import AttendraBrand from './components/AttendraBrand.tsx'

const LoginPage = lazy(() => import('./pages/LoginPage.tsx'))
const InitialSetupPage = lazy(() => import('./pages/InitialSetupPage.tsx'))
const SignupPage = lazy(() => import('./pages/SignupPage.tsx'))
const DashboardPage = lazy(() => import('./pages/DashboardPage.tsx'))
const EmployeesPage = lazy(() => import('./pages/EmployeesPage.tsx'))
const BranchesPage = lazy(() => import('./pages/BranchesPage.tsx'))
const DepartmentsPage = lazy(() => import('./pages/DepartmentsPage.tsx'))
const PositionsPage = lazy(() => import('./pages/PositionsPage.tsx'))
const AttendancePage = lazy(() => import('./pages/AttendancePage.tsx'))
const ReportsPage = lazy(() => import('./pages/ReportsPage.tsx'))
const TabelPage = lazy(() => import('./pages/TabelPage.tsx'))
const WorkSchedulePage = lazy(() => import('./pages/WorkSchedulePage.tsx'))
const DevicesPage = lazy(() => import('./pages/DevicesPage.tsx'))
const AccessLogsPage = lazy(() => import('./pages/AccessLogsPage.tsx'))
const DeviceLogSearchPage = lazy(() => import('./pages/DeviceLogSearchPage.tsx'))
const DeviceEmployeeImportPage = lazy(() => import('./pages/DeviceEmployeeImportPage.tsx'))
const SettingsPage = lazy(() => import('./pages/SettingsPage.tsx'))

const HR_ROLES = ['HEAD_OFFICE_HR', 'OFFICE_HR', 'DEPARTMENT_HR']

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuthStore()
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />
}

function HrRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, user } = useAuthStore()
  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (!user || !HR_ROLES.includes(user.userType)) return <Navigate to="/" replace />
  return <>{children}</>
}

function HeadOfficeHrRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, user } = useAuthStore()
  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (!user || user.userType !== 'HEAD_OFFICE_HR') return <Navigate to="/" replace />
  return <>{children}</>
}

function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-gray-50">
      {children}
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <Suspense
        fallback={
          <div className="flex flex-col items-center justify-center gap-4 h-screen bg-white">
            <AttendraBrand size="loading" showWordmark showTagline className="justify-center" />
            <p className="text-gray-500 text-sm">{t('app.loading')}</p>
          </div>
        }
      >
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/initial-setup" element={<InitialSetupPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/register" element={<SignupPage />} />
          <Route path="/" element={<ProtectedRoute><AppLayout><DashboardPage /></AppLayout></ProtectedRoute>} />
          <Route path="/employees" element={<HrRoute><AppLayout><EmployeesPage /></AppLayout></HrRoute>} />
          <Route path="/branches" element={<HrRoute><AppLayout><BranchesPage /></AppLayout></HrRoute>} />
          <Route path="/departments" element={<HrRoute><AppLayout><DepartmentsPage /></AppLayout></HrRoute>} />
          <Route path="/positions" element={<HrRoute><AppLayout><PositionsPage /></AppLayout></HrRoute>} />
          <Route path="/attendance" element={<HrRoute><AppLayout><AttendancePage /></AppLayout></HrRoute>} />
          <Route path="/reports" element={<HrRoute><AppLayout><ReportsPage /></AppLayout></HrRoute>} />
          <Route path="/tabel" element={<HrRoute><AppLayout><TabelPage /></AppLayout></HrRoute>} />
          <Route path="/work-schedule" element={<HrRoute><AppLayout><WorkSchedulePage /></AppLayout></HrRoute>} />
          <Route path="/devices" element={<HrRoute><AppLayout><DevicesPage /></AppLayout></HrRoute>} />
          <Route path="/access-logs" element={<HrRoute><AppLayout><AccessLogsPage /></AppLayout></HrRoute>} />
          <Route path="/device-log-search" element={<HrRoute><AppLayout><DeviceLogSearchPage /></AppLayout></HrRoute>} />
          <Route path="/setup/import-employees" element={<HeadOfficeHrRoute><AppLayout><DeviceEmployeeImportPage /></AppLayout></HeadOfficeHrRoute>} />
          <Route path="/settings" element={<ProtectedRoute><AppLayout><SettingsPage /></AppLayout></ProtectedRoute>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  )
}
