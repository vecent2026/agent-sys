-- 平台角色-权限关联表（若 v2 迁移未创建，需在 admin_system 库执行）
-- 执行: mysql -u root -p admin_system < platform_role_permission.sql
CREATE TABLE IF NOT EXISTS `platform_role_permission` (
  `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
  `role_id`       BIGINT NOT NULL COMMENT 'platform_role.id',
  `permission_id` BIGINT NOT NULL COMMENT 'platform_permission.id',
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台角色-权限关联';
