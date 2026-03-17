# 05 API 接口设计

## 1. API 路径规范

| 前缀 | 说明 | 目标服务 |
|------|------|---------|
| `/api/platform/auth/` | 平台端认证 | admin-service |
| `/api/platform/` | 平台端业务接口，仅 JWT isPlatform=true 可访问 | admin-service |
| `/api/tenant/auth/` | 租户端认证 | admin-service |
| `/api/v1/app-users` | 用户中心（保持路径兼容） | user-service |
| `/api/v1/user-tags` 等 | user-service 现有接口（保持兼容） | user-service |
| `/api/roles` | 角色管理（admin-service，自动注入 tenant_id） | admin-service |

---

## 2. 认证接口详细设计

### 2.1 平台端登录

```
POST /api/platform/auth/login

Request:
{
  "username": "admin",
  "password": "Admin@123"
}

Response 成功:
{
  "code": 200,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200,
    "user": {
      "id": 1,
      "username": "admin",
      "nickname": "超级管理员",
      "isSuper": true,
      "permissions": ["platform:tenant:list", "platform:user:list", ...]
    }
  }
}

JWT Payload（accessToken）:
{
  "userId": 1,
  "username": "admin",
  "isPlatform": true,
  "isSuper": true,
  "tokenVersion": 0,        // platform_user.token_version，用于密码修改后失效
  "exp": 1234567890
}
```

Response 失败（账号禁用）：
```json
{ "code": 401, "message": "账号已被禁用" }
```

---

### 2.2 租户端登录

```
POST /api/tenant/auth/login

Request:
{
  "mobile": "13800000001",   // 手机号（唯一登录凭据，不支持邮箱）
  "password": "xxx"
}

Response A（用户只属于 1 个未禁用租户，直接签发 token）:
{
  "code": 200,
  "data": {
    "loginType": "DIRECT",
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "expiresIn": 7200,
    "tenantId": 1,
    "tenantName": "天南大陆",
    "tenantLogo": "https://...",
    "user": {
      "id": 100,
      "nickname": "张三",
      "avatar": "https://..."
    }
  }
}

Response B（用户属于多个未禁用租户，需要选择）:
{
  "code": 200,
  "data": {
    "loginType": "SELECT_TENANT",
    "preToken": "eyJ...",    // 临时 token（见 §2.3），有效期 5 分钟
    "tenants": [
      // 注意：只返回 status=1（未禁用）的租户，禁用租户不出现在列表中
      { "id": 1, "name": "天南大陆", "logo": "https://...", "memberCount": 5 },
      { "id": 2, "name": "租户B",    "logo": "https://...", "memberCount": 12 }
    ]
  }
}

Response C（用户不属于任何未禁用租户）:
{
  "code": 403,
  "message": "您尚未加入任何租户，请联系管理员"
}
```

---

### 2.3 preToken 设计

preToken 是登录后「选择租户」阶段的临时凭证，安全性要求高于普通 token。

**preToken JWT Payload**：
```json
{
  "userId": 100,
  "type": "PRE_TOKEN",
  "exp": <当前时间 + 5分钟>   // 仅有效 5 分钟，超时需重新登录
}
```

**preToken 使用限制**：
- 只能访问 `POST /api/tenant/auth/select-tenant` 一个接口
- 在 JwtAuthenticationFilter 中识别 `type=PRE_TOKEN`，拦截其他路径并返回 401
- 使用一次后立即在 Redis 中记录为已用（防止重放）

```java
// preToken 拦截逻辑
if ("PRE_TOKEN".equals(tokenType)) {
    String requestUri = request.getRequestURI();
    if (!"/api/tenant/auth/select-tenant".equals(requestUri)) {
        response.sendError(401, "临时 Token 仅可用于选择租户");
        return;
    }
    // 检查是否已使用
    if (redisTemplate.hasKey("used_pretoken:" + jti)) {
        response.sendError(401, "临时 Token 已失效");
        return;
    }
}
```

---

### 2.4 选择租户（登录后）

```
POST /api/tenant/auth/select-tenant
Authorization: Bearer <preToken>

Request:
{
  "tenantId": 1
}

校验逻辑：
  1. 验证 preToken 有效且 type=PRE_TOKEN
  2. 校验 userId 在 tenant_user 中确实属于该 tenantId 且 status=1
  3. 校验 platform_tenant.status=1（租户未被禁用）
  4. 在 Redis 中标记 preToken 已使用
  5. 签发正式 accessToken + refreshToken

Response:
{
  "code": 200,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "expiresIn": 7200,
    "tenantId": 1,
    "tenantName": "天南大陆"
  }
}
```

---

### 2.5 切换租户（已登录状态）

