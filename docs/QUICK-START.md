# Quick Start Guide

Fast-track deployment guide for Spring Boot and React applications.

## 📋 Checklist

Before you start:
- [ ] Jenkins server with Docker
- [ ] CapRover server running
- [ ] Jenkins shared library configured
- [ ] CapRover app created

---

## 🚀 Spring Boot Quick Start

### 1. Create Files

```bash
# Create .cicd directory
mkdir -p .cicd

# Create Dockerfile
cat > .cicd/Dockerfile << 'EOF'
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

# Create captain-definition
cat > captain-definition << 'EOF'
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile",
  "containerHttpPort": "8080"
}
EOF

# Create Jenkinsfile
cat > Jenkinsfile << 'EOF'
@Library('jenkins-shared-library') _

springBootPipeline(
    applicationName: 'my-spring-api'
)
EOF
```

### 2. Commit & Deploy

```bash
git add .cicd/ captain-definition Jenkinsfile
git commit -m "Add CapRover deployment"
git push origin main
```

### 3. Trigger Jenkins Build

Jenkins → Your Job → **Build Now**

✅ Done! Your Spring Boot app is deployed.

---

## ⚛️ React Quick Start

### 1. Create Files

```bash
# Create .cicd directory
mkdir -p .cicd

# Create Dockerfile
cat > .cicd/Dockerfile << 'EOF'
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
CMD ["nginx", "-g", "daemon off;"]
EOF

# Create nginx.conf
cat > .cicd/nginx.conf << 'EOF'
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript;

    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    location = /index.html {
        expires -1;
        add_header Cache-Control "no-cache";
    }

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /health {
        return 200 "healthy\n";
        add_header Content-Type text/plain;
    }
}
EOF

# Create captain-definition
cat > captain-definition << 'EOF'
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile",
  "containerHttpPort": "80"
}
EOF

# Create Jenkinsfile
cat > Jenkinsfile << 'EOF'
@Library('jenkins-shared-library') _

reactPipeline(
    applicationName: 'my-react-app'
)
EOF
```

### 2. Commit & Deploy

```bash
git add .cicd/ captain-definition Jenkinsfile
git commit -m "Add CapRover deployment"
git push origin main
```

### 3. Trigger Jenkins Build

Jenkins → Your Job → **Build Now**

✅ Done! Your React app is deployed.

---

## 📁 Final Project Structure

### Spring Boot
```
my-spring-boot-app/
├── .cicd/
│   └── Dockerfile
├── src/
├── pom.xml
├── captain-definition
└── Jenkinsfile
```

### React
```
my-react-app/
├── .cicd/
│   ├── Dockerfile
│   └── nginx.conf
├── src/
├── package.json
├── captain-definition
└── Jenkinsfile
```

---

## 🔧 Configuration Templates

### Spring Boot with Database

**captain-definition:**
```json
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile",
  "envVars": {
    "SPRING_PROFILES_ACTIVE": "prod",
    "DB_HOST": "srv-captain--postgres",
    "DB_PORT": "5432",
    "DB_NAME": "mydb",
    "DB_USERNAME": "user",
    "DB_PASSWORD": "%%SECRET_DB_PASSWORD%%"
  },
  "containerHttpPort": "8080"
}
```

### React with API Proxy

**.cicd/nginx.conf:**
```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # Proxy API calls
    location /api/ {
        proxy_pass http://srv-captain--backend-api:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # React Router
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

---

## 🎯 Common Commands

### CapRover App Management
```bash
# Create app in CapRover dashboard
Apps → Create New App → Enter name

# Enable HTTPS
Apps → Your App → HTTP Settings → Enable HTTPS

# View logs
Apps → Your App → App Logs

# Environment variables
Apps → Your App → App Configs → Environment Variables
```

### Jenkins
```bash
# Manual build
Your Job → Build Now

# View console output
Your Job → Last Build → Console Output

# Configure webhooks for auto-deploy
Repository → Settings → Webhooks → Add webhook
URL: https://jenkins.yourdomain.com/github-webhook/
```

### Local Testing
```bash
# Test Docker build locally
docker build -f .cicd/Dockerfile -t myapp .
docker run -p 8080:8080 myapp

# Test React build
npm run build
npx serve -s build
```

---

## ❓ Troubleshooting Quick Fixes

### "captain-definition file not found"
```bash
# Ensure it's in project root
ls -la captain-definition
git add captain-definition
git commit -m "Add captain-definition"
git push
```

### "Missing required deployment credentials"
```
Jenkins → Manage Jenkins → Configure System
→ Global Properties → Environment Variables
→ Add: CAPROVER_URL and CAPROVER_PASSWORD
```

### Build fails with dependency errors
```bash
# Ensure lock files are committed
git add package-lock.json  # or yarn.lock
git add pom.xml           # or build.gradle
git commit -m "Add lock files"
git push
```

### React app shows blank page
```nginx
# Add to nginx.conf
location / {
    try_files $uri $uri/ /index.html;
}
```

---

## 📚 Full Documentation

For detailed guides:
- [Spring Boot Deployment Guide](./SPRING-BOOT-DEPLOYMENT.md)
- [React Deployment Guide](./REACT-DEPLOYMENT.md)
- [Main README](../README.md)

---

## 💡 Pro Tips

1. **Always use `.cicd/` folder** - Keeps deployment files organized
2. **Commit lock files** - Ensures consistent builds
3. **Enable HTTPS in CapRover** - Secure your apps
4. **Use environment variables** - Never hardcode credentials
5. **Add health checks** - Monitor app status
6. **Set up webhooks** - Auto-deploy on push
7. **Check logs** - CapRover dashboard has live logs

---

**Need help?** Check the full documentation or contact DevOps team.

**Happy Deploying! 🚀**
