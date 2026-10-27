# blog-server 可执行 jar 镜像（构建上下文：backend/blog/blog-server，Jenkins 已先 mvn package）
# 产物 jar 名为 blog-service.jar（finalName 锁定，兼容历史运维/CI 脚本）
# profile 由 docker run 追加参数传入：--spring.profiles.active=test
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 19100
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
