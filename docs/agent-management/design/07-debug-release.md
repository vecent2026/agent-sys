# 调试测试与发布管理功能设计

## 文档信息

| 项 | 内容 |
|----|------|
| 文档编号 | 07 |
| 所属层次 | 功能层 |
| 关联文档 | 04-agent-studio、06-workflow-multiagent、08-observability-evaluation |
| 版本 | v1.0 |
| 状态 | 草稿 |

---

## 1. 设计原则

**核心原则：草稿与发布严格隔离**

- 调试阶段的任何修改不影响已发布的线上版本
- 同一时刻线上只有一个「已发布版本」在服务真实用户
- 版本快照完整记录配置状态，支持随时回滚

**用户体验目标：**
- 从「有想法」到「在线上验证」不超过 5 分钟
- 调试反馈实时，看到问题立即可以修改，无需重新部署
- 发布流程有保障，不会因为漏配关键项导致线上故障

---

## 2. 版本状态模型

### 2.1 Agent / Skill / Workflow 通用状态机

```
              编辑
[draft] ──────────────▶ [draft]
   │
   │ 发布（满足校验规则）
   ▼
[published] ◀──────────────────────────────── 回滚
   │             发布新版本时，旧版本自动变为↓
   ▼
[deprecated]
   │
   │ 归档（可选，主动操作）
   ▼
[archived]
```

**状态说明：**

| 状态 | 说明 | 可执行操作 |
|------|------|-----------|
| `draft` | 草稿，可编辑，不对外提供服务 | 编辑、发布、删除 |
| `published` | 已发布，对外提供服务，不可直接编辑 | 查看、回滚、新建版本（基于此版本创建草稿） |
| `deprecated` | 被新版本取代，不再提供服务 | 查看历史、回滚到此版本 |
| `archived` | 归档，不参与列表展示，不可操作 | 查看 |

### 2.2 版本号规范

- 每次发布自动生成版本号，格式：`YYYYMMDD-HHMM-{n}`（如 `20260318-1430-1`）
- 可选：用户在发布时手动填写版本备注（`v1.0 正式上线`、`修复提示词偏差`）

---

## 3. 草稿态调试

### 3.1 调试模式与线上模式隔离

```
同一 Agent 的两个独立执行路径：

调试路径：
用户（有编辑权限）→ 调试面板 → [草稿版本配置] → LLM/Tool → 调试响应
   └── 调用不计入线上监控指标
   └── Trace 存入调试专用数据空间

线上路径：
终端用户/API → [已发布版本配置] → LLM/Tool → 正式响应
   └── 调用计入监控指标
   └── Trace 存入正式数据空间
```

### 3.2 调试面板设计（Agent 调试）

**布局：**

```
┌────────────────────────────────────────────────────────────────┐
│  Agent 调试：客服助手（草稿 v3）        [切换已发布版本对比]     │
├─────────────────────────────┬──────────────────────────────────┤
│  对话窗口                    │  调试信息面板                     │
│                              │                                  │
│  [历史对话轮次显示区域]       │  ▼ 配置快照（只读）              │
│                              │    System Prompt: ...            │
│  ─────────────────────────  │    Model: gpt-4.1-mini           │
│                              │    Temperature: 0.7              │
│  用户：你能帮我查询我的订单吗  │    Tools: [search, query_db]     │
│                              │                                  │
│  Assistant: 当然可以，请提    │  ▼ 本次调用 Trace               │
│  供您的订单号...              │    ┌──────────────────────────┐  │
│                              │    │ ① Receive Message 2ms    │  │
│                              │    │ ② Tool: query_db 234ms   │  │
│  ─────────────────────────  │    │   输入: order_id=12345    │  │
│  [输入框]         [发送]      │    │   输出: {status: shipped} │  │
│                              │    │ ③ Generate Response 890ms│  │
│  [清空对话] [导出对话]         │    │   输入 tokens: 234       │  │
│                              │    │   输出 tokens: 87         │  │
│                              │    └──────────────────────────┘  │
│                              │                                  │
│                              │  ▼ 性能指标                      │
│                              │    总耗时: 1126ms                 │
│                              │    Token 消耗: 321                │
│                              │    估算成本: ¥0.0032              │
└─────────────────────────────┴──────────────────────────────────┘
```

