USE admin_system;

ALTER TABLE sys_role_permission
DROP PRIMARY KEY,
ADD COLUMN id bigint(20) NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST,
ADD UNIQUE KEY uk_role_permission (role_id, permission_id);
