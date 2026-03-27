FROM maven:3.9.11-eclipse-temurin-17 AS builder

WORKDIR /workspace

COPY pom.xml settings.xml ./
COPY maven-repo ./maven-repo
COPY src ./src

RUN mvn -q -s settings.xml -Dmaven.repo.local=/workspace/maven-repo -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /workspace/target/words-1.0.0.jar app.jar

RUN mkdir -p /app/books /app/logs

EXPOSE 8080

ENTRYPOINT ["java", "-Xmx4g", "-Duser.timezone=Asia/Shanghai", "-jar", "app.jar"]
