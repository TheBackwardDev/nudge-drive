import { randomInt } from 'node:crypto'
import nodemailer, { type Transporter } from 'nodemailer'
import { env } from '../config/env.js'
import { prisma } from '../config/prisma.js'
import { hashToken, randomToken } from './crypto.js'

/** Email verification is only enforced when Gmail SMTP credentials are configured. */
export const mailEnabled = () => Boolean(env.SMTP_USER && env.SMTP_PASS)

let transporter: Transporter | null = null
function transport() {
  transporter ??= nodemailer.createTransport({
    host: env.SMTP_HOST,
    port: env.SMTP_PORT,
    secure: env.SMTP_PORT === 465,
    auth: { user: env.SMTP_USER, pass: env.SMTP_PASS },
  })
  return transporter
}

const CODE_TTL_MS = 15 * 60 * 1000
const MAX_ATTEMPTS = 5
const codeHash = (userId: string, code: string) => hashToken(`${userId}:${code}`)

/** Creates a 6-digit code + one-tap link and emails them. Older codes stay valid until they expire. */
export async function issueVerification(user: { id: string; email: string; name: string }) {
  const code = String(randomInt(0, 1_000_000)).padStart(6, '0')
  const linkToken = randomToken(32)
  await prisma.emailVerification.create({
    data: { userId: user.id, codeHash: codeHash(user.id, code), linkTokenHash: hashToken(linkToken), expiresAt: new Date(Date.now() + CODE_TTL_MS) },
  })
  const link = `${env.FRONTEND_URL}/api/auth/verify-email?token=${linkToken}`
  const first = user.name.split(' ')[0] || 'there'
  await transport().sendMail({
    from: env.MAIL_FROM || `Nudge Drive <${env.SMTP_USER}>`,
    to: user.email,
    subject: `${code} is your Nudge Drive code`,
    text: `Hi ${first},\n\nYour Nudge Drive verification code for ${user.email} is ${code}. It expires in 15 minutes.\n\nOr open this link: ${link}\n\nIf you didn't create an account, you can ignore this email.`,
    html: `<!doctype html><html><body style="margin:0;background:#F7F4FF;font-family:Inter,Segoe UI,Arial,sans-serif;color:#1E1440">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="padding:32px 12px"><tr><td align="center">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:440px;background:#ffffff;border-radius:28px;padding:32px 28px;box-shadow:0 18px 40px rgba(110,80,220,.12)">
<tr><td align="center"><div style="width:56px;height:56px;border-radius:18px;background:linear-gradient(135deg,#CDBFFF,#8B72F5);color:#fff;font:700 30px/56px Arial,sans-serif">n</div></td></tr>
<tr><td align="center" style="padding-top:18px;font:700 22px Arial,sans-serif">Confirm your email</td></tr>
<tr><td align="center" style="padding-top:8px;color:#4E4670;font-size:15px;line-height:1.5">Hi ${escapeHtml(first)}, enter this code in the Nudge Drive app to confirm <b>${escapeHtml(user.email)}</b>.</td></tr>
<tr><td align="center" style="padding:22px 0"><div style="display:inline-block;letter-spacing:10px;font:700 34px 'Courier New',monospace;background:#ECE6FF;border-radius:18px;padding:14px 22px 14px 32px">${code}</div></td></tr>
<tr><td align="center"><a href="${link}" style="display:inline-block;background:#7059F0;color:#fff;text-decoration:none;font:700 15px Arial,sans-serif;padding:14px 26px;border-radius:26px">Verify with one tap</a></td></tr>
<tr><td align="center" style="padding-top:20px;color:#6B6390;font-size:12px;line-height:1.5">The code expires in 15 minutes. If you didn't create an account, ignore this email.</td></tr>
</table></td></tr></table></body></html>`,
  })
}

async function markVerified(userId: string, verificationId: string) {
  await prisma.$transaction([
    prisma.emailVerification.update({ where: { id: verificationId }, data: { usedAt: new Date() } }),
    prisma.user.update({ where: { id: userId }, data: { emailVerifiedAt: new Date() } }),
  ])
}

