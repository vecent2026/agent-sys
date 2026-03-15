# Agent 管理平台技术架构设计方案

## 1. 文档信息

- 文档类型：技术架构设计文档
- 适用范围：`service/agent-service` 智能体管理能力建设
- 依赖文档：`agent-requirements.md` v0.1
- 版本：v0.1

---

## 2. 架构总览

### 2.1 系统定位

`agent-service` 是面向内部团队的智能体管理中台服务，聚焦"搭建 → 调试 → 发布 → 运行 → 复盘"闭环能力，不重复建设 `admin-service` 已有的 RBAC、用户管理、操作日志等通用后台治理能力。

### 2.2 架构分层图

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端（Frontend）                          │
│  React 19 + TypeScript + Ant Design + Zustand + React Router    │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │  大模型管理   │  │  智能体配置  │  │   调试 / 发布 / 观测  │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP / REST
┌───────────────────────────▼─────────────────────────────────────┐
│                   agent-service（后端）                           │
│             Java 17 + Spring Boot 3.2.1                         │
│                                                                 │
│  ┌────────────┐ ┌────────────┐ ┌───────────┐ ┌──────────────┐  │
│  │ 模型接入层  │ │ 智能体配置 │ │ 调试发布层 │ │ 观测分析层   │  │
│  └────────────┘ └────────────┘ └───────────┘ └──────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                   基础设施适配层                           │   │
│  │  LLM Adapter（OpenAI / Azure / Moonshot / 通义 / 网关）   │   │
│  └──────────────────────────────────────────────────────────┘   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                         数据层                                   │
│   MySQL 8.4  │  Redis 7.4  │  Elasticsearch 8.x  │  Kafka       │
└─────────────────────────────────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                      外部依赖                                    │
│   admin-service（RBAC / 用户 / 操作日志）                        │
│   统一密钥/凭据管理服务（Secret Manager）                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 后端架构设计

### 3.1 技术选型

| 组件 | 选型 | 版本 | 用途 |
|------|------|------|------|
| 运行时 | Java | 17 | 服务基础运行环境 |
| 框架 | Spring Boot | 3.2.1 | Web + DI + 配置管理 |
| 持久层 | MySQL | 8.4.8 | 主业务数据存储 |
| 缓存 | Redis | 7.4.8 | 热点配置缓存、分布式锁 |
| 搜索 | Elasticsearch | 8.19.7 | 调用日志全文检索、观测分析 |
| 消息队列 | Kafka | — | 调用事件异步落库、成本统计 |
| 测试框架 | JUnit 5 + Mockito 4 + Testcontainers 1.19.3 | — | 单元测试 / 集成测试 |

### 3.2 模块结构

```
agent-service/
├── api/                        # HTTP 控制器层（REST API）
│   ├── model/                  # 大模型接入相关接口
│   ├── agent/                  # 智能体配置相关接口
│   ├── debug/                  # 调试接口
│   ├── publish/                # 发布管理接口
│   └── observe/                # 观测分析接口
├── application/                # 应用服务层（用例编排）
│   ├── model/
│   ├── agent/
│   ├── debug/
│   ├── publish/
│   └── observe/
├── domain/                     # 领域层（核心业务逻辑）
│   ├── model/                  # 大模型领域模型
│   ├── agent/                  # 智能体领域模型
│   ├── publish/                # 版本发布领域模型
│   └── observe/                # 观测领域模型
├── infrastructure/             # 基础设施层
│   ├── llm/                    # LLM 适配器（各厂商实现）
│   ├── persistence/            # MySQL 仓储实现
│   ├── cache/                  # Redis 缓存实现
│   ├── search/                 # ES 检索实现
│   ├── mq/                     # Kafka 消息生产/消费
│   └── secret/                 # 密钥管理客户端
└── common/                     # 跨层公共工具
    ├── exception/
    ├── response/
    └── util/
```

### 3.3 核心模块设计

#### 3.3.1 大模型接入管理

