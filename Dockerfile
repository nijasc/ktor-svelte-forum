FROM eclipse-temurin:21-jdk AS build
COPY --from=node:24-slim /usr/local/bin/node /usr/local/bin/node
COPY --from=node:24-slim /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm
RUN npm install -g bun
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew buildFatJar --no-daemon --console=plain
RUN cp app/build/libs/*-all.jar /app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home app
COPY --from=build /app.jar /app.jar
USER app
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
