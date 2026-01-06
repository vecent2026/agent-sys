# 系统架构设计文档 (System Architecture Design)

| 项目 | 内容 |
| :--- | :--- |
| 文档版本 | V1.2 |
| 关联 PRD | V1.1 |
| 最后更新 | 2026-01-05 |
| 状态 | 修订版 |

### 文档修订记录
| 版本 | 日期 | 修改人 | 说明 |
| :--- | :--- | :--- | :--- |
| V1.1 | 2026-01-02 | System | 初始版本创建 |
| V1.2 | 2026-01-05 | Architect | 引入 Kafka + Elasticsearch 重构操作日志架构；更新技术选型、数据流向及部署架构。 |

## 第一阶段：需求解构与约束识别

### 1.1 功能性需求分析 (Functional Requirements)
基于 PRD V1.1，系统核心业务边界划分为以下域：
*   **认证域 (IAM)**：负责用户身份识别、令牌签发（双 Token 机制）、令牌刷新及黑名单管理。
*   **权限域 (RBAC)**：实现基于角色的访问控制，包含用户、角色、权限节点（菜单/按钮/API）的 CRUD 及关联关系维护。
*   **审计域 (Audit)**：全量采集关键业务操作及安全事件（登录/登出），保障数据可追溯性。

### 1.2 非功能性需求 (Non-functional Requirements)
*   **性能 (Performance)**：
    *   API 平均响应时间 < 200ms（95% 分位）。
    *   登录/鉴权接口响应时间 < 100ms。
    *   支持并发用户数：500+（基于单体架构预估）。
*   **可用性 (Availability)**：
    *   SLA 目标：99.9%。
    *   具备无状态服务特性，支持故障重启。
*   **安全性 (Security)**：
    *   **传输层**：全链路 HTTPS 加密。
    *   **数据层**：用户密码采用 BCrypt 加盐哈希存储。
    *   **认证层**：基于 JWT 的 Stateless 认证，结合 Redis 实现 Token 黑名单机制。
    *   **防护层**：
        *   **CORS**：配置严格的跨域策略，仅允许受信任域名访问。
        *   **XSS**：启用全局输入过滤与输出转义。
        *   **SQL 注入**：强制使用 MyBatis 预编译参数。
        *   **限流 (Rate Limiting)**：关键接口（如登录）引入 Redisson 或 Nginx 限流，防止暴力破解。
*   **伸缩性 (Scalability)**：
    *   后端服务无状态设计，支持通过负载均衡进行水平扩展。

### 1.3 技术约束 (Constraints)
*   **架构模式**：前后端分离的单体架构 (Monolithic)。
*   **后端技术栈**：Java (Spring Boot)。
*   **前端技术栈**：React + Ant Design。
*   **数据存储**：MySQL (关系型数据), Redis (缓存/会话), Elasticsearch (日志检索)。
*   **中间件**：Kafka (消息队列)。

---

## 第二阶段：架构决策推理

### 2.1 架构模式选择
**决策**：采用 **分层单体架构 (Layered Monolith)**。
**逻辑理由**：
1.  **复杂度适配**：当前业务主要围绕 RBAC 和基础管理，领域边界清晰但交互紧密，微服务会引入不必要的分布式事务和运维复杂度。
2.  **开发效率**：单体架构在代码复用、调试、部署方面具有显著优势，符合“快速交付”原则。
3.  **扩展性预留**：通过模块化（Modular Monolith）设计，保持各业务包（User, Role, Auth）的低耦合，未来可低成本拆分为微服务。

