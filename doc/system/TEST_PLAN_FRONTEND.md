# 前端测试设计方案 (Frontend Test Plan)

| 项目 | 内容 |
| :--- | :--- |
| 文档版本 | V1.1 |
| 关联文档 | PRD V1.1, FRONTEND_DESIGN V1.2 |
| 创建日期 | 2026-01-04 |
| 状态 | 正式版 |

## 1. 测试概述

### 1.1 测试目标
*   确保核心业务流程（登录、RBAC 权限控制）的正确性。
*   验证通用组件（如权限按钮、网络请求封装）的稳定性。
*   保障表单验证和交互逻辑符合 PRD 要求。
*   防止后续迭代引入回归缺陷。

### 1.2 技术选型
基于 Vite + React 技术栈，采用以下测试工具组合：
*   **测试框架**: **Vitest** (专为 Vite 设计，速度快，配置简单)。
*   **组件测试库**: **React Testing Library (RTL)** (关注用户行为，而非实现细节)。
*   **环境模拟**: **jsdom** (在 Node 环境模拟浏览器 DOM)。
*   **Mock 工具**: **Vitest Native Mocks** (用于 Mock API 请求、Zustand Store、React Router)。

---

## 2. 测试策略与范围

采用 **金字塔测试模型**，重点覆盖单元测试和组件/集成测试。

### 2.1 单元测试 (Unit Testing)
*   **范围**: 纯函数、工具类、Hooks、Zustand Store。
*   **重点**:
    *   `utils/request.ts`: 拦截器逻辑（Token 注入、401 刷新锁、错误处理、TraceId 记录）。
    *   `utils/storage.ts`: LocalStorage 的读写清除。
    *   `store/userStore.ts`: 登录、登出、权限设置的状态变更。
    *   `utils/tree-utils.ts` (若有): 权限树的扁平化/树形转换逻辑。

### 2.2 组件测试 (Component Testing)
*   **范围**: 通用组件、业务组件。
*   **重点**:
    *   `components/AuthButton`: 根据 Store 权限判断是否渲染子元素。
    *   `components/IconPicker`: 图标搜索与选择逻辑。
    *   **表单组件**: 验证规则（必填、正则）、提交时的 Loading 状态、防重复提交。

### 2.3 集成测试 (Integration Testing)
*   **范围**: 页面级交互、路由跳转。
*   **重点**:
    *   **登录流程**: 输入 -> 提交 -> API Mock -> Store 更新 -> 路由跳转。
    *   **权限守卫**: 未登录访问受保页面 -> 重定向至登录页；已登录但无权限 -> 403 提示或重定向。
    *   **动态菜单**: 登录后侧边栏菜单是否根据权限正确渲染。
    *   **列表页**: 数据加载 -> 渲染表格 -> 筛选查询 -> 分页切换。

---

## 3. 详细测试用例设计

### 3.1 基础设施层 (Infrastructure)

| 模块 | 测试点 | 预期结果 |
| :--- | :--- | :--- |
| **Request** | 请求拦截 | 请求头自动携带 `Authorization: Bearer {token}` |
| **Request** | 响应拦截 (200) | 直接返回 `data.data`，剥离外层结构 |
| **Request** | 响应拦截 (401) | 触发 Token 刷新流程，挂起并发请求，刷新成功后重试原请求 |
| **Request** | **并发 401** | 模拟 3 个并发请求同时返回 401，验证只调用一次 refresh 接口，且 3 个请求最终都成功重试 |
| **Request** | 响应拦截 (401 失败) | 刷新失败时清空 Store 并跳转登录页 |
| **Request** | 错误处理 | 验证 Error Message 中是否包含 TraceId (若后端返回) |
| **Store** | UserStore | `setToken` 正确更新 Store 和 LocalStorage |
| **Store** | UserStore | `logout` 清除所有状态和 LocalStorage |

### 3.2 认证模块 (Auth)

