# 技能库（Skill Library）功能设计

## 文档信息

| 项 | 内容 |
|----|------|
| 文档编号 | 05 |
| 所属层次 | 功能层 |
| 关联文档 | 02-llm-management、04-agent-studio、06-workflow-multiagent |
| 版本 | v1.0 |
| 状态 | 草稿 |

---

## 1. 为什么需要 Skill 层

### 1.1 没有 Skill 层时的痛点

当平台只有 Tool 和 Agent 两个层次时，开发者会遇到以下问题：

**痛点一：提示词逻辑散落在各个 Agent**

同一个「情感分类」逻辑在客服 Agent、评论分析 Agent、反馈处理 Agent 中各写一遍，互相不一致，升级时需要逐个修改。

**痛点二：确定性任务被迫包装成 Agent**

「把用户输入的自然语言转成 SQL」是一个输入/输出确定的任务，不需要规划循环，但如果只有 Agent 和 Tool 两种选择，开发者会创建一个「SQL 生成 Agent」——这带来了不必要的自主决策开销和难以预测的行为。

**痛点三：LLM 任务无法在 Workflow 中作为节点复用**

Workflow 节点中有「工具调用节点」（执行代码）但没有「LLM 任务节点」。需要调用 LLM 完成固定任务时，只能在工作流中嵌入整个 Agent，成本过高。

**痛点四：质量无法统一评估**

散落在各处的提示词无法统一打分、统一对比，无法做横向效果比较。

### 1.2 Skill 层的定位

```
┌─────────────────────────────────────────────────────────────────────┐
│                        调用方层次                                     │
│  Agent（自主规划）   Workflow（确定性流程）   API（外部直接调用）         │
└──────────────────────────────┬──────────────────────────────────────┘
                                │ 调用
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        ★ Skill 层（本文档）                            │
│  LLM 驱动 + 确定性接口 + 跨 Agent 复用 + 独立版本管理                  │
└─────────────────────────────────────────────────────────────────────┘
                                │ 使用
                    ┌───────────┴───────────┐
                    ▼                       ▼
         ┌──────────────────┐    ┌──────────────────────┐
         │   LLM Gateway    │    │   Tool（纯代码执行）    │
         │  (模型调用层)     │    │  搜索/计算/HTTP 等     │
         └──────────────────┘    └──────────────────────┘
```

**Skill 的核心定义：**

```
Skill = 提示词模板 + 模型配置 + 输入 Schema + 输出 Schema + （可选）工具调用
```

三个关键特征：
1. **LLM 驱动**：依赖语言模型完成认知任务（区别于 Tool）
2. **确定性接口**：给定相同输入，行为可预期（区别于 Agent 的自主规划循环）
3. **跨范围复用**：可被多个 Agent、多个 Workflow 引用（区别于内嵌在单个 Agent 的提示词）

---

## 2. Skill vs Tool vs Agent vs Workflow 边界对比

| 维度 | Tool | **Skill** | Agent | Workflow |
|------|------|-----------|-------|----------|
| 核心驱动 | 代码执行 | LLM + 提示词 | LLM + 自主规划 | 预定义流程图 |
| 是否有 LLM 调用 | 否 | 是（确定次数） | 是（循环不确定） | 可选（节点级） |
| 输入输出 | 强类型 Schema | 强类型 Schema | 自然语言 | 变量传递 |
| 执行次数 | 单次确定 | 确定（1~N 步） | 循环直到目标 | 按图执行 |
| 能否自主决策 | 否 | 否 | 是 | 否（条件分支除外） |
| 复用粒度 | 功能函数 | 认知任务 | 完整任务目标 | 完整流程 |
| 典型用途 | 网络搜索、代码执行 | 摘要、翻译、提取 | 研究助手、规划任务 | 审批流、数据处理管道 |
| 版本管理 | 代码版本 | 独立版本 | 独立版本 | 独立版本 |

**决策规则（何时用 Skill）：**

- 任务需要 LLM **且** 输入/输出格式固定 → **Skill**
- 任务需要 LLM **且** 需要自主决定下一步 → **Agent**
- 任务不需要 LLM，只执行代码 → **Tool**
- 多个 Skill/Tool/Agent 需要按流程串联 → **Workflow**

---

## 3. Skill 类型定义

### 3.1 Atomic Skill（原子技能）

**定义：** 单次 LLM 调用，完成一个明确、独立的认知任务。

**特征：** 一个提示词 → 一次模型调用 → 一个结果

**典型场景：**

