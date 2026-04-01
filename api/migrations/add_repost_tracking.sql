-- ============================================================
-- Migration: Add repost tracking
-- Run this on the production database before deploying the
-- updated repost_listing.php
-- ============================================================

-- 1. New table: logs every repost event
CREATE TABLE IF NOT EXISTS `listing_repost_log` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `listing_id`  BIGINT UNSIGNED NOT NULL,
  `user_id`     BIGINT UNSIGNED NOT NULL,
  `ip_address`  VARCHAR(45)     DEFAULT NULL,
  `reposted_at` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_listing` (`listing_id`),
  INDEX `idx_user`    (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Add repost_count column to listing table
ALTER TABLE `listing`
  ADD COLUMN `repost_count` INT UNSIGNED NOT NULL DEFAULT 0;
