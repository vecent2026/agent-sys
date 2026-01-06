# 前端架构设计文档 (Frontend Design)

| 项目 | 内容 |
| :--- | :--- |
| 文档版本 | V1.5 |
| 关联 PRD | V1.2 |
| 关联架构 | V1.2 |
| 最后更新 | 2026-01-06 |
| 状态 | 修订版 |

## 1. 引言

### 1.1 文档修订记录
| 版本 | 日期 | 修改人 | 说明 |
| :--- | :--- | :--- | :--- |
| V1.2 | 2026-01-04 | Frontend Lead | 初始版本创建 |
| V1.3 | 2026-01-05 | Frontend Lead | 1. 统一修正 API 路径前缀为 `/api`；2. 完善日志模块设计，适配 Elasticsearch 实现；3. 更新文档元信息。 |
| V1.4 | 2026-01-06 | Frontend Developer | 1. 明确区分 401 和 403 错误处理逻辑：401 清除认证信息跳转登录页，403 仅提示无权限保留认证状态；2. 增强 App.tsx 中 restoreUserState 函数的健壮性；3. 更新代理配置端口。 |
| V1.5 | 2026-01-06 | Frontend Developer | 1. 权限节点管理模块添加日志记录开关配置；


## 2. 技术选型与环境

基于《系统架构设计文档》的约束，前端采用以下技术栈：

### 2.1 核心框架
*   **构建工具**: Vite 5.x
*   **UI 框架**: React 18
*   **组件库**: Ant Design 6.0
*   **路由管理**: React Router 6 (Data Router 模式)
*   **状态管理**: 
    *   **全局状态**: Zustand (轻量级，用于 UserInfo, Token, Theme)
    *   **服务端状态**: React Query (TanStack Query v5) (用于 API 数据缓存与同步)
*   **HTTP 客户端**: Axios (统一拦截器封装)
*   **工具库**: 
    *   `dayjs` (时间处理)
    *   `lodash-es` (工具函数)
    *   `clsx` / `tailwind-merge` (样式类名处理)

### 2.2 开发环境
*   **Node.js**: >= 18.0.0
*   **包管理器**: pnpm (推荐) 或 npm
*   **代码规范**: ESLint + Prettier + Stylelint

---

## 3. 项目目录结构

```text
src/
├── api/                # API 接口定义 (按模块划分: auth, user, role, perm, log)
├── assets/             # 静态资源 (Images, Icons)
├── components/         # 通用组件
│   ├── AuthButton/     # 权限按钮组件
│   ├── PageContainer/  # 页面容器组件
│   ├── IconPicker/     # 图标选择器
│   └── ...
├── config/             # 全局配置 (Enums, Constants)
├── hooks/              # 自定义 Hooks (useAuth, useTable, etc.)
├── layouts/            # 布局组件 (BasicLayout, LoginLayout)
├── pages/              # 页面视图
│   ├── auth/           # 登录页
│   ├── system/         # 系统管理模块
│   │   ├── user/       # 用户管理
│   │   ├── role/       # 角色管理
│   │   ├── perm/       # 权限管理
│   │   └── log/        # 操作日志
│   └── dashboard/      # 首页/仪表盘
├── router/             # 路由配置 (静态路由 + 动态路由加载器)
├── store/              # Zustand Store (userStore, appStore)
├── styles/             # 全局样式
├── types/              # TypeScript 类型定义
├── utils/              # 工具函数 (request, storage, tree-utils)
└── App.tsx             # 根组件
```

---

## 4. 核心架构设计

### 4.1 路由与权限控制 (Routing & Auth)

#### 4.1.1 动态路由策略
采用 **“后端驱动”** 模式。前端仅硬编码基础路由（Login, 404），业务路由根据用户权限动态生成。

1.  **登录阶段**: 获取 `Access Token`。
2.  **初始化阶段**: 
    *   仅当存在有效 `accessToken` 时才尝试恢复用户状态
    *   调用 `/api/auth/me` 获取用户信息及权限列表 (`permissions: string[]`)。
    *   调用 `/api/auth/menus` (可选) 或根据权限列表过滤本地路由配置表。
    *   若 token 无效或获取用户信息失败，清除无效认证信息，跳转到登录页面
3.  **路由注册**: 使用 React Router 的 `createBrowserRouter` 动态更新路由表。

#### 4.1.2 按钮级权限
封装 `<AuthButton>` 组件，实现细粒度的操作控制。

```tsx
// 使用示例
<AuthButton perm="user:add">
  <Button type="primary">新增用户</Button>
</AuthButton>
```