| 技能名称 | 输入 | 输出 |
|---------|------|------|
| 文本摘要 | `{ text: string, max_length?: int }` | `{ summary: string }` |
| 语言检测 | `{ text: string }` | `{ language: string, confidence: float }` |
| 情感分类 | `{ text: string }` | `{ sentiment: "positive"\|"negative"\|"neutral", score: float }` |
| 关键词提取 | `{ text: string, top_k?: int }` | `{ keywords: string[] }` |
| 文本翻译 | `{ text: string, target_language: string }` | `{ translated: string }` |
| 意图识别 | `{ utterance: string, intent_list: string[] }` | `{ intent: string, confidence: float }` |
| 标题生成 | `{ content: string, style?: string }` | `{ title: string }` |
| 难度评估 | `{ text: string }` | `{ level: "beginner"\|"intermediate"\|"advanced" }` |

**配置项：**

```yaml
type: atomic
name: 文本摘要
input_schema:
  text:
    type: string
    required: true
    max_length: 10000
  max_length:
    type: integer
    default: 200
output_schema:
  summary:
    type: string
prompt_template: |
  请将以下文本压缩成不超过 {{ max_length }} 字的摘要，
  保留核心要点，语言简洁清晰。

  文本内容：
  {{ text }}
model_config:
  model_id: "<引用模型管理中的模型 ID>"
  temperature: 0.3
  max_tokens: 512
```

---

### 3.2 Composite Skill（复合技能）

**定义：** 多步确定性流水线，每步可以是 LLM 调用或 Tool 调用，步骤顺序固定（无规划循环）。

**特征：** Step1 → Step2 → … → StepN，数据在步骤间流转，但不存在「决定下一步是什么」的自主行为。

**典型场景：**

**示例一：NL to SQL**
```
Step 1: 意图解析（LLM）— 理解用户想查什么
Step 2: Schema 注入（Tool）— 查询数据库表结构
Step 3: SQL 生成（LLM）— 生成 SQL 语句
Step 4: 语法校验（Tool）— SQL 语法检查
Step 5: 安全审查（LLM）— 检查 SQL 是否涉及写操作或危险操作
```

**示例二：文档问答**
```
Step 1: 查询改写（LLM）— 将用户问题改写为检索友好的短语
Step 2: 知识库检索（Tool）— 执行向量检索
Step 3: 答案生成（LLM）— 基于检索结果生成回答
```

**示例三：代码审查**
```
Step 1: 代码解析（Tool）— 提取 AST、函数签名
Step 2: 安全检查（LLM）— 分析安全隐患
Step 3: 规范检查（LLM）— 对照编码规范
Step 4: 汇总报告（LLM）— 整合问题生成报告
```

**配置项：**

```yaml
type: composite
name: NL to SQL
steps:
  - name: intent_parse
    type: llm
    prompt_template: "分析用户意图：{{ user_input }}"
    output_key: intent
  - name: schema_fetch
    type: tool
    tool_id: "<数据库 Schema 查询工具 ID>"
    input:
      tables: "{{ intent.related_tables }}"
    output_key: schema
  - name: sql_generate
    type: llm
    prompt_template: "根据意图 {{ intent }} 和表结构 {{ schema }} 生成 SQL"
    output_key: sql
  - name: sql_validate
    type: tool
    tool_id: "<SQL 语法校验工具 ID>"
    input:
      sql: "{{ sql }}"
    output_key: validation_result
final_output_key: sql
```

**错误处理策略**

Composite Skill 的步骤按顺序执行，错误处理规则如下：

| 错误类型 | 默认行为 | 可配置项 |
|---------|---------|---------|
| 步骤超时 | 立即终止并返回错误 | `step_timeout_ms`（默认 30000） |
| 步骤异常（非超时） | 按指数退避重试（最多 2 次），仍失败则终止 | `retry_count`（0-3） |
| 所有步骤失败 | 整体返回 `SKILL_EXEC_FAILED`，附带失败步骤索引 | — |
| 部分步骤失败（并行模式） | 返回成功步骤的结果，失败步骤结果为 null | `fail_fast`（true 时任一失败则终止） |

错误响应格式：
```json
{
  "code": "SKILL_EXEC_FAILED",
  "failed_step": 2,
  "step_error": "LLM call timeout after 30000ms",
  "partial_output": { }
}
```

---

### 3.3 Extraction Skill（抽取技能）

**定义：** 从非结构化文本中抽取结构化信息，强制 JSON Schema 输出。

