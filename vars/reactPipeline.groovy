#!/usr/bin/env groovy

/**
 * React Application Deployment Pipeline for CapRover
 *
 * This pipeline deploys React applications to CapRover.
 * CapRover handles the build process using the captain-definition file.
 * Jenkins only needs Node.js for CapRover CLI - no build tools required!
 *
 * IMPORTANT: You must create a captain-definition file in your repository root.
 *
 * @param config Map containing:
 *   Required:
 *   - applicationName: Name of the React app in CapRover
 *
 *   Optional - Deployment:
 *   - caproverUrl: CapRover server URL (default: env.CAPROVER_URL)
 *   - caproverPasswordId: Jenkins credential ID for CapRover password (default: 'caprover-password')
 *   - gitBranch: Branch to deploy from (default: env.GIT_BRANCH)
 *   - dockerImage: Docker image for pipeline agent (default: 'ouznoreyni/node-git-alpine:latest')
 *   - pipelineTimeout: Pipeline timeout in minutes (default: 30)
 *
 *   Optional - Docker Build & Push:
 *   - pushDockerImage: Boolean to enable Docker build and push (default: false)
 *   - dockerRegistry: Docker registry URL (default: 'docker.io')
 *   - dockerRegistryCredentialId: Jenkins credential ID for Docker registry (default: 'docker-hub-credentials')
 *   - dockerImageName: Docker image name (default: applicationName)
 *   - dockerImageTag: Docker image tag (default: git commit hash)
 *   - dockerfilePath: Path to Dockerfile (default: './Dockerfile')
 *   - dockerBuildArgs: Map of build arguments (default: [:])
 *
 *   Optional - Notifications:
 *   - notificationChannels: List of notification channel configs (see example below)
 *
 * Example usage in Jenkinsfile:
 *   @Library('jenkins-shared-library') _
 *   reactPipeline(
 *       applicationName: 'my-react-app',
 *       pushDockerImage: true,
 *       dockerImageName: 'myorg/my-react-app',
 *       notificationChannels: [
 *           [
 *               type: 'email',
 *               notifyEmails: 'dev@example.com;ops@example.com',
 *               fromEmail: 'jenkins@example.com'
 *           ],
 *           [
 *               type: 'slack',
 *               channel: '#deployments',
 *               webhook: env.SLACK_WEBHOOK_URL
 *           ]
 *       ]
 *   )
 */
