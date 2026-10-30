FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY backend backend
RUN mvn --batch-mode -pl backend/discovery-server -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/discovery-server/target/discovery-server-*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java","-jar","app.jar"]
