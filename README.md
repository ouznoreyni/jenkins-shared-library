# Jenkins Shared Library for CapRover Deployments

A comprehensive Jenkins shared library providing reusable CI/CD pipelines for deploying Spring Boot and React applications to CapRover with the Dockerfile in `.cicd` folder.

## 🚀 Features

- **Spring Boot Pipeline** - Optimized for Java applications with Maven/Gradle support
- **React Pipeline** - Tailored for React applications with npm/yarn support
- **Generic Deployment Pipeline** - Flexible pipeline for custom applications
- **CapRover Integration** - Seamless deployment to CapRover PaaS
- **Email Notifications** - Beautiful HTML email notifications for success/failure
- **Organized Structure** - Dockerfile and configs in `.cicd` folder
- **Production Ready** - Multi-stage builds, health checks, and optimizations

## 📚 Documentation

### Quick Start
- **[Quick Start Guide](./docs/QUICK-START.md)** - Get started in 5 minutes

### Detailed Guides
- **[Spring Boot Deployment Guide](./docs/SPRING-BOOT-DEPLOYMENT.md)** - Complete guide for backend apps
- **[React Deployment Guide](./docs/REACT-DEPLOYMENT.md)** - Complete guide for frontend apps

## 📁 Project Structure Convention

All deployment files should be organized as follows:

```
your-application/
├── .cicd/                      # Deployment configurations
│   ├── Dockerfile              # Docker build instructions
│   └── nginx.conf              # (React only) Nginx configuration
├── src/                        # Your application source code
├── package.json / pom.xml      # Dependencies
├── captain-definition          # CapRover deployment config (root)
└── Jenkinsfile                 # Jenkins pipeline (root)
```

## ⚡ Quick Examples

### Spring Boot Application

**1. Create `.cicd/Dockerfile`:**
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

**2. Create `captain-definition`:**
```json
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile",
  "containerHttpPort": "8080"
}
```

**3. Create `Jenkinsfile`:**
```groovy
@Library('jenkins-shared-library') _

springBootPipeline(
    applicationName: 'my-spring-api'
)
```

### React Application

**1. Create `.cicd/Dockerfile`:**
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
CMD ["nginx", "-g", "daemon off;"]
```

**2. Create `.cicd/nginx.conf`:**
```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

**3. Create `captain-definition`:**
```json
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile",
  "containerHttpPort": "80"
}
```

**4. Create `Jenkinsfile`:**
```groovy
@Library('jenkins-shared-library') _

reactPipeline(
    applicationName: 'my-react-app'
)
```

## 🔧 Installation

### 1. Add Library to Jenkins

1. Go to **Jenkins → Manage Jenkins → Configure System**
2. Scroll to **Global Pipeline Libraries**
3. Click **Add**
4. Configure:
   - **Name**: `jenkins-shared-library`
   - **Default version**: `main`
   - **Retrieval method**: Modern SCM
   - **Source Code Management**: Git
   - **Project Repository**: Your repository URL

### 2. Configure Environment Variables

Go to **Jenkins → Manage Jenkins → Configure System → Global Properties → Environment Variables**

Add:
```
CAPROVER_URL=captain.your-domain.com
CAPROVER_PASSWORD=your-caprover-password
NOTIFICATION_EMAILS=team@example.com
FROM_EMAIL=jenkins@example.com
```

## 📖 Usage

### Spring Boot Pipeline

```groovy
@Library('jenkins-shared-library') _

springBootPipeline(
    // Required
    applicationName: 'noreyni-api',

    // Optional
    caproverUrl: env.CAPROVER_URL,
    caproverPassword: env.CAPROVER_PASSWORD,
    gitBranch: env.GIT_BRANCH,
    notificationEmails: 'backend-team@company.com',
    fromEmail: 'jenkins@company.com',
    pipelineTimeout: 30
)
```

### React Pipeline

```groovy
@Library('jenkins-shared-library') _

reactPipeline(
    // Required
    applicationName: 'noreyni-dashboard',

    // Optional
    caproverUrl: env.CAPROVER_URL,
    caproverPassword: env.CAPROVER_PASSWORD,
    gitBranch: env.GIT_BRANCH,
    notificationEmails: 'frontend-team@company.com',
    fromEmail: 'jenkins@company.com',
    pipelineTimeout: 30
)
```

### Generic Deployment Pipeline

```groovy
@Library('jenkins-shared-library') _

deployToCapRover(
    applicationName: 'my-custom-app',

    // Custom hooks
    additionalSetupSteps: {
        sh 'echo "Custom setup"'
    },

    preDeploymentSteps: {
        sh 'echo "Pre-deployment validation"'
    },

    postDeploymentSteps: {
        sh 'echo "Post-deployment tasks"'
    }
)
```

## 🏗️ Pipeline Stages

All pipelines follow this structure:

