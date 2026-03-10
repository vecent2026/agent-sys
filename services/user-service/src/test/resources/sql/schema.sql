-- App User Table
CREATE TABLE IF NOT EXISTS `app_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `nickname` VARCHAR(50) NOT NULL,
  `mobile` VARCHAR(20) NOT NULL,
  `email` VARCHAR(100) NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `register_time` DATETIME NOT NULL,
  `register_source` VARCHAR(20) NOT NULL DEFAULT 'WEB',
  `last_login_time` DATETIME NULL,
  `last_login_ip` VARCHAR(50) NULL,
  `avatar` VARCHAR(255) NULL,
  `gender` TINYINT NULL,
  `birthdate` DATE NULL,
  `city` VARCHAR(100) NULL,
  `bio` VARCHAR(500) NULL,
  `custom_fields` JSON NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_mobile` (`mobile`),
  UNIQUE INDEX `uk_email` (`email`),
  INDEX `idx_status` (`status`),
  INDEX `idx_register_time` (`register_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- App User Tag Table
CREATE TABLE IF NOT EXISTS `app_user_tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL,
  `color` VARCHAR(20) NOT NULL DEFAULT 'blue',
  `description` VARCHAR(200) NULL,
  `user_count` INT NOT NULL DEFAULT 0,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- App User Tag Relation Table
CREATE TABLE IF NOT EXISTS `app_user_tag_relation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `tag_id` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_user_tag` (`user_id`, `tag_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_tag_id` (`tag_id`),
  CONSTRAINT `fk_user_tag_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `app_user_tag` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- App User Field Table
CREATE TABLE IF NOT EXISTS `app_user_field` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL,
  `label` VARCHAR(50) NOT NULL,
  `type` VARCHAR(20) NOT NULL,
  `options` JSON NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `is_required` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- App User Field Value Table
CREATE TABLE IF NOT EXISTS `app_user_field_value` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `field_id` BIGINT NOT NULL,
  `value` JSON NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_user_field` (`user_id`, `field_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_field_id` (`field_id`),
  CONSTRAINT `fk_field_value_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_field_value_field` FOREIGN KEY (`field_id`) REFERENCES `app_user_field` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;