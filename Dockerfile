# Stage 1: Build with minimal JDK
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
# Кэшируем зависимости
RUN mvn dependency:go-offline -B -Dmaven.wagon.http.retryHandler.count=3
COPY src ./src
# Собираем без документации и тестов
RUN mvn package -DskipTests -Dmaven.javadoc.skip=true -B -q

# Stage 2: Минимальный runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Настройка безопасности и прав
RUN addgroup -S appuser && adduser -S appuser -G appuser \
    && chown -R appuser:appuser /app \
    && mkdir -p /var/log/app \
    && chown -R appuser:appuser /var/log/app

USER appuser

COPY --from=build --chown=appuser:appuser /app/target/*.jar app.jar

ENTRYPOINT ["java", "-XX:+UseZGC", "-Xmx256m", "-jar", "app.jar"]