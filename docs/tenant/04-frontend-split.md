# 04 前端拆分方案

## 1. 总体策略：同仓库双入口（Vite MPA）

不新建仓库，在现有 `frontend/` 目录内通过 Vite **多页面** 方案构建两套独立入口，共享组件库和工具函数，但路由、状态、权限完全隔离。

```
frontend/
├── src/
│   ├── platform/              ← 平台端（新增）
│   │   ├── main.tsx
│   │   ├── router/
│   │   ├── store/
│   │   ├── pages/
│   │   │   ├── login/
│   │   │   ├── tenants/       ← 租户管理（新增）
│   │   │   ├── users/         ← 平台用户管理（新增）
│   │   │   ├── roles/         ← 平台角色管理（新增）
│   │   │   ├── permissions/   ← 权限节点管理（新增）
│   │   │   └── logs/          ← 平台操作日志（新增）
│   │   └── layout/
│   │
│   ├── tenant/                ← 租户端（现有代码重组）
│   │   ├── main.tsx
│   │   ├── router/
│   │   ├── store/
│   │   ├── pages/
│   │   │   ├── login/         ← 含多租户选择步骤（内嵌，不跳转独立页）
│   │   │   ├── dashboard/
│   │   │   ├── members/       ← 成员管理（新增）
│   │   │   ├── app-users/     ← 用户中心 > 用户管理（现有 app-user/user，迁移）
│   │   │   ├── tags/          ← 用户中心 > 标签管理（现有 app-user/tag，迁移）
│   │   │   ├── field-def/     ← 用户中心 > 字段管理（现有 app-user/field，迁移）
│   │   │   ├── roles/         ← 角色管理（现有 system/role，迁移改造）
│   │   │   └── logs/          ← 操作日志（现有）
│   │   └── layout/
│   │
│   └── shared/                ← 共享资源（从现有 src/ 提取）
│       ├── components/        ← 通用 UI 组件
│       ├── hooks/
│       ├── utils/             ← request.ts, storage.ts 等
│       ├── types/
│       └── styles/
│
├── platform.html              ← 平台端入口 HTML（新增）
├── index.html                 ← 租户端入口 HTML（保持）
└── vite.config.ts
```

> **关于 design-system 目录**：项目根目录下存在 `/design-system/`（仅含设计规范文档 MASTER.md），实际 React 组件库在 `frontend/src/design-system/`。建议：将根目录的 `/design-system/` 移入 `docs/design-system/`，与其他设计文档统一管理；代码组件库保持现有位置不动。根目录单列一个只含文档的目录容易与代码混淆。

---

## 2. Vite 多入口配置

```typescript
// vite.config.ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@shared': resolve(__dirname, 'src/shared'),
      '@platform': resolve(__dirname, 'src/platform'),
      '@tenant': resolve(__dirname, 'src/tenant'),
    },
  },
  build: {
    rollupOptions: {
      input: {
        main:     resolve(__dirname, 'index.html'),        // 租户端
        platform: resolve(__dirname, 'platform.html'),     // 平台端
      },
      output: {
        // 按入口名分目录输出，避免资源冲突
        chunkFileNames: 'assets/[name]-[hash].js',
      },
    },
  },
  server: {
    proxy: { /* 保持现有代理配置 */ }
  }
})
```

---

## 3. 登录流程详细交互

### 3.1 平台端登录（`/platform/login`）

**页面行为**：

```
访问 /platform/login
  ├── 已有有效 platform_token？
  │     └── 是 → 直接跳转 /platform/tenants
  └── 否 → 展示平台登录表单

表单字段：
  - 用户名（必填）
  - 密码（必填）
  - [登录] 按钮

点击登录：
  1. 前端表单校验（非空、格式）
  2. Loading 状态：按钮禁用 + Spin
  3. 调用 POST /api/platform/auth/login
  4a. 成功 → 存储 platform_token（与租户 token 用不同 key 隔离）
           → 跳转 /platform/tenants
  4b. 失败 → 展示错误信息（账号或密码错误 / 账号已禁用）
           → 密码框清空，焦点回到密码框
```

