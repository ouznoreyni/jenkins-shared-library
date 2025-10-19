#!/usr/bin/env groovy

/**
 * Generic CapRover Deployment Pipeline
 *
 * This is the base deployment pipeline that can be used for any application type.
 * For specialized pipelines, see springBootPipeline.groovy and reactPipeline.groovy
 *
 * @param config Map containing:
 *   - applicationName: Name of the application in CapRover (required)
 *   - gitBranch: Branch to deploy from (default: env.GIT_BRANCH)
 *   - caproverUrl: CapRover server URL (default: env.CAPROVER_URL)
 *   - caproverPassword: CapRover password (default: env.CAPROVER_PASSWORD)
 *   - notificationEmails: Semicolon-separated email list (default: env.NOTIFICATION_EMAILS)
 *   - fromEmail: Sender email address (default: env.FROM_EMAIL)
 *   - dockerImage: Docker image for pipeline agent (default: 'ouznoreyni/node-git-alpine:latest')
 *   - pipelineTimeout: Pipeline timeout in minutes (default: 30)
 *   - deploymentTimeout: Deployment timeout in seconds (default: 300)
 *   - additionalSetupSteps: Closure for additional setup steps (optional)
 *   - preDeploymentSteps: Closure for pre-deployment steps (optional)
 *   - postDeploymentSteps: Closure for post-deployment steps (optional)
 */
