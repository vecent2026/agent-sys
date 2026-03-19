# 目标微服务架构设计（激进重建版）

> 版本：v2.0 | 设计日期：2026-03-19 | 策略：一次性全量落地，不保留任何过渡代码
> 前置：[01-diagnosis.md](./01-diagnosis.md)

---

## 目录

1. [核心原则](#一核心原则)
2. [目标架构总览](#二目标架构总览)
3. [服务职责边界](#三服务职责边界)
   - [3.0 服务边界总矩阵](#30-服务边界总矩阵)
   - [3.1 iam-service](#31-iam-serviceidentity--access-management)
   - [3.2 user-service](#32-user-serviceapp-user-service)
   - [3.3 audit-service](#33-audit-serviceaudit-log-service)
   - [3.4 租户成员关系边界声明](#34-租户成员关系边界声明)
4. [跨服务契约与 common-lib 规范](#四跨服务契约与-common-lib-规范)
5. [服务间通信规范](#五服务间通信规范)
6. [API 路由规范](#六api-路由规范)
7. [基础设施设计](#七基础设施设计)
8. [扩展性设计](#八扩展性设计)
9. [统一术语表](#九统一术语表)

---

## 一、核心原则

> 项目未上线，彻底消除所有历史债务，不保留任何临时代码、过渡接口、兼容 shim。

| 原则 | 激进落地方式 |
|------|------------|
| **领域边界即服务边界** | 服务名 = 领域名，包名 = 领域名，不存在名实不符 |
| **零冗余** | 跨服务共享的是**契约**（响应格式、Header 规范、错误码），不是代码；Java 服务通过 common-lib 共享实现，Python 等异构服务遵循同一契约各自实现 |
| **删除优于废弃** | 旧接口、调试代码直接删除，不加 `@Deprecated` 不加 Profile 门控 |
| **安全强制** | 内部接口在代码层强制鉴权，不依赖网络拓扑假设 |
| **可扩展** | 新增服务只需参照规范 + 依赖 common-lib，不修改任何现有服务 |

---

## 二、目标架构总览

```
                     ┌──────────────────────────────────────────────────────────┐
                     │                   Nginx Gateway :80                       │
                     │                                                            │
                     │  /platform          → frontend (平台端 SPA)              │
                     │  /                  → frontend (租户端 SPA)              │
                     │                                                            │
                     │  /api/platform/logs → audit-service  ← 注意优先级        │
                     │  /api/tenant/logs   → audit-service                       │
                     │  /api/platform/*    → iam-service                         │
                     │  /api/tenant/*      → iam-service                         │
                     │  /api/rbac/*        → iam-service    ← 补全缺失           │
                     │                                                            │
                     │  /api/v1/app-users  → user-service                        │
                     │  /api/user/*        → user-service                        │
                     │  /api/v1/user-*     → user-service                        │
                     │                                                            │
                     │  /api/internal/*    → [不暴露，Docker 内网专用]           │
                     │  /api/auth/*        → [已删除]                            │
                     └──────┬──────────────────┬──────────────────┬──────────────┘
                            │                  │                  │
               ┌────────────▼─────┐  ┌─────────▼──────┐  ┌──────▼────────────┐
               │   iam-service    │  │  user-service   │  │   audit-service   │
               │     :8081        │  │    :8082        │  │     :8083         │
               │  com.starry.iam    │  │  com.starry.user  │  │  com.starry.audit   │
               │                  │  │                 │  │                   │
               │ auth/            │  │ AppUser         │  │ LogConsumer       │
               │ rbac/            │  │ AppUserField    │  │ AuditLogController│
               │ account/         │  │ AppUserTag      │  │ AuditLogQuery-    │
               │ platform/        │  │ UserView        │  │   Service         │
               │ tenant/          │  │ (internal API)  │  │ LogRetentionTask  │
               └────────┬─────────┘  └────────▲────────┘  └─────────┬─────────┘
                        │                     │                      │
                        │  OpenFeign +        │                      │
                        │  Resilience4j       │                      │
                        │  X-Internal-Secret  │                      │
                        └─────────────────────┘                      │
                        │                                             │
                        │ Kafka Producer (LogAspect)                 │ Kafka Consumer
                        └──────────────────┬──────────────────────────┘
                                           │
                                    ┌──────▼──────┐     ┌──────────────────┐
                                    │    Kafka    │     │  Elasticsearch   │
                                    │    :9092    │     │     :9200        │
                                    └─────────────┘     └──────────────────┘

基础设施：MySQL:3306 | Redis:6379 | Kafka:9092 | Elasticsearch:9200
Maven：services/pom.xml（父 pom）统一管理所有模块版本
```

### 服务一览

| 服务 | 端口 | Java 包 | Maven artifactId | Spring 应用名 | 数据库 |
|------|------|---------|-----------------|-------------|--------|
| `iam-service` | 8081 | `com.starry.iam` | `iam-service` | `iam-service` | `starry_iam` |
| `user-service` | 8082 | `com.starry.user` | `user-service` | `user-service` | `starry_user` |
| `audit-service` | 8083 | `com.starry.audit` | `audit-service` | `audit-service` | ES only |
| `common-lib` | — | `com.starry.common` | `common-lib` | — | — |

---

## 三、服务职责边界

### 3.0 服务边界总矩阵

> 以下两张表是判断任何边界争议的最终依据。代码评审时，任何违反这两张表的实现都应被拒绝。

#### 服务职责矩阵

| 领域 | 主责服务 | 允许其他服务直接写 | 跨服务访问方式 | 明确禁止 |
|------|----------|-------------------|--------------|---------|
| 平台账号（SysUser） | iam-service | **否** | API | 其他服务不得直连 `starry_iam` 中的账号表 |
| 平台角色 / 权限树 | iam-service | **否** | API | 其他服务不得直接操作 role / permission 表 |
| 租户元数据（Tenant） | iam-service | **否** | API | 其他服务不得直连 tenant 表 |
| 系统账号的租户归属 | iam-service | **否** | API | user-service 不得读写 `starry_iam.tenant_user` |
| C 端用户（AppUser） | user-service | **否** | Feign `/api/internal/users/**` | iam-service 不得直连 `starry_user` |
| C 端用户的租户归属 | user-service | **否** | Feign `/api/internal/users/**` | iam-service 查 C 端成员关系必须通过 Feign |
| 审计日志（写） | audit-service | **否**（仅 Kafka） | Kafka Topic `starry-audit-log` | 任何服务不得直接写 ES |
| 审计日志（读） | audit-service | — | Query API `/api/platform/logs` 等 | 任何服务不得直接读 ES `starry_audit_log` 索引 |

#### 数据所有权矩阵

| 存储资源 | 归属服务 | 位置 | 其他服务访问规则 |
|---------|---------|------|----------------|
| `starry_iam`（全部表） | iam-service | MySQL | 禁止跨服务直连；通过 iam-service API 访问 |
| `starry_user`（全部表） | user-service | MySQL | 禁止跨服务直连；通过 Feign 内部接口访问 |
| `starry_audit_log` | audit-service | Elasticsearch | 禁止直连 ES；写入走 Kafka，读取走 Query API |
| `starry-audit-log` Topic | audit-service（消费） | Kafka | iam-service 可生产；任何服务不得绕过 audit-service 直接消费此 Topic |
| Redis `iam:*` | iam-service | Redis | 禁止其他服务直接读写；命名空间隔离 |
| Redis `user:*` | user-service | Redis | 禁止其他服务直接读写；命名空间隔离 |

---

### 3.1 iam-service（Identity & Access Management）

**职责**：系统身份认证 + 访问控制 + 系统账号管理 + 租户/平台治理

```
iam-service/src/main/java/com/starry/iam/
├── IamServiceApplication.java
├── common/                        # 服务级基础设施（非业务）
│   ├── annotation/Log.java        # 审计日志注解
│   ├── aspect/LogAspect.java      # Kafka 生产者 AOP
│   ├── config/
│   │   ├── KafkaConfig.java
│   │   ├── WebConfig.java         # OpenFeign + RestTemplate Bean
│   │   ├── MybatisPlusConfig.java
│   │   ├── SwaggerConfig.java
│   │   └── CacheConfig.java       # Redis CacheManager（新增）
│   ├── filter/
│   │   ├── TraceIdFilter.java
│   │   ├── XssFilter.java
│   │   └── XssHttpServletRequestWrapper.java
│   ├── handler/MyMetaObjectHandler.java
│   ├── health/KafkaHealthIndicator.java
│   ├── interceptor/RateLimitInterceptor.java
│   └── security/
│       ├── CustomUserDetails.java
│       ├── JwtAuthenticationFilter.java
│       ├── JwtUtil.java            # 完整 JWT 创建+验证（iam-service 专用）
│       ├── SecurityConfig.java
│       └── UserDetailsServiceImpl.java
│
└── modules/
    ├── auth/        认证：JWT 签发、刷新、登出（仅保留平台端+租户端两套）
    ├── rbac/        权限：角色、权限树（加 Redis 缓存）
    ├── account/     系统账号：平台账号 CRUD（原 user 模块）
    ├── platform/    平台治理：租户管理、平台角色
    └── tenant/      租户管理：成员、租户角色
```

**为什么 5 个模块聚合在一个服务内**

iam-service 同时承载 auth / rbac / account / platform / tenant，这是**第一阶段的聚合边界**，不是永久的最终边界。聚合的理由如下：

| 理由 | 说明 |
|------|------|
| 共享安全上下文 | 5 个模块共用同一套 `JwtUtil`、`SecurityConfig`、`JwtAuthenticationFilter`，拆开反而要复制这套基础设施 |
| 共享数据库 | 5 个模块的表全部在 `starry_iam` 中，跨库 join 的收益远小于拆服务的成本 |
| 登录与权限强耦合 | auth 模块在签发 JWT 时需要实时查询 rbac 模块的权限树，网络调用延迟不可接受 |
| 当前规模不足 | 5 个模块合计约 150 个类，单服务完全可以管理，过早拆分只增加运维复杂度 |

**未来拆分阈值（明确说明，不是当前目标）**：

| 触发场景 | 拆出的服务 |
|---------|----------|
| 租户治理出现套餐、配额、审批流、生命周期管理 | `tenant-service` |
| 平台治理出现多区域、运营看板、报表等平台级运营能力 | `platform-service` |
| RBAC 演进为跨产品的通用权限平台 | `rbac-service` |

> 在上述场景出现前，任何"感觉应该拆"的冲动都不应该触发拆分。拆分必须有明确的业务驱动，而不是代码整洁驱动。

**彻底删除（不废弃，直接删）**：
- `modules/auth/controller/AuthController.java`（`/api/auth` 旧接口）
- `modules/rbac/controller/DbFixController.java`（调试代码不进生产）
- `modules/log/`（整个目录：日志查询相关全部迁至 audit-service）
- `modules/platform/controller/PlatformLogController.java`
- `modules/tenant/controller/TenantLogController.java`
- `modules/log/entity/SysLogDocument.java`（在 audit-service 中统一定义）

**配置清理**：
- `application.yml` 删除 `spring.elasticsearch.*`（iam-service 不再读 ES）
- `SecurityConfig` 删除 `/api/auth/**` 白名单（对应接口已删除）
- `pom.xml` 删除 `spring-boot-starter-data-elasticsearch`

---

### 3.2 user-service（App User Service）

**职责**：租户应用 C 端用户的完整管理（与 iam-service 系统账号完全隔离）

**两类用户的终极边界说明**：

| 维度 | iam-service / SysUser | user-service / AppUser |
|------|-----------------------|------------------------|
| 称呼 | 系统账号 | C 端用户 / 应用用户 |
| 登录 | 用户名 + 密码 | 手机号 + 密码 |
| 用途 | 登录后台管理系统 | 使用租户的产品 App |
| 数据库 | `starry_iam.platform_user` | `starry_user.app_user` |
| 由谁创建 | 平台管理员手动创建 | 用户自行注册 |

**安全加固**（激进落地）：
- `InternalUserController` 的所有接口必须通过 `InternalAuthFilter` 校验 `X-Internal-Secret` header
- `SecurityConfig` 中 `/api/internal/**` 不再 `permitAll()`，改为由 `InternalAuthFilter` 前置拦截

---

### 3.3 audit-service（Audit Log Service）

**职责**：审计日志完整生命周期（写入、查询、保留策略）—— 日志领域的唯一归属

```
audit-service/src/main/java/com/starry/audit/
├── AuditServiceApplication.java
├── config/
│   ├── SecurityConfig.java    # JWT 验证（只读，不创建 token）
│   └── SwaggerConfig.java
├── consumer/
│   └── LogConsumer.java       # Kafka 消费 → ES 写入
├── controller/
│   └── AuditLogController.java  # GET /api/platform/logs + GET /api/tenant/logs
├── dto/
│   └── LogQueryDto.java
├── entity/
│   └── SysLogDocument.java    # ★ 全项目唯一定义，权威来源
├── repository/
│   └── SysLogRepository.java
├── service/
│   ├── AuditLogQueryService.java
│   └── impl/AuditLogQueryServiceImpl.java
└── task/
    └── LogRetentionTask.java  # 定时清理过期日志（从 iam-service 迁入）
```

**日志领域完整闭环**：
```
iam-service LogAspect ──Kafka──► audit-service LogConsumer ──► Elasticsearch
                                 audit-service AuditLogController ◄── HTTP 查询
                                 audit-service LogRetentionTask ──► 定时清理
```

**audit-service 认证与授权模型**

audit-service 是纯资源服务，使用 `JwtValidator`（来自 common-lib）验证 token，不签发 token。

| 接口 | 调用方身份要求 | tenantId 来源 | 数据范围 |
|------|-------------|-------------|---------|
| `GET /api/platform/logs` | JWT 中 `isPlatform = true` | 可由前端传入（平台管理员可查任意租户） | 全量或按传入的 tenantId 过滤 |
| `GET /api/tenant/logs` | JWT 中 `isPlatform = false` | **强制从 JWT token 读取，忽略前端传入** | 严格限定为 token 中的 tenantId |

> **关键安全约束**：租户端日志查询时，`tenantId` 过滤条件必须由服务端从 JWT 中提取并强制注入，前端传入的 `tenantId` 参数直接忽略。违反此规则会导致租户间数据越权访问。

---

### 3.4 租户成员关系边界声明

租户成员关系是整个系统中最容易重新耦合的边界，必须明确唯一事实来源。

#### 两类 tenant_user 的区分

系统中存在两个同名但含义完全不同的概念：

| 概念 | 表 | 所在数据库 | 含义 | 主责服务 |
|------|---|---------|------|---------|
| 系统账号的租户归属 | `tenant_user` | `starry_iam` | SysUser ↔ Tenant 映射（后台管理员属于哪个租户） | iam-service |
| C 端用户的租户归属 | `tenant_user` | `starry_user` | AppUser ↔ Tenant 映射（C 端用户属于哪个租户） | user-service |

#### 唯一事实来源声明

| 问题 | 唯一事实来源 | 不允许的做法 |
|------|------------|------------|
| 某个系统账号是否属于某租户 | `starry_iam.tenant_user` | 不得从 user-service 查询或推断 |
| 某个 C 端用户是否属于某租户 | `starry_user.tenant_user` | 不得从 iam-service 查询或推断 |
| 某个 C 端用户在租户中的角色 | `starry_user.tenant_user_role` | 不得与 iam-service 的角色表混用 |

#### 跨服务调用规则

- **iam-service 登录验证**：查 `starry_iam.tenant_user`，确认系统账号有权访问该租户
- **user-service 登录验证**：查 `starry_user.tenant_user`，确认 C 端用户属于该租户
- **iam-service 需要查 C 端用户成员信息**：必须通过 Feign 调用 `user-service` 的内部接口，不得直连 `starry_user`
- **任何跨库 join 均被禁止**：`starry_iam` 与 `starry_user` 之间禁止任何形式的跨库关联查询

---

## 四、跨服务契约与 common-lib 规范

### 4.1 契约与实现的分层

多语言微服务架构下，"共享"分两个层次：

| 层次 | 是什么 | 共享方式 | 适用范围 |
|------|--------|---------|---------|
| **契约（Contract）** | JSON 响应结构、Header 名称、错误码、Kafka 消息格式 | 文档约定（本节 4.2）| **所有语言**（Java / Python / Go 等） |
| **实现（Implementation）** | Java 类、Spring Filter、MyBatis-Plus 实体 | Maven 模块 common-lib | **仅 Java 服务** |

> **结论**：common-lib 是 Java 服务对契约的实现载体，不是跨语言共享机制。
> Python 的 agent-service 等异构服务**遵循相同契约**，用各自语言原生方式实现，不依赖 common-lib。

---

### 4.2 跨语言契约规范（所有服务必须遵守）

#### HTTP 响应格式

所有服务的 HTTP 接口统一返回以下 JSON 结构：

```json
{
  "code":      200,
  "message":   "success",
  "data":      {},
  "timestamp": 1710000000000
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | integer | 200 = 成功；4xx = 客户端错误；5xx = 服务端错误 |
| `message` | string | 人可读描述，成功时为 "success" |
| `data` | any | 业务数据，失败时为 null |
| `timestamp` | long | 服务端时间戳（毫秒） |

**Python 实现参考**（Pydantic）：
```python
from pydantic import BaseModel
from typing import Generic, TypeVar, Optional
import time

T = TypeVar("T")

class Result(BaseModel, Generic[T]):
    code: int
    message: str
    data: Optional[T] = None
    timestamp: int = int(time.time() * 1000)

    @classmethod
    def success(cls, data: T = None) -> "Result[T]":
        return cls(code=200, message="success", data=data)

    @classmethod
    def error(cls, code: int, message: str) -> "Result":
        return cls(code=code, message=message)
```

#### 内部服务鉴权 Header

```
Header 名：X-Internal-Secret
值：与环境变量 INTERNAL_SECRET 相同
用途：所有 /api/internal/** 接口的调用方必须携带此 Header
```

所有语言的实现均从环境变量 `INTERNAL_SECRET` 读取，不硬编码。

#### JWT 声明结构

iam-service 签发的 JWT payload 结构，所有需要验证 JWT 的服务（含 Python）遵循：

```json
{
  "sub":          "用户标识（平台端=username，租户端=mobile）",
  "userId":       12345,
  "isPlatform":   true,
  "tenantId":     67890,
  "isTenantAdmin": false,
  "authorities":  ["permission:key:1", "permission:key:2"],
  "tokenVersion": 1,
  "jti":          "唯一 token ID（用于黑名单）",
  "iat":          1710000000,
  "exp":          1710007200
}
```

**Python 验证参考**（PyJWT）：
```python
import jwt

def validate_token(token: str, secret: str) -> dict:
    return jwt.decode(token, secret, algorithms=["HS256"])
```

#### JWT Claims 消费矩阵

明确每个 claim 由谁写入、被哪些服务消费，避免服务依赖隐式假设。

| Claim | 含义 | 写入方 | iam-service | user-service | audit-service |
|-------|------|--------|:-----------:|:------------:|:-------------:|
| `sub` | 用户标识（用户名/手机号） | iam-service | 身份标识 | — | 日志记录操作人 |
| `userId` | 用户数据库 ID | iam-service | 业务关联 | 查询 AppUser 关系 | 日志中的 userId |
| `isPlatform` | 平台端 / 租户端标识 | iam-service | 路由分支判断 | 隔离业务逻辑 | **控制查询范围** |
| `tenantId` | 当前租户 ID | iam-service | 租户上下文设置 | 成员关系验证 | **强制租户隔离** |
| `isTenantAdmin` | 是否租户管理员 | iam-service | 管理权限判断 | 业务权限控制 | — |
| `authorities` | 权限 key 列表 | iam-service | 方法级鉴权 | — | 平台端日志接口鉴权 |
| `tokenVersion` | token 版本号（修改密码/踢出时递增） | iam-service | Redis 黑名单比对 | — | — |
| `jti` | token 唯一 ID | iam-service | 主动登出黑名单键 | — | — |
| `iat` / `exp` | 签发时间 / 过期时间 | iam-service | 标准验证 | 标准验证 | 标准验证 |

> **user-service 和 audit-service 只验证不信任**：两者均使用 `JwtValidator.parseToken()` 提取 claims，不假设任何 claim 一定存在，必须做 null 检查。

#### Kafka 消息格式（审计日志）

Topic `starry-audit-log` 的消息体：

```json
{
  "traceId":    "uuid",
  "userId":     12345,
  "tenantId":   67890,
  "isPlatform": false,
  "username":   "操作人",
  "module":     "模块名",
  "action":     "动作名",
  "ip":         "127.0.0.1",
  "params":     "请求参数（限2000字符）",
  "result":     "返回结果（限2000字符）",
  "errorMsg":   null,
  "status":     "SUCCESS",
  "costTime":   123,
  "createTime": "2026-03-19T10:00:00"
}
```

异构服务如需写入审计日志，直接向 `starry-audit-log` 发送此格式的 JSON 消息即可，audit-service 统一消费。

---

### 4.3 common-lib（Java 服务专用）

**Maven 坐标**：`com.starry:common-lib:1.0.0-SNAPSHOT`

**适用范围**：iam-service、user-service、audit-service 及未来所有 **Java** 服务。

```
common-lib/src/main/java/com/starry/common/
├── result/
│   └── Result.java              # 4.2 响应格式的 Java 实现
├── context/
│   └── TenantContext.java       # ThreadLocal 租户上下文（Java 线程模型专用）
├── exception/
│   └── BusinessException.java   # 业务异常基类
├── entity/
│   └── BaseEntity.java          # MyBatis-Plus 公共字段（ORM 专用）
├── security/
│   ├── InternalAuthConstants.java   # 4.2 Header 名称的 Java 常量定义
│   ├── InternalAuthFilter.java      # 内部接口鉴权过滤器（Spring 专用）
│   └── JwtValidator.java            # 4.2 JWT 验证的 Java 实现（资源服务用）
└── utils/
    └── IpUtil.java              # IP 工具
```

### JwtValidator vs JwtUtil 分工

| 类 | 位置 | 职责 |
|----|------|------|
| `JwtUtil` | `iam-service/common/security/` | 创建 + 验证 JWT（完整功能，含平台/租户/pre-token） |
| `JwtValidator` | `common-lib/security/` | **仅验证** JWT 签名和有效期，提取 Claims（资源服务使用） |

### 使用规范（Java 服务）

- `Result<T>` 是所有 HTTP 接口的唯一返回类型，不在各服务中重复定义
- `TenantContext` 由 JWT Filter 在请求开始设置，`finally` 块中 `clear()`
- `InternalAuthFilter` 直接从 common-lib 使用，各 Java 服务无需重写
- `BusinessException` 由各服务自己的 `GlobalExceptionHandler` 统一处理

### common-lib 准入规则

**进入 common-lib 必须同时满足以下三条**：

| 条件 | 说明 |
|------|------|
| ① 多个服务确实都需要 | 至少 2 个现有服务用到，不以"将来可能用"为由纳入 |
| ② 语义稳定 | 不会因单个服务的业务变化而频繁修改；一旦修改就会触发所有服务同步升级 |
| ③ 契约性质 | 定义的是接口约定 / 安全机制，而不是业务逻辑实现 |

**以下类型禁止进入 common-lib**：

- 只有 1 个服务使用的工具类（即使代码看起来"通用"）
- 含有业务判断逻辑的代码（如"租户是否有效"、"用户是否有权限"）
- 与特定框架深度耦合且容易随业务变化的实现

**当前 common-lib 内容的准入说明**：

| 类 | 类型 | 准入依据 |
|----|------|---------|
| `Result` | 契约性 | 所有服务的 HTTP 响应格式，语义极稳定 |
| `InternalAuthFilter` | 契约+安全 | 所有服务的内部接口鉴权机制一致 |
| `JwtValidator` | 安全机制 | user-service 和 audit-service 均需验证 JWT |
| `InternalAuthConstants` | 常量契约 | Header 名不能各自定义 |
| `IpUtil` | 工具 | 多服务需要获取客户端 IP，逻辑稳定 |
| `BaseEntity` | 框架耦合型 ⚠️ | 暂时纳入，因 3 个服务都用 MyBatis-Plus；若未来某服务迁移 ORM，需从 common-lib 移除 |
| `TenantContext` | 框架耦合型 ⚠️ | 基于 Java `ThreadLocal`，异步场景下存在传播问题；若引入 WebFlux / 虚拟线程，需重新评估 |
| `BusinessException` | 框架耦合型 ⚠️ | 与 Spring `GlobalExceptionHandler` 配合使用；各服务已有独立 handler，改动风险可控 |

> ⚠️ 标记的类是"当前可接受，但要警惕"：任何修改这三个类的 PR 都需要评估对所有服务的影响。

---

## 五、服务间通信规范

### 5.1 同步调用：OpenFeign + Resilience4j

iam-service 调用 user-service 从 `RestTemplate` 升级为 **声明式 Feign Client + 熔断降级**。

**为什么不继续使用封装后的 RestTemplate**

| 问题 | RestTemplate 的局限 | OpenFeign 的收益 |
|------|-------------------|----------------|
| 接口契约模糊 | 调用方自己拼 URL 和参数，user-service 接口变更时无编译期提示 | 接口定义为 Java interface，变更有编译器保护 |
| Header 注入分散 | `X-Internal-Secret` 需在每个调用点手动添加，容易遗漏 | `InternalFeignConfig` 统一注入，一处配置全局生效 |
| 调用点不收敛 | 分散在各 Service 实现类中，难以整体替换 | 所有调用通过 `UserServiceClient` 一个入口，替换时只需 mock 此接口 |
| 熔断需手写 | 需要手动 try-catch + 超时控制，逻辑散落 | Resilience4j 通过注解声明，与业务代码解耦 |
| 未来扩展成本高 | 新增被调服务需要重复写 URL 拼接、错误处理等样板代码 | 新增 FeignClient interface 即可，遵循同一模式 |

> **为什么现在就引入而不是"等规模大了再升级"**：项目未上线，一次性引入的成本最低；若先上 RestTemplate 再迁 Feign，需要同时回归所有调用链路，风险更高。

**iam-service 新增**：

```java
// client/UserServiceClient.java
@FeignClient(name = "user-service", url = "${internal.user-service.url}",
             configuration = InternalFeignConfig.class,
             fallbackFactory = UserServiceClientFallbackFactory.class)
public interface UserServiceClient {
    @PostMapping("/api/internal/users/verify")
    Result<Map<String, Object>> verifyCredentials(@RequestBody VerifyRequest request);

    @GetMapping("/api/internal/users/{userId}/tenants")
    Result<List<Long>> getUserTenants(@PathVariable Long userId);

    @GetMapping("/api/internal/users/by-mobile")
    Result<Map<String, Object>> getUserByMobile(@RequestParam String mobile);

    @PostMapping("/api/internal/users/ensure")
    Result<Map<String, Object>> ensureUser(@RequestBody EnsureUserRequest request);
}

// config/InternalFeignConfig.java（自动注入 X-Internal-Secret header）
public class InternalFeignConfig implements RequestInterceptor {
    @Value("${INTERNAL_SECRET}")
    private String secret;

    @Override
    public void apply(RequestTemplate template) {
        template.header("X-Internal-Secret", secret);
    }
}

// fallback/UserServiceClientFallbackFactory.java
@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {
    @Override
    public UserServiceClient create(Throwable cause) {
        return new UserServiceClient() {
            // 降级实现：登录失败提示"用户服务暂不可用"
        };
    }
}
```

**Resilience4j 配置**：
```yaml
resilience4j:
  circuitbreaker:
    instances:
      user-service:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
  timelimiter:
    instances:
      user-service:
        timeoutDuration: 3s
```

### 5.2 异步调用：Kafka

```
生产者：iam-service / LogAspect
  Topic：starry-audit-log
  序列化：JsonSerializer（SysLogDocument）

消费者：audit-service / LogConsumer
  Group：audit-service-group
  模式：批量消费

类型映射（Kafka 消息头 __TypeId__ 兼容）：
  spring.json.type.mapping:
    com.starry.admin.modules.log.entity.SysLogDocument:com.starry.audit.entity.SysLogDocument
    com.starry.admin.log.entity.SysLogDocument:com.starry.audit.entity.SysLogDocument
```

### 5.3 内部接口鉴权

所有 `/api/internal/**` 接口统一流程：

```
调用方（iam-service）                     被调方（user-service）
┌──────────────────────────────┐          ┌──────────────────────────────┐
│ InternalFeignConfig          │          │ InternalAuthFilter           │
│ (自动注入 X-Internal-Secret) │ ───────► │ 校验 X-Internal-Secret header│
│                              │          │ 不匹配 → 401 立即返回        │
│ Resilience4j 超时 3s         │          │ 匹配 → 放行到 Controller     │
│ 熔断 failRate > 50%          │          └──────────────────────────────┘
└──────────────────────────────┘
```

`INTERNAL_SECRET` 通过 Docker 环境变量注入，不写死在代码中。

---

## 六、API 路由规范

### 6.1 当前路由（维持不变，仅调整 upstream）

前端不改动，只在 Nginx 层修正路由归属：

| 路径前缀 | 变更前 upstream | 变更后 upstream | 说明 |
|---------|----------------|----------------|------|
| `/api/platform/logs` | `admin_service` | `audit_service` | **日志路由迁至 audit-service** |
| `/api/tenant/logs` | `admin_service` | `audit_service` | **日志路由迁至 audit-service** |
| `/api/platform/*` | `admin_service` | `iam_service` | upstream 改名 |
| `/api/tenant/*` | `admin_service` | `iam_service` | upstream 改名 |
| `/api/rbac/*` | 缺失 | `iam_service` | **补全缺失路由** |
| `/api/auth/*` | `admin_service` | **删除** | 对应接口已删除 |
| `/api/v1/*` | `user_service` | `user_service` | 不变 |
| `/api/user/*` | `user_service` | `user_service` | 不变 |
| `/api/internal/*` | 不应暴露 | **删除** | 仅 Docker 内网 |

### 6.2 新增服务路由规范（未来 agent-service 等）

```nginx
# 新服务只需添加：
upstream agent_service { server agent-service:8090; }
location ^~ /api/agent/ { proxy_pass http://agent_service; }
```

---

## 七、基础设施设计

### 7.1 Maven 多模块结构

```
services/
├── pom.xml          ← 父 pom，统一管理所有版本
├── common-lib/
├── iam-service/     ← 声明 parent: starry-services
├── user-service/    ← 声明 parent: starry-services
└── audit-service/   ← 声明 parent: starry-services
```

父 pom 管理版本：Spring Boot 3.2.1、Java 17、MyBatis-Plus 3.5.5、JWT 0.11.5、Lombok 1.18.32、OpenFeign、Resilience4j

### 7.2 数据库 / 索引 / Topic 命名规范

> 所有存储资源名称必须与所属服务的 domain 名一一对应，消除歧义。统一前缀 `starry_`。

#### MySQL 数据库

| 服务 | 旧库名 | **新库名** | 变更 |
|------|--------|-----------|------|
| iam-service | `admin_system` | **`starry_iam`** | 重命名 |
| user-service | `trae_user` | **`starry_user`** | 重命名 |

#### Elasticsearch 索引

| 服务 | 旧索引名 | **新索引名** | 变更 |
|------|---------|------------|------|
| audit-service | `sys_log` | **`starry_audit_log`** | 重命名 |

#### Kafka Topic

| 生产者 | 消费者 | 旧 Topic | **新 Topic** | 变更 |
|--------|--------|---------|------------|------|
| iam-service (LogAspect) | audit-service (LogConsumer) | `sys-log-topic` | **`starry-audit-log`** | 重命名 |

#### 命名规则

```
MySQL 数据库：starry_{domain}          → starry_iam, starry_user
ES 索引：     starry_{domain}_{entity} → starry_audit_log
Kafka Topic： starry-{domain}-{event}  → starry-audit-log
```

- 所有名称小写，MySQL / ES 用下划线，Kafka 用连字符
- 不使用 `admin`、`system`、`log` 等模糊语义词，必须体现 domain

---

### 7.3 docker-compose 服务拓扑

```
nginx-gateway
  ├── iam-service (依赖: mysql[starry_iam], redis, kafka, user-service)
  ├── user-service (依赖: mysql[starry_user], redis)
  ├── audit-service (依赖: kafka[starry-audit-log], elasticsearch[starry_audit_log], redis)
  └── frontend

基础设施（互相独立启动）：
  mysql, redis, kafka, zookeeper, elasticsearch
```

**删除的依赖**：
- `iam-service` 不再依赖 `elasticsearch`（日志查询移走）

**新增的依赖**：
- `audit-service` 新增依赖 `redis`（JWT 黑名单查询）

**新增环境变量**：
- `INTERNAL_SECRET`（所有服务共享，通过 .env 注入）

### 7.4 环境变量规范

新建 `.env.example`（根目录，提交到 git）：
```bash
# 内部服务鉴权密钥（所有服务共享）
INTERNAL_SECRET=change-me-in-production

# JWT 密钥（所有服务共享，需与签发方一致）
JWT_SECRET=starry-admin-system-secret-key-must-be-very-long-and-secure-and-safe

# 数据库
MYSQL_PASSWORD=your-mysql-password
```

`.env`（不提交，本地使用）从 `.env.example` 复制后填写。

---

## 八、扩展性设计

### 8.1 新增服务标准步骤（5 步）

1. 创建 `services/{name}-service/`，pom.xml 指向父 pom，依赖 `common-lib`
2. 包名 `com.starry.{name}`，主类 `{Name}ServiceApplication`
3. `docker-compose.yml` 新增服务定义，加入 `starry-net`
4. `nginx.conf` 新增 upstream + location（`/api/{name}/`）
5. `services/pom.xml` `<modules>` 中添加 `<module>{name}-service</module>`

不需要修改任何现有服务代码。

### 8.2 服务命名约定

所有维度以 `{domain}` 为唯一锚点，确保从代码到基础设施全链路名实一致。

#### 应用层

| 维度 | 规范 | 示例（domain=iam）|
|------|------|-----------------|
| 目录名 | `{domain}-service`（小写连字符）| `iam-service` |
| Docker 服务名 | 同目录名 | `iam-service` |
| Java 包名 | `com.starry.{domain}` | `com.starry.iam` |
| Maven artifactId | `{domain}-service` | `iam-service` |
| Spring 应用名 | `{domain}-service` | `iam-service` |
| 主类名 | `{Domain}ServiceApplication` | `IamServiceApplication` |
| Nginx upstream | `{domain}_service`（下划线）| `iam_service` |
| API 前缀 | `/api/{scope}/`（按业务范围）| `/api/platform/`, `/api/tenant/` |

#### 存储层（与应用层 domain 强绑定）

| 维度 | 规范 | 示例 |
|------|------|------|
| MySQL 数据库名 | `starry_{domain}` | `starry_iam`, `starry_user` |
| Elasticsearch 索引 | `starry_{domain}_{entity}` | `starry_audit_log` |
| Kafka Topic | `starry-{domain}-{event}`（连字符）| `starry-audit-log` |
| Redis key 前缀 | `{domain}:`（冒号分隔）| `iam:token:`, `user:session:` |

> **约束**：禁止使用 `admin`、`system`、`log`、`service` 等无领域含义的词作为存储资源名称的核心词。

### 8.3 演进路径（当前不引入）

| 规模 | 可引入 | 触发时机 |
|------|--------|---------|
| 服务 ≥ 6 | Spring Boot Admin（服务监控） | 运维复杂度上升 |
| 服务 ≥ 8 | Spring Cloud Gateway（取代 Nginx） | 路由规则复杂化 |
| 服务 ≥ 10 | Service Mesh（Istio） | 流量治理、链路追踪需求 |

---

## 九、统一术语表

| 术语 | 定义 | 对应位置 |
|------|------|---------|
| **SysUser** / 系统账号 | 登录后台管理系统的账号（平台管理员/租户管理员） | `com.starry.iam.modules.account.entity.SysUser` |
| **AppUser** / C端用户 | 租户应用的终端注册用户 | `com.starry.user.entity.AppUser` |
| **PlatformAdmin** / 平台管理员 | 可管理所有租户的超级管理员 | `SysUser.isSuper = true` |
| **TenantAdmin** / 租户管理员 | 单租户内的管理员 | `TenantUser.isTenantAdmin = true` |
| **AuditLog** / 审计日志 | 系统操作记录（Kafka → ES） | `com.starry.audit.entity.SysLogDocument` |
| **IAM** | Identity & Access Management | iam-service 全部职责 |
| **内部接口** | 服务间调用专用接口，不经过 Nginx | `/api/internal/**`，需 X-Internal-Secret |