### 2.2 关键技术选型
| 组件 | 选型 | 逻辑理由 |
| :--- | :--- | :--- |
| **后端框架** | **Spring Boot 3.x** | 工业级标准，生态成熟，内置 Security 和 Data JPA/MyBatis 支持，开箱即用。 |
| **前端框架** | **React 18 + Ant Design 6.0** | Ant Design 提供现成的组件库，高度可定制化，符合后台管理系统的 UI 需求。 |
| **数据库** | **MySQL 8.0** | RBAC 模型强依赖关系查询（Join），ACID 特性保障权限数据一致性。 |
| **缓存/会话** | **Redis 7.x** | 1. 存储 Refresh Token 及黑名单（高性能读写）。<br>2. 缓存权限树结构（减少 DB 压力）。 |
| **日志存储** | **Elasticsearch 8.x** | 专用于海量操作日志的存储与全文检索，支持多维度组合查询，性能远超 MySQL。 |
| **消息队列** | **Kafka 4.x** | 高吞吐量消息缓冲，实现业务主流程与日志记录的异步解耦，削峰填谷。 |
| **ORM 框架** | **MyBatis Plus** | 相比 JPA 更灵活，适合国内开发习惯，内置分页和代码生成器，提升开发效率。 |

### 2.3 数据架构设计
*   **数据流向**：
    *   读请求：Client -> Nginx -> Spring Boot -> (Redis Cache) -> MySQL。
    *   写请求：Client -> Nginx -> Spring Boot -> MySQL -> (Delete Cache)。
    *   **日志流**：App -> Kafka -> Log Consumer -> Elasticsearch。
*   **一致性模型**：
    *   **最终一致性**：权限变更后，通过清除 Redis 缓存迫使前端刷新。
    *   **强制下线 (Force Logout)**：引入 **Token 版本号机制**。
        *   用户登录时签发携带 `version` 的 Token。
        *   管理员踢人时，自增 Redis 中的版本号 `auth:token:version:{userId}`。
        *   鉴权时校验 Token 版本与 Redis 版本是否一致，不一致则拒绝访问。
*   **存储策略**：
    *   **MySQL**：存储核心业务数据（User, Role, Permission）。
    *   **Elasticsearch**：存储操作日志（Operation Logs），按天/月索引滚动。
    *   **Redis**：
        *   `auth:token:refresh:{userId}` (String): 存储 Refresh Token。
        *   `auth:token:blacklist:{jti}` (String): 存储已注销的 Access Token (TTL = Access Token 有效期)。
        *   `auth:token:version:{userId}` (Integer): 存储用户当前的 Token 版本号（用于强制下线）。
        *   `sys:cache:user_perms:{userId}` (Set): 缓存用户权限列表。

---

## 第三阶段：详细设计规范

### 3.1 组件通信
*   **通信协议**：HTTP/1.1 (RESTful API)。
*   **数据格式**：JSON。
*   **交互模型**：同步请求-响应 (Request-Response)。
*   **异步解耦**：
    *   **审计日志**：采用 **Kafka 消息队列**。业务操作完成后发送消息至 Kafka Topic，由独立消费者异步写入 Elasticsearch，确保主流程低延迟。

### 3.2 接口协议 (API Standard)
遵循 RESTful 风格，统一响应结构：
```json
{
  "code": 200,          // 业务状态码 (200:成功, 401:未授权, 403:禁止, 500:系统错误)
  "message": "success", // 提示信息
  "data": { ... },      // 业务数据
  "timestamp": 1704124800000
}
```
*   **URI 规范**：
    *   `GET /api/v1/users` (列表)
    *   `POST /api/v1/users` (新增)
    *   `PUT /api/v1/users/{id}` (修改)
    *   `DELETE /api/v1/users/{id}` (删除)

### 3.3 前端架构设计 (Frontend Architecture)
*   **核心技术栈**：
    *   **构建工具**：Vite 5.x (极速冷启动与 HMR)。
    *   **路由管理**：React Router 6 (Data Router 模式)。
    *   **状态管理**：Zustand (轻量级全局状态) + React Query (服务端状态缓存)。
    *   **HTTP 客户端**：Axios (封装拦截器)。

*   **动态路由与权限控制**：
    *   **路由加载策略**：采用“后端驱动”模式。前端仅保留基础路由（Login, 404），登录后根据后端返回的权限标识（Permission Keys）动态过滤并注册业务路由。
    *   **按钮级权限**：封装 `<AuthButton perm="user:add">` 组件，内部校验当前用户权限集合，无权限则不渲染。