**Token 存储 Key 隔离**：

```typescript
// src/shared/utils/storage.ts
export const STORAGE_KEYS = {
  // 租户端
  ACCESS_TOKEN:  'access_token',
  REFRESH_TOKEN: 'refresh_token',
  USER_INFO:     'user_info',

  // 平台端（独立 key，防止互相覆盖）
  PLATFORM_ACCESS_TOKEN:  'platform_access_token',
  PLATFORM_REFRESH_TOKEN: 'platform_refresh_token',
  PLATFORM_USER_INFO:     'platform_user_info',
} as const;
```

---

### 3.2 租户端登录（`/login`）

**表单校验规则**：

| 字段 | 校验 | 错误提示 |
|------|------|---------|
| 手机号 | 非空；11 位纯数字 | 请输入正确的手机号 |
| 密码 | 非空；长度 6~20 位 | 密码长度为 6-20 位 |

> 登录仅支持手机号 + 密码，不支持邮箱登录。邮箱为可选联系信息，不作为登录凭据。

**完整状态机（含页面内租户选择步骤）**：

```
┌─────────────────────────────────────────────────────────────────┐
│                     LoginPage 状态机                              │
│                                                                 │
│  step='credentials'                                             │
│  ┌─────────────────────────────────┐                            │
│  │  手机号 ___________________     │                            │
│  │  密  码 ___________________     │                            │
│  │           [登录]                │                            │
│  └─────────────────────────────────┘                            │
│           │ 提交                                                │
│    ┌──────┴──────┐                                              │
│  单租户        多租户                                            │
│  DIRECT     SELECT_TENANT                                       │
│    │              │                                             │
│    ▼              ▼                                             │
│  存入 token   step='select_tenant'（preToken 存 state，不跳页）  │
│  → 首页       ┌─────────────────────────────────┐              │
│               │  请选择要进入的租户               │              │
│               │  ┌──────────┐ ┌──────────┐      │              │
│               │  │ 天南大陆  │ │  租户B   │      │              │
│               │  └──────────┘ └──────────┘      │              │
│               │  [← 重新登录]                    │              │
│               └─────────────────────────────────┘              │
│                        │ 点击租户卡片                           │
│                    调用 selectTenant                            │
│                        │ 成功                                   │
│                   存入正式 token → 首页                          │
└─────────────────────────────────────────────────────────────────┘
```

**登录接口调用后的处理**：

```typescript
// src/tenant/pages/login/LoginPage.tsx
type LoginStep = 'credentials' | 'select_tenant';

const LoginPage: React.FC = () => {
  const [step, setStep] = useState<LoginStep>('credentials');
  const [preToken, setPreToken] = useState<string>('');
  const [tenantList, setTenantList] = useState<TenantBrief[]>([]);
  const [loading, setLoading] = useState(false);

  const handleLogin = async (values: { mobile: string; password: string }) => {
    setLoading(true);
    try {
      const res = await authApi.login(values);

      if (res.loginType === 'DIRECT') {
        // 单租户：直接进入（仅一个启用的租户）
        storage.setToken(res.accessToken, res.refreshToken);
        userStore.setUserInfo(res.user, res.tenantId, res.tenantName);
        navigate('/');
      } else if (res.loginType === 'SELECT_TENANT') {
        // 多租户：切换到选择步骤（preToken 存 state，不跳页不写 sessionStorage）
        setPreToken(res.preToken);
        setTenantList(res.tenants); // 接口只返回未禁用的租户
        setStep('select_tenant');
      }
    } catch (err: any) {
      if (err.code === 401) {
        message.error('账号或密码错误');
        form.setFieldValue('password', '');
      } else if (err.code === 403) {
        message.error('您尚未加入任何租户，请联系管理员');
      } else {
        message.error(err.message);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      {step === 'credentials' && <CredentialsForm onSubmit={handleLogin} loading={loading} />}
      {step === 'select_tenant' && (
        <TenantSelectStep
          tenants={tenantList}
          preToken={preToken}
          onBack={() => { setStep('credentials'); setPreToken(''); }}
        />
      )}
    </div>
  );
};
```

