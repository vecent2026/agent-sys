# 工作流引擎与多智能体编排功能设计

## 文档信息

| 项 | 内容 |
|----|------|
| 文档编号 | 06 |
| 所属层次 | 功能层 |
| 关联文档 | 04-agent-studio、05-skill-library、08-observability-evaluation |
| 版本 | v1.0 |
| 状态 | 草稿 |

---

## 1. 决策矩阵：何时用 Workflow vs Agent vs Skill

在构建自动化任务时，正确选择执行单元至关重要：

| 判断维度 | Skill | Agent | Workflow |
|---------|-------|-------|---------|
| 执行步骤数 | 1~5 步 | 不确定 | 确定（有向图） |
| 是否需要自主决策 | 否 | 是 | 否（条件分支固定） |
| 是否需要循环迭代 | 否 | 是（ReAct 循环） | 可（迭代节点） |
| 输入/输出格式 | 强 Schema | 自然语言 | 强类型变量 |
| 执行时长 | 秒级 | 秒~分钟 | 秒~小时 |
| 是否需要人工介入 | 否 | 可选 | 是（Human-in-the-Loop 节点） |
| 多系统集成需求 | 低 | 中 | 高 |
| 可审计性要求 | 低 | 中 | 高（每步骤有明确输入/输出记录） |

**选择指引：**

```
任务描述
    │
    ├── 单次 LLM 认知任务，输入输出确定？ ──▶ Skill（Atomic/Extraction/Generation）
    │
    ├── 需要 LLM 自主规划 + 工具调用循环？ ──▶ Agent
    │
    ├── 多步骤确定性流程，可能涉及多系统？
    │   ├── 无需人工介入 ──▶ Workflow（自动化）
    │   └── 需要人工审批/输入 ──▶ Workflow（Human-in-the-Loop）
    │
    └── 超出单 Agent 能力，需要多专家协作？ ──▶ Multi-Agent Orchestration
```

---

## 2. 工作流引擎概述

### 2.1 设计理念

工作流引擎的核心是「确定性」：每一步执行什么、条件分支走哪条路，在设计阶段已通过可视化画布完全定义。与 Agent 的「自主规划」相对，Workflow 的执行过程是完全可预期、可审计的。

**核心特征：**
- **可视化编排**：拖拽节点、连线定义执行图
- **强类型变量**：节点间数据流通过显式变量传递
- **完整 Trace**：每个节点的输入/输出均被记录
- **版本化**：Workflow 配置有完整版本管理

### 2.2 工作流执行引擎

```
                    ┌─────────────────────────────┐
                    │        Workflow Engine        │
                    │                               │
触发器 ──触发事件──▶ │  解析 DAG                     │
                    │  ├── 调度节点执行              │
                    │  ├── 管理变量上下文            │
                    │  ├── 处理条件分支              │
                    │  ├── 管理并行任务              │
                    │  └── 处理错误与重试            │
                    │                               │
                    └───────────────────────────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              ▼                    ▼                    ▼
        LLM Gateway          Tool Executor         Human Task Queue
       （模型调用）           （工具执行）            （等待人工处理）
```

---

## 3. 工作流节点类型

### 3.1 触发节点（Trigger Node）

工作流的入口，定义何时启动工作流。

#### 3.1.1 手动触发

用户在界面上手动启动，支持传入参数。

```yaml
node_type: trigger
trigger_type: manual
input_schema:
  topic:
    type: string
    required: true
    description: 要处理的主题
```

#### 3.1.2 API 触发

通过 HTTP POST 请求触发，适合系统集成。

```yaml
node_type: trigger
trigger_type: api
endpoint: /api/v1/workflows/{workflow_id}/trigger
input_schema:
  # 与 HTTP Body 对应
  payload:
    type: object
auth:
  type: bearer_token    # 鉴权方式
```

#### 3.1.3 定时触发

按 Cron 表达式定时执行。

```yaml
node_type: trigger
trigger_type: schedule
cron: "0 9 * * 1-5"    # 工作日早 9 点
timezone: Asia/Shanghai
max_instances: 1         # 同时最多运行 1 个实例
```

