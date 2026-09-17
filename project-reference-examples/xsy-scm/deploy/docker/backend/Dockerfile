# XSY-SCM 后端服务镜像（多阶段构建）
# 阶段一：Maven 构建（容器内完成编译，本机无需安装 JDK / Maven）
# 阶段二：JRE 运行（镜像体积小）
#
# 构建上下文：仓库根目录
#   docker build -f deploy/docker/backend/Dockerfile -t xsy-scm-server:dev .

FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先只拷贝 pom，最大化利用 Docker 层缓存（依赖不变时不重新下载）
COPY xsy-scm-server/pom.xml ./pom.xml
COPY xsy-scm-server/xsy-scm-base/pom.xml ./xsy-scm-base/pom.xml
COPY xsy-scm-server/xsy-scm-server/pom.xml ./xsy-scm-server/pom.xml
# 依赖下载单独成层：pom 不变时直接命中缓存（不使用 BuildKit 缓存挂载，兼容 classic builder）
RUN mvn -B -ntp dependency:go-offline -pl xsy-scm-server -am -DskipTests || true

# 再拷贝源码
COPY xsy-scm-server ./

# Maven profile：dev / test / pre / prod，与 pom.xml 中 profiles 对应
ARG MAVEN_PROFILE=dev
RUN mvn -B -ntp -P ${MAVEN_PROFILE} clean package -DskipTests

# ---------------------------------------------------------------- 运行阶段
FROM eclipse-temurin:17-jre-jammy

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LOCALPATH=/app/logs \
    JAVA_OPTS="-Xms512m -Xmx1024m"

RUN ln -snf /usr/share/zoneinfo/${TZ} /etc/localtime && echo ${TZ} > /etc/timezone \
    && mkdir -p /app/logs

WORKDIR /app

# 产物名：xsy-scm-server-{profile}-{version}.jar
COPY --from=builder /build/xsy-scm-server/target/*.jar /app/app.jar

VOLUME ["/app/logs"]
EXPOSE 1024

# 端口存活探测（项目未引入 actuator，用 TCP 探测）
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD bash -c 'echo > /dev/tcp/127.0.0.1/1024' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
