# 微服务架构迁移执行计划（激进全量版）

> 版本：v2.0 | 制定日期：2026-03-19
> 策略：**一次性全量落地**，所有步骤均在本阶段完成，无过渡代码
> 前置：[02-target-architecture.md](./02-target-architecture.md)

---

## 整体变更地图

```
【删除】                          【重命名】                     【新建】
──────────────────────────        ─────────────────────────     ─────────────────────────────
AuthController.java               admin-service → iam-service   services/pom.xml（父pom）
DbFixController.java              log-service → audit-service   services/common-lib/
PlatformLogController.java        com.starry.admin → com.starry.iam services/common-lib/.../Result.java
TenantLogController.java          com.starry.admin.log → c.t.audit com.starry.common.../TenantContext.java
admin/modules/log/（整目录）       modules/user → modules/account common-lib/.../JwtValidator.java
admin/SysLogDocument.java         BackendApplication → Iam...App common-lib/.../InternalAuthFilter.java
user/.../Result.java（本地副本）                                 audit/.../AuditLogController.java
user/.../TenantContext.java       【迁移】                        audit/.../AuditLogQueryService.java
user/.../BusinessException.java   LogRetentionTask → audit-svc   audit/.../SecurityConfig.java
Nginx /api/auth/* 路由            PlatformLogController → audit   user/.../InternalAuthFilter.java（废弃，用common-lib版）
Nginx /api/internal/* 路由        TenantLogController → audit     iam/.../UserServiceClient.java（Feign）
                                  LogService → AuditLogQuery-     iam/.../CacheConfig.java
                                  Service（改名+迁移）            iam/modules/rbac/.../CacheConfig.java
                                  LogAspect（包名更新，保留原位）  services/.env.example
```

---

## 执行顺序与依赖

```
Step 1: services/pom.xml + common-lib（其他所有步骤的基础）
  │
  ├─► Step 2: audit-service（rename + 日志查询迁入）
  │
  ├─► Step 3: iam-service（rename + 包重命名 + 删除遗留代码 + 切换 OpenFeign）
  │
  ├─► Step 4: user-service（内部接口鉴权加固 + 切换 common-lib 类型）
  │
  └─► Step 5: 基础设施（docker-compose + nginx + .env.example）

Step 2/3/4 之间没有顺序依赖，可以并行，但都依赖 Step 1 完成。
Step 5 依赖 Step 2/3/4 全部完成。
```

---

## Step 1：services/pom.xml + common-lib

