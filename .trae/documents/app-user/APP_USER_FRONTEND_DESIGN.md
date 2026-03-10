# 用户中心（user-service）模块前端设计方案

## 1. 设计概述

本设计方案基于 `APP_USER_PRD.md` 需求文档，旨在实现前台用户（C端用户）的管理界面，包括用户列表、标签管理、自定义字段管理等。技术栈沿用项目现有的 React + Vite + Ant Design + Zustand 架构。

## 2. 目录结构

代码将位于 `frontend/src` 目录下，新增 `app-user` 模块。

```
frontend/src
├── api
│   └── app-user            // API 接口定义
│       ├── index.ts        // 统一导出
│       ├── user.ts         // 用户管理接口
│       ├── tag.ts          // 标签管理接口
│       └── field.ts        // 字段管理接口
├── pages
│   └── app-user            // 页面视图
│       ├── UserList        // 用户列表页
│       │   ├── index.tsx
│       │   ├── components
│       │   │   ├── SearchForm.tsx
│       │   │   ├── UserTable.tsx
│       │   │   └── BatchAction.tsx
│       ├── UserDetail      // 用户详情页
│       │   ├── index.tsx
│       │   ├── components
│       │   │   ├── BaseInfo.tsx
│       │   │   ├── TagInfo.tsx
│       │   │   └── FieldInfo.tsx
│       ├── TagManagement   // 标签管理页
│       │   ├── index.tsx
│       │   ├── components
│       │   │   ├── TagCategoryList.tsx
│       │   │   ├── TagList.tsx
│       │   │   └── TagModal.tsx
│       └── FieldManagement // 字段管理页
│           ├── index.tsx
│           ├── components
│           │   ├── FieldList.tsx
│           │   └── FieldModal.tsx
├── store
│   └── app-user            // 状态管理
│       ├── useUserStore.ts // 用户列表状态
│       └── useTagStore.ts  // 标签状态
└── router
    └── modules
        └── app-user.tsx    // 路由配置
```

## 3. 路由设计

在 `router/modules/app-user.tsx` 中配置路由：

```typescript
export const appUserRoutes = [
  {
    path: '/app-user',
    element: <Layout />,
    children: [
      {
        path: 'list',
        element: <UserList />,
        meta: { title: '用户列表', permission: 'app:user:list' }
      },
      {
        path: 'detail/:id',
        element: <UserDetail />,
        meta: { title: '用户详情', permission: 'app:user:view' }
      },
      {
        path: 'tags',
        element: <TagManagement />,
        meta: { title: '标签管理', permission: 'app:tag:list' }
      },
      {
        path: 'fields',
        element: <FieldManagement />,
        meta: { title: '字段管理', permission: 'app:field:list' }
      }
    ]
  }
];
```

## 4. 页面组件设计

### 4.1 用户列表页 (UserList)
*   **SearchForm**: 支持昵称/手机号、注册来源、状态、标签（多选）、注册时间范围筛选；**筛选栏右侧提供「导出」按钮，根据当前筛选条件导出用户数据**。
*   **UserTable**: 展示用户基本信息、标签（Tag组件展示前3个）、操作列（查看详情、分配标签）。
*   **BatchAction**: 列表下方批量操作栏，支持批量打标签、批量移除标签。
*   **用户新增/编辑**: 使用 **Drawer（抽屉）组件** 进行用户信息的新增和编辑操作，提供更好的用户体验和更大的操作空间。

### 4.2 用户详情页 (UserDetail)
*   **BaseInfo**: 展示用户头像、昵称、手机号、邮箱、注册来源等基础信息。
*   **TagInfo**: 展示用户已关联的标签，支持添加/移除标签。
*   **FieldInfo**: 动态渲染自定义字段信息。
    *   根据 `field_type` 渲染不同组件（文本、单选、多选、链接）。
    *   支持编辑自定义字段值。

