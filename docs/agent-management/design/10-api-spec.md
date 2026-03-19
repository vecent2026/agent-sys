# 智能体服务 Agent Service REST API 规范

**文档编号：** 10
**所属层次：** 技术层
**版本：** v1.0

---

## 目录

1. [通用规范](#1-通用规范)
2. [LLM 模型管理](#2-llm-模型管理)
3. [密钥管理](#3-密钥管理)
4. [知识库管理](#4-知识库管理)
5. [Skill 库](#5-skill-库)
6. [智能体管理](#6-智能体管理)
7. [工作流](#7-工作流)
8. [调试与测试](#8-调试与测试)
9. [对话 API](#9-对话-api)
10. [可观测性](#10-可观测性)
11. [成本分析](#11-成本分析)
12. [API Key 管理](#12-api-key-管理)

---

## 1. 通用规范

### 1.1 基础 URL

| 环境 | 地址 |
|------|------|
| 本地开发 | `http://localhost:8090` |
| 生产环境 | 经 Nginx 路由，前缀 `/api/agent/`，对外统一走 80 端口 |

### 1.2 认证

所有接口（除对话 API 使用 API Key 外）均要求在请求头携带 JWT Bearer Token。Token 由 admin-service（端口 8081）签发，agent-service 复用同一 JWT Secret 进行本地验证，无需跨服务调用。

```
Authorization: Bearer <jwt_token>
```

JWT Payload 中包含：

| 字段 | 类型 | 说明 |
|------|------|------|
| `sub` | string | 用户 ID |
| `tenant_id` | string | 租户 ID |
| `role` | string | 角色，`platform_admin` / `tenant_admin` / `tenant_user` |
| `exp` | number | 过期时间（Unix 时间戳） |

### 1.3 分页

分页查询统一使用 Query 参数：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | integer | 1 | 页码，从 1 开始 |
| `size` | integer | 20 | 每页条数，最大 100 |

分页响应结构：

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "total": 100,
    "page": 1,
    "size": 20,
    "items": [ ... ]
  }
}
```

### 1.4 统一响应格式

```json
{
  "code": 0,
  "message": "OK",
  "data": { }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | integer | 0 表示成功，非 0 表示失败 |
| `message` | string | 结果描述 |
| `data` | object / array / null | 成功时的业务数据 |

### 1.5 错误码体系

#### 通用错误码

| 错误码 | 说明 |
|--------|------|
| 40001 | 参数缺失 |
| 40002 | 参数格式错误 |
| 40003 | 未授权（Token 无效或已过期） |
| 40004 | 资源不存在 |
| 40005 | 无权限（已认证但权限不足） |

#### 业务错误码

| 错误码 | 说明 |
|--------|------|
| 50001 | 模型已被 Agent 引用，不可删除 |
| 50002 | 发布前检查未通过 |
| 50003 | 版本冲突（乐观锁冲突） |
| 50004 | Skill 版本不兼容（MAJOR 变更） |
| 50005 | 知识库索引未完成 |
| 50006 | API Key 已过期 |
| 50007 | 调用频率超限（Rate Limit） |

**业务错误码分段（按模块）**

| 模块 | 错误码范围 | 示例 |
|------|-----------|------|
| LLM 模型管理 | 51000–51099 | `51001 MODEL_NOT_FOUND`、`51002 MODEL_UNAVAILABLE`、`51003 MODEL_CIRCUIT_OPEN` |
| 密钥管理 | 51100–51199 | `51101 SECRET_DECRYPT_FAILED`、`51102 SECRET_QUOTA_EXCEEDED` |
| 知识库 | 51200–51399 | `51201 KB_NOT_FOUND`、`51202 DOC_PROCESS_FAILED`、`51203 EMBEDDING_FAILED` |
| Skill 库 | 51400–51599 | `51401 SKILL_NOT_FOUND`、`51402 SKILL_EXEC_FAILED`、`51403 SKILL_VERSION_INCOMPATIBLE` |
| 智能体 | 51600–51799 | `51601 AGENT_NOT_FOUND`、`51602 AGENT_PUBLISH_VALIDATION_FAILED`、`51603 AGENT_MAX_STEPS_EXCEEDED` |
| 工作流 | 51800–51999 | `51801 WORKFLOW_NOT_FOUND`、`51802 WORKFLOW_EXEC_TIMEOUT`、`51803 HUMAN_TASK_TIMEOUT` |
| 对话 / 调试 | 52000–52199 | `52001 SESSION_NOT_FOUND`、`52002 STREAM_INTERRUPTED`、`52003 CONTEXT_OVERFLOW` |
| 可观测性 | 52200–52399 | `52201 TRACE_NOT_FOUND` |
| API Key | 52400–52499 | `52401 APIKEY_INVALID`、`52402 APIKEY_REVOKED`、`52403 APIKEY_RATE_LIMITED` |

> 所有业务错误码都以 `{ "code": 51601, "message": "...", "data": null }` 格式返回，HTTP 状态码统一为 4xx/5xx（见通用规范）。

### 1.6 HTTP 状态码约定

| HTTP 状态码 | 含义 |
|-------------|------|
| 200 | 请求成功 |
| 400 | 请求参数有误 |
| 401 | 未认证 |
| 403 | 已认证但权限不足 |
| 404 | 资源不存在 |
| 429 | 请求频率超限 |
| 500 | 服务内部错误 |

---

## 2. LLM 模型管理

> **适用范围：** 平台端（`platform_admin`）负责 CRUD；租户端（`tenant_admin` / `tenant_user`）只读。

### 2.1 创建模型

**POST** `/api/models`

在平台全局注册一个 LLM 模型配置，关联密钥后租户可选用。

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 模型显示名称，如 `GPT-4o` |
| `provider` | string | 是 | 厂商，枚举：`openai` / `azure_openai` / `anthropic` / `zhipu` / `qwen` / `custom` |
| `model_id` | string | 是 | 厂商侧模型标识，如 `gpt-4o` |
| `api_key_id` | string | 是 | 关联的密钥配置 ID |
| `base_url` | string | 否 | 自定义 API 端点，`custom` 厂商时必填 |
| `context_window` | integer | 否 | 上下文窗口大小（token 数），默认 128000 |
| `max_output_tokens` | integer | 否 | 最大输出 token，默认 4096 |
| `supports_vision` | boolean | 否 | 是否支持图片输入，默认 false |
| `supports_function_call` | boolean | 否 | 是否支持 Function Call，默认 false |
| `description` | string | 否 | 模型描述 |

#### 请求示例

```http
POST /api/models
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "name": "GPT-4o",
  "provider": "openai",
  "model_id": "gpt-4o",
  "api_key_id": "key_01HX1234",
  "context_window": 128000,
  "max_output_tokens": 4096,
  "supports_vision": true,
  "supports_function_call": true,
  "description": "OpenAI GPT-4o 主力模型"
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "model_01HX5678",
    "name": "GPT-4o",
    "provider": "openai",
    "model_id": "gpt-4o",
    "api_key_id": "key_01HX1234",
    "base_url": null,
    "context_window": 128000,
    "max_output_tokens": 4096,
    "supports_vision": true,
    "supports_function_call": true,
    "description": "OpenAI GPT-4o 主力模型",
    "status": "active",
    "created_at": "2026-03-18T10:00:00Z",
    "updated_at": "2026-03-18T10:00:00Z"
  }
}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 40001 | `name` 或 `provider` 或 `model_id` 或 `api_key_id` 缺失 |
| 40002 | `provider` 枚举值不合法 |
| 40004 | `api_key_id` 对应的密钥不存在 |
| 40005 | 非 `platform_admin` 无权创建 |

---

### 2.2 更新模型

**PUT** `/api/models/{model_id}`

更新已有模型的配置信息，支持部分字段更新。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `model_id` | string | 模型 ID |

#### Body 参数

同创建接口，所有字段均为可选，仅传入需要修改的字段。

#### 请求示例

```http
PUT /api/models/model_01HX5678
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "max_output_tokens": 8192,
  "description": "更新最大输出 token 至 8192"
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "model_01HX5678",
    "name": "GPT-4o",
    "provider": "openai",
    "model_id": "gpt-4o",
    "max_output_tokens": 8192,
    "description": "更新最大输出 token 至 8192",
    "updated_at": "2026-03-18T11:00:00Z"
  }
}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 40004 | 模型不存在 |
| 40005 | 非 `platform_admin` 无权修改 |

---

### 2.3 删除模型

**DELETE** `/api/models/{model_id}`

从平台中删除模型配置。若有 Agent 正在引用该模型，则拒绝删除。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `model_id` | string | 模型 ID |

#### 请求示例

```http
DELETE /api/models/model_01HX5678
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": null
}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 40004 | 模型不存在 |
| 40005 | 非 `platform_admin` 无权删除 |
| 50001 | 模型已被 Agent 引用，不可删除 |

---

### 2.4 获取模型详情

**GET** `/api/models/{model_id}`

获取单个模型的完整配置信息。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `model_id` | string | 模型 ID |

#### 请求示例

```http
GET /api/models/model_01HX5678
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "model_01HX5678",
    "name": "GPT-4o",
    "provider": "openai",
    "model_id": "gpt-4o",
    "api_key_id": "key_01HX1234",
    "base_url": null,
    "context_window": 128000,
    "max_output_tokens": 8192,
    "supports_vision": true,
    "supports_function_call": true,
    "description": "更新最大输出 token 至 8192",
    "status": "active",
    "created_at": "2026-03-18T10:00:00Z",
    "updated_at": "2026-03-18T11:00:00Z"
  }
}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 40004 | 模型不存在 |

---

### 2.5 获取模型列表

**GET** `/api/models`

分页获取模型列表。平台端可见全部；租户端只见 `status=active` 的模型（用于 Agent 配置选择）。

> **可见性自动过滤**：API 根据当前 JWT 中的 `tenant_id` 自动过滤，仅返回以下条件之一成立的模型：① `visibility = 'PUBLIC'`；② `visibility = 'TENANT_WHITELIST'` 且当前租户在白名单内；③ 当前请求者为平台管理员（`role = 'PLATFORM_ADMIN'`，可查看所有模型）。前端无需额外过滤。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | integer | 否 | 页码，默认 1 |
| `size` | integer | 否 | 每页条数，默认 20 |
| `provider` | string | 否 | 按厂商筛选 |
| `status` | string | 否 | `active` / `inactive`，租户端强制为 `active` |
| `keyword` | string | 否 | 按模型名称模糊搜索 |

#### 请求示例

```http
GET /api/models?page=1&size=20&provider=openai
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "total": 3,
    "page": 1,
    "size": 20,
    "items": [
      {
        "id": "model_01HX5678",
        "name": "GPT-4o",
        "provider": "openai",
        "model_id": "gpt-4o",
        "context_window": 128000,
        "max_output_tokens": 8192,
        "supports_vision": true,
        "supports_function_call": true,
        "status": "active"
      }
    ]
  }
}
```

---

### 2.6 一键测试模型连通性

**POST** `/api/models/{model_id}/test`

向目标模型发送一条最小测试请求，验证密钥有效性与网络连通性。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `model_id` | string | 模型 ID |

#### 请求示例

```http
POST /api/models/model_01HX5678/test
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "success": true,
    "latency_ms": 342,
    "response_preview": "Hello! I'm GPT-4o.",
    "tested_at": "2026-03-18T11:05:00Z"
  }
}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 40004 | 模型不存在 |
| 40005 | 非 `platform_admin` 无权测试 |
| 50006 | 密钥已过期或无效 |

---

## 3. 密钥管理

> **适用范围：** 平台端（`platform_admin`）专用，响应体中永不返回明文 secret 值。

### 3.1 创建密钥

**POST** `/api/api-keys/provider`

注册一个 LLM 厂商的 API Key 配置，明文 secret 仅在创建时传入后加密存储。

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 密钥配置名称，便于识别 |
| `provider` | string | 是 | 厂商，与模型管理中枚举一致 |
| `secret` | string | 是 | API Key 明文（仅传入一次，之后不可查询） |
| `base_url` | string | 否 | 自定义 API 端点 |
| `description` | string | 否 | 备注说明 |

#### 请求示例

```http
POST /api/api-keys/provider
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "name": "OpenAI 主账号",
  "provider": "openai",
  "secret": "sk-proj-xxxxxxxxxxxxxxxxxxxx",
  "description": "生产环境主 API Key"
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "key_01HX1234",
    "name": "OpenAI 主账号",
    "provider": "openai",
    "secret_preview": "sk-proj-****xxxx",
    "base_url": null,
    "description": "生产环境主 API Key",
    "created_at": "2026-03-18T09:00:00Z"
  }
}
```

> `secret_preview` 仅展示首尾字符，完整明文不可再次获取。

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 40001 | `name` / `provider` / `secret` 缺失 |
| 40005 | 非 `platform_admin` 无权操作 |

---

### 3.2 更新密钥

**PUT** `/api/api-keys/provider/{key_id}`

更新密钥配置，如需更换 secret 则重新传入。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `key_id` | string | 密钥配置 ID |

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 否 | 新名称 |
| `secret` | string | 否 | 新的 API Key 明文（传入则覆盖旧值） |
| `base_url` | string | 否 | 新的 API 端点 |
| `description` | string | 否 | 新备注 |

#### 请求示例

```http
PUT /api/api-keys/provider/key_01HX1234
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "secret": "sk-proj-yyyyyyyyyyyyyyyyyyyy",
  "description": "已轮换 API Key"
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "key_01HX1234",
    "name": "OpenAI 主账号",
    "provider": "openai",
    "secret_preview": "sk-proj-****yyyy",
    "description": "已轮换 API Key",
    "updated_at": "2026-03-18T12:00:00Z"
  }
}
```

---

### 3.3 删除密钥

**DELETE** `/api/api-keys/provider/{key_id}`

删除密钥配置。若有模型正在引用该密钥，则拒绝删除。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `key_id` | string | 密钥配置 ID |

#### 请求示例

```http
DELETE /api/api-keys/provider/key_01HX1234
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": null
}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 40004 | 密钥不存在 |
| 50001 | 密钥已被模型引用，不可删除 |

---

### 3.4 获取密钥列表

**GET** `/api/api-keys/provider`

分页获取所有密钥配置，响应不包含明文 secret。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | integer | 否 | 页码，默认 1 |
| `size` | integer | 否 | 每页条数，默认 20 |
| `provider` | string | 否 | 按厂商筛选 |

#### 请求示例

```http
GET /api/api-keys/provider?provider=openai
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "total": 2,
    "page": 1,
    "size": 20,
    "items": [
      {
        "id": "key_01HX1234",
        "name": "OpenAI 主账号",
        "provider": "openai",
        "secret_preview": "sk-proj-****yyyy",
        "description": "已轮换 API Key",
        "created_at": "2026-03-18T09:00:00Z",
        "updated_at": "2026-03-18T12:00:00Z"
      }
    ]
  }
}
```

---

## 4. 知识库管理

> **适用范围：** 租户端，数据隔离到当前 `tenant_id`。

### 4.1 创建知识库

**POST** `/api/knowledge-bases`

创建一个新知识库，后续可上传文档构建向量索引。

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 知识库名称 |
| `description` | string | 否 | 描述 |
| `embedding_model_id` | string | 是 | 用于向量化的嵌入模型 ID |
| `chunk_size` | integer | 否 | 分块大小（token），默认 512 |
| `chunk_overlap` | integer | 否 | 分块重叠（token），默认 50 |

#### 请求示例

```http
POST /api/knowledge-bases
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "name": "产品手册知识库",
  "description": "包含所有产品使用文档",
  "embedding_model_id": "model_emb_01",
  "chunk_size": 512,
  "chunk_overlap": 50
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "kb_01HX9999",
    "name": "产品手册知识库",
    "description": "包含所有产品使用文档",
    "tenant_id": "tenant_abc",
    "embedding_model_id": "model_emb_01",
    "chunk_size": 512,
    "chunk_overlap": 50,
    "doc_count": 0,
    "status": "ready",
    "created_at": "2026-03-18T13:00:00Z"
  }
}
```

---

### 4.2 获取知识库列表

**GET** `/api/knowledge-bases`

分页获取当前租户的知识库列表。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | integer | 否 | 页码，默认 1 |
| `size` | integer | 否 | 每页条数，默认 20 |
| `keyword` | string | 否 | 按名称模糊搜索 |

#### 请求示例

```http
GET /api/knowledge-bases?page=1&size=20
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "total": 5,
    "page": 1,
    "size": 20,
    "items": [
      {
        "id": "kb_01HX9999",
        "name": "产品手册知识库",
        "doc_count": 12,
        "status": "ready",
        "created_at": "2026-03-18T13:00:00Z"
      }
    ]
  }
}
```

---

### 4.3 更新知识库

**PUT** `/api/knowledge-bases/{kb_id}`

更新知识库的基本信息（名称、描述）。嵌入模型与分块参数变更需重新索引，请先删除旧知识库再创建。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `kb_id` | string | 知识库 ID |

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 否 | 新名称 |
| `description` | string | 否 | 新描述 |

#### 请求示例

```http
PUT /api/knowledge-bases/kb_01HX9999
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "name": "产品手册知识库 V2"
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "kb_01HX9999",
    "name": "产品手册知识库 V2",
    "updated_at": "2026-03-18T14:00:00Z"
  }
}
```

---

### 4.4 删除知识库

**DELETE** `/api/knowledge-bases/{kb_id}`

删除知识库及其所有文档与向量索引。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `kb_id` | string | 知识库 ID |

#### 请求示例

```http
DELETE /api/knowledge-bases/kb_01HX9999
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": null
}
```

---

### 4.5 上传文档

**POST** `/api/knowledge-bases/{kb_id}/documents`

向知识库上传文档，支持 PDF / DOCX / TXT / Markdown。上传后异步执行解析与向量化，可通过查询处理状态接口轮询进度。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `kb_id` | string | 知识库 ID |

#### Body 参数（multipart/form-data）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | file | 是 | 文件本体，最大 50 MB |
| `title` | string | 否 | 文档标题，默认取文件名 |

#### 请求示例

```http
POST /api/knowledge-bases/kb_01HX9999/documents
Authorization: Bearer eyJhbGc...
Content-Type: multipart/form-data

--boundary
Content-Disposition: form-data; name="file"; filename="product_manual.pdf"
Content-Type: application/pdf

<binary content>
--boundary
Content-Disposition: form-data; name="title"

产品使用手册 2026
--boundary--
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "doc_id": "doc_01HXAAAA",
    "title": "产品使用手册 2026",
    "filename": "product_manual.pdf",
    "file_size": 1048576,
    "status": "processing",
    "created_at": "2026-03-18T14:30:00Z"
  }
}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 40002 | 文件格式不支持 |
| 40004 | 知识库不存在 |

---

### 4.6 查询文档处理状态

**GET** `/api/knowledge-bases/{kb_id}/documents/{doc_id}`

查询文档的解析与向量化进度。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `kb_id` | string | 知识库 ID |
| `doc_id` | string | 文档 ID |

#### 请求示例

```http
GET /api/knowledge-bases/kb_01HX9999/documents/doc_01HXAAAA
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "doc_id": "doc_01HXAAAA",
    "title": "产品使用手册 2026",
    "status": "completed",
    "chunk_count": 48,
    "error_message": null,
    "created_at": "2026-03-18T14:30:00Z",
    "completed_at": "2026-03-18T14:32:15Z"
  }
}
```

`status` 枚举值：`pending` / `processing` / `completed` / `failed`

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 50005 | 知识库索引尚未完成 |

---

### 4.7 获取文档列表

**GET** `/api/knowledge-bases/{kb_id}/documents`

分页获取知识库下所有文档。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `kb_id` | string | 知识库 ID |

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | integer | 否 | 页码，默认 1 |
| `size` | integer | 否 | 每页条数，默认 20 |
| `status` | string | 否 | 按处理状态筛选 |

#### 请求示例

```http
GET /api/knowledge-bases/kb_01HX9999/documents?page=1&size=20
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "total": 12,
    "page": 1,
    "size": 20,
    "items": [
      {
        "doc_id": "doc_01HXAAAA",
        "title": "产品使用手册 2026",
        "filename": "product_manual.pdf",
        "file_size": 1048576,
        "chunk_count": 48,
        "status": "completed",
        "created_at": "2026-03-18T14:30:00Z"
      }
    ]
  }
}
```

---

### 4.8 删除文档

**DELETE** `/api/knowledge-bases/{kb_id}/documents/{doc_id}`

从知识库中删除指定文档及其向量索引。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `kb_id` | string | 知识库 ID |
| `doc_id` | string | 文档 ID |

#### 请求示例

```http
DELETE /api/knowledge-bases/kb_01HX9999/documents/doc_01HXAAAA
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": null
}
```

---

### 4.9 检索测试

**POST** `/api/knowledge-bases/{kb_id}/retrieve`

对知识库执行语义检索，返回最相关的文档片段，用于验证知识库效果。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `kb_id` | string | 知识库 ID |

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `query` | string | 是 | 检索查询文本 |
| `top_k` | integer | 否 | 返回最相关片段数量，默认 5，最大 20 |
| `score_threshold` | float | 否 | 相似度阈值，低于此值不返回，默认 0.5 |

> **运行时参数覆盖**：请求体中的检索参数（`top_k`、`score_threshold`、`retrieval_strategy`）为可选项，若提供则覆盖知识库级别的默认配置；若不提供则使用知识库创建时设定的默认值。这一覆盖仅对本次检索有效，不修改知识库配置。

#### 请求示例

```http
POST /api/knowledge-bases/kb_01HX9999/retrieve
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "query": "如何重置密码",
  "top_k": 5,
  "score_threshold": 0.6
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "query": "如何重置密码",
    "results": [
      {
        "chunk_id": "chunk_001",
        "doc_id": "doc_01HXAAAA",
        "doc_title": "产品使用手册 2026",
        "content": "在账户设置页面点击「忘记密码」，输入注册邮箱后系统将发送重置链接...",
        "score": 0.92,
        "page": 15
      }
    ],
    "latency_ms": 87
  }
}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 50005 | 知识库索引未完成，无法检索 |