### 1.1 新建 `services/pom.xml`（父 pom）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.1</version>
        <relativePath/>
    </parent>

    <groupId>com.starry</groupId>
    <artifactId>starry-services</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>starry-services</name>
    <description>Trae Microservices Parent POM</description>

    <modules>
        <module>common-lib</module>
        <module>iam-service</module>
        <module>user-service</module>
        <module>audit-service</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <mybatis-plus.version>3.5.5</mybatis-plus.version>
        <jjwt.version>0.11.5</jjwt.version>
        <springdoc.version>2.2.0</springdoc.version>
        <lombok.version>1.18.32</lombok.version>
        <resilience4j.version>2.1.0</resilience4j.version>
        <openfeign.version>4.1.0</openfeign.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.starry</groupId>
                <artifactId>common-lib</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-api</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-impl</artifactId>
                <version>${jjwt.version}</version>
                <scope>runtime</scope>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-jackson</artifactId>
                <version>${jjwt.version}</version>
                <scope>runtime</scope>
            </dependency>
            <dependency>
                <groupId>org.springdoc</groupId>
                <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
                <version>${springdoc.version}</version>
            </dependency>
            <dependency>
                <groupId>io.github.resilience4j</groupId>
                <artifactId>resilience4j-spring-boot3</artifactId>
                <version>${resilience4j.version}</version>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-starter-openfeign</artifactId>
                <version>${openfeign.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### 1.2 新建 `services/common-lib/`

**目录结构**：
```
services/common-lib/
├── pom.xml
└── src/main/java/com/starry/common/
    ├── result/Result.java
    ├── context/TenantContext.java
    ├── exception/BusinessException.java
    ├── entity/BaseEntity.java
    ├── security/
    │   ├── InternalAuthConstants.java
    │   ├── InternalAuthFilter.java
    │   └── JwtValidator.java
    └── utils/IpUtil.java
```

**common-lib/pom.xml**：
```xml
<parent>
    <groupId>com.starry</groupId>
    <artifactId>starry-services</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
</parent>
<artifactId>common-lib</artifactId>
<name>common-lib</name>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

**Result.java**（从 admin-service 复制，更新 package）：
```java
package com.starry.common.result;
// 内容与 admin-service/common/result/Result.java 相同
// 包含：Integer code, String message, T data, Long timestamp
// 静态工厂：success(), success(data), error(code, message)
```

**TenantContext.java**（从 admin-service 复制，更新 package）：
```java
package com.starry.common.context;
// ThreadLocal<Long> TENANT_ID_HOLDER，setTenantId / getTenantId / clear
```

**BusinessException.java**（从 admin-service 复制，更新 package）：
```java
package com.starry.common.exception;
// 业务异常：Integer code + String message，继承 RuntimeException
```

**BaseEntity.java**（从 admin-service 复制，更新 package）：
```java
package com.starry.common.entity;
// MyBatis-Plus 公共字段：createTime, updateTime, isDeleted（逻辑删除）
```

**InternalAuthConstants.java**（新建）：
```java
package com.starry.common.security;

public final class InternalAuthConstants {
    /** 内部服务间调用鉴权 header 名 */
    public static final String HEADER_NAME = "X-Internal-Secret";
    /** 对应的环境变量 key */
    public static final String ENV_KEY     = "INTERNAL_SECRET";

    private InternalAuthConstants() {}
}
```

**InternalAuthFilter.java**（新建，可被所有服务复用）：
```java
package com.starry.common.security;

/**
 * 内部接口鉴权过滤器。
 * 对 /api/internal/** 路径校验 X-Internal-Secret header。
 * 使用方：在各服务的 SecurityConfig 中注册，放在 JWT filter 之前。
 */
public class InternalAuthFilter extends OncePerRequestFilter {

    private final String internalSecret;

    public InternalAuthFilter(String internalSecret) {
        this.internalSecret = internalSecret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/internal");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws IOException, ServletException {
        String headerSecret = request.getHeader(InternalAuthConstants.HEADER_NAME);
        if (headerSecret == null || !MessageDigest.isEqual(
                headerSecret.getBytes(StandardCharsets.UTF_8),
                internalSecret.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                "{\"code\":401,\"message\":\"Unauthorized internal request\",\"data\":null}");
            return;
        }
        chain.doFilter(request, response);
    }
}
```

**JwtValidator.java**（新建，供资源服务使用）：
```java
package com.starry.common.security;

/**
 * JWT 验证工具（仅验证+解析，不创建 token）。
 * 供 audit-service、user-service 等资源服务使用。
 * token 创建由 iam-service 的 JwtUtil 负责。
 */
@Component
public class JwtValidator {
    private final Key signingKey;

    public JwtValidator(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 验证 token 有效性，返回 Claims；无效抛出异常 */
    public Claims validateAndGetClaims(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    /** 判断是否平台端 token */
    public boolean isPlatformToken(Claims claims) {
        return Boolean.TRUE.equals(claims.get("isPlatform", Boolean.class));
    }

    /** 提取租户 ID */
    public Long getTenantId(Claims claims) {
        Object tenantId = claims.get("tenantId");
        return tenantId != null ? Long.valueOf(tenantId.toString()) : null;
    }

    /** 提取用户 ID */
    public Long getUserId(Claims claims) {
        Object userId = claims.get("userId");
        return userId != null ? Long.valueOf(userId.toString()) : null;
    }
}
```

**IpUtil.java**（从 admin-service 复制，更新 package）。

---

## Step 2：log-service → audit-service

### 2.1 目录重命名

```bash
mv services/log-service services/audit-service
```

### 2.2 pom.xml 完整替换

```xml
<parent>
    <groupId>com.starry</groupId>
    <artifactId>starry-services</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
</parent>

<artifactId>audit-service</artifactId>
<name>audit-service</name>

<dependencies>
    <dependency><groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-data-elasticsearch</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-data-redis</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>org.springframework.kafka</groupId>
                <artifactId>spring-kafka</artifactId></dependency>
    <dependency><groupId>org.springdoc</groupId>
                <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId></dependency>
    <dependency><groupId>com.starry</groupId>
                <artifactId>common-lib</artifactId></dependency>
    <dependency><groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId></dependency>
</dependencies>
```

### 2.3 Java 包重命名

**批量替换**：`com.starry.admin.log` → `com.starry.audit`

**目录迁移**：
```bash
mkdir -p services/audit-service/src/main/java/com/starry/audit
mv services/audit-service/src/main/java/com/starry/admin/log/* \
   services/audit-service/src/main/java/com/starry/audit/
rm -rf services/audit-service/src/main/java/com/starry/admin
```

**主类**：`LogServiceApplication.java` → `AuditServiceApplication.java`
```java
package com.starry.audit;

@SpringBootApplication
@EnableScheduling
public class AuditServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
```

### 2.4 SysLogDocument 包路径更新

`com/starry/audit/entity/SysLogDocument.java`：
```java
package com.starry.audit.entity;  // 更新 package，内容不变
```

`SysLogRepository.java`、`LogConsumer.java` 同步更新 import。

### 2.5 新建日志查询 API（从 iam-service 迁入）

**dto/LogQueryDto.java**（迁自 iam-service，更新 package）：
```java
package com.starry.audit.dto;
// 字段：userId, tenantId, isPlatform, username, module, action, status,
//       startTime, endTime, page, pageSize
```

**service/AuditLogQueryService.java**（接口）：
```java
package com.starry.audit.service;

public interface AuditLogQueryService {
    Page<SysLogDocument> page(LogQueryDto queryDto);
}
```

**service/impl/AuditLogQueryServiceImpl.java**：
迁自 `iam-service/modules/log/service/impl/LogServiceImpl.java`，更新所有包引用。

**controller/AuditLogController.java**（合并原 PlatformLogController + TenantLogController）：
```java
package com.starry.audit.controller;

@RestController
@Tag(name = "审计日志")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    /** 平台端：查询所有日志（需要平台 JWT） */
    @GetMapping("/api/platform/logs")
    @PreAuthorize("hasAuthority('platform:log:list')")
    public Result<Page<SysLogDocument>> platformLogs(LogQueryDto queryDto) {
        queryDto.setIsPlatform(true);
        return Result.success(auditLogQueryService.page(queryDto));
    }

    /** 租户端：查询本租户日志（tenantId 从 JWT 强制注入） */
    @GetMapping("/api/tenant/logs")
    public Result<Page<SysLogDocument>> tenantLogs(
            LogQueryDto queryDto,
            @AuthenticationPrincipal JwtAuthenticationToken token) {
        Long tenantId = jwtValidator.getTenantId((Claims) token.getCredentials());
        queryDto.setTenantId(tenantId);
        queryDto.setIsPlatform(false);
        return Result.success(auditLogQueryService.page(queryDto));
    }
}
```

**config/SecurityConfig.java**（新建，使用 common-lib JwtValidator）：
```java
package com.starry.audit.config;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${" + InternalAuthConstants.ENV_KEY + ":change-me}")
    private String internalSecret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            JwtValidator jwtValidator) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new InternalAuthFilter(internalSecret),
                             UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new AuditJwtAuthFilter(jwtValidator),
                             UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

**task/LogRetentionTask.java**（迁自 iam-service，更新 package）：
```java
package com.starry.audit.task;
// 定时清理过期 ES 日志，内容从 iam-service 迁入
```

### 2.6 application.yml 完整内容

```yaml
server:
  port: 8083

spring:
  application:
    name: audit-service
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: audit-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.starry.*"
        spring.json.type.mapping: >
          com.starry.admin.modules.log.entity.SysLogDocument:com.starry.audit.entity.SysLogDocument,
          com.starry.admin.log.entity.SysLogDocument:com.starry.audit.entity.SysLogDocument
    listener:
      type: batch
      concurrency: 1
  elasticsearch:
    uris: ${ES_URIS:http://localhost:9200}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

jwt:
  secret: ${JWT_SECRET:starry-admin-system-secret-key-must-be-very-long-and-secure-and-safe}

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

---

## Step 3：admin-service → iam-service

### 3.1 目录重命名

```bash
mv services/admin-service services/iam-service
```

### 3.2 pom.xml 更新

**修改内容**：
- 添加 `<parent>` 指向 `starry-services`
- `artifactId`: `admin-service` → `iam-service`
- 删除 `spring-boot-starter-data-elasticsearch`（日志查询职责移走）
- 添加 `spring-cloud-starter-openfeign`（取代 RestTemplate）
- 添加 `resilience4j-spring-boot3`（熔断降级）
- 添加 `common-lib`

```xml
<dependencies>
    <!-- 删除 -->
    <!-- <dependency>spring-boot-starter-data-elasticsearch</dependency> -->

    <!-- 新增 -->
    <dependency>
        <groupId>com.starry</groupId>
        <artifactId>common-lib</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
    </dependency>

    <!-- 保留所有其他原有依赖 -->
</dependencies>
```

### 3.3 批量包重命名：`com.starry.admin` → `com.starry.iam`

**操作**（~150 个 .java 文件）：
```bash
# 1. 移动目录
mv services/iam-service/src/main/java/com/starry/admin \
   services/iam-service/src/main/java/com/starry/iam

# 2. 批量替换包声明和 import
find services/iam-service/src -name "*.java" \
  -exec sed -i 's/com\.starry\.admin/com.starry.iam/g' {} \;
```

### 3.4 主类重命名

```
BackendApplication.java → IamServiceApplication.java
```
```java
package com.starry.iam;

@SpringBootApplication
@EnableFeignClients
@EnableCaching
public class IamServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(IamServiceApplication.class, args);
    }
}
```

### 3.5 模块重命名：`modules/user` → `modules/account`

```bash
mv services/iam-service/src/main/java/com/starry/iam/modules/user \
   services/iam-service/src/main/java/com/starry/iam/modules/account

