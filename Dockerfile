FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
COPY settings.xml /root/.m2/settings.xml
COPY maven-repo /root/.m2/repository
COPY src ./src

RUN mvn -s /root/.m2/settings.xml -o -Paliyun clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

RUN mkdir -p /app/books /app/logs

EXPOSE 8080

ENTRYPOINT ["java", "-Xmx4g", "-Duser.timezone=Asia/Shanghai", "-jar", "app.jar"]