---

## 5. Skill 库

> **适用范围：** 租户端，Skill 为可复用的工具函数（Tool）封装。

### 5.1 创建 Skill

**POST** `/api/skills`

创建一个新 Skill 草稿，支持 HTTP API、代码执行、数据库查询等类型。

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | Skill 名称（英文，供 Agent 识别） |
| `display_name` | string | 是 | 展示名称（中文） |
| `type` | string | 是 | 类型：`http_api` / `code` / `db_query` |
| `description` | string | 是 | 功能描述，LLM 据此决定是否调用 |
| `input_schema` | object | 是 | JSON Schema 格式的入参定义 |
| `output_schema` | object | 否 | JSON Schema 格式的出参定义 |
| `config` | object | 是 | 类型相关配置，见下表 |

**`config` 字段（`http_api` 类型）：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `url` | string | 目标 API URL |
| `method` | string | HTTP 方法 |
| `headers` | object | 固定请求头 |
| `timeout_ms` | integer | 超时毫秒数，默认 5000 |

#### 请求示例

```http
POST /api/skills
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "name": "get_weather",
  "display_name": "获取天气",
  "type": "http_api",
  "description": "根据城市名称获取当前天气信息，包括温度、湿度、天气状况",
  "input_schema": {
    "type": "object",
    "properties": {
      "city": {
        "type": "string",
        "description": "城市名称，如「北京」"
      }
    },
    "required": ["city"]
  },
  "config": {
    "url": "https://api.weather.example.com/current",
    "method": "GET",
    "headers": {
      "X-API-Key": "weather_key_xxx"
    },
    "timeout_ms": 3000
  }
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "skill_01HXBBBB",
    "name": "get_weather",
    "display_name": "获取天气",
    "type": "http_api",
    "description": "根据城市名称获取当前天气信息，包括温度、湿度、天气状况",
    "status": "draft",
    "version": null,
    "tenant_id": "tenant_abc",
    "created_at": "2026-03-18T15:00:00Z"
  }
}
```

