# Imagen de ui-demo (specs/008-ui-demo/spec.md, RNF-UI-003).
#
# Primer contenedor de este proyecto que no es un bounded context de dominio:
# React+Vite construido en la primera etapa, servido por nginx en la segunda
# -- el mismo nginx sirve también de proxy curado (nginx.conf, §4 de la
# spec), así que el navegador nunca necesita conocer un puerto interno de
# docker-compose (RNF-UI-001).
FROM node:22-slim AS build
WORKDIR /workspace
COPY contexts/ui-demo/package.json contexts/ui-demo/package-lock.json ./
RUN npm ci
COPY contexts/ui-demo/ ./
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY contexts/ui-demo/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