#### 3.1.4 事件触发

监听内部或外部事件（Kafka 消息、Webhook 回调等）。

```yaml
node_type: trigger
trigger_type: event
event_source: kafka
topic: user.feedback.created
filter:
  sentiment: negative   # 仅触发 sentiment 为 negative 的事件
```

---

### 3.2 LLM 节点（LLM Node）

单次模型调用，不包含工具循环。

**配置项：**

| 字段 | 说明 |
|------|------|
| `model_id` | 引用模型管理中的模型 |
| `system_prompt` | 系统提示词（支持变量） |
| `user_prompt` | 用户提示词模板（支持变量插值） |
| `temperature` | 推理参数 |
| `max_tokens` | 最大输出 |
| `structured_output` | 是否强制 JSON Schema 输出 |
| `output_schema` | 输出结构定义（structured_output 为 true 时必填） |
| `stream` | 是否流式（只影响前端显示，不影响后续节点） |

**变量引用语法：**

```
系统提示词：你是一名专业的 {{ role }} 助手，擅长处理 {{ domain }} 领域的问题。

用户提示词：
请分析以下内容：
{{ trigger.output.user_input }}

参考上下文：
{{ knowledge_retrieval_node.output.context }}
```

---

### 3.3 Agent 节点（Agent Node）

将已发布的 Agent 作为工作流中的一个步骤执行，Agent 内部完成自主规划循环。

**配置项：**

| 字段 | 说明 |
|------|------|
| `agent_id` | 引用已发布的 Agent |
| `agent_version` | 固定版本或 latest |
| `input_mapping` | 将 Workflow 变量映射到 Agent 的输入 |
| `output_key` | Agent 输出保存到哪个 Workflow 变量 |
| `max_steps` | 覆盖 Agent 默认的最大循环步数 |
| `timeout_ms` | Agent 执行超时 |

**使用场景：**
- 工作流中某一步需要 Agent 的自主推理能力（如：收集信息 → **Agent 分析** → 生成报告）
- 将复杂任务的一部分委托给专门的 Agent（如多 Agent 协作中的子任务）

---

### 3.4 Skill 节点（Skill Node）

调用已发布的 Skill，确定性 LLM 任务。

**配置项：**

| 字段 | 说明 |
|------|------|
| `skill_id` | 引用 Skill 库中的 Skill |
| `version_pin` | 版本锁定（latest 或固定） |
| `input_mapping` | Workflow 变量 → Skill 输入字段映射 |
| `output_key` | Skill 输出保存到哪个 Workflow 变量 |

---

### 3.5 工具节点（Tool Node）

调用 Tool Ecosystem 中的工具（内置工具、MCP 工具、自定义 HTTP 工具）。

**内置工具类型：**

| 工具 | 功能 |
|------|------|
| `web_search` | 网络搜索（Tavily / Bing / Google） |
| `code_execute` | Python 代码沙箱执行 |
| `http_request` | 自定义 HTTP 请求（GET/POST/PUT/DELETE） |
| `file_read` | 读取上传的文件内容 |
| `database_query` | 执行数据库查询（只读） |
| `email_send` | 发送邮件（需配置 SMTP） |
| `calendar_event` | 创建日历事件 |

**MCP 工具节点：**

```yaml
node_type: tool
tool_type: mcp
mcp_server_id: "<MCP Server ID>"
tool_name: "create_issue"
input_mapping:
  title: "{{ llm_node.output.issue_title }}"
  description: "{{ llm_node.output.issue_body }}"
output_key: github_issue
```

---

### 3.6 知识库节点（Knowledge Retrieval Node）

从知识库检索相关内容并注入后续节点。

**配置项：**

| 字段 | 说明 |
|------|------|
| `knowledge_base_id` | 引用知识库 |
| `query_template` | 检索 Query 模板（支持变量） |
| `top_k` | 返回最相关的 K 个片段，默认 5 |
| `retrieval_strategy` | vector / keyword / hybrid |
| `score_threshold` | 最低相关度分数（低于此分数的结果丢弃） |
| `reranker` | 是否启用 Reranker |
| `output_format` | 输出格式：`text`（拼接）或 `chunks`（列表） |

