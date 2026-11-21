/**
 * Every lifecycle state the server stores, in one place. The database columns are plain strings,
 * so code should compare and write these constants instead of string literals.
 */

/** files.status */
export const FileStatus = {
  /** S3 upload in progress: the row exists so the object key can use its id. Shown nowhere. */
  Uploading: 'uploading',
  Active: 'active',
  /** In the user's Nudge trash (can be restored). A Drive sync never brings it back. */
  Trashed: 'deleted',
  /** Gone from the provider outside Nudge. Hidden everywhere; a sync brings it back if it reappears. */
  Missing: 'missing',
} as const
export type FileStatus = (typeof FileStatus)[keyof typeof FileStatus]

/** upload_sessions.status: uploading -> completed | failed | abandoned (all three are final). */
export const UploadStatus = {
  Uploading: 'uploading',
  Completed: 'completed',
  Failed: 'failed',
  /** Never finished; closed by the recovery sweep. */
  Abandoned: 'abandoned',
} as const
export type UploadStatus = (typeof UploadStatus)[keyof typeof UploadStatus]

/** connected_accounts.status */
export const AccountStatus = { Connected: 'connected', Disconnected: 'disconnected' } as const
