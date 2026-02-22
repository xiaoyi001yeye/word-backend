FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM amazoncorretto:17-alpine-jdk

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

RUN mkdir -p /opt/translation

EXPOSE 8080

ENTRYPOINT ["java", "-Xmx4g", "-Duser.timezone=Asia/Shanghai", "-jar", "app.jar"]
