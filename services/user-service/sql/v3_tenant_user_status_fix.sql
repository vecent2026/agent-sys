-- ============================================================
-- tenant_user status 列补丁
-- 用于修复早期 v2 迁移脚本未创建 status 列的问题
-- 执行前请先备份数据库
-- ============================================================

USE trae_user;
SET NAMES utf8mb4;

SET @tenant_user_status_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'trae_user'
    AND TABLE_NAME = 'tenant_user'
    AND COLUMN_NAME = 'status'
);

SET @tenant_user_status_alter_sql := IF(
  @tenant_user_status_exists = 0,
  'ALTER TABLE `tenant_user` ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT ''成员状态: 1正常 0禁用'' AFTER `is_admin`',
  'SELECT 1'
);

PREPARE tenant_user_status_stmt FROM @tenant_user_status_alter_sql;
EXECUTE tenant_user_status_stmt;
DEALLOCATE PREPARE tenant_user_status_stmt;

UPDATE `tenant_user`
SET `status` = 1
WHERE `status` IS NULL;
