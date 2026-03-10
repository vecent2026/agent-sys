---
alwaysApply: false
description: 端口映射规则
---

# 端口配置规则

## 当前端口分配

| 服务 | 端口 | 类型 |
|------|------|------|
| nginx-gateway | 80 | 网关 |
| mysql | 3306 | 数据库 |
| redis | 6379 | 缓存 |
| elasticsearch | 9200 | 搜索引擎 |
| kafka | 9092 | 消息队列 |
| zookeeper | 2181 | 协调服务 |
| admin-service | 8081 | Java微服务 |
| user-service | 8082 | Java微服务 |
| log-service | 8083 | Java微服务 |
| agent-service | 8090 | Python服务 |

## 端口分配原则

- **80**: 网关入口
- **808x**: Java Spring Boot服务
- **809x**: Python FastAPI服务
- **810x-819x**: 其他微服务预留
- **3000-3999**: 前端开发服务器预留

## 可用端口池

### Java微服务 (808x)
8080, 8084-8089

### Python服务 (809x)
8091-8099

### 其他微服务
8100-8199

## 添加新服务

1. 从可用端口池选择端口
2. 更新 `docker-compose.yml` 端口映射
3. 更新服务配置文件端口
4. 更新本文档
