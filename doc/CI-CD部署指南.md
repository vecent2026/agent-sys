# CI/CD 本地部署指南

## 1. 概述
本项目采用 **Gitea + Gitea Actions** 作为本地 CI/CD 解决方案。该方案轻量级、易于部署，非常适合本地开发环境。

## 2. 架构说明
- **Gitea**: 代码托管平台，提供 Git 仓库管理和 Web 界面。
- **Gitea Runner**: 执行 CI/CD 流水线的代理程序，负责运行构建、测试和部署任务。

## 3. 启动步骤

### 3.1 启动所有服务
```bash
docker-compose up -d
```

### 3.2 初始化 Gitea
1.  访问 `http://localhost:3000`，进入 Gitea 初始化页面。
2.  按照提示完成初始配置（数据库选择 SQLite3 即可，简化配置）。
3.  创建管理员账户。

### 3.3 注册 Runner
1.  在 Gitea Web 界面，进入 **管理 -> Runners** 页面。
2.  创建一个新的 Runner，获取 Registration Token。
3.  更新 `docker-compose.yml` 中 `gitea-runner` 服务的 `GITEA_RUNNER_REGISTRATION_TOKEN` 环境变量。
4.  重启 Runner 服务：
    ```bash
    docker-compose up -d gitea-runner
    ```

### 3.4 创建仓库并推送代码
1.  在 Gitea 中创建一个新的仓库（如 `admin-system`）。
2.  将本地代码推送到 Gitea 仓库：
    ```bash
    git remote add gitea http://localhost:3000/<your-username>/admin-system.git
    git push gitea main
    ```

### 3.5 触发 CI/CD
- 推送代码到 `main` 分支后，Gitea Actions 将自动触发流水线。
- 在 Gitea 仓库的 **Actions** 标签页可以查看流水线运行状态。

## 4. 流水线说明
流水线定义在 `.gitea/workflows/build.yml` 文件中，包含以下步骤：
1.  **Checkout**: 拉取代码。
2.  **Set up JDK 17**: 安装 Java 开发环境。
3.  **Build Admin Service**: 构建 Admin 服务。
4.  **Build Log Service**: 构建 Log 服务。
5.  **Set up Docker Buildx**: 准备 Docker 构建环境。
6.  **Build Docker Images**: 构建所有服务的 Docker 镜像。
7.  **Deploy**: 使用 Docker Compose 部署服务。

## 5. 注意事项
- **Docker Socket**: Runner 需要挂载 `/var/run/docker.sock` 才能执行 Docker 命令。
- **网络**: 所有服务（包括 Runner）必须在同一 Docker 网络中，以便互相访问。
- **资源**: 建议本地环境至少分配 8GB 内存给 Docker。