# 批量替换包路径
find services/iam-service/src/main/java/com/starry/iam/modules/account \
  -name "*.java" \
  -exec sed -i 's/com\.starry\.iam\.modules\.user/com.starry.iam.modules.account/g' {} \;
```

### 3.6 删除遗留代码（直接删除，无过渡）

```bash
# 删除日志查询整个模块（迁至 audit-service）
rm -rf services/iam-service/src/main/java/com/starry/iam/modules/log/

# 删除 PlatformLogController（日志查询移走）
rm services/iam-service/src/main/java/com/starry/iam/modules/platform/controller/PlatformLogController.java

# 删除 TenantLogController
rm services/iam-service/src/main/java/com/starry/iam/modules/tenant/controller/TenantLogController.java

# 删除旧版 AuthController（/api/auth 接口废弃，直接删除）
rm services/iam-service/src/main/java/com/starry/iam/modules/auth/controller/AuthController.java

# 删除 DbFixController（调试代码不进任何生产 profile）
rm services/iam-service/src/main/java/com/starry/iam/modules/rbac/controller/DbFixController.java
```

**同步从 SecurityConfig 中删除**：
- `/api/auth/**` 白名单（对应 Controller 已删）
- `AuthController` 相关路径

### 3.7 RestTemplate → OpenFeign 替换

**删除**：`WebConfig.java` 中的 `RestTemplate` Bean（或保留 Bean 但清空 iam→user 的 RestTemplate 调用）

**新建**：`client/UserServiceClient.java`
```java
package com.starry.iam.client;

@FeignClient(
    name = "user-service",
    url = "${internal.user-service.url:http://user-service:8082}",
    configuration = InternalFeignConfig.class,
    fallbackFactory = UserServiceClientFallbackFactory.class
)
public interface UserServiceClient {

    @PostMapping("/api/internal/users/verify")
    Result<Map<String, Object>> verifyCredentials(@RequestBody Map<String, String> body);

    @GetMapping("/api/internal/users/by-mobile")
    Result<Map<String, Object>> getUserByMobile(@RequestParam("mobile") String mobile);

    @GetMapping("/api/internal/users/{userId}/tenants")
    Result<List<Long>> getUserTenants(@PathVariable("userId") Long userId);

    @GetMapping("/api/internal/users/{userId}/tenant-memberships")
    Result<List<Map<String, Object>>> getTenantMemberships(@PathVariable("userId") Long userId);

    @PostMapping("/api/internal/users/ensure")
    Result<Map<String, Object>> ensureUser(@RequestBody Map<String, Object> body);

    @GetMapping("/api/internal/users/{userId}/tenant-admin")
    Result<Boolean> isTenantAdmin(@PathVariable("userId") Long userId,
                                   @RequestParam("tenantId") Long tenantId);

    @PostMapping("/api/internal/tenant-users")
    Result<Void> createTenantUser(@RequestBody Map<String, Object> body);

    @DeleteMapping("/api/internal/tenant-users")
    Result<Void> deleteTenantUser(@RequestParam("userId") Long userId,
                                   @RequestParam("tenantId") Long tenantId);
}
```

**新建**：`client/InternalFeignConfig.java`
```java
package com.starry.iam.client;

public class InternalFeignConfig implements RequestInterceptor {
    @Value("${" + InternalAuthConstants.ENV_KEY + ":change-me}")
    private String internalSecret;

    @Override
    public void apply(RequestTemplate template) {
        template.header(InternalAuthConstants.HEADER_NAME, internalSecret);
    }
}
```

**新建**：`client/UserServiceClientFallbackFactory.java`
```java
package com.starry.iam.client;

@Component
@Slf4j
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {
    @Override
    public UserServiceClient create(Throwable cause) {
        log.error("user-service call failed: {}", cause.getMessage());
        return new UserServiceClient() {
            @Override
            public Result<Map<String, Object>> verifyCredentials(Map<String, String> body) {
                throw new BusinessException(503, "用户服务暂不可用，请稍后重试");
            }
            // 其他方法类似处理
        };
    }
}
```

**更新**：`TenantAuthServiceImpl.java` —— 将所有 `restTemplate.xxx()` 替换为 `userServiceClient.xxx()` 调用。

### 3.8 权限缓存

**更新**：`modules/rbac/service/impl/PermissionServiceImpl.java`
```java
@Service
@CacheConfig(cacheNames = "permission")
public class PermissionServiceImpl implements PermissionService {

    @Cacheable(key = "'tree'")
    public List<SysPermission> listTree() { ... }

    @Cacheable(key = "'list:' + #name")
    public List<SysPermission> listAll(String name) { ... }

    @CacheEvict(allEntries = true)
    public void save(SysPermission permission) { ... }

    @CacheEvict(allEntries = true)
    public void updateById(SysPermission permission) { ... }

    @CacheEvict(allEntries = true)
    public void removeById(Long id) { ... }
}
```

**新建**：`modules/rbac/config/CacheConfig.java`
```java
package com.starry.iam.modules.rbac.config;

@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .disableCachingNullValues()
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .build();
    }
}
```

### 3.9 application.yml 更新

```yaml
spring:
  application:
    name: iam-service    # 原可能是 backend，统一改为 iam-service

# 删除以下配置（ES 依赖移除）：
# spring:
#   elasticsearch:
#     uris: ...

resilience4j:
  circuitbreaker:
    instances:
      user-service:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
  timelimiter:
    instances:
      user-service:
        timeoutDuration: 3s
```

---

## Step 4：user-service 加固

### 4.1 更新 pom.xml

添加 `<parent>` 指向 `starry-services` + 添加 `common-lib` 依赖。

### 4.2 注册 InternalAuthFilter（使用 common-lib 版）

**更新**：`common/security/SecurityConfig.java`
```java
@Value("${" + InternalAuthConstants.ENV_KEY + ":change-me}")
private String internalSecret;

@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        // ... 原有配置 ...
        .authorizeHttpRequests(auth -> auth
            // 删除：.requestMatchers("/api/internal/**").permitAll()
            // 其他路径配置不变
        )
        // 新增：InternalAuthFilter 在 JWT filter 之前
        .addFilterBefore(new InternalAuthFilter(internalSecret),
                         JwtAuthenticationFilter.class)
        .build();
}
```

### 4.3 替换本地公共类为 common-lib 版

以下本地文件删除，改用 common-lib：

| 删除文件 | 替换为 |
|----------|--------|
| `common/result/Result.java` | `com.starry.common.result.Result` |
| `common/context/TenantContext.java` | `com.starry.common.context.TenantContext` |
| `common/exception/BusinessException.java` | `com.starry.common.exception.BusinessException` |
| `common/utils/IpUtil.java`（如有） | `com.starry.common.utils.IpUtil` |

**批量更新 import**：
```bash
find services/user-service/src -name "*.java" \
  -exec sed -i \
    -e 's/import com\.starry\.user\.common\.result\.Result/import com.starry.common.result.Result/g' \
    -e 's/import com\.starry\.user\.common\.context\.TenantContext/import com.starry.common.context.TenantContext/g' \
    -e 's/import com\.starry\.user\.common\.exception\.BusinessException/import com.starry.common.exception.BusinessException/g' \
  {} \;
```

---

## Step 5：基础设施同步

### 5.1 新建 `.env.example`（根目录）

```bash
# .env.example
# 复制为 .env 后填写实际值，.env 不提交 git

# 内部服务间鉴权密钥（生产环境必须修改）
INTERNAL_SECRET=change-me-in-production

# JWT 签名密钥（所有服务共用，必须一致）
JWT_SECRET=starry-admin-system-secret-key-must-be-very-long-and-secure-and-safe

# 数据库密码
MYSQL_ROOT_PASSWORD=your-root-password
MYSQL_PASSWORD=your-password

# 以下可保持默认
MYSQL_HOST=mysql
MYSQL_PORT=3306
REDIS_HOST=redis
REDIS_PORT=6379
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
ES_URIS=http://elasticsearch:9200
```

确认 `.env` 已在 `.gitignore` 中。

### 5.2 更新 `docker-compose.yml`

**变更清单**：

```yaml
services:
  # ── 原 admin-service ──────────────────────────────────────
  iam-service:                              # ★ 改名
    container_name: iam-service
    build:
      context: ./services/iam-service       # ★ 改路径
    environment:
      MYSQL_HOST: mysql
      MYSQL_PORT: 3306
      MYSQL_DATABASE: starry_iam
      MYSQL_USERNAME: ${MYSQL_USERNAME:-root}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-root}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      JWT_SECRET: ${JWT_SECRET}
      INTERNAL_SECRET: ${INTERNAL_SECRET}   # ★ 新增
      USER_SERVICE_URL: http://user-service:8082
    depends_on:                              # ★ 移除 elasticsearch
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_started
      user-service:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # ── user-service（新增环境变量）──────────────────────────
  user-service:
    environment:
      # ... 原有环境变量 ...
      INTERNAL_SECRET: ${INTERNAL_SECRET}   # ★ 新增

  # ── 原 log-service ────────────────────────────────────────
  audit-service:                            # ★ 改名
    container_name: audit-service
    build:
      context: ./services/audit-service     # ★ 改路径
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      ES_URIS: http://elasticsearch:9200
      REDIS_HOST: redis                     # ★ 新增
      REDIS_PORT: 6379                      # ★ 新增
      JWT_SECRET: ${JWT_SECRET}             # ★ 新增
      INTERNAL_SECRET: ${INTERNAL_SECRET}   # ★ 新增（audit 的 /api/internal 预留）
    depends_on:
      kafka:
        condition: service_started
      elasticsearch:
        condition: service_healthy
      redis:                                # ★ 新增
        condition: service_healthy

  # ── nginx-gateway（更新 depends_on）─────────────────────
  nginx-gateway:
    depends_on:
      iam-service:                          # ★ 改名（原 admin-service）
        condition: service_healthy
      user-service:
        condition: service_healthy
      audit-service:                        # ★ 改名（原 log-service）
        condition: service_healthy
      frontend:
        condition: service_started
```

### 5.3 更新 `infrastructure/nginx/nginx.conf`

```nginx
resolver 127.0.0.11 valid=30s;
resolver_timeout 3s;

# ── Upstream 定义 ─────────────────────────────────────────────
upstream iam_service   { server iam-service:8081;   }   # ★ 原 admin_service
upstream user_service  { server user-service:8082;  }
upstream audit_service { server audit-service:8083; }   # ★ 原 log_service
upstream frontend_service { server frontend:80;     }

# ── 公共 Proxy 参数 ───────────────────────────────────────────
# 在每个 location 内重复以下配置（或提取为 include）：
# proxy_set_header Host $host;
# proxy_set_header X-Real-IP $remote_addr;
# proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
# proxy_set_header X-Forwarded-Proto $scheme;
# proxy_http_version 1.1;
# client_max_body_size 10M;

server {
    listen 80;

    # ── 静态资源缓存 ─────────────────────────────────────────
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        proxy_pass http://frontend_service;
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # ── 平台端入口 ───────────────────────────────────────────
    location = /platform.html {
        proxy_pass http://frontend_service;
    }
    location ^~ /platform {
        proxy_pass http://frontend_service;
        try_files $uri $uri/ /platform.html;
    }

    # ── 审计日志 API → audit-service ─────────────────────────
    # ★ 必须在 /api/platform/ 和 /api/tenant/ 之前定义（优先匹配）
    location ^~ /api/platform/logs {
        proxy_pass http://audit_service;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
    location ^~ /api/tenant/logs {
        proxy_pass http://audit_service;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # ── iam-service 路由 ─────────────────────────────────────
    location ^~ /api/platform/ {
        proxy_pass http://iam_service;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
    location ^~ /api/tenant/ {
        proxy_pass http://iam_service;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
    location ^~ /api/rbac/ {                 # ★ 补全缺失路由
        proxy_pass http://iam_service;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # ── user-service 路由 ────────────────────────────────────
    location ^~ /api/v1/app-users {
        proxy_pass http://user_service;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        client_max_body_size 50M;  # 支持用户导入文件上传
    }
    location ^~ /api/user/ {
        proxy_pass http://user_service;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
    location ^~ /api/v1/user-tags {
        proxy_pass http://user_service;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
    location ^~ /api/v1/tag-categories {
        proxy_pass http://user_service;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
    location ^~ /api/v1/user-fields {
        proxy_pass http://user_service;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # ★ 删除：/api/auth/* 路由（AuthController 已删除）
    # ★ 删除：/api/internal/* 路由（不经 Nginx，仅 Docker 内网）
    # ★ 删除：/api/logs 下线路由

    # ── Swagger / Actuator（开发环境） ───────────────────────
    location ^~ /iam/swagger-ui {
        proxy_pass http://iam_service/swagger-ui;
    }
    location ^~ /audit/swagger-ui {
        proxy_pass http://audit_service/swagger-ui;
    }
    location ^~ /user/swagger-ui {
        proxy_pass http://user_service/swagger-ui;
    }

    # ── SPA 兜底 ─────────────────────────────────────────────
    location / {
        proxy_pass http://frontend_service;
        try_files $uri $uri/ /index.html;
    }
}
```

---

## 验收标准

### 代码质量

| 检查项 | 命令 | 预期结果 |
|--------|------|---------|
| 无 `com.starry.admin` 残留 | `grep -r "com\.starry\.admin" services/` | 0 结果 |
| 无 `com.starry.admin.log` 残留 | `grep -r "com\.starry\.admin\.log" services/` | 0 结果 |
| SysLogDocument 唯一 | `find services -name "SysLogDocument.java"` | 仅 audit-service 1 个 |
| AuthController 已删 | `find services -name "AuthController.java"` | 0 结果 |
| DbFixController 已删 | `find services -name "DbFixController.java"` | 0 结果 |
| Result 统一来源 | `find services -path "*/common/result/Result.java"` | 仅 common-lib 1 个 |
| TenantContext 统一 | `find services -name "TenantContext.java"` | 仅 common-lib 1 个 |
| RestTemplate 调用移除 | `grep -r "restTemplate\." services/iam-service/` | 0 结果 |

