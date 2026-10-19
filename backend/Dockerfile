FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy

ARG VCS_REF=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.source="https://github.com/ZaneLau/traclux-probe" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tini \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 geoprobe \
    && useradd --system --uid 10001 --gid geoprobe --home-dir /app --shell /usr/sbin/nologin geoprobe

WORKDIR /app

ENV TZ=Asia/Shanghai

COPY --from=build /workspace/target/*.jar /app/app.jar

RUN chown -R geoprobe:geoprobe /app

USER geoprobe

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl --fail --silent http://127.0.0.1:8080/api/v1/health >/dev/null || exit 1

ENTRYPOINT ["tini", "--", "java", "-Duser.timezone=Asia/Shanghai", "-jar", "/app/app.jar"]
