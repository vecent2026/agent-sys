# 数据库设计文档

## 文档信息

| 属性 | 内容 |
|------|------|
| 文档编号 | 09 |
| 所属层次 | 技术层 |
| 关联文档 | 02-llm-management、03-knowledge-base、04-agent-studio、05-skill-library、06-workflow-multiagent、07-debug-release、08-observability-evaluation |
| 版本 | v1.0 |
| 创建日期 | 2026-03-18 |
| 状态 | 正式 |

---

## 1. 设计原则与规范

### 1.1 DDL 规范

| 规范项 | 规则 |
|--------|------|
| 主键 | `VARCHAR(36)` 存储 UUID，字段名统一为 `id` |
| 时间字段 | `DATETIME(3)` 毫秒精度，统一使用 `created_at` 和 `updated_at` |
| 外键引用 | 命名格式 `{table}_id`，如 `agent_id`、`tenant_id` |
| JSON 字段 | 使用 MySQL `JSON` 类型 |
| 枚举字段 | 使用 `VARCHAR` + COMMENT 说明枚举值，不使用 MySQL ENUM 类型 |
| 软删除 | `is_deleted TINYINT(1)` + `deleted_at DATETIME(3)`，不执行物理 DELETE |
| 字符集 | 统一 `utf8mb4`，排序规则 `utf8mb4_unicode_ci` |
| 存储引擎 | 统一 `InnoDB` |
| 注释 | 所有表和字段必须带 `COMMENT` |

### 1.2 索引设计原则

1. **主键索引**：每张表以 UUID 字符串作为主键，InnoDB 会自动建立聚簇索引。
2. **租户隔离索引**：所有多租户表的查询条件必须先过滤 `tenant_id`，因此 `tenant_id` 通常是联合索引的最左前缀列。
3. **软删除索引**：所有含 `is_deleted` 的表，业务查询均需附加 `is_deleted = 0` 过滤，联合索引中应包含该列。
4. **时间范围索引**：`created_at`、`started_at` 等时间字段上建立索引，支持时间范围分页查询和数据清理任务。
5. **唯一性约束**：业务上唯一的字段（如租户内模型标识、智能体名称）建立唯一索引，同时配合 `is_deleted` 避免软删除后数据冲突——部分场景需使用函数索引或通过业务逻辑保证。
6. **避免过度索引**：写多读少的日志/记录类表（如 `trace_turn`、`skill_invocation`）仅建立必要的查询索引，避免影响写入性能。
7. **大 JSON 字段**：JSON 字段不参与索引，需要检索的子字段应提取为普通列单独存储。

---

## 2. 数据隔离策略

### 2.1 隔离模式

本平台采用 **共享数据库 + `tenant_id` 列隔离** 模式（Shared Database, Shared Schema），所有租户的数据存储在同一个数据库实例的同一组表中，通过 `tenant_id` 列在应用层区分数据归属。

### 2.2 `tenant_id` 的含义

- `tenant_id` 存储 UUID 字符串，与 `admin-service` 中 `platform_tenant.id` 对应（admin-service 使用 BIGINT 自增主键，agent-service 中以字符串形式存储其字符表示）。
- `tenant_id IS NULL` 或 `tenant_id = 'PLATFORM'` 表示平台级别的全局数据（如平台公共 Skill、平台级 LLM 配置），对所有租户可见。
- 平台管理员操作不需要 `tenant_id` 过滤；租户用户的所有数据访问必须附加 `tenant_id = ?` 条件。

### 2.3 隔离保障措施

1. **Repository 层强制过滤**：数据访问层统一注入当前请求的 `tenant_id`，禁止绕过过滤直接执行全表查询。
2. **联合索引最左前缀**：所有涉及租户数据的联合索引均以 `(tenant_id, ...)` 开头，确保索引命中。
3. **API 鉴权**：通过 JWT 中携带的 `tenant_id` 与请求中的资源 `tenant_id` 做一致性校验，防止越权访问。
4. **审计日志**：`security_event` 表记录跨租户访问尝试等安全事件。

---

## 3. 表关系总览

```
tenant (引用 admin-service 的 platform_tenant)
  └── user (引用 admin-service 的 app_user)

llm_model
  ├── llm_secret (1:N，一个模型有多个密钥，Key 池化)
  ├── llm_availability_test (1:N，可用性测试历史)
  └── llm_model_key_pool (1:N，Key 池策略)

agent
  ├── agent_version (1:N，版本快照)
  ├── agent_tool_binding (1:N，工具绑定)
  ├── agent_kb_binding (1:N，知识库绑定)
  └── agent_skill_binding (1:N，Skill 绑定)

knowledge_base
  └── kb_document (1:N)
        └── kb_chunk (1:N，元数据，向量在 Qdrant)

skill
  ├── skill_version (1:N，版本快照)
  └── skill_invocation (1:N，调用记录)

workflow
  ├── workflow_version (1:N，版本快照)
  ├── workflow_run (1:N，执行实例)
  │     └── workflow_node_execution (1:N，节点执行记录)
  └── human_task (1:N，人工任务，关联 workflow_run)

test_suite
  └── test_case (1:N)
        └── test_run (1:N，批量测试执行)

ab_test (关联 agent，比较两个 agent_version)

api_key (关联 agent，对外提供调用凭证)

trace_session
  └── trace_turn (1:N)

evaluation_result (关联 trace_turn)
security_event (租户级别安全审计)
cost_daily_stats (租户+模型+日期维度成本汇总)
alert_rule (租户级别告警规则)
```

---

## 4. 基础表

### 4.1 tenant（租户元数据缓存表）

**用途**：缓存 `admin-service` 中的租户基础信息，供 agent-service 本地查询使用，避免跨服务调用。数据由 admin-service 同步写入，agent-service 只读。

```sql
CREATE TABLE `tenant` (
  `id`           VARCHAR(36)   NOT NULL                 COMMENT '租户UUID，与 admin-service platform_tenant.id 对应',
  `tenant_code`  VARCHAR(50)   NOT NULL                 COMMENT '租户编码，URL 友好标识',
  `tenant_name`  VARCHAR(100)  NOT NULL                 COMMENT '租户名称',
  `status`       VARCHAR(20)   NOT NULL DEFAULT 'active' COMMENT '租户状态：active=启用, suspended=已暂停, deleted=已删除',
  `plan`         VARCHAR(50)   DEFAULT NULL             COMMENT '套餐类型：free/pro/enterprise',
  `created_at`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户元数据缓存表（只读，由 admin-service 同步）';
```

**关键字段说明**：
- `id`：使用字符串 UUID，而非 admin-service 的 BIGINT，保持 agent-service 内部 ID 规范统一。
- `status`：用于快速判断租户是否可用，避免每次查询都调用 admin-service。

---

### 4.2 user（用户元数据缓存表）

**用途**：缓存 `admin-service` 中 app_user 的基础信息，供 agent-service 本地关联查询（如创建人、操作人展示）。

```sql
CREATE TABLE `user` (
  `id`           VARCHAR(36)   NOT NULL                 COMMENT '用户UUID，与 admin-service app_user.id 对应',
  `tenant_id`    VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID',
  `username`     VARCHAR(50)   NOT NULL                 COMMENT '登录用户名',
  `nickname`     VARCHAR(100)  DEFAULT NULL             COMMENT '显示名称',
  `email`        VARCHAR(100)  DEFAULT NULL             COMMENT '邮箱',
  `avatar`       VARCHAR(500)  DEFAULT NULL             COMMENT '头像URL',
  `status`       VARCHAR(20)   NOT NULL DEFAULT 'active' COMMENT '状态：active=正常, disabled=禁用',
  `created_at`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户元数据缓存表（只读，由 admin-service 同步）';
```

---

## 5. LLM 管理

### 5.1 llm_model（模型配置表）

**用途**：存储平台接入的所有大语言模型的配置信息，包括供应商类型、调用地址、能力标注和计费单价。平台管理员操作，不含 tenant_id 隔离（平台级全局配置）。

