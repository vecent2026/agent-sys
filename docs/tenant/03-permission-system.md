# 03 权限体系设计

## 1. 权限分层模型

```
┌───────────────────────────────────────────────────────────────────┐
│                        平台层（Platform）                           │
│                                                                   │
│  platform_user（平台管理员）                                        │
│       │ N:M via platform_user_role                                │
│       ▼                                                           │
│  platform_role（平台角色，如"系统管理员""运营管理员"）                │
│  ⚠ 超级管理员（is_super=1）不走角色校验，直接拥有全部平台权限          │
│    普通平台用户必须分配 platform_role 才能访问平台功能                │
│    platform_role 的增删改通常仅超管可操作（platform:role:*）         │
│       │ N:M via platform_role_permission                          │
│       ▼                                                           │
│  platform_permission（全局权限节点树，含 scope 字段）                │
│  ├── scope=platform（平台专属节点，不可授权给租户）                   │
│  │   ├── platform:tenant:*    租户管理                             │
│  │   ├── platform:user:*      平台用户管理                         │
│  │   ├── platform:role:*      平台角色管理                         │
│  │   ├── platform:perm:*      权限节点管理                         │
│  │   └── platform:log:*       平台操作日志                         │
│  └── scope=tenant（可授权给租户的节点，平台配置每个租户的使用范围）    │
│      ├── tenant:member:*          成员管理                         │
│      ├── 用户中心（模块）                                           │
│      │   ├── tenant:appuser:*     用户管理                         │
│      │   ├── tenant:tag:*         标签管理                         │
│      │   └── tenant:field:*       字段管理                         │
│      ├── tenant:role:*            角色管理                         │
│      └── tenant:log:*             操作日志                         │
│                                                                   │
│  tenant_permission：平台为每个租户授权可用节点的子集                 │
└───────────────────────────────────────────────────────────────────┘
                    │ 平台授权给租户（tenant_permission）
                    ▼
┌───────────────────────────────────────────────────────────────────┐
│                        租户层（Tenant）                             │
│                                                                   │
│  tenant_role（tenant_id=X）：租户自定义角色                         │
│  ├── "租户管理员"（创建租户时自动生成，持有该租户全部授权节点）         │
│  └── 其他自定义角色（在授权范围内由租户管理员配置）                    │
│  tenant_role_permission：角色 → 权限节点（在 tenant_permission 内） │
│  tenant_user_role：用户 → 租户 → 角色（三元关系）                   │
└───────────────────────────────────────────────────────────────────┘
```

---

## 2. 权限节点规范

### 2.1 命名规范

```
{scope}:{resource}:{action}

scope:    platform（平台功能）| tenant（租户功能）
resource: 资源名称（小写，连字符）
action:   list | query | add | edit | remove | reset | export | disable | assign | role | category | permission
```

### 2.2 权限标识迁移映射

> 现有权限标识格式为 `sys:xxx`，改造后统一迁移。以下为部分示例，完整 UPDATE 语句见 `06-migration-plan.md §4`。

| 旧标识 | 新标识 | scope | 说明 |
|--------|--------|-------|------|
| `sys:user:list` | `platform:user:list` | platform | 平台用户管理 |
| `sys:user:add` | `platform:user:add` | platform | |
| `sys:user:edit` | `platform:user:edit` | platform | |
| `sys:user:remove` | `platform:user:remove` | platform | |
| `sys:user:reset` | `platform:user:reset` | platform | |
| `sys:role:list` | `tenant:role:list` | tenant | 原有角色全部归入租户侧 |
| `sys:role:add` | `tenant:role:add` | tenant | |
| `sys:role:edit` | `tenant:role:edit` | tenant | |
| `sys:role:remove` | `tenant:role:remove` | tenant | |
| `sys:perm:list` | `platform:perm:list` | platform | |

> `platform:role:*`、`platform:perm:*`、`platform:tenant:*` 等节点为**新增**，原系统中不存在，无迁移映射，直接插入（见 §7 初始化 SQL）。

### 2.3 平台专属节点完整列表（scope=platform）