*   **网络层封装规范**：
    *   **请求拦截**：自动注入 `Authorization: Bearer {token}`。
    *   **响应拦截**：
        *   `401 Unauthorized`：暂停当前请求队列，尝试使用 Refresh Token 刷新。
            *   刷新成功：重试原请求。
            *   刷新失败：清空本地存储，强制跳转登录页。
        *   全局错误提示：统一使用 Ant Design Message 组件展示后端返回的 `message`。

### 3.4 部署架构
采用容器化部署方案，确保环境一致性。

*   **网络拓扑**：
    *   **接入层**：Nginx (反向代理 + 静态资源服务器 + IP 限流)。
    *   **应用层**：Spring Boot Container (多副本部署)。
    *   **数据层**：MySQL (主从/单机), Redis (AOF 持久化), Elasticsearch (日志集群)。
    *   **中间件**：Kafka Cluster (消息缓冲)。

*   **容器编排 (Docker Compose 示例)**：
    ```yaml
    services:
      nginx:
        ports: ["80:80"]
        depends_on: [backend]
      backend:
        image: admin-system:latest
        environment:
          - DB_HOST=mysql
          - REDIS_HOST=redis
          - KAFKA_SERVERS=kafka:9092
      mysql:
        image: mysql:8.0
        volumes: ["./data/mysql:/var/lib/mysql"]
      redis:
        image: redis:7.0
      kafka:
        image: bitnami/kafka:3.4
        environment:
          - KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181
      elasticsearch:
        image: elasticsearch:8.7.0
        environment:
          - discovery.type=single-node
          - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ```

### 3.5 可观测性设计 (Observability)
*   **健康检查 (Health Check)**：
    *   集成 `Spring Boot Actuator`，暴露 `/actuator/health` 端点。
    *   Docker Compose 基于此端点进行存活探测 (Liveness Probe) 和就绪探测 (Readiness Probe)。
*   **日志规范 (Logging)**：
    *   **格式**：统一采用 JSON 格式输出，便于 ELK 采集。
    *   **链路追踪**：引入 `TraceId` (MDC)，确保 Nginx -> App -> DB 的全链路日志可串联。
    *   **级别**：生产环境默认 `INFO`，异常堆栈强制记录 `ERROR`。

### 3.6 API 工程化 (API Engineering)
*   **文档标准**：
    *   集成 **OpenAPI (Swagger) 3.0**，实现代码即文档。
    *   要求所有 Controller 和 DTO 必须包含 `@Operation` 和 `@Schema` 注解。
*   **异常处理**：
    *   实现全局异常处理器 (`@RestControllerAdvice`)。
    *   定义异常映射表：
        *   `MethodArgumentNotValidException` -> 400 (参数校验失败)
        *   `AccessDeniedException` -> 403 (权限不足)
        *   `BadCredentialsException` -> 401 (认证失败)
        *   `Exception` -> 500 (系统内部错误)
*   **参数校验**：
    *   强制使用 JSR-303 (`@NotNull`, `@Size`, `@Pattern`) 进行入参校验。

### 3.7 数据库设计规范 (DB Design Specs)
*   **基础规范**：
    *   字符集：强制使用 `utf8mb4_general_ci`，支持 Emoji。
    *   引擎：InnoDB。
*   **通用字段**：
    *   所有业务表必须包含：`id` (PK), `create_time`, `update_time`, `create_by`, `update_by`, `is_deleted`。
*   **逻辑删除**：
    *   采用 `is_deleted` (TINYINT) 字段：`0` (未删除), `1` (已删除)。
    *   MyBatis Plus 配置全局逻辑删除插件。
*   **索引策略**：
    *   唯一索引：`uk_username`, `uk_mobile`, `uk_role_key`。
    *   普通索引：`idx_create_time` (用于排序/归档), `idx_parent_id` (树形结构查询)。