---

### 5.2 更新 Skill

**PUT** `/api/skills/{skill_id}`

更新 Skill 草稿内容。已发布的 Skill 不可直接编辑，需先克隆新版本。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `skill_id` | string | Skill ID |

#### Body 参数

同创建接口，所有字段均为可选。

#### 请求示例

```http
PUT /api/skills/skill_01HXBBBB
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "description": "根据城市名称获取当前天气信息，包括温度、湿度、天气状况及未来 3 日预报"
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "skill_01HXBBBB",
    "description": "根据城市名称获取当前天气信息，包括温度、湿度、天气状况及未来 3 日预报",
    "updated_at": "2026-03-18T15:30:00Z"
  }
}
```

---

### 5.3 删除 Skill

**DELETE** `/api/skills/{skill_id}`

删除 Skill。已被 Agent 引用的已发布版本不可删除。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `skill_id` | string | Skill ID |

#### 请求示例

```http
DELETE /api/skills/skill_01HXBBBB
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": null
}
```

---

### 5.4 获取 Skill 列表

**GET** `/api/skills`

分页获取当前租户的 Skill 列表。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | integer | 否 | 页码，默认 1 |
| `size` | integer | 否 | 每页条数，默认 20 |
| `type` | string | 否 | 按类型筛选 |
| `status` | string | 否 | `draft` / `published` |
| `keyword` | string | 否 | 按名称模糊搜索 |