---

### 3.7 条件分支节点（Condition Node）

根据条件表达式选择不同执行路径。

**条件类型：**

| 类型 | 说明 |
|------|------|
| 表达式条件 | 基于变量值的逻辑判断 |
| LLM 判断 | 让 LLM 做复杂的自然语言判断 |

**表达式条件示例：**

```yaml
node_type: condition
branches:
  - name: 高优先级
    condition: "{{ sentiment_skill.output.score }} > 0.8 AND {{ sentiment_skill.output.sentiment }} == 'negative'"
    next_node: escalation_flow
  - name: 中优先级
    condition: "{{ sentiment_skill.output.sentiment }} == 'negative'"
    next_node: standard_flow
  - name: 默认
    condition: "else"
    next_node: positive_response
```

**LLM 判断条件：**

```yaml
node_type: condition
judge_type: llm
model_id: "<模型 ID>"
judge_prompt: |
  根据以下内容，判断该工单应路由到哪个部门。

  工单内容：{{ user_input }}

  可选项：
  - billing: 计费相关问题
  - technical: 技术支持问题
  - general: 一般咨询

  只输出选项名称，不要其他内容。
branches:
  - name: billing
    next_node: billing_team_node
  - name: technical
    next_node: tech_support_node
  - name: general
    next_node: general_support_node
```

---

### 3.8 循环节点（Loop Node）

对列表数据进行迭代处理。

**场景示例：**
- 对搜索结果列表中的每个 URL 执行抓取+摘要
- 对用户上传的多个文档逐一处理
- 批量生成个性化邮件

**配置项：**

```yaml
node_type: loop
loop_type: foreach        # foreach（列表迭代）或 while（条件循环）
iterate_over: "{{ search_results }}"   # 要迭代的列表变量
item_variable: "current_result"        # 当前迭代项变量名
max_iterations: 50                     # 防止无限循环
parallel: false                        # 是否并行执行（谨慎开启）
sub_nodes:
  - <子节点列表，每次迭代执行>
output_key: processed_results          # 收集每次迭代的输出
```

**While 循环（用于不确定次数的轮询）：**

```yaml
loop_type: while
condition: "{{ check_result.status }} != 'completed'"
check_interval_ms: 5000
max_iterations: 20       # 最多等待 100 秒
```

---

### 3.9 并行节点（Parallel Node）

多分支并发执行，等待全部完成后合并结果。

**配置项：**

```yaml
node_type: parallel
branches:
  - name: search_branch
    nodes: [web_search_node, summarize_node]
  - name: knowledge_branch
    nodes: [knowledge_retrieval_node]
  - name: history_branch
    nodes: [fetch_history_node]
merge_strategy: merge_all    # merge_all（等全部）/ first_success（第一个成功）
output_key: parallel_results
timeout_ms: 30000            # 整体并行超时
```

**合并策略：**

| 策略 | 行为 |
|------|------|
| `merge_all` | 等所有分支完成，结果合并为对象 |
| `first_success` | 第一个成功的分支结果生效，其余取消 |
| `race` | 第一个完成（成功或失败）生效 |

---

### 3.10 代码节点（Code Node）

在安全沙箱中执行 Python 代码，用于数据转换、格式化、计算等。

**特性：**
- 隔离执行环境（Docker 容器沙箱）
- 可访问 Workflow 变量（通过特定 API 读写）
- 禁止网络访问（防止数据泄露，需网络访问请用 Tool 节点）
- 支持常用数据处理库（pandas、json、re、datetime 等）
- 执行超时：默认 30 秒

**示例：**

```python
# inputs: workflow.get_variable("price_list")
import json

price_list = inputs["price_list"]

# 数据转换：过滤价格 > 100 的商品，并按价格排序
filtered = [p for p in price_list if p["price"] > 100]
sorted_prices = sorted(filtered, key=lambda x: x["price"], reverse=True)

# outputs 会自动设置为 return 的值
return {
    "filtered_products": sorted_prices,
    "count": len(sorted_prices),
    "max_price": sorted_prices[0]["price"] if sorted_prices else 0
}
```

