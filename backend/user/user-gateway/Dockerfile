# user-gateway 可执行 jar 镜像（构建上下文：backend/user/user-gateway，Jenkins 已先 mvn package）
# profile 由 docker run 追加参数传入：--spring.profiles.active=test
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8173
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
