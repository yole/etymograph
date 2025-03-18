FROM node:22
WORKDIR /app
COPY . .
RUN npm ci
RUN grep -rli --include \*.js 'unstable_runtimeJS: true' pages | xargs -i@ sed -i 's/unstable_runtimeJS: true/unstable_runtimeJS: false/g' @
CMD npx next build && cp -r /app/out /export