**领域模型：`LlmModel`**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 模型 ID |
| `name` | VARCHAR(100) | 模型名称（展示用） |
| `modelIdentifier` | VARCHAR(100) UNIQUE | 模型标识，如 `gpt-4.1-mini` |
| `providerType` | ENUM | 厂商类型：`OPENAI / AZURE_OPENAI / MOONSHOT / TONGYI / CUSTOM_GATEWAY` |
| `baseUrl` | VARCHAR(500) | 基础请求地址 |
| `apiPath` | VARCHAR(200) | 接口类型/API 路径 |
| `apiVersion` | VARCHAR(50) | API 版本（可选） |
| `authType` | ENUM | 固定为 `API_KEY` |
| `secretConfigId` | VARCHAR(100) | 密钥配置 ID（引用外部密钥服务） |
| `timeoutMs` | INT | 请求超时时间（毫秒） |
| `status` | ENUM | `ENABLED / DISABLED` |
| `isDefault` | BOOLEAN | 是否默认模型（唯一约束通过业务逻辑保证） |
| `lastTestStatus` | ENUM | `AVAILABLE / ISSUE / UNTESTED` |
| `lastTestMessage` | TEXT | 最近一次测试详情 |
| `lastTestAt` | DATETIME | 最近一次测试时间 |
| `createdAt` | DATETIME | 创建时间 |
| `updatedAt` | DATETIME | 更新时间 |

**LLM 适配器设计（策略模式）**

```
LlmAdapter（接口）
├── OpenAiAdapter
├── AzureOpenAiAdapter
├── MoonshotAdapter
├── TongyiAdapter
└── CustomGatewayAdapter
```

- 统一入参：`LlmChatRequest`（messages、modelIdentifier、temperature、topP、maxTokens、timeoutMs）
- 统一出参：`LlmChatResponse`（content、latencyMs、promptTokens、completionTokens、errorCode、errorMessage）
- 适配器通过 `providerType` 由 `LlmAdapterFactory` 路由选择
- 密钥在运行时从 Secret Manager 动态获取，不在代码或配置中明文存储

**一键测试流程：**

```
[前端] 点击一键测试
  → POST /api/models/{id}/test
  → ModelTestService.test(modelId)
    → 从 DB 读取 LlmModel
    → 从 SecretManager 获取 API Key
    → LlmAdapterFactory.get(providerType).test(request)
    → 记录 latencyMs、status、errorMessage
    → 写回 lastTestStatus / lastTestMessage / lastTestAt
  → 返回测试结果
```

#### 3.3.2 智能体配置管理

**领域模型：`AgentConfig`**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 智能体 ID |
| `name` | VARCHAR(100) | 智能体名称 |
| `description` | TEXT | 描述 |
| `promptTemplate` | TEXT | 系统提示词模板 |
| `outputFormat` | ENUM | `TEXT / STRUCTURED` |
| `toolCallEnabled` | BOOLEAN | 工具调用开关 |
| `knowledgeEnabled` | BOOLEAN | 知识库开关 |
| `modelId` | BIGINT FK | 关联的大模型 ID |
| `temperature` | DECIMAL(3,2) | 温度（智能体维度） |
| `topP` | DECIMAL(3,2) | topP |
| `maxTokens` | INT | 最大输出长度 |
| `status` | ENUM | `DRAFT / PUBLISHED / ARCHIVED` |
| `publishedVersionId` | BIGINT FK | 当前发布版本 ID |
| `createdAt` | DATETIME | — |
| `updatedAt` | DATETIME | — |

#### 3.3.3 调试与发布管理

**版本快照模型：`AgentVersion`**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 版本 ID |
| `agentId` | BIGINT FK | 所属智能体 ID |
| `versionNo` | INT | 版本号（自增） |
| `configSnapshot` | JSON | 发布时的配置完整快照 |
| `publishStatus` | ENUM | `PUBLISHED / ROLLED_BACK / SUPERSEDED` |
| `publishedAt` | DATETIME | 发布时间 |
| `publishedBy` | VARCHAR(100) | 发布人（来自 admin-service） |
| `rollbackFrom` | BIGINT | 回滚来源版本 ID（若为回滚操作） |

**调试会话模型：`DebugSession`**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 会话 ID |
| `agentId` | BIGINT FK | 关联智能体 |
| `draftConfigSnapshot` | JSON | 调试时使用的草稿配置快照 |
| `messages` | JSON | 对话记录 |
| `createdAt` | DATETIME | — |

**发布校验规则（发布前门禁）：**

1. 智能体名称非空
2. 提示词模板非空
3. 关联模型存在且状态为 `ENABLED`
4. 关联模型 `lastTestStatus` 不为 `ISSUE`（可配置告警级别）
5. 若 `toolCallEnabled=true`，至少配置一个工具

#### 3.3.4 运行观测与成本视图

**调用事件流设计（异步）：**

```
[调用方] 调用智能体
  → agent-service 处理请求
  → 发送 AgentCallEvent 到 Kafka Topic: agent-call-events
  → consumer 消费 → 写入 Elasticsearch（会话级日志）
                  → 写入 MySQL（成本统计聚合表）
```

