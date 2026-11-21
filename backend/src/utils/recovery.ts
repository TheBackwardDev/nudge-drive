import { prisma } from '../config/prisma.js'
import { deleteS3Key } from '../modules/s3/s3.service.js'
import { FileStatus, UploadStatus } from './states.js'

const HOUR = 3600_000
/** Google keeps a resumable upload open for a week; any other upload is done within a day or never. */
const GOOGLE_UPLOAD_TTL = 7 * 24 * HOUR
const UPLOAD_TTL = 24 * HOUR

/**
 * Closes work that can never finish: a crash or restart in the middle of an upload leaves its session
 * (and, for S3, a hidden placeholder file) in "uploading" for good. Runs at startup, then every hour.
 */
export async function recoverStuckWork() {
  const now = Date.now()
  const sessions = await prisma.uploadSession.updateMany({
    where: {
      status: UploadStatus.Uploading,
      OR: [
        { googleSessionUri: null, createdAt: { lt: new Date(now - UPLOAD_TTL) } },
        { googleSessionUri: { not: null }, createdAt: { lt: new Date(now - GOOGLE_UPLOAD_TTL) } },
      ],
    },
    data: { status: UploadStatus.Abandoned, errorMessage: 'The upload never finished.' },
  })

  const placeholders = await prisma.file.findMany({
    where: { status: FileStatus.Uploading, createdAt: { lt: new Date(now - UPLOAD_TTL) } },
    select: { id: true, connectedAccountId: true, providerFileId: true },
  })
  for (const f of placeholders) {
    if (f.providerFileId !== 'pending') await deleteS3Key(f.connectedAccountId, f.providerFileId).catch(() => undefined)
    await prisma.file.deleteMany({ where: { id: f.id, status: FileStatus.Uploading } })
  }
  if (sessions.count || placeholders.length) console.info('[recovery] closed stuck uploads', { sessions: sessions.count, files: placeholders.length })
}

export function startRecoverySweep() {
  const run = () => recoverStuckWork().catch((error) => console.error('[recovery] sweep failed', error))
  void run()
  setInterval(run, HOUR).unref()
}
