-- ============================================================
-- admin_system 历史遗留清理脚本 v4（幂等）
-- 目标：
-- 1) 清理已废弃表 platform_admin_role（如有数据先迁移到 platform_user_role）
-- 2) 清理已废弃列 platform_user.is_super（超管身份已由 platform_role.is_super 决定）
-- ============================================================

USE admin_system;
SET NAMES utf8mb4;

-- 如果存在旧表 platform_admin_role，先迁移数据再删除
SET @tbl_exists := (
  SELECT COUNT(1)
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = 'admin_system'
    AND TABLE_NAME = 'platform_admin_role'
);

SET @sql := IF(
  @tbl_exists = 1,
  'INSERT IGNORE INTO `platform_user_role` (`user_id`, `role_id`)
     SELECT `user_id`, `role_id` FROM `platform_admin_role`',
  'SELECT ''skip migrate platform_admin_role'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  @tbl_exists = 1,
  'DROP TABLE `platform_admin_role`',
  'SELECT ''skip drop platform_admin_role'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 清理 platform_user.is_super 列（已废弃）
SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'admin_system'
    AND TABLE_NAME = 'platform_user'
    AND COLUMN_NAME = 'is_super'
);

SET @sql := IF(
  @col_exists = 1,
  'ALTER TABLE `platform_user` DROP COLUMN `is_super`',
  'SELECT ''skip drop platform_user.is_super'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
