FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B package -DskipTests
FROM eclipse-temurin:17-jre-jammy
RUN groupadd --gid 10001 zem && useradd --uid 10001 --gid zem --no-create-home zem && mkdir /data && chown zem:zem /data && chmod 700 /data
WORKDIR /app
COPY --from=build /build/target/zem-api-0.1.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