---

### 3.3 登录内嵌租户选择步骤（`TenantSelectStep` 组件）

> 不再跳转独立的 `/tenant-select` 页面；租户选择作为 `LoginPage` 的第二步，URL 始终保持 `/login`，preToken 存在组件 state 中，不写入 sessionStorage。

**组件逻辑**：

```typescript
// src/tenant/pages/login/TenantSelectStep.tsx
interface TenantSelectStepProps {
  tenants: TenantBrief[];   // 接口已过滤，只含未禁用的租户
  preToken: string;
  onBack: () => void;       // 返回账号密码步骤
}

const TenantSelectStep: React.FC<TenantSelectStepProps> = ({ tenants, preToken, onBack }) => {
  const [selectingId, setSelectingId] = useState<number | null>(null);

  const handleSelect = async (tenantId: number) => {
    setSelectingId(tenantId);
    try {
      const res = await authApi.selectTenant(tenantId, preToken);

      storage.setToken(res.accessToken, res.refreshToken);
      userStore.setUserInfo(res.user, res.tenantId, res.tenantName);
      navigate('/');
    } catch (err: any) {
      if (err.code === 401) {
        // preToken 已过期（5分钟内有效）
        message.warning('选择超时，请重新登录');
        onBack(); // 回到账号密码步骤
      } else {
        message.error(err.message);
        setSelectingId(null);
      }
    }
  };

  return (
    <div className="tenant-select-step">
      <h3>请选择要进入的租户空间</h3>
      <div className="tenant-list">
        {tenants.map(t => (
          <div
            key={t.id}
            className="tenant-card"
            onClick={() => handleSelect(t.id)}
          >
            {selectingId === t.id ? <Spin /> : (
              <>
                <img src={t.logo || defaultLogo} alt={t.name} />
                <span>{t.name}</span>
                <span className="member-count">{t.memberCount} 名成员</span>
              </>
            )}
          </div>
        ))}
      </div>
      <Button type="link" onClick={onBack}>← 重新登录</Button>
    </div>
  );
};
```

**UI 示意**（嵌在登录卡片内，替换账号密码表单区域）：

```
┌──────────────────────────────────────────────┐
│           请选择要进入的租户空间                 │
│                                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │  [Logo]  │  │  [Logo]  │  │  [Logo]  │   │
│  │ 天南大陆  │  │  租户B   │  │  租户C   │   │
│  │ 5名成员  │  │ 12名成员  │  │ 3名成员  │   │
│  └──────────┘  └──────────┘  └──────────┘   │
│                                              │
│  [← 重新登录]                                │
└──────────────────────────────────────────────┘

说明：
- 接口只返回该用户所属的**未禁用**租户，禁用租户不出现在列表中
- 点击卡片：该卡片进入 Loading 状态，调用 selectTenant 接口
- 成功后直接跳首页；preToken 过期则回到账号密码步骤
- 只有一个可用租户时后端直接返回 DIRECT，不进入此步骤
```

---

### 3.4 租户切换（已登录状态）

**设计方案**：将租户切换**整合进头像下拉菜单**，无需独立触发入口。

**交互设计**：

```
┌─────────────────────────────────────────────────────────┐
│  Header 右上角：[头像] 张三 ▼                            │
└─────────────────────────────────────────────────────────┘

点击头像展开下拉：
┌──────────────────────────────────────┐
│  张三                                │
│  当前空间：天南大陆                   │
├──────────────────────────────────────┤
│  切换空间                            │
│  ┌────────────────────────────────┐  │
│  │ ✓  天南大陆      （当前，不可点）│  │
│  │    租户B                       │  │  ← 点击直接切换（二次确认弹窗）
│  │    租户C                       │  │
│  └────────────────────────────────┘  │
├──────────────────────────────────────┤
│  退出登录                            │
└──────────────────────────────────────┘

说明：
- 仅当 tenantList.length > 1 时展示"切换空间"区块
- 已禁用的租户不出现在列表（接口返回时已过滤）
- 当前租户标记 ✓ 且置灰不可点
- 点击其他租户 → Modal.confirm 确认 → 切换
```