---

### 3.11 人工节点（Human-in-the-Loop Node）

暂停工作流执行，等待人工处理后继续。

**使用场景：**
- 高风险操作前的人工审批（如：自动生成的合同内容需人工确认）
- 需要人工补充信息（如：AI 提取信息不完整，需人工补充）
- 质量把关（如：AI 生成的营销文案需人工审核后才能发布）
- 例外情况处理（如：自动化流程无法处理的特殊案例转人工）

**配置项：**

```yaml
node_type: human_task
task_type: approval           # approval（审批）/ input（信息采集）/ review（内容审核）

# 审批配置
assignee_config:
  type: role                  # 按角色分配
  role: "manager"
  fallback_user: "<user_id>"  # 指定角色无人时的备用人

notification:
  channels: [email, in_app]
  message_template: |
    工作流「{{ workflow.name }}」需要您审批。

    摘要：{{ llm_summary.output.summary }}

    请点击链接进行处理：{{ task_url }}

deadline:
  hours: 24                   # 24 小时内未处理则超时
  on_timeout: auto_reject     # auto_reject / auto_approve / escalate

# 提供给审批人的上下文信息
context_variables:
  - llm_summary.output.summary
  - trigger.output.user_input

# 审批人可以做的操作
actions:
  - name: approve
    label: 批准
    next_node: publish_node
  - name: reject
    label: 拒绝
    next_node: notify_user_node
    require_reason: true      # 拒绝时必须填写理由
  - name: modify
    label: 修改后重新提交
    next_node: edit_node
    editable_fields:          # 审批人可以直接修改哪些内容
      - llm_summary.output.summary

# 信息采集配置（task_type: input 时）
form_schema:
  supplementary_info:
    type: string
    label: 请补充以下信息
    required: true
  priority:
    type: select
    label: 紧急程度
    options: [低, 中, 高, 紧急]
```

**人工任务处理界面（工作台）：**

```
┌─────────────────────────────────────────────────────┐
│  待处理任务                              [3 条待处理] │
├─────────────────────────────────────────────────────┤
│  📋 合同内容审批                          2小时前    │
│     工作流：合同生成流程 v2.1                         │
│     优先级：高                                        │
│     截止：剩余 22 小时                                │
│     [查看详情]                                        │
├─────────────────────────────────────────────────────┤
│  ✉ 营销邮件审核                           4小时前    │
│     工作流：周报邮件自动化                            │
│     [查看详情]                                        │
└─────────────────────────────────────────────────────┘
```

**任务详情页：**

```
┌─────────────────────────────────────────────────────┐
│  审批任务：合同内容确认                               │
├─────────────────────────────────────────────────────┤
│  AI 生成内容摘要：                                    │
│  这是一份与北京科技有限公司的技术服务合同...          │
│                                                      │
│  完整内容：[展开查看 / 下载]                          │
├─────────────────────────────────────────────────────┤
│  执行历史：                                           │
│  ✅ 触发：手动触发（张三）                            │
│  ✅ 信息采集：完成                                    │
│  ✅ 合同生成：耗时 3.2s                               │
│  ⏳ 人工审批：等待中（本步骤）                        │
├─────────────────────────────────────────────────────┤
│  [批准]  [拒绝并填写原因]  [修改后重提]               │
└─────────────────────────────────────────────────────┘
```

---

### 3.12 子流程节点（Sub-Workflow Node）

将另一个已发布的工作流作为当前工作流的一个步骤嵌套调用。

**优势：**
- 流程复用（通用子流程可在多个父流程中引用）
- 保持画布简洁（将复杂子流程封装为单个节点）

**配置项：**

```yaml
node_type: sub_workflow
workflow_id: "<子流程 ID>"
workflow_version: "latest"
input_mapping:
  content: "{{ previous_node.output.text }}"
output_key: sub_result
```

---

### 3.13 变量节点（Variable Node）

在工作流中显式地定义、转换、合并变量。