| 权限标识 | 说明 |
|---------|------|
| `platform:tenant:list` | 查看租户列表 |
| `platform:tenant:add` | 创建租户 |
| `platform:tenant:edit` | 编辑租户信息 |
| `platform:tenant:disable` | 禁用/启用租户 |
| `platform:tenant:permission` | 配置租户可用权限范围 |
| `platform:user:list` | 查看平台用户列表 |
| `platform:user:add` | 新增平台用户 |
| `platform:user:edit` | 修改平台用户 |
| `platform:user:remove` | 删除平台用户 |
| `platform:user:reset` | 重置平台用户密码 |
| `platform:role:list` | 查看平台角色列表 |
| `platform:role:add` | 新增平台角色 |
| `platform:role:edit` | 编辑平台角色 |
| `platform:role:remove` | 删除平台角色 |
| `platform:perm:list` | 查看权限节点树 |
| `platform:perm:add` | 新增权限节点 |
| `platform:perm:edit` | 修改权限节点 |
| `platform:perm:remove` | 删除权限节点 |
| `platform:log:list` | 查看平台操作日志 |

### 2.4 租户功能节点完整列表（scope=tenant）

> **权限粒度说明**：本阶段权限体系覆盖**菜单/按钮级**访问控制，不涉及数据级权限（如按标签、人群包或字段级的访问限制）。数据级 RBAC 如有需要，将在独立设计文档中单独扩展，不应依赖本阶段权限体系来实现。

租户侧功能分为以下四个独立模块：

| 模块 | 说明 |
|------|------|
| **成员管理** | 控制「谁能访问本租户」，管理用户的租户成员资格和角色分配 |
| **用户中心** | 管理「用户画像数据」，包含用户管理、标签管理、字段管理三个子模块 |
| **角色管理** | 租户内角色的创建、权限配置 |
| **操作日志** | 查看本租户范围内的操作记录 |

> 成员管理 与 用户中心 相互独立：成员管理操作的是 `tenant_user`（谁有资格登录），用户中心操作的是 `app_user` 的画像数据。一个用户可以是成员但画像数据为空，也可以只有画像数据而未加入租户。

---

**成员管理**（操作 `tenant_user` / `tenant_user_role` 表）

| 权限标识 | 说明 |
|---------|------|
| `tenant:member:list` | 查看租户成员列表 |
| `tenant:member:add` | 添加成员（将用户加入本租户） |
| `tenant:member:remove` | 移除成员（从本租户移除，不影响账号本身） |
| `tenant:member:disable` | 禁用/启用成员在本租户的访问 |
| `tenant:member:role` | 为成员分配/修改角色 |

**用户中心 > 用户管理**（查看和编辑用户画像数据，操作 `app_user` 表）

| 权限标识 | 说明 |
|---------|------|
| `tenant:appuser:list` | 查看用户列表 |
| `tenant:appuser:query` | 查看用户详情 |
| `tenant:appuser:edit` | 编辑用户信息 |
| `tenant:appuser:export` | 导出用户数据 |

**用户中心 > 标签管理**（操作 `tenant_tag` / `tenant_tag_category` 表）

| 权限标识 | 说明 |
|---------|------|
| `tenant:tag:list` | 查看标签列表 |
| `tenant:tag:add` | 新增标签 |
| `tenant:tag:edit` | 编辑标签 |
| `tenant:tag:remove` | 删除标签 |
| `tenant:tag:category` | 管理标签分类 |

**用户中心 > 字段管理**（操作 `tenant_field_def` / `tenant_field_value` 表）

| 权限标识 | 说明 |
|---------|------|
| `tenant:field:list` | 查看自定义字段列表 |
| `tenant:field:add` | 新增自定义字段 |
| `tenant:field:edit` | 编辑自定义字段 |
| `tenant:field:remove` | 删除自定义字段 |

**角色管理**（操作 `tenant_role` / `tenant_role_permission` 表）

| 权限标识 | 说明 |
|---------|------|
| `tenant:role:list` | 查看角色列表 |
| `tenant:role:add` | 新增角色 |
| `tenant:role:edit` | 编辑角色 |
| `tenant:role:remove` | 删除角色 |
| `tenant:role:assign` | 为用户分配角色 |

**操作日志**

| 权限标识 | 说明 |
|---------|------|
| `tenant:log:list` | 查看本租户操作日志 |

---

## 3. 权限加载流程

### 3.1 租户用户权限加载（三元查询）

> 改造前：`SELECT perm_key FROM sys_role r JOIN sys_user_role ur ON ... JOIN sys_role_permission rp ON ...`
> 改造后：查询链路变为 `tenant_user_role` → `tenant_role` → `tenant_role_permission` → `platform_permission`