#### 请求示例

```http
GET /api/skills?status=published&page=1&size=20
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "total": 8,
    "page": 1,
    "size": 20,
    "items": [
      {
        "id": "skill_01HXBBBB",
        "name": "get_weather",
        "display_name": "获取天气",
        "type": "http_api",
        "status": "published",
        "version": "1.0.0",
        "created_at": "2026-03-18T15:00:00Z"
      }
    ]
  }
}
```

---

### 5.5 发布 Skill

**POST** `/api/skills/{skill_id}/publish`

将 Skill 草稿发布为新版本，版本号遵循语义化版本（SemVer）。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `skill_id` | string | Skill ID |

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `version` | string | 是 | 目标版本号，如 `1.0.0`、`1.1.0`、`2.0.0` |
| `change_notes` | string | 否 | 变更说明 |

#### 请求示例

```http
POST /api/skills/skill_01HXBBBB/publish
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "version": "1.0.0",
  "change_notes": "首次发布"
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "skill_01HXBBBB",
    "name": "get_weather",
    "status": "published",
    "version": "1.0.0",
    "published_at": "2026-03-18T16:00:00Z"
  }
}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 50002 | 发布前检查未通过（如 `input_schema` 不合法） |
| 50004 | 版本号为 MAJOR 变更，有 Agent 引用时需确认兼容性 |

---

### 5.6 调用 Skill

**POST** `/api/skills/{skill_id}/invoke`

直接调用指定版本的 Skill，用于测试或外部集成调用。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `skill_id` | string | Skill ID |

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `version` | string | 否 | 调用指定版本，默认最新发布版本 |
| `inputs` | object | 是 | 入参对象，需符合 `input_schema` |

#### 请求示例

```http
POST /api/skills/skill_01HXBBBB/invoke
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "version": "1.0.0",
  "inputs": {
    "city": "北京"
  }
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "skill_id": "skill_01HXBBBB",
    "version": "1.0.0",
    "outputs": {
      "temperature": 18,
      "humidity": 55,
      "condition": "晴",
      "wind_speed": "3 级"
    },
    "latency_ms": 256,
    "invoked_at": "2026-03-18T16:10:00Z"
  }
}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 40002 | `inputs` 不符合 `input_schema` |
| 50007 | 调用频率超限 |

---

### 5.7 获取版本历史

**GET** `/api/skills/{skill_id}/versions`

获取 Skill 的所有发布版本记录。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `skill_id` | string | Skill ID |

#### 请求示例

```http
GET /api/skills/skill_01HXBBBB/versions
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": [
    {
      "version": "1.1.0",
      "change_notes": "新增未来 3 日预报",
      "published_by": "user_001",
      "published_at": "2026-03-18T17:00:00Z"
    },
    {
      "version": "1.0.0",
      "change_notes": "首次发布",
      "published_by": "user_001",
      "published_at": "2026-03-18T16:00:00Z"
    }
  ]
}
```

---

## 6. 智能体管理

> **适用范围：** 租户端，管理 Agent 的完整生命周期。

### 6.1 创建 Agent

**POST** `/api/agents`

创建一个新 Agent 草稿。

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | Agent 名称 |
| `description` | string | 否 | Agent 功能描述 |
| `model_id` | string | 是 | 使用的 LLM 模型 ID |
| `system_prompt` | string | 否 | 系统提示词 |
| `temperature` | float | 否 | 模型温度，0~2，默认 0.7 |
| `max_tokens` | integer | 否 | 单次最大输出 token，默认 2048 |
| `skill_ids` | array[string] | 否 | 关联的 Skill ID 列表 |
| `knowledge_base_ids` | array[string] | 否 | 关联的知识库 ID 列表 |
| `avatar_url` | string | 否 | Agent 头像 URL |

#### 请求示例

```http
POST /api/agents
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "name": "客服助手",
  "description": "处理用户咨询与售后问题的智能助手",
  "model_id": "model_01HX5678",
  "system_prompt": "你是一位专业的客服助手，请用友好、简洁的语言回答用户问题。",
  "temperature": 0.5,
  "max_tokens": 1024,
  "skill_ids": ["skill_01HXBBBB"],
  "knowledge_base_ids": ["kb_01HX9999"]
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "agent_01HXCCCC",
    "name": "客服助手",
    "description": "处理用户咨询与售后问题的智能助手",
    "model_id": "model_01HX5678",
    "status": "draft",
    "version": null,
    "tenant_id": "tenant_abc",
    "created_at": "2026-03-18T18:00:00Z"
  }
}
```

---

### 6.2 更新 Agent

**PUT** `/api/agents/{agent_id}`

更新 Agent 草稿配置。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `agent_id` | string | Agent ID |

#### Body 参数

同创建接口，所有字段均为可选。

#### 请求示例

```http
PUT /api/agents/agent_01HXCCCC
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "system_prompt": "你是一位专业的客服助手，请用友好、简洁的语言回答用户问题。遇到无法解决的问题，引导用户转接人工客服。",
  "temperature": 0.3
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "agent_01HXCCCC",
    "updated_at": "2026-03-18T18:30:00Z"
  }
}
```

---

### 6.3 删除 Agent

**DELETE** `/api/agents/{agent_id}`

删除 Agent。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `agent_id` | string | Agent ID |

#### 请求示例

```http
DELETE /api/agents/agent_01HXCCCC
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": null
}
```

---

### 6.4 获取 Agent 详情

**GET** `/api/agents/{agent_id}`

获取 Agent 完整配置。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `agent_id` | string | Agent ID |

#### 请求示例

```http
GET /api/agents/agent_01HXCCCC
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "agent_01HXCCCC",
    "name": "客服助手",
    "description": "处理用户咨询与售后问题的智能助手",
    "model_id": "model_01HX5678",
    "system_prompt": "你是一位专业的客服助手，请用友好、简洁的语言回答用户问题。遇到无法解决的问题，引导用户转接人工客服。",
    "temperature": 0.3,
    "max_tokens": 1024,
    "skill_ids": ["skill_01HXBBBB"],
    "knowledge_base_ids": ["kb_01HX9999"],
    "status": "published",
    "version": "1.0.0",
    "avatar_url": null,
    "created_at": "2026-03-18T18:00:00Z",
    "updated_at": "2026-03-18T18:30:00Z"
  }
}
```

---

### 6.5 获取 Agent 列表

**GET** `/api/agents`

