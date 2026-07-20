FROM node:22
WORKDIR /app
COPY . .
RUN npm ci
CMD npx next build && cp -r /app/out /export
