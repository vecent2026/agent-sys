-- ============================================================
-- admin_system 增量迁移脚本 v2（幂等）
-- 目标：补齐 platform_user.is_builtin 字段，兼容旧库结构
-- ============================================================

USE admin_system;
SET NAMES utf8mb4;

SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'admin_system'
    AND TABLE_NAME = 'platform_user'
    AND COLUMN_NAME = 'is_builtin'
);

SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE `platform_user` ADD COLUMN `is_builtin` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否内置账号: 1=内置（不可删除/禁用/修改角色）'' AFTER `token_version`',
  'SELECT ''platform_user.is_builtin already exists'' AS msg'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