### 4.3 标签管理页 (TagManagement)
*   **TagCategoryList**: 左侧展示标签分类列表，支持新增/编辑/删除分类。
    *   新增"全部"分类选项，点击可查看所有类型的标签
    *   分类列表中显示每个分类的标签数量
    *   分类文字前加上当前分类的颜色圆形色块
    *   分类项添加更多图标，点击后显示编辑、删除按钮
*   **TagList**: 右侧展示选中分类下的标签列表，支持新增/编辑/删除标签。
    *   标签名称列宽度优化为200px，避免标签名称过长覆盖其他字段
    *   当选择"全部"分类时，在新增标签按钮后面增加标签名称搜索功能
*   **TagModal**: 标签编辑弹窗，不再支持选择颜色（颜色由分类决定）。
*   **CategoryModal**: 分类编辑弹窗，支持选择颜色（圆形颜色块选择器）。
    *   提供12种预设颜色选择
    *   选中状态显示白色勾勾，提高视觉辨识度

### 4.4 字段管理页 (FieldManagement)
*   **FieldList**: 展示字段列表，支持拖拽排序（使用 `dnd-kit` 或 `react-beautiful-dnd`）。
*   **FieldModal**: 字段编辑弹窗。
    *   **字段类型选择**: RADIO / CHECKBOX / TEXT / LINK。
    *   **配置项动态渲染**:
        *   RADIO/CHECKBOX: 动态添加选项（Label/Value）。
        *   TEXT: 配置最大长度、正则校验。
        *   LINK: 配置链接类型提示。
    *   **默认字段限制**: 若为默认字段，禁用类型选择和标识修改，仅允许修改排序。
*   **字段排序规则**:
    *   昵称、头像固定排序号为 1、2，始终排在最前面，不可拖拽排序
    *   其他默认字段排序号从 101 开始
    *   新增自定义字段时，系统自动计算排序号 = 当前最大排序号 + 1
    *   列表按排序号升序展示字段

## 5. 状态管理 (Zustand)

### 5.1 useUserStore
*   `userList`: 用户列表数据
*   `loading`: 加载状态
*   `pagination`: 分页信息
*   `fetchUserList`: 获取用户列表 action
*   `updateUserStatus`: 更新用户状态 action

### 5.2 useTagStore
*   `categories`: 标签分类列表
*   `tags`: 当前分类下的标签列表
*   `fetchCategories`: 获取分类列表 action
*   `fetchTags`: 获取标签列表 action

## 6. API 接口定义

使用 `axios` 封装请求，定义在 `api/app-user` 目录下。

```typescript
// api/app-user/user.ts
export const getUserList = (params: UserQueryParams) => {
  return request.get('/app-users', { params });
};

export const getUserDetail = (id: number) => {
  return request.get(`/app-users/${id}`);
};

export const batchTagUser = (data: BatchTagParams) => {
  return request.post('/app-users/batch-tags', data);
};

export const batchRemoveTagUser = (data: BatchTagParams) => {
  return request.delete('/app-users/batch-tags', { data });
};
```

## 7. 关键交互逻辑

### 7.1 动态表单渲染
在用户详情页和字段管理页，需根据 `field_type` 动态渲染表单组件。
*   **RADIO**: 使用 `<Radio.Group>`
*   **CHECKBOX**: 使用 `<Checkbox.Group>`
*   **TEXT**: 使用 `<Input>` 或 `<Input.TextArea>`
*   **LINK**: 使用 `<Input>` 并提供跳转链接预览

### 7.2 拖拽排序
在字段管理页，使用拖拽库实现字段排序功能。
*   拖拽结束时，获取新的排序列表，调用后端 `PUT /api/v1/user-fields/sort` 接口更新排序。

### 7.3 标签颜色选择
在创建/编辑标签时，提供预设的 12 种颜色供选择，与 Ant Design 的 Tag 组件颜色保持一致。
