FROM eclipse-temurin:17-jre

WORKDIR /app

COPY target/words-1.0.0.jar app.jar

RUN mkdir -p /app/books /app/logs

EXPOSE 8080

ENTRYPOINT ["java", "-Xmx4g", "-Duser.timezone=Asia/Shanghai", "-jar", "app.jar"]
