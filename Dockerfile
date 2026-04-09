FROM gradle:8.3-jdk17
WORKDIR /app
COPY . .
EXPOSE 8080
ENV ETYMOGRAPH_GRAPH_PATH=/app/jrrt.json
ENV GOOGLE_CLIENT_ID=_
ENV GOOGLE_CLIENT_SECRET=_
CMD ./gradlew bootRun --args='--etymograph.path=${ETYMOGRAPH_GRAPH_PATH} --server.address=0.0.0.0 --spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID} --spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}'