**特征：** 提示词中明确定义抽取字段和格式，模型输出必须符合指定 Schema（使用 Structured Output / Function Calling 保证）。

**典型场景：**

| 技能名称 | 输入 | 输出 Schema |
|---------|------|------------|
| 联系人抽取 | 自然语言文本 | `{name, phone, email, company}` |
| 简历解析 | 简历文本/PDF 内容 | `{name, skills[], experience[], education[]}` |
| 合同要素提取 | 合同文本 | `{parties[], amount, date, key_terms[]}` |
| 发票信息提取 | 发票内容 | `{vendor, amount, date, items[]}` |
| 事件抽取 | 新闻/报告文本 | `{event_type, time, location, participants[]}` |
| 需求解析 | 用户需求描述 | `{feature, priority, acceptance_criteria[]}` |

**配置项：**

```yaml
type: extraction
name: 联系人抽取
input_schema:
  text:
    type: string
    required: true
output_schema:
  type: object
  properties:
    contacts:
      type: array
      items:
        type: object
        properties:
          name: { type: string }
          phone: { type: string }
          email: { type: string }
          company: { type: string }
        required: [name]
prompt_template: |
  从以下文本中提取所有联系人信息，严格按照 JSON 格式输出。
  如果某个字段缺失，用 null 填充。

  文本：{{ text }}
model_config:
  model_id: "<模型 ID>"
  temperature: 0.1       # 抽取任务用低温度保证稳定性
  structured_output: true  # 启用 Structured Output
```

---

### 3.4 Generation Skill（生成技能）

**定义：** 基于模板和上下文生成特定格式的文本内容，强调创作性输出。

**特征：** 模板化输入 → 格式化/风格化输出，适合邮件起草、报告生成、内容创作等需要特定格式或风格的场景。

**典型场景：**

| 技能名称 | 输入 | 输出 |
|---------|------|------|
| 邮件起草 | `{context, tone, recipient_role}` | 完整邮件正文 |
| 周报生成 | `{work_items[], achievements[], blockers[]}` | 周报 Markdown 文本 |
| 商品描述生成 | `{product_name, features[], target_audience}` | 营销文案 |
| API 文档生成 | `{endpoint, params[], response_schema}` | Markdown 文档 |
| 错误消息优化 | `{error_code, technical_msg, user_context}` | 用户友好提示语 |
| 测试用例生成 | `{function_name, input_types[], expected_behavior}` | 测试代码 |

**配置项：**

```yaml
type: generation
name: 邮件起草
input_schema:
  context:
    type: string
    description: 邮件背景信息
  tone:
    type: string
    enum: [formal, friendly, urgent]
    default: formal
  recipient_role:
    type: string
    description: 收件人角色，如"客户"、"同事"、"上级"
output_schema:
  subject:
    type: string
  body:
    type: string
prompt_template: |
  请为以下情境起草一封{{ tone == 'formal' ? '正式' : tone == 'friendly' ? '友好' : '紧急' }}邮件。

  背景：{{ context }}
  收件人：{{ recipient_role }}

  要求：
  - 主题行简洁明确
  - 正文结构清晰
  - 语气{{ tone == 'formal' ? '正式专业' : tone == 'friendly' ? '温和友好' : '简洁直接' }}

  请输出 JSON 格式：{ "subject": "...", "body": "..." }
model_config:
  model_id: "<模型 ID>"
  temperature: 0.7   # 生成任务可用较高温度
  max_tokens: 1024
```

---

## 4. Skill 完整配置项说明

### 4.1 基础信息

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `skill_id` | string | 系统生成 | UUID，全局唯一标识 |
| `name` | string | 是 | Skill 名称，租户内唯一 |
| `display_name` | string | 是 | 展示名称，支持中文 |
| `description` | string | 是 | 功能描述，100 字以内 |
| `type` | enum | 是 | `atomic` / `composite` / `extraction` / `generation` |
| `category` | string | 否 | 分类标签（如「文本处理」「数据抽取」「内容生成」） |
| `tags` | string[] | 否 | 自定义标签 |
| `icon` | string | 否 | 图标标识 |
| `tenant_id` | string | 是 | 所属租户（平台级 Skill 为 null） |
| `created_by` | string | 是 | 创建人 user_id |
| `created_at` | datetime | 系统生成 | 创建时间 |

### 4.2 版本信息

| 字段 | 类型 | 说明 |
|------|------|------|
| `version` | string | 语义化版本号，如 `1.2.0` |
| `version_id` | string | 版本 UUID |
| `is_latest` | boolean | 是否为最新版本 |
| `changelog` | string | 版本变更说明 |
| `published_at` | datetime | 发布时间 |
| `status` | enum | `draft` / `published` / `deprecated` |

