# 前端双端设计（平台端 + 租户端）

## 文档信息

| 项 | 内容 |
|----|------|
| 文档编号 | 12 |
| 所属层次 | 技术层 |
| 关联文档 | 02-llm-management、04-agent-studio、07-debug-release |
| 版本 | v1.0 |
| 状态 | 草稿 |

---

## 1. 整体架构

### 1.1 双端定位

| 端 | 访问者 | 核心功能 | URL 路径 |
|----|--------|---------|---------|
| 平台端 | 平台管理员 | 大模型管理、密钥管理、平台监控 | `/platform/` |
| 租户端 | 租户管理员、开发者 | Agent 配置、知识库、Skill、Workflow、调试发布、可观测性 | `/tenant/` |

两端共用同一个 React SPA（已有的 `frontend/` 项目），通过路由前缀区分，复用 admin-service 的用户认证体系。

### 1.2 路由结构

```
/platform/
  ├── llm-models          # 大模型接入管理
  ├── secrets             # 密钥管理
  └── platform-monitor    # 平台监控（P1）

/tenant/
  ├── agents              # 智能体列表
  ├── agents/:id          # 智能体配置（Agent Studio）
  ├── agents/:id/debug    # 调试面板
  ├── agents/:id/versions # 版本历史
  ├── skills              # Skill 库
  ├── skills/:id          # Skill 详情/编辑
  ├── workflows           # 工作流列表
  ├── workflows/:id       # 工作流画布编辑器
  ├── knowledge-bases     # 知识库列表
  ├── knowledge-bases/:id # 知识库详情
  ├── observability       # 可观测性总览
  ├── sessions            # 会话日志
  ├── sessions/:id        # 会话详情（Trace 回放）
  ├── cost-analysis       # 成本分析
  ├── api-keys            # API Key 管理
  └── settings            # 租户设置
```

---

## 2. 平台端页面设计

### 2.1 大模型接入管理（`/platform/llm-models`）

**页面结构：上方工具栏 + 下方表格（遵循项目 UI 规范）**

**工具栏（左）：**
- 搜索框（按模型名称/标识搜索，实时触发）
- 厂商类型下拉筛选
- 状态筛选（启用/停用/全部）

**工具栏（右）：**
- 「添加模型」按钮（primary 样式）

**表格列：**

| 列 | 内容 | 备注 |
|----|------|------|
| 模型名称 | 展示名 + 模型标识（灰色小字） | — |
| 厂商 | 厂商 Tag（带颜色） | 按厂商配置颜色 |
| 状态 | 启用/停用 Badge | 绿/灰 |
| 默认模型 | 「默认」Tag 或空 | 全局唯一 |
| 可用性 | 可用/存在问题/未测试 | 绿/橙/灰 |
| 最后测试时间 | 相对时间（如「2小时前」） | — |
| 操作 | 一键测试 / 编辑 / 删除 | 固定右侧 |

**一键测试行内交互：**
- 点击「测试」按钮 → 按钮变为加载中
- 2~10 秒后显示结果：✅ 可用（耗时 320ms）或 ❌ 失败（错误信息）
- 结果写回表格「可用性」列

**添加/编辑模型 Drawer：**

右侧 Drawer，宽度 600px，分区块展示：

```
┌─── 基础信息 ─────────────────────────────────────┐
│ 模型名称 *        [________________________]      │
│ 模型标识 *        [________________________]      │
│                   如：gpt-4.1-mini                │
│ 厂商类型 *        [OpenAI           ▼]            │
└───────────────────────────────────────────────────┘

┌─── 调用配置 ─────────────────────────────────────┐
│ Base URL *        [https://api.openai.com/v1]     │
│ 接口路径          [/chat/completions]             │
│ 请求超时 *        [30000]  ms                     │
└───────────────────────────────────────────────────┘

┌─── 鉴权配置 ─────────────────────────────────────┐
│ 鉴权方式          API Key（固定）                  │
│ 密钥配置 *        [选择密钥配置  ▼] [+ 新建密钥]   │
└───────────────────────────────────────────────────┘

┌─── 管理属性 ─────────────────────────────────────┐
│ 状态              ● 启用  ○ 停用                   │
│ 默认模型          □ 设为默认模型                    │
└───────────────────────────────────────────────────┘
```

