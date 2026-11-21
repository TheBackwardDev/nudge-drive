import { Router } from 'express'
import { google } from 'googleapis'
import { z } from 'zod'
import { prisma } from '../../config/prisma.js'
import { env } from '../../config/env.js'
import { requireAuth, type AuthRequest } from '../../middleware/auth.middleware.js'
import { hashPassword, verifyPassword } from '../../utils/password.js'
import { encryptText, hashToken, randomToken } from '../../utils/crypto.js'
import { signAccessToken } from '../../utils/jwt.js'
import { createOAuthClient, syncGoogleQuota } from '../google/google.service.js'
import { mailEnabled, issueVerification, verifyCode, verifyLinkToken } from '../../utils/mailer.js'
import { registerAccountDeletion } from './account-deletion.js'
import { registerGoogleNative } from './google-native.js'

export const authRouter = Router()

const registerSchema = z.object({ name: z.string().min(2), email: z.string().email(), password: z.string().min(8), captchaToken: z.string().optional() })
const loginSchema = z.object({ email: z.string().email(), password: z.string().min(1) })
const refreshSchema = z.object({ refreshToken: z.string().min(1) })
const googleExchangeSchema = z.object({ token: z.string().min(1) })

export async function createSession(userId: string, req: AuthRequest) {
  const refreshToken = randomToken()
  const expiresAt = new Date(Date.now() + env.REFRESH_TOKEN_TTL_DAYS * 24 * 60 * 60 * 1000)
  const session = await prisma.userSession.create({
    data: {
      userId,
      refreshTokenHash: hashToken(refreshToken),
      userAgent: req.header('User-Agent'),
      ipAddress: req.ip,
      expiresAt,
    },
  })
  return { accessToken: signAccessToken({ sub: userId, sid: session.id }), refreshToken }
}

async function verifyCaptcha(token: string | undefined) {
  if (!env.RECAPTCHA_SECRET_KEY) return true
  if (!token) return false
  const form = new URLSearchParams({ secret: env.RECAPTCHA_SECRET_KEY, response: token })
  const response = await fetch('https://www.google.com/recaptcha/api/siteverify', { method: 'POST', body: form })
  const data = await response.json() as { success?: boolean }
  return Boolean(data.success)
}

authRouter.post('/register', async (req, res, next) => {
  try {
    const body = registerSchema.parse(req.body)
    if (!(await verifyCaptcha(body.captchaToken))) return res.status(400).json({ code: 'CAPTCHA_FAILED', message: 'Captcha verification failed.' })
    const existing = await prisma.user.findUnique({ where: { email: body.email } })
    if (existing) return res.status(409).json({ code: 'AUTH_EMAIL_TAKEN', message: 'Email already registered.' })
    const user = await prisma.user.create({
      data: { name: body.name, email: body.email, passwordHash: await hashPassword(body.password), emailVerifiedAt: mailEnabled() ? null : new Date() },
    })
    const tokens = await createSession(user.id, req)
    if (mailEnabled()) await issueVerification(user).catch((e) => console.error('verification email failed', e))
    return res.status(201).json({ ...tokens, user: { id: user.id, name: user.name, email: user.email, emailVerified: user.emailVerifiedAt !== null } })
  } catch (error) {
    return next(error)
  }
})

authRouter.post('/login', async (req, res, next) => {
  try {
    const body = loginSchema.parse(req.body)
    const user = await prisma.user.findUnique({ where: { email: body.email } })
    if (!user || !(await verifyPassword(user.passwordHash, body.password))) return res.status(401).json({ code: 'AUTH_INVALID_CREDENTIALS', message: 'Invalid email or password.' })
    const tokens = await createSession(user.id, req)
    return res.json({ ...tokens, user: { id: user.id, name: user.name, email: user.email, emailVerified: user.emailVerifiedAt !== null } })
  } catch (error) {
    return next(error)
  }
})

/**
 * Nudge Drive desktop (Windows/macOS) signs in through the system browser and listens on
 * http://127.0.0.1:<port>/google-auth for the result (loopback redirect, RFC 8252). The port is
 * remembered in the one-time OAuth state, so the callback can only send the hand-off token to
 * the loopback address the app asked for.
 */
const desktopPortSchema = z.coerce.number().int().min(1024).max(65535)
const desktopPort = (flow: string): number | null => (flow.startsWith('login-desktop:') ? Number(flow.slice('login-desktop:'.length)) || null : null)
const loginRedirect = (flow: string, query: string) => {
  const port = desktopPort(flow)
  return port ? `http://127.0.0.1:${port}/google-auth?${query}` : `${env.FRONTEND_URL}/google-auth?${query}`
}