### 4.3 模型配置

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `model_id` | string | 是 | 引用 LLM 管理中的模型 ID |
| `temperature` | float | 否 | 默认 0.7，范围 0~2 |
| `top_p` | float | 否 | 默认 1.0 |
| `max_tokens` | int | 否 | 最大输出 Token 数 |
| `structured_output` | boolean | 否 | 是否启用结构化输出（Extraction Skill 建议开启） |
| `fallback_model_id` | string | 否 | 备用模型，主模型不可用时自动切换 |

### 4.4 提示词配置

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `system_prompt` | string | 否 | 系统角色提示词 |
| `prompt_template` | string | 是 | 主提示词模板，支持 Jinja2 变量语法 |
| `few_shot_examples` | object[] | 否 | 少样本示例，格式：`[{input, output}]` |
| `variables` | object | 是 | 变量定义（与 input_schema 对应） |

### 4.5 输入/输出 Schema

```yaml
input_schema:
  <field_name>:
    type: string | integer | float | boolean | array | object
    required: true | false
    default: <default_value>
    description: <字段说明>
    enum: [<可选值列表>]        # 枚举类型时使用
    min_length: <int>           # string 类型时
    max_length: <int>           # string 类型时
    minimum: <number>           # number 类型时
    maximum: <number>           # number 类型时
    items:                      # array 类型时
      type: <item_type>

output_schema:
  type: object | string         # 顶层类型
  properties:                   # type 为 object 时
    <field>:
      type: <type>
  description: <输出格式说明>
```

### 4.6 可见性与访问控制

| 字段 | 类型 | 说明 |
|------|------|------|
| `visibility` | enum | `platform_public` / `tenant_shared` / `agent_private` |
| `allowed_tenant_ids` | string[] | visibility 为 `platform_public` 时限定可访问租户 |
| `allowed_agent_ids` | string[] | visibility 为 `agent_private` 时限定可使用的 Agent |

**可见性规则：**

```
platform_public
  └── 平台管理员创建
  └── 所有租户均可引用（或限定白名单租户）
  └── 租户只读，不可修改

tenant_shared
  └── 租户管理员创建
  └── 该租户内所有 Agent/Workflow 均可引用
  └── 租户内用户只读，创建者可修改

agent_private
  └── 任意用户创建
  └── 仅指定 Agent 可以引用
  └── 创建者可修改
```

### 4.7 调用配置

| 字段 | 类型 | 说明 |
|------|------|------|
| `timeout_ms` | int | 执行超时时间，默认 30000ms |
| `max_retries` | int | 失败重试次数，默认 2 |
| `retry_delay_ms` | int | 重试间隔，默认 1000ms |
| `async_support` | boolean | 是否支持异步调用 |
| `stream_support` | boolean | 是否支持流式输出（Generation Skill 适用） |

---

## 5. 版本管理

### 5.1 版本生命周期

```
创建/修改
    │
    ▼
[draft]  ──编辑修改──▶ [draft]
    │
    │ 发布
    ▼
[published] ──新版本发布──▶ 旧版本自动变为 [deprecated]
    │
    │ 主动废弃 或 新版本替换
    ▼
[deprecated]
```

### 5.2 版本号规范

采用语义化版本（Semantic Versioning）：`MAJOR.MINOR.PATCH`

| 变更类型 | 版本升级规则 | 示例 |
|---------|------------|------|
| 破坏性变更（Input/Output Schema 不兼容） | MAJOR +1 | 1.0.0 → 2.0.0 |
| 新增字段（向后兼容） | MINOR +1 | 1.0.0 → 1.1.0 |
| 提示词优化、Bug 修复 | PATCH +1 | 1.0.0 → 1.0.1 |

### 5.3 版本快照

每个已发布版本保存完整快照（JSON），包含：
- 提示词模板
- 输入/输出 Schema
- 模型配置（模型 ID + 推理参数）
- Few-shot 示例
- 依赖的 Tool 列表

快照一旦创建不可修改，确保版本可回溯。

### 5.4 版本引用机制

调用方（Agent / Workflow）可以选择：

| 引用方式 | 说明 | 适用场景 |
|---------|------|---------|
| `latest` | 始终使用最新发布版本 | 快速迭代期 |
| `>=1.0.0` | 语义化版本范围约束 | 需要兼容性保障 |
| `1.2.0`（固定版本） | 锁定到具体版本 | 生产稳定性要求高 |