**观测指标聚合表：`AgentCallStat`**

| 字段 | 类型 | 说明 |
|------|------|------|
| `agentId` | BIGINT | 智能体 ID |
| `statDate` | DATE | 统计日期 |
| `statHour` | TINYINT | 统计小时（0-23，精细化分析用） |
| `totalCalls` | INT | 总调用次数 |
| `successCalls` | INT | 成功次数 |
| `failCalls` | INT | 失败次数 |
| `totalPromptTokens` | BIGINT | 总输入 Token |
| `totalCompletionTokens` | BIGINT | 总输出 Token |
| `avgLatencyMs` | INT | 平均响应耗时（ms） |

**Elasticsearch 索引：`agent-call-logs`**

用于会话级问题定位，存储每次调用的：agentId、sessionId、requestMessages、responseContent、latencyMs、errorCode、errorCategory、timestamp 等字段，支持按智能体、时间范围、错误类型全文检索。

---

## 4. 前端架构设计

### 4.1 技术选型

| 组件 | 版本 | 用途 |
|------|------|------|
| React | 19.2.0 | UI 渲染框架 |
| TypeScript | 5.9.3 | 类型安全 |
| Vite | 7.2.4 | 构建工具 |
| Ant Design | 6.1.3 | UI 组件库 |
| Zustand | 5.0.9 | 全局状态管理 |
| React Router | 7.11.0 | 路由管理 |

### 4.2 页面结构

```
src/pages/
├── agent/
│   ├── list/           # 智能体列表
│   ├── create/         # 新建智能体
│   ├── edit/           # 编辑智能体配置
│   ├── debug/          # 草稿调试对话页
│   └── observe/        # 单智能体观测详情
├── model/
│   └── list/           # 大模型管理列表
└── dashboard/
    └── index/          # 运行概览（可选）
```

### 4.3 状态管理（Zustand Store 划分）

| Store | 职责 |
|-------|------|
| `useModelStore` | 模型列表、测试状态、CRUD 操作 |
| `useAgentStore` | 智能体列表、当前编辑草稿、发布状态 |
| `useDebugStore` | 当前调试会话消息流、流式响应状态 |
| `useObserveStore` | 观测数据缓存、时间范围筛选条件 |

### 4.4 关键交互设计

**模型管理页面**：
- 表格展示，支持多条件筛选（名称/标识/状态）
- 默认模型用 Tag 高亮标识
- 一键测试：行内按钮 → loading 状态 → 成功/失败 Badge 更新
- 删除前置校验：若被引用则弹窗告知引用数

**智能体配置页面**：
- 两栏布局：左侧配置表单，右侧草稿调试对话区
- 模型选择仅展示 `ENABLED` 状态的模型
- 推理参数（temperature / topP / maxTokens）在选择模型后展开
- 实时草稿保存（debounce 自动保存）

**发布管理**：
- 发布前校验结果以清单形式展示（通过/未通过项）
- 版本历史列表，支持一键回滚（需二次确认）

---

## 5. 接口设计（REST API）

### 5.1 大模型接入接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/models` | 分页查询模型列表（支持筛选） |
| POST | `/api/models` | 新增模型 |
| PUT | `/api/models/{id}` | 编辑模型（仅允许编辑字段） |
| DELETE | `/api/models/{id}` | 删除模型（前置引用校验） |
| POST | `/api/models/{id}/test` | 一键可用性测试 |
| GET | `/api/models/enabled` | 获取可用模型列表（智能体配置用） |

### 5.2 智能体配置接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/agents` | 分页查询智能体列表 |
| POST | `/api/agents` | 新建智能体 |
| GET | `/api/agents/{id}` | 获取智能体详情（含草稿） |
| PUT | `/api/agents/{id}` | 保存智能体草稿配置 |
| DELETE | `/api/agents/{id}` | 删除智能体 |

### 5.3 调试与发布接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/agents/{id}/debug` | 草稿调试发起对话（流式 SSE） |
| POST | `/api/agents/{id}/publish/validate` | 发布前校验 |
| POST | `/api/agents/{id}/publish` | 执行发布 |
| GET | `/api/agents/{id}/versions` | 获取版本列表 |
| POST | `/api/agents/{id}/versions/{versionId}/rollback` | 回滚到指定版本 |