**调试面板功能：**

| 功能 | 说明 |
|------|------|
| 实时 Trace 展示 | 每条消息的完整推理步骤、工具调用链 |
| 配置快照 | 当前草稿配置的只读展示，不需要切换到配置页 |
| 版本对比 | 左右分栏同时测试草稿版本和已发布版本 |
| 变量注入 | 手动注入测试变量（用户信息、上下文等） |
| 保存测试用例 | 将当前对话轮次保存为测试集用例 |
| 导出对话 | 导出完整对话历史（JSON/Markdown） |

### 3.3 SSE 流式输出

Agent 响应支持 Server-Sent Events (SSE) 流式输出，调试面板实时显示逐字生成效果：

**流式响应格式：**

```
data: {"type": "thinking", "content": "用户想查询订单状态..."}

data: {"type": "tool_call", "tool": "query_db", "inputs": {"order_id": "12345"}}

data: {"type": "tool_result", "tool": "query_db", "outputs": {"status": "shipped", "eta": "明天"}}

data: {"type": "text_delta", "content": "您的订单"}

data: {"type": "text_delta", "content": "（12345）"}

data: {"type": "text_delta", "content": "已发货，预计明天送达。"}

data: {"type": "done", "usage": {"input_tokens": 234, "output_tokens": 87}}
```

**推理过程展示（Thinking）：**

对于支持推理过程的模型（如 Claude 3.7 Sonnet Extended Thinking），调试面板可以可选展示推理过程：

```
▼ 思考过程（点击展开）
  用户在询问订单状态。我需要先查询数据库获取订单信息，
  然后根据状态给出友好的回复。需要调用 query_db 工具...
  [共 156 个 thinking tokens]
▼ 最终回复
  您的订单...
```

### 3.4 快速迭代循环

调试过程中频繁的「修改配置 → 重新测试」循环的体验优化：

1. **配置变更即时生效**：修改提示词/参数后，下一条消息立即使用新配置（无需保存草稿）
2. **对话历史保留**：修改配置后不清空对话历史，可以继续追问
3. **差异高亮**：在 Trace 中高亮显示与上次请求相比，本次调用的变化之处
4. **快速回退**：本地保留最近 10 次配置修改历史，可以一键回退

---

## 4. 批量测试

### 4.1 测试数据集

**测试集格式（CSV/JSON）：**

```json
[
  {
    "case_id": "case_001",
    "input": {
      "user_message": "我想退货，订单号是 12345",
      "user_context": { "vip_level": "gold" }
    },
    "expected_behavior": {
      "description": "应询问退货原因，并告知退货流程",
      "must_contain": ["退货原因", "7天无理由"],
      "must_not_contain": ["无法处理", "联系人工"],
      "response_type": "text"
    }
  },
  {
    "case_id": "case_002",
    "input": { "user_message": "你会干嘛" },
    "expected_behavior": {
      "description": "应介绍自己的功能",
      "must_contain": ["客服", "帮助"],
      "expected_intent": "self_introduction"
    }
  }
]
```

**测试集管理：**

- 支持从文件上传（CSV/JSON）或手动添加用例
- 支持从调试历史中导出对话轮次到测试集
- 测试集本身也有版本管理，支持团队共享
- 可以打标签（"回归测试集"、"边界用例"、"节假日场景"）

### 4.2 批量执行

**配置：**

```
测试集：客服场景测试集 v2（128 条用例）
执行版本：草稿 v3
并发数：5（同时执行 5 条用例）
超时：每条 30 秒
```

**执行进度界面：**

