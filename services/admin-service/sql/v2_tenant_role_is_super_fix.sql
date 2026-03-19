-- ============================================================
-- tenant_role is_super 列补丁
-- 用于修复早期 admin_system 建表脚本未创建 is_super 列的问题
-- 执行前请先备份数据库
-- ============================================================

USE admin_system;
SET NAMES utf8mb4;

ALTER TABLE `tenant_role`
  ADD COLUMN IF NOT EXISTS `is_super` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否超管角色: 1=持有此角色的用户拥有全部租户权限' AFTER `is_builtin`;

UPDATE `tenant_role`
SET `is_super` = 1
WHERE `role_key` = 'tenant_admin' AND `is_super` = 0;
