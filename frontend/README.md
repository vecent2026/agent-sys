# 前端项目说明

本目录是智能体管理平台的前端应用，技术栈为 React + TypeScript + Vite。

## 技术栈

- React 19
- TypeScript 5
- Vite 7
- Ant Design 6
- React Router 7
- Zustand 5
- React Query 5
- Vitest

## 环境要求

- Node.js 20 及以上
- npm 10 及以上（或兼容版本）

建议先检查：

```bash
node -v
npm -v
```

## 安装依赖

```bash
npm install
```

## 启动开发环境

```bash
npm run dev
```

默认由 Vite 启动开发服务。后端联调时，请先确保网关或后端服务已可访问。

## 构建与预览

构建生产包：

```bash
npm run build
```

本地预览构建结果：

```bash
npm run preview
```

## 代码检查与测试

运行 ESLint：

```bash
npm run lint
```

运行单元测试：

```bash
npm run test
```

## 目录约定

- `src/pages/`: 页面级模块
- `src/components/`: 通用组件
- `src/api/`: 接口请求封装
- `src/store/`: Zustand 状态管理
- `src/types/`: 类型定义
- `src/styles/` 与样式文件: 页面和组件样式

## 开发约束

- 优先复用现有组件和 API 封装，不重复造轮子。
- 保持 TypeScript 类型完整，避免 `any` 扩散。
- UI 风格遵循仓库根目录规范：`/CLAUDE.md` 和 `design-system/`。
- 涉及接口联调时，确保与后端端口和网关配置一致。

## 常见问题

依赖安装失败：
- 删除 `node_modules` 与 lock 文件后重装。
- 检查 Node.js 版本是否满足要求。

联调请求失败：
- 确认后端服务是否启动（常用端口 8081/8082/8083）。
- 确认网关与本地代理配置是否一致。
