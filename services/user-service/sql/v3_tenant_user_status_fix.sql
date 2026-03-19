-- ============================================================
-- tenant_user status 列补丁
-- 用于修复早期 v2 迁移脚本未创建 status 列的问题
-- 执行前请先备份数据库
-- ============================================================

USE trae_user;
SET NAMES utf8mb4;

ALTER TABLE `tenant_user`
  ADD COLUMN IF NOT EXISTS `status` TINYINT NOT NULL DEFAULT 1 COMMENT '成员状态: 1正常 0禁用' AFTER `is_admin`;

UPDATE `tenant_user`
SET `status` = 1
WHERE `status` IS NULL;
