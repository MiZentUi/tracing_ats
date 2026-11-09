FROM node:24-alpine AS build
# openapi-generator-cli запускается на JVM.
RUN apk add --no-cache openjdk17-jre
WORKDIR /app

COPY frontend/package.json frontend/package-lock.json ./frontend/
WORKDIR /app/frontend
RUN npm ci --no-audit --no-fund

COPY contracts /app/contracts
COPY frontend /app/frontend
RUN npm run build

FROM nginx:1.27-alpine
COPY frontend/nginx/templates/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/frontend/dist /usr/share/nginx/html
ENV VACANCY_SERVICE_URL=http://vacancy-service:8080
EXPOSE 80
