# 智能体管理平台 — Claude Code 项目规范

## 项目概述

智能体管理平台，包含后台管理系统与智能体服务。前端为 React 单页应用，后端为 Java Spring Boot 微服务架构。

## 目录结构

```
├── frontend/          # React 前端
├── services/
│   ├── admin-service  # Java 后台管理服务（端口 8081）
│   ├── user-service   # Java 用户服务（端口 8082）
│   ├── log-service    # Java 日志服务（端口 8083）
│   └── agent-service  # Python/FastAPI 智能体服务（端口 8090）
├── infrastructure/    # Nginx、MySQL 配置
├── design-system/     # 前端设计系统主题（theme.ts 等）
└── docs/              # 设计文档与架构说明
```

## 技术栈

### 后端
- **Java 17** + **Spring Boot 3.2.1**
- **MySQL 8.4.8** / **Redis 7.4.8** / **Elasticsearch 8.19.7** / **Kafka**
- 测试：JUnit 5、Mockito 4、Testcontainers 1.19.3
- **Python** + **FastAPI**（agent-service）

### 前端
- **React 19** + **TypeScript 5** + **Vite**
- **Ant Design 6** + **Zustand 5** + **React Router 7**
- 测试：Vitest

> 引入新依赖前需得到确认，不随意升级版本。

## 代码规范

### 通用
- 不要过度设计：只实现被要求的功能，不添加未被请求的抽象层、错误处理或配置项
- 不要添加无意义的注释，只在逻辑不自明时才写注释
- 不要创建不必要的新文件，优先复用/修改已有代码

### 后端（Java）
- 遵循现有包结构（controller / service / mapper / dto / entity）
- 接口统一返回 `Result<T>` 包装类
- 数据库操作使用 MyBatis-Plus

### 前端
- 组件放在 `frontend/src/components/`，页面放在 `frontend/src/pages/`
- 状态管理使用 Zustand store（`frontend/src/store/`）
- API 请求统一封装在 `frontend/src/api/`
- 类型定义放在 `frontend/src/types/`

## UI 规范（前端）

### 核心原则
- 优先使用 Ant Design 主题 token，**禁止**在业务组件中写死颜色和样式
- 新颜色/阴影/圆角须先更新 `design-system/theme.ts`，再使用

### 配色
| 用途 | 色值 |
|------|------|
| 页面背景 | `#F8FAFC` |
| 容器背景 | `#FFFFFF` |
| 主色 Primary | `#2563EB` |
| 文本主色 | `#020617` |
| 文本次级 | `#64748B` |
| 成功 / 警告 / 错误 | `#16A34A` / `#EAB308` / `#DC2626` |

### 布局
- 页面整体：外层容器铺满视口，白色主体区域，圆角 0，阴影 0
- 列表/表格页：「上方工具栏 + 下方表格 + 底部操作栏」三段结构
- 其他布局模式须先获得确认

### 表格页
- 创建/编辑通过**右侧 Drawer** 承载，不用 Modal 或整页跳转
- 筛选项变更后**立即触发查询**，不需要单独的查询按钮
- 操作列固定在右侧，危险操作使用 `danger` 样式并二次确认
- 工具栏按钮宽度由内容决定，禁止 flex 拉伸

### 卡片
- 背景 `#FFFFFF`，圆角 `15px`，内边距 `16px`
- 阴影：`0 4px 6px rgba(15, 23, 42, 0.08)`

## 服务端口

| 服务 | 端口 |
|------|------|
| Nginx 网关 | 80 |
| admin-service | 8081 |
| user-service | 8082 |
| log-service | 8083 |
| agent-service | 8090 |
| MySQL | 3306 |
| Redis | 6379 |
| Elasticsearch | 9200 |
| Kafka | 9092 |
