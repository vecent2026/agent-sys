# 01 整体架构概览

## 1. 核心概念定义

| 概念 | 说明 |
|------|------|
| **平台（Platform）** | SaaS 运营方，拥有超级管理员权限，负责创建/管理租户、配置全局权限节点 |
| **租户（Tenant）** | 由平台开通的组织/企业，有独立的用户群体和数据空间 |
| **平台用户** | 平台方管理员，存储在 `platform_user` 表，独立于租户体系 |
| **租户成员** | 普通应用用户（`app_user`），可属于一个或多个租户，通过 `user_tenant` 关联 |
| **租户管理员** | 租户内拥有管理员角色的成员，管理本租户内的角色和用户 |
| **权限节点** | 平台统一定义的功能点（菜单/按钮），租户不可新增/删除 |
| **租户角色** | 租户在平台授权范围内自定义的角色，绑定若干权限节点 |

---

## 2. 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                         用户浏览器                           │
│                                                             │
│   ┌─────────────────┐        ┌─────────────────────────┐   │
│   │   平台端 Portal  │        │     租户端 Portal        │   │
│   │ /platform/*     │        │ /tenant/* (或独立域名)    │   │
│   │ 超管专用         │        │ 租户成员使用              │   │
│   └────────┬────────┘        └───────────┬─────────────┘   │
└────────────┼────────────────────────────┼────────────────────┘
             │                            │
             ▼                            ▼
┌─────────────────────────────────────────────────────────────┐
│                     Nginx 反向代理                           │
│  /platform/* → 平台端静态资源                                │
│  /tenant/*   → 租户端静态资源                                │
│  /api/*      → 后端微服务（按路径前缀分发）                   │
└──────────────────────────┬──────────────────────────────────┘
                           │
         ┌─────────────────┴──────────────────┐
         ▼                                   ▼
┌────────────────┐              ┌─────────────────┐
│  admin-service │              │  user-service   │
│  :8081         │              │  :8082          │
│  平台/租户管理  │              │  用户/标签管理   │
│  权限/角色/日志 │              │                 │
└────────────────┘              └─────────────────┘
         │                                   │
         └───────────────────────────────────┘
                           │
                    ┌──────▼──────┐
                    │   MySQL     │
                    │  admin_db   │
                    │  user_db    │
                    └─────────────┘
```

---

## 3. 数据隔离策略：同库同表 + tenant_id

### 3.1 隔离原则

所有与租户相关的业务表均增加 `tenant_id BIGINT` 字段，通过 MyBatis-Plus 的 **租户插件（TenantLineInnerInterceptor）** 在 SQL 层自动注入和过滤，业务代码无需显式传递 `tenant_id`。

```
应用代码                 MyBatis-Plus 插件              实际 SQL
─────────────────────────────────────────────────────────────
SELECT * FROM app_user   →  自动追加 tenant_id 条件  →  SELECT * FROM app_user
WHERE status = 1                                        WHERE status = 1
                                                        AND tenant_id = {current}
```

### 3.2 哪些表需要 tenant_id

**admin_db（admin-service）**

| 表 | 是否需要 tenant_id | 说明 |
|----|--------------------|------|
| `platform_user` | ❌ | 纯平台管理员，与租户体系完全隔离 |
| `platform_tenant` | ❌ | 租户元数据表，平台级，无需隔离 |
| `platform_permission` | ❌ | 全局权限节点树，含 scope 字段，平台统一定义 |
| `platform_role` | ❌ | 平台角色，全局共享，无需 tenant_id 过滤 |
| `platform_user_role` | ❌ | 平台用户↔角色关联，平台级 |
| `platform_role_permission` | ❌ | 平台角色↔权限节点关联，平台级 |
| `tenant_role` | ✅ | 角色属于租户，需隔离（含 tenant_id 字段） |
| `tenant_permission` | ❌（手动查询） | 平台为租户授权的权限范围，按 tenant_id 过滤，不走插件 |
| `tenant_role_permission` | ❌（手动查询） | 租户角色↔权限节点映射，不走插件 |
| `tenant_user_role` | ❌（手动查询） | 用户-租户-角色三元关联，不走插件 |
| `operation_log` | ✅（is_platform 区分） | 租户操作日志含 tenant_id；平台日志 tenant_id=NULL |

**user_db（user-service）**

| 表 | 是否需要 tenant_id | 说明 |
|----|--------------------|------|
| `app_user` | ❌ | 用户是全局账号，通过 `tenant_user` 关联租户 |
| `tenant_user` | ❌（手动查询） | 用户-租户成员关系，按 user_id 查询，不走插件 |
| `tenant_tag_category` | ✅ | 标签分类属于租户 |
| `tenant_tag` | ✅ | 标签属于租户 |
| `tenant_user_tag` | ✅ | 用户-标签关联属于租户 |
| `tenant_field_def` | ✅ | 自定义字段定义属于租户 |
| `tenant_field_value` | ✅ | 字段值属于租户 |

---

## 4. 身份体系设计

### 4.1 两套身份（互不混淆）

```
平台用户 (platform_user)
  ├── 登录入口: POST /api/platform/auth/login（凭据：用户名 + 密码）
  ├── JWT 载荷: { userId, username, isPlatform: true, tenantId: null, tokenVersion: N }
  └── 权限: 全局超管或平台角色权限，不受 tenant_id 过滤

租户成员 (app_user + tenant_user)
  ├── 登录入口: POST /api/tenant/auth/login（凭据：手机号 + 密码）
  ├── JWT 载荷: { userId, mobile, isPlatform: false, tenantId: "xxx", tenantVersion: N }
  └── 权限: 当前 tenantId 下的角色权限
```

### 4.2 租户上下文传递

```
HTTP 请求链路：
  前端 → Header: Authorization: Bearer <JWT>
       → JWT 解析出 tenantId
       → 存入 ThreadLocal (TenantContext)
       → MyBatis-Plus 插件读取 TenantContext.getTenantId()
       → 自动追加 SQL 条件
  请求结束 → ThreadLocal 清理
```

---

## 5. 设计原则

1. **平台数据与租户数据物理隔离**：平台管理员使用独立表 `platform_user`，不与 `app_user` 混用
2. **用户身份全局唯一，租户成员关系多对多**：同一手机号/邮箱在全平台只有一条 `app_user` 记录
3. **权限节点只读于租户**：租户只能分配平台已授权给该租户的权限节点
4. **租户数据强隔离**：任何跨租户查询必须通过平台端超管接口，不允许租户侧绕过
5. **无感知改造**：通过 MyBatis-Plus 租户插件 + ThreadLocal 实现，业务代码改动最小

---

## 6. 跨服务 tenant 上下文传递

### 6.1 服务间调用链路

当 admin-service 调用 user-service 时（如创建租户需要注册管理员账号），需要在 HTTP Header 中传递租户上下文，不能依赖 ThreadLocal：

```
用户请求
  ↓
admin-service（从 JWT 解析 tenantId → 设置 TenantContext）
  ↓ Feign/RestTemplate 调用
user-service（从 X-Tenant-Id Header 读取 → 设置 TenantContext）
```

详细实现见 `05-api-design.md §5`。

### 6.2 异步任务中的 ThreadLocal 丢失

`@Async` 方法在新线程中执行，ThreadLocal 数据不会自动继承，需要在提交任务时显式传递：

```java
// 错误做法：直接调用 @Async 方法，内部 TenantContext.getTenantId() = null
@Async
public void asyncTask() {
    Long id = TenantContext.getTenantId(); // ❌ null
}

// 正确做法：调用前捕获，传入 lambda
Long tenantId = TenantContext.getTenantId();
asyncService.processWithTenant(tenantId, () -> {
    // 内部已设置 TenantContext
});
```

详细实现见 `02-database-design.md §7.3`。

---

## 7. Token 失效机制汇总

| 场景 | 失效方式 | 立即生效 |
|------|---------|---------|
| 用户主动登出 | jti 加入 Redis 黑名单 | ✅ |
| 平台用户改密码 | `platform_user.token_version` +1，JWT 中版本不匹配拒绝 | ✅ |
| 平台用户被禁用 | `token_version` +1 | ✅ |
| 租户被禁用 | `platform_tenant.data_version` +1，JWT 中 `tenantVersion` 不匹配拒绝 | ✅ |
| 租户成员被移除 | 下次 `user_tenant` 查询返回无记录，切换租户失败；当前 token 自然过期 | ❌（需等 token 过期） |
| token 自然过期 | accessToken 7200s 过期 | — |