```yaml
node_type: variable
operations:
  - set:
      key: "combined_context"
      value: "{{ search_node.output.results | join('\n') }}"
  - set:
      key: "max_price"
      value: 100
  - delete:
      key: "temp_data"          # 清理不再需要的变量（节省内存）
```

---

## 4. 工作流画布交互设计

### 4.1 画布布局

```
┌────────────────────────────────────────────────────────────────┐
│  工作流名称                                  [保存草稿] [发布]   │
├────────────┬──────────────────────────────────┬────────────────┤
│  节点面板  │            画布区域               │   属性面板     │
│            │                                   │                │
│ ▼ 触发器   │  [触发节点] ──▶ [LLM节点]         │ 选中节点属性   │
│  手动触发  │       │                           │ 显示在这里     │
│  API 触发  │       ▼                           │                │
│  定时触发  │  [条件分支]                        │                │
│  事件触发  │    ├──▶ [工具节点]                 │                │
│            │    └──▶ [Skill节点]                │                │
│ ▼ 处理节点 │                                   │                │
│  LLM 节点  │  拖拽节点到画布                    │                │
│  Agent节点 │  点击连接点连线                    │                │
│  Skill节点 │  点击节点配置属性                  │                │
│  ...       │                                   │                │
│            │                                   │                │
│ ▼ 控制流   │  [+] 添加注释                      │                │
│  条件分支  │  [□] 选区框选                      │                │
│  循环节点  │  [🔍] 缩放                         │                │
│  并行节点  │  [⬛] 小地图                        │                │
└────────────┴──────────────────────────────────┴────────────────┘
```

### 4.2 变量传递可视化

选中连线时，可查看该连线传递的变量：

```
[LLM节点] ─────────────────────▶ [条件节点]
              传递：
              • sentiment（string）
              • score（float）
              • raw_output（string）
```

### 4.3 执行状态可视化

工作流运行时，画布实时展示执行状态：

```
[触发节点]✅ ──▶ [LLM节点]✅ ──▶ [条件节点]✅
                                    │
                              走"高优先级"分支
                                    │
                                    ▼
                         [人工审批节点] ⏳ 等待中...
```

节点状态颜色：
- 灰色：等待执行
- 蓝色动画：执行中
- 绿色：成功
- 红色：失败
- 黄色：等待人工处理

---

## 5. 工作流变量系统

### 5.1 变量来源

| 变量类型 | 访问方式 | 说明 |
|---------|---------|------|
| 触发输入 | `{{ trigger.input.field_name }}` | 触发工作流时传入的参数 |
| 节点输出 | `{{ node_name.output.field_name }}` | 指定节点的输出 |
| 全局变量 | `{{ global.tenant_id }}` | 租户、工作流元信息 |
| 循环变量 | `{{ loop.current_item }}` `{{ loop.index }}` | 循环节点内使用 |
| 系统变量 | `{{ sys.timestamp }}` `{{ sys.workflow_run_id }}` | 系统内置变量 |

### 5.2 变量作用域

```
工作流级变量（全局可访问）
    └── 节点级临时变量（仅在该节点内使用）
            └── 循环内局部变量（每次迭代独立）
```

### 5.3 变量转换（Jinja2 过滤器）

```
{{ text | upper }}                      # 转大写
{{ items | length }}                    # 列表长度
{{ items | join(', ') }}               # 列表拼接
{{ timestamp | date('YYYY-MM-DD') }}   # 日期格式化
{{ price | round(2) }}                 # 数字精度
{{ json_str | from_json }}             # JSON 字符串解析
{{ obj | to_json }}                    # 序列化为 JSON 字符串
```

---

## 6. 错误处理与重试策略

### 6.1 节点级错误处理

每个节点可以配置错误处理策略：

```yaml
on_error:
  strategy: retry          # retry / skip / fail_fast / goto_node
  max_retries: 3
  retry_delay_ms: 2000
  retry_backoff: exponential   # linear / exponential
  fallback_value: null         # strategy 为 skip 时使用的默认值
  goto_node: error_handler     # strategy 为 goto_node 时跳转目标
```