分页获取当前租户的 Agent 列表。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | integer | 否 | 页码，默认 1 |
| `size` | integer | 否 | 每页条数，默认 20 |
| `status` | string | 否 | `draft` / `published` |
| `keyword` | string | 否 | 按名称模糊搜索 |

#### 请求示例

```http
GET /api/agents?page=1&size=20&status=published
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "total": 3,
    "page": 1,
    "size": 20,
    "items": [
      {
        "id": "agent_01HXCCCC",
        "name": "客服助手",
        "status": "published",
        "version": "1.0.0",
        "created_at": "2026-03-18T18:00:00Z"
      }
    ]
  }
}
```

---

### 6.6 发布 Agent

**POST** `/api/agents/{agent_id}/publish`

将 Agent 草稿发布为正式版本。发布前系统会自动进行合法性检查。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `agent_id` | string | Agent ID |

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `version` | string | 是 | 目标版本号，如 `1.0.0` |
| `release_notes` | string | 否 | 发版说明 |

#### 请求示例

```http
POST /api/agents/agent_01HXCCCC/publish
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "version": "1.0.0",
  "release_notes": "初始版本，支持天气查询与产品知识问答"
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "agent_01HXCCCC",
    "status": "published",
    "version": "1.0.0",
    "published_at": "2026-03-18T19:00:00Z"
  }
}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 50002 | 发布前检查未通过（如模型不可用、Skill 未发布） |

---

### 6.7 回滚 Agent

**POST** `/api/agents/{agent_id}/rollback`

将 Agent 回滚到指定历史版本，回滚后当前版本变为草稿。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `agent_id` | string | Agent ID |

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `target_version` | string | 是 | 目标历史版本号 |

#### 请求示例

```http
POST /api/agents/agent_01HXCCCC/rollback
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "target_version": "0.9.0"
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "agent_01HXCCCC",
    "status": "published",
    "version": "0.9.0",
    "rolled_back_at": "2026-03-18T20:00:00Z"
  }
}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 40004 | 目标版本不存在 |

---

### 6.8 获取 Agent 版本历史

**GET** `/api/agents/{agent_id}/versions`

获取 Agent 的所有发布版本记录。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `agent_id` | string | Agent ID |

#### 请求示例

```http
GET /api/agents/agent_01HXCCCC/versions
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": [
    {
      "version": "1.0.0",
      "release_notes": "初始版本，支持天气查询与产品知识问答",
      "published_by": "user_001",
      "published_at": "2026-03-18T19:00:00Z",
      "snapshot": {
        "model_id": "model_01HX5678",
        "skill_ids": ["skill_01HXBBBB"],
        "knowledge_base_ids": ["kb_01HX9999"]
      }
    }
  ]
}
```

---

## 7. 工作流

> **适用范围：** 租户端，Workflow 为多步骤 Agent 协作编排。

### 7.1 创建工作流

**POST** `/api/workflows`

创建一个新工作流草稿。

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 工作流名称 |
| `description` | string | 否 | 工作流描述 |
| `trigger_type` | string | 是 | 触发方式：`manual` / `schedule` / `webhook` |
| `cron_expression` | string | 否 | `trigger_type=schedule` 时必填，cron 表达式 |
| `nodes` | array | 是 | 节点列表，见节点结构 |
| `edges` | array | 是 | 边列表，描述节点间连接关系 |

**节点结构：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | 节点唯一 ID |
| `type` | string | `start` / `agent` / `skill` / `condition` / `end` |
| `label` | string | 节点名称 |
| `config` | object | 节点类型相关配置 |

#### 请求示例

```http
POST /api/workflows
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "name": "每日客服报告",
  "description": "每天 9 点汇总昨日会话数据并生成报告",
  "trigger_type": "schedule",
  "cron_expression": "0 9 * * *",
  "nodes": [
    { "id": "n1", "type": "start", "label": "开始", "config": {} },
    {
      "id": "n2",
      "type": "agent",
      "label": "数据汇总",
      "config": { "agent_id": "agent_01HXCCCC", "prompt_template": "汇总昨日 {{date}} 的会话数据" }
    },
    { "id": "n3", "type": "end", "label": "结束", "config": {} }
  ],
  "edges": [
    { "from": "n1", "to": "n2" },
    { "from": "n2", "to": "n3" }
  ]
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "wf_01HXDDDD",
    "name": "每日客服报告",
    "trigger_type": "schedule",
    "cron_expression": "0 9 * * *",
    "status": "draft",
    "tenant_id": "tenant_abc",
    "created_at": "2026-03-18T21:00:00Z"
  }
}
```

---

### 7.2 更新工作流

**PUT** `/api/workflows/{workflow_id}`

更新工作流草稿。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `workflow_id` | string | 工作流 ID |

#### Body 参数

同创建接口，所有字段均为可选。

#### 请求示例

```http
PUT /api/workflows/wf_01HXDDDD
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "cron_expression": "0 8 * * 1-5"
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "wf_01HXDDDD",
    "updated_at": "2026-03-18T21:30:00Z"
  }
}
```

---

### 7.3 删除工作流

**DELETE** `/api/workflows/{workflow_id}`

删除工作流，已发布且正在运行中的工作流需先停止。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `workflow_id` | string | 工作流 ID |

#### 请求示例

```http
DELETE /api/workflows/wf_01HXDDDD
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": null
}
```

---

### 7.4 获取工作流列表

**GET** `/api/workflows`

分页获取当前租户的工作流列表。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | integer | 否 | 页码，默认 1 |
| `size` | integer | 否 | 每页条数，默认 20 |
| `status` | string | 否 | `draft` / `published` / `running` |

#### 请求示例

```http
GET /api/workflows?page=1&size=20
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "total": 2,
    "page": 1,
    "size": 20,
    "items": [
      {
        "id": "wf_01HXDDDD",
        "name": "每日客服报告",
        "trigger_type": "schedule",
        "status": "published",
        "last_run_at": "2026-03-18T09:00:00Z"
      }
    ]
  }
}
```

---

### 7.5 发布工作流

**POST** `/api/workflows/{workflow_id}/publish`

发布工作流，发布后定时触发生效。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `workflow_id` | string | 工作流 ID |

#### 请求示例

```http
POST /api/workflows/wf_01HXDDDD/publish
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "wf_01HXDDDD",
    "status": "published",
    "published_at": "2026-03-18T22:00:00Z"
  }
}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 50002 | 发布前检查未通过（如节点引用的 Agent 未发布） |

---

### 7.6 手动触发工作流

**POST** `/api/workflows/{workflow_id}/trigger`

手动立即触发一次工作流执行。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `workflow_id` | string | 工作流 ID |

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `inputs` | object | 否 | 运行时输入变量 |

#### 请求示例

```http
POST /api/workflows/wf_01HXDDDD/trigger
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "inputs": {
    "date": "2026-03-17"
  }
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "execution_id": "exec_01HXEEEE",
    "workflow_id": "wf_01HXDDDD",
    "status": "running",
    "triggered_at": "2026-03-18T22:10:00Z"
  }
}
```

---

### 7.7 获取工作流执行历史

**GET** `/api/workflows/{workflow_id}/executions`

分页获取工作流的历史执行记录。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `workflow_id` | string | 工作流 ID |

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | integer | 否 | 页码，默认 1 |
| `size` | integer | 否 | 每页条数，默认 20 |
| `status` | string | 否 | `running` / `success` / `failed` |

#### 请求示例

```http
GET /api/workflows/wf_01HXDDDD/executions?page=1&size=10
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "total": 15,
    "page": 1,
    "size": 10,
    "items": [
      {
        "execution_id": "exec_01HXEEEE",
        "status": "success",
        "trigger_type": "manual",
        "duration_ms": 4230,
        "triggered_at": "2026-03-18T22:10:00Z",
        "completed_at": "2026-03-18T22:10:04Z"
      }
    ]
  }
}
```

---

## 8. 调试与测试

### 8.1 草稿调试（SSE 流式）

**POST** `/api/debug/stream`

对 Agent 或工作流草稿进行实时流式调试，通过 SSE（Server-Sent Events）返回中间推理步骤与最终结果。

> **权限要求**：仅 Agent 的编辑者（`role = 'TENANT_ADMIN'` 或 Agent 的创建者）可调用；使用草稿配置（`draft_config`）执行，不消耗已发布 Agent 的配额；产生的 Trace 标记为 `namespace=debug`。

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `target_type` | string | 是 | `agent` / `workflow` |
| `target_id` | string | 是 | 草稿 ID |
| `messages` | array | 是 | 消息历史，含当前用户输入 |
| `variables` | object | 否 | 运行时变量覆盖 |

**`messages` 元素结构：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `role` | string | `user` / `assistant` |
| `content` | string | 消息内容 |

#### 请求示例

```http
POST /api/debug/stream
Authorization: Bearer eyJhbGc...
Content-Type: application/json
Accept: text/event-stream

