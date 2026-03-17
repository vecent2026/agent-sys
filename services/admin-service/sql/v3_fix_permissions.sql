-- ============================================================
-- admin_system 权限修复脚本 v3
-- 修复内容：
--   1. 补充缺失的 platform:tenant:* 权限节点
--   2. 将 platform:tenant:* 授权给超级管理员角色（role_id=1）
--   3. 为 tenant_user 表增加 status / invite_by 字段
-- ============================================================

USE admin_system;
SET NAMES utf8mb4;

-- ============================================================
-- Step 1: 新增租户管理权限目录及按钮节点
-- ============================================================

-- 插入"租户管理"目录节点（parent_id=0 表示顶级目录）
INSERT IGNORE INTO `platform_permission` (`id`, `parent_id`, `name`, `type`, `permission_key`, `path`, `sort`, `scope`, `is_deleted`, `create_time`, `update_time`)
VALUES (43, 0, '租户管理', 'DIR', NULL, '/tenants', 5, 'platform', 0, NOW(), NOW());

-- 插入租户管理操作按钮
INSERT IGNORE INTO `platform_permission` (`parent_id`, `name`, `type`, `permission_key`, `sort`, `scope`, `is_deleted`, `create_time`, `update_time`)
VALUES
  (43, '租户列表', 'BTN', 'platform:tenant:list',   1, 'platform', 0, NOW(), NOW()),
  (43, '租户详情', 'BTN', 'platform:tenant:query',  2, 'platform', 0, NOW(), NOW()),
  (43, '新增租户', 'BTN', 'platform:tenant:add',    3, 'platform', 0, NOW(), NOW()),
  (43, '修改租户', 'BTN', 'platform:tenant:edit',   4, 'platform', 0, NOW(), NOW()),
  (43, '删除租户', 'BTN', 'platform:tenant:remove', 5, 'platform', 0, NOW(), NOW());

-- ============================================================
-- Step 2: 将 platform:tenant:* 授权给超级管理员角色（role_id=1）
-- tenant_role_permission 的 tenant_id 填 1（默认租户）
-- ============================================================

INSERT IGNORE INTO `tenant_role_permission` (`role_id`, `permission_id`, `tenant_id`)
SELECT 1, p.`id`, 1
FROM `platform_permission` p
WHERE p.`permission_key` IN (
  'platform:tenant:list',
  'platform:tenant:query',
  'platform:tenant:add',
  'platform:tenant:edit',
  'platform:tenant:remove'
);

-- ============================================================
-- Step 3: 为 trae_user.tenant_user 补充 status / invite_by 字段
--         status: 1=正常, 0=已禁用（租户内禁用该成员）
-- ============================================================

USE trae_user;

ALTER TABLE `tenant_user`
  ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '成员状态: 1=正常 0=已禁用',
  ADD COLUMN `invite_by` BIGINT DEFAULT NULL COMMENT '邀请人 user_id（NULL 表示自注册/平台直接分配）';

ALTER TABLE `tenant_user`
  ADD INDEX `idx_tenant_status` (`tenant_id`, `status`);

-- ============================================================
-- 完成
-- ============================================================