**版本兼容性规则（语义化版本）**

| 版本变更类型 | 判定规则 | 对引用方的影响 |
|------------|---------|-------------|
| PATCH（1.0.x） | 仅 Bug 修复，不改变接口 | 自动静默升级，无需确认 |
| MINOR（1.x.0） | 新增可选输入字段或输出字段 | 自动升级；旧调用方不受影响（新字段有默认值） |
| MAJOR（x.0.0） | 删除/重命名字段、改变执行逻辑 | **破坏性升级**：自动暂停所有引用方（Workflow 节点、Agent 绑定），需引用方管理员手动确认升级后恢复运行 |

MAJOR 升级发布时，系统扫描所有引用该 Skill 的 Workflow 和 Agent，在其状态栏显示「有 Skill 需要确认升级」警告，且该引用方不可发布，直至完成升级确认。

**版本升级影响分析：**

发布新版本时，系统自动扫描：
1. 有哪些 Agent/Workflow 引用了该 Skill
2. 引用方式是 `latest` 还是固定版本
3. 如果是 MAJOR 升级（破坏性变更），提示引用方检查兼容性

---

## 6. Skill 调用方式

### 6.1 方式一：Agent 将 Skill 注册为能力项

在 Agent 配置中绑定若干 Skill，Agent 在规划阶段决定何时调用哪个 Skill。

```
用户: "帮我分析这段评论的情感，然后翻译成英文"

Agent 规划：
  Step 1: 调用 [情感分类 Skill] → { sentiment: "negative", score: 0.87 }
  Step 2: 调用 [文本翻译 Skill] → { translated: "This product is terrible" }
  Step 3: 整合结果返回用户
```

**配置方式（Agent Studio 中）：**

```yaml
skills:
  - skill_id: "<情感分类 Skill ID>"
    version: "latest"
    alias: "analyze_sentiment"   # Agent 内部引用别名
  - skill_id: "<文本翻译 Skill ID>"
    version: "1.0.x"
    alias: "translate_text"
```

### 6.2 方式二：Workflow 中作为 Skill 节点

在工作流画布中，Skill 作为独立节点类型存在。

```
[触发节点] → [情感分析 Skill 节点] → [条件分支]
                                          ├── negative → [升级工单 Tool]
                                          └── positive → [感谢回复 Generation Skill]
```

**节点配置：**

- 选择 Skill（从租户 Skill 库搜索）
- 映射输入变量（将前置节点输出映射到 Skill 输入字段）
- 映射输出变量（将 Skill 输出映射到 Workflow 变量）

### 6.3 方式三：直接 API 调用

Skill 发布后，自动生成独立的 REST API 端点，可直接调用而无需经过 Agent。

**请求格式：**

```http
POST /api/v1/skills/{skill_id}/invoke
Authorization: Bearer <token>
Content-Type: application/json

{
  "version": "latest",          // 可选，默认 latest
  "inputs": {
    "text": "这个产品太棒了！",
    "max_length": 100
  },
  "stream": false               // Generation Skill 支持 stream: true
}
```

**响应格式：**

```json
{
  "code": 0,
  "data": {
    "skill_id": "...",
    "version": "1.2.0",
    "outputs": {
      "sentiment": "positive",
      "score": 0.94
    },
    "usage": {
      "input_tokens": 23,
      "output_tokens": 15,
      "latency_ms": 312
    },
    "trace_id": "..."
  }
}
```

**流式响应（stream: true）：**

```
data: {"chunk": "这是", "done": false}
data: {"chunk": "一封", "done": false}
data: {"chunk": "正式的商务邮件...", "done": false}
data: {"outputs": {"subject": "合作邀请", "body": "..."}, "done": true}
```

### 6.4 方式四：Skill 内嵌调用（Composite Skill 中调用子 Skill）

Composite Skill 的某个步骤可以调用另一个已发布的 Skill，实现能力复用。

```yaml
steps:
  - name: sentiment_check
    type: skill
    skill_id: "<情感分类 Skill ID>"
    input:
      text: "{{ user_input }}"
    output_key: sentiment_result
  - name: generate_response
    type: skill
    skill_id: "<邮件起草 Skill ID>"
    condition: "{{ sentiment_result.sentiment == 'negative' }}"
    input:
      context: "{{ user_input }}"
      tone: "formal"
    output_key: response
```

---

## 7. Skill 发现与管理界面

### 7.1 Skill 库列表页

