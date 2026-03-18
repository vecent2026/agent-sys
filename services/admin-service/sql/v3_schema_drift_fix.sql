-- ============================================================
-- admin_system 漂移修复脚本 v3（幂等）
-- 目标：补齐当前库相对 schema.sql 的关键缺失列
-- ============================================================

USE admin_system;
SET NAMES utf8mb4;

-- platform_role.create_by
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'admin_system' AND TABLE_NAME = 'platform_role' AND COLUMN_NAME = 'create_by'
);
SET @sql := IF(
  @exists = 0,
  'ALTER TABLE `platform_role` ADD COLUMN `create_by` VARCHAR(50) DEFAULT NULL AFTER `update_time`',
  'SELECT ''skip platform_role.create_by'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- platform_role.update_by
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'admin_system' AND TABLE_NAME = 'platform_role' AND COLUMN_NAME = 'update_by'
);
SET @sql := IF(
  @exists = 0,
  'ALTER TABLE `platform_role` ADD COLUMN `update_by` VARCHAR(50) DEFAULT NULL AFTER `create_by`',
  'SELECT ''skip platform_role.update_by'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- platform_tenant.create_by
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'admin_system' AND TABLE_NAME = 'platform_tenant' AND COLUMN_NAME = 'create_by'
);
SET @sql := IF(
  @exists = 0,
  'ALTER TABLE `platform_tenant` ADD COLUMN `create_by` VARCHAR(50) DEFAULT NULL AFTER `update_time`',
  'SELECT ''skip platform_tenant.create_by'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- platform_tenant.update_by
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'admin_system' AND TABLE_NAME = 'platform_tenant' AND COLUMN_NAME = 'update_by'
);
SET @sql := IF(
  @exists = 0,
  'ALTER TABLE `platform_tenant` ADD COLUMN `update_by` VARCHAR(50) DEFAULT NULL AFTER `create_by`',
  'SELECT ''skip platform_tenant.update_by'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- platform_role_permission.create_time
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'admin_system' AND TABLE_NAME = 'platform_role_permission' AND COLUMN_NAME = 'create_time'
);
SET @sql := IF(
  @exists = 0,
  'ALTER TABLE `platform_role_permission` ADD COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER `permission_id`',
  'SELECT ''skip platform_role_permission.create_time'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- tenant_role_permission.create_time
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'admin_system' AND TABLE_NAME = 'tenant_role_permission' AND COLUMN_NAME = 'create_time'
);
SET @sql := IF(
  @exists = 0,
  'ALTER TABLE `tenant_role_permission` ADD COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER `permission_id`',
  'SELECT ''skip tenant_role_permission.create_time'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- tenant_user_role.create_time
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'admin_system' AND TABLE_NAME = 'tenant_user_role' AND COLUMN_NAME = 'create_time'
);
SET @sql := IF(
  @exists = 0,
  'ALTER TABLE `tenant_user_role` ADD COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER `role_id`',
  'SELECT ''skip tenant_user_role.create_time'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- platform_user_role：补 id/create_time，并与 schema 约定保持一致
SET @has_id := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'admin_system' AND TABLE_NAME = 'platform_user_role' AND COLUMN_NAME = 'id'
);
SET @has_ct := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'admin_system' AND TABLE_NAME = 'platform_user_role' AND COLUMN_NAME = 'create_time'
);

SET @sql := IF(
  @has_id = 0,
  'ALTER TABLE `platform_user_role`
      ADD COLUMN `id` BIGINT(20) NOT NULL AUTO_INCREMENT FIRST,
      ADD COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER `role_id`,
      DROP PRIMARY KEY,
      ADD PRIMARY KEY (`id`),
      ADD UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
      ADD KEY `idx_user_id` (`user_id`)',
  IF(
    @has_ct = 0,
    'ALTER TABLE `platform_user_role` ADD COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER `role_id`',
    'SELECT ''skip platform_user_role drift fix'' AS msg'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