### 编译验证

```bash
cd services
mvn clean install -DskipTests        # 全量编译
# 预期：所有模块 BUILD SUCCESS
```

### 功能验证

```bash
docker-compose up -d
docker-compose ps  # 所有服务 healthy

# 1. 平台端登录
curl -s -X POST http://localhost/api/platform/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq .
# 预期：code=200，返回 accessToken

# 2. 旧接口已删除（/api/auth 不再存在）
curl -s http://localhost/api/auth/login
# 预期：404

# 3. 租户端登录
curl -s -X POST http://localhost/api/tenant/auth/login \
  -H "Content-Type: application/json" \
  -d '{"mobile":"13800000001","password":"123456"}' | jq .
# 预期：code=200

# 4. 日志查询（由 audit-service 处理）
TOKEN="<platform-token>"
curl -s "http://localhost/api/platform/logs?page=1&pageSize=10" \
  -H "Authorization: Bearer $TOKEN" | jq .
# 预期：code=200，返回分页日志

# 5. RBAC 路由（原先 Nginx 中缺失）
curl -s "http://localhost/api/rbac/roles" \
  -H "Authorization: Bearer $TOKEN" | jq .
# 预期：code=200，不再 502

# 6. 内部接口不经 Nginx 暴露
curl -s http://localhost/api/internal/users/by-mobile?mobile=13800000001
# 预期：404（Nginx 无该路由）

# 7. 内部接口 user-service 直连鉴权
curl -s http://localhost:8082/api/internal/users/by-mobile?mobile=13800000001
# 预期：401（无 X-Internal-Secret）
curl -s http://localhost:8082/api/internal/users/by-mobile?mobile=13800000001 \
  -H "X-Internal-Secret: change-me-in-production"
# 预期：200

# 8. 权限缓存（第二次请求走 Redis）
curl -s "http://localhost/api/platform/permissions/tree" \
  -H "Authorization: Bearer $TOKEN"
# 查看 Redis：redis-cli keys "permission*" 应有缓存 key
```