{
  "target_type": "agent",
  "target_id": "agent_01HXCCCC",
  "messages": [
    { "role": "user", "content": "北京今天天气怎么样？" }
  ]
}
```

#### 成功响应（SSE 流）

```
data: {"event":"thinking","content":"正在调用天气查询工具..."}

data: {"event":"tool_call","tool":"get_weather","inputs":{"city":"北京"}}

data: {"event":"tool_result","tool":"get_weather","outputs":{"temperature":18,"condition":"晴"}}

data: {"event":"delta","content":"北京今天天气晴朗，"}

data: {"event":"delta","content":"气温 18°C，"}

data: {"event":"delta","content":"适合外出。"}

data: {"event":"done","usage":{"prompt_tokens":120,"completion_tokens":35,"total_tokens":155}}
```

> Content-Type: `text/event-stream`，每行 `data:` 后跟 JSON 对象。

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 40004 | 草稿 Agent/Workflow 不存在 |

---

### 8.2 创建测试集

**POST** `/api/test-sets`

创建一个测试集，包含若干测试用例，用于批量评测 Agent 效果。

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 测试集名称 |
| `agent_id` | string | 是 | 关联的 Agent ID |
| `cases` | array | 是 | 测试用例列表 |

**`cases` 元素结构：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `input` | string | 用户输入 |
| `expected_output` | string | 期望输出（用于评分参考） |
| `tags` | array[string] | 标签，便于分组 |

#### 请求示例

```http
POST /api/test-sets
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "name": "客服问答测试集 v1",
  "agent_id": "agent_01HXCCCC",
  "cases": [
    {
      "input": "你们的退货政策是什么？",
      "expected_output": "我们支持 7 天无理由退货，商品需保持原包装。",
      "tags": ["售后", "退货"]
    },
    {
      "input": "如何联系人工客服？",
      "expected_output": "您可以通过工作日 9:00-18:00 拨打 400-xxx-xxxx 联系人工客服。",
      "tags": ["联系方式"]
    }
  ]
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "ts_01HXFFFF",
    "name": "客服问答测试集 v1",
    "agent_id": "agent_01HXCCCC",
    "case_count": 2,
    "created_at": "2026-03-18T23:00:00Z"
  }
}
```

---

### 8.3 执行批量测试

**POST** `/api/test-sets/{test_set_id}/run`

对测试集中所有用例异步执行批量测试，返回任务 ID。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `test_set_id` | string | 测试集 ID |

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `concurrency` | integer | 否 | 并发数，默认 3，最大 10 |

#### 请求示例

```http
POST /api/test-sets/ts_01HXFFFF/run
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "concurrency": 5
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "run_id": "run_01HXGGGG",
    "test_set_id": "ts_01HXFFFF",
    "status": "running",
    "total_cases": 2,
    "started_at": "2026-03-18T23:05:00Z"
  }
}
```

---

### 8.4 获取测试结果

**GET** `/api/test-sets/{test_set_id}/runs/{run_id}`

查询批量测试的执行进度与结果。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `test_set_id` | string | 测试集 ID |
| `run_id` | string | 测试运行 ID |

#### 请求示例

```http
GET /api/test-sets/ts_01HXFFFF/runs/run_01HXGGGG
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "run_id": "run_01HXGGGG",
    "status": "completed",
    "total_cases": 2,
    "passed": 2,
    "failed": 0,
    "avg_latency_ms": 512,
    "avg_score": 0.91,
    "started_at": "2026-03-18T23:05:00Z",
    "completed_at": "2026-03-18T23:05:03Z",
    "case_results": [
      {
        "case_index": 0,
        "input": "你们的退货政策是什么？",
        "actual_output": "我们支持 7 天无理由退货，请保持商品原包装完好。",
        "expected_output": "我们支持 7 天无理由退货，商品需保持原包装。",
        "score": 0.93,
        "latency_ms": 490,
        "tokens_used": 145
      },
      {
        "case_index": 1,
        "input": "如何联系人工客服？",
        "actual_output": "工作日 9:00-18:00，拨打 400-xxx-xxxx 可联系人工客服。",
        "expected_output": "您可以通过工作日 9:00-18:00 拨打 400-xxx-xxxx 联系人工客服。",
        "score": 0.89,
        "latency_ms": 534,
        "tokens_used": 132
      }
    ]
  }
}
```

---

## 9. 对话 API

> **适用范围：** 对外开放，使用 API Key 认证（见第 12 节），不使用 JWT。

### 9.1 单次对话（非流式）

**POST** `/api/v1/chat`

向指定 Agent 发送消息，同步等待完整响应。适合低延迟要求场景。

#### 请求头

```
Authorization: Bearer <api_key>
Content-Type: application/json
```

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `agent_id` | string | 是 | Agent ID |
| `session_id` | string | 否 | 会话 ID，传入则保持上下文；不传则新建会话 |
| `message` | string | 是 | 用户消息内容 |
| `metadata` | object | 否 | 透传的业务元数据，原样记录到日志 |

#### 请求示例

```http
POST /api/v1/chat
Authorization: Bearer sk-agent-xxxxxxxxxxxxxxxx
Content-Type: application/json

{
  "agent_id": "agent_01HXCCCC",
  "session_id": "sess_abc123",
  "message": "北京今天天气怎么样？",
  "metadata": {
    "user_id": "end_user_001",
    "channel": "web"
  }
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "session_id": "sess_abc123",
    "message_id": "msg_01HXHHHH",
    "content": "北京今天天气晴朗，气温 18°C，适合外出。",
    "usage": {
      "prompt_tokens": 120,
      "completion_tokens": 22,
      "total_tokens": 142
    },
    "latency_ms": 890,
    "created_at": "2026-03-18T23:30:00Z"
  }
}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 40003 | API Key 无效 |
| 50006 | API Key 已过期 |
| 50007 | 调用频率超限 |

---

### 9.2 流式对话（SSE）

**POST** `/api/v1/chat/stream`

向指定 Agent 发送消息，以 SSE 流式返回逐词输出。适合对话式 UI 场景。

#### 请求头

```
Authorization: Bearer <api_key>
Content-Type: application/json
Accept: text/event-stream
```

#### Body 参数

同非流式对话（9.1）。

#### 请求示例

```http
POST /api/v1/chat/stream
Authorization: Bearer sk-agent-xxxxxxxxxxxxxxxx
Content-Type: application/json
Accept: text/event-stream

{
  "agent_id": "agent_01HXCCCC",
  "session_id": "sess_abc123",
  "message": "帮我介绍一下你能做什么"
}
```

#### 成功响应（SSE 流）

