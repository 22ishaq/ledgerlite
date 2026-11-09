FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# dependencies first so they're cached unless the pom changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 ledgerlite
WORKDIR /app
COPY --from=build /src/target/ledgerlite-*.jar app.jar
USER ledgerlite
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
