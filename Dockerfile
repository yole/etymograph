FROM gradle:7.6.1-jdk17
WORKDIR /app
COPY . .
EXPOSE 8080
CMD ./gradlew bootRun --args='--etymograph.path=/app/jrrt.json --server.address=0.0.0.0'
