import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '../api/authApi.ts'
import { useAuthStore } from '../store/authStore.ts'
import { t } from '../i18n/index.ts'
import { roleLabel } from '../i18n/labels.ts'
import AttendraBrand from '../components/AttendraBrand.tsx'
import { getApiErrorMessage } from '../utils/apiError.ts'

const AUTH_BG = 'linear-gradient(135deg, #1e1b4b 0%, #312e81 50%, #2d2472 100%)'
const ACCENT_BAR_GRADIENT = 'linear-gradient(90deg, #7c3aed, #a855f7, #c084fc)'
const BUTTON_GRADIENT = 'linear-gradient(135deg, #7c3aed, #a855f7)'
const INPUT_CLS =
  'w-full pl-10 pr-4 py-3 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent text-gray-800 placeholder-gray-400 bg-gray-50 transition-all'
const INPUT_CLS_NO_ICON =
  'w-full px-4 py-3 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent text-gray-800 placeholder-gray-400 bg-gray-50 transition-all'

function AuthBlobs() {
  return (
    <>
      <div
        className="absolute top-0 left-0 w-96 h-96 rounded-full opacity-20 pointer-events-none"
        style={{ background: 'radial-gradient(circle, #a855f7 0%, transparent 70%)', transform: 'translate(-30%, -30%)' }}
      />
      <div
        className="absolute bottom-0 right-0 w-80 h-80 rounded-full opacity-15 pointer-events-none"
        style={{ background: 'radial-gradient(circle, #7c3aed 0%, transparent 70%)', transform: 'translate(30%, 30%)' }}
      />
    </>
  )
}

const ROLE_RANK: Record<string, number> = {
  HEAD_OFFICE_HR: 0,
  OFFICE_HR: 1,
  DEPARTMENT_HR: 2,
  EMPLOYEE: 3,
}

const HR_ROLES = [
  { value: 'HEAD_OFFICE_HR', label: roleLabel('HEAD_OFFICE_HR') },
  { value: 'OFFICE_HR', label: roleLabel('OFFICE_HR') },
  { value: 'DEPARTMENT_HR', label: roleLabel('DEPARTMENT_HR') },
  { value: 'EMPLOYEE', label: roleLabel('EMPLOYEE') },
]

export default function SignupPage() {
  const { user, isAuthenticated } = useAuthStore()
  const availableRoles = useMemo(() => {
    if (!isAuthenticated || !user?.userType) {
      // First bootstrap only — anonymous may create HEAD_OFFICE_HR
      return HR_ROLES.filter((r) => r.value === 'HEAD_OFFICE_HR')
    }
    const callerRank = ROLE_RANK[user.userType] ?? 99
    return HR_ROLES.filter((r) => (ROLE_RANK[r.value] ?? 99) > callerRank)
  }, [isAuthenticated, user?.userType])

  const [username, setUsername] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [role, setRole] = useState(availableRoles[0]?.value ?? 'EMPLOYEE')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const handleSignup = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (!username.trim()) {
      setError(t('signup.usernameRequired'))
      return
    }
    if (password.length < 8) {
      setError(t('signup.passwordMinLength'))
      return
    }
    if (!/\d/.test(password)) {
      setError(t('signup.passwordNeedsDigit'))
      return
    }
    if (password !== passwordConfirm) {
      setError(t('signup.passwordMismatch'))
      return
    }

    setLoading(true)
    try {
      await authApi.signup({ username: username.trim(), firstName, lastName, password, role })
      setSuccess(true)
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 401) {
        setError(t('signup.authRequired'))
      } else if (status === 403) {
        setError(t('signup.forbidden'))
      } else {
        setError(getApiErrorMessage(err, t('signup.error')))
      }
    } finally {
      setLoading(false)
    }
  }

  if (success) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4" style={{ background: AUTH_BG }}>
        <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden">
          <div className="h-1 w-full" style={{ background: ACCENT_BAR_GRADIENT }} />
          <div className="p-8 text-center">
            <AttendraBrand size="auth" showWordmark showTagline className="justify-center mb-6" />
            <h2 className="text-xl font-extrabold mb-2" style={{ color: '#1e1b4b' }}>{t('signup.success')}</h2>
            <p className="text-gray-500 text-sm mb-6">{t('signup.successNote')}</p>
            <Link
              to="/login"
              className="inline-flex items-center gap-2 px-6 py-3 rounded-xl text-white font-semibold text-sm tracking-wide transition-all hover:shadow-lg active:scale-95"
              style={{ background: BUTTON_GRADIENT }}
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 16l-4-4m0 0l4-4m-4 4h14m-5 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h7a3 3 0 013 3v1" />
              </svg>
              {t('login.signIn')}
            </Link>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4" style={{ background: AUTH_BG }}>
      <AuthBlobs />

      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden">
        {/* Top accent bar */}
        <div className="h-1 w-full" style={{ background: ACCENT_BAR_GRADIENT }} />

        <div className="p-8 pt-7">
          {/* Back link */}
          <button
            type="button"
            onClick={() => {
              if (window.history.length > 1) {
                navigate(-1)
              } else {
                navigate(isAuthenticated ? '/employees' : '/login')
              }
            }}
            className="flex items-center gap-1.5 text-xs font-medium mb-6 transition-colors"
            style={{ color: '#7c3aed' }}
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
            {t('signup.back')}
          </button>

          {/* Brand — full logo */}
          <div className="flex flex-col items-center mb-7">
            <AttendraBrand size="hero" showWordmark showTagline className="justify-center" />
          </div>

          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl mb-5 text-sm flex items-center gap-2">
              <svg className="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
              {error}
            </div>
          )}

          <form onSubmit={handleSignup} className="space-y-4">
            {/* Username */}
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">{t('signup.username')}</label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                  </svg>
                </span>
                <input
                  type="text"
                  autoComplete="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className={INPUT_CLS}
                  placeholder="ali.hasanov"
                  required
                />
              </div>
            </div>

            {/* Name */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-1.5">{t('signup.firstName')}</label>
                <input
                  type="text"
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  className={INPUT_CLS_NO_ICON}
                  placeholder="Əli"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-1.5">{t('signup.lastName')}</label>
                <input
                  type="text"
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  className={INPUT_CLS_NO_ICON}
                  placeholder="Həsənov"
                  required
                />
              </div>
            </div>

            {/* Role */}
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">{t('signup.role')}</label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                  </svg>
                </span>
                <select
                  value={role}
                  onChange={(e) => setRole(e.target.value)}
                  className={INPUT_CLS}
                >
                  {availableRoles.map((r) => (
                    <option key={r.value} value={r.value}>{r.label}</option>
                  ))}
                </select>
              </div>
            </div>

            {/* Password */}
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">{t('signup.password')}</label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                  </svg>
                </span>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className={INPUT_CLS}
                  placeholder="••••••••"
                  required
                />
              </div>
              <p className="text-xs text-gray-400 mt-1">{t('signup.passwordHint')}</p>
            </div>

            {/* Confirm Password */}
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">{t('signup.passwordConfirm')}</label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                  </svg>
                </span>
                <input
                  type="password"
                  value={passwordConfirm}
                  onChange={(e) => setPasswordConfirm(e.target.value)}
                  className={INPUT_CLS}
                  placeholder="••••••••"
                  required
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full text-white py-3 px-4 rounded-xl font-semibold text-sm tracking-wide transition-all disabled:opacity-50 disabled:cursor-not-allowed hover:shadow-lg active:scale-95 mt-2"
              style={{ background: BUTTON_GRADIENT }}
            >
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  {t('signup.signingUp')}
                </span>
              ) : (
                t('signup.signUp')
              )}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
