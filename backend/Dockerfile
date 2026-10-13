# ============================================================================
#  后端镜像：多阶段构建
# ----------------------------------------------------------------------------
#  阶段 1：用 Maven 镜像编译打包
#  阶段 2：仅拷贝 jar 到 JRE 镜像，减小体积
#
#  关于 maven-settings.xml：
#  容器内 Maven 默认直连 Maven Central，在部分网络环境下会频繁出现
#      "Remote host terminated the handshake"
#      "Premature end of Content-Length delimited message body"
#  导致同一份代码构建随机失败。该 settings.xml 将中央仓库指向国内镜像，
#  并把 Spring 里程碑仓库排除在镜像规则之外（Spring AI 1.0.0-M4 是
#  milestone 版本，只存在于 repo.spring.io/milestone，被镜像接管会 404）。
# ============================================================================

FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 先只拷贝 pom 与 Maven 配置，让依赖层可被缓存复用
COPY pom.xml .
COPY maven-settings.xml .

# 预热依赖（利用 Docker 层缓存，源码变动时无需重新下载依赖）
RUN mvn -s maven-settings.xml -B dependency:go-offline -DskipTests || true

COPY src ./src
RUN mvn -s maven-settings.xml -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/ai-pub-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
