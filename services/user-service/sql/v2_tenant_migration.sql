-- ============================================================
-- trae_user 多租户迁移脚本 v2
-- 执行前请备份数据库！
-- ============================================================

USE trae_user;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- Step 1: 改名 + 加字段
-- ============================================================

-- user_tag_category → tenant_tag_category
RENAME TABLE user_tag_category TO tenant_tag_category;
ALTER TABLE tenant_tag_category
  ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '所属租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);
-- 移除原有唯一索引（标签分类名仅在同租户内唯一）
ALTER TABLE tenant_tag_category DROP INDEX `uk_name`;
ALTER TABLE tenant_tag_category ADD UNIQUE KEY `uk_tenant_name` (`tenant_id`, `name`);

-- user_tag → tenant_tag
RENAME TABLE user_tag TO tenant_tag;
ALTER TABLE tenant_tag
  ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '所属租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);

-- user_tag_relation → tenant_user_tag
RENAME TABLE user_tag_relation TO tenant_user_tag;
ALTER TABLE tenant_user_tag
  ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '所属租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);
-- 重建唯一键，加入 tenant_id
ALTER TABLE tenant_user_tag DROP INDEX `uk_user_tag`;
ALTER TABLE tenant_user_tag ADD UNIQUE KEY `uk_tenant_user_tag` (`tenant_id`, `user_id`, `tag_id`);

-- user_field → tenant_field_def
RENAME TABLE user_field TO tenant_field_def;
ALTER TABLE tenant_field_def
  ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '所属租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);
-- 唯一键改为租户内唯一
ALTER TABLE tenant_field_def DROP INDEX `uk_field_name`;
ALTER TABLE tenant_field_def DROP INDEX `uk_field_key`;
ALTER TABLE tenant_field_def ADD UNIQUE KEY `uk_tenant_field_name` (`tenant_id`, `field_name`);
ALTER TABLE tenant_field_def ADD UNIQUE KEY `uk_tenant_field_key` (`tenant_id`, `field_key`);

-- user_field_value → tenant_field_value
RENAME TABLE user_field_value TO tenant_field_value;
ALTER TABLE tenant_field_value
  ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '所属租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);

-- user_view 加 tenant_id（表名保持不变）
ALTER TABLE user_views
  ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '所属租户ID',
  ADD INDEX `idx_tenant` (`tenant_id`);

-- app_user 加 password 字段（租户端登录用）
ALTER TABLE app_user
  ADD COLUMN IF NOT EXISTS `password` VARCHAR(100) DEFAULT NULL COMMENT 'BCrypt密码（租户登录用）';

-- ============================================================
-- Step 2: 创建新表
-- ============================================================

-- 用户-租户成员关系
CREATE TABLE IF NOT EXISTS `tenant_user` (
  `id`        BIGINT   PRIMARY KEY AUTO_INCREMENT,
  `user_id`   BIGINT   NOT NULL COMMENT 'app_user.id',
  `tenant_id` BIGINT   NOT NULL,
  `is_admin`  TINYINT  NOT NULL DEFAULT 0 COMMENT '是否租户管理员',
  `join_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_tenant` (`user_id`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-租户成员关系';

-- ============================================================
-- Step 3: 将所有现有 app_user 归入默认租户
-- ============================================================

INSERT IGNORE INTO `tenant_user` (`user_id`, `tenant_id`, `is_admin`)
SELECT `id`, 1, 0 FROM `app_user` WHERE `is_deleted` = 0;

SET FOREIGN_KEY_CHECKS = 1;
