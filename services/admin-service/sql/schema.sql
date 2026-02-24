CREATE DATABASE IF NOT EXISTS admin_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE admin_system;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码',
  `nickname` varchar(50) NOT NULL COMMENT '昵称',
  `mobile` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(50) DEFAULT NULL COMMENT '邮箱',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '状态(1:启用, 0:禁用)',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(50) DEFAULT NULL COMMENT '最后登录IP',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0:正常, 1:删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_mobile` (`mobile`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_name` varchar(50) NOT NULL COMMENT '角色名称',
  `role_key` varchar(50) NOT NULL COMMENT '角色标识',
  `description` varchar(200) DEFAULT NULL COMMENT '描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0:正常, 1:删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ----------------------------
-- Table structure for sys_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '父ID',
  `name` varchar(50) NOT NULL COMMENT '节点名称',
  `type` varchar(20) NOT NULL COMMENT '类型(DIR/MENU/BTN)',
  `permission_key` varchar(100) DEFAULT NULL COMMENT '权限标识',
  `path` varchar(200) DEFAULT NULL COMMENT '路由地址',
  `component` varchar(200) DEFAULT NULL COMMENT '组件路径',
  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0:正常, 1:删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_key` (`permission_key`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- ----------------------------
-- Table structure for sys_role_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  `permission_id` bigint(20) NOT NULL COMMENT '权限ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';


SET FOREIGN_KEY_CHECKS = 1;