```java
// TenantUserDetailsServiceImpl.java
@Override
public UserDetails loadUserByUsername(String mobile) {
    // 租户端以手机号为登录凭据，参数名使用 mobile 更准确
    // 此时 TenantContext 已由 JwtAuthenticationFilter 设置
    Long tenantId = TenantContext.getTenantId();
    AppUser user = appUserMapper.selectByMobile(mobile);

    // 查询用户在当前租户的所有角色 ID
    List<Long> roleIds = tenantUserRoleMapper.selectRoleIds(user.getId(), tenantId);

    // 查询角色对应的权限节点（走 tenant_role_permission）
    List<String> permKeys = tenantRolePermissionMapper.selectPermKeysByRoleIds(roleIds);

    List<GrantedAuthority> authorities = permKeys.stream()
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toList());

    return new TenantUserDetails(user, tenantId, authorities);
}
```

对应 Mapper SQL：

```xml
<!-- TenantUserRoleMapper.xml -->
<select id="selectRoleIds" resultType="long">
    SELECT role_id
    FROM tenant_user_role
    WHERE user_id = #{userId} AND tenant_id = #{tenantId}
</select>

<!-- TenantRolePermissionMapper.xml -->
<select id="selectPermKeysByRoleIds" resultType="string">
    SELECT DISTINCT p.permission_key
    FROM tenant_role_permission trp
    JOIN platform_permission p ON p.id = trp.permission_id
    WHERE trp.role_id IN
    <foreach collection="roleIds" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
    AND p.is_deleted = 0
    AND p.permission_key IS NOT NULL
</select>
```

### 3.2 平台用户权限加载

> 平台用户权限通过 `platform_user_role` → `platform_role_permission` → `platform_permission` 链路加载。
> 若用户持有 `is_super=1` 的角色，则直接查询全量权限，跳过角色关联链路（见 §6）。

权限加载发生在**登录时**（`PlatformAuthServiceImpl.login()`），权限列表写入 JWT `authorities` claim，后续请求直接从 JWT 读取，无需再查数据库。

```java
// PlatformAuthServiceImpl.login() — 核心权限加载逻辑
boolean isSuper = platformUserRoleMapper.existsSuperRole(user.getId()); // 查用户是否持有 is_super=1 的角色
List<SysPermission> perms = isSuper
    ? permissionMapper.selectAllPermissions()               // 超管：全量权限
    : permissionMapper.selectPermissionsByUserId(user.getId()); // 普通用户：角色链路
List<String> authorities = perms.stream()
    .filter(p -> StringUtils.hasText(p.getPermissionKey()))
    .map(SysPermission::getPermissionKey)
    .collect(Collectors.toList());
```

对应 Mapper SQL：

```sql
-- 查询普通平台用户权限（角色链路）
SELECT DISTINCT p.permission_key
FROM platform_user_role pur
JOIN platform_role_permission prp ON prp.role_id = pur.role_id
JOIN platform_permission p ON p.id = prp.permission_id
WHERE pur.user_id = #{userId}
  AND p.is_deleted = 0
  AND p.permission_key IS NOT NULL;

-- 查询用户是否持有超管角色
SELECT COUNT(*) > 0
FROM platform_user_role pur
JOIN platform_role r ON r.id = pur.role_id
WHERE pur.user_id = #{userId}
  AND r.is_super = 1
  AND r.is_deleted = 0;
```

### 3.3 权限缓存方案（Redis）

> 每次请求都查数据库成本高，需要缓存用户的权限列表。

