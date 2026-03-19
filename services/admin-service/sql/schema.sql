-- ============================================================
-- admin_system 完整建表脚本（多租户架构，开发环境全量重建）
-- 执行前请确保数据库已创建或执行第一行 CREATE DATABASE
-- ============================================================

CREATE DATABASE IF NOT EXISTS admin_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE admin_system;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ──────────────────────────────────────────────
-- 平台层：platform_user / platform_role / platform_permission
-- ──────────────────────────────────────────────

DROP TABLE IF EXISTS `platform_role_permission`;
DROP TABLE IF EXISTS `platform_user_role`;
DROP TABLE IF EXISTS `platform_user`;
DROP TABLE IF EXISTS `platform_role`;
DROP TABLE IF EXISTS `platform_permission`;

CREATE TABLE `platform_permission` (
  `id`             BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id`      BIGINT(20)   NOT NULL DEFAULT 0      COMMENT '父节点ID，0=根',
  `name`           VARCHAR(50)  NOT NULL                COMMENT '节点名称',
  `type`           VARCHAR(20)  NOT NULL                COMMENT '类型: DIR/MENU/BTN',
  `permission_key` VARCHAR(100) DEFAULT NULL            COMMENT '权限标识（BTN类型必填，唯一）',
  `path`           VARCHAR(200) DEFAULT NULL            COMMENT '路由地址（MENU/DIR填写）',
  `component`      VARCHAR(200) DEFAULT NULL            COMMENT '前端组件路径（MENU填写）',
  `sort`           INT(11)      NOT NULL DEFAULT 0      COMMENT '同级排序号',
  `scope`          VARCHAR(20)  NOT NULL DEFAULT 'tenant' COMMENT 'platform=平台专属; tenant=可授权给租户',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by`      VARCHAR(50)  DEFAULT NULL,
  `update_by`      VARCHAR(50)  DEFAULT NULL,
  `is_deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_key` (`permission_key`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_scope` (`scope`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限节点表（平台统一维护，scope区分归属）';

CREATE TABLE `platform_role` (
  `id`          BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_name`   VARCHAR(50)  NOT NULL               COMMENT '角色名称',
  `role_key`    VARCHAR(50)  NOT NULL               COMMENT '角色标识（唯一）',
  `is_builtin`  TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '是否内置角色: 1=内置（不可删除/修改）',
  `is_super`    TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '是否超管角色: 1=持有此角色的用户拥有全部平台权限',
  `description` VARCHAR(200) DEFAULT NULL           COMMENT '描述',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by`   VARCHAR(50)  DEFAULT NULL,
  `update_by`   VARCHAR(50)  DEFAULT NULL,
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台角色表';

CREATE TABLE `platform_user` (
  `id`              BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`        VARCHAR(50)  NOT NULL               COMMENT '登录用户名',
  `password`        VARCHAR(100) NOT NULL               COMMENT '密码（BCrypt加密）',
  `nickname`        VARCHAR(50)  NOT NULL               COMMENT '昵称',
  `mobile`          VARCHAR(20)  DEFAULT NULL           COMMENT '手机号',
  `email`           VARCHAR(50)  DEFAULT NULL           COMMENT '邮箱',
  `is_builtin`      TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '是否内置账号: 1=内置（不可删除/禁用/修改角色）',
  `status`          TINYINT(1)   NOT NULL DEFAULT 1     COMMENT '状态: 1启用 0禁用',
  `token_version`   INT(11)      NOT NULL DEFAULT 0     COMMENT 'Token版本号，改密/禁用时+1令旧token立即失效',
  `last_login_time` DATETIME     DEFAULT NULL,
  `last_login_ip`   VARCHAR(50)  DEFAULT NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by`       VARCHAR(50)  DEFAULT NULL,
  `update_by`       VARCHAR(50)  DEFAULT NULL,
  `is_deleted`      TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台管理员用户表';

CREATE TABLE `platform_user_role` (
  `id`          BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT(20)  NOT NULL               COMMENT '平台用户ID（platform_user.id）',
  `role_id`     BIGINT(20)  NOT NULL               COMMENT '平台角色ID（platform_role.id）',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台用户-角色关联表';

CREATE TABLE `platform_role_permission` (
  `id`            BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id`       BIGINT(20)  NOT NULL               COMMENT '平台角色ID（platform_role.id）',
  `permission_id` BIGINT(20)  NOT NULL               COMMENT '权限节点ID（platform_permission.id）',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台角色-权限关联表';

-- ──────────────────────────────────────────────
-- 租户层：platform_tenant / tenant_role / tenant_*
-- ──────────────────────────────────────────────

DROP TABLE IF EXISTS `tenant_user_role`;
DROP TABLE IF EXISTS `tenant_role_permission`;
DROP TABLE IF EXISTS `tenant_permission`;
DROP TABLE IF EXISTS `tenant_role`;
DROP TABLE IF EXISTS `platform_tenant`;

CREATE TABLE `platform_tenant` (
  `id`            BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_code`   VARCHAR(64)  NOT NULL               COMMENT '租户唯一标识（英文小写，不可修改）',
  `tenant_name`   VARCHAR(128) NOT NULL               COMMENT '租户名称',
  `status`        TINYINT(1)   NOT NULL DEFAULT 1     COMMENT '状态: 1启用 0禁用',
  `expire_time`   DATETIME     DEFAULT NULL           COMMENT '过期时间，NULL=永久有效',
  `max_users`     INT(11)      NOT NULL DEFAULT 100   COMMENT '最大用户数',
  `description`   VARCHAR(500) DEFAULT NULL           COMMENT '租户描述',
  `contact_name`  VARCHAR(64)  DEFAULT NULL           COMMENT '联系人姓名',
  `contact_phone` VARCHAR(32)  DEFAULT NULL           COMMENT '联系电话',
  `contact_email` VARCHAR(128) DEFAULT NULL           COMMENT '联系邮箱',
  `data_version`  INT(11)      NOT NULL DEFAULT 0     COMMENT '配置版本号，禁用/权限变更时+1令租户JWT立即失效',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by`     VARCHAR(50)  DEFAULT NULL,
  `update_by`     VARCHAR(50)  DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户元数据表';

CREATE TABLE `tenant_permission` (
  `id`            BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`     BIGINT(20)  NOT NULL               COMMENT '租户ID（platform_tenant.id）',
  `permission_id` BIGINT(20)  NOT NULL               COMMENT '权限节点ID（scope=tenant的 platform_permission.id）',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_perm` (`tenant_id`, `permission_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台对租户的权限授权表（租户可用权限范围）';

CREATE TABLE `tenant_role` (
  `id`          BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`   BIGINT(20)   NOT NULL               COMMENT '所属租户ID（platform_tenant.id）',
  `role_name`   VARCHAR(50)  NOT NULL               COMMENT '角色名称',
  `role_key`    VARCHAR(50)  NOT NULL               COMMENT '角色标识',
  `is_builtin`  TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '是否内置角色: 1=租户初始化时创建（不可删除）',
  `is_super`    TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '是否超管角色: 1=持有此角色的用户拥有全部租户权限',
  `description` VARCHAR(200) DEFAULT NULL           COMMENT '描述',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by`   VARCHAR(50)  DEFAULT NULL,
  `update_by`   VARCHAR(50)  DEFAULT NULL,
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_role_key` (`tenant_id`, `role_key`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户角色表';

CREATE TABLE `tenant_role_permission` (
  `id`            BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`     BIGINT(20)  NOT NULL               COMMENT '租户ID（冗余，便于按租户查询和缓存失效）',
  `role_id`       BIGINT(20)  NOT NULL               COMMENT '租户角色ID（tenant_role.id）',
  `permission_id` BIGINT(20)  NOT NULL               COMMENT '权限节点ID（必须在 tenant_permission 授权范围内）',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  KEY `idx_tenant_role` (`tenant_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户角色-权限关联表';

CREATE TABLE `tenant_user_role` (
  `id`          BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT(20)  NOT NULL               COMMENT '应用用户ID（app_user.id，跨库无FK）',
  `tenant_id`   BIGINT(20)  NOT NULL               COMMENT '租户ID（platform_tenant.id）',
  `role_id`     BIGINT(20)  NOT NULL               COMMENT '租户角色ID（tenant_role.id）',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tenant_role` (`user_id`, `tenant_id`, `role_id`),
  KEY `idx_user_tenant` (`user_id`, `tenant_id`),
  KEY `idx_tenant_role` (`tenant_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-租户-角色三元关联表';

-- ──────────────────────────────────────────────
-- 操作日志
-- ──────────────────────────────────────────────

DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
  `id`          BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`   BIGINT(20)   DEFAULT NULL           COMMENT '租户ID，平台操作为NULL',
  `user_id`     BIGINT(20)   NOT NULL               COMMENT '操作用户ID',
  `is_platform` TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '1=平台操作 0=租户操作',
  `module`      VARCHAR(64)  DEFAULT NULL           COMMENT '模块名称',
  `action`      VARCHAR(128) NOT NULL               COMMENT '操作描述',
  `method`      VARCHAR(10)  DEFAULT NULL           COMMENT 'HTTP方法',
  `url`         VARCHAR(256) DEFAULT NULL           COMMENT '请求URL',
  `params`      TEXT         DEFAULT NULL           COMMENT '请求参数',
  `result`      TINYINT(1)   DEFAULT NULL           COMMENT '1=成功 0=失败',
  `error_msg`   VARCHAR(512) DEFAULT NULL           COMMENT '失败原因',
  `ip`          VARCHAR(64)  DEFAULT NULL           COMMENT '来源IP',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_time` (`tenant_id`, `create_time`),
  KEY `idx_platform_time` (`is_platform`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

SET FOREIGN_KEY_CHECKS = 1;
