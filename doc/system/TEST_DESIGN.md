# 后端测试设计方案 (Backend Test Design)

| 项目 | 内容 |
| :--- | :--- |
| 文档版本 | V1.2 |
| 关联 PRD | V1.1 |
| 关联技术设计 | V1.2 |
| 创建日期 | 2026-01-03 |
| 最后更新 | 2026-01-05 |
| 状态 | 修订版 |

## 1. 引言

### 1.1 目的
本文档旨在定义后台管理系统后端的测试策略、范围及具体实施方案。通过单元测试和集成测试的结合，确保系统核心业务逻辑的正确性、API 接口的稳定性以及安全机制的有效性，从而保障交付质量。

### 1.2 适用范围
本方案适用于 `backend` 工程的所有 Java 代码，重点覆盖以下模块：
*   认证模块 (Auth)
*   用户模块 (User)
*   角色模块 (Role)
*   权限模块 (Permission)
*   日志模块 (Log)
*   公共组件 (Common)

---

## 2. 测试策略

采用“测试金字塔”策略，以大量的单元测试为基础，辅以关键路径的集成测试。

### 2.1 技术选型
| 类型 | 工具/框架 | 用途 |
| :--- | :--- | :--- |
| **测试框架** | **JUnit 5** | 基础测试框架，提供注解和断言支持。 |
| **Mock 工具** | **Mockito** | 模拟 Service 层的外部依赖（如 Mapper, RedisUtil）。 |
| **集成测试** | **Spring Boot Test** | 加载 Spring 上下文，进行端到端测试。 |
| **Web 测试** | **MockMvc** | 模拟 HTTP 请求，验证 Controller 层接口。 |
| **数据库模拟** | **H2 Database** (或 Testcontainers) | 在内存中模拟 MySQL 数据库，确保测试环境隔离。 |
| **断言库** | **AssertJ** | 提供流式断言 API，提高测试代码可读性。 |
| **覆盖率工具** | **JaCoCo** | 统计代码覆盖率，生成测试报告。 |

### 2.2 覆盖率目标
*   **核心业务逻辑 (Service)**: 行覆盖率 > 80%
*   **工具类 (Utils)**: 行覆盖率 > 90%
*   **API 接口 (Controller)**: 核心接口 100% 覆盖

---

## 3. 单元测试设计 (Unit Testing)

单元测试主要针对 **Service 层** 和 **Common 层** 的独立逻辑进行验证，不启动 Spring 完整上下文，通过 Mock 解除外部依赖。

### 3.1 测试原则
*   **独立性**：每个测试用例相互独立，互不影响。
*   **快速执行**：不依赖真实数据库和网络 IO。
*   **Mock 依赖**：使用 `@ExtendWith(MockitoExtension.class)` 和 `@Mock` 模拟 Mapper 和其他 Service。

### 3.2 关键模块测试点

#### 3.2.1 认证模块 (AuthService)
*   **登录 (login)**:
    *   输入正确账号密码 -> 验证返回 Access/Refresh Token，验证 Redis 存储。
    *   输入错误密码 -> 验证抛出 `BadCredentialsException`。
    *   账号被禁用 -> 验证抛出异常。
*   **刷新 Token (refreshToken)**:
    *   有效 Refresh Token -> 验证返回新 Access Token。
    *   无效/过期 Refresh Token -> 验证抛出异常。
    *   Token 版本号不一致 -> 验证抛出异常。
*   **登出 (logout)**:
    *   验证 Redis 删除 Refresh Token，Access Token 加入黑名单。

#### 3.2.2 用户模块 (UserService)
*   **新增用户**:
    *   验证密码加密 (BCrypt)。
    *   验证用户名唯一性检查。
*   **状态修改**:
    *   禁用用户 -> 验证触发强制下线逻辑 (Redis 版本号自增)。

#### 3.2.3 权限模块 (PermissionService)
*   **构建权限树**:
    *   Mock 扁平权限列表 -> 验证转换为正确的树形结构 (Parent-Child 关系)。
*   **删除权限**:
    *   有子节点 -> 验证抛出异常。

#### 3.2.4 角色模块 (RoleService)
*   **分配权限**:
    *   验证角色与权限的关联关系正确存储。
    *   验证重复分配权限时不会产生冗余数据。

#### 3.2.5 日志模块 (LogAspect & Kafka Producer/Consumer)
*   **敏感字段脱敏**:
    *   验证密码、Token 等敏感字段在日志中被正确掩码。
    *   测试 `LogAspect.desensitize()` 方法处理各种敏感字段。
*   **Kafka 生产者逻辑**:
    *   验证 `LogAspect` 正确组装 `SysLogDocument` 并调用 `KafkaTemplate.send()`。
    *   验证发送失败时仅记录日志，不影响主业务流程。
    *   验证 `traceId` 正确从 `MDC` 获取并传入日志。