```
POST /api/tenant/auth/switch-tenant
Authorization: Bearer <accessToken>

Request:
{
  "tenantId": 2
}

校验逻辑（与 select-tenant 一致，但使用正式 token）:
  1. 验证当前 accessToken 有效
  2. 从 token 中取 userId，校验该用户属于目标 tenantId
  3. 校验目标租户状态正常

Response:
{
  "code": 200,
  "data": {
    "accessToken": "eyJ...",   // 新 token，tenantId=2
    "refreshToken": "eyJ...",
    "tenantId": 2,
    "tenantName": "租户B"
  }
}
```

---

### 2.6 刷新 Token

```
POST /api/tenant/auth/refresh
（平台端：POST /api/platform/auth/refresh）

Request Header: Authorization: Bearer <refreshToken>

Response:
{
  "code": 200,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",    // 滚动刷新
    "expiresIn": 7200
  }
}
```

> **重要**：刷新 token 时必须保留 `tenantId`、`isPlatform`、`tokenVersion`、`tenantVersion` 等所有上下文字段，且重新从数据库校验一遍这些字段的有效性。
> 平台端 token 版本校验统一使用 Redis 键 `platform:version:{userId}`；用户改密/禁用/重置密码后需更新该键，确保旧 token 立即失效。

---

### 2.7 登出

```
POST /api/tenant/auth/logout
POST /api/platform/auth/logout
Authorization: Bearer <accessToken>

后端处理：
  1. 将当前 accessToken 的 jti 加入 Redis 黑名单（TTL = token 剩余有效期）
  2. 删除 refreshToken（存储于 Redis 时直接删除）

Response:
{
  "code": 200,
  "message": "已退出登录"
}
```

> **Token 黑名单**：登出后将 jti 存入 Redis `blacklist:{jti}`，每次请求在 `JwtAuthenticationFilter` 中校验。

---

### 2.8 获取当前用户可选租户列表

```
GET /api/tenant/auth/my-tenants
Authorization: Bearer <accessToken>

Response:
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "天南大陆",
      "logo": "https://...",
      "isCurrent": true,
      "memberStatus": 1   // 当前用户在该租户的状态
    },
    {
      "id": 2,
      "name": "租户B",
      "logo": "https://...",
      "isCurrent": false,
      "memberStatus": 1
    }
  ]
}
```

---

## 3. 平台端接口

### 3.1 租户管理

```
GET    /api/platform/tenants                     # 租户列表（分页）
       Query: page, size, keyword, status

POST   /api/platform/tenants                     # 创建租户（含初始管理员）
       Body: { tenantCode, tenantName, ..., adminUser: { mobile, nickname } }  // 初始密码由系统自动生成并短信通知

GET    /api/platform/tenants/{id}                # 租户详情
PUT    /api/platform/tenants/{id}                # 更新租户信息
PUT    /api/platform/tenants/{id}/status         # 启用/禁用租户（同时更新 data_version）
       Body: { status: 0|1 }

GET    /api/platform/tenants/{id}/permissions    # 查询该租户的授权权限节点 ID 列表
PUT    /api/platform/tenants/{id}/permissions    # 更新租户授权权限节点（覆盖式）
       Body: { permissionIds: [1, 2, 3, ...] }

GET    /api/platform/tenants/{id}/stats          # 租户统计：成员数、角色数、今日活跃等
GET    /api/platform/tenants/{id}/members        # 该租户成员列表（平台视角，可跨租户）
```

### 3.2 平台用户管理

```
GET    /api/platform/users                       # 平台用户列表（分页）
POST   /api/platform/users                       # 创建平台用户
PUT    /api/platform/users/{id}                  # 更新平台用户信息
PUT    /api/platform/users/{id}/status           # 启用/禁用（更新 token_version）
PUT    /api/platform/users/{id}/password         # 重置密码（更新 token_version）
DELETE /api/platform/users/{ids}                 # 删除平台用户
```

返回字段约定（列表/详情）：
- `isSuper`：是否持有任一 `is_super=1` 平台角色（计算字段，不直接来源于 `platform_user` 持久字段）
- `isBuiltin`：是否内置账号（`platform_user.is_builtin`），用于保护删除/禁用/修改角色等操作

### 3.3 权限节点管理

```
GET    /api/platform/permissions                 # 全部权限节点树（含 scope 区分）
POST   /api/platform/permissions                 # 新增权限节点
PUT    /api/platform/permissions/{id}            # 更新权限节点
DELETE /api/platform/permissions/{id}            # 删除（需无任何角色使用）
```

### 3.4 平台操作日志

