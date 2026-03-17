# ==============================================================================
# 多阶段构建 Dockerfile
# 第一阶段：构建阶段 - 负责编译和打包应用
# 第二阶段：运行阶段 - 负责运行应用
# ==============================================================================

# 构建阶段：使用 Maven 镜像执行编译和打包
FROM maven:3.9.6-eclipse-temurin-17 AS builder

ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

ENV HTTP_PROXY=${HTTP_PROXY}
ENV HTTPS_PROXY=${HTTPS_PROXY}
ENV NO_PROXY=${NO_PROXY}

RUN mkdir -p /root/.m2 && echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" \n  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \n  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 \n  http://maven.apache.org/xsd/settings-1.0.0.xsd"> \n  <mirrors> \n    <mirror> \n      <id>alimaven</id> \n      <name>Aliyun Maven</name> \n      <url>https://maven.aliyun.com/repository/public</url> \n      <mirrorOf>central</mirrorOf> \n    </mirror> \n  </mirrors> \n</settings>' > /root/.m2/settings.xml

WORKDIR /app

# 复制项目的 pom.xml 文件到工作目录
COPY pom.xml .

# 预先下载依赖，利用 Docker 缓存机制
# 只要 pom.xml 不变，这一步就会被缓存，大大加快后续构建速度
RUN mvn dependency:go-offline -B

# 复制项目源代码到工作目录
COPY src ./src

# 执行 Maven 打包命令
# clean: 清理之前的构建产物
# package: 编译并打包应用
# -Dmaven.test.skip=true: 完全跳过测试编译和执行，加快构建速度
# -o: 离线模式，使用已下载的依赖
RUN mvn clean package -Dmaven.test.skip=true -o

# ==============================================================================
# 运行阶段：使用轻量级的 Java 运行时镜像
# ==============================================================================
FROM amazoncorretto:17-alpine

ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

ENV HTTP_PROXY=${HTTP_PROXY}
ENV HTTPS_PROXY=${HTTPS_PROXY}
ENV NO_PROXY=${NO_PROXY}

RUN apk add --no-cache curl fontconfig ttf-dejavu

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