**布局：** 左侧分类导航 + 右侧卡片网格/列表切换

**分类导航：**
```
全部
├── 文本处理
│   ├── 摘要与压缩
│   ├── 翻译
│   └── 风格转换
├── 信息抽取
│   ├── 实体识别
│   ├── 结构化抽取
│   └── 关键词提取
├── 内容生成
│   ├── 文档生成
│   ├── 代码生成
│   └── 创意写作
├── 分析判断
│   ├── 情感分析
│   ├── 意图识别
│   └── 内容审核
└── 复合流程
    ├── NL to SQL
    └── 文档问答
```

**筛选条件：**
- Skill 类型（Atomic / Composite / Extraction / Generation）
- 可见性（平台公开 / 租户共享 / 私有）
- 状态（草稿 / 已发布 / 已废弃）
- 创建人
- 关键词搜索（名称 + 描述全文搜索）

**卡片信息展示：**
- Skill 名称 + 类型标签
- 描述（截断，hover 展示完整）
- 版本号
- 调用量（近 7 天）
- 平均延迟
- 质量评分（LLM-as-Judge 综合分）
- 操作：调试 / 查看详情 / 引用到 Agent

### 7.2 Skill 详情页

**标签页结构：**

| 标签 | 内容 |
|------|------|
| 基本信息 | 名称/描述/类型/分类/标签/创建人/时间 |
| 提示词 | 系统提示词 + 主提示词（语法高亮） |
| Schema | 输入/输出 JSON Schema 可视化 |
| 版本历史 | 版本列表 + 变更日志 |
| 使用情况 | 引用的 Agent/Workflow 列表 |
| 评估数据 | 质量评分趋势 + 近期测试结果 |

### 7.3 Skill 创建/编辑 Drawer

**步骤流程（分步引导）：**

**Step 1 - 基础信息**
- Skill 名称（必填）
- 类型选择（Atomic / Composite / Extraction / Generation）
- 功能描述（必填，100 字以内）
- 分类选择
- 可见性设置

**Step 2 - 输入输出 Schema**
- 可视化 Schema 编辑器（表格填写 + JSON 编辑两种模式）
- 输入字段列表：字段名、类型、是否必填、默认值、描述
- 输出字段列表

**Step 3 - 提示词配置**
- 系统提示词输入框
- 主提示词模板（代码编辑器，支持 `{{ variable }}` 变量高亮）
- 变量插入辅助（点击变量名自动插入）
- Few-shot 示例添加

**Step 4 - 模型与推理参数**
- 模型选择（从已启用模型列表选择）
- 推理参数（temperature / top_p / max_tokens）
- 备用模型（可选）

**Step 5 - 调试与验证**
- 实时调试面板（输入测试数据 → 看输出结果）
- 多组测试用例批量跑
- 输出格式校验（Extraction Skill 校验 JSON Schema 符合性）

---

## 8. Skill 调试面板

调试面板是 Skill 配置页内嵌的实时验证工具（类似 Postman 体验）：

```
┌──────────────────────────────────────────────────────────────┐
│  调试 Skill：情感分类                                    v1.2.0 │
├─────────────────────────┬────────────────────────────────────┤
│  输入                    │  输出                               │
│                          │                                    │
│  text *                  │  {                                 │
│  ┌────────────────────┐  │    "sentiment": "positive",        │
│  │ 这个产品真的太棒了  │  │    "score": 0.94                   │
│  │ 质量非常好！        │  │  }                                 │
│  └────────────────────┘  │                                    │
│                          │  ──────────────────────────        │
│                          │  ⏱ 耗时：287ms                     │
│                          │  🔢 Token：输入 23 / 输出 12        │
│  [运行] [清空]           │  📊 延迟分位：P50 290ms             │
└──────────────────────────┴────────────────────────────────────┘
│  调试历史                                                       │
│  #1  "这产品太差了" → negative(0.91)  312ms                    │
│  #2  "还行吧"       → neutral(0.63)   298ms                    │
│  #3  "真的很棒！"   → positive(0.94)  287ms                    │
└──────────────────────────────────────────────────────────────┘
```

**功能：**
- 单次运行：手动填写输入，看实时输出
- 批量测试：上传 CSV 测试集，批量执行并统计
- Diff 对比：并排对比两个版本的输出差异
- 自动保存调试历史（最近 50 条）

---

## 9. Skill 评估体系

### 9.1 自动质量评估

每次 Skill 调用后，系统异步执行 LLM-as-Judge 评估：

**评估维度：**