```
GET    /api/platform/logs                        # 平台操作日志（is_platform=1）
       Query: page, size, operatorName, module, startTime, endTime
```

---

## 4. 租户端接口

### 4.1 用户中心（user-service，自动注入 tenant_id）

**用户管理**
```
GET    /api/v1/app-users                         # 当前租户用户列表（含自定义字段）
GET    /api/v1/app-users/{id}                    # 用户详情
PUT    /api/v1/app-users/{id}                    # 编辑用户信息
GET    /api/v1/app-users/export                  # 导出用户数据（CSV）
```

**标签管理**
```
GET    /api/v1/user-tags                         # 当前租户标签列表
POST   /api/v1/user-tags                         # 创建标签
PUT    /api/v1/user-tags/{id}                    # 编辑标签
DELETE /api/v1/user-tags/{id}                    # 删除标签
GET    /api/v1/tag-categories                    # 标签分类列表
POST   /api/v1/tag-categories                    # 创建标签分类
PUT    /api/v1/tag-categories/{id}               # 编辑标签分类
DELETE /api/v1/tag-categories/{id}               # 删除标签分类
POST   /api/v1/app-users/{id}/tags               # 为用户打标签
```

**字段管理**
```
GET    /api/v1/field-defs                        # 当前租户自定义字段定义列表
POST   /api/v1/field-defs                        # 创建自定义字段定义
PUT    /api/v1/field-defs/{id}                   # 编辑自定义字段定义
DELETE /api/v1/field-defs/{id}                   # 删除自定义字段定义
```

### 4.2 角色管理（admin-service）

```
GET    /api/roles                                # 当前租户角色列表
GET    /api/roles/{id}                           # 角色详情（含权限节点）
POST   /api/roles                                # 创建角色（tenant_id 自动注入）
PUT    /api/roles/{id}                           # 更新角色名称/描述
DELETE /api/roles/{ids}                          # 删除角色
PUT    /api/roles/{id}/permissions               # 分配角色权限（校验在授权范围内）
       Body: { permissionIds: [1, 2, 3] }

GET    /api/roles/available-permissions          # 当前租户可用权限节点树（用于分配角色时展示）
```

### 4.3 租户成员管理（admin-service）

> 成员管理 = 控制「谁属于本租户」，与用户中心的用户画像管理相互独立。

```
GET    /api/tenant/members                       # 当前租户成员列表（含角色名称）
GET    /api/tenant/members/{userId}              # 成员详情（含角色）
POST   /api/tenant/members                       # 添加成员（写入 tenant_user + tenant_user_role）
       Body: { userId, roleIds: [1, 2] }          # userId=已存在的 app_user.id
PUT    /api/tenant/members/{userId}/roles        # 为成员分配角色（覆盖式）
       Body: { roleIds: [1, 2] }
PUT    /api/tenant/members/{userId}/status       # 禁用/启用成员在本租户的访问
DELETE /api/tenant/members/{userId}              # 移除成员（删 tenant_user_role + tenant_user，不删 app_user）
```

### 4.4 租户操作日志

```
GET    /api/tenant/logs                          # 当前租户操作日志
       Query: page, size, operatorName, module, startTime, endTime
```

---

## 5. 跨服务调用：tenant_id 透传

### 5.1 问题

当 admin-service 需要调用 user-service（如查询用户信息），HTTP 调用不会自动携带 `TenantContext`，导致 user-service 收不到租户上下文。

### 5.2 方案：X-Tenant-Id 请求头透传

```
前端请求 → Nginx → admin-service（ThreadLocal 设置 tenantId）
                          ↓ 调用 user-service
                    Feign/RestTemplate 拦截器在 Header 中注入：
                    X-Tenant-Id: {tenantId}
                    X-Is-Platform: false
                          ↓
                   user-service 接收请求：
                   从 Header 读取 X-Tenant-Id，设置 TenantContext
```

**admin-service 出口拦截器（Feign）**：

```java
@Component
public class TenantFeignInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            template.header("X-Tenant-Id", tenantId.toString());
        } else {
            template.header("X-Is-Platform", "true");
        }
    }
}
```

**user-service 入口过滤器（识别内部调用）**：

```java
@Component
@Order(1)  // 优先级高于 JWT 过滤器
public class InternalTenantFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, ...) {
        // 内部服务调用，通过 Header 传递租户上下文（需配合 IP 白名单或内部 token 鉴权）
        String tenantIdHeader = request.getHeader("X-Tenant-Id");
        String isPlatformHeader = request.getHeader("X-Is-Platform");

        if (tenantIdHeader != null) {
            TenantContext.setTenantId(Long.parseLong(tenantIdHeader));
        } else if ("true".equals(isPlatformHeader)) {
            // 平台调用，不设置 tenantId（插件 ignoreTable = true）
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 只对内部调用生效（通过 IP 白名单判断，或内部标识 Header）
        return !isInternalRequest(request);
    }
}
```