**方案说明**：不采用"点击展开左侧面板"方案，因为：租户数量通常较少（大多数用户归属 1~5 个租户），直接在下拉内展开列表更简洁；若未来租户较多（>10），可在列表区域加搜索框。

**切换交互逻辑**：

```typescript
// src/tenant/layout/Header.tsx
const handleSwitchTenant = async (tenantId: number) => {
  if (tenantId === currentTenantId) return; // 当前租户不处理

  const target = tenantList.find(t => t.id === tenantId)!;
  Modal.confirm({
    title: '切换租户空间',
    content: `确定切换到「${target.name}」？当前页面未保存的内容将丢失。`,
    okText: '确认切换',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await authApi.switchTenant(tenantId);
        storage.setToken(res.accessToken, res.refreshToken);
        userStore.setCurrentTenant(res.tenantId, res.tenantName);
        // 刷新整个应用（路由、权限、菜单需重置）
        // 权限在刷新后由 initAfterLogin 重新从 /api/tenant/auth/permissions 拉取，无需在此设置
        window.location.href = '/';
      } catch (err: any) {
        if (err.code === 403) {
          message.error('您已不在该租户中，请刷新页面');
          userStore.fetchMyTenants(); // 刷新租户列表
        }
      }
    }
  });
};
```

---

## 4. 路由守卫设计

### 4.1 平台端路由守卫

```typescript
// src/platform/router/PlatformGuard.tsx
export const PlatformGuard: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { platformToken, platformUser } = usePlatformUserStore();
  const location = useLocation();

  if (!platformToken) {
    // 未登录：跳转平台登录页，保存原始路径
    return <Navigate to="/platform/login" state={{ from: location }} replace />;
  }

  // token 过期判断（前端解析 JWT exp 字段）
  if (isTokenExpired(platformToken)) {
    usePlatformUserStore.getState().logout();
    return <Navigate to="/platform/login" replace />;
  }

  return <>{children}</>;
};
```

### 4.2 租户端路由守卫

```typescript
// src/tenant/router/TenantGuard.tsx
export const TenantGuard: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { accessToken, currentTenantId } = useUserStore();
  const location = useLocation();

  // 未登录
  if (!accessToken) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // 已登录但没有 tenantId（理论上不应出现，preToken 阶段中间状态）
  // 注意：不跳转 /tenant-select（该路由不存在），回到 /login 由登录页内嵌 TenantSelectStep 处理
  if (!currentTenantId) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
};
```

### 4.3 权限路由守卫（按钮/页面级）

```typescript
// src/shared/components/AuthButton.tsx
interface AuthButtonProps {
  permission: string;    // 如 'tenant:role:add'
  children: React.ReactNode;
  fallback?: React.ReactNode;  // 无权限时显示的内容（默认 null）
}

export const AuthButton: React.FC<AuthButtonProps> = ({ permission, children, fallback = null }) => {
  const { permissions } = useUserStore();
  const hasPermission = permissions.includes(permission);
  return hasPermission ? <>{children}</> : <>{fallback}</>;
};

// 使用示例：
<AuthButton permission="tenant:role:add">
  <Button type="primary" onClick={handleCreate}>新增角色</Button>
</AuthButton>
```

```typescript
// src/shared/hooks/usePermission.ts
export function usePermission() {
  const { permissions } = useUserStore();

  return {
    has: (perm: string) => permissions.includes(perm),
    hasAny: (perms: string[]) => perms.some(p => permissions.includes(p)),
    hasAll: (perms: string[]) => perms.every(p => permissions.includes(p)),
  };
}
```

---

## 5. 菜单动态渲染

### 5.1 菜单数据来源

**方案**：前端路由配置中声明 `permission` 字段，运行时根据用户权限过滤，不依赖后端返回菜单树（减少接口依赖，前端掌控路由结构）。

