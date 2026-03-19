# 目标微服务架构设计（激进重建版）

> 版本：v2.0 | 设计日期：2026-03-19 | 策略：一次性全量落地，不保留任何过渡代码
> 前置：[01-diagnosis.md](./01-diagnosis.md)

---

## 目录

1. [核心原则](#一核心原则)
2. [目标架构总览](#二目标架构总览)
3. [服务职责边界](#三服务职责边界)
4. [common-lib 规范](#四common-lib-规范)
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

---

## 五、服务间通信规范

### 5.1 同步调用：OpenFeign + Resilience4j

iam-service 调用 user-service 从 `RestTemplate` 升级为 **声明式 Feign Client + 熔断降级**。

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