export async function verifyCode(userId: string, code: string): Promise<'ok' | 'invalid' | 'expired' | 'too_many'> {
  const user = await prisma.user.findUniqueOrThrow({ where: { id: userId }, select: { emailVerifiedAt: true } })
  if (user.emailVerifiedAt) return 'ok'
  // Any of the last few unexpired codes is accepted (emails can arrive late or out of order after "Resend").
  const open = await prisma.emailVerification.findMany({
    where: { userId, usedAt: null, expiresAt: { gt: new Date() } }, orderBy: { createdAt: 'desc' }, take: 3,
  })
  if (open.length === 0) return 'expired'
  const latest = open[0]!
  if (latest.attempts >= MAX_ATTEMPTS) return 'too_many'
  const match = open.find((v) => v.codeHash === codeHash(userId, code))
  if (!match) {
    await prisma.emailVerification.update({ where: { id: latest.id }, data: { attempts: { increment: 1 } } })
    return latest.attempts + 1 >= MAX_ATTEMPTS ? 'too_many' : 'invalid'
  }
  await markVerified(userId, match.id)
  await prisma.emailVerification.updateMany({ where: { userId, usedAt: null }, data: { usedAt: new Date() } })
  return 'ok'
}

export async function verifyLinkToken(token: string): Promise<boolean> {
  const v = await prisma.emailVerification.findUnique({ where: { linkTokenHash: hashToken(token) } })
  if (!v || v.usedAt || v.expiresAt < new Date()) return false
  await markVerified(v.userId, v.id)
  return true
}

/** Branded one-card email (same look as the verification email). */
function card(title: string, bodyHtml: string, footer: string) {
  return `<!doctype html><html><body style="margin:0;background:#F7F4FF;font-family:Inter,Segoe UI,Arial,sans-serif;color:#1E1440">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="padding:32px 12px"><tr><td align="center">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:440px;background:#ffffff;border-radius:28px;padding:32px 28px;box-shadow:0 18px 40px rgba(110,80,220,.12)">
<tr><td align="center"><div style="width:56px;height:56px;border-radius:18px;background:linear-gradient(135deg,#CDBFFF,#8B72F5);color:#fff;font:700 30px/56px Arial,sans-serif">n</div></td></tr>
<tr><td align="center" style="padding-top:18px;font:700 22px Arial,sans-serif">${title}</td></tr>
${bodyHtml}
<tr><td align="center" style="padding-top:20px;color:#6B6390;font-size:12px;line-height:1.5">${footer}</td></tr>
</table></td></tr></table></body></html>`
}

/** Code that confirms deleting the account. */
export async function sendDeletionCode(user: { email: string; name: string }, code: string) {
  const first = user.name.split(' ')[0] || 'there'
  await transport().sendMail({
    from: env.MAIL_FROM || `Nudge Drive <${env.SMTP_USER}>`,
    to: user.email,
    subject: `${code} confirms deleting your Nudge Drive account`,
    text: `Hi ${first},\n\nSomeone (hopefully you) asked to delete the Nudge Drive account ${user.email}. To confirm, enter this code: ${code}. It expires in 15 minutes.\n\nYour files stay in your own Google Drive / storage. If you didn't ask for this, ignore this email and change your password.`,
    html: card('Confirm account deletion',
      `<tr><td align="center" style="padding-top:8px;color:#4E4670;font-size:15px;line-height:1.5">Hi ${escapeHtml(first)}, enter this code to permanently delete the Nudge Drive account <b>${escapeHtml(user.email)}</b>.</td></tr>
<tr><td align="center" style="padding:22px 0"><div style="display:inline-block;letter-spacing:10px;font:700 34px 'Courier New',monospace;background:#FFE4E6;color:#9F1239;border-radius:18px;padding:14px 22px 14px 32px">${code}</div></td></tr>`,
      "The code expires in 15 minutes. Your files stay in your own Google Drive or storage. If you didn't ask for this, ignore this email and change your password."),
  })
}

/** Sent after the account is gone. */
export async function sendAccountDeletedNotice(user: { email: string; name: string }) {
  const first = user.name.split(' ')[0] || 'there'
  await transport().sendMail({
    from: env.MAIL_FROM || `Nudge Drive <${env.SMTP_USER}>`,
    to: user.email,
    subject: 'Your Nudge Drive account was deleted',
    text: `Hi ${first},\n\nYour Nudge Drive account ${user.email} and everything Nudge Drive stored about it have been deleted. Your files are still in your own Google Drive / storage, and Nudge Drive's access to them has been removed.\n\nThanks for trying Nudge Drive.`,
    html: card('Account deleted',
      `<tr><td align="center" style="padding-top:8px;color:#4E4670;font-size:15px;line-height:1.5">Hi ${escapeHtml(first)}, your Nudge Drive account <b>${escapeHtml(user.email)}</b> and everything Nudge Drive stored about it have been deleted. Your files are still in your own Google Drive or storage, and Nudge Drive's access to them has been removed.</td></tr>`,
      'Thanks for trying Nudge Drive.'),
  })
}

function escapeHtml(s: string) {
  return s.replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch]!)
}