### 2.2 密钥管理（`/platform/secrets`）

类似模型管理的表格页，特殊点：

- 创建时展示完整密钥一次，之后仅展示前 4 位 + 末 4 位（`sk-abcd...efgh`）
- 编辑时密钥字段显示为 `••••••••••••`，不支持查看原值，只支持「重置密钥」

---

## 3. 租户端页面设计

### 3.1 Agent 列表（`/tenant/agents`）

**卡片网格布局（每行 3 张）：**

```
┌────────────────────────────────────────────────────┐
│  智能体                     [+ 创建智能体]           │
│  [搜索...]  [状态: 全部 ▼]  [标签: 全部 ▼]          │
│                                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐         │
│  │ 🤖       │  │ 🤖       │  │ 🤖       │         │
│  │ 客服助手  │  │ 数据分析  │  │ 合同助手  │         │
│  │          │  │          │  │          │         │
│  │ 已发布    │  │ 已发布    │  │ 草稿      │         │
│  │ v3       │  │ v1       │  │ —        │         │
│  │          │  │          │  │          │         │
│  │ 调用 1.2k │  │ 调用 342  │  │ 未发布    │         │
│  │          │  │          │  │          │         │
│  │[配置][调试]│  │[配置][调试]│  │[配置][调试]│        │
│  └──────────┘  └──────────┘  └──────────┘         │
└────────────────────────────────────────────────────┘
```

### 3.2 Agent Studio（`/tenant/agents/:id`）

**最核心的页面。** 左侧配置面板 + 右侧实时调试面板。

```
┌────────────────────────────────────────────────────────────────────┐
│  ← 智能体列表   客服助手（草稿 v3）           [保存] [发布]  [查看历史] │
├────────────────────────────┬───────────────────────────────────────┤
│  配置区                     │  调试区                               │
│                             │                                       │
│  [基本信息]                  │  ┌──── 对话窗口 ─────────────────┐   │
│  [提示词]       ◀ 当前选中   │  │                               │   │
│  [模型与参数]               │  │  👤 你好                       │   │
│  [工具]                     │  │  🤖 您好！我是客服助手...      │   │
│  [知识库]                   │  │                               │   │
│  [Skills]                   │  │  👤 帮我查询订单 12345         │   │
│  [记忆]                     │  │  🤖 [工具调用: query_db]      │   │
│  [安全]                     │  │     您的订单已发货...          │   │
│  [发布配置]                  │  └───────────────────────────────┘   │
│                             │                                       │
│  ┌─── 提示词 ─────────────┐  │  ┌──── Trace 面板 ───────────────┐   │
│  │ System Prompt:         │  │  │                               │   │
│  │ 你是一名专业的客服...   │  │  │  ① LLM Call  430ms           │   │
│  │                        │  │  │  ② query_db  280ms           │   │
│  │ 变量：                  │  │  │  ③ LLM Call  890ms           │   │
│  │ {{current_date}} ✓     │  │  │                               │   │
│  │ {{user_name}}   ✓     │  │  │  总耗时: 1600ms  Token: 387    │   │
│  └────────────────────────┘  │  └───────────────────────────────┘   │
│                             │                                       │
│  [预览变量效果]              │  [输入...        ] [发送]  [清空]    │
└────────────────────────────┴───────────────────────────────────────┘
```

**配置区各 Section 的交互：**

**提示词配置：**
- 代码编辑器（支持 `{{ variable }}` 语法高亮）
- 右侧变量面板：列出所有可用变量，点击自动插入
- 底部字数统计 + Token 估算
- 「AI 优化」按钮：调用 LLM 对提示词给出改进建议

**模型与参数：**

```
模型选择     [gpt-4.1-mini（启用） ▼]
             ⚠️ 上次测试：可用（2小时前）

Temperature  [──●──────────] 0.7
             最精确                    最多样

Top P        [────●────────] 1.0

Max Tokens   [________________] 2048

流式输出      ● 开启  ○ 关闭

备用模型      [moonshot-v1-8k ▼]  + 新增备用
```

**工具配置：**

```
已绑定工具

查询数据库 (query_db)      [移除]
  调用策略: 自动决策 ▼

网络搜索 (web_search)      [移除]
  调用策略: 自动决策 ▼

[+ 添加工具]  [管理 MCP Server]
```