```java
@Service
public class TenantPermissionCacheService {

    private static final String TENANT_CACHE_KEY    = "perm:tenant:%d:user:%d";
    private static final String PLATFORM_CACHE_KEY  = "perm:platform:user:%d";
    private static final long   CACHE_TTL           = 30; // 分钟

    // ── 租户用户权限缓存 ──

    public List<String> getTenantPermissions(Long userId, Long tenantId) {
        String key = String.format(TENANT_CACHE_KEY, tenantId, userId);
        List<String> cached = redisTemplate.opsForValue().get(key);
        if (cached != null) return cached;

        List<String> perms = loadTenantPermsFromDb(userId, tenantId);
        redisTemplate.opsForValue().set(key, perms, CACHE_TTL, TimeUnit.MINUTES);
        return perms;
    }

    public void evictUserPermCache(Long userId, Long tenantId) {
        redisTemplate.delete(String.format(TENANT_CACHE_KEY, tenantId, userId));
    }

    public void evictTenantPermCache(Long tenantId) {
        Set<String> keys = redisTemplate.keys(String.format("perm:tenant:%d:user:*", tenantId));
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ── 平台用户权限缓存 ──

    public List<String> getPlatformPermissions(Long userId) {
        String key = String.format(PLATFORM_CACHE_KEY, userId);
        List<String> cached = redisTemplate.opsForValue().get(key);
        if (cached != null) return cached;

        List<String> perms = loadPlatformPermsFromDb(userId);
        redisTemplate.opsForValue().set(key, perms, CACHE_TTL, TimeUnit.MINUTES);
        return perms;
    }

    public void evictPlatformUserPermCache(Long userId) {
        redisTemplate.delete(String.format(PLATFORM_CACHE_KEY, userId));
    }
}
```

**缓存失效触发点**

| 操作 | 失效范围 |
|------|---------|
| 为租户用户分配/移除角色 | 该用户在该租户的权限缓存 |
| 修改租户角色的权限节点 | 持有该角色的所有用户缓存（按 role_id 反查） |
| 平台修改租户授权范围 | 整个租户的权限缓存 |
| 租户被禁用 | 整个租户的权限缓存 |
| 修改平台用户的角色 | 该平台用户的权限缓存 |
| 修改平台角色的权限节点 | 持有该角色的所有平台用户缓存 |

---

## 4. 角色管理规则

### 4.1 租户操作范围

| 操作 | 是否允许 | 限制条件 |
|------|---------|---------|
| 创建角色 | ✅ | 角色 `tenant_id` 自动设为当前 `tenantId` |
| 删除角色 | ✅ | 角色 `tenant_id` 必须等于当前 `tenantId`；有用户绑定时禁止删除 |
| 为角色分配权限 | ✅ | 所选权限节点必须全部在 `tenant_permission` 授权范围内 |
| 为用户分配角色 | ✅ | 角色必须属于当前租户；写入 `tenant_user_role` |
| 查看其他租户角色 | ❌ | MyBatis-Plus 租户插件自动过滤 |

### 4.2 角色-权限分配校验（服务层强校验）

```java
@Transactional
public void assignRolePermissions(Long tenantId, Long roleId, List<Long> permissionIds) {
    // 1. 校验角色属于该租户
    TenantRole role = tenantRoleMapper.selectById(roleId);
    if (role == null || !role.getTenantId().equals(tenantId)) {
        throw new BusinessException("无权操作该角色");
    }

    // 2. 校验权限节点在该租户授权范围内
    Set<Long> allowedIds = tenantPermissionMapper.selectPermissionIdSet(tenantId);
    List<Long> invalid = permissionIds.stream()
        .filter(id -> !allowedIds.contains(id))
        .collect(Collectors.toList());
    if (!invalid.isEmpty()) {
        throw new BusinessException("包含未授权的权限节点: " + invalid);
    }

    // 3. 更新角色-权限关联
    tenantRolePermissionMapper.deleteByRoleId(roleId);
    if (!permissionIds.isEmpty()) {
        tenantRolePermissionMapper.batchInsert(tenantId, roleId, permissionIds);
    }

    // 4. 清除持有该角色的用户权限缓存
    List<Long> affectedUserIds = tenantUserRoleMapper.selectUserIdsByRoleId(roleId, tenantId);
    affectedUserIds.forEach(uid -> permissionCacheService.evictUserPermCache(uid, tenantId));
}
```

---

## 5. 租户第一个管理员（初始化流程）

> 平台创建租户时，必须同时指定第一个管理员账号，否则该租户无人可操作。

### 5.1 创建流程

```
平台端操作：
  POST /api/platform/tenants
  {
    "tenantName": "天南大陆",
    "tenantCode": "tiannan",
    ...
    "adminUser": {           ← 初始管理员信息（手机号为唯一标识）
      "mobile": "13800000001",
      "nickname": "租户管理员"
    }
  }
  注：初始密码由系统生成后通过短信/邮件通知管理员，不在 API 中明文传递。

后端处理（全程事务保护，跨服务步骤使用补偿机制）：
  1. 创建 platform_tenant 记录（admin-service）
  2. 创建/查找 app_user（通过 user-service RPC，手机号唯一）
  3. 建立 tenant_user 成员关系（通过 user-service RPC）
  4. 创建"租户管理员"角色（tenant_role，tenant_id = 新租户ID）
  5. 为该角色分配租户全部授权节点（取 tenant_permission 中的完整范围）
  6. 创建 tenant_user_role（user_id, tenant_id, role_id）
```