```typescript
// src/tenant/router/menuConfig.ts
export interface MenuConfig {
  key: string;
  label: string;
  icon?: React.ReactNode;
  path?: string;
  permission?: string;          // 需要的权限标识
  children?: MenuConfig[];
}

export const TENANT_MENU: MenuConfig[] = [
  {
    key: 'members',
    label: '成员管理',
    icon: <TeamOutlined />,
    path: '/members',
    permission: 'tenant:member:list',
  },
  {
    key: 'user-center',
    label: '用户中心',
    icon: <UserOutlined />,
    children: [
      {
        key: 'app-users',
        label: '用户管理',
        path: '/app-users',
        permission: 'tenant:appuser:list',
      },
      {
        key: 'tags',
        label: '标签管理',
        icon: <TagsOutlined />,
        path: '/tags',
        permission: 'tenant:tag:list',
      },
      {
        key: 'field-def',
        label: '字段管理',
        icon: <ProfileOutlined />,
        path: '/field-def',
        permission: 'tenant:field:list',
      },
    ],
  },
  {
    key: 'roles',
    label: '角色管理',
    icon: <SafetyOutlined />,
    path: '/roles',
    permission: 'tenant:role:list',
  },
  {
    key: 'logs',
    label: '操作日志',
    icon: <FileTextOutlined />,
    path: '/logs',
    permission: 'tenant:log:list',
  },
];
```

### 5.2 菜单过滤逻辑

```typescript
// src/tenant/layout/TenantSidebar.tsx
const useFilteredMenu = () => {
  const { permissions } = useUserStore();

  const filterMenu = (menus: MenuConfig[]): MenuConfig[] => {
    return menus
      .filter(item => !item.permission || permissions.includes(item.permission))
      .map(item => ({
        ...item,
        children: item.children ? filterMenu(item.children) : undefined,
      }))
      .filter(item => !item.children || item.children.length > 0); // 过滤空目录
  };

  return filterMenu(TENANT_MENU);
};
```

### 5.3 无菜单时的处理

```typescript
// 登录成功后，如果过滤后的菜单为空（用户没有任何权限）
if (filteredMenus.length === 0) {
  // 显示"暂无权限"页面，而不是空白
  return <NoPermissionPage message="您暂无任何菜单权限，请联系租户管理员分配角色" />;
}

// 默认跳转第一个有权限的菜单
const firstPath = getFirstMenuPath(filteredMenus);
navigate(firstPath, { replace: true });
```

---

## 6. 各核心页面交互规范

### 6.1 平台端 — 租户管理页

**页面：租户列表**

```
列表列：租户名称、租户编码、联系人、状态（启用/禁用 Switch）、成员数、创建时间、操作

操作列：
  [配置权限]  → 打开权限配置抽屉
  [查看]     → 跳转租户详情
  [禁用]     → 二次确认弹窗："禁用后，该租户所有成员将立即被踢出登录，确认操作？"

权限配置抽屉：
  左侧：权限节点树（全部 scope=tenant 的节点，支持树形展示，父节点勾选联动子节点）
  右侧：已授权节点预览
  底部：[取消] [保存]
  保存后：刷新该租户的权限缓存（调用 PUT /api/platform/tenants/{id}/permissions）
```

**页面：创建租户**

```
表单字段：
  - 租户名称（必填，最长50字）
  - 租户编码（必填，仅字母数字下划线，唯一性校验通过防抖 API）
  - Logo（选填，图片上传）
  - 联系人姓名（选填）
  - 联系手机（选填）
  - 联系邮箱（选填）
  - 到期时间（选填，DatePicker）
  - 最大用户数（选填，InputNumber）
  ── 初始管理员 ──
  - 管理员手机号（必填，作为登录账号，系统自动生成初始密码并短信通知）
  - 管理员昵称（必填）

租户编码唯一性校验（防抖 500ms）：
  输入时 → 调用 GET /api/platform/tenants/check-code?code=xxx
         → 已存在：显示红色错误提示
         → 可用：显示绿色对勾
```

---

### 6.2 租户端 — 角色管理页

**页面：角色列表**

