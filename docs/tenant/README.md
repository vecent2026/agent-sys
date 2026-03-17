# 多租户架构设计文档

## 文档目录

| 文档 | 说明 |
|------|------|
| [01-architecture-overview.md](./01-architecture-overview.md) | 整体架构概览、核心概念、设计原则 |
| [02-database-design.md](./02-database-design.md) | 数据库表结构设计、DDL、索引策略 |
| [03-permission-system.md](./03-permission-system.md) | 权限体系设计、平台权限节点与租户角色管理 |
| [04-frontend-split.md](./04-frontend-split.md) | 前端平台端/租户端拆分方案 |
| [05-api-design.md](./05-api-design.md) | 接口变更、认证流程、租户切换 |
| [06-migration-plan.md](./06-migration-plan.md) | 数据迁移方案、分阶段实施计划 |

## 核心需求概览

1. **租户由平台开通**：平台端（超管）负责创建/管理租户
2. **同库同表隔离**：所有表通过 `tenant_id` 字段区分租户数据
3. **用户多租户**：一个用户账号可加入多个租户，登录后可切换租户上下文
4. **历史数据迁移**：现有数据归入名为「天南大陆」的默认租户
5. **权限分层**：平台端配置权限节点，租户端只做角色管理（角色-权限节点的映射）
6. **平台独立体系**：平台管理员有独立的用户表、操作日志，与租户数据完全隔离

## 关键约束

- 后端服务架构维持现有微服务体系（admin-service / user-service）
- 不引入第三方多租户框架，通过 MyBatis-Plus 拦截器 + AOP 实现透明 `tenant_id` 过滤
- JWT Token 携带 `tenantId`，网关层做初步校验，服务层做二次校验