def call(Map config) {
    // Validate required parameters
    if (!config.applicationName) {
        error "❌ Missing required parameter: 'applicationName' must be specified"
    }

    // Configuration - Deployment
    def appName = config.applicationName
    def gitBranch = config.gitBranch ?: env.GIT_BRANCH ?: 'main'
    def caproverUrl = config.caproverUrl ?: env.CAPROVER_URL
    def caproverPasswordId = config.caproverPasswordId ?: 'caprover-password'
    def agentDockerImage = config.dockerImage ?: 'ouznoreyni/node-git-alpine:latest'
    def pipelineTimeout = config.pipelineTimeout ?: 30

    // Configuration - Docker Build & Push
    def pushDockerImage = config.pushDockerImage ?: false
    def dockerRegistry = config.dockerRegistry ?: 'docker.io'
    def dockerRegistryCredentialId = config.dockerRegistryCredentialId ?: 'docker-hub-credentials'
    def dockerImageName = config.dockerImageName ?: appName
    def dockerImageTag = config.dockerImageTag ?: '' // Will be set to commit hash if empty
    def dockerfilePath = config.dockerfilePath ?: './Dockerfile'
    def dockerBuildArgs = config.dockerBuildArgs ?: [:]

    // Configuration - Notifications (support both old and new format)
    def notificationChannels = config.notificationChannels ?: []

    // Backward compatibility: if old format is used, convert to new format
    if (!notificationChannels && (config.notificationEmails || config.fromEmail)) {
        notificationChannels << [
            type: 'email',
            notifyEmails: config.notificationEmails ?: env.NOTIFICATION_EMAILS,
            fromEmail: config.fromEmail ?: env.FROM_EMAIL ?: 'jenkins@noreyni.com'
        ]
    }

    // Add Slack if configured
    if (!notificationChannels.find { it.type == 'slack' } && (config.slackWebhook || env.SLACK_WEBHOOK_URL)) {
        notificationChannels << [
            type: 'slack',
            channel: config.slackChannel ?: '',
            webhook: config.slackWebhook ?: env.SLACK_WEBHOOK_URL
        ]
    }

    // Validate deployment credentials
    if (!caproverUrl) {
        error "❌ Missing required deployment credential: CAPROVER_URL must be configured"
    }

    pipeline {
        agent {
            docker {
                image agentDockerImage
                args '-u root:root -v /var/run/docker.sock:/var/run/docker.sock'
            }
        }

        options {
            timeout(time: pipelineTimeout, unit: 'MINUTES')
            disableConcurrentBuilds()
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '10'))
        }

        stages {
            stage('🚀 Initialize React Pipeline') {
                steps {
                    script {
                        echo "═══════════════════════════════════════════════════════"
                        echo "  React Application Deployment"
                        echo "═══════════════════════════════════════════════════════"
                        echo "📦 Application: ${appName}"
                        echo "⚛️  Framework: React"
                        echo "🌿 Branch: ${gitBranch}"
                        echo "🔗 CapRover: ${caproverUrl}"
                        echo "🏗️  Build: #${BUILD_NUMBER}"
                        echo "═══════════════════════════════════════════════════════"

                        sh 'node --version'
                        sh 'npm --version'
                        sh 'git --version'
                    }
                }
            }

            stage('📥 Checkout Source Code') {
                steps {
                    script {
                        echo "📥 Checking out source code with full git history..."

                        // Clean workspace first
                        deleteDir()

                        // Checkout with full git history for CapRover
                        checkout([
                            $class: 'GitSCM',
                            branches: [[name: "*/${gitBranch}"]],
                            extensions: [
                                [$class: 'CloneOption', depth: 0, noTags: false, shallow: false],
                                [$class: 'LocalBranch', localBranch: gitBranch]
                            ],
                            userRemoteConfigs: scm.userRemoteConfigs
                        ])

                        sh """
                            # Fix git safe.directory issue when running in Docker container
                            git config --global --add safe.directory \$(pwd)

                            git branch -a
                            git log --oneline -n 5
                            echo "✅ Git repository ready"
                        """

                        // Store commit hash for notifications
                        env.GIT_COMMIT_HASH = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                        env.GIT_COMMIT_MESSAGE = sh(returnStdout: true, script: 'git log -1 --pretty=%B').trim()
                        env.GIT_COMMIT_AUTHOR = sh(returnStdout: true, script: 'git log -1 --pretty=%an').trim()
                    }
                }
            }

            stage('🐳 Build Docker Image') {
                when {
                    expression { pushDockerImage == true }
                }
                steps {
                    script {
                        echo "═══════════════════════════════════════════════════════"
                        echo "  Building Docker Image"
                        echo "═══════════════════════════════════════════════════════"

                        // Set image tag if not provided
                        def imageTag = dockerImageTag ?: env.GIT_COMMIT_HASH
                        def fullImageName = "${dockerRegistry}/${dockerImageName}:${imageTag}"
                        def latestImageName = "${dockerRegistry}/${dockerImageName}:latest"

                        // Store for later stages
                        env.DOCKER_IMAGE_TAG = imageTag
                        env.DOCKER_FULL_IMAGE_NAME = fullImageName
                        env.DOCKER_LATEST_IMAGE_NAME = latestImageName

                        echo "📦 Image: ${fullImageName}"
                        echo "📦 Latest: ${latestImageName}"
                        echo "📝 Dockerfile: ${dockerfilePath}"

                        // Verify Dockerfile exists
                        if (!fileExists(dockerfilePath)) {
                            error "❌ Dockerfile not found at: ${dockerfilePath}"
                        }

                        // Build Docker arguments string
                        def buildArgsString = ''
                        dockerBuildArgs.each { key, value ->
                            buildArgsString += " --build-arg ${key}=${value}"
                        }

                        sh """
                            echo "🔨 Building Docker image..."
                            docker build \\
                                -t ${fullImageName} \\
                                -t ${latestImageName} \\
                                ${buildArgsString} \\
                                -f ${dockerfilePath} \\
                                .

                            echo "✅ Docker image built successfully!"
                            docker images | grep ${dockerImageName} | head -5
                        """
                    }
                }
            }

            stage('🚀 Push Docker Image') {
                when {
                    expression { pushDockerImage == true }
                }
                steps {
                    script {
                        echo "═══════════════════════════════════════════════════════"
                        echo "  Pushing Docker Image to Registry"
                        echo "═══════════════════════════════════════════════════════"

                        withCredentials([usernamePassword(
                            credentialsId: dockerRegistryCredentialId,
                            usernameVariable: 'DOCKER_USER',
                            passwordVariable: 'DOCKER_PASS'
                        )]) {
                            sh """
                                set +x  # Disable command echo for security
                                echo "🔑 Logging in to Docker registry: ${dockerRegistry}"
                                echo "\$DOCKER_PASS" | docker login ${dockerRegistry} -u "\$DOCKER_USER" --password-stdin

                                echo "📤 Pushing tagged image: ${env.DOCKER_FULL_IMAGE_NAME}"
                                docker push ${env.DOCKER_FULL_IMAGE_NAME}

                                echo "📤 Pushing latest image: ${env.DOCKER_LATEST_IMAGE_NAME}"
                                docker push ${env.DOCKER_LATEST_IMAGE_NAME}

                                echo "🧹 Logging out from Docker registry"
                                docker logout ${dockerRegistry}

                                set -x  # Re-enable command echo
                                echo "✅ Docker images pushed successfully!"
                            """
                        }

                        echo "═══════════════════════════════════════════════════════"
                        echo "  Docker Image Published"
                        echo "═══════════════════════════════════════════════════════"
                        echo "📦 Tagged: ${env.DOCKER_FULL_IMAGE_NAME}"
                        echo "📦 Latest: ${env.DOCKER_LATEST_IMAGE_NAME}"
                        echo "🌐 Registry: ${dockerRegistry}"
                        echo "═══════════════════════════════════════════════════════"
                    }
                }
            }

            stage('🛠️ Install CapRover CLI') {
                steps {
                    echo "📥 Installing CapRover CLI..."
                    sh 'npm install -g caprover'
                    sh 'caprover --version'
                    echo "✅ CapRover CLI installed successfully"
                }
            }

            stage('🔍 Verify CapRover Configuration') {
                steps {
                    script {
                        echo "🔍 Verifying CapRover deployment configuration..."

                        // Check if captain-definition exists
                        if (!fileExists('captain-definition')) {
                            error """
❌ captain-definition file not found!
Please create a captain-definition file in your repository.
"""
                        }

                        sh 'cat captain-definition'
                        echo "✅ Found captain-definition file"
                    }
                }
            }

            stage('📋 Prepare Deployment') {
                steps {
                    echo "Preparing deployment for branch: ${gitBranch}"
                    script {
                        if (!caproverUrl) {
                            error "❌ Missing required deployment credential: CAPROVER_URL"
                        }
                        echo "✅ Deployment credentials verified"
                    }
                }
            }

            stage('🚢 Deploy to CapRover') {
                steps {
                    script {
                        echo "═══════════════════════════════════════════════════════"
                        echo "  Deploying ${appName} to CapRover"
                        echo "  CapRover will handle the build process"
                        echo "═══════════════════════════════════════════════════════"

                        withCredentials([string(credentialsId: caproverPasswordId, variable: 'CAPROVER_PASSWORD')]) {
                            sh """
                                set +x  # Disable command echo for security

                                echo "🚀 Starting CapRover deployment..."
                                echo "ℹ️  CapRover will:"
                                echo "   1. Clone your repository"
                                echo "   2. Build using your captain-definition"
                                echo "   3. Install dependencies"
                                echo "   4. Build React app for production"
                                echo "   5. Create optimized Nginx Docker image"
                                echo "   6. Deploy the container"

                                echo "🔍 Verifying git repository..."
                                echo "📍 Current commit: \$(git rev-parse HEAD)"
                                echo "📍 Current branch: \$(git rev-parse --abbrev-ref HEAD)"
                                echo "📍 Remote: \$(git config --get remote.origin.url)"

                                echo "🚢 Deploying to CapRover..."
                                caprover deploy \
                                    -h ${caproverUrl} \
                                    -p \$CAPROVER_PASSWORD \
                                    -b ${gitBranch} \
                                    -a ${appName}

                                DEPLOY_EXIT_CODE=\$?

                                if [ \$DEPLOY_EXIT_CODE -eq 0 ]; then
                                    echo "✅ React application deployed successfully!"
                                else
                                    echo "❌ Deployment failed with exit code \$DEPLOY_EXIT_CODE"
                                    exit \$DEPLOY_EXIT_CODE
                                fi

                                set -x  # Re-enable command echo
                            """
                        }
                    }
                }
            }

            stage('✅ Deployment Complete') {
                steps {
                    script {
                        echo "═══════════════════════════════════════════════════════"
                        echo "  Deployment Summary"
                        echo "═══════════════════════════════════════════════════════"
                        echo "✅ Application: ${appName}"
                        echo "🌐 URL: https://${appName}.${caproverUrl}"
                        echo "🔗 CapRover Dashboard: https://${caproverUrl}"
                        echo "ℹ️  Check CapRover dashboard for application logs and status"
                        echo "═══════════════════════════════════════════════════════"
                    }
                }
            }
        }

        post {
            success {
                script {
                    echo "✅ React deployment pipeline completed successfully!"

                    // Build additional info with Docker image details
                    def additionalInfo = ""
                    if (pushDockerImage && env.DOCKER_FULL_IMAGE_NAME) {
                        additionalInfo = "Docker Image: ${env.DOCKER_FULL_IMAGE_NAME}"
                    }

                    // Send notifications to all configured channels
                    sendNotification(
                        status: 'success',
                        serviceAppName: appName,
                        notificationChannels: notificationChannels,
                        gitBranch: gitBranch,
                        caproverUrl: caproverUrl,
                        deploymentType: 'React',
                        commitHash: env.GIT_COMMIT_HASH ?: '',
                        commitMessage: env.GIT_COMMIT_MESSAGE ?: '',
                        author: env.GIT_COMMIT_AUTHOR ?: '',
                        additionalInfo: additionalInfo
                    )
                }
            }

            failure {
                script {
                    echo "❌ React deployment pipeline failed!"

                    // Send notifications to all configured channels
                    sendNotification(
                        status: 'failure',
                        serviceAppName: appName,
                        notificationChannels: notificationChannels,
                        gitBranch: gitBranch,
                        caproverUrl: caproverUrl,
                        deploymentType: 'React',
                        commitHash: env.GIT_COMMIT_HASH ?: '',
                        commitMessage: env.GIT_COMMIT_MESSAGE ?: '',
                        author: env.GIT_COMMIT_AUTHOR ?: ''
                    )
                }
            }

            always {
                script {
                    echo "🧹 Cleaning up workspace..."

                    // Clean up Docker images if built
                    if (pushDockerImage && env.DOCKER_FULL_IMAGE_NAME) {
                        try {
                            sh """
                                echo "🧹 Cleaning up Docker images..."
                                docker rmi ${env.DOCKER_FULL_IMAGE_NAME} || true
                                docker rmi ${env.DOCKER_LATEST_IMAGE_NAME} || true
                            """
                        } catch (Exception e) {
                            echo "⚠️  Failed to clean up Docker images: ${e.message}"
                        }
                    }
                }
                cleanWs()
            }
        }
    }
}
