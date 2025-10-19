# React Application Deployment Guide

Complete guide for deploying React applications to CapRover using Jenkins shared library.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Configuration Files](#configuration-files)
- [Jenkins Setup](#jenkins-setup)
- [Deployment](#deployment)
- [Troubleshooting](#troubleshooting)
- [Examples](#examples)

---

## Prerequisites

Before you begin, ensure you have:

- ✅ React application (Create React App, Vite, Next.js, etc.)
- ✅ Jenkins server with Docker support
- ✅ CapRover server running and accessible
- ✅ Jenkins shared library configured
- ✅ CapRover app created (e.g., `my-react-app`)

---

## Project Structure

Your React project should follow this structure:

```
my-react-app/
├── .cicd/
│   ├── Dockerfile              # Docker build instructions
│   └── nginx.conf              # Nginx configuration
├── public/
│   ├── index.html
│   └── ...
├── src/
│   ├── components/
│   ├── App.tsx
│   └── ...
├── package.json
├── Jenkinsfile                 # Jenkins pipeline definition
└── captain-definition          # CapRover deployment config
```

---

## Configuration Files

### 1. Dockerfile (`.cicd/Dockerfile`)

Create a multi-stage Dockerfile for optimal production build:

#### For npm (Create React App or Vite):

```dockerfile
# Build stage
FROM node:18-alpine AS build
WORKDIR /app

# Copy package files (cached layer)
COPY package.json package-lock.json ./

# Install dependencies
RUN npm ci --only=production=false

# Copy source code
COPY . .

# Build for production
RUN npm run build

# Production stage
FROM nginx:alpine

# Copy custom nginx config
COPY .cicd/nginx.conf /etc/nginx/conf.d/default.conf

# Copy built assets from build stage
COPY --from=build /app/build /usr/share/nginx/html

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=3s CMD wget --quiet --tries=1 --spider http://localhost/health || exit 1

# Start nginx
CMD ["nginx", "-g", "daemon off;"]
```

#### For yarn:

```dockerfile
# Build stage
FROM node:18-alpine AS build
WORKDIR /app

# Copy package files (cached layer)
COPY package.json yarn.lock ./

# Install dependencies
RUN yarn install --frozen-lockfile

# Copy source code
COPY . .

# Build for production
RUN yarn build

# Production stage
FROM nginx:alpine

# Copy custom nginx config
COPY .cicd/nginx.conf /etc/nginx/conf.d/default.conf

# Copy built assets from build stage
COPY --from=build /app/build /usr/share/nginx/html

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=3s CMD wget --quiet --tries=1 --spider http://localhost/health || exit 1

# Start nginx
CMD ["nginx", "-g", "daemon off;"]
```

#### For Vite (Output to `dist` instead of `build`):

```dockerfile
# Build stage
FROM node:18-alpine AS build
WORKDIR /app

# Copy package files
COPY package.json package-lock.json ./
RUN npm ci

# Copy source and build
COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine

# Copy nginx config
COPY .cicd/nginx.conf /etc/nginx/conf.d/default.conf

# Copy built assets (Vite outputs to dist/)
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s CMD wget --quiet --tries=1 --spider http://localhost/health || exit 1
CMD ["nginx", "-g", "daemon off;"]
```

### 2. nginx.conf (`.cicd/nginx.conf`)

Create an optimized Nginx configuration:

```nginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    # Enable gzip compression for better performance
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_types
        text/plain
        text/css
        text/xml
        text/javascript
        application/x-javascript
        application/xml+rss
        application/javascript
        application/json
        application/vnd.ms-fontobject
        application/x-font-ttf
        font/opentype
        image/svg+xml
        image/x-icon;

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "no-referrer-when-downgrade" always;
    add_header Content-Security-Policy "default-src 'self' https:; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline';" always;

    # Cache static assets aggressively
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot|otf)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        access_log off;
    }

    # Don't cache index.html to ensure users get latest version
    location = /index.html {
        expires -1;
        add_header Cache-Control "no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0";
    }

    # React Router support - serve index.html for all routes
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Health check endpoint
    location /health {
        access_log off;
        return 200 "healthy\n";
        add_header Content-Type text/plain;
    }

    # API proxy (if your React app calls a backend API)
    # Uncomment and configure if needed
    # location /api/ {
    #     proxy_pass http://srv-captain--backend-api:8080/;
    #     proxy_http_version 1.1;
    #     proxy_set_header Upgrade $http_upgrade;
    #     proxy_set_header Connection 'upgrade';
    #     proxy_set_header Host $host;
    #     proxy_cache_bypass $http_upgrade;
    #     proxy_set_header X-Real-IP $remote_addr;
    #     proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    #     proxy_set_header X-Forwarded-Proto $scheme;
    # }
}
```

### 3. captain-definition (Root Directory)

Create `captain-definition` in your project root:

#### Basic Configuration:

```json
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile"
}
```

#### Advanced Configuration with Environment Variables:

```json
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile",
  "captainServiceName": "my-react-app",
  "envVars": {},
  "volumes": [],
  "containerHttpPort": "80"
}
```

**Note:** Environment variables for React apps are set at **build time**, not runtime. Use build args in Dockerfile.

#### With Build-Time Environment Variables:

Update your Dockerfile to accept build args:

```dockerfile
# Build stage
FROM node:18-alpine AS build
WORKDIR /app

# Build arguments
ARG REACT_APP_API_URL
ARG REACT_APP_ENV

# Set as environment variables for build
ENV REACT_APP_API_URL=$REACT_APP_API_URL
ENV REACT_APP_ENV=$REACT_APP_ENV

COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine
COPY .cicd/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

And in `captain-definition`:

```json
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile",
  "captainServiceName": "my-react-app",
  "dockerfileBuildArgs": {
    "REACT_APP_API_URL": "https://api.yourdomain.com",
    "REACT_APP_ENV": "production"
  },
  "containerHttpPort": "80"
}
```

### 4. Jenkinsfile (Root Directory)

Create `Jenkinsfile` in your project root:

#### Minimal Configuration:

```groovy
@Library('jenkins-shared-library') _

reactPipeline(
    applicationName: 'my-react-app'
)
```

#### Full Configuration:

```groovy
@Library('jenkins-shared-library') _

reactPipeline(
    // Required
    applicationName: 'noreyni-dashboard',

    // CapRover Configuration
    caproverUrl: env.CAPROVER_URL,
    caproverPassword: env.CAPROVER_PASSWORD,

    // Git Configuration
    gitBranch: env.GIT_BRANCH,

    // Notifications
    notificationEmails: 'frontend-team@noreyni.com;devops@noreyni.com',
    fromEmail: 'jenkins@noreyni.com',

    // Advanced (optional)
    dockerImage: 'ouznoreyni/docker-node-alpine-22-git:latest',
    pipelineTimeout: 30
)
```

---

## Jenkins Setup

### 1. Configure Jenkins Environment Variables

Go to **Jenkins → Manage Jenkins → Configure System → Global Properties → Environment Variables**

Add these variables:

| Variable | Value | Description |
|----------|-------|-------------|
| `CAPROVER_URL` | `captain.yourdomain.com` | Your CapRover server URL |
| `CAPROVER_PASSWORD` | `your-password` | CapRover password |
| `NOTIFICATION_EMAILS` | `team@company.com` | Semicolon-separated emails |
| `FROM_EMAIL` | `jenkins@company.com` | Email sender address |

### 2. Create Jenkins Pipeline Job

1. **New Item** → Enter name → **Pipeline** → OK
2. Under **Pipeline**:
   - Definition: `Pipeline script from SCM`
   - SCM: `Git`
   - Repository URL: `https://github.com/yourorg/your-react-app.git`
   - Branch: `*/main`
   - Script Path: `Jenkinsfile`
3. **Save**

---

## Deployment

### First Time Deployment

1. **Create App in CapRover:**
   ```bash
   # Login to CapRover dashboard
   # Apps → Create New App
   # App Name: my-react-app (must match Jenkinsfile applicationName)
   ```

2. **Enable HTTPS (Recommended):**
   ```
   Dashboard → Apps → my-react-app → HTTP Settings
   ✓ Enable HTTPS
   ✓ Force HTTPS by redirecting all HTTP traffic to HTTPS
   ```

3. **Commit Your Code:**
   ```bash
   mkdir -p .cicd
   # Create .cicd/Dockerfile and .cicd/nginx.conf
   # Create captain-definition and Jenkinsfile

   git add .cicd/ captain-definition Jenkinsfile
   git commit -m "Add CapRover deployment configuration"
   git push origin main
   ```

4. **Trigger Jenkins Build:**
   - Go to Jenkins → Your Job → **Build Now**

### Subsequent Deployments

Simply push to your repository:

```bash
git add .
git commit -m "Your changes"
git push origin main
```

---

## Environment Variables in React

### Create React App (.env files)

Create `.env.production`:

```env
# API Configuration
REACT_APP_API_URL=https://api.yourdomain.com
REACT_APP_API_TIMEOUT=5000

# Feature Flags
REACT_APP_ENABLE_ANALYTICS=true
REACT_APP_ENABLE_LOGGING=false

# App Configuration
REACT_APP_ENV=production
REACT_APP_VERSION=$npm_package_version
```

Access in your React code:

```typescript
// src/config/api.ts
export const API_CONFIG = {
  baseURL: process.env.REACT_APP_API_URL,
  timeout: parseInt(process.env.REACT_APP_API_TIMEOUT || '5000'),
};

// src/App.tsx
console.log('App version:', process.env.REACT_APP_VERSION);
```

### Vite (.env files)

Create `.env.production`:

```env
# Must prefix with VITE_
VITE_API_URL=https://api.yourdomain.com
VITE_APP_TITLE=My Awesome App
VITE_ENABLE_ANALYTICS=true
```

Access in your code:

```typescript
// src/config.ts
export const config = {
  apiUrl: import.meta.env.VITE_API_URL,
  appTitle: import.meta.env.VITE_APP_TITLE,
  analyticsEnabled: import.meta.env.VITE_ENABLE_ANALYTICS === 'true',
};
```

### Runtime Configuration (Advanced)

For runtime environment variables, create a config file served by Nginx:

**public/config.js:**
```javascript
window.APP_CONFIG = {
  API_URL: '%%API_URL%%',
  ENV: '%%ENV%%',
};
```

**Update nginx.conf:**
```nginx
location = /config.js {
    add_header Cache-Control "no-store, no-cache, must-revalidate";
    expires -1;
}
```

**Use in React:**
```typescript
// src/config/runtime.ts
declare global {
  interface Window {
    APP_CONFIG: {
      API_URL: string;
      ENV: string;
    };
  }
}

export const runtimeConfig = window.APP_CONFIG;
```

---

## Troubleshooting

### Build Fails: "MODULE_NOT_FOUND"

**Problem:** Dependencies not installed

**Solution:** Check your `package-lock.json` or `yarn.lock` is committed:
```bash
git add package-lock.json
git commit -m "Add lockfile"
git push
```

### Blank Page After Deployment

**Problem:** Routes not working or assets not loading

**Solution 1:** Check nginx.conf has React Router support:
```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

**Solution 2:** Check your `package.json` homepage field:
```json
{
  "homepage": ".",
  ...
}
```

### API Calls Fail with CORS Error

**Problem:** Backend API not allowing requests

**Solution:** Add proxy in nginx.conf:
```nginx
location /api/ {
    proxy_pass http://srv-captain--backend-api:8080/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

### Environment Variables Not Working

**Problem:** `process.env.REACT_APP_*` is undefined

**Solutions:**

1. **Ensure proper prefix:**
   - Create React App: `REACT_APP_`
   - Vite: `VITE_`

2. **Set at build time:**
```dockerfile
ARG REACT_APP_API_URL
ENV REACT_APP_API_URL=$REACT_APP_API_URL
```

3. **Check `.env.production` exists**

### Assets Return 404

**Problem:** Static files not found

**Solution:** Check build output directory matches Dockerfile:
```dockerfile
# For CRA (outputs to build/)
COPY --from=build /app/build /usr/share/nginx/html

# For Vite (outputs to dist/)
COPY --from=build /app/dist /usr/share/nginx/html
```

---

## Examples

### Example 1: Simple Create React App

**Project:** `my-dashboard`

**.cicd/Dockerfile:**
```dockerfile
FROM node:18-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY .cicd/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK CMD wget --quiet --tries=1 --spider http://localhost/health || exit 1
CMD ["nginx", "-g", "daemon off;"]
```

**.cicd/nginx.conf:**
```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /health {
        return 200 "healthy\n";
        add_header Content-Type text/plain;
    }
}
```

**captain-definition:**
```json
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile",
  "containerHttpPort": "80"
}
```

**Jenkinsfile:**
```groovy
@Library('jenkins-shared-library') _

reactPipeline(
    applicationName: 'my-dashboard'
)
```

### Example 2: Vite + TypeScript with API

**Project:** `noreyni-dashboard`

**.cicd/Dockerfile:**
```dockerfile
FROM node:18-alpine AS build
WORKDIR /app

ARG VITE_API_URL
ARG VITE_APP_TITLE
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_APP_TITLE=$VITE_APP_TITLE

COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY .cicd/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK CMD wget --quiet --tries=1 --spider http://localhost/health || exit 1
CMD ["nginx", "-g", "daemon off;"]
```

**.cicd/nginx.conf:**
```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    gzip on;
    gzip_vary on;
    gzip_comp_level 6;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml;

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;

    # Cache static assets
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # Don't cache index.html
    location = /index.html {
        expires -1;
        add_header Cache-Control "no-cache";
    }

    # React Router
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Proxy API requests to backend
    location /api/ {
        proxy_pass http://srv-captain--noreyni-api:7780/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Health check
    location /health {
        access_log off;
        return 200 "healthy\n";
        add_header Content-Type text/plain;
    }
}
```

**captain-definition:**
```json
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile",
  "captainServiceName": "noreyni-dashboard",
  "dockerfileBuildArgs": {
    "VITE_API_URL": "https://api.yourcompany.com",
    "VITE_APP_TITLE": "Noreyni Dashboard"
  },
  "containerHttpPort": "80"
}
```

**Jenkinsfile:**
```groovy
@Library('jenkins-shared-library') _

reactPipeline(
    applicationName: 'noreyni-dashboard',
    notificationEmails: 'frontend-team@company.com;product@company.com',
    pipelineTimeout: 20
)
```

**vite.config.ts:**
```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:7780',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom', 'react-router-dom'],
        },
      },
    },
  },
});
```

**.env.production:**
```env
VITE_API_URL=https://api.yourcompany.com
VITE_APP_TITLE=Noreyni Dashboard
VITE_APP_VERSION=1.0.0
```

### Example 3: Next.js Application

**Project:** `nextjs-app`

**.cicd/Dockerfile:**
```dockerfile
FROM node:18-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:18-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:18-alpine AS runner
WORKDIR /app

ENV NODE_ENV production

RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000

ENV PORT 3000

CMD ["node", "server.js"]
```

**next.config.js:**
```javascript
/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  reactStrictMode: true,
  swcMinify: true,
};

module.exports = nextConfig;
```

**captain-definition:**
```json
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile",
  "containerHttpPort": "3000"
}
```

---

## Best Practices

### 1. Optimize Build Size
```json
// package.json
{
  "scripts": {
    "build": "react-scripts build",
    "analyze": "source-map-explorer 'build/static/js/*.js'"
  }
}
```

### 2. Use Code Splitting
```typescript
// Lazy load routes
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Settings = lazy(() => import('./pages/Settings'));

<Suspense fallback={<Loading />}>
  <Routes>
    <Route path="/dashboard" element={<Dashboard />} />
    <Route path="/settings" element={<Settings />} />
  </Routes>
</Suspense>
```

### 3. Add Service Worker (PWA)
```typescript
// src/index.tsx
import * as serviceWorkerRegistration from './serviceWorkerRegistration';

serviceWorkerRegistration.register();
```

### 4. Monitor Bundle Size
Add to CI/CD:
```dockerfile
RUN npm run build
RUN ls -lh build/static/js/
```

### 5. Security Headers
Always include in nginx.conf:
```nginx
add_header X-Frame-Options "SAMEORIGIN" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-XSS-Protection "1; mode=block" always;
```

---

## Additional Resources

- [React Documentation](https://react.dev/)
- [Vite Documentation](https://vitejs.dev/)
- [Nginx Documentation](https://nginx.org/en/docs/)
- [CapRover Documentation](https://caprover.com/docs)

---

## Support

For issues or questions:
1. Check Jenkins build logs
2. Review CapRover deployment logs
3. Verify nginx configuration
4. Test locally with Docker
5. Contact DevOps team

**Happy Deploying! 🚀**