```
批量测试进行中... 78/128（61%）

[████████████████████░░░░░░░░░░░] 61%

通过：65  失败：8  进行中：5  待执行：50

预计剩余：2分30秒
```

### 4.3 测试结果分析

**总览：**

```
测试完成 ✅

通过率：76.6%（98/128）

失败用例（30条）：
  ├── 未提及退货流程（12条）         [查看详情]
  ├── 语气过于生硬（8条）            [查看详情]
  ├── 未识别 VIP 用户身份（6条）     [查看详情]
  └── 其他（4条）                   [查看详情]

性能数据：
  P50 延迟：820ms
  P95 延迟：2340ms
  平均 Token：287

[导出报告] [与草稿 v2 对比] [加入回归套件]
```

**失败用例详情：**

| case_id | 输入 | 期望行为 | 实际输出 | 失败原因 |
|---------|------|---------|---------|---------|
| case_031 | "我是会员，想退款" | 应识别 VIP 身份 | 标准退款流程 | 未提及 VIP 专属通道 |

**与历史版本对比（A/B 对比表）：**

| 指标 | 草稿 v2 | 草稿 v3（当前） | 变化 |
|------|---------|----------------|------|
| 通过率 | 71.9% | 76.6% | ↑ +4.7% |
| P50 延迟 | 950ms | 820ms | ↓ -130ms |
| 平均 Token | 312 | 287 | ↓ -25 |
| VIP 识别失败 | 15 条 | 6 条 | ↓ -9 条 |

---

## 5. A/B 测试

### 5.1 A/B 测试设计

A/B 测试允许将真实流量按比例分配到不同版本，通过统计数据决定哪个版本更优。

**适用场景：**
- 提示词优化效果验证（新版 System Prompt 是否真的更好）
- 模型切换评估（换到新模型后效果是否提升）
- 推理参数调优（temperature 从 0.7 改为 0.5 的实际影响）

**配置：**

```yaml
ab_test:
  name: "提示词优化 AB 测试"
  start_date: "2026-03-18"
  end_date: "2026-03-25"    # 或者手动结束

  variants:
    - name: "控制组（当前版本）"
      agent_version: "20260310-1430-1"
      traffic_ratio: 0.5    # 50% 流量
    - name: "实验组（新提示词）"
      agent_version: "20260318-0930-1"
      traffic_ratio: 0.5

  success_metrics:
    primary: user_satisfaction_score   # 主要指标
    secondary:
      - response_relevance_score       # LLM-as-Judge 相关性
      - session_completion_rate        # 会话完成率
      - avg_turns_per_session          # 平均对话轮数

  statistical_settings:
    confidence_level: 0.95
    min_sample_size: 500              # 每组最少 500 次有效交互后才显示统计显著性
```

### 5.2 A/B 测试结果面板

```
A/B 测试：提示词优化 AB 测试
状态：进行中（第 5 天 / 共 7 天）

                    控制组          实验组          差异
流量               51.2%           48.8%           —
交互次数            1,243           1,187           —
──────────────────────────────────────────────────
满意度评分 ★        3.82            4.17          ↑ +0.35 ✅
相关性评分          0.78            0.84          ↑ +0.06 ✅
会话完成率          62.1%           71.3%          ↑ +9.2% ✅
平均对话轮数         3.2             2.8           ↓ -0.4  ✅
P50 响应延迟        820ms           840ms          ↑ +20ms ⚠️
──────────────────────────────────────────────────
统计显著性          —               p=0.023        ✅ 显著

[提前结束并发布实验组] [继续观察] [停止测试]
```

---

## 6. 发布前校验

### 6.1 校验规则清单

发布前系统自动执行完整性和合规性检查：

**必须通过（阻断发布）：**

