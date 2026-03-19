-- ============================================================
-- tenant_role role_key 唯一索引修复
-- 用于修复早期 admin_system 库中 tenant_role 仍使用全局唯一 uk_role_key(role_key)
-- 正确约束应为租户内唯一：uk_tenant_role_key(tenant_id, role_key)
-- 执行前请先备份数据库
-- ============================================================

USE admin_system;
SET NAMES utf8mb4;

ALTER TABLE `tenant_role`
  DROP INDEX `uk_role_key`,
  ADD UNIQUE KEY `uk_tenant_role_key` (`tenant_id`, `role_key`);