| 维度 | 说明 | 权重 |
|------|------|------|
| 准确性 | 输出内容是否正确 | 40% |
| 完整性 | 是否覆盖输入中的所有关键信息 | 25% |
| 格式合规 | 是否符合 Output Schema | 20% |
| 简洁性 | 输出是否简洁无冗余 | 15% |

**评估频率：**
- 默认对 10% 的调用执行自动评估（采样）
- 调试模式下对所有调用执行评估
- 用户可手动触发对特定历史调用的评估

### 9.2 质量指标面板

在 Skill 详情页的「评估数据」标签页中展示：

```
质量分（近 7 天均值）: 4.2 / 5.0  ▲ +0.3

调用量   ████████████  1,234 次
成功率   ██████████░░  91.2%
P50 延迟 ████████░░░░  312ms
P95 延迟 ██████░░░░░░  850ms

问题分布：
  格式不合规  ───────  34 次 (3.3%)
  超时        ──────   22 次 (2.1%)
  模型报错    ────     15 次 (1.4%)
```

### 9.3 版本质量对比

可选择两个版本横向对比质量指标，为版本升级提供决策依据：

| 指标 | v1.1.0 | v1.2.0 | 变化 |
|------|--------|--------|------|
| 质量均分 | 3.9 | 4.2 | ↑ +0.3 |
| 成功率 | 89.1% | 91.2% | ↑ +2.1% |
| P50 延迟 | 380ms | 312ms | ↓ -68ms |
| 格式合规率 | 94.1% | 96.7% | ↑ +2.6% |

---

## 10. Skill 从提示词晋升的路径

当一个 Agent 内的提示词逻辑满足以下条件时，系统提示用户将其晋升为独立 Skill：

**晋升条件（满足其中之一即可触发提示）：**
1. 同一段提示词逻辑在 2 个以上 Agent 中重复出现
2. 某段提示词被单独调试次数超过 10 次
3. 用户手动发起晋升

**晋升流程：**

```
1. 在 Agent 配置的提示词中选中一段 → 点击「晋升为 Skill」
2. 系统自动提取：
   - 提示词模板
   - 引用的变量（作为 input_schema 初始版本）
3. 进入 Skill 创建向导，补充：
   - 技能名称和描述
   - 完善 output_schema
   - 选择类型（Atomic / Extraction / Generation）
4. 发布 Skill
5. 原 Agent 中的对应提示词自动替换为 Skill 引用
```

---

## 11. Skill 在多 Agent 场景中的作用

### 11.1 Orchestrator 模式下的任务分配

在 Multi-Agent Orchestration 中，Orchestrator 负责将任务分配给合适的 Agent 或 Skill。

**使用 Skill 的优势：**

```
传统方式：
  Orchestrator → "请调用翻译 Agent 执行翻译"
  问题：Agent 名称耦合，Agent 下线或改名时需要修改 Orchestrator

改进方式：
  Orchestrator → "调用具有 skill:translation 能力的执行单元"
  优势：能力声明式分配，解耦调用方和执行方
```

**Skill 能力标签注册：**

每个 Skill 可以注册一组标准化能力标签（`skill_tags`），Orchestrator 根据标签而非名称进行任务路由：

```yaml
skill_tags:
  - translation        # 翻译能力
  - zh-to-en           # 中译英
  - formal-language    # 正式语体
```

### 11.2 Sub-Agent 能力声明

Sub-Agent 可以将自身具备的 Skill 能力声明给 Orchestrator：

```yaml
# Sub-Agent 能力声明
agent_capabilities:
  skills:
    - skill_id: "<翻译 Skill ID>"
      tags: [translation, zh-to-en]
    - skill_id: "<情感分类 Skill ID>"
      tags: [sentiment-analysis]
  tools:
    - search_web
    - execute_python
```

Orchestrator 在接收到任务后，根据需要的能力标签，在已注册的 Sub-Agent 和直接 Skill 调用之间选择最优路由。

---

## 12. 平台 Skill 市场（P1）

### 12.1 设计思路

平台方可以创建并向所有租户开放「公共 Skill 库」，提供开箱即用的高质量技能。

**分层设计：**

```
平台公共 Skill（所有租户可用）
  ├── 官方维护（平台管理员创建、更新）
  └── 社区贡献（租户提交 → 平台审核 → 发布）

租户共享 Skill（当前租户内可用）
  └── 租户管理员创建并在租户内共享

Agent 私有 Skill（仅指定 Agent 可用）
  └── 开发者创建
```

### 12.2 公共 Skill 审核流程