| 检查项 | 说明 |
|--------|------|
| 名称不为空 | Agent/Skill/Workflow 名称已填写 |
| 模型已配置 | 至少绑定一个处于启用状态的模型 |
| 系统提示词不为空 | System Prompt 至少有基础内容 |
| 绑定工具可用 | 所有绑定的工具处于启用状态，MCP Server 连通 |
| 绑定知识库可用 | 绑定的知识库处于启用状态且索引完成 |
| 绑定 Skill 已发布 | 引用的 Skill 已发布（非草稿） |
| 输入/输出 Schema 合法 | Skill 的 Schema 格式正确 |
| 工作流 DAG 无环 | Workflow 图中不存在循环依赖 |
| 触发节点存在 | Workflow 至少有一个触发节点 |

**建议修复（警告，不阻断发布）：**

| 检查项 | 建议原因 |
|--------|---------|
| 未配置开场白 | 缺少开场白可能影响首次使用体验 |
| 未添加建议问题 | 引导性问题可提升用户互动率 |
| 未设置对话轮数限制 | 可能导致超长会话产生高成本 |
| 未配置备用模型 | 主模型故障时无法降级服务 |
| 提示词未包含限制语 | 可能导致 AI 回答超出业务范围 |
| 未运行过批量测试 | 建议发布前先进行充分测试 |

**校验结果展示：**

```
发布前检查

✅ 必要检查（8/8 通过）
  ✅ 名称已填写：客服助手
  ✅ 模型已配置：gpt-4.1-mini（已启用）
  ✅ 系统提示词：已填写（324字）
  ✅ 工具可用：query_db ✅  search ✅
  ✅ 知识库可用：产品手册（已索引）
  ✅ 关联 Skill 已发布：情感分析 v1.2 ✅
  ✅ Schema 格式：合法
  ✅ 无循环依赖

⚠️ 建议检查（3 条警告）
  ⚠️ 未配置开场白 — 建议添加，提升首次体验
  ⚠️ 未设置最大对话轮数 — 可能产生超预期成本
  ⚠️ 未运行批量测试 — 本次草稿尚未经过测试

[忽略警告，继续发布] [取消，去修改]
```

---

## 7. 发布流程

### 7.1 标准发布流程

```
用户点击「发布」
    │
    ▼
发布前校验（自动）
    ├── 失败 ──▶ 展示错误清单，阻断发布
    └── 通过 ──▶ 继续
    │
    ▼
填写版本信息
  - 版本备注（选填，如"修复提示词偏差，优化 VIP 识别"）
  - 更新日志（选填）
    │
    ▼
二次确认弹窗
  - 显示将被替换的当前发布版本信息
  - 展示本次变更摘要（提示词有修改/新增工具/参数调整）
    │
    ▼
执行发布
  - 创建版本快照（完整 JSON）
  - 当前发布版本状态变为 deprecated
  - 新版本状态变为 published
  - 触发发布后钩子（可选）：通知 Webhook、更新下游系统
    │
    ▼
发布成功提示
  - 版本号：20260318-1430-1
  - 发布时间
  - 生效范围（全量/灰度）
```

### 7.2 灰度发布（P1）

灰度发布允许新版本先对部分用户生效，验证稳定后再全量推送。

**灰度策略配置：**

```yaml
gradual_release:
  enabled: true
  strategy: percentage          # percentage（按比例）/ user_whitelist（白名单）
  initial_percentage: 10        # 初始 10% 用户
  rollout_plan:
    - percentage: 10
      wait_hours: 24            # 观察 24 小时
      auto_next: true           # 如果无异常，自动进入下一阶段
    - percentage: 30
      wait_hours: 24
    - percentage: 100
      wait_hours: 0
  auto_rollback:
    enabled: true
    error_rate_threshold: 0.05  # 错误率超过 5% 自动回滚
    latency_threshold_ms: 5000  # P95 延迟超过 5s 自动回滚
```

**灰度状态面板：**