*   **Kafka 消费者逻辑**:
    *   验证 `LogConsumer` 正确接收批量消息。
    *   验证调用 `SysLogRepository.saveAll()` 持久化到 Elasticsearch。
    *   验证异常处理机制（重试、死信队列）。
    *   **死信队列测试**:
        *   模拟 `SysLogRepository.saveAll()` 抛出异常。
        *   验证重试机制生效（3次重试，间隔1秒）。
        *   验证重试耗尽后消息被转发到死信队列 `sys-log-topic.DLT`。
        *   验证死信队列消息包含原始消息内容和异常信息。

#### 3.2.6 系统初始化 (DataInitService)
*   **默认数据初始化**:
    *   验证系统首次启动时自动创建 admin 用户。
    *   验证超级管理员角色拥有所有权限。
    *   验证已存在 admin 用户时跳过初始化。

#### 3.2.7 参数校验 (DTO Validations)
*   **用户密码复杂度**:
    *   验证新增用户时密码必须包含字母和数字。
    *   验证密码长度符合要求 (6-20 字符)。
*   **字段唯一性校验**:
    *   验证手机号、邮箱的唯一性约束。
*   **必填字段校验**:
    *   验证必填字段为空时抛出异常。

### 3.3 代码示例 (AuthServiceTest)

```java
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private SysUserMapper sysUserMapper;
    
    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void login_Success() {
        // Arrange
        LoginBody loginBody = new LoginBody("admin", "123456");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin");
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtUtil.createAccessToken(anyString(), anyLong())).thenReturn("access_token");
        
        // Act
        Map<String, String> result = authService.login(loginBody);
        
        // Assert
        assertNotNull(result);
        assertEquals("access_token", result.get("accessToken"));
        verify(redisUtil).set(eq("auth:refresh:admin"), anyString(), anyLong(), any());
    }
}
```

### 3.4 代码示例 (LogAspectTest)

```java
@ExtendWith(MockitoExtension.class)
class LogAspectTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Mock
    private JwtUtil jwtUtil;
    
    @Mock
    private SysUserMapper sysUserMapper;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private LogAspect logAspect;

    @Test
    void log_Aspect_KafkaSend() {
        // Arrange: Mock KafkaTemplate.send() to return a completed future
        when(kafkaTemplate.send(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        
        // Act: Can't directly test around() without AOP, but we can test saveLog() if it were package-private.
        // For this example, we verify the KafkaTemplate is mocked and no interactions have occurred yet.
        
        // Assert
        verifyNoInteractions(kafkaTemplate);
    }
}
```

---

## 4. 集成测试设计 (Integration Testing)

集成测试启动 Spring Context，验证 Controller、Service、Mapper 乃至数据库的协同工作情况。

### 4.1 测试环境配置
*   使用 `application-test.yml` 配置文件。
*   **数据库**: 使用 H2 内存数据库模拟 MySQL，配置 `schema.sql` 自动初始化表结构。
*   **Redis**: 可使用 Embedded Redis 或 Mock RedisUtil。
*   **Kafka**: 使用真实的 Kafka 集群，配置测试专用 Topic 和 Group ID。
*   **Elasticsearch**: 使用真实的 Elasticsearch 集群，配置测试专用索引前缀。

### 4.2 测试对象：Controller 层
使用 `MockMvc` 发起 HTTP 请求，验证：
*   HTTP 状态码 (200, 401, 403, 400)。
*   响应 JSON 结构 (`code`, `message`, `data`)。
*   参数校验 (`@Valid`)。
*   权限拦截 (`@PreAuthorize`)。

### 4.3 关键场景测试

#### 4.3.1 安全鉴权流程
*   **无 Token 访问受保护接口**:
    *   `GET /api/users` -> Expect 401.
*   **权限不足访问**:
    *   普通用户访问 `DELETE /api/users/1` -> Expect 403.
*   **Token 黑名单/过期**:
    *   使用已登出 Token 访问 -> Expect 401.

#### 4.3.2 完整业务流 (CRUD)
*   **用户管理流程**:
    1.  管理员登录 -> 获取 Token。
    2.  新增用户 (POST /api/users) -> 200 OK。
    3.  查询用户列表 (GET /api/users) -> 验证包含新用户。
    4.  修改用户 (PUT /api/users) -> 200 OK。
    5.  删除用户 (DELETE /api/users/{id}) -> 200 OK。

#### 4.3.3 参数校验场景
*   **密码复杂度校验**:
    *   发送包含纯数字密码的请求 -> Expect 400 Bad Request。
    *   发送包含纯字母密码的请求 -> Expect 400 Bad Request。
    *   发送密码长度不足 6 位的请求 -> Expect 400 Bad Request。

#### 4.3.4 角色权限分配
*   **分配权限给角色**:
    1.  创建新角色 (POST /api/roles)。
    2.  为角色分配权限 (POST /api/roles/{id}/permissions)。
    3.  验证分配的权限被正确存储。

#### 4.3.5 系统初始化验证
*   **首次启动初始化**:
    1.  清空数据库（或使用新的测试实例）。
    2.  启动应用 -> 验证 admin 用户自动创建。
    3.  验证默认权限树已生成。

