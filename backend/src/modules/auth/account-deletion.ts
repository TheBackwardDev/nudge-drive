import { randomInt } from 'node:crypto'
import type { Router } from 'express'
import { z } from 'zod'
import { prisma } from '../../config/prisma.js'
import { requireAuth, type AuthRequest } from '../../middleware/auth.middleware.js'
import { verifyPassword } from '../../utils/password.js'
import { decryptText, hashToken } from '../../utils/crypto.js'
import { mailEnabled, sendAccountDeletedNotice, sendDeletionCode } from '../../utils/mailer.js'
import { purgeCacheFor } from '../files/preview-cache.js'

/**
 * Account deletion (required by Google Play): the signed-in user confirms with their password or
 * with a 6-digit code emailed to them (Google sign-in users don't know a password), then every
 * record Nudge Drive keeps about them is removed. Files stay in the user's own Google Drive / S3
 * storage: Nudge only ever held references and access tokens, and those tokens are revoked.
 */
const CODE_TTL_MS = 15 * 60 * 1000
const MAX_ATTEMPTS = 5
type PendingCode = { hash: string; expiresAt: number; attempts: number; sentAt: number }
// In memory on purpose: a code only lives 15 minutes, and a restart just means asking for a new one.
const pending = new Map<string, PendingCode>()
const codeHash = (userId: string, code: string) => hashToken(`delete:${userId}:${code}`)

export function registerAccountDeletion(router: Router) {
  router.post('/delete-account/code', requireAuth, async (req: AuthRequest, res, next) => {
    try {
      if (!mailEnabled()) return res.status(503).json({ code: 'MAIL_DISABLED', message: 'Email is not set up on this server. Confirm with your password instead.' })
      const userId = req.user!.id
      const last = pending.get(userId)
      if (last && Date.now() - last.sentAt < 45_000) return res.status(429).json({ code: 'RATE_LIMIT', message: 'Please wait a few seconds before asking for another code.' })
      const user = await prisma.user.findUniqueOrThrow({ where: { id: userId }, select: { id: true, email: true, name: true } })
      const code = String(randomInt(0, 1_000_000)).padStart(6, '0')
      pending.set(userId, { hash: codeHash(userId, code), expiresAt: Date.now() + CODE_TTL_MS, attempts: 0, sentAt: Date.now() })
      await sendDeletionCode(user, code)
      return res.json({ status: 'sent', email: user.email })
    } catch (error) {
      return next(error)
    }
  })

  router.post('/delete-account', requireAuth, async (req: AuthRequest, res, next) => {
    try {
      const body = z.object({ password: z.string().min(1).optional(), code: z.string().regex(/^\d{6}$/).optional() }).parse(req.body)
      const userId = req.user!.id
      const user = await prisma.user.findUniqueOrThrow({ where: { id: userId } })

      let confirmed = false
      if (body.password) confirmed = await verifyPassword(user.passwordHash, body.password).catch(() => false)
      if (!confirmed && body.code) {
        const p = pending.get(userId)
        if (!p || p.expiresAt < Date.now()) return res.status(400).json({ code: 'CODE_EXPIRED', message: 'That code has expired. Send a new one.' })
        if (p.attempts >= MAX_ATTEMPTS) return res.status(429).json({ code: 'CODE_LOCKED', message: 'Too many wrong tries. Send a new code.' })
        if (p.hash !== codeHash(userId, body.code)) {
          p.attempts++
          return res.status(400).json({ code: 'CODE_INVALID', message: 'That code does not match the one we emailed.' })
        }
        confirmed = true
      }
      if (!confirmed) {
        return res.status(400).json({
          code: body.password ? 'PASSWORD_INVALID' : 'CONFIRMATION_REQUIRED',
          message: body.password ? 'That password is not right.' : 'Confirm with your password or the code we email you.',
        })
      }

      pending.delete(userId)
      await deleteUserCompletely(user.id, user.email)
      if (mailEnabled()) void sendAccountDeletedNotice({ email: user.email, name: user.name }).catch(() => {})
      return res.json({ status: 'deleted' })
    } catch (error) {
      return next(error)
    }
  })
}

/** Removes the user and everything linked to them. Storage tokens are revoked first (best effort). */
export async function deleteUserCompletely(userId: string, email: string) {
  const accounts = await prisma.connectedAccount.findMany({ where: { userId }, select: { provider: true, refreshTokenEncrypted: true, accessTokenEncrypted: true } })
  await Promise.all(accounts.map(async (a) => {
    if (a.provider !== 'google_drive') return
    const enc = a.refreshTokenEncrypted ?? a.accessTokenEncrypted
    if (!enc) return
    try {
      const token = decryptText(enc)
      await fetch('https://oauth2.googleapis.com/revoke', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ token }) })
    } catch { /* the account is removed either way */ }
  }))

  const files = await prisma.file.findMany({ where: { userId }, select: { id: true } })

  await prisma.$transaction([
    // These point at the user without cascading, so remove them explicitly.
    prisma.auditLog.deleteMany({ where: { userId } }),
    prisma.workspaceInvite.deleteMany({ where: { inviteeEmail: email } }),
    prisma.providerConfig.deleteMany({ where: { userId } }),
    // Everything else (sessions, drives, files, folders, links, API keys, invites sent, ...) cascades.
    prisma.user.delete({ where: { id: userId } }),
  ])

  await purgeCacheFor(files.map((f) => f.id)).catch(() => {})
}
