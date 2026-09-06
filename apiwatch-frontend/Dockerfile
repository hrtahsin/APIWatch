# syntax=docker/dockerfile:1.7

FROM node:22-alpine@sha256:16e22a550f3863206a3f701448c45f7912c6896a62de43add43bb9c86130c3e2 AS build
WORKDIR /app
ARG VITE_API_BASE_URL=/api
ARG VITE_DEMO_MODE=false
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_DEMO_MODE=$VITE_DEMO_MODE
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.31-alpine@sha256:d9083fe47768377ef55dedafd67d4da7c2f2bc2bece7554954f29359deb0dce9
ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.title="APIWatch Frontend" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.source="https://github.com/hrtahsin/APIWatch"
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