### 6.2 工作流级错误处理

在工作流设置中配置全局错误处理：

```yaml
global_error_handler:
  notification:
    enabled: true
    channels: [email, in_app]
    recipients: ["{{ global.workflow_owner_email }}"]
  cleanup_steps:
    - rollback_node_id         # 可选：执行清理步骤
  max_execution_time_ms: 3600000   # 整体超时 1 小时
```

---

## 7. 工作流版本管理

工作流版本管理与 Agent 版本管理逻辑一致：

| 状态 | 说明 |
|------|------|
| `draft` | 草稿，可随时编辑，不对外可用 |
| `published` | 已发布，对外可触发，不可编辑（需新建版本） |
| `deprecated` | 已废弃，不可触发，历史记录仍可查看 |

**版本快照** 包含：完整节点配置、连线关系、变量定义、触发器配置。

---

## 8. 多智能体编排（Multi-Agent Orchestration）

### 8.1 为什么需要多 Agent

单 Agent 的能力边界：

- **上下文窗口限制**：处理超长任务时上下文溢出
- **专业化不足**：通用 Agent 在特定领域的深度不如专家 Agent
- **并行化受限**：单 Agent 本质上是串行的，大任务处理慢
- **可维护性差**：一个 Agent 承担过多职责时配置复杂难以维护

**多 Agent 的核心价值：** 将复杂任务分解，由专业 Sub-Agent 并行或协同处理，最终合并结果。

### 8.2 编排模式

#### 模式一：Supervisor 模式（监督者模式）

一个 Orchestrator Agent 负责任务规划和分配，多个 Worker Agent 负责执行子任务。

```
用户任务："写一份关于新能源汽车市场的研究报告"
    │
    ▼
Orchestrator Agent（规划）
    ├── 任务一：市场规模数据搜集 ──▶ Research Agent
    ├── 任务二：竞争格局分析   ──▶ Analysis Agent
    ├── 任务三：政策法规查询   ──▶ Regulation Agent
    └── 合并结果 ──▶ Writing Agent（报告撰写）
    │
    ▼
最终报告
```

**Orchestrator 配置：**

```yaml
orchestration_mode: supervisor
orchestrator_agent_id: "<主控 Agent ID>"
worker_agents:
  - agent_id: "<研究 Agent ID>"
    capability_tags: [web_search, data_collection]
    description: 擅长信息搜集和数据整理
  - agent_id: "<分析 Agent ID>"
    capability_tags: [data_analysis, chart_generation]
    description: 擅长数据分析和洞察生成
  - agent_id: "<写作 Agent ID>"
    capability_tags: [report_writing, document_formatting]
    description: 擅长专业报告撰写
task_allocation_strategy: capability_match   # 按能力标签匹配分配
max_parallel_agents: 3
```

#### 模式二：Pipeline 模式（管道模式）

Agent 链式执行，前一个 Agent 的输出作为后一个的输入。

```
数据收集 Agent → 数据清洗 Agent → 分析 Agent → 可视化 Agent → 报告 Agent
```

**特点：** 确定性强，每步输出明确，适合有固定流程的复杂任务。

**与 Workflow 的区别：** Pipeline 中每一步是 Agent（有自主判断和工具调用），Workflow 中的节点是确定性操作。

#### 模式三：Parallel 模式（并行模式）

多个 Agent 并行独立完成各自子任务，最后由 Merger Agent 汇总结果。

```
                    ┌──▶ 法律合规 Agent ──▶┐
用户任务 ──▶ 分发 ──├──▶ 技术可行性 Agent──▶├──▶ Merger Agent ──▶ 综合报告
                    └──▶ 市场竞争 Agent  ──▶┘
```

**适用场景：** 同一任务需要从多个视角独立分析（法律/技术/商业），各角度无依赖关系。

#### 模式四：Debate 模式（辩论模式）

多个 Agent 对同一问题给出不同立场的论点，最终由 Judge Agent 综合评判。

```
问题 → Agent A（正方）─┐
                       ├─▶ Judge Agent ──▶ 最终结论
     → Agent B（反方）─┘
```

