-- 多租户超管口径/权限口径发布前检查
-- 用法:
--   docker exec -i mysql mysql -uroot -proot -D admin_system < scripts/release/tenant_alignment_check.sql

SELECT '=== 1) 核心字段存在性 ===' AS section;
SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = 'admin_system'
  AND (
    (table_name = 'platform_role' AND column_name IN ('is_super', 'is_builtin')) OR
    (table_name = 'platform_user' AND column_name IN ('token_version'))
  )
ORDER BY table_name, column_name;

SELECT '=== 2) 旧字段遗留（platform_user.is_super） ===' AS section;
SELECT
  CASE WHEN COUNT(*) > 0 THEN 'FOUND' ELSE 'NOT_FOUND' END AS old_column_status,
  COUNT(*) AS cnt
FROM information_schema.columns
WHERE table_schema = 'admin_system'
  AND table_name = 'platform_user'
  AND column_name = 'is_super';

SELECT '=== 3) 权限 key 前缀分布（应为 perm>0, menu=0） ===' AS section;
SELECT
  SUM(permission_key LIKE 'platform:perm:%') AS perm_keys,
  SUM(permission_key LIKE 'platform:menu:%') AS menu_keys,
  COUNT(*) AS total_platform_keys
FROM platform_permission
WHERE permission_key LIKE 'platform:%';

SELECT '=== 4) 超管角色与内置角色统计 ===' AS section;
SET @has_role_is_super := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = 'admin_system'
    AND table_name = 'platform_role'
    AND column_name = 'is_super'
);
SET @has_role_is_builtin := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = 'admin_system'
    AND table_name = 'platform_role'
    AND column_name = 'is_builtin'
);
SET @sql_role_stats := IF(
  @has_role_is_super = 1 AND @has_role_is_builtin = 1,
  'SELECT SUM(is_super = 1) AS super_roles, SUM(is_builtin = 1) AS builtin_roles, SUM(is_super = 1 AND is_builtin = 1) AS super_and_builtin_roles FROM platform_role WHERE is_deleted = 0',
  'SELECT ''MISSING_COLUMNS'' AS super_roles, ''MISSING_COLUMNS'' AS builtin_roles, ''MISSING_COLUMNS'' AS super_and_builtin_roles'
);
PREPARE stmt_role_stats FROM @sql_role_stats;
EXECUTE stmt_role_stats;
DEALLOCATE PREPARE stmt_role_stats;

SELECT '=== 5) 用户是否通过角色持有超管身份 ===' AS section;
SET @sql_user_super := IF(
  @has_role_is_super = 1,
  'SELECT pur.user_id, GROUP_CONCAT(DISTINCT pr.role_key ORDER BY pr.role_key) AS role_keys, MAX(pr.is_super) AS has_super_role FROM platform_user_role pur JOIN platform_role pr ON pr.id = pur.role_id GROUP BY pur.user_id ORDER BY pur.user_id',
  'SELECT NULL AS user_id, ''MISSING platform_role.is_super'' AS role_keys, NULL AS has_super_role'
);
PREPARE stmt_user_super FROM @sql_user_super;
EXECUTE stmt_user_super;
DEALLOCATE PREPARE stmt_user_super;

SELECT '=== 6) 发布建议判定（人工核对） ===' AS section;
SELECT
  CASE
    WHEN SUM(permission_key LIKE 'platform:menu:%') = 0 THEN 'OK'
    ELSE 'NEED_FIX'
  END AS permission_key_status
FROM platform_permission
WHERE permission_key LIKE 'platform:%';
