# ===== 构建阶段 =====
# 用 JDK 17 而非 21：与 pom 中声明的 java.version 保持一致
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先只拷 pom，利用 Docker 层缓存：源码改动不会导致重新下载依赖
COPY pom.xml .
COPY kb-common/pom.xml kb-common/
COPY kb-rag/pom.xml kb-rag/
COPY kb-admin/pom.xml kb-admin/
RUN mvn -B -q dependency:go-offline

# 再拷源码构建
COPY . .
RUN mvn -B -q clean package -DskipTests

# ===== 运行阶段 =====
FROM eclipse-temurin:17-jre

WORKDIR /app

# 时区设为东八区，否则日志时间与业务时间差 8 小时，排查问题极痛苦
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 非 root 运行，降低容器逃逸后的影响面（安全基线要求）
RUN useradd -r -u 1001 -m appuser
USER appuser

COPY --from=builder /build/kb-admin/target/*.jar app.jar

# 容器内文件存储目录
RUN mkdir -p /app/data/files

EXPOSE 8080

# 限制堆内存，避免容器被 OOM Killer 干掉；
# MaxRAMPercentage 比固定 -Xmx 更适合容器环境
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseG1GC", "-jar", "app.jar"]