**实现逻辑**:
*   从 Global Store 获取当前用户的权限集合 `Set<string>`。
*   判断 `props.perm` 是否存在于集合中（或包含 `*:*:*` 超级权限）。
*   若有权限则渲染子组件，否则渲染 `null` (或禁用状态)。

### 4.2 网络层封装 (Network Layer)

基于 Axios 进行二次封装，统一处理认证与异常。

#### 4.2.1 请求拦截器
*   自动读取 LocalStorage 中的 `accessToken`。
*   添加到 Header: `Authorization: Bearer {token}`。

#### 4.2.2 响应拦截器与并发刷新
*   **200 OK**: 直接返回 `data.data` (解包)。
*   **401 Unauthorized**: 
    *   **含义**: 身份认证失败 - 没有提供有效的认证信息或认证已过期
    *   **处理逻辑**:
        *   **并发锁机制**: 
            *   设置 `isRefreshing = true`。
            *   后续并发请求进入 `failedQueue` 队列挂起。
        *   **刷新流程**:
            *   调用 `/api/auth/refresh` 接口。
            *   **成功**: 更新 Store 与 LocalStorage，重试 `failedQueue` 中的所有请求，释放锁。
            *   **失败**: 清空 Store，跳转 `/login`，拒绝所有挂起请求。
*   **403 Forbidden**: 
    *   **含义**: 权限不足 - 已认证成功，但没有访问资源的权限
    *   **处理逻辑**:
        *   显示友好提示“当前账号暂无此操作权限”
        *   **重要**: 保留用户认证状态，不清除认证信息
        *   不强制跳转，允许用户继续使用系统其他功能
        *   仅通过提示告知用户无权限执行该操作
*   **业务错误 (code != 200)**: 
    *   全局 Message 提示 `data.message`。
    *   **TraceId 处理**: 尝试从响应头 (`X-Trace-Id`) 或响应体 (`data.traceId`) 中获取 TraceId，并在错误提示中展示（如 "系统错误，TraceId: xxxxx"），便于排查问题。

### 4.3 状态管理 (State Management)

#### 4.3.1 Global Store (Zustand)
*   **UserStore**: 
    *   `userInfo`: { id, username, nickname, avatar }
    *   `permissions`: string[] (权限标识集合)
    *   `token`: { access, refresh }
    *   `actions`: login, logout, setUserInfo
*   **AppStore**:
    *   `theme`: 'light' | 'dark'
    *   `collapsed`: boolean (侧边栏状态)

#### 4.3.2 Server State (React Query)
*   用于列表数据、详情数据的获取与缓存。
*   配置 `staleTime` 避免频繁请求。
*   利用 `invalidateQueries` 在增删改后自动刷新列表。

---

## 5. 业务模块设计与 API 映射

### 5.1 登录模块 (Auth)
*   **页面**: `/login`
*   **核心组件**: `LoginForm`
*   **交互逻辑**:
    *   表单校验通过后，按钮进入 `loading` 状态。
    *   调用 Login API，成功后存储 Token，跳转 Dashboard。
    *   失败则重置 `loading` 并提示错误。

**[API 映射]**
| 动作 | 方法 | 接口路径 | 参数/备注 |
| :--- | :--- | :--- | :--- |
| 用户登录 | POST | `/api/auth/login` | Body: `{username, password}` |
| 获取用户信息 | GET | `/api/auth/me` | Header: Token |
| 刷新 Token | POST | `/api/auth/refresh` | Body: `{refreshToken}` |
| 退出登录 | POST | `/api/auth/logout` | - |

### 5.2 系统布局 (Layout)
*   **结构**: 经典的 Admin Layout (Sider + Header + Content)。
*   **Sider**: 
    *   根据权限动态渲染菜单树。
    *   支持折叠/展开。
*   **Header**:
    *   面包屑导航 (Breadcrumb)。
    *   用户头像下拉菜单 (个人中心、退出登录)。
*   **Content**:
    *   `<Outlet />` 渲染子路由。
    *   ErrorBoundary 全局错误捕获。

### 5.3 用户管理 (User)
*   **路径**: `/system/user`
*   **组件**:
    *   **SearchForm**: 搜索栏 (用户名、手机号、状态)。
    *   **UserTable**: 表格展示。
        *   **排序**: 默认按 `last_login_time` (最后登录时间) 倒序排列。
        *   **列**: 用户名, 昵称, 手机, 状态, 创建时间, 最后登录时间, 操作。
    *   **UserModal**: 新增/编辑弹窗。
    *   **ResetPwdModal**: 重置密码弹窗。
*   **交互**:
    *   **状态切换**: Switch 组件直接调用 API 更新状态，成功后刷新列表。
    *   **删除**: Popconfirm 二次确认。
    *   **重置密码**: 管理员点击“重置密码”，弹出 Modal 输入新密码（或确认重置为默认），调用 `/api/users/{id}/password` 接口。
    *   **分配角色**: 独立的 Modal，展示角色列表（多选）。