authRouter.get('/google/url', async (req, res, next) => {
  try {
    const config = await prisma.providerConfig.findFirstOrThrow({ where: { userId: null, provider: 'google_drive', status: 'active' }, orderBy: { createdAt: 'desc' } })
    const state = randomToken()
    const port = req.query.desktopPort === undefined ? null : desktopPortSchema.parse(req.query.desktopPort)
    const flow = port ? `login-desktop:${port}` : 'login'
    await prisma.oauthState.create({ data: { providerConfigId: config.id, flow, stateHash: hashToken(state), expiresAt: new Date(Date.now() + 10 * 60_000) } })
    const client = createOAuthClient(config)
    const url = client.generateAuthUrl({
      access_type: 'offline',
      prompt: 'consent',
      include_granted_scopes: true,
      scope: config.scopes as string[],
      state,
    })
    return res.json({ url })
  } catch (error) {
    return next(error)
  }
})

authRouter.get('/google/callback', async (req, res) => {
  let flow = 'login'
  try {
    const query = z.object({ code: z.string(), state: z.string() }).parse(req.query)
    const oauthState = await prisma.oauthState.findUniqueOrThrow({ where: { stateHash: hashToken(query.state) }, include: { providerConfig: true } })
    flow = oauthState.flow
    const isLogin = flow === 'login' || desktopPort(flow) !== null
    if (!isLogin || oauthState.usedAt || oauthState.expiresAt < new Date()) return res.redirect(loginRedirect(isLogin ? flow : 'login', 'status=error'))

    const client = createOAuthClient(oauthState.providerConfig)
    const tokenResult = await client.getToken(query.code)
    const tokens = tokenResult.tokens
    if (!tokens.access_token) return res.redirect(loginRedirect(flow, 'status=error'))
    client.setCredentials(tokens)

    const oauth2 = google.oauth2({ version: 'v2', auth: client })
    const profile = await oauth2.userinfo.get()
    const providerAccountId = profile.data.id
    const email = profile.data.email
    if (!providerAccountId || !email) return res.redirect(loginRedirect(flow, 'status=error'))

    const name = profile.data.name || email.split('@')[0] || 'Google User'
    const user = await prisma.user.upsert({
      where: { email },
      create: { email, name, passwordHash: await hashPassword(randomToken(32)), emailVerifiedAt: new Date() },
      update: { name, emailVerifiedAt: new Date() },
    })
    const existingAccount = await prisma.connectedAccount.findUnique({ where: { userId_provider_providerAccountId: { userId: user.id, provider: 'google_drive', providerAccountId } } })
    const refreshTokenEncrypted = tokens.refresh_token ? encryptText(tokens.refresh_token) : existingAccount?.refreshTokenEncrypted
    if (!refreshTokenEncrypted) return res.redirect(loginRedirect(flow, 'status=error'))

    const account = await prisma.connectedAccount.upsert({
      where: { userId_provider_providerAccountId: { userId: user.id, provider: 'google_drive', providerAccountId } },
      create: {
        userId: user.id,
        providerConfigId: oauthState.providerConfigId,
        provider: 'google_drive',
        providerAccountId,
        email,
        displayName: profile.data.name,
        avatarUrl: profile.data.picture,
        accessTokenEncrypted: encryptText(tokens.access_token),
        refreshTokenEncrypted,
        tokenExpiresAt: new Date(tokens.expiry_date ?? Date.now() + 3600_000),
        scopes: oauthState.providerConfig.scopes as string[],
        status: 'connected',
      },
      update: {
        providerConfigId: oauthState.providerConfigId,
        email,
        displayName: profile.data.name,
        avatarUrl: profile.data.picture,
        accessTokenEncrypted: encryptText(tokens.access_token),
        refreshTokenEncrypted,
        tokenExpiresAt: new Date(tokens.expiry_date ?? Date.now() + 3600_000),
        scopes: oauthState.providerConfig.scopes as string[],
        status: 'connected',
      },
    })

    await prisma.oauthState.update({ where: { id: oauthState.id }, data: { usedAt: new Date(), userId: user.id } })
    await syncGoogleQuota(account.id).catch(() => undefined)

    const handoffToken = randomToken()
    await prisma.authHandoff.create({ data: { userId: user.id, tokenHash: hashToken(handoffToken), expiresAt: new Date(Date.now() + 5 * 60_000) } })
    return res.redirect(loginRedirect(flow, `token=${handoffToken}`))
  } catch (error) {
    console.error('Google Auth callback failed:', error)
    return res.redirect(loginRedirect(flow, 'status=error'))
  }
})

authRouter.post('/google/exchange', async (req, res, next) => {
  try {
    const body = googleExchangeSchema.parse(req.body)
    const handoff = await prisma.authHandoff.findFirst({ where: { tokenHash: hashToken(body.token), usedAt: null, expiresAt: { gt: new Date() } }, include: { user: true } })
    if (!handoff) return res.status(401).json({ code: 'AUTH_GOOGLE_HANDOFF_INVALID', message: 'Google login session expired.' })
    // Claim it atomically: two requests racing with the same token must not both get a session.
    const claimed = await prisma.authHandoff.updateMany({ where: { id: handoff.id, usedAt: null }, data: { usedAt: new Date() } })
    if (claimed.count === 0) return res.status(401).json({ code: 'AUTH_GOOGLE_HANDOFF_INVALID', message: 'Google login session expired.' })
    const tokens = await createSession(handoff.userId, req)
    return res.json({ ...tokens, user: { id: handoff.user.id, name: handoff.user.name, email: handoff.user.email } })
  } catch (error) {
    return next(error)
  }
})

