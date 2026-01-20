FROM maven:3-eclipse-temurin-21-alpine AS build
ARG VER=SNAPSHOT
COPY application /tmp/application
COPY frontend /tmp/frontend
COPY frameworks /tmp/frameworks
COPY modules /tmp/modules
COPY entities /tmp/entities
COPY providers /tmp/providers
COPY runtimes /tmp/runtimes
COPY triggers /tmp/triggers
COPY templates /tmp/templates
COPY pom.xml /tmp/pom.xml
WORKDIR /tmp
RUN --mount=type=cache,target=/root/.m2,source=/cache/.m2,from=ghcr.io/scc-digitalhub/digitalhub-core:cache \ 
    mvn -Drevision=${VER} install -pl 'modules/commons'
RUN --mount=type=cache,target=/root/.m2,source=/cache/.m2,from=ghcr.io/scc-digitalhub/digitalhub-core:cache \ 
    mvn -Drevision=${VER} install -pl '!modules/commons,!frontend,!application'    
RUN --mount=type=cache,target=/root/.m2,source=/cache/.m2,from=ghcr.io/scc-digitalhub/digitalhub-core:cache \
    --mount=type=cache,target=/tmp/frontend/target,source=/cache/frontend/target,from=ghcr.io/scc-digitalhub/digitalhub-core:cache \ 
    --mount=type=cache,target=/tmp/frontend/console/node_modules,source=/cache/frontend/console/node_modules,from=ghcr.io/scc-digitalhub/digitalhub-core:cache \ 
    mvn -Drevision=${VER} install -pl 'frontend'
RUN --mount=type=cache,target=/root/.m2,source=/cache/.m2,from=ghcr.io/scc-digitalhub/digitalhub-core:cache \ 
    mvn -Drevision=${VER} package -pl 'application'

FROM maven:3-eclipse-temurin-21-alpine AS builder
WORKDIR /tmp
COPY --from=build /tmp/application/target/*.jar /tmp/
RUN java -Djarmode=layertools -jar *.jar extract

FROM gcr.io/distroless/java21-debian12:nonroot
ENV APP=core.jar
WORKDIR /app
LABEL org.opencontainers.image.source=https://github.com/scc-digitalhub/digitalhub-core
COPY --from=builder /tmp/dependencies/ ./
COPY --from=builder /tmp/snapshot-dependencies/ ./
COPY --from=builder /tmp/spring-boot-loader/ ./
COPY --from=builder /tmp/dh-dependencies/ ./
COPY --from=builder /tmp/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