```
data: {"event":"delta","message_id":"msg_01HXIIII","content":"我是客服助手，"}

data: {"event":"delta","message_id":"msg_01HXIIII","content":"可以帮您解答产品相关问题，"}

data: {"event":"delta","message_id":"msg_01HXIIII","content":"处理退货、查询物流等售后事宜。"}

data: {"event":"done","message_id":"msg_01HXIIII","usage":{"prompt_tokens":85,"completion_tokens":40,"total_tokens":125}}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 40003 | API Key 无效 |
| 50006 | API Key 已过期 |
| 50007 | 调用频率超限 |

---

## 10. 可观测性

> **适用范围：** 租户端，查询当前租户的运营数据。

### 10.1 获取指标概览

**GET** `/api/observability/metrics`

获取指定时间范围内的核心指标汇总，包括调用量、成功率、平均延迟、Token 消耗。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `start_date` | string | 是 | 起始日期，格式 `YYYY-MM-DD` |
| `end_date` | string | 是 | 结束日期，格式 `YYYY-MM-DD` |
| `agent_id` | string | 否 | 按 Agent 筛选，不传则汇总全租户 |
| `granularity` | string | 否 | 时间粒度：`day` / `hour`，默认 `day` |

#### 请求示例

```http
GET /api/observability/metrics?start_date=2026-03-11&end_date=2026-03-18&agent_id=agent_01HXCCCC
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "summary": {
      "total_calls": 1240,
      "success_rate": 0.987,
      "avg_latency_ms": 743,
      "total_tokens": 185600,
      "total_cost_usd": 3.71
    },
    "timeseries": [
      {
        "date": "2026-03-17",
        "calls": 180,
        "success_rate": 0.994,
        "avg_latency_ms": 710,
        "tokens": 26000
      },
      {
        "date": "2026-03-18",
        "calls": 200,
        "success_rate": 0.98,
        "avg_latency_ms": 770,
        "tokens": 29400
      }
    ]
  }
}
```

---

### 10.2 获取会话列表

**GET** `/api/observability/sessions`

分页获取会话记录列表，支持按 Agent、时间范围筛选。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | integer | 否 | 页码，默认 1 |
| `size` | integer | 否 | 每页条数，默认 20 |
| `agent_id` | string | 否 | 按 Agent 筛选 |
| `start_date` | string | 否 | 起始日期 |
| `end_date` | string | 否 | 结束日期 |
| `has_feedback` | boolean | 否 | 筛选有用户评价的会话 |

#### 请求示例

```http
GET /api/observability/sessions?agent_id=agent_01HXCCCC&page=1&size=20
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "total": 350,
    "page": 1,
    "size": 20,
    "items": [
      {
        "session_id": "sess_abc123",
        "agent_id": "agent_01HXCCCC",
        "message_count": 4,
        "total_tokens": 620,
        "avg_latency_ms": 830,
        "user_rating": 5,
        "started_at": "2026-03-18T23:30:00Z",
        "last_message_at": "2026-03-18T23:35:00Z"
      }
    ]
  }
}
```

---

### 10.3 获取会话详情

**GET** `/api/observability/sessions/{session_id}`

获取单个会话的完整消息记录与工具调用链路。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `session_id` | string | 会话 ID |

#### 请求示例

```http
GET /api/observability/sessions/sess_abc123
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "session_id": "sess_abc123",
    "agent_id": "agent_01HXCCCC",
    "messages": [
      {
        "message_id": "msg_01HXHHHH",
        "role": "user",
        "content": "北京今天天气怎么样？",
        "created_at": "2026-03-18T23:30:00Z"
      },
      {
        "message_id": "msg_01HXIIII",
        "role": "assistant",
        "content": "北京今天天气晴朗，气温 18°C，适合外出。",
        "tool_calls": [
          {
            "tool": "get_weather",
            "inputs": { "city": "北京" },
            "outputs": { "temperature": 18, "condition": "晴" },
            "latency_ms": 256
          }
        ],
        "usage": { "prompt_tokens": 120, "completion_tokens": 22, "total_tokens": 142 },
        "latency_ms": 890,
        "created_at": "2026-03-18T23:30:01Z"
      }
    ],
    "user_rating": 5,
    "user_feedback": "回答准确，非常满意"
  }
}
```

---

### 10.4 提交用户评价

**POST** `/api/observability/sessions/{session_id}/feedback`

终端用户对某次会话提交满意度评价。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `session_id` | string | 会话 ID |

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `rating` | integer | 是 | 评分，1~5 星 |
| `feedback` | string | 否 | 文字评价 |
| `message_id` | string | 否 | 针对具体消息的评价，不传则对整个会话评价 |

#### 请求示例

```http
POST /api/observability/sessions/sess_abc123/feedback
Authorization: Bearer sk-agent-xxxxxxxxxxxxxxxx
Content-Type: application/json

{
  "rating": 5,
  "feedback": "回答准确，非常满意"
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "session_id": "sess_abc123",
    "rating": 5,
    "submitted_at": "2026-03-18T23:40:00Z"
  }
}
```

---

### 10.5 查询 Trace 详情

**GET** `/api/observability/traces/{trace_id}`

返回完整调用树，包含 Session → Turn → AgentRun → LLM Call / Tool Call 的层级结构。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `trace_id` | string | Trace ID |

#### 请求示例

```http
GET /api/observability/traces/trace_xxx
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 200,
  "data": {
    "trace_id": "trace_xxx",
    "namespace": "production",
    "session_id": "session_xxx",
    "agent_id": "agent_xxx",
    "total_duration_ms": 2340,
    "total_tokens": 1024,
    "turns": [
      {
        "turn_id": "turn_xxx",
        "user_input": "...",
        "agent_output": "...",
        "agent_runs": [
          {
            "run_id": "run_xxx",
            "step": 1,
            "thought": "...",
            "action": "tool_call",
            "llm_calls": [],
            "tool_calls": []
          }
        ]
      }
    ]
  }
}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 52201 | Trace 不存在 |

---

## 11. 成本分析

> **适用范围：** 租户端，分析 Token 消耗与费用。

### 11.1 获取成本概览

**GET** `/api/cost/overview`

获取指定时间范围内的 Token 消耗与费用汇总。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `start_date` | string | 是 | 起始日期，格式 `YYYY-MM-DD` |
| `end_date` | string | 是 | 结束日期，格式 `YYYY-MM-DD` |

#### 请求示例

```http
GET /api/cost/overview?start_date=2026-03-01&end_date=2026-03-18
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "period": {
      "start_date": "2026-03-01",
      "end_date": "2026-03-18"
    },
    "total_tokens": 2350000,
    "prompt_tokens": 1820000,
    "completion_tokens": 530000,
    "total_cost_usd": 47.00,
    "by_agent": [
      {
        "agent_id": "agent_01HXCCCC",
        "agent_name": "客服助手",
        "tokens": 1800000,
        "cost_usd": 36.00
      },
      {
        "agent_id": "agent_02HXDDDD",
        "agent_name": "数据分析助手",
        "tokens": 550000,
        "cost_usd": 11.00
      }
    ],
    "by_model": [
      {
        "model_id": "model_01HX5678",
        "model_name": "GPT-4o",
        "tokens": 2350000,
        "cost_usd": 47.00
      }
    ]
  }
}
```

---

### 11.2 获取每日明细

**GET** `/api/cost/daily`

按天获取 Token 消耗与费用明细，支持按 Agent 或模型分组。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `start_date` | string | 是 | 起始日期，格式 `YYYY-MM-DD` |
| `end_date` | string | 是 | 结束日期，格式 `YYYY-MM-DD` |
| `group_by` | string | 否 | 分组维度：`agent` / `model`，默认不分组 |
| `agent_id` | string | 否 | 按 Agent 筛选 |

#### 请求示例

```http
GET /api/cost/daily?start_date=2026-03-16&end_date=2026-03-18&group_by=agent
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": [
    {
      "date": "2026-03-18",
      "total_tokens": 145000,
      "total_cost_usd": 2.90,
      "breakdown": [
        {
          "agent_id": "agent_01HXCCCC",
          "agent_name": "客服助手",
          "tokens": 110000,
          "cost_usd": 2.20
        },
        {
          "agent_id": "agent_02HXDDDD",
          "agent_name": "数据分析助手",
          "tokens": 35000,
          "cost_usd": 0.70
        }
      ]
    },
    {
      "date": "2026-03-17",
      "total_tokens": 132000,
      "total_cost_usd": 2.64,
      "breakdown": []
    }
  ]
}
```

---

## 12. API Key 管理

> **适用范围：** 租户端，管理对外开放接口（第 9 节）使用的 API Key。

