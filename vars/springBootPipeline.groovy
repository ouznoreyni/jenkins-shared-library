#!/usr/bin/env groovy

/**
 * Spring Boot Application Deployment Pipeline for CapRover
 *
 * This pipeline deploys Spring Boot applications to CapRover.
 * CapRover handles the build process using the captain-definition file.
 * Jenkins only needs Node.js for CapRover CLI - no Maven/Java required!
 *
 * IMPORTANT: You must create a captain-definition file in your repository root.
 *
 * @param config Map containing:
 *   Required:
 *   - applicationName: Name of the Spring Boot app in CapRover
 *
 *   Optional:
 *   - caproverUrl: CapRover server URL (default: env.CAPROVER_URL)
 *   - caproverPassword: CapRover password (default: env.CAPROVER_PASSWORD)
 *   - gitBranch: Branch to deploy from (default: env.GIT_BRANCH)
 *   - notificationEmails: Email list for notifications (default: env.NOTIFICATION_EMAILS)
 *   - fromEmail: Sender email (default: env.FROM_EMAIL)
 *   - dockerImage: Docker image for pipeline agent (default: 'ouznoreyni/node-git-alpine:latest')
 *   - pipelineTimeout: Pipeline timeout in minutes (default: 30)
 *
 * Example usage in Jenkinsfile:
 *   @Library('jenkins-shared-library') _
 *   springBootPipeline(
 *       applicationName: 'my-springboot-api'
 *   )
 */
