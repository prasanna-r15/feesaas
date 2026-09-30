# Build from the backend folder:
#   docker build -t feesaas-api ./backend
# Run (Neon or any Postgres):
#   docker run --rm -p 9085:9085 -e PORT=9085 -e DB_URL=... -e DB_APP_USER=... -e DB_APP_PASSWORD=... -e DB_OWNER_USER=... -e DB_OWNER_PASSWORD=... feesaas-api

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies -x test || true

COPY src src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar -x test \
    && cp build/libs/feesaas-0.1.0-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring
COPY --from=build /workspace/app.jar /app/app.jar
USER spring
EXPOSE 9085
ENV PORT=9085
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT} -jar /app/app.jar"]