```
灰度发布进行中（v20260318-1430-1）

当前阶段：第 1 阶段（10% 流量）
进行中：14/24 小时

指标（新版本 vs 旧版本）：
  错误率：0.8% vs 1.2%  ✅ 正常
  P95 延迟：1240ms vs 1180ms  ⚠️ 略有上升

[立即全量发布] [暂停灰度] [回滚到旧版本]
```

---

## 8. 版本快照

### 8.1 快照内容

每个已发布版本保存完整的 JSON 快照，确保可以完整还原任何历史版本：

**Agent 版本快照结构：**

```json
{
  "snapshot_id": "snap_abc123",
  "agent_id": "agent_xyz",
  "version": "20260318-1430-1",
  "published_at": "2026-03-18T14:30:00+08:00",
  "published_by": "user_admin_001",
  "config": {
    "basic": {
      "name": "客服助手",
      "description": "处理用户售后问题",
      "type": "conversational"
    },
    "prompt": {
      "system_prompt": "你是一名专业的客服助手...",
      "variables": { "current_date": "{{sys.date}}" }
    },
    "model": {
      "model_id": "model_gpt4o_001",
      "model_identifier": "gpt-4.1-mini",
      "temperature": 0.7,
      "max_tokens": 2048,
      "fallback_model_id": "model_moonshot_001"
    },
    "tools": [
      {
        "tool_id": "tool_query_db",
        "name": "query_db",
        "version": "1.2.0",
        "call_strategy": "auto"
      }
    ],
    "knowledge_bases": [
      {
        "kb_id": "kb_product_manual",
        "name": "产品手册",
        "top_k": 5,
        "retrieval_strategy": "hybrid"
      }
    ],
    "skills": [
      {
        "skill_id": "skill_sentiment",
        "version": "1.2.0",
        "alias": "analyze_sentiment"
      }
    ],
    "memory": {
      "window_strategy": "sliding",
      "max_turns": 20,
      "cross_session": false
    },
    "behavior": {
      "max_steps": 10,
      "timeout_ms": 60000,
      "output_format": "text"
    }
  }
}
```

### 8.2 版本历史列表

```
版本历史（客服助手）

版本                  发布时间              发布人    备注              操作
20260318-1430-1  ✅  2026-03-18 14:30    张三      修复 VIP 识别问题   [查看] [回滚]
20260310-0930-1      2026-03-10 09:30    张三      增加知识库          [查看] [回滚]
20260301-1600-1      2026-03-01 16:00    李四      初始版本发布        [查看] [回滚]
```

---

## 9. 回滚

### 9.1 回滚流程

```
用户选择历史版本 → 点击「回滚到此版本」
    │
    ▼
确认弹窗
  ⚠️ 确认回滚到 v20260310-0930-1？
  当前版本（v20260318-1430-1）将变为 deprecated 状态
  回滚后将立即生效，请确认影响范围
    │
    ▼
执行回滚
  - 历史版本的快照配置恢复为当前 published 版本
  - 原 published 版本变为 deprecated
  - 写入回滚操作日志（操作人、时间、回滚原因、从哪个版本到哪个版本）
    │
    ▼
回滚完成提示
```

**回滚不会：**
- 清除历史调用数据
- 修改原版本快照内容
- 影响 deprecated 状态的版本（依然可以再次回滚到更早版本）

---

## 10. 多渠道集成

### 10.1 REST API

发布后，Agent 自动获得 REST API 端点，支持第三方系统调用：

**单次对话（非流式）：**

```http
POST /api/v1/agents/{agent_id}/chat
Authorization: Bearer <api_key>
Content-Type: application/json

{
  "session_id": "session_abc123",    // 可选，用于维持多轮对话
  "message": "我想退货，订单号是 12345",
  "stream": false,
  "context": {
    "user_id": "user_001",           // 可选：传入用户上下文
    "user_name": "张三",
    "metadata": { "vip_level": "gold" }
  }
}
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "session_id": "session_abc123",
    "message_id": "msg_xyz789",
    "content": "您好，我来帮您处理退货。请问您退货的原因是什么？",
    "usage": {
      "input_tokens": 156,
      "output_tokens": 43,
      "total_tokens": 199,
      "latency_ms": 1240
    },
    "trace_id": "trace_def456"
  }
}
```

