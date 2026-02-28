# ==============================================================================
# 多阶段构建 Dockerfile
# 第一阶段：构建阶段 - 负责编译和打包应用
# 第二阶段：运行阶段 - 负责运行应用
# ==============================================================================

# 构建阶段：使用 Maven 镜像执行编译和打包
# 使用 maven:3.9.6-eclipse-temurin-17 镜像，包含 Java 17 和 Maven 3.9.6
# 标记为 builder，以便在后续阶段引用
FROM maven:3.9.6-eclipse-temurin-17 AS builder

# 声明代理环境变量（可选）
# 这些变量可以在构建时通过 --build-arg 传入
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

# 设置环境变量，用于 Maven 下载依赖时使用代理
ENV HTTP_PROXY=${HTTP_PROXY}
ENV HTTPS_PROXY=${HTTPS_PROXY}
ENV NO_PROXY=${NO_PROXY}

# 设置工作目录为 /app
WORKDIR /app

# 复制项目的 pom.xml 文件到工作目录
# 这样可以利用 Docker 的缓存机制，当 pom.xml 未变化时，跳过依赖下载步骤
COPY pom.xml .

# 复制项目源代码到工作目录
COPY src ./src

# 执行 Maven 打包命令
# clean: 清理之前的构建产物
# package: 编译并打包应用
# -DskipTests: 跳过测试，加快构建速度
RUN mvn clean package -DskipTests

# ==============================================================================
# 运行阶段：使用轻量级的 Java 运行时镜像
# 使用 amazoncorretto:17-alpine 镜像，这是一个基于 Alpine Linux 的轻量级 Java 17 运行时
# ==============================================================================
FROM amazoncorretto:17-alpine

# 声明代理环境变量（可选）
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

# 设置环境变量
ENV HTTP_PROXY=${HTTP_PROXY}
ENV HTTPS_PROXY=${HTTPS_PROXY}
ENV NO_PROXY=${NO_PROXY}

# 设置工作目录为 /app
WORKDIR /app

# 从构建阶段复制打包好的 jar 文件到当前阶段
# --from=builder: 指定从 builder 阶段复制
# /app/target/*.jar: 构建阶段中打包生成的 jar 文件路径
# app.jar: 重命名为 app.jar，方便后续执行
COPY --from=builder /app/target/*.jar app.jar

# 暴露应用运行的端口
# 8081: Spring Boot 应用的默认端口
EXPOSE 8081

# 设置容器启动时执行的命令
# 使用 exec 格式，确保信号能够正确传递给应用
# java -jar app.jar: 启动 Spring Boot 应用
ENTRYPOINT ["java", "-jar", "app.jar"]