**知识库配置：**

```
已绑定知识库

产品手册 (kb_product)       [配置] [移除]
  检索策略: 混合检索
  Top K: 5  Score 阈值: 0.7

FAQ 数据库 (kb_faq)         [配置] [移除]

[+ 添加知识库]
```

**发布配置：**

```
访问权限
  ● 私有（仅租户内部）
  ○ 公开（通过 API Key 访问）

API 速率限制
  每分钟请求数        [200]
  每用户每分钟       [10]
  最大并发会话        [100]

单会话限制
  最大对话轮数        [50]
  最大 Token/会话    [50000]
```

### 3.3 Skill 库（`/tenant/skills`）

**左侧分类导航 + 右侧列表/卡片：**

```
┌──────────────────────────────────────────────────────────┐
│  Skill 库                              [+ 创建 Skill]     │
│                                                           │
│ ┌──────────────────┐  ┌──────────────────────────────────┐│
│ │ 全部 (23)        │  │ 搜索... [类型 ▼] [可见性 ▼]      ││
│ │                  │  │                                  ││
│ │ ▶ 文本处理 (8)   │  │ ┌──────────────────────────────┐ ││
│ │   摘要与压缩 (3) │  │ │ 情感分类          [Atomic]   │ ││
│ │   翻译 (2)      │  │ │ v1.2.0 · 租户共享             │ ││
│ │   风格转换 (3)  │  │ │ 输入情感倾向，输出正负中性评分  │ ││
│ │                  │  │ │ 调用 1,234次/7天  ⭐ 4.2     │ ││
│ │ ▶ 信息抽取 (5)   │  │ │              [调试] [使用]    │ ││
│ │ ▶ 内容生成 (6)   │  │ └──────────────────────────────┘ ││
│ │ ▶ 分析判断 (4)   │  │                                  ││
│ └──────────────────┘  └──────────────────────────────────┘│
└──────────────────────────────────────────────────────────┘
```

**Skill 创建/编辑（分步 Drawer）：**

Step 1: 基础信息 → Step 2: 输入输出 Schema → Step 3: 提示词 → Step 4: 模型配置 → Step 5: 调试

### 3.4 工作流画布（`/tenant/workflows/:id`）

工作流编辑器是技术复杂度最高的页面。

**技术实现选型：**

| 库 | Stars | 适用性 |
|----|-------|--------|
| **React Flow** | 22k+ | 轻量、可定制性强、MIT 协议，**推荐** |
| AntV G6 | 11k+ | 国产，Ant Design 生态，学习曲线稍高 |
| Rete.js | 9k+ | 专为 Node-based Editor，功能完整 |

选用 **React Flow（@xyflow/react）**，与 Ant Design 结合构建自定义节点。

**画布布局（详见第 2 部分 Section 4 工作流画布）：**

自定义节点组件示例（LLM Node）：

```tsx
// frontend/src/components/workflow/nodes/LLMNode.tsx

import { Handle, Position, NodeProps } from '@xyflow/react';
import { Tag, Tooltip } from 'antd';

interface LLMNodeData {
  label: string;
  modelName: string;
  status?: 'idle' | 'running' | 'completed' | 'failed';
}

export const LLMNode: React.FC<NodeProps<LLMNodeData>> = ({ data, selected }) => {
  const statusColor = {
    idle: '#CBD5E1',
    running: '#3B82F6',
    completed: '#16A34A',
    failed: '#DC2626'
  }[data.status || 'idle'];

  return (
    <div className={`workflow-node llm-node ${selected ? 'selected' : ''}`}
         style={{ borderColor: statusColor }}>
      <Handle type="target" position={Position.Left} />

      <div className="node-header">
        <span className="node-icon">🤖</span>
        <span className="node-type">LLM 节点</span>
      </div>
      <div className="node-body">
        <div className="node-label">{data.label}</div>
        <Tag color="blue">{data.modelName}</Tag>
      </div>

      <Handle type="source" position={Position.Right} />
    </div>
  );
};
```

### 3.5 知识库管理（`/tenant/knowledge-bases/:id`）

**知识库详情页，分 Tabs：**

