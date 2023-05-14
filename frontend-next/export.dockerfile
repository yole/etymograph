FROM node:20
WORKDIR /app
COPY . .
RUN npm ci
CMD npx next build
