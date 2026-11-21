import type { Request, Router } from 'express'
import { z } from 'zod'
import { prisma } from '../../config/prisma.js'
import { requireAuth, type AuthRequest } from '../../middleware/auth.middleware.js'
import { hashPassword } from '../../utils/password.js'
import { decryptText, encryptText, randomToken } from '../../utils/crypto.js'
import { startGoogleDriveImport, syncGoogleQuota } from '../google/google.service.js'

/**
 * Native Google sign-in for the Android app. The app shows the phone's own Google account picker
 * (Credential Manager) and consent sheet (AuthorizationClient), which hand it a one-time server
 * auth code instead of sending the user to a web page. The code is exchanged here with the same
 * web OAuth client the browser flow uses, so the resulting Drive tokens are identical.
 */
type Session = { accessToken: string; refreshToken: string }
type CreateSession = (userId: string, req: AuthRequest) => Promise<Session>

const codeSchema = z.object({ code: z.string().min(10).max(2048) })
const DRIVE_SCOPE = 'https://www.googleapis.com/auth/drive'

async function activeConfig() {
  return prisma.providerConfig.findFirstOrThrow({ where: { userId: null, provider: 'google_drive', status: 'active' }, orderBy: { createdAt: 'desc' } })
}

export class NativeGoogleError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message) }
}

/** Server auth code → tokens + profile. Android codes are exchanged with an empty redirect_uri. */
export async function exchange(code: string) {
  const config = await activeConfig()
  const form = new URLSearchParams({
    code,
    client_id: decryptText(config.clientIdEncrypted),
    client_secret: decryptText(config.clientSecretEncrypted),
    redirect_uri: '',
    grant_type: 'authorization_code',
  })
  const tokenRes = await fetch('https://oauth2.googleapis.com/token', { method: 'POST', body: form })
  const tokens = (await tokenRes.json()) as { access_token?: string; refresh_token?: string; expires_in?: number; scope?: string; error?: string; error_description?: string }
  if (!tokenRes.ok || !tokens.access_token) {
    console.error('Native Google code exchange failed:', tokens.error, tokens.error_description)
    throw new NativeGoogleError(400, 'GOOGLE_CODE_INVALID', 'Google sign-in expired. Please try again.')
  }
  if (!(tokens.scope ?? '').split(' ').includes(DRIVE_SCOPE)) {
    throw new NativeGoogleError(400, 'GOOGLE_DRIVE_NOT_ALLOWED', 'Nudge Drive needs access to your Google Drive to store your files there. Please try again and allow it.')
  }
  const profileRes = await fetch('https://www.googleapis.com/oauth2/v2/userinfo', { headers: { Authorization: `Bearer ${tokens.access_token}` } })
  const profile = (await profileRes.json()) as { id?: string; email?: string; name?: string; picture?: string }
  if (!profileRes.ok || !profile.id || !profile.email) throw new NativeGoogleError(400, 'GOOGLE_PROFILE_FAILED', 'Couldn\'t read your Google profile. Please try again.')
  return { config, tokens: { ...tokens, access_token: tokens.access_token }, profile: { ...profile, id: profile.id, email: profile.email } }
}

async function saveAccount(userId: string, result: Awaited<ReturnType<typeof exchange>>) {
  const { config, tokens, profile } = result
  const key = { userId_provider_providerAccountId: { userId, provider: 'google_drive' as const, providerAccountId: profile.id } }
  const existing = await prisma.connectedAccount.findUnique({ where: key })
  const refreshTokenEncrypted = tokens.refresh_token ? encryptText(tokens.refresh_token) : existing?.refreshTokenEncrypted
  if (!refreshTokenEncrypted) throw new NativeGoogleError(400, 'GOOGLE_OAUTH_FAILED', 'Google didn\'t give offline access. Please try again.')
  const data = {
    providerConfigId: config.id,
    email: profile.email,
    displayName: profile.name,
    avatarUrl: profile.picture,
    accessTokenEncrypted: encryptText(tokens.access_token),
    refreshTokenEncrypted,
    tokenExpiresAt: new Date(Date.now() + (tokens.expires_in ?? 3600) * 1000),
    // What Google actually granted (it can include extra permissions such as Google Photos).
    scopes: tokens.scope ? tokens.scope.split(' ').filter(Boolean) : (config.scopes as string[]),
    status: 'connected' as const,
  }
  const account = await prisma.connectedAccount.upsert({
    where: key,
    create: { userId, provider: 'google_drive', providerAccountId: profile.id, ...data },
    update: data,
  })
  await syncGoogleQuota(account.id).catch(() => undefined)
  // Show everything already on that Drive, not just what Nudge puts there.
  if (!account.fullSync) startGoogleDriveImport(account.id, userId)
  return account
}

function fail(res: import('express').Response, next: import('express').NextFunction, error: unknown) {
  if (error instanceof NativeGoogleError) return res.status(error.status).json({ code: error.code, message: error.message })
  return next(error)
}

export function registerGoogleNative(router: Router, createSession: CreateSession) {
  /** The web client ID (public) and scopes the app asks Google for. */
  router.get('/google/native-config', async (_req: Request, res, next) => {
    try {
      const config = await activeConfig()
      return res.json({ clientId: decryptText(config.clientIdEncrypted), scopes: config.scopes })
    } catch (error) {
      return next(error)
    }
  })

  /** Sign in (or sign up) with a Google account picked on the phone; its Drive joins the pool. */
  router.post('/google/native', async (req: AuthRequest, res, next) => {
    try {
      const { code } = codeSchema.parse(req.body)
      const result = await exchange(code)
      const { profile } = result
      const name = profile.name || profile.email.split('@')[0] || 'Google User'
      const user = await prisma.user.upsert({
        where: { email: profile.email },
        create: { email: profile.email, name, passwordHash: await hashPassword(randomToken(32)), emailVerifiedAt: new Date() },
        update: { name, emailVerifiedAt: new Date() },
      })
      await saveAccount(user.id, result)
      const session = await createSession(user.id, req)
      return res.json({ ...session, user: { id: user.id, name: user.name, email: user.email } })
    } catch (error) {
      return fail(res, next, error)
    }
  })

  /** Signed-in user adds another Google Drive picked on the phone. */
  router.post('/google/native/connect', requireAuth, async (req: AuthRequest, res, next) => {
    try {
      const { code } = codeSchema.parse(req.body)
      const account = await saveAccount(req.user!.id, await exchange(code))
      return res.json({ status: 'connected', email: account.email })
    } catch (error) {
      return fail(res, next, error)
    }
  })
}
