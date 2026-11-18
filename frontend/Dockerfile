# syntax=docker/dockerfile:1

# ---- build: installs exactly the locked dependencies and builds the production bundle ----
# Runs on the machine that builds ($BUILDPLATFORM): the bundle is static files, the same for any architecture.
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---- runtime: static files served by nginx, which also proxies /api to the back-end ----
FROM nginx:1.29-alpine
# host:port of the API inside the Docker network
ENV BACKEND_HOST=backend:8080
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1/nginx-health || exit 1
