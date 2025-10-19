# Spring Boot Application Deployment Guide

Complete guide for deploying Spring Boot applications to CapRover using Jenkins shared library.

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

- ✅ Spring Boot application with Maven or Gradle
- ✅ Jenkins server with Docker support
- ✅ CapRover server running and accessible
- ✅ Jenkins shared library configured
- ✅ CapRover app created (e.g., `my-spring-api`)

---

## Project Structure

Your Spring Boot project should follow this structure:

```
my-spring-boot-app/
├── .cicd/
│   └── Dockerfile              # Docker build instructions
├── src/
│   ├── main/
│   │   ├── java/
│   │   └── resources/
│   │       └── application.yml
│   └── test/
├── pom.xml                     # Maven configuration
├── Jenkinsfile                 # Jenkins pipeline definition
└── captain-definition          # CapRover deployment config
```

---

## Configuration Files

### 1. Dockerfile (`.cicd/Dockerfile`)

Create a multi-stage Dockerfile for optimal build size:

#### For Maven Projects:

```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -Pprod

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for health check
RUN apk add --no-cache curl

# Copy built artifact from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check (optional but recommended)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

#### For Gradle Projects:

```dockerfile
# Build stage
FROM gradle:8.5-jdk21 AS build
WORKDIR /app

# Copy gradle files (cached layer)
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Download dependencies
RUN ./gradlew dependencies --no-daemon

# Copy source code and build
COPY src ./src
RUN ./gradlew clean build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for health check
RUN apk add --no-cache curl

# Copy built artifact from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### 2. captain-definition (Root Directory)

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
  "captainServiceName": "my-spring-api",
  "envVars": {
    "SPRING_PROFILES_ACTIVE": "prod",
    "JAVA_OPTS": "-Xms512m -Xmx1024m",
    "TZ": "UTC"
  },
  "volumes": [
    {
      "containerPath": "/app/logs",
      "hostPath": "/var/lib/docker/volumes/my-spring-api-logs/_data"
    }
  ],
  "containerHttpPort": "8080"
}
```

#### With Database Connection:

```json
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile",
  "captainServiceName": "noreyni-api",
  "envVars": {
    "SPRING_PROFILES_ACTIVE": "prod",
    "JAVA_OPTS": "-Xms512m -Xmx1024m",
    "DB_HOST": "srv-captain--postgres",
    "JWT_SECRET": "%%SECRET_JWT_SECRET%%",
    "TZ": "Africa/Dakar"
  },
  "volumes": [],
  "containerHttpPort": "7780"
}
```

**Note:** Use `%%SECRET_NAME%%` for sensitive data like JWT_SECRET. Set these in CapRover dashboard under App Configs → Environment Variables.

### 3. Jenkinsfile (Root Directory)

Create `Jenkinsfile` in your project root:

#### Minimal Configuration:

```groovy
@Library('jenkins-shared-library') _

springBootPipeline(
    applicationName: 'my-spring-api'
)
```

#### Full Configuration:

```groovy
@Library('jenkins-shared-library') _

springBootPipeline(
    // Required
    applicationName: 'noreyni-api',

    // CapRover Configuration
    caproverUrl: env.CAPROVER_URL,
    caproverPassword: env.CAPROVER_PASSWORD,

    // Git Configuration
    gitBranch: env.GIT_BRANCH,

    // Notifications
    notificationEmails: 'backend-team@noreyni.com;devops@noreyni.com',
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

**Security Best Practice:** Use Jenkins Credentials Plugin:

```groovy
springBootPipeline(
    applicationName: 'my-api',
    caproverPassword: credentials('caprover-password-id')
)
```

### 2. Create Jenkins Pipeline Job

1. **New Item** → Enter name → **Pipeline** → OK
2. Under **Pipeline**:
   - Definition: `Pipeline script from SCM`
   - SCM: `Git`
   - Repository URL: `https://github.com/ouznoreyni/your-spring-app.git`
   - Branch: `*/main`
   - Script Path: `Jenkinsfile`
3. **Save**

### 3. Configure Webhooks (Optional)

For automatic builds on push:

**GitHub:**
- Repository → Settings → Webhooks → Add webhook
- Payload URL: `https://jenkins.yourdomain.com/github-webhook/`
- Content type: `application/json`
- Events: `Just the push event`

**GitLab:**
- Project → Settings → Webhooks
- URL: `https://jenkins.yourdomain.com/project/your-job-name`
- Trigger: `Push events`

---

## Deployment

### First Time Deployment

1. **Create App in CapRover:**
   ```bash
   # Login to CapRover dashboard
   # Apps → Create New App
   # App Name: my-spring-api (must match Jenkinsfile applicationName)
   ```

2. **Set Secrets in CapRover (if needed):**
   ```
   Dashboard → Apps → my-spring-api → App Configs → Environment Variables

   Add any secrets like:
   - SECRET_JWT_SECRET: your-jwt-secret
   - SECRET_API_KEY: your-api-key
   ```

3. **Commit Your Code:**
   ```bash
   git add .cicd/Dockerfile captain-definition Jenkinsfile
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

Jenkins will automatically:
1. ✅ Install CapRover CLI
2. ✅ Verify captain-definition exists
3. ✅ Validate deployment credentials
4. ✅ Deploy to CapRover
5. ✅ CapRover builds using your Dockerfile
6. ✅ Send email notification

---

## Application Configuration

### application.yml (Recommended)

Use environment variables in `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: noreyni-api

  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/myapp
    username: postgres
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true

server:
  port: ${SERVER_PORT:7780}

jwt:
  secret: ${JWT_SECRET:default-secret-key-change-in-production}
  expiration: 86400000

logging:
  level:
    root: INFO
    com.yourcompany: DEBUG
  file:
    name: /app/logs/application.log
```

### Profile-Specific Configuration

Create `application-prod.yml`:

```yaml
spring:
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate

logging:
  level:
    root: WARN
    com.yourcompany: INFO
```

---

## Troubleshooting

### Pipeline Fails: "captain-definition file not found"

**Problem:** Missing captain-definition file

**Solution:**
```bash
# Create captain-definition in project root
cat > captain-definition << 'EOF'
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile"
}
EOF

