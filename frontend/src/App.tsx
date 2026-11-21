import { lazy, Suspense, useEffect } from 'react'
import { Navigate, Outlet, Route, Routes, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { AppShell } from '@/components/layout/AppShell'
import { PageSpinner } from '@/components/ui/Spinner'
import { landingFor, useAuth } from '@/context/AuthContext'
import { FileActionsProvider } from '@/context/FileActions'
import { UploadProvider } from '@/context/UploadContext'

const LandingPage = lazy(() => import('@/pages/LandingPage'))
const LoginPage = lazy(() => import('@/pages/auth/LoginPage'))
const RegisterPage = lazy(() => import('@/pages/auth/RegisterPage'))
const VerifyEmailPage = lazy(() => import('@/pages/auth/VerifyEmailPage'))
const GoogleAuthPage = lazy(() => import('@/pages/auth/GoogleAuthPage'))
const GoogleConnectedPage = lazy(() => import('@/pages/GoogleConnectedPage'))
const WelcomePage = lazy(() => import('@/pages/WelcomePage'))
const PublicFilePage = lazy(() => import('@/pages/PublicFilePage'))
const LegalPage = lazy(() => import('@/pages/LegalPage'))
const DeleteAccountPage = lazy(() => import('@/pages/DeleteAccountPage'))
const HomePage = lazy(() => import('@/pages/HomePage'))
const FilesPage = lazy(() => import('@/pages/FilesPage'))
const PhotosPage = lazy(() => import('@/pages/PhotosPage'))
const RecentPage = lazy(() => import('@/pages/RecentPage'))
const FoldersPage = lazy(() => import('@/pages/FoldersPage'))
const SharedPage = lazy(() => import('@/pages/SharedPage'))
const DrivesPage = lazy(() => import('@/pages/DrivesPage'))
const FreeUpPage = lazy(() => import('@/pages/FreeUpPage'))
const ActivityPage = lazy(() => import('@/pages/ActivityPage'))
const TrashPage = lazy(() => import('@/pages/TrashPage'))
const ApiKeysPage = lazy(() => import('@/pages/ApiKeysPage'))
const SettingsPage = lazy(() => import('@/pages/SettingsPage'))
const ProfilePage = lazy(() => import('@/pages/ProfilePage'))
const NotificationsPage = lazy(() => import('@/pages/NotificationsPage'))
const AboutPage = lazy(() => import('@/pages/AboutPage'))

/** Signed in with a verified email; otherwise to sign-in (returning here afterwards) or the code screen. */
function RequireAuth() {
  const { signedIn, user } = useAuth()
  const location = useLocation()
  if (!signedIn) return <Navigate to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`} replace />
  if (user?.emailVerified === false) return <Navigate to="/verify-email" replace />
  return (
    <UploadProvider>
      <FileActionsProvider>
        <Outlet />
      </FileActionsProvider>
    </UploadProvider>
  )
}

function SignedOutOnly() {
  const { signedIn, user } = useAuth()
  const [params] = useSearchParams()
  if (signedIn && user) return <Navigate to={landingFor(user, params.get('next') ?? undefined)} replace />
  return <Outlet />
}

function Root() {
  const { signedIn } = useAuth()
  return signedIn ? <Navigate to="/home" replace /> : <LandingPage />
}

/** Old URLs (/all-files?folderId=…) keep working. */
function LegacyFiles() {
  const [params] = useSearchParams()
  const next = new URLSearchParams()
  const folder = params.get('folderId')
  if (folder) next.set('folder', folder)
  const q = params.get('q')
  if (q) next.set('q', q)
  const s = next.toString()
  return <Navigate to={`/files${s ? `?${s}` : ''}`} replace />
}

/** Any API call that answers EMAIL_NOT_VERIFIED sends the user to the code screen. */
function VerifyRedirect() {
  const navigate = useNavigate()
  useEffect(() => {
    const go = () => navigate('/verify-email', { replace: true })
    window.addEventListener('nd:verify-email', go)
    return () => window.removeEventListener('nd:verify-email', go)
  }, [navigate])
  return null
}

export default function App() {
  return (
    <Suspense fallback={<PageSpinner />}>
      <VerifyRedirect />
      <Routes>
        <Route index element={<Root />} />
        <Route element={<SignedOutOnly />}>
          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />
        </Route>
        <Route path="verify-email" element={<VerifyEmailPage />} />
        <Route path="google-auth" element={<GoogleAuthPage />} />
        <Route path="google-connected" element={<GoogleConnectedPage />} />
        <Route path="public/files/:token" element={<PublicFilePage />} />
        <Route path="public/files/:token/embed" element={<PublicFilePage embed />} />
        <Route path="privacy" element={<LegalPage doc="privacy" />} />
        <Route path="terms" element={<LegalPage doc="terms" />} />
        <Route path="delete-account" element={<DeleteAccountPage />} />

        <Route element={<RequireAuth />}>
          <Route path="welcome" element={<WelcomePage />} />
          <Route element={<AppShell />}>
            <Route path="home" element={<HomePage />} />
            <Route path="files" element={<FilesPage />} />
            <Route path="photos" element={<PhotosPage />} />
            <Route path="recent" element={<RecentPage />} />
            <Route path="folders" element={<FoldersPage />} />
            <Route path="shared" element={<SharedPage />} />
            <Route path="drives" element={<DrivesPage />} />
            <Route path="drives/:accountId/free-up" element={<FreeUpPage />} />
            <Route path="activity" element={<ActivityPage />} />
            <Route path="trash" element={<TrashPage />} />
            <Route path="api-keys" element={<ApiKeysPage />} />
            <Route path="settings" element={<SettingsPage />} />
            <Route path="profile" element={<ProfilePage />} />
            <Route path="notifications" element={<NotificationsPage />} />
            <Route path="about" element={<AboutPage />} />
          </Route>
          <Route path="all-files" element={<LegacyFiles />} />
          <Route path="quota" element={<Navigate to="/drives" replace />} />
          <Route path="starred" element={<Navigate to="/files" replace />} />
          <Route path="archived" element={<Navigate to="/files" replace />} />
          <Route path="api" element={<Navigate to="/api-keys" replace />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
