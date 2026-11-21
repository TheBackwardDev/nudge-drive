-- Email verification + full Google Drive sync (Nudge Drive)
ALTER TABLE `users` ADD COLUMN `email_verified_at` DATETIME(3) NULL;
-- Everyone who already has an account is treated as verified.
UPDATE `users` SET `email_verified_at` = `created_at`;

ALTER TABLE `connected_accounts` ADD COLUMN `full_sync` BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE `email_verifications` (
    `id` CHAR(36) NOT NULL,
    `user_id` CHAR(36) NOT NULL,
    `code_hash` VARCHAR(255) NOT NULL,
    `link_token_hash` VARCHAR(255) NOT NULL,
    `attempts` INTEGER NOT NULL DEFAULT 0,
    `expires_at` DATETIME(3) NOT NULL,
    `used_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE INDEX `email_verifications_link_token_hash_key`(`link_token_hash`),
    INDEX `email_verifications_user_id_idx`(`user_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `email_verifications` ADD CONSTRAINT `email_verifications_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
