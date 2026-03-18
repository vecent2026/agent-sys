-- ============================================================
-- v6: 租户测试账号/角色数据（幂等）
-- 目标：补齐 tenant:log:list 与租户测试账号完整链路数据
-- 测试账号：13900000001 / 123456
-- ============================================================

SET NAMES utf8mb4;

-- 1) admin_system：确保 tenant:log:list 存在并绑定到 tenant=1 的 admin 角色
USE admin_system;
START TRANSACTION;

SET @log_parent_id := (
  SELECT id FROM platform_permission
  WHERE name = '操作日志' AND scope = 'tenant' AND is_deleted = 0
  ORDER BY id LIMIT 1
);

INSERT INTO platform_permission (
  parent_id, name, type, permission_key, path, component, sort,
  create_by, update_by, is_deleted, scope
)
SELECT COALESCE(@log_parent_id, 0), '查看租户日志', 'BTN', 'tenant:log:list', NULL, NULL, 99,
       'system', 'system', 0, 'tenant'
WHERE NOT EXISTS (
  SELECT 1 FROM platform_permission WHERE permission_key = 'tenant:log:list' AND is_deleted = 0
);

SET @tenant_log_perm_id := (
  SELECT id FROM platform_permission
  WHERE permission_key = 'tenant:log:list' AND is_deleted = 0
  ORDER BY id LIMIT 1
);

INSERT INTO tenant_permission (tenant_id, permission_id)
SELECT 1, @tenant_log_perm_id
WHERE @tenant_log_perm_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM tenant_permission
    WHERE tenant_id = 1 AND permission_id = @tenant_log_perm_id
  );

SET @tenant_admin_role_id := (
  SELECT id FROM tenant_role
  WHERE tenant_id = 1 AND role_key = 'admin' AND is_deleted = 0
  ORDER BY id LIMIT 1
);

INSERT INTO tenant_role_permission (role_id, permission_id, tenant_id)
SELECT @tenant_admin_role_id, @tenant_log_perm_id, 1
WHERE @tenant_admin_role_id IS NOT NULL
  AND @tenant_log_perm_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM tenant_role_permission
    WHERE role_id = @tenant_admin_role_id AND tenant_id = 1 AND permission_id = @tenant_log_perm_id
  );

COMMIT;

-- 2) trae_user：admin_system：补齐租户测试账号及其租户管理员角色关系
USE trae_user;
START TRANSACTION;

SET @seed_password := (
  SELECT password FROM app_user WHERE mobile = '13800138000' LIMIT 1
);

INSERT INTO app_user (
  nickname, mobile, gender, register_source, status,
  register_time, is_deleted, password
)
SELECT '租户测试账号', '13900000001', 0, 'seed', 1,
       NOW(), 0, @seed_password
WHERE NOT EXISTS (
  SELECT 1 FROM app_user WHERE mobile = '13900000001'
);

SET @tenant_test_user_id := (
  SELECT id FROM app_user WHERE mobile = '13900000001' LIMIT 1
);

INSERT INTO tenant_user (user_id, tenant_id, is_admin, join_time, status)
SELECT @tenant_test_user_id, 1, 0, NOW(), 1
WHERE @tenant_test_user_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM tenant_user WHERE user_id = @tenant_test_user_id AND tenant_id = 1
  );

COMMIT;

USE admin_system;
START TRANSACTION;

SET @tenant_admin_role_id2 := (
  SELECT id FROM tenant_role
  WHERE tenant_id = 1 AND role_key = 'admin' AND is_deleted = 0
  ORDER BY id LIMIT 1
);

INSERT INTO tenant_user_role (user_id, tenant_id, role_id)
SELECT @tenant_test_user_id, 1, @tenant_admin_role_id2
WHERE @tenant_test_user_id IS NOT NULL
  AND @tenant_admin_role_id2 IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM tenant_user_role
    WHERE user_id = @tenant_test_user_id AND tenant_id = 1 AND role_id = @tenant_admin_role_id2
  );

COMMIT;