**Tab 1 - 文档列表：**
```
┌─────────────────────────────────────────────────────┐
│  [上传文档] [从 URL 导入] [从数据库同步]              │
│                                                     │
│ 文件名          状态      切片数  创建时间  操作      │
│ 产品说明.pdf    ✅ 已索引  234     3天前    [删除]    │
│ FAQ.xlsx       ⏳ 处理中  —       1分钟前  [取消]    │
│ 竞品分析.md    ✅ 已索引  89      1周前    [删除]    │
└─────────────────────────────────────────────────────┘
```

**Tab 2 - 检索测试：**
```
Query: [帮我查一下退换货政策___________] [检索]

检索策略: ● 混合  ○ 向量  ○ 关键词    Top K: [5]

─── 结果 ──────────────────────────────────────────
[0.923] 退换货政策（产品说明.pdf · 第 23 页）
  "消费者在收到商品后 7 日内，如商品存在质量问题..."
  [查看上下文] [标记为优质]

[0.876] 退款流程（FAQ.xlsx · 第 5 行）
  "退款申请提交后，3-5 个工作日到账..."
```

**Tab 3 - 配置：**
- Embedding 模型选择
- 切分策略配置（切分方式、块大小、重叠度）
- 检索策略默认值

### 3.6 可观测性（`/tenant/observability`）

**时间范围选择器：** 最近 1h / 6h / 24h / 7d / 30d / 自定义

**多 Agent 切换下拉：** 全部 / 特定 Agent

**指标卡片（4 格）：**
```
[调用量] [成功率] [P50 延迟] [Token 消耗]
```

**趋势图：**
- 折线图：调用量、成功率（按小时聚合）
- 延迟分位图（P50/P75/P95 三线）
- 错误分类饼图

### 3.7 会话日志（`/tenant/sessions`）

**表格 + 筛选：**
- 时间范围、Agent、状态、评价（点赞/点踩/无评价）、关键词搜索

**会话详情页（`/tenant/sessions/:id`）：**

仿聊天界面展示完整对话，每条消息可展开查看 Trace 详情。

---

## 4. 公共组件设计

### 4.1 TraceViewer 组件

会话详情页和调试面板共用的 Trace 展示组件：

```tsx
// frontend/src/components/trace/TraceViewer.tsx

interface TraceViewerProps {
  steps: TraceStep[];
  expanded?: boolean;  // 默认展开还是折叠
}

export const TraceViewer: React.FC<TraceViewerProps> = ({ steps, expanded }) => {
  return (
    <div className="trace-viewer">
      {steps.map((step, index) => (
        <TraceStepItem key={step.span_id} step={step} index={index + 1} />
      ))}
      <div className="trace-summary">
        <span>总耗时: {totalLatency}ms</span>
        <span>Token: {totalTokens}</span>
        <span>估算成本: ¥{estimatedCost}</span>
      </div>
    </div>
  );
};

const TraceStepItem: React.FC<{ step: TraceStep; index: number }> = ({ step, index }) => {
  const [expanded, setExpanded] = useState(false);
  const icon = { llm_call: '🤖', tool_call: '🔧', skill_call: '⚡', knowledge_retrieval: '📚' }[step.span_type];

  return (
    <div className={`trace-step ${step.status}`} onClick={() => setExpanded(!expanded)}>
      <div className="step-header">
        <span className="step-number">① </span>
        <span className="step-icon">{icon}</span>
        <span className="step-name">{step.display_name}</span>
        <span className="step-latency">{step.latency_ms}ms</span>
        <StatusBadge status={step.status} />
      </div>
      {expanded && (
        <div className="step-detail">
          <div className="step-input">
            <label>输入</label>
            <pre>{JSON.stringify(step.input, null, 2)}</pre>
          </div>
          <div className="step-output">
            <label>输出</label>
            <pre>{JSON.stringify(step.output, null, 2)}</pre>
          </div>
        </div>
      )}
    </div>
  );
};
```

### 4.2 SSE 流式输出 Hook

