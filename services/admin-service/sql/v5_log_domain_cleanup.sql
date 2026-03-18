-- ============================================================
-- v5: 操作日志权限域清理（不做迁移，仅清除历史错误绑定）
-- 目标：
-- 1) 租户侧统一使用 tenant:log:list
-- 2) 清除 tenant_* 关联中误绑的 platform:log:list
-- ============================================================

USE admin_system;
SET NAMES utf8mb4;

START TRANSACTION;

-- 1) 删除租户可用权限范围中，误绑的平台日志权限
DELETE tp
FROM tenant_permission tp
JOIN platform_permission p ON p.id = tp.permission_id
WHERE p.permission_key = 'platform:log:list';

-- 2) 删除租户角色权限中，误绑的平台日志权限
DELETE trp
FROM tenant_role_permission trp
JOIN platform_permission p ON p.id = trp.permission_id
WHERE p.permission_key = 'platform:log:list';

-- 3) 清理异常定义：如果存在 tenant scope 却使用 platform:log:list 的脏权限节点，一并删除
DELETE FROM platform_permission
WHERE permission_key = 'platform:log:list'
  AND scope = 'tenant';

COMMIT;

-- 说明：
-- - 本脚本不自动给租户角色补 tenant:log:list（按策略不做迁移兼容）。
-- - 执行后请在平台端重新为租户/角色显式配置 tenant:log:list。