### 5.2 事务处理

```java
/**
 * 创建租户并初始化管理员。
 *
 * 注意：Steps 2-3 为跨服务 RPC 调用（user-service），与本地事务不在同一个分布式事务中。
 * 若 user-service 调用失败，需通过补偿接口（deleteUserFromTenant）回滚已创建的 tenant/role 记录。
 * 若本地 DB 操作（Steps 4-6）失败，@Transactional 自动回滚，但 user-service 侧已创建的数据
 * 需通过补偿逻辑清理（发送补偿消息或定时任务扫描孤立记录）。
 */
@Transactional(rollbackFor = Exception.class)
public void createTenantWithAdmin(CreateTenantDto dto) {
    // Step 1: 创建租户（admin-service 本地 DB）
    PlatformTenant tenant = new PlatformTenant();
    BeanUtils.copyProperties(dto, tenant);
    platformTenantMapper.insert(tenant);
    Long tenantId = tenant.getId();

    // Step 2: 创建/查找 app_user（RPC 调用 user-service，手机号唯一）
    Long userId = userServiceClient.ensureAppUser(dto.getAdminUser());

    // Step 3: 建立用户-租户成员关系（RPC 调用 user-service，写入 tenant_user）
    userServiceClient.addUserToTenant(userId, tenantId, "PLATFORM_CREATE");

    // Step 4: 创建默认"租户管理员"角色（admin-service 本地 DB）
    TenantRole adminRole = new TenantRole();
    adminRole.setTenantId(tenantId);
    adminRole.setRoleName("租户管理员");
    adminRole.setRoleKey("tenant_admin");
    tenantRoleMapper.insert(adminRole);

    // Step 5: 为角色分配租户可用的全部权限节点
    List<Long> permIds = tenantPermissionMapper.selectPermissionIds(tenantId);
    if (!permIds.isEmpty()) {
        tenantRolePermissionMapper.batchInsert(tenantId, adminRole.getId(), permIds);
    }

    // Step 6: 将用户与角色关联（写入 tenant_user_role，admin-service 本地 DB）
    tenantUserRoleMapper.insert(userId, tenantId, adminRole.getId());
}
```

---

## 6. 超级管理员设计

### 6.1 设计原则

超管身份由**角色**决定，而非用户字段：`platform_role.is_super=1` 标识超管角色，任何平台用户只要被分配了此角色，即获得超管权限。这样超管权限可以复用给多个账号，而不必在用户表上打标。

| 概念 | 说明 |
|------|------|
| **内置超管角色** | `platform_role(is_super=1, is_builtin=1, role_key='super_admin')`，系统初始化时创建，不可删除、不可修改 |
| **内置 admin 账号** | `platform_user(is_builtin=1, username='admin')`，默认密码 `123456`，不可删除、不可禁用、不可修改角色分配 |
| **超管身份判断** | 登录时查询用户持有的角色，若任意角色 `is_super=1`，则 JWT 中 `isSuper=true` |

### 6.2 登录权限加载逻辑

```
登录时：
  1. 查询用户持有的所有角色
  2. 若任意角色 is_super=1：
       → 查询全部 platform_permission（不限 scope）
       → JWT 中 isSuper=true，authorities=全量权限 key
  3. 否则：
       → 走 platform_user_role → platform_role_permission → platform_permission 链路
       → JWT 中 isSuper=false，authorities=角色授权的权限 key
```

```java
// PlatformAuthServiceImpl.login()
boolean isSuper = platformUserRoleMapper.hasAnyRole(user.getId(), roleKey -> roleMapper.isSuper(roleKey));
List<SysPermission> perms = isSuper
    ? permissionMapper.selectAllPermissions()
    : permissionMapper.selectPermissionsByUserId(user.getId());
List<String> authorities = perms.stream()
    .filter(p -> StringUtils.hasText(p.getPermissionKey()))
    .map(SysPermission::getPermissionKey)
    .collect(Collectors.toList());
// 生成 JWT，isSuper 写入 claim
String token = jwtUtil.createPlatformToken(user.getId(), user.getUsername(), isSuper, tokenVersion, authorities);
```