```
列表列：角色名称、角色标识、描述、权限数量、用户数量、创建时间、操作

操作列：
  [编辑权限]  → 打开权限配置抽屉
  [分配用户]  → 打开用户分配抽屉
  [删除]     → 有关联用户时：禁用删除按钮，Tooltip 提示"该角色已有 N 名用户，无法删除"
             → 无关联用户时：二次确认后删除
```

**权限配置抽屉（角色-权限分配）**：

```
抽屉宽度：600px
标题：配置角色权限 — 「角色名称」

内容：
  提示文字：「以下权限节点为本租户已授权范围，超出范围的节点不可选择」

  权限树（CheckboxTree）：
  ⚠ 只展示该租户 tenant_permission 授权范围内的节点，超出范围的节点直接不渲染
    ├── 用户中心
    │   ├── ☑ 查看用户列表
    │   ├── ☑ 查看用户详情
    │   ├── ☑ 编辑用户信息
    │   └── ☐ 导出用户数据    ← 未授权则此行根本不出现
    ├── 标签管理
    │   ├── ☑ 查看标签
    │   └── ☑ 新增标签
    └── ...
  （前端在渲染前先用 tenant_permission 集合过滤全量节点树，过滤后再渲染）

  全选/全不选 按钮
  底部：已选 N 项  [取消] [保存]

保存交互：
  → 调用 PUT /api/rbac/roles/{id}/permissions
  → 后端返回 200：Drawer 关闭，列表刷新「权限数量」列
  → 后端返回错误（含未授权节点）：Toast 提示后端错误信息，不关闭抽屉
```

---

### 6.3 租户端 — 成员管理页

**页面：成员列表**

```
列表列：头像、昵称、手机号、状态、角色（Tag 展示，多角色时 +N 省略）、加入时间、操作

搜索：按昵称/手机号搜索

操作列：
  [分配角色]  → 打开角色分配抽屉
  [禁用]     → 二次确认，调用 PUT /api/tenant/members/{userId}/status
  [移除]     → 二次确认："移除后，该成员将无法访问本租户，账号本身不受影响。确认移除？"
             → 调用 DELETE /api/tenant/members/{userId}
             → 若命中“最后一名超管”保护，展示后端 message（明确原因）
```

**角色分配抽屉（成员-角色分配）**：

```
标题：分配角色 — 「成员昵称」

内容：
  当前角色：[标签展示，可删除]
  选择角色：CheckboxGroup（展示当前租户的所有角色）
             注意：角色列表只显示当前租户的角色（tenant_id 过滤）

底部：[取消] [保存]

保存：
  → 调用 PUT /api/tenant/members/{userId}/roles
  → 若当前成员是最后一名超管且移除了超管角色，前端先提示并阻止提交
  → 若后端仍返回保护错误，按 message 原样展示
  → 成功：抽屉关闭，列表行「角色」列实时更新
```

**角色页补充交互（内置超管）**：

```
内置超管角色（is_builtin=1）：
  - 编辑按钮置灰，提示“内置超管角色不可编辑”
  - 删除按钮置灰，提示“内置超管角色不可删除”
  - 权限分配抽屉：权限树全勾选展示、整体只读，保存按钮禁用
```

---

### 6.4 状态管理 — userStore 扩展

```typescript
// src/tenant/store/userStore.ts
interface UserState {
  // 认证信息
  accessToken: string;
  refreshToken: string;

  // 用户信息
  userId: number | null;
  nickname: string;
  avatar: string;

  // 租户上下文（多租户核心）
  currentTenantId: number | null;
  currentTenantName: string;
  currentTenantLogo: string;

  // 权限（登录后从接口获取）
  permissions: string[];

  // 可切换的租户列表
  tenantList: TenantBrief[];

  // actions
  setToken: (access: string, refresh: string) => void;
  setUserInfo: (user: UserInfo, tenantId: number, tenantName: string) => void;
  setPermissions: (perms: string[]) => void;
  setCurrentTenant: (tenantId: number, tenantName: string) => void;
  fetchMyTenants: () => Promise<void>;
  logout: () => void;
}
```

**登录后权限加载时机**：

