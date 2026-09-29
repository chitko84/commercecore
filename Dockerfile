FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN ./mvnw -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/target/commercecore.jar /app/commercecore.jar
ENV APP_MAIN=com.commercecore.CommerceCoreApplication
ENTRYPOINT ["sh","-c","exec java -Dloader.main=${APP_MAIN} -jar /app/commercecore.jar"]