| 组件/页面 | 测试场景 | 操作步骤 | 预期结果 |
| :--- | :--- | :--- | :--- |
| **Login Page** | 表单验证 | 点击登录（空表单） | 显示“请输入用户名/密码”错误提示 |
| **Login Page** | 登录成功 | 输入正确账号密码 -> 点击登录 | 按钮 Loading -> 调用 Login API -> 跳转 Dashboard |
| **Login Page** | 登录失败 | Mock API 返回 500 | 按钮 Loading 结束 -> 显示错误 Message |
| **AuthGuard** | 路由守卫 | 无 Token 访问 `/dashboard` | 自动重定向至 `/login` |

### 3.3 通用组件 (Common Components)

| 组件 | 测试场景 | 前置条件 | 预期结果 |
| :--- | :--- | :--- | :--- |
| **AuthButton** | 有权限 | Store 包含 `user:add` | 渲染子组件 (Button) |
| **AuthButton** | 无权限 | Store 不含 `user:add` | 不渲染任何内容 (null) |
| **AuthButton** | 超级管理员 | Store 包含 `*:*:*` | 渲染子组件 |

### 3.4 业务模块 (Business Modules)

#### 用户管理 (User)
*   **列表渲染**: Mock API 返回用户列表，验证表格行数与内容匹配。
*   **状态切换**: 点击 Switch 组件，验证是否调用 `PUT /users/{id}/status` 接口。
*   **新增用户**:
    *   验证密码复杂度校验（必须包含字母数字）。
    *   验证提交时 Modal 不可关闭 (`maskClosable=false`)。
*   **编辑用户**: 验证编辑表单中不显示密码字段。
*   **删除用户**: 点击删除 -> 弹出 Popconfirm -> 确认 -> 调用 DELETE API。
*   **重置密码**: 验证重置密码 Modal 的弹出与 API 调用。
*   **分配角色**: 验证分配角色 Modal 正确加载角色列表并提交选中的 ID。

#### 角色管理 (Role)
*   **权限分配**:
    *   Mock 权限树数据。
    *   验证 Tree 组件的渲染。
    *   验证勾选父节点时，子节点的全选/半选逻辑（依赖 AntD Tree 行为，重点测试提交数据格式）。
*   **删除角色 (失败)**: Mock 后端返回“存在关联用户”错误，验证前端显示具体的错误提示信息。

#### 权限管理 (Permission)
*   **树形表格**: 验证表格是否以树形结构渲染（检查缩进或层级）。
*   **新增子节点**: 点击行内“新增子级”，验证弹窗中 `parentId` 是否自动回填。
*   **删除节点**: 验证删除操作的 API 调用。

#### 操作日志 (Log)
*   **列表渲染**: 验证日志列表正确展示操作人、模块、IP 等信息。
*   **筛选查询**: 输入筛选条件（如模块名），验证 API 请求参数是否包含该条件。
*   **详情查看**: 点击“详情”，验证 Drawer 弹出并展示 JSON 格式的请求参数/结果。

### 3.5 布局与菜单 (Layout & Menu)
*   **动态菜单**:
    *   Mock 用户权限列表。
    *   验证侧边栏菜单项是否根据权限列表进行过滤（例如：无 `role:list` 权限时不显示“角色管理”菜单）。
*   **用户信息**: 验证 Header 正确展示当前登录用户的昵称/头像。

---

## 4. 测试环境配置计划

### 4.1 安装依赖
```bash
npm install -D vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event
```

### 4.2 配置文件 (vite.config.ts)
```ts
/// <reference types="vitest" />
import { defineConfig } from 'vite'
// ...

export default defineConfig({
  // ... existing config
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: true, // 支持 CSS 模块解析
  },
})
```

### 4.3 Setup 文件 (src/test/setup.ts)
*   引入 `@testing-library/jest-dom` 扩展断言。
*   Mock `window.matchMedia` (AntD 组件依赖)。
*   Mock `localStorage`。

---

## 5. 执行计划

1.  **环境搭建**: 安装依赖，配置 Vitest。
2.  **基础测试**: 编写 `utils/request.test.ts` 和 `store/userStore.test.ts`。
3.  **组件测试**: 编写 `components/AuthButton.test.tsx`。
4.  **页面测试**: 编写 `pages/auth/Login.test.tsx`。
5.  **运行与回归**: 确保 `npm run test` 全通过。