**[API 映射]**
| 动作 | 方法 | 接口路径 | 参数/备注 |
| :--- | :--- | :--- | :--- |
| 查询用户列表 | GET | `/api/users` | Query: `page, size, username, mobile, status` |
| 新增用户 | POST | `/api/users` | Body: `UserForm` |
| 修改用户 | PUT | `/api/users/{id}` | Body: `UserForm` |
| 删除用户 | DELETE | `/api/users/{ids}` | Path: `ids` (逗号分隔) |
| 重置密码 | PUT | `/api/users/{id}/password` | Body: `{password}` |
| 修改状态 | PUT | `/api/users/{id}/status` | Body: `{status}` |
| 获取用户角色 | GET | `/api/users/{id}/roles` | 返回角色 ID 列表 |
| 分配角色 | POST | `/api/users/{id}/roles` | Body: `{roleIds: []}` |

### 5.4 角色管理 (Role)
*   **路径**: `/system/role`
*   **组件**:
    *   **RoleTable**: 角色列表。
        *   **排序**: 支持按“授权人数”排序 (需后端 API 支持)。
    *   **RoleModal**: 基础信息编辑。
    *   **PermDrawer/Modal**: 分配权限。
*   **权限分配交互**:
    *   使用 AntD `Tree` 组件展示权限树。
    *   支持 `checkable`，回显已拥有的权限 ID。
    *   **半选状态处理**: 提交时需包含半选父节点 ID（视后端逻辑而定，通常后端需要全量 ID）。

**[API 映射]**
| 动作 | 方法 | 接口路径 | 参数/备注 |
| :--- | :--- | :--- | :--- |
| 查询角色列表 | GET | `/api/roles` | Query: `page, size, roleName` |
| 新增角色 | POST | `/api/roles` | Body: `RoleForm` |
| 修改角色 | PUT | `/api/roles/{id}` | Body: `RoleForm` |
| 删除角色 | DELETE | `/api/roles/{ids}` | Path: `ids` |
| 获取角色权限 | GET | `/api/roles/{id}/permissions` | 返回权限 ID 列表 |
| 分配权限 | POST | `/api/roles/{id}/permissions` | Body: `{permissionIds: []}` |

### 5.5 权限管理 (Permission)
*   **路径**: `/system/perm`
*   **组件**:
    *   **PermTreeTable**: 树形表格展示。
    *   **IconPicker**: 图标选择器组件，支持搜索 AntD Icons，返回图标字符串标识 (e.g., "UserOutlined")。
*   **交互**:
    *   **新增子项**: 在行操作栏提供“新增子级”按钮，自动填入 `parentId`。
    *   **图标选择**: 在新增/编辑菜单节点时，通过 IconPicker 选择图标。
    *   **日志记录开关**: 在新增/编辑按钮类型节点时，添加日志记录开关配置，默认开启。

**[API 映射]**
| 动作 | 方法 | 接口路径 | 参数/备注 |
| :--- | :--- | :--- | :--- |
| 查询权限树 | GET | `/api/permissions/tree` | 返回树形结构，包含 `logEnabled` 字段 |
| 新增权限 | POST | `/api/permissions` | Body: `PermForm`，包含 `logEnabled` 字段 |
| 修改权限 | PUT | `/api/permissions/{id}` | Body: `PermForm`，包含 `logEnabled` 字段 |
| 删除权限 | DELETE | `/api/permissions/{id}` | Path: `id` |

**[类型定义示例]**
```typescript
export interface Permission {
  id: number;
  parentId: number | null;
  name: string;
  type: 'DIR' | 'MENU' | 'BTN';
  permissionKey: string;
  path?: string;
  component?: string;
  icon?: string;
  sort: number;
  logEnabled: boolean; // 是否开启日志记录
  children?: Permission[];
}

export interface PermForm {
  parentId?: number;
  name: string;
  type: 'DIR' | 'MENU' | 'BTN';
  permissionKey: string;
  path?: string;
  component?: string;
  icon?: string;
  sort: number;
  logEnabled?: boolean; // 是否开启日志记录，默认true
}
```

### 5.6 操作日志 (Log)
*   **路径**: `/system/log`
*   **组件**:
    *   **SearchForm**: 筛选栏。
        *   字段: 操作人 (Input), 操作模块 (Input), 操作事件 (Input), 状态 (Select: 成功/失败), 时间范围 (RangePicker)。
    *   **LogTable**: 纯展示表格。
        *   **列**: TraceId, 操作人, 模块, 事件, IP, 状态, 耗时, 时间, 操作。
    *   **DetailDrawer**: 点击查看请求参数与响应结果的 JSON 详情。
        *   **新增展示**: TraceId (链路追踪ID), ErrorMsg (异常堆栈信息)。