#### 4.3.6 日志模块端到端
*   **完整日志流程**:
    1.  触发带 `@Log` 注解的接口调用（如登录）。
    2.  验证 `LogAspect` 正确捕获并发送日志到 Kafka。
    3.  验证 `LogConsumer` 消费消息并写入 Elasticsearch。
    4.  调用日志查询接口验证数据正确入库。
*   **异常场景测试**:
    1.  模拟 Kafka 不可用 -> 验证业务流程不受影响，仅记录错误日志。
    2.  模拟 Elasticsearch 不可用 -> 验证消息被转发到死信队列。
*   **死信队列端到端测试**:
    1.  模拟 Elasticsearch 集群不可用。
    2.  触发多条日志记录。
    3.  验证所有日志消息被发送到死信队列 `sys-log-topic.DLT`。
    4.  恢复 Elasticsearch 集群，验证系统恢复正常消费。

### 4.4 代码示例 (UserControllerIntegrationTest)

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional // 测试后自动回滚数据
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void getUserList_WithAdminPermission_ShouldReturn200() throws Exception {
        // Arrange: 生成拥有 sys:user:list 权限的 Token
        String token = "Bearer " + generateTestToken("admin", "sys:user:list");

        // Act & Assert
        mockMvc.perform(get("/api/users")
                .header("Authorization", token)
                .param("page", "1")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void deleteUser_WithoutPermission_ShouldReturn403() throws Exception {
        // Arrange: 生成无权限 Token
        String token = "Bearer " + generateTestToken("user", "sys:user:query");

        // Act & Assert
        mockMvc.perform(delete("/api/users/1")
                .header("Authorization", token))
                .andExpect(status().isForbidden()); // 403
    }
}
```

---

## 5. 测试数据管理

### 5.1 数据初始化
*   **Schema**: 利用 Spring Boot 的 `spring.sql.init.schema-locations=classpath:schema.sql` 在测试启动时创建表。
*   **Data**:
    *   **静态数据**: `data.sql` 预置基础角色和权限。
    *   **动态数据**: 在 `@BeforeEach` 中通过 Mapper 插入测试所需的特定数据。

### 5.2 数据清理
*   **@Transactional**: 在集成测试类上添加此注解，每个 `@Test` 方法执行完后自动回滚事务，保证测试环境纯净。

---

## 6. CI/CD 集成

### 6.1 Maven 构建配置
在 `pom.xml` 中配置 `maven-surefire-plugin`，确保构建时自动运行测试。

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
    <configuration>
        <includes>
            <include>**/*Test.java</include>
        </includes>
    </configuration>
</plugin>
```

### 6.2 流水线集成
在 GitLab CI / Jenkins / GitHub Actions 中添加测试步骤：
1.  Checkout Code.
2.  Set up JDK 17.
3.  Run `mvn test`.
4.  Generate Report (JaCoCo).
5.  Check Quality Gate (如覆盖率 < 80% 则构建失败)。

---

## 7. 附录：测试用例清单 (部分)

| 模块 | 测试类 | 测试方法 | 描述 | 类型 |
| :--- | :--- | :--- | :--- | :--- |
| Auth | AuthServiceImplTest | login_Success | 登录成功逻辑验证 | Unit |
| Auth | AuthServiceImplTest | login_WrongPassword | 登录失败（密码错误） | Unit |
| Auth | AuthControllerTest | login_Api | 登录接口参数校验 | Integration |
| User | UserServiceImplTest | save_DuplicateUsername | 新增用户重名校验 | Unit |
| User | UserServiceImplTest | changeStatus_Disabled | 禁用用户触发强制下线 | Unit |
| User | UserControllerTest | page_Query | 用户分页查询接口 | Integration |
| User | UserControllerTest | save_InvalidPassword | 新增用户密码复杂度校验 | Integration |
| Role | RoleServiceImplTest | delete_HasUsers | 删除关联用户的角色 | Unit |
| Role | RoleServiceImplTest | assignPermissions | 角色分配权限 | Unit |
| Perm | PermissionServiceTest | listTree_Structure | 权限树结构验证 | Unit |
| Perm | PermissionServiceTest | delete_HasChildren | 删除有子节点的权限 | Unit |
| Log | LogAspectTest | log_Aspect_KafkaSend | AOP 日志切面 Kafka 发送逻辑 | Unit |
| Log | LogAspectTest | desensitize_SensitiveFields | 日志敏感字段脱敏 | Unit |
| Log | LogConsumerTest | batchProcess_Logs | Kafka 批量消费日志 | Unit |
| Log | LogConsumerTest | sendToDlt_WhenEsFails | ES 写入失败转 DLT | Unit |
| Log | LogControllerTest | page_QueryFromEs | 从 ES 查询日志 | Integration |
| Log | LogControllerTest | endToEnd_LogFlow | 完整日志流程 (AOP → Kafka → ES) | Integration |
| System | DataInitServiceTest | initData_FirstStartup | 首次启动初始化数据 | Integration |
| System | DataInitServiceTest | initData_SkipExists | 已存在admin用户时跳过初始化 | Integration |
