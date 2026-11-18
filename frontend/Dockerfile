# syntax=docker/dockerfile:1
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ARG NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_SITE_URL
ARG NEXT_PUBLIC_CDN_URL
ARG NEXT_PUBLIC_STORAGE_ORIGIN
ARG NEXT_PUBLIC_TURNSTILE_SITE_KEY
RUN npm run build

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup -S ngbcl && adduser -S ngbcl -G ngbcl
COPY --from=build --chown=ngbcl:ngbcl /app ./
USER ngbcl
EXPOSE 3000
CMD ["npm", "start"]
