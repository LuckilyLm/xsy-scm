# xsy-scm V2 backend image
# Build context: repository root

FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY xsy-scm-server/pom.xml ./pom.xml
COPY xsy-scm-server/sa-base/pom.xml ./sa-base/pom.xml
COPY xsy-scm-server/sa-admin/pom.xml ./sa-admin/pom.xml

COPY xsy-scm-server/ ./

ARG MAVEN_PROFILE=prod
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -P${MAVEN_PROFILE} clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-Xms512m -Xmx1024m"

# 图形验证码使用 Java AWT，精简 JRE 需要字体与 fontconfig。
RUN apt-get update && apt-get install -y --no-install-recommends curl fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid 10001 --create-home --home-dir /home/app app \
    && mkdir -p /app/logs \
    && chown -R app:app /app

WORKDIR /app
COPY --from=builder --chown=app:app /build/sa-admin/target/*.jar /app/app.jar

VOLUME ["/app/logs"]
EXPOSE 1024
USER app

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