```typescript
// frontend/src/hooks/useAgentStream.ts

export function useAgentStream() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [trace, setTrace] = useState<TraceStep[]>([]);
  const abortRef = useRef<AbortController | null>(null);

  const sendMessage = useCallback(async (agentId: string, text: string, sessionId?: string) => {
    abortRef.current = new AbortController();
    setStreaming(true);

    const userMessage: ChatMessage = { role: 'user', content: text };
    setMessages(prev => [...prev, userMessage]);

    const assistantMessage: ChatMessage = { role: 'assistant', content: '' };
    setMessages(prev => [...prev, assistantMessage]);

    try {
      const response = await fetch(`/api/v1/agents/${agentId}/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${getToken()}` },
        body: JSON.stringify({ message: text, session_id: sessionId, stream: true }),
        signal: abortRef.current.signal
      });

      const reader = response.body!.getReader();
      const decoder = new TextDecoder();

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const lines = decoder.decode(value).split('\n');
        for (const line of lines) {
          if (!line.startsWith('data: ')) continue;
          const event = JSON.parse(line.slice(6));

          if (event.type === 'text_delta') {
            setMessages(prev => {
              const updated = [...prev];
              updated[updated.length - 1].content += event.content;
              return updated;
            });
          } else if (event.type === 'tool_call' || event.type === 'tool_result') {
            setTrace(prev => [...prev, event]);
          } else if (event.type === 'done') {
            setStreaming(false);
          }
        }
      }
    } catch (e) {
      if ((e as Error).name !== 'AbortError') {
        // 显示错误提示
      }
    } finally {
      setStreaming(false);
    }
  }, []);

  const stopStream = useCallback(() => {
    abortRef.current?.abort();
    setStreaming(false);
  }, []);

  return { messages, trace, streaming, sendMessage, stopStream };
}
```

### 4.3 VersionHistory 组件

多处使用（Agent、Skill、Workflow 版本历史），封装为通用组件：

```tsx
// frontend/src/components/version/VersionHistory.tsx

interface VersionHistoryProps {
  resourceType: 'agent' | 'skill' | 'workflow';
  resourceId: string;
  currentVersion: string;
  onRollback: (version: string) => void;
}

export const VersionHistory: React.FC<VersionHistoryProps> = ({
  resourceType, resourceId, currentVersion, onRollback
}) => {
  const { data: versions, loading } = useVersionHistory(resourceType, resourceId);

  return (
    <Timeline>
      {versions?.map(v => (
        <Timeline.Item
          key={v.version}
          color={v.version === currentVersion ? 'green' : 'gray'}
        >
          <div className="version-item">
            <span className="version-tag">{v.version}</span>
            {v.version === currentVersion && <Tag color="green">当前版本</Tag>}
            <span className="version-time">{formatRelativeTime(v.published_at)}</span>
            <span className="version-user">{v.published_by_name}</span>
            <span className="version-note">{v.version_note}</span>
            {v.version !== currentVersion && (
              <Popconfirm
                title={`确认回滚到 ${v.version}？`}
                onConfirm={() => onRollback(v.version)}
              >
                <Button size="small">回滚到此版本</Button>
              </Popconfirm>
            )}
          </div>
        </Timeline.Item>
      ))}
    </Timeline>
  );
};
```

---

## 5. API 调用封装

### 5.1 agent-service API 请求封装

```typescript
// frontend/src/api/agentService.ts

import request from './request';   // 现有的 axios 封装

// LLM 模型
export const llmApi = {
  list: (params: LLMListParams) => request.get('/agent/v1/llm-models', { params }),
  create: (data: CreateLLMModel) => request.post('/agent/v1/llm-models', data),
  update: (id: string, data: UpdateLLMModel) => request.put(`/agent/v1/llm-models/${id}`, data),
  delete: (id: string) => request.delete(`/agent/v1/llm-models/${id}`),
  test: (id: string) => request.post(`/agent/v1/llm-models/${id}/test`),
};

// Agent
export const agentApi = {
  list: (params: AgentListParams) => request.get('/agent/v1/agents', { params }),
  create: (data: CreateAgent) => request.post('/agent/v1/agents', data),
  get: (id: string) => request.get(`/agent/v1/agents/${id}`),
  update: (id: string, data: UpdateAgent) => request.put(`/agent/v1/agents/${id}`, data),
  publish: (id: string, data: PublishRequest) => request.post(`/agent/v1/agents/${id}/publish`, data),
  rollback: (id: string, version: string) => request.post(`/agent/v1/agents/${id}/rollback`, { version }),
  versions: (id: string) => request.get(`/agent/v1/agents/${id}/versions`),
  debug: (id: string, data: DebugRequest) => request.post(`/agent/v1/agents/${id}/debug`, data),
};

// 可观测性
export const observabilityApi = {
  metrics: (agentId: string, params: MetricsParams) =>
    request.get(`/agent/v1/agents/${agentId}/metrics`, { params }),
  sessions: (params: SessionListParams) => request.get('/agent/v1/sessions', { params }),
  sessionDetail: (id: string) => request.get(`/agent/v1/sessions/${id}`),
};
```