```
租户提交 Skill 到公共库
    │
    ▼
自动审查（提示词安全检测、Schema 合规检查）
    │
    ▼
人工审核（平台管理员 Review）
    │
    ▼
上架公共库（分配标准化能力标签）
```

### 12.3 Skill 评分与推荐

- 基于调用量、质量分、社区评分（1~5 星）综合排名
- 相似 Skill 去重推荐（向量相似度检测，避免功能重叠）
- 按使用场景推荐（在创建 Agent 时，根据 Agent 描述推荐相关 Skill）

---

## 13. 数据模型（概览）

以下为关键表结构概览，详细 DDL 见 `09-database-design.md`：

### skill 表（主表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(36) | UUID 主键 |
| tenant_id | varchar(36) | 租户 ID，平台级 Skill 为 null |
| name | varchar(100) | 技能名称 |
| display_name | varchar(100) | 展示名称 |
| description | varchar(500) | 功能描述 |
| type | varchar(20) | atomic/composite/extraction/generation |
| category | varchar(50) | 分类 |
| tags | json | 标签列表 |
| skill_tags | json | 能力标签（多 Agent 场景路由用） |
| visibility | varchar(30) | platform_public/tenant_shared/agent_private |
| current_version | varchar(20) | 当前发布版本号 |
| status | varchar(20) | active/archived |
| created_by | varchar(36) | 创建人 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |

### skill_version 表（版本快照）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(36) | UUID 主键 |
| skill_id | varchar(36) | 关联 skill.id |
| version | varchar(20) | 版本号 |
| status | varchar(20) | draft/published/deprecated |
| config_snapshot | json | 完整配置快照 |
| changelog | text | 版本说明 |
| published_at | datetime | 发布时间 |
| published_by | varchar(36) | 发布人 |

### skill_invocation 表（调用记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(36) | UUID 主键 |
| skill_id | varchar(36) | Skill ID |
| version | varchar(20) | 调用版本 |
| caller_type | varchar(20) | agent/workflow/api |
| caller_id | varchar(36) | 调用方 ID |
| inputs | json | 输入参数（脱敏后） |
| outputs | json | 输出内容（可选存储） |
| status | varchar(20) | success/failed/timeout |
| latency_ms | int | 调用耗时 |
| input_tokens | int | 输入 Token 数 |
| output_tokens | int | 输出 Token 数 |
| error_code | varchar(50) | 错误码（失败时） |
| error_message | text | 错误信息 |
| trace_id | varchar(36) | 关联全链路 Trace |
| created_at | datetime | 调用时间 |

### agent_skill_binding 表（引用关系）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(36) | UUID 主键 |
| agent_id | varchar(36) | Agent ID |
| skill_id | varchar(36) | Skill ID |
| version_pin | varchar(20) | 版本锁定（latest 或固定版本） |
| alias | varchar(100) | Agent 内引用别名 |

---

## 14. 接口概览

详细 API 规范见 `10-api-spec.md`，以下为主要端点列表：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/skills` | 获取 Skill 列表（支持分页、筛选） |
| POST | `/api/v1/skills` | 创建 Skill |
| GET | `/api/v1/skills/{id}` | 获取 Skill 详情 |
| PUT | `/api/v1/skills/{id}` | 更新 Skill（草稿态） |
| POST | `/api/v1/skills/{id}/publish` | 发布 Skill 版本 |
| POST | `/api/v1/skills/{id}/deprecate` | 废弃 Skill |
| GET | `/api/v1/skills/{id}/versions` | 获取版本历史 |
| POST | `/api/v1/skills/{id}/invoke` | 调用 Skill |
| GET | `/api/v1/skills/{id}/metrics` | 获取质量指标 |
| GET | `/api/v1/skills/{id}/usages` | 获取引用关系 |

---

## 15. 与其他模块的关系

```
┌─────────────────────────────────────────────────────┐
│                     Skill Library                    │
│                                                     │
│  引用 → LLM Gateway（模型调用）                       │
│  引用 → Tool Ecosystem（工具执行，Composite Skill）    │
│                                                     │
│  被引用 ← Agent Studio（Agent 绑定 Skill）             │
│  被引用 ← Workflow Engine（Skill 节点）               │
│  被引用 ← 外部 API（直接调用）                         │
│                                                     │
│  数据流向 → Observability（调用 Trace）               │
│  数据流向 → Evaluation（质量评分）                     │
└─────────────────────────────────────────────────────┘
```

---

*文档版本：v1.0 | 最后更新：2026-03-18*
