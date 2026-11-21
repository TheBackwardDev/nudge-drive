import type { NextFunction, Request, Response } from 'express'
import { prisma } from '../config/prisma.js'
import { verifyAccessToken } from '../utils/jwt.js'

export type AuthRequest = Request & {
  user?: { id: string; sessionId: string; emailVerified: boolean }
}

export async function requireAuth(req: AuthRequest, res: Response, next: NextFunction) {
  try {
    const header = req.header('Authorization')
    if (!header?.startsWith('Bearer ')) return res.status(401).json({ code: 'AUTH_REQUIRED', message: 'Bearer token required.' })
    const payload = verifyAccessToken(header.slice(7))
    const session = await prisma.userSession.findUnique({ where: { id: payload.sid }, include: { user: { select: { emailVerifiedAt: true } } } })
    if (!session || session.revokedAt || session.expiresAt < new Date()) return res.status(401).json({ code: 'AUTH_SESSION_EXPIRED', message: 'Session expired.' })
    const emailVerified = session.user.emailVerifiedAt !== null
    req.user = { id: payload.sub, sessionId: payload.sid, emailVerified }
    // Unverified accounts may only use /auth/* (verify, resend, me, logout) until they confirm their email.
    const path = req.originalUrl.startsWith('/api/') ? req.originalUrl.slice(4) : req.originalUrl
    if (!emailVerified && !path.startsWith('/auth/')) {
      return res.status(403).json({ code: 'EMAIL_NOT_VERIFIED', message: 'Please verify your email address first.' })
    }
    return next()
  } catch {
    return res.status(401).json({ code: 'AUTH_INVALID_TOKEN', message: 'Invalid token.' })
  }
}
