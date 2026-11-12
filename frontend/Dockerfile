FROM openapitools/openapi-generator-cli:v7.22.0 AS generator
WORKDIR /spec
COPY contracts/openapi.yaml ./openapi.yaml
RUN /usr/local/bin/docker-entrypoint.sh generate \
    -i openapi.yaml \
    -g typescript-axios \
    -o /generated \
    --additional-properties supportsES6=true,useSingleRequestParameter=true

FROM node:24-alpine AS build
WORKDIR /app/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --ignore-scripts --no-audit --no-fund

COPY frontend .
COPY --from=generator /generated ./src/api/generated

RUN npx vue-tsc -b && npx vite build

FROM nginx:1.27-alpine
COPY frontend/nginx/templates/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/frontend/dist /usr/share/nginx/html
ENV ATS_SERVICE_URL=http://ats-service:8080
EXPOSE 80
