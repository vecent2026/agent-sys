# 构建阶段
FROM maven:3.9.6-eclipse-temurin-17 AS builder

ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

ENV HTTP_PROXY=${HTTP_PROXY}
ENV HTTPS_PROXY=${HTTPS_PROXY}
ENV NO_PROXY=${NO_PROXY}

# 添加国内 Maven 镜像源
RUN mkdir -p /root/.m2 && echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" \n  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \n  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 \n  http://maven.apache.org/xsd/settings-1.0.0.xsd"> \n  <mirrors> \n    <mirror> \n      <id>alimaven</id> \n      <name>Aliyun Maven</name> \n      <url>https://maven.aliyun.com/repository/public</url> \n      <mirrorOf>central</mirrorOf> \n    </mirror> \n  </mirrors> \n</settings>' > /root/.m2/settings.xml

WORKDIR /app

# 复制 pom.xml 和源代码
COPY pom.xml .
COPY src ./src

# 打包应用
RUN mvn clean package -DskipTests

# 运行阶段
FROM amazoncorretto:17-alpine

ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

ENV HTTP_PROXY=${HTTP_PROXY}
ENV HTTPS_PROXY=${HTTPS_PROXY}
ENV NO_PROXY=${NO_PROXY}

# 安装 curl 命令用于健康检查
RUN apk add --no-cache curl

WORKDIR /app

# 从构建阶段复制 jar 文件
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