```typescript
// 登录成功后立即拉取权限（放在 store.setUserInfo 中触发，或在路由守卫中触发）
const initAfterLogin = async () => {
  // 拉取用户在当前租户的权限列表
  const perms = await authApi.getMyPermissions(); // GET /api/tenant/auth/permissions
  userStore.setPermissions(perms);

  // 拉取可切换租户列表（供 Header 展示）
  const tenants = await authApi.getMyTenants();
  userStore.setTenantList(tenants);
};
```

---

## 7. request.ts 改造

### 7.1 平台端和租户端使用不同的请求实例

```typescript
// src/shared/utils/request.ts（保持现有结构，增加 platform 实例）

// 租户端请求实例（现有逻辑，不变）
export const tenantInstance = axios.create({ timeout: 10000 });

// 平台端请求实例（Token 读取不同的 storage key）
export const platformInstance = axios.create({ timeout: 10000 });

platformInstance.interceptors.request.use((config) => {
  const token = storage.get(STORAGE_KEYS.PLATFORM_ACCESS_TOKEN);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

platformInstance.interceptors.response.use(
  (response) => { /* 同 tenantInstance，但 401 时跳 /platform/login */ },
  async (error) => {
    if (error.response?.status === 401) {
      usePlatformUserStore.getState().logout();
      window.location.href = '/platform/login';
    }
    // ...
  }
);
```

### 7.2 Token 刷新防并发（双实例各自维护刷新状态）

两个请求实例各自维护独立的 `isRefreshing` 和 `failedQueue`，防止平台端刷新和租户端刷新互相干扰。

---

## 8. Nginx 路由配置

```nginx
# platform.html 平台端单页应用
location /platform {
    root /usr/share/nginx/html;
    # 排除 /platform/login（让 React Router 处理）
    try_files $uri $uri/ /platform.html;
}

# 租户端单页应用（现有）
location / {
    root /usr/share/nginx/html;
    try_files $uri $uri/ /index.html;
}

# API 路由（保持现有配置）
location /api/platform/ {
    proxy_pass http://admin-service:8081;
    proxy_set_header X-Real-IP $remote_addr;
}
location /api/tenant/ {
    proxy_pass http://admin-service:8081;
}
# 其余 /api/v1/、/api/rbac/roles 等保持现有
```

---

## 9. 工程改造工作量评估

| 模块 | 工作内容 | 状态 | 优先级 |
|------|---------|------|--------|
| 目录重组 | 现有 `src/` → `src/tenant/` + `src/shared/` | 新建 | P0 |
| Vite 多入口 + platform.html | 配置和模板文件 | 新建 | P0 |
| 平台端登录页 + 路由框架 | 新建 | 新建 | P0 |
| 租户多租户登录（内嵌选择步骤） | 改造现有登录页，增加 step 状态和 TenantSelectStep 组件 | 改造 | P0 |
| 双 axios 实例 | 改造 request.ts | 改造 | P0 |
| 路由守卫 (PlatformGuard / TenantGuard) | 新建 | 新建 | P0 |
| 权限 Hook + AuthButton 组件 | 新建 | 新建 | P0 |
| 菜单动态过滤 | 改造现有侧边栏 | 改造 | P1 |
| Header 头像下拉含租户切换 | 在现有头像下拉中增加租户切换区块 | 改造 | P1 |
| 角色管理页（租户端） | 现有 system/role 迁移并适配多租户 | 迁移改造 | P1 |
| 成员管理页（租户端） | 新建 | 新建 | P1 |
| 字段管理页（租户端） | 现有 app-user/field 迁移 | 迁移 | P1 |
| 标签管理页（租户端） | 现有 app-user/tag 迁移 | 迁移 | P1 |
| 用户管理页（租户端） | 现有 app-user/user 迁移 | 迁移 | P1 |
| 平台端租户管理页（含权限配置抽屉） | 新建 | 新建 | P1 |
| 平台端用户管理页 | 新建 | 新建 | P2 |
| 平台端角色管理页 | 新建 | 新建 | P2 |
| 平台端权限节点管理页 | 新建 | 新建 | P2 |
| 平台端操作日志页 | 新建 | 新建 | P2 |
