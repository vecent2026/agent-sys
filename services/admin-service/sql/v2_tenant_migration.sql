-- ============================================================
-- admin_system 多租户迁移脚本 v2
-- 执行前请备份数据库！
-- ============================================================

USE admin_system;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- Step 1: 改名现有表
-- ============================================================

-- sys_user → platform_user（加 token_version, is_super）
RENAME TABLE sys_user TO platform_user;
ALTER TABLE platform_user
  ADD COLUMN `token_version` INT NOT NULL DEFAULT 0 COMMENT 'Token 版本，改密/禁用时+1',
  ADD COLUMN `is_super` TINYINT NOT NULL DEFAULT 0 COMMENT '是否超管 0=否 1=是';

-- sys_permission → platform_permission（加 scope）
RENAME TABLE sys_permission TO platform_permission;
ALTER TABLE platform_permission
  ADD COLUMN `scope` VARCHAR(20) NOT NULL DEFAULT 'tenant'
    COMMENT 'platform=平台专属; tenant=可授权给租户';

-- sys_role → tenant_role（加 tenant_id，初始所有角色归属默认租户 id=1）
RENAME TABLE sys_role TO tenant_role;
ALTER TABLE tenant_role
  ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '所属租户ID（默认租户=1）',
  ADD INDEX `idx_tenant_id` (`tenant_id`);

-- sys_user_role → platform_user_role
RENAME TABLE sys_user_role TO platform_user_role;

-- sys_role_permission → platform_role_permission
-- 注意：该表在代码中被 TenantRolePermission 实体使用（@TableName("tenant_role_permission")）
-- 此处改名为 tenant_role_permission 以与实体一致
RENAME TABLE sys_role_permission TO tenant_role_permission;
ALTER TABLE tenant_role_permission
  ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '所属租户ID',
  ADD INDEX `idx_tenant_id` (`tenant_id`);

-- ============================================================
-- Step 2: 更新权限 key 前缀
-- ============================================================

-- 系统管理相关 → platform scope
UPDATE platform_permission
  SET scope = 'platform',
      permission_key = REPLACE(permission_key, 'sys:', 'platform:')
WHERE permission_key LIKE 'sys:user%'
   OR permission_key LIKE 'sys:role%'
   OR permission_key LIKE 'sys:perm%'
   OR permission_key LIKE 'sys:log%'
   OR permission_key LIKE 'sys:menu%';

-- 应用用户/标签/字段 → tenant scope
UPDATE platform_permission
  SET scope = 'tenant',
      permission_key = REPLACE(permission_key, 'sys:', 'tenant:')
WHERE permission_key LIKE 'sys:app%'
   OR permission_key LIKE 'sys:tag%'
   OR permission_key LIKE 'sys:field%';

-- ============================================================
-- Step 3: 创建新表
-- ============================================================

-- 平台角色（区别于租户角色，平台管理员使用；初始为空，超管用 is_super=1 标识）
CREATE TABLE IF NOT EXISTS `platform_role` (
  `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
  `role_name`   VARCHAR(50)  NOT NULL COMMENT '角色名称',
  `role_key`    VARCHAR(50)  NOT NULL COMMENT '角色标识',
  `description` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0,
  UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台角色表';

-- 平台用户-角色关联（platform_user ↔ platform_role）
-- 原 platform_user_role 表存的是 platform_user ↔ tenant_role 关系
-- 新建此表用于平台角色分配
-- 注：代码中 SysUserRoleMapper 的 @TableName 已改为 platform_user_role，
--     TenantMemberController 使用 TenantUserRoleMapper (tenant_user_role)，
--     此处新表命名为 platform_admin_role 避免冲突
CREATE TABLE IF NOT EXISTS `platform_admin_role` (
  `id`      BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT 'platform_user.id',
  `role_id` BIGINT NOT NULL COMMENT 'platform_role.id',
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台用户-平台角色关联';

-- 租户元数据
CREATE TABLE IF NOT EXISTS `platform_tenant` (
  `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
  `tenant_code`  VARCHAR(64)  NOT NULL UNIQUE COMMENT '租户唯一标识',
  `tenant_name`  VARCHAR(128) NOT NULL COMMENT '租户名称',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
  `expire_time`  DATETIME     NULL     COMMENT 'NULL=永不过期',
  `max_users`    INT          NOT NULL DEFAULT 100,
  `data_version` INT          NOT NULL DEFAULT 0 COMMENT '禁用/配置变更时+1，即时失效JWT',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户元数据';

-- 平台对租户的权限授权
CREATE TABLE IF NOT EXISTS `tenant_permission` (
  `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
  `tenant_id`     BIGINT NOT NULL,
  `permission_id` BIGINT NOT NULL,
  `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_tenant_perm` (`tenant_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台对租户的权限授权';

-- 用户-租户-角色三元表
CREATE TABLE IF NOT EXISTS `tenant_user_role` (
  `id`        BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`   BIGINT NOT NULL COMMENT 'app_user.id（跨库，无FK）',
  `tenant_id` BIGINT NOT NULL,
  `role_id`   BIGINT NOT NULL COMMENT 'tenant_role.id',
  UNIQUE KEY `uk_user_tenant_role` (`user_id`, `tenant_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户在租户内的角色';

-- 操作日志
CREATE TABLE IF NOT EXISTS `operation_log` (
  `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
  `tenant_id`   BIGINT       NULL COMMENT '租户操作时填写；平台操作为 NULL',
  `user_id`     BIGINT       NOT NULL,
  `is_platform` TINYINT      NOT NULL DEFAULT 0 COMMENT '1=平台操作日志',
  `module`      VARCHAR(64)  NULL,
  `action`      VARCHAR(128) NOT NULL,
  `method`      VARCHAR(10)  NULL,
  `url`         VARCHAR(256) NULL,
  `params`      TEXT         NULL,
  `result`      TINYINT      NULL COMMENT '1=成功 0=失败',
  `error_msg`   VARCHAR(512) NULL,
  `ip`          VARCHAR(64)  NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

-- ============================================================
-- Step 4: 初始化数据
-- ============================================================

-- 插入默认租户
INSERT IGNORE INTO `platform_tenant` (`id`, `tenant_code`, `tenant_name`, `status`, `data_version`)
VALUES (1, 'default', '默认租户', 1, 0);

-- 超管账号标记（id=1 的 platform_user）
UPDATE `platform_user` SET `is_super` = 1 WHERE `id` = 1;

-- 为默认租户授权所有 tenant-scope 权限
INSERT IGNORE INTO `tenant_permission` (`tenant_id`, `permission_id`)
SELECT 1, `id` FROM `platform_permission` WHERE `scope` = 'tenant' AND `is_deleted` = 0;

SET FOREIGN_KEY_CHECKS = 1;
