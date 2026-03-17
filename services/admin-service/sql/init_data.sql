-- ============================================================
-- admin_system 初始化数据
-- 依赖：schema.sql 已执行完毕
-- ============================================================

USE admin_system;
SET NAMES utf8mb4;

-- ──────────────────────────────────────────────
-- 1. 内置超级管理员角色
-- ──────────────────────────────────────────────

INSERT INTO `platform_role` (`id`, `role_name`, `role_key`, `is_builtin`, `is_super`, `description`)
VALUES (1, '超级管理员', 'super_admin', 1, 1, '内置超管角色，不可删除/修改，持有此角色的用户拥有全部平台权限');

-- ──────────────────────────────────────────────
-- 2. 内置 admin 账号（密码：123456）
-- ──────────────────────────────────────────────

INSERT INTO `platform_user` (`id`, `username`, `password`, `nickname`, `is_builtin`, `status`)
VALUES (1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '超级管理员', 1, 1);

-- admin 绑定到超管角色
INSERT INTO `platform_user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- ──────────────────────────────────────────────
-- 3. 默认租户
-- ──────────────────────────────────────────────

INSERT INTO `platform_tenant` (`id`, `tenant_code`, `tenant_name`, `status`, `data_version`)
VALUES (1, 'default', '默认租户', 1, 0);

-- ──────────────────────────────────────────────
-- 4. 权限节点（platform_permission）
-- scope=platform：平台端菜单/操作，不授权给租户
-- scope=tenant：租户端菜单/操作，平台可授权给各租户
-- ──────────────────────────────────────────────

-- ════════════════════════════════════
-- 平台端权限节点（scope=platform）
-- ════════════════════════════════════

-- 平台管理 根目录
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (0,'平台管理','DIR',NULL,'/platform',NULL,1,'platform');
SET @platform_dir = LAST_INSERT_ID();

-- 租户管理
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@platform_dir,'租户管理','MENU',NULL,'/platform/tenants','platform/tenant/TenantList',10,'platform');
SET @tenant_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@tenant_menu,'查看租户',  'BTN','platform:tenant:list',      NULL,NULL,1,'platform'),
(@tenant_menu,'新增租户',  'BTN','platform:tenant:add',       NULL,NULL,2,'platform'),
(@tenant_menu,'编辑租户',  'BTN','platform:tenant:edit',      NULL,NULL,3,'platform'),
(@tenant_menu,'禁用租户',  'BTN','platform:tenant:disable',   NULL,NULL,4,'platform'),
(@tenant_menu,'配置权限',  'BTN','platform:tenant:permission',NULL,NULL,5,'platform');

-- 平台用户管理
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@platform_dir,'平台用户','MENU',NULL,'/platform/users','platform/user/UserList',20,'platform');
SET @puser_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@puser_menu,'查看用户','BTN','platform:user:list',  NULL,NULL,1,'platform'),
(@puser_menu,'新增用户','BTN','platform:user:add',   NULL,NULL,2,'platform'),
(@puser_menu,'编辑用户','BTN','platform:user:edit',  NULL,NULL,3,'platform'),
(@puser_menu,'删除用户','BTN','platform:user:remove',NULL,NULL,4,'platform'),
(@puser_menu,'重置密码','BTN','platform:user:reset', NULL,NULL,5,'platform'),
(@puser_menu,'分配角色','BTN','platform:user:role',  NULL,NULL,6,'platform');

-- 平台角色管理
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@platform_dir,'平台角色','MENU',NULL,'/platform/roles','platform/role/RoleList',30,'platform');
SET @prole_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@prole_menu,'查看角色','BTN','platform:role:list',  NULL,NULL,1,'platform'),
(@prole_menu,'新增角色','BTN','platform:role:add',   NULL,NULL,2,'platform'),
(@prole_menu,'编辑角色','BTN','platform:role:edit',  NULL,NULL,3,'platform'),
(@prole_menu,'删除角色','BTN','platform:role:remove',NULL,NULL,4,'platform');

-- 权限节点管理
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@platform_dir,'权限节点','MENU',NULL,'/platform/permissions','platform/perm/PermList',40,'platform');
SET @perm_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@perm_menu,'查看权限节点','BTN','platform:perm:list',  NULL,NULL,1,'platform'),
(@perm_menu,'新增权限节点','BTN','platform:perm:add',   NULL,NULL,2,'platform'),
(@perm_menu,'编辑权限节点','BTN','platform:perm:edit',  NULL,NULL,3,'platform'),
(@perm_menu,'删除权限节点','BTN','platform:perm:remove',NULL,NULL,4,'platform');

-- 平台操作日志
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@platform_dir,'操作日志','MENU',NULL,'/platform/logs','platform/log/LogList',50,'platform');
SET @plog_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@plog_menu,'查看操作日志','BTN','platform:log:list',NULL,NULL,1,'platform');

-- ════════════════════════════════════
-- 租户端权限节点（scope=tenant）
-- ════════════════════════════════════

-- 租户功能 根目录
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (0,'租户功能','DIR',NULL,'/tenant',NULL,2,'tenant');
SET @tenant_func_dir = LAST_INSERT_ID();

-- 成员管理
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@tenant_func_dir,'成员管理','MENU',NULL,'/members','tenant/member/MemberList',10,'tenant');
SET @member_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@member_menu,'查看成员','BTN','tenant:member:list',   NULL,NULL,1,'tenant'),
(@member_menu,'添加成员','BTN','tenant:member:add',    NULL,NULL,2,'tenant'),
(@member_menu,'移除成员','BTN','tenant:member:remove', NULL,NULL,3,'tenant'),
(@member_menu,'禁用成员','BTN','tenant:member:disable',NULL,NULL,4,'tenant'),
(@member_menu,'分配角色','BTN','tenant:member:role',   NULL,NULL,5,'tenant');

-- 角色管理
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@tenant_func_dir,'角色管理','MENU',NULL,'/roles','tenant/role/RoleList',20,'tenant');
SET @trole_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@trole_menu,'查看角色','BTN','tenant:role:list',  NULL,NULL,1,'tenant'),
(@trole_menu,'新增角色','BTN','tenant:role:add',   NULL,NULL,2,'tenant'),
(@trole_menu,'编辑角色','BTN','tenant:role:edit',  NULL,NULL,3,'tenant'),
(@trole_menu,'删除角色','BTN','tenant:role:remove',NULL,NULL,4,'tenant');

-- 用户中心（目录）
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@tenant_func_dir,'用户中心','DIR',NULL,'/user-center',NULL,30,'tenant');
SET @uc_dir = LAST_INSERT_ID();

-- 用户中心 > 用户管理
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@uc_dir,'用户管理','MENU',NULL,'/app-users','tenant/appuser/AppUserList',10,'tenant');
SET @appuser_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@appuser_menu,'查看用户列表','BTN','tenant:user:list',  NULL,NULL,1,'tenant'),
(@appuser_menu,'查看用户详情','BTN','tenant:user:query', NULL,NULL,2,'tenant'),
(@appuser_menu,'编辑用户信息','BTN','tenant:user:edit',  NULL,NULL,3,'tenant'),
(@appuser_menu,'导出用户数据','BTN','tenant:user:export',NULL,NULL,4,'tenant');

-- 用户中心 > 标签管理
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@uc_dir,'标签管理','MENU',NULL,'/tags','tenant/tag/TagList',20,'tenant');
SET @tag_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@tag_menu,'查看标签',    'BTN','tenant:tag:list',    NULL,NULL,1,'tenant'),
(@tag_menu,'新增标签',    'BTN','tenant:tag:add',     NULL,NULL,2,'tenant'),
(@tag_menu,'编辑标签',    'BTN','tenant:tag:edit',    NULL,NULL,3,'tenant'),
(@tag_menu,'删除标签',    'BTN','tenant:tag:remove',  NULL,NULL,4,'tenant'),
(@tag_menu,'管理标签分类','BTN','tenant:tag:category',NULL,NULL,5,'tenant');

-- 用户中心 > 字段管理
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@uc_dir,'字段管理','MENU',NULL,'/field-def','tenant/field/FieldList',30,'tenant');
SET @field_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@field_menu,'查看自定义字段','BTN','tenant:field:list',  NULL,NULL,1,'tenant'),
(@field_menu,'新增自定义字段','BTN','tenant:field:add',   NULL,NULL,2,'tenant'),
(@field_menu,'编辑自定义字段','BTN','tenant:field:edit',  NULL,NULL,3,'tenant'),
(@field_menu,'删除自定义字段','BTN','tenant:field:remove',NULL,NULL,4,'tenant');

-- 操作日志
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@tenant_func_dir,'操作日志','MENU',NULL,'/logs','tenant/log/LogList',40,'tenant');
SET @tlog_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@tlog_menu,'查看操作日志','BTN','tenant:log:list',NULL,NULL,1,'tenant');

-- ──────────────────────────────────────────────
-- 5. 为默认租户授权全部 scope=tenant 的权限节点
-- ──────────────────────────────────────────────

INSERT INTO `tenant_permission` (`tenant_id`, `permission_id`)
SELECT 1, `id` FROM `platform_permission` WHERE `scope` = 'tenant' AND `is_deleted` = 0;