git add captain-definition
git commit -m "Add captain-definition"
git push
```

### Pipeline Fails: "Missing required deployment credentials"

**Problem:** Jenkins environment variables not set

**Solution:**
1. Go to Jenkins → Manage Jenkins → Configure System
2. Scroll to Global Properties → Environment Variables
3. Add `CAPROVER_URL` and `CAPROVER_PASSWORD`
4. Save and retry build

### Build Fails: "No such file or directory: Dockerfile"

**Problem:** Dockerfile not in `.cicd` folder

**Solution:**
```bash
mkdir -p .cicd
# Move or create Dockerfile in .cicd/
git add .cicd/Dockerfile
git commit -m "Move Dockerfile to .cicd folder"
git push
```

### Application Crashes After Deployment

**Problem:** Missing environment variables or secrets

**Solution:**
1. Check CapRover logs:
   ```
   Dashboard → Apps → your-app → App Logs
   ```

2. Verify environment variables:
   ```
   Dashboard → Apps → your-app → App Configs → Environment Variables
   ```

3. Check health endpoint:
   ```bash
   curl https://your-app.yourdomain.com/actuator/health
   ```

### Database Connection Issues

**Problem:** Can't connect to database

**Solution:**

1. **Check service name:**
   ```
   CapRover apps use: srv-captain--app-name
   Example: srv-captain--postgres
   ```

2. **Update DB_HOST:**
   ```json
   "envVars": {
     "DB_HOST": "srv-captain--postgres"
   }
   ```

3. **Verify network:**
   ```
   Apps on same CapRover can communicate using service names
   ```

---

## Examples

### Example 1: Simple Spring Boot REST API

**Project:** `simple-api`

**.cicd/Dockerfile:**
```dockerfile
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
```

**captain-definition:**
```json
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile",
  "containerHttpPort": "8080"
}
```

**Jenkinsfile:**
```groovy
@Library('jenkins-shared-library') _

springBootPipeline(
    applicationName: 'simple-api'
)
```

### Example 2: Microservice with Database

**Project:** `user-service`

**.cicd/Dockerfile:**
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -Pprod

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**captain-definition:**
```json
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile",
  "captainServiceName": "user-service",
  "envVars": {
    "SPRING_PROFILES_ACTIVE": "prod",
    "JAVA_OPTS": "-Xms256m -Xmx512m",
    "DB_HOST": "srv-captain--postgres"
  },
  "containerHttpPort": "8080"
}
```

**Jenkinsfile:**
```groovy
@Library('jenkins-shared-library') _

springBootPipeline(
    applicationName: 'user-service',
    notificationEmails: 'backend-team@company.com'
)
```

## Best Practices

### 1. Use Multi-Stage Builds
- ✅ Smaller final image size
- ✅ Faster deployments
- ✅ Better security (no build tools in production)

### 2. Health Checks
Always add health checks:
```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
```

Add Spring Boot Actuator:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 3. Environment Variables
- ✅ Never hardcode credentials
- ✅ Use CapRover secrets for sensitive data
- ✅ Provide defaults for development

### 4. Logging
Configure logging to files for persistence:
```yaml
logging:
  file:
    name: /app/logs/application.log
  pattern:
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

### 5. Resource Limits
Set appropriate JVM memory:
```json
"JAVA_OPTS": "-Xms512m -Xmx1024m -XX:+UseG1GC"
```

---

## Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [CapRover Documentation](https://caprover.com/docs)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Jenkins Pipeline Syntax](https://www.jenkins.io/doc/book/pipeline/syntax/)

---

## Support

For issues or questions:
1. Check Jenkins build logs
2. Review CapRover deployment logs
3. Verify configuration files
4. Contact DevOps team

**Happy Deploying! 🚀**
