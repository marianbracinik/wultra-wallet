FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY wallet-api ./wallet-api
COPY rate-download-client ./rate-download-client
COPY wallet-db ./wallet-db
COPY wallet ./wallet
RUN mvn -B verify

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S wallet && adduser -S wallet -G wallet \
    && mkdir -p /app/data && chown -R wallet:wallet /app
WORKDIR /app
COPY --from=build /build/wallet/target/wallet-1.0-SNAPSHOT.jar app.jar
USER wallet
VOLUME /app/data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
