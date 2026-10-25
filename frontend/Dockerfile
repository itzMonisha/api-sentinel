# ==============================================================================
# API Sentinel — Production Multi-Stage Frontend Dockerfile
# Stage 1: Build static distribution using Node 20
# Stage 2: Serve optimized assets via high-performance Nginx Alpine
# ==============================================================================

FROM node:20-alpine AS builder

WORKDIR /app

# Copy package manifests first to leverage Docker layer cache
COPY package.json package-lock.json ./
RUN npm ci

# Copy source code and build production bundle
COPY . .
ARG VITE_API_URL=""
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

# ==============================================================================
# Production Runtime Stage (Nginx)
# ==============================================================================
FROM nginx:alpine-slim AS runner

# Remove default nginx welcome page
RUN rm -rf /usr/share/nginx/html/*

# Copy custom production nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy production bundle from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q --spider http://localhost/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
