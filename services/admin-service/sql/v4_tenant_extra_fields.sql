-- ============================================================
-- admin_system 租户扩展字段 v4
-- 修复内容：
--   1. platform_tenant 新增 description / contact_name / contact_phone / contact_email
-- ============================================================

USE admin_system;
SET NAMES utf8mb4;

ALTER TABLE `platform_tenant`
  ADD COLUMN `description`   VARCHAR(500) DEFAULT NULL COMMENT '租户描述',
  ADD COLUMN `contact_name`  VARCHAR(64)  DEFAULT NULL COMMENT '联系人姓名',
  ADD COLUMN `contact_phone` VARCHAR(32)  DEFAULT NULL COMMENT '联系电话',
  ADD COLUMN `contact_email` VARCHAR(128) DEFAULT NULL COMMENT '联系邮箱';

-- ============================================================
-- 完成
-- ============================================================
