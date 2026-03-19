# 微服务架构诊断报告

> 版本：v1.0 | 诊断日期：2026-03-19 | 范围：admin-service / user-service / log-service + Nginx 网关 + 前端

---

## 目录

1. [现状架构总览](#一现状架构总览)
2. [各服务职责分析](#二各服务职责分析)
3. [问题诊断清单](#三问题诊断清单)
4. [整改建议](#四整改建议)
5. [附录](#五附录)

---

## 一、现状架构总览

```
                          ┌─────────────────────────────────┐
                          │          Nginx Gateway :80       │
                          │                                  │
                          │  /platform  → frontend (平台端)  │
                          │  /          → frontend (租户端)  │
                          │  /api/platform/* → admin-service │
                          │  /api/tenant/*   → admin-service │
                          │  /api/auth       → admin-service │
                          │  /api/v1/*       → user-service  │
                          └────┬──────┬──────────────────────┘
                               │      │
              ┌────────────────▼┐    ┌▼──────────────────┐
              │  admin-service  │    │   user-service     │
              │     :8081       │───►│     :8082          │
              │                 │    │                    │
              │ • 认证 (JWT)    │    │ • C端用户管理      │
              │ • 平台管理      │    │ • 自定义字段       │
              │ • 租户管理      │    │ • 用户标签         │
              │ • RBAC权限      │    │ • 用户视图         │
              │ • 系统账号管理  │    │ • 用户导入         │
              │ • 日志查询(ES)  │    └────────────────────┘
              └──────┬──────────┘
                     │ Kafka 生产
                     │ (sys-log-topic)
              ┌──────▼──────────┐
              │   log-service   │
              │     :8083       │
              │                 │       ┌──────────────────┐
              │ • Kafka 消费    │──────►│  Elasticsearch   │
              │   (写入 ES)     │       │    :9200         │
              └─────────────────┘       └──────────────────┘

基础设施：MySQL:3306 | Redis:6379 | Kafka:9092 | Zookeeper:2181
```

### 服务基本信息

| 服务 | 端口 | 数据库 | 包名 | 代码规模 |
|------|------|--------|------|---------|
| admin-service | 8081 | `admin_system` | `com.trae.admin` | ~150 个 Java 类，6 个业务模块 |
| user-service | 8082 | `trae_user` | `com.trae.user` | ~56 个 Java 类，1 个扩展模块 |
| log-service | 8083 | 无（纯 ES） | `com.trae.admin.log` ⚠️ | 4 个 Java 类，0 个 HTTP 接口 |

---

## 二、各服务职责分析

### 2.1 admin-service — 实际承担了 6 个领域

从代码模块结构看，admin-service 内部有 **6 个完全独立的业务模块**，名字叫 "admin" 但实质是一个领域大杂烩：

```
admin-service/modules/
├── auth/       认证领域：JWT 签发/刷新/登出，3 个 Controller（通用/租户/平台）
├── rbac/       权限领域：角色、权限树、角色-权限绑定，租户与平台双套 RBAC
├── user/       系统账号领域：平台用户 CRUD，路径 /api/platform/users
├── platform/   平台治理领域：租户管理、平台角色管理、日志查询（平台视角）
├── tenant/     租户管理领域：成员管理、租户角色、日志查询（租户视角）
└── log/        日志领域（读）：LogService 直连 ES 查询，日志清理定时任务
```

**6 个模块对应 6 类完全不同的业务需求，服务命名 "admin" 无法准确描述任何一个。**

### 2.2 user-service — 职责单一但命名有二义性

user-service 管理的是**应用终端用户（C 端用户）**，与 admin-service 中的"系统账号用户"完全不同：

| 对比维度 | admin-service/UserController | user-service/AppUserController |
|---------|------------------------------|-------------------------------|
| 管理对象 | 平台系统账号（超管/租户管理员） | 租户应用的终端注册用户 |
| 路由前缀 | `/api/platform/users` | `/api/v1/app-users` |
| 数据表 | `platform_user` (admin_system 库) | `app_user` (trae_user 库) |
| 登录系统 | 后台管理系统 | 租户自己的产品 App |
| Entity | `SysUser` | `AppUser` |

**两类"用户"都叫 user，但毫无业务关联，是当前最大的认知负担。**

### 2.3 log-service — 职责被切割，只承担了写入端

```
日志领域现状（被拆分在两个服务中）：

admin-service                       log-service
┌─────────────────────┐            ┌──────────────────────┐
│ LogAspect           │──Kafka──►  │ LogConsumer          │
│ (生产：AOP 切面写)  │            │ (消费：写入 ES)       │
│                     │            └──────────────────────┘
│ LogService          │
│ (查询：直连 ES 读)  │ ← 日志查询 API 在 admin-service！
│                     │
│ TenantLogController │ /api/tenant/logs
│ PlatformLogController│ /api/platform/logs
└─────────────────────┘
```

**log-service 实际只是一个 Kafka→ES 的管道，日志查询 API 留在了 admin-service。日志领域的写入和读取分散在两个服务中。**

---

## 三、问题诊断清单

### P0 — 阻塞性问题（影响团队理解和后续扩展）

#### P0-1：admin-service 名实不符，职责过载

- **现象**：`admin-service` 同时承担认证、权限、平台治理、租户管理、系统账号、日志查询六个职责
- **影响**：任何一个领域的修改都需要动 admin-service；新人无法从名字判断代码归属；随着智能体服务加入，admin-service 有继续膨胀的风险
- **根因**：项目早期将"后台相关"一切内容都放入一个服务，未按领域边界切分

#### P0-2：两种"用户"概念同名，无文档区分

- **现象**：
  - `admin-service` 中存在 `SysUser`、`UserController`、`UserService`
  - `user-service` 中存在 `AppUser`、`AppUserController`、`AppUserService`
  - 两者都叫"用户"，但业务含义完全不同
- **影响**：开发时极易将新代码放错服务；跨服务接口联调时概念混乱
- **根因**：缺乏全局统一的领域词汇表

---

### P1 — 高优先级问题（影响可维护性和系统健壮性）

#### P1-1：log-service 包名使用了 admin 命名空间

- **现象**：log-service 的包名为 `com.trae.admin.log`，而非 `com.trae.log`
- **影响**：独立服务使用了另一个服务的包名前缀，代码归属感混乱；IDE 全局搜索时干扰严重
- **整改**：重命名为 `com.trae.log`

#### P1-2：日志领域职责被人为拆散

- **现象**：
  - 日志**写入**：admin-service AOP → Kafka → log-service 消费 → ES
  - 日志**查询**：admin-service LogService 直连 ES
  - 日志**HTTP 接口**：TenantLogController、PlatformLogController 在 admin-service
- **影响**：日志相关代码分布在两个服务，修改日志逻辑必须同时动两个服务；log-service 只是个"管道"，不完整
- **整改方向**：日志查询 API 移入 log-service，admin-service 只保留 AOP 写入切面

#### P1-3：SysLogDocument 实体三重定义

- **现象**：以下三处存在相同或相似的 `SysLogDocument` 类：
  - `com.trae.admin.modules.log.entity.SysLogDocument`
  - `com.trae.admin.modules.user.entity.SysLogDocument`（user 模块中的冗余定义）
  - `com.trae.admin.log.entity.SysLogDocument`（log-service 中）
- **影响**：ES 映射可能不一致；序列化时 Kafka 消息类型头依赖包名，跨服务时易出现类型不匹配
- **整改**：统一为一个位置的定义，其他地方引用或复制保持命名一致

#### P1-4：内部接口 `/api/internal/*` 无认证保护

- **现象**：user-service 的 `InternalUserController` 提供内部服务调用接口，仅依赖注释说明"IP 白名单保护"，代码层面无 token 校验
- **影响**：若 Nginx/Docker 网络配置失误，内部接口将暴露在外网
- **整改**：增加内部服务调用的 shared-secret header 校验，或通过 Spring Security 配置 IP 白名单

---

### P2 — 中优先级问题（影响稳定性和运维效率）

#### P2-1：服务间调用使用裸 RestTemplate，无容错机制

- **现象**：admin-service 调用 user-service 使用原始 `RestTemplate`，无超时重试、无熔断降级
- **影响**：user-service 不可用时，admin-service 相关接口直接报错；无 fallback 处理
- **建议**：引入 Spring Cloud OpenFeign + Resilience4j，或至少配置 RestTemplate 超时

#### P2-2：认证 Controller 三重化，逻辑边界不清

- **现象**：存在 `AuthController`、`TenantAuthController`、`PlatformAuthController` 三个认证控制器
- **影响**：登录逻辑分散，JWT 生成/验证策略难以统一维护；前端需要记忆三个不同的登录端点
- **建议**：合并为统一认证入口，通过请求参数/路径区分场景，或保留两个（平台/租户双端）并废弃旧版

#### P2-3：权限缓存缺失，每次请求都查询数据库

- **现象**：`PermissionService` 的权限树查询无 Redis 缓存层，每次鉴权都触发 DB 查询
- **影响**：高并发场景下数据库压力大；权限加载是所有接口的必经路径
- **建议**：在 PermissionService 加入 `@Cacheable`，以 `tenantId:userId` 为 key 缓存权限列表

#### P2-4：Nginx 路由表中缺少部分服务路由

- **现象**：admin-service 存在 `/api/rbac/*` 路由（RoleController、PermissionController），但 nginx.conf 中未见对应的 `location ^~ /api/rbac/` 配置
- **影响**：rbac 接口可能无法通过网关正常访问，或依赖其他路由的兜底匹配
- **建议**：补全 Nginx 路由配置，所有服务接口路径在 nginx.conf 中显式声明

---

### P3 — 低优先级问题（可计划整改）

#### P3-1：API 版本规范不统一

- **现象**：
  - user-service：`/api/v1/app-users`（有版本号）
  - admin-service：`/api/platform/users`、`/api/tenant/logs`（无版本号）
- **建议**：统一约定版本策略，推荐 `/api/{scope}/v1/{resource}` 或全部无版本

#### P3-2：DbFixController 暴露在生产代码中

- **现象**：`DbFixController`（路径 `/api/debug/db`）存在于 admin-service 生产代码中，用于数据库修复
- **影响**：调试/修复接口若无严格权限控制，存在安全风险
- **建议**：移入独立的 profile 或用 `@ConditionalOnProperty` 控制启用条件

#### P3-3：两个服务各自维护 common 包，代码重复

- **现象**：`admin-service/common` 和 `user-service/common` 中均有 `Result.java`、`JwtUtil.java`、`TenantContext.java` 等几乎相同的类
- **建议**：提取为 `common-lib` 共享 Maven 模块（加入 agent-service 后重复问题会更突出）

---

## 四、整改建议

### 4.1 短期（不重构，先规范命名和文档）

这些改动成本低，可立即执行，能显著降低新成员的认知负担：

| 编号 | 改动项 | 成本 |
|------|-------|------|
| S-1 | 在 CLAUDE.md 和代码注释中明确两类"用户"的定义和区分 | 极低 |
| S-2 | 修复 log-service 包名 `com.trae.admin.log` → `com.trae.log` | 低（重命名） |
| S-3 | 删除 `admin-service/modules/user/entity/SysLogDocument`（冗余定义） | 低 |
| S-4 | 补全 Nginx `/api/rbac/` 路由配置 | 极低 |
| S-5 | 为 InternalUserController 增加 shared-secret header 校验 | 低 |

### 4.2 中期（模块级重组，不拆服务）

在现有三个服务框架内，通过模块重组让职责更清晰：

```
整改前                          整改后
─────────────────────           ─────────────────────────────
admin-service                   admin-service（改名建议：iam-service）
 ├── auth/        ─────────────► ├── auth/         认证领域（保留）
 ├── rbac/        ─────────────► ├── rbac/          权限领域（保留）
 ├── user/        ─────────────► ├── account/       系统账号（重命名）
 ├── platform/    ─────────────► ├── platform/      平台治理（保留）
 ├── tenant/      ─────────────► ├── tenant/        租户管理（保留）
 └── log/（读）                  └── （日志查询移出）

log-service（改名建议：audit-service）
 ├── consumer/    ─────────────► ├── consumer/      Kafka 消费（保留）
 ├── entity/      ─────────────► ├── entity/        ES 文档（保留）
 └── repository/  ─────────────► ├── repository/    ES 仓库（保留）
                                 ├── controller/    ← 新增：日志查询 API 移入
                                 └── service/       ← 新增：LogQueryService
```

### 4.3 长期（服务级拆分，加入智能体服务后一起规划）

结合智能体服务上线，将整体微服务架构演进为：

```
服务名称              职责                       端口
─────────────────────────────────────────────────────
iam-service          认证 + RBAC + 系统账号       8081（原 admin-service）
user-service         C端用户管理（保持不变）       8082
audit-service        审计日志（读+写统一）         8083（原 log-service）
agent-service        智能体服务（新增）            8090
```

**这是最小化的服务拆分方案**，仅做职责对齐，不引入服务注册/发现等复杂基础设施。

### 4.4 命名规范约定（立即生效）

| 概念 | 统一术语 | 说明 |
|------|---------|------|
| 系统账号 | `SysUser` / `system user` | 登录后台管理系统的账号 |
| 应用终端用户 | `AppUser` / `app user` | 租户应用的 C 端注册用户 |
| 平台管理员 | `PlatformAdmin` | 超级管理员，管理所有租户 |
| 租户管理员 | `TenantAdmin` | 单租户内的管理员 |
| 日志 | `AuditLog` | 系统操作审计日志（区别于应用业务日志） |

---

## 五、附录

### 5.1 Controller 全量路由表

| 服务 | Controller | 路径前缀 | 主要动词 |
|------|-----------|---------|---------|
| admin-service | AuthController | `/api/auth` | POST login/logout/refresh, GET me/menus |
| admin-service | TenantAuthController | `/api/tenant/auth` | POST login/logout/refresh |
| admin-service | PlatformAuthController | `/api/platform/auth` | POST login/logout |
| admin-service | UserController | `/api/platform/users` | GET/POST/PUT/DELETE |
| admin-service | RoleController | `/api/rbac/roles` | GET/POST/PUT/DELETE |
| admin-service | PermissionController | `/api/platform/permissions` | GET tree, POST/PUT/DELETE |
| admin-service | PlatformTenantController | `/api/platform/tenants` | GET/POST/PUT/DELETE, PUT status |
| admin-service | PlatformRoleController | `/api/platform/roles` | GET/POST/PUT/DELETE |
| admin-service | PlatformLogController | `/api/platform/logs` | GET |
| admin-service | TenantMemberController | `/api/tenant/members` | GET/POST/PUT/DELETE |
| admin-service | TenantLogController | `/api/tenant/logs` | GET |
| admin-service | DbFixController | `/api/debug/db` | POST（⚠️ 调试接口） |
| user-service | AppUserController | `/api/v1/app-users` | GET/PUT/POST（导入/导出/标签） |
| user-service | AppUserFieldController | `/api/v1/user-fields` | GET/POST/PUT/DELETE |
| user-service | AppUserTagController | `/api/v1/user-tags` | GET/POST/PUT/DELETE |
| user-service | UserViewController | `/api/user/views` | GET/POST/PUT/DELETE |
| user-service | InternalUserController | `/api/internal` | GET/POST（服务内部调用） |

### 5.2 数据库归属

| 数据库 | 使用方 | 主要表 |
|-------|-------|-------|
| `admin_system` | admin-service | platform_user, platform_role, platform_permission, platform_tenant, tenant_role, tenant_permission, tenant_user_role |
| `trae_user` | user-service | app_user, app_user_field, app_user_tag, app_user_tag_category, app_user_tag_relation, app_user_field_value, tenant_user |
| Elasticsearch `sys_log` | log-service（写）/ admin-service（读） | SysLogDocument（⚠️ 同一 index 由两个服务访问） |

### 5.3 问题优先级汇总

| 编号 | 问题 | 级别 | 建议时机 |
|------|-----|------|---------|
| P0-1 | admin-service 名实不符 | P0 | 中期重组时同步处理 |
| P0-2 | 两种"用户"同名无区分 | P0 | 立即文档化，中期代码对齐 |
| P1-1 | log-service 包名错误 | P1 | 短期修复 |
| P1-2 | 日志领域职责分散 | P1 | 中期重组 |
| P1-3 | SysLogDocument 三重定义 | P1 | 短期清理 |
| P1-4 | 内部接口无认证 | P1 | 短期加固 |
| P2-1 | RestTemplate 无容错 | P2 | 引入 agent-service 时统一改造 |
| P2-2 | 认证 Controller 三重化 | P2 | 中期重组 |
| P2-3 | 权限缓存缺失 | P2 | 中期优化 |
| P2-4 | Nginx 路由不完整 | P2 | 短期补全 |
| P3-1 | API 版本不统一 | P3 | 长期规范 |
| P3-2 | DbFixController 生产残留 | P3 | 短期清理 |
| P3-3 | common 包重复 | P3 | 长期提取 |