**适用场景：** 需要充分论证的决策（技术选型、风险评估、方案比较）。

---

### 8.3 Agent 能力声明与路由

在多 Agent 场景中，每个 Sub-Agent 通过声明自己的能力标签，让 Orchestrator 能够按能力路由任务：

**Agent 能力声明（在 Agent Studio 中配置）：**

```yaml
capability_declaration:
  tags:
    - data_analysis
    - python_coding
    - chart_generation
  max_concurrent_tasks: 2        # 最多同时处理 2 个任务
  preferred_task_types:          # 优先承接的任务类型
    - quantitative_analysis
    - data_visualization
```

**Orchestrator 路由策略：**

| 策略 | 说明 |
|------|------|
| `capability_match` | 按能力标签精确匹配 |
| `least_busy` | 分配给当前负载最低的 Agent |
| `round_robin` | 轮询分配（能力相同的 Agent） |
| `priority` | 按 Agent 设置的优先级 |

---

### 8.4 Agent 间通信

**消息格式（A2A 协议规范）：**

```json
{
  "message_id": "msg_abc123",
  "from_agent": "orchestrator_agent_id",
  "to_agent": "research_agent_id",
  "task": {
    "id": "task_xyz789",
    "type": "information_retrieval",
    "description": "搜集 2024 年新能源汽车市场规模数据",
    "input": {
      "query": "2024 新能源汽车 市场规模",
      "sources": ["web", "knowledge_base"],
      "output_format": "structured"
    },
    "deadline_ms": 30000,
    "priority": "high"
  }
}
```

**响应格式：**

```json
{
  "message_id": "msg_def456",
  "task_id": "task_xyz789",
  "status": "completed",
  "result": {
    "data": { ... },
    "confidence": 0.87,
    "sources": [...]
  },
  "usage": {
    "steps": 5,
    "tool_calls": 3,
    "latency_ms": 8432
  }
}
```

---

### 8.5 A2A 协议集成规划

**什么是 A2A：** Google 发布的 Agent-to-Agent 通信标准（v1.0，2026 Q1 稳定版），已并入 Linux Foundation AAIF，是跨平台 Agent 互联的事实标准。

**A2A Agent Card：**

每个发布的 Agent 自动生成标准化的 Agent Card（`/.well-known/agent.json`），描述 Agent 的能力、输入/输出格式和访问方式：

```json
{
  "agent_id": "agent_xyz",
  "name": "数据分析专家",
  "description": "擅长量化数据分析和可视化",
  "version": "2.1.0",
  "capabilities": {
    "skills": ["data_analysis", "python_coding"],
    "tools": ["execute_python", "generate_chart"],
    "input_modes": ["text", "file"],
    "output_modes": ["text", "file", "structured_data"]
  },
  "communication": {
    "protocol": "a2a/1.0",
    "endpoint": "https://platform.example.com/a2a/agents/agent_xyz",
    "auth": {
      "type": "bearer_token",
      "token_url": "https://platform.example.com/oauth/token"
    }
  },
  "pricing": {
    "per_task": 0.01
  }
}
```

**跨平台调用流程：**

```
本平台 Orchestrator
    │
    │ 1. 发现外部 Agent（通过 Agent Registry 或直接 URL）
    ▼
外部 Agent Card 查询（GET /.well-known/agent.json）
    │
    │ 2. 获取 Agent 能力和通信方式
    ▼
Task 分配（POST /a2a/tasks，A2A 标准格式）
    │
    │ 3. 外部 Agent 执行任务
    ▼
Result 回调（Webhook 或轮询）
    │
    │ 4. 接收结果，继续本平台工作流
    ▼
后续流程
```

**平台内部 A2A 支持（P2 规划）：**

| 功能 | 说明 |
|------|------|
| Agent Card 自动生成 | 发布 Agent 时自动生成 A2A Agent Card |
| 外部 Agent 注册 | 管理员可以注册外部 Agent，在工作流中使用 |
| A2A 安全层 | JWT 鉴权 + 租户隔离 + 调用频率限制 |
| 跨平台任务追踪 | Trace 记录外部 A2A 调用的输入/输出 |