### 5.2 Nginx 路由配置（agent-service 代理）

```nginx
# infrastructure/nginx/nginx.conf（新增）

location /api/agent/ {
    proxy_pass http://agent-service:8090/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;

    # SSE 支持
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 300s;
    chunked_transfer_encoding on;
}
```

---

## 6. 状态管理（Zustand Store）

### 6.1 Agent Store

```typescript
// frontend/src/store/agentStore.ts

import { create } from 'zustand';

interface AgentStore {
  agents: Agent[];
  currentAgent: Agent | null;
  draftConfig: AgentConfig | null;    // 未保存的草稿配置
  isDirty: boolean;                   // 是否有未保存修改

  fetchAgents: () => Promise<void>;
  setCurrentAgent: (agent: Agent) => void;
  updateDraftConfig: (patch: Partial<AgentConfig>) => void;
  saveDraft: () => Promise<void>;
  publishAgent: (versionNote: string) => Promise<void>;
}

export const useAgentStore = create<AgentStore>((set, get) => ({
  agents: [],
  currentAgent: null,
  draftConfig: null,
  isDirty: false,

  fetchAgents: async () => {
    const { data } = await agentApi.list({});
    set({ agents: data.items });
  },

  updateDraftConfig: (patch) => {
    set(state => ({
      draftConfig: state.draftConfig ? { ...state.draftConfig, ...patch } : patch as AgentConfig,
      isDirty: true
    }));
    // 自动保存（防抖 2 秒）
    debouncedSave();
  },

  saveDraft: async () => {
    const { currentAgent, draftConfig } = get();
    if (!currentAgent || !draftConfig) return;
    await agentApi.update(currentAgent.id, draftConfig);
    set({ isDirty: false });
  },
}));
```

---

## 7. 权限控制（前端）

前端根据 JWT 中的 `roles` 和 `is_platform_admin` 字段控制页面可见性：

```typescript
// frontend/src/utils/permissions.ts

export function canAccessPlatform(user: UserInfo): boolean {
  return user.is_platform_admin === true;
}

export function canEditAgent(user: UserInfo): boolean {
  return user.roles.includes('tenant_admin') || user.roles.includes('agent_developer');
}

export function canPublishAgent(user: UserInfo): boolean {
  return user.roles.includes('tenant_admin');
}
```

路由守卫：

```typescript
// frontend/src/router/guards.tsx

export const PlatformGuard: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const user = useCurrentUser();
  if (!canAccessPlatform(user)) {
    return <Navigate to="/403" replace />;
  }
  return <>{children}</>;
};
```

---

## 8. 响应式设计与主题

### 8.1 遵循现有 UI 规范

严格遵循 `CLAUDE.md` 中定义的 UI 规范：

- 页面背景：`#F8FAFC`
- 容器背景：`#FFFFFF`
- 主色：`#2563EB`
- 卡片圆角：`15px`，阴影：`0 4px 6px rgba(15, 23, 42, 0.08)`
- 表格页三段结构：工具栏 + 表格 + 操作栏
- 创建/编辑使用右侧 Drawer
- 危险操作使用 `danger` 样式 + 二次确认

### 8.2 工作流画布专用主题

工作流画布需要额外的样式定义（节点颜色、连线样式）：

```scss
// design-system/workflow-theme.scss

.workflow-node {
  border-radius: 8px;
  border: 2px solid #CBD5E1;
  background: #FFFFFF;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);

  &.selected {
    border-color: #2563EB;
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.2);
  }

  &.running { border-color: #3B82F6; }
  &.completed { border-color: #16A34A; }
  &.failed { border-color: #DC2626; }
  &.waiting_human { border-color: #EAB308; }
}
```

---

*文档版本：v1.0 | 最后更新：2026-03-18*