*   **特性**:
    *   不支持新增/修改/删除（审计数据的不可篡改性）。
    *   默认按时间倒序展示。

**[API 映射]**
| 动作 | 方法 | 接口路径 | 参数/备注 |
| :--- | :--- | :--- | :--- |
| 查询日志列表 | GET | `/api/logs` | Query: `page, size, username, module, status, startTime, endTime` <br> **Response**: `List<LogVO>` (注意 id 为 string, status 为 string) |

**[类型定义示例]**
```typescript
export interface LogVO {
  id: string;          // ES UUID
  traceId: string;     // 链路追踪ID
  userId: number;
  username: string;
  module: string;
  action: string;
  ip: string;
  status: string;      // 后端返回 "SUCCESS" / "FAIL" 或 "1"/"0"
  costTime: number;
  createTime: string;
  params?: string;     // 详情字段
  result?: string;     // 详情字段
  errorMsg?: string;   // 异常堆栈信息
}
```

---

## 6. 表单验证规则 (Validation Rules)

基于 PRD 字段约束，前端需实现以下验证规则：

### 6.1 用户表单
*   **用户名**: 必填，4-20字符，正则 `/^[a-zA-Z0-9_]+$/` (仅字母数字下划线)。
*   **密码**: 新增必填/编辑选填，6-20字符，需包含字母和数字。
*   **昵称**: 必填，2-20字符。
*   **手机号**: 选填，11位手机号正则。
*   **邮箱**: 选填，标准邮箱正则。

### 6.2 角色表单
*   **角色名称**: 必填，2-20字符。
*   **角色标识**: 必填，2-50字符，建议英文。
*   **描述**: 选填，最大200字符。

### 6.3 权限表单
*   **节点名称**: 必填，2-20字符。
*   **权限标识**: 仅按钮类型必填，格式建议 `module:action`。
*   **路由地址**: 菜单必填，`/` 开头。
*   **排序号**: 必填，整数。
*   **日志记录**: 选填，布尔值，默认true。

---

## 7. API 接口定义规范

前端 API 定义应与后端 Controller 路径保持一致，建议统一放在 `src/api` 目录下。

### 7.1 命名规范
*   **路径**: `/api/{module}/{resource}`
*   **方法**: RESTful (GET, POST, PUT, DELETE)

### 7.2 示例结构
```ts
// src/api/user.ts

// 查询用户列表
export const getUserList = (params: UserQueryParams) => 
  request.get<PageResult<UserVO>>('/api/users', { params });

// 新增用户
export const createUser = (data: UserForm) => 
  request.post('/api/users', data);

// 修改用户
export const updateUser = (id: number, data: UserForm) => 
  request.put(`/api/users/${id}`, data);

// 删除用户
export const deleteUser = (ids: number[]) => 
  request.delete(`/api/users/${ids.join(',')}`); // 或 request.delete('/api/users', { data: ids })

// 重置密码
export const resetUserPassword = (id: number, password?: string) =>
  request.put(`/api/users/${id}/password`, { password });
```

---

## 8. UI/UX 规范

### 8.1 样式主题
*   基于 Ant Design 6.0 默认主题。
*   **主色调**: 品牌蓝 (`#1677ff`)。
*   **布局间距**: 
    *   Content Padding: `24px`
    *   Card Gap: `16px`

### 8.2 交互反馈
*   **加载中**: Table `loading` 属性，全局 Suspense Fallback。
*   **防重复提交**: 
    *   所有表单提交按钮在请求期间必须进入 `loading` 状态。
    *   Modal 弹窗在提交期间禁止关闭 (`maskClosable={false}`, `closable={false}`)。
*   **成功**: `message.success('操作成功')`。
*   **失败**: `message.error(errorMsg)`。
*   **危险操作**: 必须使用 `Modal.confirm` 或 `Popconfirm` 进行二次确认。

---

## 9. 部署与构建

### 9.1 构建配置 (vite.config.ts)
*   配置 `@` 别名指向 `src`。
*   配置 Proxy 代理解决开发环境跨域：
    ```ts
    server: {
      proxy: {
        '/api': {
          target: 'http://localhost:8081',
          changeOrigin: true
        }
      }
    }
    ```

### 9.2 Nginx 部署
*   SPA 应用需配置 `try_files` 防止 404。
    ```nginx
    location / {
        root   /usr/share/nginx/html;
        index  index.html index.htm;
        try_files $uri $uri/ /index.html;
    }
    ```