### 12.1 创建 API Key

**POST** `/api/agent-api-keys`

为当前租户创建一个新的 API Key，用于调用对话 API。

#### Body 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | Key 名称，便于识别用途 |
| `agent_id` | string | 否 | 绑定到指定 Agent；不传则可调用租户下所有 Agent |
| `expires_at` | string | 否 | 过期时间，ISO 8601 格式；不传则永不过期 |
| `rate_limit_per_minute` | integer | 否 | 每分钟调用上限，默认 60 |

#### 请求示例

```http
POST /api/agent-api-keys
Authorization: Bearer eyJhbGc...
Content-Type: application/json

{
  "name": "官网客服接入",
  "agent_id": "agent_01HXCCCC",
  "expires_at": "2027-03-18T00:00:00Z",
  "rate_limit_per_minute": 100
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "ak_01HXJJJJ",
    "name": "官网客服接入",
    "key": "sk-agent-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "agent_id": "agent_01HXCCCC",
    "expires_at": "2027-03-18T00:00:00Z",
    "rate_limit_per_minute": 100,
    "created_at": "2026-03-18T23:50:00Z"
  }
}
```

> `key` 字段的完整明文**仅在创建时返回一次**，之后无法再次查看，请妥善保存。

---

### 12.2 获取 API Key 列表

**GET** `/api/agent-api-keys`

分页获取当前租户的 API Key 列表，响应不含明文 key 值。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | integer | 否 | 页码，默认 1 |
| `size` | integer | 否 | 每页条数，默认 20 |
| `agent_id` | string | 否 | 按绑定 Agent 筛选 |
| `status` | string | 否 | `active` / `expired` / `revoked` |

#### 请求示例

```http
GET /api/agent-api-keys?page=1&size=20
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "total": 3,
    "page": 1,
    "size": 20,
    "items": [
      {
        "id": "ak_01HXJJJJ",
        "name": "官网客服接入",
        "key_preview": "sk-agent-****xxxx",
        "agent_id": "agent_01HXCCCC",
        "status": "active",
        "rate_limit_per_minute": 100,
        "last_used_at": "2026-03-18T23:55:00Z",
        "expires_at": "2027-03-18T00:00:00Z",
        "created_at": "2026-03-18T23:50:00Z"
      }
    ]
  }
}
```

---

### 12.3 撤销 API Key

**POST** `/api/agent-api-keys/{key_id}/revoke`

立即撤销指定 API Key，撤销后使用该 Key 的调用立即失效。

#### Path 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `key_id` | string | API Key ID |

#### 请求示例

```http
POST /api/agent-api-keys/ak_01HXJJJJ/revoke
Authorization: Bearer eyJhbGc...
```

#### 成功响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "id": "ak_01HXJJJJ",
    "status": "revoked",
    "revoked_at": "2026-03-18T23:59:00Z"
  }
}
```

#### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 40004 | API Key 不存在 |
| 40005 | 无权操作（Key 属于其他租户） |

---

## 附录：接口汇总

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| LLM 模型 | POST | `/api/models` | 创建模型 |
| LLM 模型 | PUT | `/api/models/{model_id}` | 更新模型 |
| LLM 模型 | DELETE | `/api/models/{model_id}` | 删除模型 |
| LLM 模型 | GET | `/api/models/{model_id}` | 获取模型详情 |
| LLM 模型 | GET | `/api/models` | 获取模型列表 |
| LLM 模型 | POST | `/api/models/{model_id}/test` | 测试模型连通性 |
| 密钥管理 | POST | `/api/api-keys/provider` | 创建密钥 |
| 密钥管理 | PUT | `/api/api-keys/provider/{key_id}` | 更新密钥 |
| 密钥管理 | DELETE | `/api/api-keys/provider/{key_id}` | 删除密钥 |
| 密钥管理 | GET | `/api/api-keys/provider` | 获取密钥列表 |
| 知识库 | POST | `/api/knowledge-bases` | 创建知识库 |
| 知识库 | GET | `/api/knowledge-bases` | 获取知识库列表 |
| 知识库 | PUT | `/api/knowledge-bases/{kb_id}` | 更新知识库 |
| 知识库 | DELETE | `/api/knowledge-bases/{kb_id}` | 删除知识库 |
| 知识库 | POST | `/api/knowledge-bases/{kb_id}/documents` | 上传文档 |
| 知识库 | GET | `/api/knowledge-bases/{kb_id}/documents/{doc_id}` | 查询文档状态 |
| 知识库 | GET | `/api/knowledge-bases/{kb_id}/documents` | 获取文档列表 |
| 知识库 | DELETE | `/api/knowledge-bases/{kb_id}/documents/{doc_id}` | 删除文档 |
| 知识库 | POST | `/api/knowledge-bases/{kb_id}/retrieve` | 检索测试 |
| Skill | POST | `/api/skills` | 创建 Skill |
| Skill | PUT | `/api/skills/{skill_id}` | 更新 Skill |
| Skill | DELETE | `/api/skills/{skill_id}` | 删除 Skill |
| Skill | GET | `/api/skills` | 获取 Skill 列表 |
| Skill | POST | `/api/skills/{skill_id}/publish` | 发布 Skill |
| Skill | POST | `/api/skills/{skill_id}/invoke` | 调用 Skill |
| Skill | GET | `/api/skills/{skill_id}/versions` | 获取版本历史 |
| 智能体 | POST | `/api/agents` | 创建 Agent |
| 智能体 | PUT | `/api/agents/{agent_id}` | 更新 Agent |
| 智能体 | DELETE | `/api/agents/{agent_id}` | 删除 Agent |
| 智能体 | GET | `/api/agents/{agent_id}` | 获取 Agent 详情 |
| 智能体 | GET | `/api/agents` | 获取 Agent 列表 |
| 智能体 | POST | `/api/agents/{agent_id}/publish` | 发布 Agent |
| 智能体 | POST | `/api/agents/{agent_id}/rollback` | 回滚 Agent |
| 智能体 | GET | `/api/agents/{agent_id}/versions` | 获取版本历史 |
| 工作流 | POST | `/api/workflows` | 创建工作流 |
| 工作流 | PUT | `/api/workflows/{workflow_id}` | 更新工作流 |
| 工作流 | DELETE | `/api/workflows/{workflow_id}` | 删除工作流 |
| 工作流 | GET | `/api/workflows` | 获取工作流列表 |
| 工作流 | POST | `/api/workflows/{workflow_id}/publish` | 发布工作流 |
| 工作流 | POST | `/api/workflows/{workflow_id}/trigger` | 手动触发 |
| 工作流 | GET | `/api/workflows/{workflow_id}/executions` | 获取执行历史 |
| 调试测试 | POST | `/api/debug/stream` | 草稿调试（SSE） |
| 调试测试 | POST | `/api/test-sets` | 创建测试集 |
| 调试测试 | POST | `/api/test-sets/{test_set_id}/run` | 执行批量测试 |
| 调试测试 | GET | `/api/test-sets/{test_set_id}/runs/{run_id}` | 获取测试结果 |
| 对话 API | POST | `/api/v1/chat` | 单次对话（非流式） |
| 对话 API | POST | `/api/v1/chat/stream` | 流式对话（SSE） |
| 可观测性 | GET | `/api/observability/metrics` | 获取指标概览 |
| 可观测性 | GET | `/api/observability/sessions` | 获取会话列表 |
| 可观测性 | GET | `/api/observability/sessions/{session_id}` | 获取会话详情 |
| 可观测性 | POST | `/api/observability/sessions/{session_id}/feedback` | 提交用户评价 |
| 成本分析 | GET | `/api/cost/overview` | 获取成本概览 |
| 成本分析 | GET | `/api/cost/daily` | 获取每日明细 |
| API Key | POST | `/api/agent-api-keys` | 创建 API Key |
| API Key | GET | `/api/agent-api-keys` | 获取 API Key 列表 |
| API Key | POST | `/api/agent-api-keys/{key_id}/revoke` | 撤销 API Key |

---

文档版本：v1.0 | 最后更新：2026-03-18
