# 06 实施计划

> **当前阶段**：开发环境，无生产数据。
> 直接以 `schema.sql + init_data.sql` 全量重建数据库，无需增量迁移脚本。

---

## 1. 数据库初始化（开发环境）

```bash
# 重置并重建数据库（开发环境专用，会清空全部数据）
mysql -u root -p < services/admin-service/sql/schema.sql
mysql -u root -p < services/admin-service/sql/init_data.sql
```

执行后验证：

```sql
USE admin_system;

-- 内置超管角色存在
SELECT id, role_name, is_builtin, is_super FROM platform_role;          -- 应有 super_admin

-- admin 账号存在且为内置
SELECT id, username, is_builtin, status FROM platform_user;             -- 应有 admin，is_builtin=1

-- admin 已绑定超管角色
SELECT * FROM platform_user_role;                                        -- user_id=1, role_id=1

-- 权限节点已初始化（含 platform + tenant 两类）
SELECT scope, COUNT(*) FROM platform_permission GROUP BY scope;

-- 默认租户已授权全部 tenant 权限
SELECT COUNT(*) FROM tenant_permission WHERE tenant_id = 1;
```

---

## 2. 后端实施清单

### admin-service

**认证与安全**
- [ ] `SysUser` Entity：`is_super` → `is_builtin`（超管身份改为由角色决定）
- [ ] `SysRole` Entity：增加 `is_builtin`、`is_super` 字段
- [ ] `PlatformAuthServiceImpl`：登录/刷新权限加载，超管判断改为查角色中是否有 `is_super=1`
- [ ] `UserServiceImpl`：保护逻辑从 `user.is_super` 改为 `user.is_builtin`
- [ ] `RoleServiceImpl`：保护逻辑使用 `role.is_builtin` 而非硬编码 `role_key`

**平台端权限管理**
- [ ] 权限节点列表 API 增加 `scope` 筛选参数，支持前端按 Tab 分组展示
- [ ] 角色权限树展示：超管角色（`is_super=1`）返回全量权限节点（全勾选展示，不写入关联表）

**租户管理**
- [ ] `PlatformTenantController` CRUD + 权限配置接口（为租户授权 scope=tenant 节点）
- [ ] `TenantPermissionController`：返回租户已授权权限树

**租户端**
- [ ] `TenantRoleController`：租户角色 CRUD，权限范围限制在 `tenant_permission` 内
- [ ] `TenantMemberController`：成员管理（分配角色、禁用、移除）
- [ ] 租户 JWT 失效机制：`data_version` 校验（禁用租户 / 权限变更时 +1）

### user-service

- [ ] MyBatis-Plus 租户插件：自动注入 `tenant_id` 过滤（`tenant_tag_category`、`tenant_tag` 等表）
- [ ] `InternalTenantFilter`：从 Header `X-Tenant-Id` 注入 `TenantContext`

---

## 3. 前端实施清单

**平台端**
- [ ] 权限节点管理页：增加 "平台权限 / 租户权限" Tab，分别展示 `scope=platform` 和 `scope=tenant` 节点
- [ ] 角色管理：超管角色（`is_builtin=1`）操作列显示"受保护"，权限树全勾选（只读）
- [ ] 用户管理：内置账号（`is_builtin=1`）禁用"删除/禁用/修改角色"操作

**租户端**
- [ ] 租户端 Permission Hook：从 JWT `authorities` 读取，控制菜单/按钮可见性
- [ ] 角色管理页：权限树范围限制为 `scope=tenant` 节点
- [ ] 成员管理页、操作日志页

---

## 4. 功能验证清单

**超管权限**
- [ ] admin 登录后所有平台菜单/按钮均可见，无"无权限"提示
- [ ] 超管角色在权限树中显示全部节点为已勾选，且不可编辑
- [ ] admin 账号的"删除/禁用/修改角色"入口不可用

**内置保护**
- [ ] 删除超管角色返回错误提示
- [ ] 修改超管角色名称/标识返回错误提示
- [ ] 删除/禁用 admin 账号返回错误提示

**权限隔离**
- [ ] 普通平台用户只能访问角色授权的菜单和操作
- [ ] 租户用户访问 `/api/platform/` 接口返回 403
- [ ] 租户角色分配的权限不超出平台授权给该租户的范围

**Token 失效**
- [ ] 修改密码后旧 token 立即失效
- [ ] 禁用租户后该租户所有用户下次请求被拒绝
