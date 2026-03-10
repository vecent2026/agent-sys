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

COPY pom.xml .
COPY src ./src

RUN mvn clean package -Dmaven.test.skip=true

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
