-- 将数据库口径对齐到“超管=角色属性、权限节点=platform:perm:*”
-- 用法:
--   docker exec -i mysql mysql -uroot -proot -D admin_system < scripts/release/align_super_admin_model.sql

START TRANSACTION;

-- 1) platform_role 增补字段（MySQL 不支持 IF NOT EXISTS，使用存储过程）
DELIMITER //
CREATE PROCEDURE add_column_if_not_exists()
BEGIN
  -- 添加 is_super 字段
  IF NOT EXISTS (
    SELECT * FROM information_schema.columns 
    WHERE table_schema = DATABASE() 
    AND table_name = 'platform_role' 
    AND column_name = 'is_super'
  ) THEN
    ALTER TABLE platform_role ADD COLUMN is_super TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否超管角色：1=是，0=否';
  END IF;
  
  -- 添加 is_builtin 字段
  IF NOT EXISTS (
    SELECT * FROM information_schema.columns 
    WHERE table_schema = DATABASE() 
    AND table_name = 'platform_role' 
    AND column_name = 'is_builtin'
  ) THEN
    ALTER TABLE platform_role ADD COLUMN is_builtin TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否系统内置：1=是，0=否';
  END IF;
END//
DELIMITER ;

CALL add_column_if_not_exists();
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

-- 2) 初始角色语义（按当前内置角色键）
UPDATE platform_role
SET is_super = 1, is_builtin = 1
WHERE role_key = 'admin' AND is_deleted = 0;

UPDATE platform_role
SET is_builtin = 1
WHERE role_key IN ('admin', 'common') AND is_deleted = 0;

-- 3) 将旧口径 platform_user.is_super=1 迁移到角色关系
INSERT INTO platform_user_role (user_id, role_id)
SELECT pu.id, pr.id
FROM platform_user pu
JOIN platform_role pr ON pr.role_key = 'admin' AND pr.is_deleted = 0
WHERE pu.is_deleted = 0
  AND pu.is_super = 1
  AND NOT EXISTS (
    SELECT 1
    FROM platform_user_role pur
    WHERE pur.user_id = pu.id
      AND pur.role_id = pr.id
  );

-- 4) 权限 key 统一: platform:menu:* -> platform:perm:*
UPDATE platform_permission
SET permission_key = REPLACE(permission_key, 'platform:menu:', 'platform:perm:')
WHERE permission_key LIKE 'platform:menu:%';

-- 5) 可选：删旧字段（如需回滚窗口，可暂不执行）
-- ALTER TABLE platform_user DROP COLUMN is_super;

COMMIT;