### 6.3 前端权限展示规则

| 场景 | 行为 |
|------|------|
| `isSuper=true` | 跳过所有 UI 权限检查，所有菜单/按钮均可见 |
| 超管角色的权限树 | 所有节点显示为已勾选（仅展示效果，不写入 `platform_role_permission`） |
| 非超管角色 | 仅展示已授权节点，操作按钮按 `authorities` 校验 |

### 6.4 内置账号/角色保护规则

后端在以下操作的 Service 层做保护：

| 保护对象 | 禁止操作 |
|---------|---------|
| `platform_role.is_builtin=1` | 删除、修改角色名/角色标识/is_super/is_builtin |
| `platform_user.is_builtin=1` | 删除、禁用、修改角色分配 |

> 内置账号允许修改密码、昵称等非关键字段，不影响安全性。

---

## 7. 权限节点数据初始化 SQL

```sql
-- platform_permission 表已包含 scope 字段（见 02-database-design.md §1.3）

-- ══════════════════════════
-- 平台层目录与节点
-- ══════════════════════════

-- 平台管理目录
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (0,'平台管理','DIR',NULL,'/platform',NULL,1,'platform');
SET @platform_dir = LAST_INSERT_ID();

-- 租户管理菜单
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@platform_dir,'租户管理','MENU',NULL,'/platform/tenants','platform/tenant/TenantList',10,'platform');
SET @tenant_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@tenant_menu,'查看租户',    'BTN','platform:tenant:list',      NULL,NULL,1,'platform'),
(@tenant_menu,'创建租户',    'BTN','platform:tenant:add',       NULL,NULL,2,'platform'),
(@tenant_menu,'编辑租户',    'BTN','platform:tenant:edit',      NULL,NULL,3,'platform'),
(@tenant_menu,'禁用租户',    'BTN','platform:tenant:disable',   NULL,NULL,4,'platform'),
(@tenant_menu,'配置权限',    'BTN','platform:tenant:permission',NULL,NULL,5,'platform');

-- 平台用户管理菜单
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@platform_dir,'平台用户','MENU',NULL,'/platform/users','platform/user/UserList',20,'platform');
SET @puser_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@puser_menu,'查看平台用户','BTN','platform:user:list',  NULL,NULL,1,'platform'),
(@puser_menu,'新增平台用户','BTN','platform:user:add',   NULL,NULL,2,'platform'),
(@puser_menu,'编辑平台用户','BTN','platform:user:edit',  NULL,NULL,3,'platform'),
(@puser_menu,'删除平台用户','BTN','platform:user:remove',NULL,NULL,4,'platform'),
(@puser_menu,'重置密码',    'BTN','platform:user:reset', NULL,NULL,5,'platform');

-- 平台角色管理菜单
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@platform_dir,'平台角色','MENU',NULL,'/platform/roles','platform/role/RoleList',30,'platform');
SET @prole_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@prole_menu,'查看平台角色','BTN','platform:role:list',  NULL,NULL,1,'platform'),
(@prole_menu,'新增平台角色','BTN','platform:role:add',   NULL,NULL,2,'platform'),
(@prole_menu,'编辑平台角色','BTN','platform:role:edit',  NULL,NULL,3,'platform'),
(@prole_menu,'删除平台角色','BTN','platform:role:remove',NULL,NULL,4,'platform');

-- 权限节点管理菜单
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@platform_dir,'权限节点','MENU',NULL,'/platform/permissions','platform/perm/PermList',40,'platform');
SET @perm_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@perm_menu,'查看权限节点','BTN','platform:perm:list',  NULL,NULL,1,'platform'),
(@perm_menu,'新增权限节点','BTN','platform:perm:add',   NULL,NULL,2,'platform'),
(@perm_menu,'编辑权限节点','BTN','platform:perm:edit',  NULL,NULL,3,'platform'),
(@perm_menu,'删除权限节点','BTN','platform:perm:remove',NULL,NULL,4,'platform');

-- 平台操作日志菜单
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@platform_dir,'操作日志','MENU',NULL,'/platform/logs','platform/log/LogList',50,'platform');
SET @plog_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@plog_menu,'查看平台日志','BTN','platform:log:list',NULL,NULL,1,'platform');


-- ══════════════════════════
-- 租户层目录与节点（scope=tenant，用于平台端配置授权范围时展示树形结构）
-- ══════════════════════════

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (0,'租户功能','DIR',NULL,'/tenant',NULL,2,'tenant');
SET @tenant_func_dir = LAST_INSERT_ID();

-- 成员管理
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@tenant_func_dir,'成员管理','MENU',NULL,'/members','tenant/member/MemberList',10,'tenant');
SET @member_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@member_menu,'查看成员列表','BTN','tenant:member:list',   NULL,NULL,1,'tenant'),
(@member_menu,'添加成员',    'BTN','tenant:member:add',    NULL,NULL,2,'tenant'),
(@member_menu,'移除成员',    'BTN','tenant:member:remove', NULL,NULL,3,'tenant'),
(@member_menu,'禁用成员',    'BTN','tenant:member:disable',NULL,NULL,4,'tenant'),
(@member_menu,'分配角色',    'BTN','tenant:member:role',   NULL,NULL,5,'tenant');

-- 用户中心目录
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@tenant_func_dir,'用户中心','DIR',NULL,'/user-center',NULL,20,'tenant');
SET @uc_dir = LAST_INSERT_ID();

-- 用户中心 > 用户管理
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@uc_dir,'用户管理','MENU',NULL,'/app-users','tenant/appuser/AppUserList',10,'tenant');
SET @appuser_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@appuser_menu,'查看用户列表','BTN','tenant:appuser:list',  NULL,NULL,1,'tenant'),
(@appuser_menu,'查看用户详情','BTN','tenant:appuser:query', NULL,NULL,2,'tenant'),
(@appuser_menu,'编辑用户信息','BTN','tenant:appuser:edit',  NULL,NULL,3,'tenant'),
(@appuser_menu,'导出用户数据','BTN','tenant:appuser:export',NULL,NULL,4,'tenant');

-- 用户中心 > 标签管理
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@uc_dir,'标签管理','MENU',NULL,'/tags','tenant/tag/TagList',20,'tenant');
SET @tag_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@tag_menu,'查看标签',    'BTN','tenant:tag:list',    NULL,NULL,1,'tenant'),
(@tag_menu,'新增标签',    'BTN','tenant:tag:add',     NULL,NULL,2,'tenant'),
(@tag_menu,'编辑标签',    'BTN','tenant:tag:edit',    NULL,NULL,3,'tenant'),
(@tag_menu,'删除标签',    'BTN','tenant:tag:remove',  NULL,NULL,4,'tenant'),
(@tag_menu,'管理标签分类','BTN','tenant:tag:category',NULL,NULL,5,'tenant');

-- 用户中心 > 字段管理
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@uc_dir,'字段管理','MENU',NULL,'/field-def','tenant/field/FieldList',30,'tenant');
SET @field_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@field_menu,'查看自定义字段','BTN','tenant:field:list',  NULL,NULL,1,'tenant'),
(@field_menu,'新增自定义字段','BTN','tenant:field:add',   NULL,NULL,2,'tenant'),
(@field_menu,'编辑自定义字段','BTN','tenant:field:edit',  NULL,NULL,3,'tenant'),
(@field_menu,'删除自定义字段','BTN','tenant:field:remove',NULL,NULL,4,'tenant');

-- 角色管理
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@tenant_func_dir,'角色管理','MENU',NULL,'/roles','tenant/role/RoleList',30,'tenant');
SET @role_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@role_menu,'查看角色列表','BTN','tenant:role:list',  NULL,NULL,1,'tenant'),
(@role_menu,'新增角色',    'BTN','tenant:role:add',   NULL,NULL,2,'tenant'),
(@role_menu,'编辑角色',    'BTN','tenant:role:edit',  NULL,NULL,3,'tenant'),
(@role_menu,'删除角色',    'BTN','tenant:role:remove',NULL,NULL,4,'tenant'),
(@role_menu,'分配角色给用户','BTN','tenant:role:assign',NULL,NULL,5,'tenant');

-- 操作日志
INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`)
VALUES (@tenant_func_dir,'操作日志','MENU',NULL,'/logs','tenant/log/LogList',40,'tenant');
SET @tlog_menu = LAST_INSERT_ID();

INSERT INTO `platform_permission` (`parent_id`,`name`,`type`,`permission_key`,`path`,`component`,`sort`,`scope`) VALUES
(@tlog_menu,'查看租户日志','BTN','tenant:log:list',NULL,NULL,1,'tenant');
```
