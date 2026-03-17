# 智能体管理平台

智能体管理平台仓库，包含前端应用、后台管理服务、用户服务、日志服务，以及本地开发所需的基础设施配置。

## 项目结构

```text
.
├── frontend/              # React + TypeScript + Vite 前端
├── services/
│   ├── admin-service/     # 后台管理服务，端口 8081
│   ├── user-service/      # 用户服务，端口 8082
│   └── log-service/       # 日志服务，端口 8083
├── infrastructure/        # Nginx、MySQL 等基础设施配置
├── design-system/         # 前端主题与设计系统
├── docs/                  # 架构和业务文档
├── docker-compose.yml     # 本地联调依赖与服务编排
└── CLAUDE.md              # 项目协作与开发规范
```

说明：
- `CLAUDE.md` 中仍提到 `agent-service`，但当前仓库目录和 `docker-compose.yml` 已显示该服务暂时移除。
- 前端当前有独立的 [frontend/README.md](/Users/wenpeilin/trae/智能体管理/frontend/README.md)，但内容仍是 Vite 模板说明。

## 技术栈

### 前端

- React 19
- TypeScript 5
- Vite
- Ant Design 6
- Zustand
- React Router 7
- Vitest

### 后端

- Java 17
- Spring Boot 3.2.1
- Maven
- MyBatis-Plus
- MySQL 8
- Redis 7
- Elasticsearch 8
- Kafka

## 端口约定

| 服务 | 端口 |
| --- | --- |
| Nginx 网关 | 80 |
| admin-service | 8081 |
| user-service | 8082 |
| log-service | 8083 |
| MySQL | 3306 |
| Redis | 6379 |
| Elasticsearch | 9200 |
| Kafka | 9092 |

## 本地开发前置条件

开始开发前，请先确认本机具备以下环境：

- Node.js 20 及以上
- npm 或兼容的前端包管理器
- Java 17
- Maven 3.9 及以上
- Docker 与 Docker Compose

建议先检查：

```bash
node -v
npm -v
java -version
mvn -v
docker -v
docker compose version
```

## 初始化清单

首次拉起项目时，建议按下面顺序完成初始化：

1. 阅读 [CLAUDE.md](/Users/wenpeilin/trae/智能体管理/CLAUDE.md)，了解目录结构、接口约定和 UI 规范。
2. 确认当前 Git 分支正确，并检查是否基于最新 `main`。
3. 安装前端依赖：

```bash
cd frontend
npm install
```

4. 确认后端 Java/Maven 环境可用。
5. 启动基础依赖：

```bash
docker compose up -d mysql redis elasticsearch zookeeper kafka
```

6. 如需完整联调，再启动全部容器：

```bash
docker compose up -d
```

7. 确认数据库初始化 SQL 已自动导入。
8. 运行前端和后端测试，建立当前可用基线。

## 启动方式

### 方式一：Docker Compose 全量启动

适合快速联调整体服务：

```bash
docker compose up -d --build
```

查看状态：

```bash
docker compose ps
```

查看日志：

```bash
docker compose logs -f admin-service
docker compose logs -f user-service
docker compose logs -f log-service
```

停止服务：

```bash
docker compose down
```

### 方式二：本地开发态启动

适合前端或单个后端服务调试。

先启动基础依赖：

```bash
docker compose up -d mysql redis elasticsearch zookeeper kafka
```

启动前端：

```bash
cd frontend
npm run dev
```

启动后端服务：

```bash
cd services/admin-service
mvn spring-boot:run
```

```bash
cd services/user-service
mvn spring-boot:run
```

```bash
cd services/log-service
mvn spring-boot:run
```

说明：
- 当前仓库中未提供统一根级启动脚本，服务通常需要分别启动。
- 后端服务依赖 MySQL、Redis、Kafka、Elasticsearch，请先保证这些容器已就绪。

## 常用命令

### 前端

```bash
cd frontend
npm run dev
npm run build
npm run lint
npm run test
```

### 后端

```bash
cd services/admin-service
mvn test
```

```bash
cd services/user-service
mvn test
```

```bash
cd services/log-service
mvn test
```

## 环境与配置说明

- 根目录存在 `.env`，但当前仓库未提供 `.env.example`，建议后续补齐。
- `docker-compose.yml` 已为容器启动注入 MySQL、Redis、Kafka、Elasticsearch 等连接信息。
- 如果采用本地 IDE 启动 Spring Boot 服务，需要确认本地配置与 Compose 中的连接参数一致。

## 当前已知事项

- 根目录此前缺少仓库级 README，本文件用于补齐统一入口说明。
- [frontend/README.md](/Users/wenpeilin/trae/智能体管理/frontend/README.md) 还是模板内容，建议后续改成项目实际说明。
- `agent-service` 当前未在仓库中提供，相关文档描述需要后续同步。

## 与 Codex 协作建议

让 Codex 接手当前项目时，建议先提供这样的起始指令：

```text
先阅读 CLAUDE.md 和 README.md，总结这个仓库的模块划分、启动方式和测试方式，再开始修改代码。
```

这样可以减少对目录结构、服务边界和 UI 规范的误判。
