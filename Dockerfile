FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress --settings .mvn/mirror-settings.xml package \
    -Dmaven.test.skip=true -Dcheckstyle.skip=true -Djacoco.skip=true

FROM eclipse-temurin:21.0.13.1_2-jre-alpine-3.24
RUN apk upgrade --no-cache
WORKDIR /app
COPY --from=build /build/bootstrap/target/playlist-bootstrap-*.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
