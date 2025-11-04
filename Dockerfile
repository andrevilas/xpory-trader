FROM gradle:5.6.4-jdk8 AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon clean assemble

FROM eclipse-temurin:8-jre
WORKDIR /opt/cpservice
ENV JAVA_OPTS=""
COPY --from=build /workspace/build/libs/*.war app.war
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.war"]