### 10.2 SSE 流式 API

```http
POST /api/v1/agents/{agent_id}/chat
Authorization: Bearer <api_key>
Content-Type: application/json
Accept: text/event-stream

{
  "session_id": "session_abc123",
  "message": "我想退货",
  "stream": true
}
```

**响应流：**

```
data: {"type": "start", "message_id": "msg_xyz789"}

data: {"type": "text_delta", "content": "您好"}
data: {"type": "text_delta", "content": "，我来"}
data: {"type": "text_delta", "content": "帮您处理退货。"}

data: {"type": "tool_call", "name": "query_order", "inputs": {"order_id": "12345"}}
data: {"type": "tool_result", "name": "query_order", "status": "success"}

data: {"type": "text_delta", "content": "您的订单状态为..."}

data: {"type": "done", "usage": {"input_tokens": 156, "output_tokens": 87, "latency_ms": 1480}}
```

### 10.3 WebSocket 实时通信（P1）

适用于需要双向实时通信的场景（如：在线客服、实时协作）：

```javascript
const ws = new WebSocket('wss://platform.example.com/api/v1/agents/{agent_id}/ws');
ws.onopen = () => {
  ws.send(JSON.stringify({
    type: 'auth',
    token: '<api_key>'
  }));
};
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  // 处理 text_delta、tool_call、done 等事件类型
};
```

### 10.4 嵌入式 Widget（P1）

一段 JavaScript 代码即可在任意网页嵌入对话框：

```html
<script>
  window.AgentWidget = {
    agentId: 'agent_xyz',
    apiKey: 'ak_xxxx',           // 建议使用只读 API Key
    theme: {
      primaryColor: '#2563EB',
      position: 'bottom-right'
    },
    welcome: '您好！有什么可以帮您的吗？',
    userIdentity: {              // 可选：传入用户身份信息
      userId: 'user_001',
      userName: '张三'
    }
  };
</script>
<script src="https://cdn.platform.example.com/agent-widget.js"></script>
```

Widget 特性：
- 支持桌面端和移动端适配
- 支持自定义品牌色和 logo
- 支持关闭会话/历史记录
- 支持文件上传（如果 Agent 支持）

### 10.5 IM 渠道集成（P2）

通过 Webhook 对接企业 IM 工具，使 Agent 具备在 IM 中响应的能力：

| 渠道 | 集成方式 |
|------|---------|
| 飞书（Lark） | 飞书机器人 Webhook + Event API |
| 钉钉 | 钉钉自定义机器人 |
| 企业微信 | 企业微信应用 API |

**配置示例（飞书）：**

```yaml
channel_integrations:
  - type: feishu
    name: 内部客服机器人
    app_id: cli_xxxxxxxx
    app_secret: "{{ secret.feishu_app_secret }}"
    encrypt_key: "{{ secret.feishu_encrypt_key }}"
    trigger_conditions:
      - mention_bot: true        # @机器人时触发
      - keywords: ["帮我", "问一下"]  # 包含关键词时触发
```

### 10.6 API Key 管理

调用 Agent API 需要 API Key，支持：

| Key 类型 | 说明 |
|---------|------|
| 租户主 Key | 完整权限（调用所有 Agent、查看 Trace） |
| Agent 专用 Key | 只能调用指定 Agent（最小权限原则） |
| 只读 Key | 只能发起对话，不能查看 Trace 或配置 |
| 临时 Key | 设置过期时间，适合前端嵌入场景 |

**API Key 安全：**
- Key 只在创建时完整展示一次（与 GitHub Token 类似）
- 支持设置 IP 白名单
- 支持设置调用来源域名限制（防止 Key 被盗用）
- 支持查看最近调用记录和 Key 使用情况

