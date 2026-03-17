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
