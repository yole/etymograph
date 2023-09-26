FROM gradle:8.3-jdk17
WORKDIR /app
COPY . .
EXPOSE 8080
ENV ETYMOGRAPH_GRAPH_PATH=/app/jrrt.json
CMD ./gradlew bootRun --args='--etymograph.path=${ETYMOGRAPH_GRAPH_PATH} --server.address=0.0.0.0'