---

## 11. 数据模型（概览）

### agent_version 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(36) | UUID |
| agent_id | varchar(36) | 关联 agent.id |
| version | varchar(30) | 版本标识符 |
| status | varchar(20) | draft/published/deprecated/archived |
| config_snapshot | json | 完整配置快照 |
| version_note | varchar(500) | 版本备注 |
| changelog | text | 更新日志 |
| gradual_release_config | json | 灰度发布配置（可空） |
| published_at | datetime | 发布时间 |
| published_by | varchar(36) | 发布人 |
| deprecated_at | datetime | 废弃时间 |
| deprecated_reason | varchar(200) | 废弃原因（回滚时填写） |

### test_suite 表（测试集）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(36) | UUID |
| tenant_id | varchar(36) | 租户 |
| name | varchar(100) | 测试集名称 |
| description | text | 描述 |
| target_type | varchar(20) | agent/skill/workflow |
| target_id | varchar(36) | 被测对象 ID |
| tags | json | 标签 |
| case_count | int | 用例数量 |
| created_by | varchar(36) | 创建人 |
| created_at | datetime | 创建时间 |

### test_case 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(36) | UUID |
| suite_id | varchar(36) | 所属测试集 |
| case_name | varchar(100) | 用例名称 |
| input | json | 输入数据 |
| expected_behavior | json | 期望行为描述 |
| tags | json | 标签 |

### test_run 表（批量测试执行记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(36) | UUID |
| suite_id | varchar(36) | 测试集 |
| target_version | varchar(30) | 执行的版本 |
| status | varchar(20) | running/completed/cancelled |
| total_cases | int | 总用例数 |
| passed | int | 通过数 |
| failed | int | 失败数 |
| started_at | datetime | 开始时间 |
| completed_at | datetime | 完成时间 |
| report | json | 汇总报告 |

### api_key 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(36) | UUID |
| tenant_id | varchar(36) | 所属租户 |
| name | varchar(100) | Key 名称 |
| key_prefix | varchar(10) | Key 前缀（展示用，如 `ak_xxxx...`） |
| key_hash | varchar(64) | Key 哈希（SHA-256，不存明文） |
| type | varchar(20) | tenant_master/agent_specific/readonly/temporary |
| agent_id | varchar(36) | 绑定的 Agent（type=agent_specific 时） |
| ip_whitelist | json | IP 白名单 |
| domain_whitelist | json | 域名白名单 |
| expires_at | datetime | 过期时间（临时 Key） |
| last_used_at | datetime | 最后使用时间 |
| status | varchar(20) | active/revoked |
| created_by | varchar(36) | 创建人 |
| created_at | datetime | 创建时间 |

---

## 12. 接口概览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/agents/{id}/debug` | 草稿态调试（单条消息） |
| POST | `/api/v1/agents/{id}/publish` | 发布 Agent |
| POST | `/api/v1/agents/{id}/rollback` | 回滚到指定版本 |
| GET | `/api/v1/agents/{id}/versions` | 获取版本历史 |
| POST | `/api/v1/test-suites` | 创建测试集 |
| GET | `/api/v1/test-suites/{id}/cases` | 获取测试用例 |
| POST | `/api/v1/test-runs` | 执行批量测试 |
| GET | `/api/v1/test-runs/{id}` | 获取测试结果 |
| POST | `/api/v1/agents/{id}/ab-tests` | 创建 A/B 测试 |
| GET | `/api/v1/agents/{id}/ab-tests/{ab_id}` | 获取 A/B 测试结果 |
| POST | `/api/v1/agents/{id}/chat` | 对话（正式调用） |
| POST | `/api/v1/api-keys` | 创建 API Key |
| GET | `/api/v1/api-keys` | 获取 API Key 列表 |
| DELETE | `/api/v1/api-keys/{id}` | 撤销 API Key |

---

*文档版本：v1.0 | 最后更新：2026-03-18*