authRouter.post('/refresh', async (req, res, next) => {
  try {
    const body = refreshSchema.parse(req.body)
    const session = await prisma.userSession.findFirst({ where: { refreshTokenHash: hashToken(body.refreshToken), revokedAt: null, expiresAt: { gt: new Date() } } })
    if (!session) return res.status(401).json({ code: 'AUTH_SESSION_EXPIRED', message: 'Refresh token expired.' })
    return res.json({ accessToken: signAccessToken({ sub: session.userId, sid: session.id }) })
  } catch (error) {
    return next(error)
  }
})

authRouter.post('/logout', requireAuth, async (req: AuthRequest, res, next) => {
  try {
    await prisma.userSession.update({ where: { id: req.user!.sessionId }, data: { revokedAt: new Date() } })
    return res.json({ status: 'ok' })
  } catch (error) {
    return next(error)
  }
})

authRouter.get('/me', requireAuth, async (req: AuthRequest, res, next) => {
  try {
    const found = await prisma.user.findUniqueOrThrow({ where: { id: req.user!.id }, select: { id: true, name: true, email: true, status: true, emailVerifiedAt: true } })
    const { emailVerifiedAt, ...rest } = found
    const user = { ...rest, emailVerified: emailVerifiedAt !== null }
    return res.json({ user })
  } catch (error) {
    return next(error)
  }
})

// ---- Account deletion (Nudge Drive; Google Play requirement) ----
registerAccountDeletion(authRouter)
registerGoogleNative(authRouter, createSession)

// ---- Email verification (Nudge Drive) ----
authRouter.post('/verify-email', requireAuth, async (req: AuthRequest, res, next) => {
  try {
    const { code } = z.object({ code: z.string().regex(/^\d{6}$/) }).parse(req.body)
    const result = await verifyCode(req.user!.id, code)
    if (result === 'ok') return res.json({ status: 'ok', emailVerified: true })
    if (result === 'expired') return res.status(400).json({ code: 'VERIFICATION_EXPIRED', message: 'That code has expired. Tap Resend code for a new one.' })
    if (result === 'too_many') return res.status(429).json({ code: 'VERIFICATION_LOCKED', message: 'Too many wrong tries. Send a new code.' })
    return res.status(400).json({ code: 'VERIFICATION_INVALID', message: 'That code does not match. Use the newest email for this address.' })
  } catch (error) {
    return next(error)
  }
})

authRouter.post('/resend-verification', requireAuth, async (req: AuthRequest, res, next) => {
  try {
    const user = await prisma.user.findUniqueOrThrow({ where: { id: req.user!.id } })
    if (user.emailVerifiedAt) return res.json({ status: 'ok', emailVerified: true })
    if (!mailEnabled()) return res.status(503).json({ code: 'MAIL_DISABLED', message: 'Email is not set up on this server.' })
    const last = await prisma.emailVerification.findFirst({ where: { userId: user.id }, orderBy: { createdAt: 'desc' } })
    if (last && Date.now() - last.createdAt.getTime() < 45_000) return res.status(429).json({ code: 'VERIFICATION_RATE_LIMIT', message: 'Please wait a few seconds before asking for another code.' })
    await issueVerification(user)
    return res.json({ status: 'sent' })
  } catch (error) {
    return next(error)
  }
})

// One-tap link from the email; works in any browser.
authRouter.get('/verify-email', async (req, res) => {
  const ok = typeof req.query.token === 'string' && (await verifyLinkToken(req.query.token).catch(() => false))
  res.setHeader('Content-Type', 'text/html; charset=utf-8')
  return res.status(ok ? 200 : 400).send(`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Nudge Drive</title>
<style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;font-family:system-ui,sans-serif;background:linear-gradient(180deg,#D9CEFF,#F7F4FF);color:#1E1440}
.card{background:#fff;border-radius:28px;padding:36px 28px;max-width:360px;text-align:center;box-shadow:0 20px 50px rgba(110,80,220,.18)}
h1{font-size:22px;margin:16px 0 8px}p{color:#4E4670;line-height:1.5;margin:0}.dot{width:64px;height:64px;border-radius:20px;margin:0 auto;background:linear-gradient(135deg,#CDBFFF,#8B72F5)}</style></head>
<body><div class="card"><div class="dot"></div><h1>${ok ? 'Email verified' : 'Link expired'}</h1><p>${ok ? 'You are all set. Go back to the Nudge Drive app and continue.' : 'This link is no longer valid. Open the app and send a new code.'}</p></div></body></html>`)
})