### 5.4 观测分析接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/observe/agents/{id}/metrics` | 调用指标（趋势/成功率/异常率），支持时间范围 |
| GET | `/api/observe/agents/{id}/sessions` | 会话级问题定位列表（ES 检索） |
| GET | `/api/observe/agents/{id}/cost` | 资源消耗与成本趋势 |

---

## 6. 数据流与关键时序

### 6.1 一键测试时序

```
前端                       agent-service              LLM厂商API
 │                              │                          │
 │  POST /models/{id}/test      │                          │
 ├─────────────────────────────►│                          │
 │                              │  查询模型配置             │
 │                              │  获取 SecretManager Key  │
 │                              │  构建测试请求             │
 │                              ├─────────────────────────►│
 │                              │                          │ 响应
 │                              │◄─────────────────────────┤
 │                              │  记录结果 → 写回 DB       │
 │◄─────────────────────────────┤                          │
 │  返回测试结果 + 耗时           │                          │
```

### 6.2 发布流程时序

```
前端                       agent-service              admin-service
 │                              │                          │
 │  POST /agents/{id}/publish/validate                     │
 ├─────────────────────────────►│                          │
 │◄─────────────────────────────┤ 返回校验清单              │
 │                              │                          │
 │  POST /agents/{id}/publish   │                          │
 ├─────────────────────────────►│                          │
 │                              │  生成版本快照             │
 │                              │  更新 publishedVersionId │
 │                              │  写操作日志 ─────────────►│
 │◄─────────────────────────────┤                          │
 │  返回版本号                   │                          │
```

### 6.3 调用观测异步流

```
调用方 → agent-service → Kafka (agent-call-events)
                                │
                    ┌───────────┴──────────┐
                    ▼                       ▼
             Elasticsearch           MySQL (AgentCallStat)
          (会话级日志存储)            (按天/小时聚合统计)
```

---

## 7. 安全与依赖边界

### 7.1 密钥安全

- 模型 API Key **不**存储在 `agent-service` 数据库中，仅存储 `secretConfigId`
- 运行时通过统一密钥/凭据管理服务动态获取 Key
- LLM 适配器仅在请求时短暂持有 Key，不做任何缓存或日志输出

### 7.2 与 admin-service 的边界

| 能力 | 负责方 | 说明 |
|------|--------|------|
| RBAC 权限校验 | admin-service | agent-service 通过 token/header 携带身份信息，admin-service 统一鉴权 |
| 用户信息查询 | admin-service | 发布人、操作人信息从 admin-service 查询 |
| 操作日志写入 | admin-service | agent-service 在关键操作后通过内部接口/事件通知 admin-service 写日志 |

### 7.3 单租户约束

- 本阶段不设计多租户隔离字段（无 `tenantId`）
- 所有接口默认面向同一组织内部使用
- 如未来需扩展多租户，数据模型预留 `orgId` 字段扩展点（当前值为固定默认值）

---

## 8. 非功能性考量

### 8.1 缓存策略

| 缓存对象 | 存储 | TTL | 更新策略 |
|---------|------|-----|---------|
| 启用状态模型列表 | Redis | 5 分钟 | 模型 CRUD 后主动失效 |
| 当前发布版本配置 | Redis | 10 分钟 | 发布/回滚后主动失效 |
| 模型可用性状态 | Redis | 30 分钟 | 一键测试后主动更新 |

### 8.2 可扩展性

- LLM 适配器采用策略模式，新增厂商只需新增 `Adapter` 实现，注册到 `LlmAdapterFactory` 即可
- Kafka 消息驱动的观测链路与主业务解耦，可独立扩展分析能力
- 版本快照以 JSON 存储完整配置，无模式限制，支持配置结构演进

### 8.3 测试策略

| 层次 | 工具 | 覆盖目标 |
|------|------|---------|
| 单元测试 | JUnit 5 + Mockito 4 | 领域模型、业务规则、适配器逻辑 |
| 集成测试 | Testcontainers 1.19.3 | MySQL / Redis / ES / Kafka 真实容器验证 |
| 前端测试 | Vitest 4.0.16 | Store 逻辑、关键组件渲染 |

---

## 9. 里程碑建议

| 阶段 | 内容 | 优先级 |
|------|------|-------|
| MVP P0 | 大模型接入管理（CRUD + 一键测试）+ 智能体基础配置 | 必须 |
| MVP P1 | 草稿调试（流式对话）+ 发布管理（版本快照 + 回滚） | 必须 |
| P2 | 运行观测基础指标（调用趋势、成功率） | 重要 |
| P3 | 成本视图 + 会话级问题定位（ES 检索） | 增强 |
