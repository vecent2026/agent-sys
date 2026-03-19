-- ============================================================
-- tenant_role role_key 唯一索引修复
-- 用于修复早期 admin_system 库中 tenant_role 仍使用全局唯一 uk_role_key(role_key)
-- 正确约束应为租户内唯一：uk_tenant_role_key(tenant_id, role_key)
-- 执行前请先备份数据库
-- ============================================================

USE admin_system;
SET NAMES utf8mb4;

SET @tenant_role_has_legacy_role_key := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'admin_system'
    AND TABLE_NAME = 'tenant_role'
    AND INDEX_NAME = 'uk_role_key'
);

SET @tenant_role_has_scoped_role_key := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'admin_system'
    AND TABLE_NAME = 'tenant_role'
    AND INDEX_NAME = 'uk_tenant_role_key'
);

SET @tenant_role_key_scope_sql := CASE
  WHEN @tenant_role_has_legacy_role_key > 0 AND @tenant_role_has_scoped_role_key = 0 THEN
    'ALTER TABLE `tenant_role` DROP INDEX `uk_role_key`, ADD UNIQUE KEY `uk_tenant_role_key` (`tenant_id`, `role_key`)'
  WHEN @tenant_role_has_legacy_role_key = 0 AND @tenant_role_has_scoped_role_key = 0 THEN
    'ALTER TABLE `tenant_role` ADD UNIQUE KEY `uk_tenant_role_key` (`tenant_id`, `role_key`)'
  ELSE
    'SELECT 1'
END;

PREPARE tenant_role_key_scope_stmt FROM @tenant_role_key_scope_sql;
EXECUTE tenant_role_key_scope_stmt;
DEALLOCATE PREPARE tenant_role_key_scope_stmt;