def call(Map config) {
    // Validate required parameters
    if (!config.applicationName) {
        error "❌ Missing required parameter: 'applicationName' must be specified"
    }

    // Configuration
    def appName = config.applicationName
    def gitBranch = config.gitBranch ?: env.GIT_BRANCH ?: 'main'
    def caproverUrl = config.caproverUrl ?: env.CAPROVER_URL
    def caproverPasswordId = config.caproverPasswordId ?: 'caprover-password'
    def notificationEmails = config.notificationEmails ?: env.NOTIFICATION_EMAILS
    def fromEmail = config.fromEmail ?: env.FROM_EMAIL ?: 'jenkins@noreyni.com'
    def dockerImage = config.dockerImage ?: 'ouznoreyni/node-git-alpine:latest'
    def pipelineTimeout = config.pipelineTimeout ?: 30

    // Validate deployment credentials
    if (!caproverUrl) {
        error "❌ Missing required deployment credential: CAPROVER_URL must be configured"
    }

    pipeline {
        agent {
            docker {
                image dockerImage
                args '-u root:root'
            }
        }

        options {
            timeout(time: pipelineTimeout, unit: 'MINUTES')
            disableConcurrentBuilds()
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '10'))
        }

        stages {
            stage('🚀 Initialize Spring Boot Pipeline') {
                steps {
                    script {
                        echo "═══════════════════════════════════════════════════════"
                        echo "  Spring Boot Deployment Pipeline"
                        echo "═══════════════════════════════════════════════════════"
                        echo "📦 Application: ${appName}"
                        echo "☕ Framework: Spring Boot"
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
                            // Verify credential is not empty
                            if (!env.CAPROVER_PASSWORD?.trim()) {
                                error "❌ CAPROVER_PASSWORD credential is empty! Please check the credential value in Jenkins."
                            }

                            sh """
                                set +x  # Disable command echo for security

                                echo "🚀 Starting CapRover deployment..."
                                echo "ℹ️  CapRover will:"
                                echo "   1. Clone your repository"
                                echo "   2. Build using your captain-definition"
                                echo "   3. Create Docker image"
                                echo "   4. Deploy the container"

                                # Verify password is available
                                if [ -z "\$CAPROVER_PASSWORD" ]; then
                                    echo "❌ ERROR: CAPROVER_PASSWORD is empty or not set!"
                                    exit 1
                                fi

                                caprover deploy \
                                    --host ${caproverUrl} \
                                    --password "\$CAPROVER_PASSWORD" \
                                    --branch ${gitBranch} \
                                    --appName ${appName}

                                DEPLOY_EXIT_CODE=\$?

                                if [ \$DEPLOY_EXIT_CODE -eq 0 ]; then
                                    echo "✅ Spring Boot application deployed successfully!"
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
                    echo "✅ Spring Boot deployment pipeline completed successfully!"

                    if (notificationEmails) {
                        def recipients = notificationEmails.split(';').collect { "<${it.trim()}>" }.join(', ')
                        emailext (
                            subject: "✅ SUCCESS: ${appName} Spring Boot App Deployed",
                            body: """
                                <html>
                                <head>
                                    <style>
                                        body { font-family: Arial, sans-serif; line-height: 1.6; }
                                        .header { background: linear-gradient(135deg, #6DB33F 0%, #5A9E33 100%); color: white; padding: 30px; text-align: center; }
                                        .content { padding: 20px; }
                                        .info-table { width: 100%; border-collapse: collapse; margin: 20px 0; }
                                        .info-table td { padding: 12px; border-bottom: 1px solid #ddd; }
                                        .info-table td:first-child { font-weight: bold; width: 180px; color: #6DB33F; }
                                        .success-box { background-color: #d4edda; border-left: 4px solid #6DB33F; padding: 15px; margin: 20px 0; }
                                        .footer { margin-top: 30px; padding-top: 20px; border-top: 2px solid #6DB33F; color: #666; font-size: 12px; }
                                    </style>
                                </head>
                                <body>
                                    <div class="header">
                                        <h1>✅ Spring Boot Deployment Successful</h1>
                                        <p>Your application is now live!</p>
                                    </div>
                                    <div class="content">
                                        <div class="success-box">
                                            <strong>🎉 Deployment completed successfully!</strong>
                                            <p>Your Spring Boot application has been deployed to CapRover and is ready to serve requests.</p>
                                        </div>
                                        <table class="info-table">
                                            <tr>
                                                <td>📦 Application:</td>
                                                <td><strong>${appName}</strong></td>
                                            </tr>
                                            <tr>
                                                <td>☕ Framework:</td>
                                                <td>Spring Boot</td>
                                            </tr>
                                            <tr>
                                                <td>🌿 Branch:</td>
                                                <td><strong>${gitBranch}</strong></td>
                                            </tr>
                                            <tr>
                                                <td>🔗 CapRover Server:</td>
                                                <td>${caproverUrl}</td>
                                            </tr>
                                            <tr>
                                                <td>🌐 Application URL:</td>
                                                <td><a href="https://${appName}.${caproverUrl}">https://${appName}.${caproverUrl}</a></td>
                                            </tr>
                                            <tr>
                                                <td>🏗️ Build Number:</td>
                                                <td>#${BUILD_NUMBER}</td>
                                            </tr>
                                            <tr>
                                                <td>🔗 Build URL:</td>
                                                <td><a href="${BUILD_URL}">${BUILD_URL}</a></td>
                                            </tr>
                                            <tr>
                                                <td>⏰ Completed At:</td>
                                                <td>${new Date()}</td>
                                            </tr>
                                        </table>
                                        <div class="footer">
                                            <p>🤖 This is an automated notification from Jenkins CI/CD Pipeline</p>
                                            <p>ℹ️  Build was performed by CapRover using your captain-definition</p>
                                        </div>
                                    </div>
                                </body>
                                </html>
                            """,
                            mimeType: 'text/html',
                            replyTo: fromEmail,
                            to: recipients,
                            attachLog: true,
                            from: fromEmail
                        )
                    }
                }
            }

            failure {
                script {
                    echo "❌ Spring Boot deployment pipeline failed!"

                    if (notificationEmails) {
                        def recipients = notificationEmails.split(';').collect { "<${it.trim()}>" }.join(', ')
                        emailext (
                            subject: "❌ FAILED: ${appName} Spring Boot Deployment",
                            body: """
                                <html>
                                <head>
                                    <style>
                                        body { font-family: Arial, sans-serif; line-height: 1.6; }
                                        .header { background: linear-gradient(135deg, #dc3545 0%, #c82333 100%); color: white; padding: 30px; text-align: center; }
                                        .content { padding: 20px; }
                                        .info-table { width: 100%; border-collapse: collapse; margin: 20px 0; }
                                        .info-table td { padding: 12px; border-bottom: 1px solid #ddd; }
                                        .info-table td:first-child { font-weight: bold; width: 180px; color: #dc3545; }
                                        .error-box { background-color: #f8d7da; border-left: 4px solid #dc3545; padding: 15px; margin: 20px 0; }
                                        .footer { margin-top: 30px; padding-top: 20px; border-top: 2px solid #dc3545; color: #666; font-size: 12px; }
                                    </style>
                                </head>
                                <body>
                                    <div class="header">
                                        <h1>❌ Spring Boot Deployment Failed</h1>
                                        <p>Action required</p>
                                    </div>
                                    <div class="content">
                                        <div class="error-box">
                                            <strong>⚠️ Deployment Failed</strong>
                                            <p>The Spring Boot application deployment to CapRover has failed. Please review the logs and take corrective action.</p>
                                        </div>
                                        <table class="info-table">
                                            <tr>
                                                <td>📦 Application:</td>
                                                <td><strong>${appName}</strong></td>
                                            </tr>
                                            <tr>
                                                <td>☕ Framework:</td>
                                                <td>Spring Boot</td>
                                            </tr>
                                            <tr>
                                                <td>🌿 Branch:</td>
                                                <td><strong>${gitBranch}</strong></td>
                                            </tr>
                                            <tr>
                                                <td>🔗 CapRover Server:</td>
                                                <td>${caproverUrl}</td>
                                            </tr>
                                            <tr>
                                                <td>🏗️ Build Number:</td>
                                                <td>#${BUILD_NUMBER}</td>
                                            </tr>
                                            <tr>
                                                <td>🔗 Build URL:</td>
                                                <td><a href="${BUILD_URL}">${BUILD_URL}</a></td>
                                            </tr>
                                            <tr>
                                                <td>❌ Failed At:</td>
                                                <td>${new Date()}</td>
                                            </tr>
                                        </table>
                                        <div class="error-box">
                                            <p><strong>🔧 Troubleshooting Steps:</strong></p>
                                            <ol>
                                                <li>Check the attached Jenkins build log for errors</li>
                                                <li>Review CapRover deployment logs in the dashboard</li>
                                                <li>Verify captain-definition is properly configured</li>
                                                <li>Ensure pom.xml/build.gradle is correct</li>
                                                <li>Check that all dependencies are available</li>
                                                <li>Verify CapRover has sufficient resources</li>
                                                <li>Test the Docker build locally</li>
                                            </ol>
                                        </div>
                                        <div class="footer">
                                            <p>🤖 This is an automated notification from Jenkins CI/CD Pipeline</p>
                                        </div>
                                    </div>
                                </body>
                                </html>
                            """,
                            mimeType: 'text/html',
                            replyTo: fromEmail,
                            to: recipients,
                            attachLog: true,
                            compressLog: true,
                            from: fromEmail
                        )
                    }
                }
            }

            always {
                script {
                    echo "🧹 Cleaning up workspace..."
                }
                cleanWs()
            }
        }
    }
}