```sql
CREATE TABLE `llm_model` (
  `id`                   VARCHAR(36)    NOT NULL                 COMMENT '模型UUID主键',
  `name`                 VARCHAR(100)   NOT NULL                 COMMENT '模型展示名称，如"GPT-4o 正式环境"',
  `model_identifier`     VARCHAR(100)   NOT NULL                 COMMENT '调用时传给 LLM 的 model 字段，如 gpt-4o，平台内唯一',
  `provider_type`        VARCHAR(50)    NOT NULL                 COMMENT '供应商类型：OPENAI/AZURE_OPENAI/ANTHROPIC/GOOGLE/MOONSHOT/TONGYI/BAICHUAN/WENXIN/DEEPSEEK/OLLAMA/VLLM/CUSTOM_GATEWAY',
  `base_url`             VARCHAR(500)   NOT NULL                 COMMENT '基础请求地址，如 https://api.openai.com/v1',
  `api_path`             VARCHAR(200)   DEFAULT '/v1/chat/completions' COMMENT 'API 接口路径',
  `api_version`          VARCHAR(50)    DEFAULT NULL             COMMENT 'API 版本，Azure OpenAI 必填，如 2024-10-21',
  `timeout_ms`           INT            NOT NULL DEFAULT 30000   COMMENT '请求超时时间（毫秒），范围 1000-300000',
  `is_default`           TINYINT(1)     NOT NULL DEFAULT 0       COMMENT '是否为默认模型，全局唯一：1=是, 0=否',
  `status`               VARCHAR(20)    NOT NULL DEFAULT 'enabled' COMMENT '模型状态：enabled=启用, disabled=停用',
  `visibility`           VARCHAR(30)    NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性：PUBLIC=全局可见, TENANT_WHITELIST=指定租户, PRIVATE=平台私有',
  `visibility_tenant_ids` JSON          DEFAULT NULL             COMMENT '可见租户ID列表，visibility=TENANT_WHITELIST 时生效',
  -- 模型能力标注
  `supports_tool_call`   TINYINT(1)     NOT NULL DEFAULT 0       COMMENT '是否支持工具调用（Function Calling）：1=支持, 0=不支持',
  `supports_structured_output` TINYINT(1) NOT NULL DEFAULT 0    COMMENT '是否支持强制 JSON 结构化输出：1=支持, 0=不支持',
  `supports_vision`      TINYINT(1)     NOT NULL DEFAULT 0       COMMENT '是否支持视觉输入（图片）：1=支持, 0=不支持',
  `supports_audio`       TINYINT(1)     NOT NULL DEFAULT 0       COMMENT '是否支持音频输入：1=支持, 0=不支持',
  `is_reasoning_model`   TINYINT(1)     NOT NULL DEFAULT 0       COMMENT '是否为推理模型（o1/o3/DeepSeek-R1 等）：1=是, 0=否',
  `context_window`       INT            DEFAULT NULL             COMMENT '上下文窗口大小（Token 数），如 128000',
  `max_output_tokens`    INT            DEFAULT NULL             COMMENT '单次最大输出 Token 数',
  -- 计费信息
  `input_price_per_1m`   DECIMAL(12, 6) DEFAULT NULL             COMMENT '输入 Token 单价（USD/1M tokens）',
  `output_price_per_1m`  DECIMAL(12, 6) DEFAULT NULL             COMMENT '输出 Token 单价（USD/1M tokens）',
  -- 可用性测试最新结果（冗余存储，避免联表）
  `last_test_status`     VARCHAR(20)    NOT NULL DEFAULT 'UNTESTED' COMMENT '最近测试状态：UNTESTED=未测试, AVAILABLE=可用, ISSUE=存在问题',
  `last_test_message`    VARCHAR(500)   DEFAULT NULL             COMMENT '最近测试错误信息',
  `last_test_at`         DATETIME(3)    DEFAULT NULL             COMMENT '最近测试时间',
  `last_test_latency_ms` INT            DEFAULT NULL             COMMENT '最近测试耗时（毫秒）',
  -- 通用字段
  `created_by`           VARCHAR(36)    DEFAULT NULL             COMMENT '创建人用户ID',
  `updated_by`           VARCHAR(36)    DEFAULT NULL             COMMENT '最后修改人用户ID',
  `is_deleted`           TINYINT(1)     NOT NULL DEFAULT 0       COMMENT '软删除标记：1=已删除, 0=正常',
  `deleted_at`           DATETIME(3)    DEFAULT NULL             COMMENT '软删除时间',
  `created_at`           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_identifier` (`model_identifier`, `is_deleted`),
  KEY `idx_provider_type` (`provider_type`),
  KEY `idx_status_deleted` (`status`, `is_deleted`),
  KEY `idx_is_default` (`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 模型配置表（平台级，无 tenant_id 隔离）';
```

**关键字段说明**：
- `model_identifier`：调用 LLM 时 `model` 参数的实际值，平台内唯一，一旦创建不可修改。
- `visibility_tenant_ids`：JSON 数组，存储白名单租户 ID，仅 `visibility=TENANT_WHITELIST` 时有效。
- `last_test_*`：冗余存储最近一次测试结果，避免每次展示模型列表都联表查询 `llm_availability_test`。

**联合索引设计说明**：
- `idx_status_deleted (status, is_deleted)`：支持列表页按状态筛选的高频查询，`is_deleted=0` 过滤软删除记录。

---

### 5.2 llm_secret（密钥配置表）

**用途**：存储 LLM 模型的 API Key，支持一个模型关联多个密钥（Key 池化）。明文 Key 使用 AES-256-GCM 加密后存储，接口层不返回原始密钥。

```sql
CREATE TABLE `llm_secret` (
  `id`             VARCHAR(36)   NOT NULL                 COMMENT '密钥UUID主键',
  `model_id`       VARCHAR(36)   NOT NULL                 COMMENT '关联的模型ID，引用 llm_model.id',
  `alias`          VARCHAR(100)  DEFAULT NULL             COMMENT '密钥别名，便于管理，如"主 Key"、"备用 Key 1"',
  `encrypted_key`  TEXT          NOT NULL                 COMMENT 'AES-256-GCM 加密后的 API Key 密文（Base64 编码）',
  `key_iv`         VARCHAR(50)   NOT NULL                 COMMENT 'AES-GCM 加密向量 IV（Base64 编码）',
  `key_tag`        VARCHAR(50)   NOT NULL                 COMMENT 'AES-GCM 认证标签（Base64 编码）',
  `key_version`    INT           NOT NULL DEFAULT 1       COMMENT '密钥加密版本号，用于轮换加密主密钥时标识密钥版本',
  `weight`         INT           NOT NULL DEFAULT 1       COMMENT 'Key 池轮转权重，权重越高分配越多请求，范围 1-100',
  `status`         VARCHAR(20)   NOT NULL DEFAULT 'active' COMMENT '密钥状态：active=启用, disabled=停用, invalid=已失效（自动标记）',
  `consecutive_failures` INT     NOT NULL DEFAULT 0       COMMENT '连续调用失败次数，达到阈值（默认3）自动标记为 invalid',
  `last_used_at`   DATETIME(3)   DEFAULT NULL             COMMENT '最近使用时间',
  `last_test_status` VARCHAR(20) NOT NULL DEFAULT 'UNTESTED' COMMENT '独立测试状态：UNTESTED/AVAILABLE/ISSUE',
  `last_test_at`   DATETIME(3)   DEFAULT NULL             COMMENT '最近独立测试时间',
  `created_by`     VARCHAR(36)   DEFAULT NULL             COMMENT '创建人用户ID',
  `is_deleted`     TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '软删除标记',
  `deleted_at`     DATETIME(3)   DEFAULT NULL             COMMENT '软删除时间',
  `created_at`     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_model_id_status` (`model_id`, `status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM API 密钥配置表（AES-256-GCM 加密存储）';
```

**关键字段说明**：
- `encrypted_key` / `key_iv` / `key_tag`：三字段组合实现 AES-256-GCM 加密，IV 每次加密随机生成，Tag 用于完整性校验。
- `key_version`：加密主密钥轮换时递增，系统可识别旧版本密钥并触发重加密流程。
- `consecutive_failures`：由调用层在每次 LLM 调用时更新；成功时归零，失败时 +1；达阈值时将 `status` 改为 `invalid` 并触发告警。

**联合索引设计说明**：
- `idx_model_id_status (model_id, status, is_deleted)`：Key 池路由时按模型查询所有有效密钥，命中率高。

---

### 5.3 llm_availability_test（可用性测试历史表）

**用途**：记录每次对 LLM 模型执行可用性测试的历史结果。最新结果回写到 `llm_model` 表，此表仅保留历史记录用于趋势分析和定时拨测。

```sql
CREATE TABLE `llm_availability_test` (
  `id`             VARCHAR(36)   NOT NULL                 COMMENT '测试记录UUID主键',
  `model_id`       VARCHAR(36)   NOT NULL                 COMMENT '被测试的模型ID，引用 llm_model.id',
  `trigger_type`   VARCHAR(20)   NOT NULL DEFAULT 'manual' COMMENT '触发方式：manual=手动触发, scheduled=定时拨测',
  `status`         VARCHAR(20)   NOT NULL                 COMMENT '测试结果：success=成功, failed=失败',
  `error_type`     VARCHAR(50)   DEFAULT NULL             COMMENT '错误类型：auth_failed/quota_exceeded/model_not_found/timeout/network_error/service_error/response_format_error',
  `error_message`  VARCHAR(1000) DEFAULT NULL             COMMENT '错误详细信息',
  `latency_ms`     INT           DEFAULT NULL             COMMENT '测试耗时（毫秒）',
  `http_status`    INT           DEFAULT NULL             COMMENT 'LLM 供应商返回的 HTTP 状态码',
  `tested_by`      VARCHAR(36)   DEFAULT NULL             COMMENT '手动触发时的操作人用户ID，定时触发时为 null',
  `created_at`     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '测试时间',
  PRIMARY KEY (`id`),
  KEY `idx_model_id_created` (`model_id`, `created_at`),
  KEY `idx_model_id_status` (`model_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 可用性测试历史记录表';
```

**关键字段说明**：
- `error_type`：标准化的错误类型枚举，便于统计分类分析各供应商的故障类型分布。
- 此表为只追加写入（append-only），不执行物理删除，依赖定期归档/清理策略。

---

### 5.4 llm_model_key_pool（Key 池配置表）

**用途**：存储模型级别的 Key 池轮转策略配置（P1 功能）。MVP 阶段每个模型只使用单一密钥，此表在 P1 阶段实现多 Key 轮转后启用。

```sql
CREATE TABLE `llm_model_key_pool` (
  `id`                    VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `model_id`              VARCHAR(36)   NOT NULL                 COMMENT '关联模型ID，引用 llm_model.id，唯一',
  `rotation_strategy`     VARCHAR(30)   NOT NULL DEFAULT 'weighted_round_robin' COMMENT '轮转策略：weighted_round_robin=加权轮询, random=随机, least_used=最少使用',
  `failure_threshold`     INT           NOT NULL DEFAULT 3       COMMENT '连续失败多少次后自动将 Key 标记为 invalid',
  `enable_auto_recovery`  TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '是否允许 invalid Key 在指定时间后自动恢复：1=是, 0=否',
  `recovery_interval_min` INT           DEFAULT 60               COMMENT '自动恢复间隔（分钟），enable_auto_recovery=1 时生效',
  `created_at`            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_id` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 模型 Key 池策略配置表（P1）';
```

---

## 6. 智能体（Agent Studio）

### 6.1 agent（智能体基础信息表）

**用途**：存储智能体的基础元数据、草稿配置和已发布配置。草稿配置和发布配置均以 JSON 形式存储，两者并存以支持「修改草稿不影响线上服务」的核心设计原则。

```sql
CREATE TABLE `agent` (
  `id`               VARCHAR(36)   NOT NULL                 COMMENT '智能体UUID主键',
  `tenant_id`        VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID',
  `name`             VARCHAR(64)   NOT NULL                 COMMENT '智能体名称，同一租户内唯一，最长64字符',
  `description`      VARCHAR(256)  DEFAULT NULL             COMMENT '智能体描述，对调用方可见',
  `avatar`           VARCHAR(500)  DEFAULT NULL             COMMENT '头像URL或 Emoji 字符',
  `agent_type`       VARCHAR(20)   NOT NULL DEFAULT 'chat'  COMMENT '智能体类型：chat=对话型, tool_use=工具调用型, react=ReAct',
  `tags`             JSON          DEFAULT NULL             COMMENT '标签列表，JSON字符串数组，如 ["客服","知识库"]',
  `status`           VARCHAR(20)   NOT NULL DEFAULT 'draft' COMMENT '当前状态：draft=草稿, published=已发布, archived=已归档',
  -- 草稿配置（JSON，实时自动保存）
  `draft_config`     JSON          DEFAULT NULL             COMMENT '草稿配置快照，包含完整的提示词/模型/工具/知识库等配置',
  `draft_updated_at` DATETIME(3)   DEFAULT NULL             COMMENT '草稿最后保存时间',
  -- 已发布配置（JSON，发布时从 draft_config 复制）
  `published_config` JSON          DEFAULT NULL             COMMENT '当前生效的已发布配置快照',
  `published_version` VARCHAR(50)  DEFAULT NULL             COMMENT '当前已发布版本号，格式 YYYYMMDD-HHMM-{n}',
  `published_at`     DATETIME(3)   DEFAULT NULL             COMMENT '最近发布时间',
  `published_by`     VARCHAR(36)   DEFAULT NULL             COMMENT '最近发布人用户ID',
  -- 通用字段
  `created_by`       VARCHAR(36)   DEFAULT NULL             COMMENT '创建人用户ID',
  `updated_by`       VARCHAR(36)   DEFAULT NULL             COMMENT '最后修改人用户ID',
  `is_deleted`       TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '软删除标记：1=已删除, 0=正常',
  `deleted_at`       DATETIME(3)   DEFAULT NULL             COMMENT '软删除时间',
  `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`, `is_deleted`),
  KEY `idx_tenant_created` (`tenant_id`, `created_at`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体基础信息表';
```

**关键字段说明**：
- `draft_config` / `published_config`：大 JSON 对象，包含 system_prompt、model_id、temperature、工具列表、知识库配置、行为策略等全部配置。API 调用使用 `published_config`，调试使用 `draft_config`。
- `agent_type`：影响配置页默认参数（如 react 类型默认开启 max_iterations）。
- `tags`：JSON 数组，支持多标签。

**联合索引设计说明**：
- `idx_tenant_status (tenant_id, status, is_deleted)`：列表页按租户 + 状态过滤的最高频查询。

---

### 6.2 agent_version（版本快照表）

**用途**：记录每次智能体发布产生的版本快照，支持版本历史查看和回滚。每次发布时从 `draft_config` 打快照，最多保留最近 20 个发布版本。

```sql
CREATE TABLE `agent_version` (
  `id`              VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `agent_id`        VARCHAR(36)   NOT NULL                 COMMENT '关联智能体ID，引用 agent.id',
  `tenant_id`       VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID（冗余，便于按租户查询）',
  `version`         VARCHAR(50)   NOT NULL                 COMMENT '版本号，格式 YYYYMMDD-HHMM-{n}，如 20260318-1430-1',
  `version_note`    VARCHAR(500)  DEFAULT NULL             COMMENT '版本备注，由发布者填写，如"修复提示词偏差"',
  `config_snapshot` JSON          NOT NULL                 COMMENT '发布时的完整配置快照（不可修改）',
  `status`          VARCHAR(20)   NOT NULL DEFAULT 'published' COMMENT '版本状态：published=当前发布, deprecated=已被新版本替代, archived=已归档',
  `published_by`    VARCHAR(36)   DEFAULT NULL             COMMENT '发布人用户ID',
  `published_at`    DATETIME(3)   NOT NULL                 COMMENT '发布时间',
  `created_at`      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_id_published` (`agent_id`, `published_at` DESC),
  KEY `idx_tenant_agent` (`tenant_id`, `agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体版本快照表（每次发布时生成，不可修改）';
```

---

### 6.3 agent_tool_binding（工具绑定表）

**用途**：记录智能体当前草稿配置中绑定的工具。已发布配置中的工具关系也存储在 `agent.published_config` JSON 中，此表主要用于快速查询「某工具被哪些智能体引用」。

```sql
CREATE TABLE `agent_tool_binding` (
  `id`           VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `agent_id`     VARCHAR(36)   NOT NULL                 COMMENT '关联智能体ID',
  `tenant_id`    VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID',
  `tool_id`      VARCHAR(36)   NOT NULL                 COMMENT '工具ID（引用工具表或内置工具标识）',
  `tool_type`    VARCHAR(30)   NOT NULL                 COMMENT '工具来源类型：builtin=平台内置, mcp=MCP工具, custom=自定义',
  `tool_name`    VARCHAR(100)  NOT NULL                 COMMENT '工具标识名（LLM 通过此名调用）',
  `is_enabled`   TINYINT(1)    NOT NULL DEFAULT 1       COMMENT '是否启用：1=启用（LLM可调用）, 0=禁用（保留配置但不暴露给LLM）',
  `sort_order`   INT           NOT NULL DEFAULT 0       COMMENT '排序序号',
  `created_at`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_tool_id` (`tool_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体工具绑定关系表';
```

---

### 6.4 agent_kb_binding（知识库绑定表）

**用途**：记录智能体与知识库的绑定关系，支持每个绑定项独立配置检索策略（覆盖知识库默认配置）。

```sql
CREATE TABLE `agent_kb_binding` (
  `id`                         VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `agent_id`                   VARCHAR(36)   NOT NULL                 COMMENT '关联智能体ID',
  `tenant_id`                  VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID',
  `kb_id`                      VARCHAR(36)   NOT NULL                 COMMENT '知识库ID，引用 knowledge_base.id',
  `use_kb_default_strategy`    TINYINT(1)    NOT NULL DEFAULT 1       COMMENT '是否使用知识库默认检索策略：1=是, 0=自定义覆盖',
  `retrieval_strategy`         VARCHAR(20)   DEFAULT NULL             COMMENT '覆盖检索策略：vector/bm25/hybrid，use_kb_default_strategy=0时生效',
  `top_k`                      INT           DEFAULT 5                COMMENT '覆盖 top_k 值',
  `score_threshold`            DECIMAL(4,3)  DEFAULT 0.500            COMMENT '覆盖相似度阈值，0.000~1.000',
  `reranker_enabled`           TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '是否在智能体级别启用 Reranker',
  `reranker_model`             VARCHAR(100)  DEFAULT NULL             COMMENT 'Reranker 模型标识',
  `context_injection_position` VARCHAR(30)   NOT NULL DEFAULT 'system_prompt' COMMENT '检索结果注入位置：system_prompt/user_message',
  `citation_enabled`           TINYINT(1)    NOT NULL DEFAULT 1       COMMENT '是否在响应中标注知识来源引用',
  `citation_format`            VARCHAR(20)   DEFAULT 'footnote'       COMMENT '引用格式：footnote=脚注样式, inline_list=末尾引用列表',
  `sort_order`                 INT           NOT NULL DEFAULT 0       COMMENT '多知识库时的检索优先级排序',
  `created_at`                 DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`                 DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_kb` (`agent_id`, `kb_id`),
  KEY `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体知识库绑定关系表';
```

---

### 6.5 agent_skill_binding（Skill 绑定表）

**用途**：记录智能体与 Skill 的绑定关系，支持版本锁定和别名配置。

```sql
CREATE TABLE `agent_skill_binding` (
  `id`           VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `agent_id`     VARCHAR(36)   NOT NULL                 COMMENT '关联智能体ID',
  `tenant_id`    VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID',
  `skill_id`     VARCHAR(36)   NOT NULL                 COMMENT 'Skill ID，引用 skill.id',
  `version_pin`  VARCHAR(20)   NOT NULL DEFAULT 'latest' COMMENT '版本锁定：latest=始终用最新版, 或固定版本号如 1.2.0',
  `alias`        VARCHAR(100)  DEFAULT NULL             COMMENT 'Agent 内引用别名，LLM 通过此别名识别该 Skill',
  `is_enabled`   TINYINT(1)    NOT NULL DEFAULT 1       COMMENT '是否启用',
  `sort_order`   INT           NOT NULL DEFAULT 0       COMMENT '排序序号',
  `created_at`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_skill` (`agent_id`, `skill_id`),
  KEY `idx_skill_id` (`skill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体 Skill 绑定关系表';
```

---

## 7. Skill 库

### 7.1 skill（Skill 基础信息表）

**用途**：存储 Skill 的基础元数据。`tenant_id IS NULL` 表示平台级公共 Skill，对所有租户可见。

```sql
CREATE TABLE `skill` (
  `id`              VARCHAR(36)   NOT NULL                 COMMENT 'Skill UUID主键',
  `tenant_id`       VARCHAR(36)   DEFAULT NULL             COMMENT '所属租户ID，NULL表示平台级公共Skill',
  `name`            VARCHAR(100)  NOT NULL                 COMMENT 'Skill 技术名称，租户内唯一，仅字母数字下划线',
  `display_name`    VARCHAR(100)  NOT NULL                 COMMENT 'Skill 展示名称，支持中文',
  `description`     VARCHAR(500)  DEFAULT NULL             COMMENT '功能描述，100字以内',
  `skill_type`      VARCHAR(20)   NOT NULL                 COMMENT 'Skill类型：atomic=原子技能, composite=复合技能, extraction=抽取技能, generation=生成技能',
  `category`        VARCHAR(50)   DEFAULT NULL             COMMENT '分类标签，如"文本处理"/"数据抽取"/"内容生成"',
  `tags`            JSON          DEFAULT NULL             COMMENT '自定义标签列表，JSON字符串数组',
  `skill_tags`      JSON          DEFAULT NULL             COMMENT '能力标签（多Agent场景路由用），如 ["translation","zh-to-en"]',
  `icon`            VARCHAR(200)  DEFAULT NULL             COMMENT '图标标识',
  `visibility`      VARCHAR(30)   NOT NULL DEFAULT 'tenant_shared' COMMENT '可见性：platform_public=平台公开, tenant_shared=租户内共享, agent_private=私有',
  `allowed_tenant_ids` JSON       DEFAULT NULL             COMMENT 'visibility=platform_public 时限定可访问的租户ID列表，null=全平台可见',
  `current_version` VARCHAR(20)   DEFAULT NULL             COMMENT '当前已发布版本号（语义化版本，如 1.2.0）',
  `status`          VARCHAR(20)   NOT NULL DEFAULT 'active' COMMENT 'Skill状态：active=正常, archived=已归档',
  `created_by`      VARCHAR(36)   DEFAULT NULL             COMMENT '创建人用户ID',
  `is_deleted`      TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '软删除标记',
  `deleted_at`      DATETIME(3)   DEFAULT NULL             COMMENT '软删除时间',
  `created_at`      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`, `is_deleted`),
  KEY `idx_visibility` (`visibility`, `is_deleted`),
  KEY `idx_skill_type` (`skill_type`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill 基础信息表';
```

**关键字段说明**：
- `tenant_id = NULL`：平台级 Skill，需在应用层用 `tenant_id IS NULL OR tenant_id = ?` 的形式查询可用 Skill 列表。
- `skill_tags`：JSON 数组，用于 Multi-Agent 场景中 Orchestrator 按能力标签路由任务。

---

### 7.2 skill_version（版本快照表）

**用途**：记录 Skill 每个已发布版本的完整配置快照，包含提示词、Schema、模型配置等。版本快照一旦创建不可修改，保证可回溯性。

```sql
CREATE TABLE `skill_version` (
  `id`              VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `skill_id`        VARCHAR(36)   NOT NULL                 COMMENT '关联 Skill ID，引用 skill.id',
  `tenant_id`       VARCHAR(36)   DEFAULT NULL             COMMENT '冗余字段，便于按租户查询',
  `version`         VARCHAR(20)   NOT NULL                 COMMENT '版本号，语义化版本如 1.2.0',
  `status`          VARCHAR(20)   NOT NULL DEFAULT 'draft' COMMENT '版本状态：draft=草稿, published=已发布, deprecated=已废弃',
  `config_snapshot` JSON          NOT NULL                 COMMENT '完整配置快照：提示词/Schema/模型配置/Few-shot示例/依赖工具列表',
  `changelog`       TEXT          DEFAULT NULL             COMMENT '版本变更说明',
  `published_by`    VARCHAR(36)   DEFAULT NULL             COMMENT '发布人用户ID',
  `published_at`    DATETIME(3)   DEFAULT NULL             COMMENT '发布时间',
  `created_at`      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_version` (`skill_id`, `version`),
  KEY `idx_skill_status` (`skill_id`, `status`),
  KEY `idx_published_at` (`skill_id`, `published_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill 版本快照表（发布后不可修改）';
```

**关键字段说明**：
- `config_snapshot`：包含 `system_prompt`、`prompt_template`、`input_schema`、`output_schema`、`model_config`、`few_shot_examples`、`steps`（Composite Skill）等完整配置。
- 每次发布时，旧的 `published` 版本状态变为 `deprecated`，只有最新版本处于 `published` 状态。

---

### 7.3 skill_invocation（调用记录表）

**用途**：记录 Skill 的每次调用明细，用于质量分析、性能统计和 LLM-as-Judge 评估。

```sql
CREATE TABLE `skill_invocation` (
  `id`              VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `skill_id`        VARCHAR(36)   NOT NULL                 COMMENT 'Skill ID',
  `tenant_id`       VARCHAR(36)   DEFAULT NULL             COMMENT '调用租户ID',
  `version`         VARCHAR(20)   NOT NULL                 COMMENT '调用的 Skill 版本号',
  `caller_type`     VARCHAR(20)   NOT NULL                 COMMENT '调用方类型：agent=智能体, workflow=工作流, api=直接API调用',
  `caller_id`       VARCHAR(36)   DEFAULT NULL             COMMENT '调用方ID（agent_id 或 workflow_run_id）',
  `trace_id`        VARCHAR(36)   DEFAULT NULL             COMMENT '关联全链路 Trace ID，引用 trace_turn.id',
  `inputs`          JSON          DEFAULT NULL             COMMENT '调用输入参数（脱敏处理后存储）',
  `outputs`         JSON          DEFAULT NULL             COMMENT '调用输出内容（可选存储，敏感内容可置null）',
  `status`          VARCHAR(20)   NOT NULL                 COMMENT '调用结果：success=成功, failed=失败, timeout=超时',
  `latency_ms`      INT           DEFAULT NULL             COMMENT '调用总耗时（毫秒）',
  `input_tokens`    INT           DEFAULT NULL             COMMENT '本次调用消耗的输入 Token 数',
  `output_tokens`   INT           DEFAULT NULL             COMMENT '本次调用消耗的输出 Token 数',
  `error_code`      VARCHAR(50)   DEFAULT NULL             COMMENT '错误码（失败时）',
  `error_message`   TEXT          DEFAULT NULL             COMMENT '错误详细信息',
  `created_at`      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '调用时间',
  PRIMARY KEY (`id`),
  KEY `idx_skill_created` (`skill_id`, `created_at`),
  KEY `idx_tenant_skill` (`tenant_id`, `skill_id`, `created_at`),
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill 调用记录表（用于质量分析和成本统计）';
```

**联合索引设计说明**：
- `idx_skill_created (skill_id, created_at)`：Skill 详情页查询近期调用记录的主要索引。
- `idx_tenant_skill (tenant_id, skill_id, created_at)`：按租户统计 Skill 调用量时使用。

---

## 8. 知识库

### 8.1 knowledge_base（知识库表）

**用途**：存储知识库的基础配置，包括 Embedding 模型选择、默认检索策略等。创建后 Embedding 模型不可更改。

```sql
CREATE TABLE `knowledge_base` (
  `id`                    VARCHAR(36)   NOT NULL                 COMMENT '知识库UUID主键',
  `tenant_id`             VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID，NULL表示平台公共知识库',
  `name`                  VARCHAR(64)   NOT NULL                 COMMENT '知识库名称，同一租户内唯一，最长64字符',
  `description`           VARCHAR(256)  DEFAULT NULL             COMMENT '知识库描述',
  `embedding_model_id`    VARCHAR(36)   NOT NULL                 COMMENT 'Embedding模型ID（引用 llm_model.id），创建后不可修改',
  `embedding_model_name`  VARCHAR(100)  DEFAULT NULL             COMMENT 'Embedding模型标识（冗余，防止模型被删后丢失）',
  `vector_dimension`      INT           DEFAULT NULL             COMMENT 'Embedding向量维度（由模型决定，如 1536）',
  `retrieval_strategy`    VARCHAR(20)   NOT NULL DEFAULT 'hybrid' COMMENT '默认检索策略：vector=纯向量, bm25=关键词, hybrid=混合',
  `qdrant_collection`     VARCHAR(200)  DEFAULT NULL             COMMENT 'Qdrant Collection名称，格式 kb_{tenant_id}_{kb_id}',
  `visibility`            VARCHAR(20)   NOT NULL DEFAULT 'tenant' COMMENT '可见范围：tenant=租户内共享, platform=平台公共（仅超管可创建）',
  `status`                VARCHAR(20)   NOT NULL DEFAULT 'active' COMMENT '状态：active=启用, inactive=禁用',
  `document_count`        INT           NOT NULL DEFAULT 0       COMMENT '文档总数（冗余统计，实时维护）',
  `chunk_count`           INT           NOT NULL DEFAULT 0       COMMENT 'Chunk总数（冗余统计）',
  `total_size_bytes`      BIGINT        NOT NULL DEFAULT 0       COMMENT '原始文档文件总大小（字节，冗余统计）',
  `created_by`            VARCHAR(36)   DEFAULT NULL             COMMENT '创建人用户ID',
  `is_deleted`            TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '软删除标记',
  `deleted_at`            DATETIME(3)   DEFAULT NULL             COMMENT '软删除时间',
  `created_at`            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`, `is_deleted`),
  KEY `idx_visibility` (`visibility`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库配置表';
```

**关键字段说明**：
- `embedding_model_id`：不可修改，一旦确定，所有文档的向量均以此模型生成，变更需新建知识库。
- `qdrant_collection`：对应 Qdrant 向量数据库中的 Collection 名称。软删除后，7 天内定时任务清理 Qdrant 中的数据。
- `document_count` / `total_size_bytes`：冗余统计字段，通过文档操作时的触发器/应用层更新，避免列表页频繁 COUNT 查询。

---

### 8.2 kb_document（文档表）

**用途**：存储知识库内每个文档的元数据，包括文件来源、处理状态和统计信息。文档内容和向量存储在 Qdrant 中，此表仅存元数据。

```sql
CREATE TABLE `kb_document` (
  `id`                VARCHAR(36)   NOT NULL                 COMMENT '文档UUID主键',
  `kb_id`             VARCHAR(36)   NOT NULL                 COMMENT '所属知识库ID，引用 knowledge_base.id',
  `tenant_id`         VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID（冗余）',
  `title`             VARCHAR(500)  NOT NULL                 COMMENT '文档标题（文件名或URL页面标题）',
  `source_type`       VARCHAR(20)   NOT NULL                 COMMENT '来源类型：upload=文件上传, url=URL爬取',
  `file_path`         VARCHAR(1000) DEFAULT NULL             COMMENT '存储路径（对象存储路径），source_type=upload时有效',
  `source_url`        VARCHAR(2000) DEFAULT NULL             COMMENT '原始URL，source_type=url时有效',
  `file_type`         VARCHAR(20)   DEFAULT NULL             COMMENT '文件类型扩展名，如 pdf/docx/md/txt',
  `file_size_bytes`   BIGINT        DEFAULT NULL             COMMENT '原始文件大小（字节）',
  `status`            VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT '处理状态：pending=待处理, processing=处理中, completed=已完成, failed=处理失败',
  `process_progress`  INT           NOT NULL DEFAULT 0       COMMENT '处理进度（0-100），processing状态时更新',
  `error_message`     TEXT          DEFAULT NULL             COMMENT '处理失败的错误信息',
  `chunk_count`       INT           NOT NULL DEFAULT 0       COMMENT '切分后的 Chunk 总数（completed后填入）',
  `char_count`        INT           DEFAULT NULL             COMMENT '文档总字符数',
  `chunking_config`   JSON          DEFAULT NULL             COMMENT '此文档的切分配置（覆盖知识库默认），null表示使用知识库默认',
  `metadata`          JSON          DEFAULT NULL             COMMENT '扩展元数据，如爬取配置、文档语言、页码等',
  `created_by`        VARCHAR(36)   DEFAULT NULL             COMMENT '上传人用户ID',
  `is_deleted`        TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '软删除标记',
  `deleted_at`        DATETIME(3)   DEFAULT NULL             COMMENT '软删除时间',
  `created_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '文档上传/爬取时间',
  `updated_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_kb_status` (`kb_id`, `status`, `is_deleted`),
  KEY `idx_tenant_kb` (`tenant_id`, `kb_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档元数据表';
```

**联合索引设计说明**：
- `idx_kb_status (kb_id, status, is_deleted)`：文档列表页按知识库 + 状态过滤的核心索引。

---

### 8.3 kb_chunk（切片元数据表）

**用途**：存储文档切片（Chunk）的元数据。Chunk 的实际文本内容和向量存储在 Qdrant 中，MySQL 仅存储用于管理和统计的元数据。

```sql
CREATE TABLE `kb_chunk` (
  `id`              VARCHAR(36)   NOT NULL                 COMMENT 'Chunk UUID主键（与 Qdrant Point ID 保持一致）',
  `document_id`     VARCHAR(36)   NOT NULL                 COMMENT '所属文档ID，引用 kb_document.id',
  `kb_id`           VARCHAR(36)   NOT NULL                 COMMENT '所属知识库ID（冗余，便于快速删除）',
  `tenant_id`       VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID（冗余）',
  `chunk_index`     INT           NOT NULL                 COMMENT 'Chunk 在文档内的顺序序号（从0开始）',
  `char_count`      INT           DEFAULT NULL             COMMENT 'Chunk 字符数',
  `token_count`     INT           DEFAULT NULL             COMMENT 'Chunk Token估算数',
  `vector_status`   VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT '向量化状态：pending=待向量化, done=已向量化, failed=向量化失败',
  `metadata`        JSON          DEFAULT NULL             COMMENT '扩展元数据，如页码、段落标题路径、位置坐标等',
  `created_at`      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_document_index` (`document_id`, `chunk_index`),
  KEY `idx_kb_vector_status` (`kb_id`, `vector_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库 Chunk 元数据表（文本和向量存于 Qdrant，此表存管理元数据）';
```

**关键字段说明**：
- `id`：与 Qdrant 中 Point 的 ID 保持一致，通过 UUID 可直接定位向量数据库中的对应条目。
- Chunk 的文本内容不存储在 MySQL，减少数据库存储压力；需要展示 Chunk 内容时从 Qdrant Payload 中读取。

---

## 9. 工作流

### 9.1 workflow（工作流表）

**用途**：存储工作流的基础元数据、草稿 DAG 配置和已发布配置，设计模式与 `agent` 表类似（草稿/发布并存）。

```sql
CREATE TABLE `workflow` (
  `id`                VARCHAR(36)   NOT NULL                 COMMENT '工作流UUID主键',
  `tenant_id`         VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID',
  `name`              VARCHAR(100)  NOT NULL                 COMMENT '工作流名称，同一租户内唯一',
  `description`       VARCHAR(500)  DEFAULT NULL             COMMENT '工作流描述',
  `tags`              JSON          DEFAULT NULL             COMMENT '标签列表',
  `status`            VARCHAR(20)   NOT NULL DEFAULT 'draft' COMMENT '状态：draft=草稿, published=已发布, archived=已归档',
  `draft_config`      JSON          DEFAULT NULL             COMMENT '草稿 DAG 配置（节点列表+连线+变量定义）',
  `draft_updated_at`  DATETIME(3)   DEFAULT NULL             COMMENT '草稿最后保存时间',
  `published_config`  JSON          DEFAULT NULL             COMMENT '已发布 DAG 配置',
  `published_version` VARCHAR(50)   DEFAULT NULL             COMMENT '已发布版本号',
  `published_at`      DATETIME(3)   DEFAULT NULL             COMMENT '最近发布时间',
  `published_by`      VARCHAR(36)   DEFAULT NULL             COMMENT '最近发布人用户ID',
  `created_by`        VARCHAR(36)   DEFAULT NULL             COMMENT '创建人用户ID',
  `is_deleted`        TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '软删除标记',
  `deleted_at`        DATETIME(3)   DEFAULT NULL             COMMENT '软删除时间',
  `created_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`, `is_deleted`),
  KEY `idx_tenant_created` (`tenant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流基础信息表';
```

**关键字段说明**：
- `draft_config`：包含完整 DAG 描述：`nodes`（节点数组，每个节点含 type/config/position）和 `edges`（连线，含条件表达式）以及 `variables`（工作流变量定义）。

---

### 9.2 workflow_version（版本快照表）

**用途**：记录工作流每次发布的版本快照，支持回滚。

```sql
CREATE TABLE `workflow_version` (
  `id`              VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `workflow_id`     VARCHAR(36)   NOT NULL                 COMMENT '关联工作流ID',
  `tenant_id`       VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID（冗余）',
  `version`         VARCHAR(50)   NOT NULL                 COMMENT '版本号',
  `version_note`    VARCHAR(500)  DEFAULT NULL             COMMENT '版本备注',
  `config_snapshot` JSON          NOT NULL                 COMMENT '发布时的完整 DAG 配置快照（不可修改）',
  `status`          VARCHAR(20)   NOT NULL DEFAULT 'published' COMMENT '版本状态：published=当前发布, deprecated=已废弃',
  `published_by`    VARCHAR(36)   DEFAULT NULL             COMMENT '发布人用户ID',
  `published_at`    DATETIME(3)   NOT NULL                 COMMENT '发布时间',
  `created_at`      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_workflow_published` (`workflow_id`, `published_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流版本快照表';
```

---

### 9.3 workflow_run（工作流执行记录表）

**用途**：记录每次工作流执行的全局信息，包含触发方式、整体状态和汇总指标。

```sql
CREATE TABLE `workflow_run` (
  `id`               VARCHAR(36)   NOT NULL                 COMMENT '执行记录UUID主键',
  `workflow_id`      VARCHAR(36)   NOT NULL                 COMMENT '关联工作流ID',
  `tenant_id`        VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID',
  `workflow_version` VARCHAR(50)   DEFAULT NULL             COMMENT '执行时使用的工作流版本号',
  `trigger_type`     VARCHAR(30)   NOT NULL                 COMMENT '触发方式：manual=手动触发, schedule=定时触发, api=API触发, webhook=Webhook触发',
  `trigger_by`       VARCHAR(36)   DEFAULT NULL             COMMENT '手动触发人用户ID，定时/Webhook触发时为null',
  `input_variables`  JSON          DEFAULT NULL             COMMENT '触发时传入的输入变量',
  `output_variables` JSON          DEFAULT NULL             COMMENT '工作流最终输出变量',
  `status`           VARCHAR(20)   NOT NULL DEFAULT 'running' COMMENT '执行状态：running=执行中, completed=成功完成, failed=失败, cancelled=已取消, waiting_human=等待人工',
  `error_message`    TEXT          DEFAULT NULL             COMMENT '失败时的错误信息',
  `started_at`       DATETIME(3)   NOT NULL                 COMMENT '执行开始时间',
  `ended_at`         DATETIME(3)   DEFAULT NULL             COMMENT '执行结束时间（null表示仍在执行中）',
  `duration_ms`      INT           DEFAULT NULL             COMMENT '总执行时长（毫秒），结束后填入',
  `node_count`       INT           NOT NULL DEFAULT 0       COMMENT '执行的节点总数',
  `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_workflow_status` (`workflow_id`, `status`, `started_at`),
  KEY `idx_tenant_started` (`tenant_id`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流执行记录表';
```

---

### 9.4 workflow_node_execution（节点执行记录表）

**用途**：记录工作流每次执行中各节点的执行明细，支持执行过程回放和故障定位。

```sql
CREATE TABLE `workflow_node_execution` (
  `id`               VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `run_id`           VARCHAR(36)   NOT NULL                 COMMENT '关联执行记录ID，引用 workflow_run.id',
  `workflow_id`      VARCHAR(36)   NOT NULL                 COMMENT '关联工作流ID（冗余）',
  `tenant_id`        VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID（冗余）',
  `node_id`          VARCHAR(100)  NOT NULL                 COMMENT '节点ID（对应 DAG 配置中的节点标识）',
  `node_type`        VARCHAR(50)   NOT NULL                 COMMENT '节点类型：trigger/llm/tool/skill/agent/condition/loop/human_task/start/end 等',
  `node_name`        VARCHAR(200)  DEFAULT NULL             COMMENT '节点展示名称',
  `execution_index`  INT           NOT NULL DEFAULT 0       COMMENT '执行序号（同一节点在循环中可能多次执行）',
  `input_data`       JSON          DEFAULT NULL             COMMENT '节点输入数据',
  `output_data`      JSON          DEFAULT NULL             COMMENT '节点输出数据',
  `status`           VARCHAR(20)   NOT NULL DEFAULT 'running' COMMENT '节点执行状态：running/completed/failed/skipped',
  `error_message`    TEXT          DEFAULT NULL             COMMENT '失败时的错误信息',
  `started_at`       DATETIME(3)   NOT NULL                 COMMENT '节点开始执行时间',
  `ended_at`         DATETIME(3)   DEFAULT NULL             COMMENT '节点结束时间',
  `duration_ms`      INT           DEFAULT NULL             COMMENT '节点执行耗时（毫秒）',
  `llm_input_tokens` INT           DEFAULT NULL             COMMENT 'LLM节点：输入Token数',
  `llm_output_tokens` INT          DEFAULT NULL             COMMENT 'LLM节点：输出Token数',
  `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_run_id` (`run_id`, `execution_index`),
  KEY `idx_workflow_node` (`workflow_id`, `node_id`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流节点执行明细表';
```

---

### 9.5 human_task（人工任务表）

**用途**：存储工作流中 Human-in-the-Loop 节点产生的待处理人工任务，包含任务状态和处理结果。

```sql
CREATE TABLE `human_task` (
  `id`                VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `run_id`            VARCHAR(36)   NOT NULL                 COMMENT '关联工作流执行记录ID，引用 workflow_run.id',
  `node_id`           VARCHAR(100)  NOT NULL                 COMMENT '触发此人工任务的节点ID',
  `workflow_id`       VARCHAR(36)   NOT NULL                 COMMENT '关联工作流ID（冗余）',
  `tenant_id`         VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID',
  `title`             VARCHAR(200)  NOT NULL                 COMMENT '任务标题，展示在待办列表',
  `description`       TEXT          DEFAULT NULL             COMMENT '任务描述/说明，展示给处理人',
  `context_data`      JSON          DEFAULT NULL             COMMENT '供处理人参考的上下文数据（前序节点输出等）',
  `form_schema`       JSON          DEFAULT NULL             COMMENT '人工输入表单Schema（JSON Schema格式）',
  `assignee_ids`      JSON          DEFAULT NULL             COMMENT '指定处理人用户ID列表，null表示任意有权限用户可处理',
  `status`            VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT '任务状态：pending=待处理, processing=处理中, completed=已完成, expired=已超时',
  `result_data`       JSON          DEFAULT NULL             COMMENT '处理人提交的结果数据（符合 form_schema）',
  `processed_by`      VARCHAR(36)   DEFAULT NULL             COMMENT '处理人用户ID',
  `processed_at`      DATETIME(3)   DEFAULT NULL             COMMENT '处理完成时间',
  `expired_at`        DATETIME(3)   DEFAULT NULL             COMMENT '任务超时时间，到期未处理则自动超时',
  `is_deleted`        TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '软删除标记',
  `deleted_at`        DATETIME(3)   DEFAULT NULL             COMMENT '软删除时间',
  `created_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_run_id` (`run_id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`, `is_deleted`),
  KEY `idx_expired_at` (`expired_at`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工任务表（工作流 Human-in-the-Loop 节点产生）';
```

**关键字段说明**：
- `form_schema`：JSON Schema 格式定义处理人需要填写的表单字段，前端据此动态渲染表单。
- `expired_at`：定时任务扫描此字段，将过期的 `pending` 任务自动变更为 `expired`，并恢复工作流执行（走超时分支）。

---

## 10. 调试与发布

### 10.1 test_suite（测试集表）

**用途**：存储用于批量测试 Agent 的测试集元数据，支持团队共享和版本管理。

```sql
CREATE TABLE `test_suite` (
  `id`           VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `tenant_id`    VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID',
  `agent_id`     VARCHAR(36)   NOT NULL                 COMMENT '关联的 Agent ID',
  `name`         VARCHAR(100)  NOT NULL                 COMMENT '测试集名称',
  `description`  VARCHAR(500)  DEFAULT NULL             COMMENT '测试集描述',
  `tags`         JSON          DEFAULT NULL             COMMENT '测试集标签，如 ["回归测试集","边界用例"]',
  `case_count`   INT           NOT NULL DEFAULT 0       COMMENT '测试用例总数（冗余统计）',
  `created_by`   VARCHAR(36)   DEFAULT NULL             COMMENT '创建人用户ID',
  `is_deleted`   TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '软删除标记',
  `deleted_at`   DATETIME(3)   DEFAULT NULL             COMMENT '软删除时间',
  `created_at`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_agent` (`tenant_id`, `agent_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量测试集元数据表';
```

---

### 10.2 test_case（测试用例表）

**用途**：存储单个测试用例的输入和期望行为规则。

```sql
CREATE TABLE `test_case` (
  `id`                VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `suite_id`          VARCHAR(36)   NOT NULL                 COMMENT '所属测试集ID，引用 test_suite.id',
  `tenant_id`         VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID（冗余）',
  `case_name`         VARCHAR(200)  DEFAULT NULL             COMMENT '用例名称（可选）',
  `input_message`     TEXT          NOT NULL                 COMMENT '用户输入消息',
  `input_context`     JSON          DEFAULT NULL             COMMENT '输入上下文变量（如 user_context: {vip_level: gold}）',
  `expected_behavior` JSON          NOT NULL                 COMMENT '期望行为规则：{description, must_contain[], must_not_contain[], expected_intent}',
  `sort_order`        INT           NOT NULL DEFAULT 0       COMMENT '排序序号',
  `created_by`        VARCHAR(36)   DEFAULT NULL             COMMENT '创建人用户ID',
  `is_deleted`        TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '软删除标记',
  `deleted_at`        DATETIME(3)   DEFAULT NULL             COMMENT '软删除时间',
  `created_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_suite_id` (`suite_id`, `is_deleted`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试用例表';
```

---

### 10.3 test_run（批量测试执行记录表）

**用途**：记录每次批量测试的执行结果和统计信息，支持历史版本对比。

```sql
CREATE TABLE `test_run` (
  `id`               VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `suite_id`         VARCHAR(36)   NOT NULL                 COMMENT '关联测试集ID',
  `tenant_id`        VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID',
  `agent_id`         VARCHAR(36)   NOT NULL                 COMMENT '被测 Agent ID',
  `agent_version`    VARCHAR(50)   DEFAULT NULL             COMMENT '被测 Agent 版本号（draft表示草稿）',
  `status`           VARCHAR(20)   NOT NULL DEFAULT 'running' COMMENT '执行状态：running=执行中, completed=已完成, cancelled=已取消',
  `total_cases`      INT           NOT NULL DEFAULT 0       COMMENT '总用例数',
  `passed_cases`     INT           NOT NULL DEFAULT 0       COMMENT '通过用例数',
  `failed_cases`     INT           NOT NULL DEFAULT 0       COMMENT '失败用例数',
  `error_cases`      INT           NOT NULL DEFAULT 0       COMMENT '执行出错用例数',
  `pass_rate`        DECIMAL(5,4)  DEFAULT NULL             COMMENT '通过率（0.0000~1.0000）',
  `p50_latency_ms`   INT           DEFAULT NULL             COMMENT 'P50 响应延迟（毫秒）',
  `p95_latency_ms`   INT           DEFAULT NULL             COMMENT 'P95 响应延迟（毫秒）',
  `avg_input_tokens` INT           DEFAULT NULL             COMMENT '平均输入Token数',
  `avg_output_tokens` INT          DEFAULT NULL             COMMENT '平均输出Token数',
  `case_results`     JSON          DEFAULT NULL             COMMENT '各用例执行结果详情（数组，存储通过/失败状态和实际输出）',
  `started_at`       DATETIME(3)   NOT NULL                 COMMENT '执行开始时间',
  `ended_at`         DATETIME(3)   DEFAULT NULL             COMMENT '执行结束时间',
  `created_by`       VARCHAR(36)   DEFAULT NULL             COMMENT '触发人用户ID',
  `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_suite_started` (`suite_id`, `started_at` DESC),
  KEY `idx_agent_started` (`agent_id`, `started_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量测试执行记录表';
```

---

### 10.4 ab_test（A/B 测试表）

**用途**：存储 A/B 测试配置，记录实验组/控制组版本、流量分配比例和统计结果。

```sql
CREATE TABLE `ab_test` (
  `id`                   VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `tenant_id`            VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID',
  `agent_id`             VARCHAR(36)   NOT NULL                 COMMENT '关联 Agent ID',
  `name`                 VARCHAR(200)  NOT NULL                 COMMENT 'A/B 测试名称',
  `description`          VARCHAR(500)  DEFAULT NULL             COMMENT '测试目的说明',
  `control_version`      VARCHAR(50)   NOT NULL                 COMMENT '控制组 Agent 版本号',
  `experiment_version`   VARCHAR(50)   NOT NULL                 COMMENT '实验组 Agent 版本号',
  `control_traffic_ratio` DECIMAL(4,3) NOT NULL DEFAULT 0.500  COMMENT '控制组流量占比（0.000~1.000）',
  `status`               VARCHAR(20)   NOT NULL DEFAULT 'running' COMMENT 'A/B测试状态：running=进行中, paused=已暂停, completed=已完成, cancelled=已取消',
  `success_metrics`      JSON          NOT NULL                 COMMENT '衡量指标配置：{primary, secondary[]}',
  `statistical_settings` JSON          DEFAULT NULL             COMMENT '统计配置：{confidence_level, min_sample_size}',
  `result_snapshot`      JSON          DEFAULT NULL             COMMENT '最近一次统计结果快照（定期刷新）',
  `started_at`           DATETIME(3)   DEFAULT NULL             COMMENT '测试开始时间',
  `ended_at`             DATETIME(3)   DEFAULT NULL             COMMENT '测试结束时间',
  `winner`               VARCHAR(20)   DEFAULT NULL             COMMENT '测试结论：control=控制组优胜, experiment=实验组优胜, inconclusive=无显著差异',
  `created_by`           VARCHAR(36)   DEFAULT NULL             COMMENT '创建人用户ID',
  `is_deleted`           TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '软删除标记',
  `deleted_at`           DATETIME(3)   DEFAULT NULL             COMMENT '软删除时间',
  `created_at`           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_agent` (`tenant_id`, `agent_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='A/B 测试配置与结果表';
```

---

### 10.5 api_key（API Key 表）

**用途**：存储智能体对外开放 API 调用所使用的 API Key，支持精细化权限控制和使用量统计。

```sql
CREATE TABLE `api_key` (
  `id`             VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `tenant_id`      VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID',
  `agent_id`       VARCHAR(36)   NOT NULL                 COMMENT '关联的 Agent ID',
  `name`           VARCHAR(100)  NOT NULL                 COMMENT 'API Key 名称/备注',
  `key_prefix`     VARCHAR(10)   NOT NULL                 COMMENT 'Key 前缀（明文展示，如 sk-abc12），用于识别 Key',
  `key_hash`       VARCHAR(64)   NOT NULL                 COMMENT 'API Key 的 SHA-256 哈希值，用于鉴权校验',
  `scopes`         JSON          DEFAULT NULL             COMMENT '授权范围（预留，当前为空表示全权限）',
  `status`         VARCHAR(20)   NOT NULL DEFAULT 'active' COMMENT 'Key状态：active=有效, disabled=已禁用, expired=已过期',
  `expires_at`     DATETIME(3)   DEFAULT NULL             COMMENT '过期时间，null表示永不过期',
  `last_used_at`   DATETIME(3)   DEFAULT NULL             COMMENT '最近使用时间',
  `total_calls`    BIGINT        NOT NULL DEFAULT 0       COMMENT '累计调用次数（冗余统计）',
  `created_by`     VARCHAR(36)   DEFAULT NULL             COMMENT '创建人用户ID',
  `is_deleted`     TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '软删除标记',
  `deleted_at`     DATETIME(3)   DEFAULT NULL             COMMENT '软删除时间',
  `created_at`     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key_hash` (`key_hash`),
  KEY `idx_agent_status` (`agent_id`, `status`, `is_deleted`),
  KEY `idx_tenant_status` (`tenant_id`, `status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 对外 API Key 表';
```

**关键字段说明**：
- `key_hash`：鉴权时对传入的 Key 做 SHA-256 哈希后与此字段比对，原始 Key 只在创建时返回一次，数据库不存储明文。
- `key_prefix`：明文存储 Key 的前 8 位字符（如 `sk-abc12`），用于管理页面让用户识别 Key 身份。

---

## 11. 可观测性

### 11.1 trace_session（会话表）

**用途**：记录用户与 Agent 的每次完整会话，作为 Trace 链路的顶层节点。

```sql
CREATE TABLE `trace_session` (
  `id`                  VARCHAR(36)   NOT NULL                 COMMENT '会话UUID主键（trace_id）',
  `tenant_id`           VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID',
  `agent_id`            VARCHAR(36)   NOT NULL                 COMMENT '关联 Agent ID',
  `agent_version`       VARCHAR(50)   DEFAULT NULL             COMMENT '使用的 Agent 版本号',
  `channel`             VARCHAR(30)   NOT NULL DEFAULT 'api'   COMMENT '访问渠道：api=API调用, widget=嵌入组件, debug=调试模式',
  `user_id`             VARCHAR(36)   DEFAULT NULL             COMMENT '调用用户ID（匿名调用时为null）',
  `user_metadata`       JSON          DEFAULT NULL             COMMENT '调用方自定义用户元数据，如 {vip_level: gold}',
  `api_key_id`          VARCHAR(36)   DEFAULT NULL             COMMENT '使用的 API Key ID（channel=api时填入）',
  `is_debug`            TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '是否调试模式：1=调试, 0=正式',
  `status`              VARCHAR(20)   NOT NULL DEFAULT 'active' COMMENT '会话状态：active=进行中, completed=正常结束, abandoned=用户中断, error=异常结束',
  `turn_count`          INT           NOT NULL DEFAULT 0       COMMENT '对话轮次数（冗余统计）',
  `total_input_tokens`  INT           NOT NULL DEFAULT 0       COMMENT '会话累计输入Token数',
  `total_output_tokens` INT           NOT NULL DEFAULT 0       COMMENT '会话累计输出Token数',
  `started_at`          DATETIME(3)   NOT NULL                 COMMENT '会话开始时间',
  `ended_at`            DATETIME(3)   DEFAULT NULL             COMMENT '会话结束时间（null=仍在进行）',
  `created_at`          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_agent_started` (`tenant_id`, `agent_id`, `started_at` DESC),
  KEY `idx_user_started` (`user_id`, `started_at` DESC),
  KEY `idx_tenant_started` (`tenant_id`, `started_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话 Trace 顶层表（Session 层）';
```

**联合索引设计说明**：
- `idx_tenant_agent_started (tenant_id, agent_id, started_at DESC)`：会话日志页按租户 + Agent + 时间倒序的核心查询索引。

---

### 11.2 trace_turn（对话轮次表）

**用途**：记录会话中每一个对话轮次（用户一次输入 + Agent 完整回复）的详细信息。

```sql
CREATE TABLE `trace_turn` (
  `id`                     VARCHAR(36)   NOT NULL                 COMMENT '轮次UUID主键',
  `session_id`             VARCHAR(36)   NOT NULL                 COMMENT '所属会话ID，引用 trace_session.id',
  `tenant_id`              VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID（冗余）',
  `agent_id`               VARCHAR(36)   NOT NULL                 COMMENT '关联 Agent ID（冗余）',
  `turn_index`             INT           NOT NULL                 COMMENT '轮次序号（从1开始）',
  `user_message`           TEXT          DEFAULT NULL             COMMENT '用户原始输入消息（可能含PII，存储前脱敏）',
  `user_message_tokens`    INT           DEFAULT NULL             COMMENT '用户消息 Token 数',
  `assistant_message`      TEXT          DEFAULT NULL             COMMENT 'Agent 最终回复内容（流式输出完成后汇总）',
  `assistant_message_tokens` INT         DEFAULT NULL             COMMENT 'Agent 回复 Token 数',
  `steps_summary`          JSON          DEFAULT NULL             COMMENT '执行步骤摘要（LLM调用次数/工具调用次数/知识库检索次数）',
  `user_feedback`          VARCHAR(10)   DEFAULT NULL             COMMENT '用户反馈：like=点赞, dislike=点踩',
  `feedback_reason`        VARCHAR(50)   DEFAULT NULL             COMMENT '点踩原因类型：inaccurate/misunderstood/incomplete/other',
  `status`                 VARCHAR(20)   NOT NULL DEFAULT 'running' COMMENT '轮次状态：running=处理中, completed=成功, failed=失败, timeout=超时',
  `error_type`             VARCHAR(50)   DEFAULT NULL             COMMENT '失败类型',
  `latency_ms`             INT           DEFAULT NULL             COMMENT '本轮次总耗时（毫秒）',
  `total_input_tokens`     INT           DEFAULT NULL             COMMENT '本轮次消耗的总输入Token（含历史上下文）',
  `total_output_tokens`    INT           DEFAULT NULL             COMMENT '本轮次消耗的总输出Token',
  `started_at`             DATETIME(3)   NOT NULL                 COMMENT '本轮次开始时间',
  `ended_at`               DATETIME(3)   DEFAULT NULL             COMMENT '本轮次结束时间',
  `created_at`             DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_index` (`session_id`, `turn_index`),
  KEY `idx_tenant_agent_started` (`tenant_id`, `agent_id`, `started_at` DESC),
  KEY `idx_feedback` (`tenant_id`, `user_feedback`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话轮次 Trace 表（Turn 层）';
```

**关键字段说明**：
- `user_message`：存储前需经过 PII 脱敏处理（手机号/身份证/邮箱自动替换为占位符）。
- Step 级别的详细 Trace（含 LLM 调用参数、工具调用输入输出）存储在 Elasticsearch，此表仅存汇总信息。
- `idx_feedback (tenant_id, user_feedback, started_at)`：支持快速查询某时间段内的差评会话。

---

### 11.3 evaluation_result（LLM-as-Judge 评估结果表）

**用途**：存储对 Agent 回复进行 LLM-as-Judge 自动质量评估的结果，支持质量趋势分析。

```sql
CREATE TABLE `evaluation_result` (
  `id`                   VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `turn_id`              VARCHAR(36)   NOT NULL                 COMMENT '关联对话轮次ID，引用 trace_turn.id',
  `session_id`           VARCHAR(36)   NOT NULL                 COMMENT '关联会话ID（冗余）',
  `tenant_id`            VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID（冗余）',
  `agent_id`             VARCHAR(36)   NOT NULL                 COMMENT '关联 Agent ID（冗余）',
  `agent_version`        VARCHAR(50)   DEFAULT NULL             COMMENT '被评估的 Agent 版本',
  `evaluator_model_id`   VARCHAR(36)   DEFAULT NULL             COMMENT '执行评估的 LLM 模型ID（Judge 模型）',
  `score_relevance`      DECIMAL(3,1)  DEFAULT NULL             COMMENT '相关性评分（1.0~5.0）',
  `score_accuracy`       DECIMAL(3,1)  DEFAULT NULL             COMMENT '准确性评分（1.0~5.0）',
  `score_completeness`   DECIMAL(3,1)  DEFAULT NULL             COMMENT '完整性评分（1.0~5.0）',
  `score_conciseness`    DECIMAL(3,1)  DEFAULT NULL             COMMENT '简洁性评分（1.0~5.0）',
  `score_format`         TINYINT(1)    DEFAULT NULL             COMMENT '格式合规评分（0=不合规, 1=合规）',
  `score_hallucination`  DECIMAL(3,1)  DEFAULT NULL             COMMENT '幻觉风险评分（1.0~5.0，越高越差）',
  `score_overall`        DECIMAL(3,1)  DEFAULT NULL             COMMENT '综合评分（1.0~5.0）',
  `issues`               JSON          DEFAULT NULL             COMMENT '具体问题描述列表',
  `eval_type`            VARCHAR(20)   NOT NULL DEFAULT 'auto'  COMMENT '评估类型：auto=自动采样评估, manual=手动触发, debug=调试模式全量',
  `created_at`           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '评估时间',
  PRIMARY KEY (`id`),
  KEY `idx_turn_id` (`turn_id`),
  KEY `idx_tenant_agent_created` (`tenant_id`, `agent_id`, `created_at` DESC),
  KEY `idx_overall_score` (`tenant_id`, `agent_id`, `score_overall`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM-as-Judge 自动质量评估结果表';
```

---

### 11.4 security_event（安全审计事件表）

**用途**：记录平台安全相关事件，包括 Prompt Injection 检测、PII 泄露告警、跨租户访问尝试等。

```sql
CREATE TABLE `security_event` (
  `id`            VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `tenant_id`     VARCHAR(36)   DEFAULT NULL             COMMENT '相关租户ID（平台级事件可为null）',
  `agent_id`      VARCHAR(36)   DEFAULT NULL             COMMENT '相关 Agent ID',
  `session_id`    VARCHAR(36)   DEFAULT NULL             COMMENT '相关会话ID',
  `turn_id`       VARCHAR(36)   DEFAULT NULL             COMMENT '相关对话轮次ID',
  `user_id`       VARCHAR(36)   DEFAULT NULL             COMMENT '相关用户ID',
  `event_type`    VARCHAR(50)   NOT NULL                 COMMENT '事件类型：prompt_injection=提示词注入, pii_detected=PII检测, cross_tenant_access=跨租户访问, input_blocked=输入拦截, output_blocked=输出拦截, api_key_abuse=API Key滥用',
  `severity`      VARCHAR(10)   NOT NULL DEFAULT 'medium' COMMENT '严重程度：low=低, medium=中, high=高, critical=严重',
  `description`   TEXT          DEFAULT NULL             COMMENT '事件描述',
  `event_data`    JSON          DEFAULT NULL             COMMENT '事件相关数据（脱敏处理）',
  `source_ip`     VARCHAR(50)   DEFAULT NULL             COMMENT '来源IP地址',
  `created_at`    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_type_created` (`tenant_id`, `event_type`, `created_at` DESC),
  KEY `idx_severity_created` (`severity`, `created_at` DESC),
  KEY `idx_agent_created` (`agent_id`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全审计事件表（只追加，不修改）';
```

**关键字段说明**：
- 此表为只追加写入（append-only），不执行软删除，保障审计完整性。
- `event_data` 中的内容需经过脱敏处理，不存储完整用户输入内容。

---

### 11.5 cost_daily_stats（成本日统计表）

**用途**：按「租户 + 模型 + 日期 + Agent」维度汇总每日 Token 消耗和估算成本，用于成本分析看板和预算告警。

```sql
CREATE TABLE `cost_daily_stats` (
  `id`                 VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `tenant_id`          VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID',
  `agent_id`           VARCHAR(36)   DEFAULT NULL             COMMENT 'Agent ID，null表示租户整体统计',
  `model_id`           VARCHAR(36)   NOT NULL                 COMMENT 'LLM 模型ID',
  `model_identifier`   VARCHAR(100)  NOT NULL                 COMMENT 'LLM 模型标识（冗余，防止模型被删）',
  `stat_date`          DATE          NOT NULL                 COMMENT '统计日期',
  `call_count`         BIGINT        NOT NULL DEFAULT 0       COMMENT '当日调用次数',
  `input_tokens`       BIGINT        NOT NULL DEFAULT 0       COMMENT '当日输入Token总量',
  `output_tokens`      BIGINT        NOT NULL DEFAULT 0       COMMENT '当日输出Token总量',
  `debug_input_tokens` BIGINT        NOT NULL DEFAULT 0       COMMENT '当日调试模式输入Token（不计入成本）',
  `debug_output_tokens` BIGINT       NOT NULL DEFAULT 0       COMMENT '当日调试模式输出Token',
  `cost_usd`           DECIMAL(12,6) NOT NULL DEFAULT 0       COMMENT '当日估算成本（USD）',
  `cost_cny`           DECIMAL(12,4) NOT NULL DEFAULT 0       COMMENT '当日估算成本（CNY，按汇率换算）',
  `exchange_rate`      DECIMAL(8,4)  DEFAULT NULL             COMMENT '计算时使用的汇率（USD/CNY）',
  `created_at`         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
  `updated_at`         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_agent_model_date` (`tenant_id`, `agent_id`, `model_id`, `stat_date`),
  KEY `idx_tenant_date` (`tenant_id`, `stat_date` DESC),
  KEY `idx_agent_date` (`agent_id`, `stat_date` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成本日统计汇总表（定时聚合，非实时）';
```

**关键字段说明**：
- `agent_id = NULL`：用于存储租户整体（不区分 Agent）的汇总数据，`UNIQUE KEY` 中 `agent_id` 参与时需注意 NULL 值处理（MySQL 中 NULL 不等于 NULL，需改用特殊标识值如 `'ALL'`）。
- 数据来源：定时任务（每小时/每天）从 `trace_turn` 聚合写入，非实时计算。

---

### 11.6 alert_rule（告警规则表）

**用途**：存储租户配置的告警规则，支持多种指标阈值触发告警并通知。

```sql
CREATE TABLE `alert_rule` (
  `id`                VARCHAR(36)   NOT NULL                 COMMENT 'UUID主键',
  `tenant_id`         VARCHAR(36)   NOT NULL                 COMMENT '所属租户ID',
  `agent_id`          VARCHAR(36)   DEFAULT NULL             COMMENT '关联 Agent ID，null表示租户级别告警',
  `name`              VARCHAR(100)  NOT NULL                 COMMENT '告警规则名称',
  `description`       VARCHAR(500)  DEFAULT NULL             COMMENT '规则说明',
  `metric`            VARCHAR(50)   NOT NULL                 COMMENT '监控指标：turn_error_rate=轮次错误率, p95_latency=P95延迟, eval_score=评估均分, hallucination=幻觉风险, monthly_cost=月成本, dislike_rate=差评率',
  `condition`         VARCHAR(10)   NOT NULL                 COMMENT '比较条件：gt=大于, lt=小于, gte=大于等于, lte=小于等于',
  `threshold`         DECIMAL(12,4) NOT NULL                 COMMENT '告警阈值',
  `window_minutes`    INT           NOT NULL DEFAULT 60      COMMENT '统计窗口（分钟），在此时间窗口内计算指标值',
  `cooldown_minutes`  INT           NOT NULL DEFAULT 30      COMMENT '告警冷却时间（分钟），同一规则触发后N分钟内不重复告警',
  `notify_channels`   JSON          NOT NULL                 COMMENT '通知渠道列表，如 ["in_app","email","webhook"]',
  `notify_webhook_url` VARCHAR(1000) DEFAULT NULL            COMMENT 'Webhook 通知地址（notify_channels 含 webhook 时填入）',
  `status`            VARCHAR(20)   NOT NULL DEFAULT 'active' COMMENT '规则状态：active=启用, disabled=禁用',
  `last_triggered_at` DATETIME(3)   DEFAULT NULL             COMMENT '最近触发时间',
  `trigger_count`     INT           NOT NULL DEFAULT 0       COMMENT '累计触发次数',
  `created_by`        VARCHAR(36)   DEFAULT NULL             COMMENT '创建人用户ID',
  `is_deleted`        TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '软删除标记',
  `deleted_at`        DATETIME(3)   DEFAULT NULL             COMMENT '软删除时间',
  `created_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`, `is_deleted`),
  KEY `idx_agent_status` (`agent_id`, `status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则配置表';
```

---

## 12. 完整建表顺序

以下为推荐的建表顺序（按依赖关系排列，先建被引用表）：

```sql
-- 第一组：基础表（无外键依赖）
-- 1. tenant
-- 2. user
-- 3. llm_model

-- 第二组：依赖 llm_model
-- 4. llm_secret
-- 5. llm_availability_test
-- 6. llm_model_key_pool

-- 第三组：独立业务主表
-- 7. knowledge_base
-- 8. skill

-- 第四组：依赖 knowledge_base
-- 9. kb_document

-- 第五组：依赖 kb_document
-- 10. kb_chunk

-- 第六组：依赖 skill
-- 11. skill_version
-- 12. skill_invocation

-- 第七组：智能体主表
-- 13. agent

-- 第八组：依赖 agent
-- 14. agent_version
-- 15. agent_tool_binding
-- 16. agent_kb_binding（依赖 agent + knowledge_base）
-- 17. agent_skill_binding（依赖 agent + skill）
-- 18. api_key

-- 第九组：工作流
-- 19. workflow
-- 20. workflow_version
-- 21. workflow_run
-- 22. workflow_node_execution
-- 23. human_task

-- 第十组：测试与发布
-- 24. test_suite
-- 25. test_case
-- 26. test_run
-- 27. ab_test

-- 第十一组：可观测性
-- 28. trace_session
-- 29. trace_turn
-- 30. evaluation_result
-- 31. security_event
-- 32. cost_daily_stats
-- 33. alert_rule
```

---

文档版本：v1.0 | 最后更新：2026-03-18