def call(Map config) {
    // Validate required parameters
    if (!config.applicationName) {
        error "❌ Missing required parameter: 'applicationName' must be specified"
    }

    // Configuration with sensible defaults
    def appName = config.applicationName
    def gitBranch = config.gitBranch ?: env.GIT_BRANCH ?: 'main'
    def caproverUrl = config.caproverUrl ?: env.CAPROVER_URL
    def caproverPasswordId = config.caproverPasswordId ?: 'caprover-password'
    def notificationEmails = config.notificationEmails ?: env.NOTIFICATION_EMAILS
    def fromEmail = config.fromEmail ?: env.FROM_EMAIL ?: 'jenkins@noreyni.com'
    def dockerImage = config.dockerImage ?: 'ouznoreyni/node-git-alpine:latest'
    def pipelineTimeout = config.pipelineTimeout ?: 30
    def deploymentTimeout = config.deploymentTimeout ?: 300

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
            stage('🚀 Initialize Pipeline') {
                steps {
                    script {
                        echo "═══════════════════════════════════════════════════════"
                        echo "  CapRover Deployment Pipeline"
                        echo "═══════════════════════════════════════════════════════"
                        echo "📦 Application: ${appName}"
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

            stage('⚙️ Additional Setup') {
                when {
                    expression { config.additionalSetupSteps != null }
                }
                steps {
                    script {
                        echo "⚙️ Running additional setup steps..."
                        config.additionalSetupSteps()
                    }
                }
            }

            stage('🔍 Verify CapRover Configuration') {
                steps {
                    script {
                        echo "🔍 Verifying CapRover deployment configuration..."

                        // Check if captain-definition file exists
                        if (fileExists('captain-definition')) {
                            echo "✅ Found captain-definition file"
                            sh 'cat captain-definition'
                        } else {
                            echo "ℹ️  No captain-definition file found (will use direct deployment)"
                        }
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

                        // Run custom pre-deployment steps if provided
                        if (config.preDeploymentSteps) {
                            echo "⚙️ Running custom pre-deployment steps..."
                            config.preDeploymentSteps()
                        }
                    }
                }
            }

            stage('🚢 Deploy to CapRover') {
                steps {
                    script {
                        echo "═══════════════════════════════════════════════════════"
                        echo "  Deploying ${appName} to CapRover"
                        echo "═══════════════════════════════════════════════════════"

                        withCredentials([string(credentialsId: caproverPasswordId, variable: 'CAPROVER_PASSWORD')]) {
                            sh """
                                set +x  # Disable command echo for security

                                echo "🚀 Starting deployment..."

                                caprover deploy \
                                    --host ${caproverUrl} \
                                    --password \$CAPROVER_PASSWORD \
                                    --branch ${gitBranch} \
                                    --appName ${appName}

                                DEPLOY_EXIT_CODE=\$?

                                if [ \$DEPLOY_EXIT_CODE -eq 0 ]; then
                                    echo "✅ Deployment completed successfully"
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

            stage('✅ Post-Deployment Tasks') {
                when {
                    expression { config.postDeploymentSteps != null }
                }
                steps {
                    script {
                        echo "⚙️ Running post-deployment tasks..."
                        config.postDeploymentSteps()
                    }
                }
            }
        }

        post {
            success {
                script {
                    echo "✅ Pipeline completed successfully!"

                    if (notificationEmails) {
                        def recipients = notificationEmails.split(';').collect { "<${it.trim()}>" }.join(', ')
                        emailext (
                            subject: "✅ SUCCESSFUL: ${appName} deployed to CapRover",
                            body: """
                                <html>
                                <head>
                                    <style>
                                        body { font-family: Arial, sans-serif; }
                                        .header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }
                                        .content { padding: 20px; }
                                        .info-table { width: 100%; border-collapse: collapse; margin-top: 20px; }
                                        .info-table td { padding: 10px; border-bottom: 1px solid #ddd; }
                                        .info-table td:first-child { font-weight: bold; width: 150px; }
                                    </style>
                                </head>
                                <body>
                                    <div class="header">
                                        <h1>✅ Deployment Successful</h1>
                                    </div>
                                    <div class="content">
                                        <p>Your application has been successfully deployed to CapRover!</p>
                                        <table class="info-table">
                                            <tr>
                                                <td>Application:</td>
                                                <td><b>${appName}</b></td>
                                            </tr>
                                            <tr>
                                                <td>Branch:</td>
                                                <td><b>${gitBranch}</b></td>
                                            </tr>
                                            <tr>
                                                <td>CapRover URL:</td>
                                                <td>${caproverUrl}</td>
                                            </tr>
                                            <tr>
                                                <td>Build Number:</td>
                                                <td>#${BUILD_NUMBER}</td>
                                            </tr>
                                            <tr>
                                                <td>Build URL:</td>
                                                <td><a href="${BUILD_URL}">${BUILD_URL}</a></td>
                                            </tr>
                                            <tr>
                                                <td>Completed At:</td>
                                                <td>${new Date()}</td>
                                            </tr>
                                        </table>
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
                    echo "❌ Pipeline failed!"

                    if (notificationEmails) {
                        def recipients = notificationEmails.split(';').collect { "<${it.trim()}>" }.join(', ')
                        emailext (
                            subject: "❌ FAILED: ${appName} deployment to CapRover",
                            body: """
                                <html>
                                <head>
                                    <style>
                                        body { font-family: Arial, sans-serif; }
                                        .header { background-color: #f44336; color: white; padding: 20px; text-align: center; }
                                        .content { padding: 20px; }
                                        .info-table { width: 100%; border-collapse: collapse; margin-top: 20px; }
                                        .info-table td { padding: 10px; border-bottom: 1px solid #ddd; }
                                        .info-table td:first-child { font-weight: bold; width: 150px; }
                                        .error-box { background-color: #ffebee; border-left: 4px solid #f44336; padding: 15px; margin-top: 20px; }
                                    </style>
                                </head>
                                <body>
                                    <div class="header">
                                        <h1>❌ Deployment Failed</h1>
                                    </div>
                                    <div class="content">
                                        <p>The deployment to CapRover has failed. Please review the build logs for details.</p>
                                        <table class="info-table">
                                            <tr>
                                                <td>Application:</td>
                                                <td><b>${appName}</b></td>
                                            </tr>
                                            <tr>
                                                <td>Branch:</td>
                                                <td><b>${gitBranch}</b></td>
                                            </tr>
                                            <tr>
                                                <td>CapRover URL:</td>
                                                <td>${caproverUrl}</td>
                                            </tr>
                                            <tr>
                                                <td>Build Number:</td>
                                                <td>#${BUILD_NUMBER}</td>
                                            </tr>
                                            <tr>
                                                <td>Build URL:</td>
                                                <td><a href="${BUILD_URL}">${BUILD_URL}</a></td>
                                            </tr>
                                            <tr>
                                                <td>Failed At:</td>
                                                <td>${new Date()}</td>
                                            </tr>
                                        </table>
                                        <div class="error-box">
                                            <p><b>Next Steps:</b></p>
                                            <ul>
                                                <li>Check the attached build log for error details</li>
                                                <li>Verify your captain-definition configuration</li>
                                                <li>Ensure all dependencies are properly configured</li>
                                                <li>Check CapRover server logs if needed</li>
                                            </ul>
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
