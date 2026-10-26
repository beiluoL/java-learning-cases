# 后端镜像构建（可选）
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw -q -DskipTests package || mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/ecommerce-learn.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