---

## 9. 工作流模板市场（P1）

平台提供常用工作流模板，租户可以一键复制并按需修改：

**模板分类：**

| 分类 | 示例模板 |
|------|---------|
| 内容处理 | 文档摘要流水线、多语言翻译流程、内容审核工作流 |
| 客户服务 | 工单分类与路由、自动回复+人工升级、客户反馈分析 |
| 数据处理 | 报表生成、数据质检+异常告警、ETL 流水线 |
| 代码辅助 | PR 自动审查、Bug 自动分析、文档自动生成 |
| 销售营销 | 线索资质评分、个性化邮件发送、竞品情报收集 |

---

## 10. 数据模型（概览）

详细 DDL 见 `09-database-design.md`：

### workflow 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(36) | UUID 主键 |
| tenant_id | varchar(36) | 所属租户 |
| name | varchar(100) | 工作流名称 |
| description | text | 描述 |
| category | varchar(50) | 分类 |
| status | varchar(20) | active / archived |
| current_version | varchar(20) | 当前发布版本 |
| created_by | varchar(36) | 创建人 |
| created_at | datetime | 创建时间 |

### workflow_version 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(36) | UUID |
| workflow_id | varchar(36) | 关联 workflow |
| version | varchar(20) | 版本号 |
| status | varchar(20) | draft/published/deprecated |
| dag_config | json | 完整 DAG 配置（节点+连线） |
| changelog | text | 版本说明 |
| published_at | datetime | 发布时间 |

### workflow_run 表（执行记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(36) | UUID |
| workflow_id | varchar(36) | 工作流 ID |
| version | varchar(20) | 执行的版本 |
| trigger_type | varchar(20) | 触发方式 |
| trigger_payload | json | 触发输入 |
| status | varchar(20) | running/completed/failed/timeout |
| started_at | datetime | 开始时间 |
| completed_at | datetime | 完成时间 |
| error | text | 失败原因 |

### workflow_node_execution 表（节点执行记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(36) | UUID |
| run_id | varchar(36) | 关联 workflow_run |
| node_id | varchar(36) | 节点 ID（与 dag_config 对应） |
| node_type | varchar(30) | 节点类型 |
| status | varchar(20) | pending/running/completed/failed/waiting_human |
| inputs | json | 节点输入快照 |
| outputs | json | 节点输出快照 |
| started_at | datetime | 开始时间 |
| completed_at | datetime | 完成时间 |
| error | text | 失败原因 |

### human_task 表（人工任务）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(36) | UUID |
| run_id | varchar(36) | 关联的工作流执行 |
| node_id | varchar(36) | 人工节点 ID |
| task_type | varchar(20) | approval/input/review |
| assignee_config | json | 分配规则配置 |
| context | json | 提供给处理人的上下文 |
| status | varchar(20) | pending/completed/timeout/escalated |
| assignee_id | varchar(36) | 实际处理人 |
| action_taken | varchar(20) | 处理动作（approve/reject/modify） |
| action_reason | text | 处理理由 |
| deadline_at | datetime | 截止时间 |
| completed_at | datetime | 完成时间 |

---

## 11. 接口概览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/workflows` | 获取工作流列表 |
| POST | `/api/v1/workflows` | 创建工作流 |
| GET | `/api/v1/workflows/{id}` | 获取工作流详情 |
| PUT | `/api/v1/workflows/{id}` | 更新工作流（草稿态） |
| POST | `/api/v1/workflows/{id}/publish` | 发布工作流版本 |
| POST | `/api/v1/workflows/{id}/trigger` | 手动触发工作流 |
| GET | `/api/v1/workflows/{id}/runs` | 获取执行历史 |
| GET | `/api/v1/workflows/runs/{run_id}` | 获取单次执行详情 |
| GET | `/api/v1/human-tasks` | 获取待处理人工任务列表 |
| POST | `/api/v1/human-tasks/{id}/action` | 提交人工任务处理结果 |

---

*文档版本：v1.0 | 最后更新：2026-03-18*
