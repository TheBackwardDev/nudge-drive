-- When a photo/video was taken (camera EXIF or the phone media date). Timeline sorts by this.
ALTER TABLE `files` ADD COLUMN `taken_at` DATETIME(3) NULL;
ALTER TABLE `upload_sessions` ADD COLUMN `taken_at` DATETIME(3) NULL;
CREATE INDEX `files_user_id_status_taken_at_idx` ON `files`(`user_id`, `status`, `taken_at`);