---

## 6. 接口权限注解迁移

```java
// 改造前（现有代码）：
@PreAuthorize("hasAuthority('sys:user:list')")
@GetMapping
public Result<Page<UserVo>> page(...) { ... }

// 改造后（平台端接口）：
@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('platform:user:list')")
@GetMapping("/api/platform/users")
public Result<Page<PlatformUserVo>> page(...) { ... }

// 改造后（租户端接口）：
@PreAuthorize("hasAuthority('tenant:role:edit')")
@PutMapping("/api/roles/{id}")
public Result<Void> updateRole(...) { ... }
```

---

## 7. 租户禁用 token 立即失效

禁用租户时的服务端处理：

```java
@Transactional
public void disableTenant(Long tenantId) {
    // 1. 更新租户状态
    platformTenantMapper.updateStatus(tenantId, 0);

    // 2. data_version +1（使该租户所有已签发 token 立即失效）
    platformTenantMapper.incrementDataVersion(tenantId);

    // 3. 清除该租户的权限缓存
    permissionCacheService.evictTenantPermCache(tenantId);

    // 4. 清除 Redis 中缓存的租户版本号（强制下次从 DB 重查）
    redisTemplate.delete("tenant:version:" + tenantId);
}
```

每次请求在 JwtAuthenticationFilter 中校验版本号：

```java
// 租户版本号校验（使用 Redis 缓存，减少 DB 查询）
private void validateTenantVersion(Long tenantId, Long tokenTenantVersion) {
    String cacheKey = "tenant:version:" + tenantId;
    Long dbVersion = redisTemplate.opsForValue().get(cacheKey);
    if (dbVersion == null) {
        dbVersion = platformTenantMapper.getDataVersion(tenantId);
        redisTemplate.opsForValue().set(cacheKey, dbVersion, 10, TimeUnit.MINUTES);
    }
    if (!Objects.equals(tokenTenantVersion, dbVersion)) {
        throw new BusinessException(401, "租户状态已变更，请重新登录");
    }
}
```

---

## 8. 多租户相关错误码约定

> 所有接口统一返回 `Result<T>` 包装类：`{ "code": 200|4xx, "message": "...", "data": ... }`

### 8.1 认证类错误（401）

| 场景 | message 示例 |
|------|-------------|
| Token 不存在 / 格式非法 | 请先登录 |
| Token 已过期（自然过期） | 登录已失效，请重新登录 |
| Token 版本不匹配（改密/禁用平台用户触发） | 账号状态已变更，请重新登录 |
| 租户版本不匹配（禁用租户触发 data_version+1） | 租户状态已变更，请重新登录 |
| preToken 不合法（非 PRE_TOKEN 类型） | 临时凭证无效 |
| preToken 已使用（重放攻击） | 临时凭证已失效，请重新登录 |
| preToken 过期（超过 5 分钟） | 选择超时，请重新登录 |
| 使用 preToken 访问非法路径 | 临时 Token 仅可用于选择租户 |

### 8.2 授权类错误（403）

| 场景 | message 示例 |
|------|-------------|
| 用户不属于任何未禁用租户（登录时） | 您尚未加入任何租户，请联系管理员 |
| 租户已被禁用（切换/正常请求时） | 该租户已被禁用，请联系平台管理员 |
| 用户在目标租户内被禁用 | 您在该租户的访问已被禁用 |
| 用户已被从目标租户移除（切换时） | 您已不在该租户中，请刷新页面 |
| 权限节点不足（`@PreAuthorize` 拦截） | 权限不足 |
| 分配权限超出租户授权范围（角色配权限时） | 包含未授权的权限节点 |

### 8.3 幂等性约定

以下接口需保证幂等，后端实现时须遵守：

| 接口 | 幂等键 | 重复调用行为 |
|------|--------|------------|
| `POST /api/platform/tenants` 中创建/查找 app_user（ensureAppUser） | `mobile` | 已存在则返回已有用户 ID，不新建 |
| `POST /api/platform/tenants` 中建立成员关系（addUserToTenant） | `(userId, tenantId)` | 已存在则忽略，不报错 |
| `POST /api/tenant/members` 添加成员 | `(userId, tenantId)` | 已存在则忽略 |
| `PUT /api/roles/{id}/permissions` 分配权限 | — | 覆盖式更新，天然幂等 |
| `PUT /api/tenant/members/{userId}/roles` 分配角色 | — | 覆盖式更新，天然幂等 |