1. **🚀 Initialize** - Display configuration and verify tools
2. **🛠️ Install CapRover CLI** - Install deployment tool
3. **🔍 Verify CapRover Configuration** - Check captain-definition exists
4. **📋 Prepare Deployment** - Validate credentials
5. **🚢 Deploy to CapRover** - Deploy application (CapRover builds using Dockerfile)
6. **✅ Deployment Complete** - Show summary and send notifications

## 🎯 captain-definition Examples

### Basic Spring Boot

```json
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile",
  "containerHttpPort": "8080"
}
```

### Spring Boot with Environment Variables

```json
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile",
  "captainServiceName": "noreyni-api",
  "envVars": {
    "SPRING_PROFILES_ACTIVE": "prod",
    "JAVA_OPTS": "-Xms512m -Xmx1024m",
    "DB_HOST": "srv-captain--postgres",
    "TZ": "Africa/Dakar"
  },
  "volumes": [],
  "containerHttpPort": "7780"
}
```

### React with Build Arguments

```json
{
  "schemaVersion": 2,
  "dockerfilePath": "./.cicd/Dockerfile",
  "captainServiceName": "noreyni-dashboard",
  "dockerfileBuildArgs": {
    "REACT_APP_API_URL": "https://api.yourdomain.com",
    "REACT_APP_ENV": "production"
  },
  "containerHttpPort": "80"
}
```

## 🔐 Security Best Practices

### Using Secrets

In `captain-definition`, use `%%SECRET_NAME%%` for sensitive data:

```json
{
  "envVars": {
    "JWT_SECRET": "%%SECRET_JWT_SECRET%%",
    "API_KEY": "%%SECRET_API_KEY%%"
  }
}
```

Then set these in CapRover dashboard:
```
Apps → Your App → App Configs → Environment Variables
Add: SECRET_JWT_SECRET, SECRET_API_KEY, etc.
```

### Jenkins Credentials

Instead of plain environment variables:

```groovy
springBootPipeline(
    applicationName: 'my-app',
    caproverPassword: credentials('caprover-password-id')
)
```

## 📊 Directory Structure

```
jenkins-shared-library/
├── vars/                                    # Pipeline definitions
│   ├── deployToCapRover.groovy             # Generic deployment
│   ├── springBootPipeline.groovy           # Spring Boot pipeline
│   └── reactPipeline.groovy                # React pipeline
├── resources/                               # Configuration templates
│   └── caprover-configs/
│       ├── spring-boot-captain-definition.json
│       ├── react-captain-definition.json
│       └── react-nginx.conf
├── docs/                                    # Documentation
│   ├── QUICK-START.md
│   ├── SPRING-BOOT-DEPLOYMENT.md
│   └── REACT-DEPLOYMENT.md
├── examples/                                # Example Jenkinsfiles
│   ├── spring-boot-jenkinsfile
│   ├── react-jenkinsfile
│   └── generic-deployment-jenkinsfile
└── README.md                                # This file
```

## 🐛 Troubleshooting

### "captain-definition file not found"
```bash
# Ensure captain-definition is in project root
ls -la captain-definition
git add captain-definition
git commit -m "Add captain-definition"
git push
```

### "Missing required deployment credentials"
```
Jenkins → Manage Jenkins → Configure System
→ Environment Variables → Add CAPROVER_URL and CAPROVER_PASSWORD
```

### Dockerfile not found
```bash
# Ensure Dockerfile is in .cicd folder
mkdir -p .cicd
# Move/create Dockerfile in .cicd/
git add .cicd/Dockerfile
git commit -m "Add Dockerfile to .cicd folder"
git push
```

### Build fails with dependencies
```bash
# Ensure lock files are committed
git add package-lock.json pom.xml
git commit -m "Add dependency files"
git push
```

## 💡 Tips & Best Practices

1. **Organize deployment files** - Keep all deployment configs in `.cicd/` folder
2. **Use multi-stage builds** - Smaller images, faster deployments
3. **Commit lock files** - `package-lock.json`, `yarn.lock` for consistent builds
4. **Add health checks** - Monitor application health
5. **Use environment variables** - Never hardcode credentials
6. **Enable HTTPS** - Always enable HTTPS in CapRover for production
7. **Set up webhooks** - Auto-deploy on git push
8. **Monitor logs** - Check CapRover dashboard for deployment logs

## 🔗 Resources

- [CapRover Documentation](https://caprover.com/docs)
- [Jenkins Pipeline Documentation](https://www.jenkins.io/doc/book/pipeline/)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [React Documentation](https://react.dev/)

## 📝 License

MIT License - Feel free to use and modify for your projects.

## 🤝 Contributing

Contributions are welcome! Please:
1. Create a feature branch
2. Make your changes
3. Test thoroughly
4. Submit a pull request

## 📧 Support

For issues or questions:
- Check the [documentation](./docs/)
- Review Jenkins build logs
- Check CapRover deployment logs
- Contact your DevOps team

---

**Made with ❤️ for simplified deployments**

**Happy Deploying! 🚀**
