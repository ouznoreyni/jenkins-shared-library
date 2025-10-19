#!/usr/bin/env groovy

/**
 * Send notifications for pipeline deployments via multiple channels
 *
 * @param params Map containing:
 *   Required:
 *   - status: 'success' or 'failure'
 *   - serviceAppName: Name of the application/service
 *
 *   Optional:
 *   - notifyEmails: Semicolon-separated list of email addresses
 *   - fromEmail: Sender email address (required if notifyEmails is provided)
 *   - slackChannel: Slack channel for notifications (e.g., '#deployments')
 *   - slackWebhook: Slack webhook URL (default: env.SLACK_WEBHOOK_URL)
 *   - channels: List of channels to notify ['email', 'slack'] (default: auto-detect based on provided params)
 *   - buildUrl: Jenkins build URL (default: env.BUILD_URL)
 *   - buildNumber: Jenkins build number (default: env.BUILD_NUMBER)
 *   - gitBranch: Git branch name (default: env.GIT_BRANCH)
 *   - caproverUrl: CapRover server URL
 *   - deploymentType: Type of deployment (e.g., 'Spring Boot', 'React', 'Node.js')
 *   - additionalInfo: Additional custom information to include
 *   - duration: Build duration
 *   - commitHash: Git commit hash
 *   - commitMessage: Git commit message
 *   - author: Commit author
 */
def call(Map params) {
    // Validate required parameters
    if (!params.status) {
        error "❌ Missing required parameter: 'status' must be specified"
    }
    if (!params.serviceAppName) {
        error "❌ Missing required parameter: 'serviceAppName' must be specified"
    }

    // Parse notification channels (new format)
    def notificationChannels = params.notificationChannels ?: []

    // Backward compatibility: convert old format to new format
    if (!notificationChannels) {
        if (params.notifyEmails || params.fromEmail) {
            notificationChannels << [
                type: 'email',
                notifyEmails: params.notifyEmails,
                fromEmail: params.fromEmail
            ]
        }
        if (params.slackChannel || params.slackWebhook || env.SLACK_WEBHOOK_URL) {
            notificationChannels << [
                type: 'slack',
                channel: params.slackChannel ?: '',
                webhook: params.slackWebhook ?: env.SLACK_WEBHOOK_URL
            ]
        }
    }

    if (!notificationChannels) {
        echo "⚠️  No notification channels configured, skipping notification"
        return
    }

    // Extract parameters with defaults
    def status = params.status.toLowerCase()
    def serviceAppName = params.serviceAppName
    def buildUrl = params.buildUrl ?: env.BUILD_URL
    def buildNumber = params.buildNumber ?: env.BUILD_NUMBER
    def gitBranch = params.gitBranch ?: env.GIT_BRANCH ?: 'N/A'
    def caproverUrl = params.caproverUrl ?: ''
    def deploymentType = params.deploymentType ?: 'Application'
    def additionalInfo = params.additionalInfo ?: ''
    def duration = params.duration ?: currentBuild.durationString?.replace(' and counting', '')
    def commitHash = params.commitHash ?: ''
    def commitMessage = params.commitMessage ?: ''
    def author = params.author ?: ''

    // Send notifications to each configured channel
    if (status == 'success' || status == 'failure') {
        notificationChannels.each { channel ->
            def channelType = channel.type?.toLowerCase()

            switch (channelType) {
                case 'email':
                    if (!channel.notifyEmails) {
                        echo "⚠️  Email channel configured but no recipients specified, skipping"
                        break
                    }
                    if (!channel.fromEmail) {
                        echo "⚠️  Email channel configured but no fromEmail specified, skipping"
                        break
                    }

                    def recipients = channel.notifyEmails.split(';').collect { "<${it.trim()}>" }.join(', ')
                    if (status == 'success') {
                        sendEmailSuccessNotification(
                            serviceAppName, recipients, channel.fromEmail, buildUrl, buildNumber,
                            gitBranch, caproverUrl, deploymentType, additionalInfo,
                            duration, commitHash, commitMessage, author
                        )
                    } else {
                        sendEmailFailureNotification(
                            serviceAppName, recipients, channel.fromEmail, buildUrl, buildNumber,
                            gitBranch, caproverUrl, deploymentType, additionalInfo,
                            duration, commitHash, commitMessage, author
                        )
                    }
                    break

                case 'slack':
                    if (!channel.webhook) {
                        echo "⚠️  Slack channel configured but no webhook specified, skipping"
                        break
                    }

                    if (status == 'success') {
                        sendSlackSuccessNotification(
                            serviceAppName, channel.channel ?: '', channel.webhook, buildUrl, buildNumber,
                            gitBranch, caproverUrl, deploymentType, additionalInfo,
                            duration, commitHash, commitMessage, author
                        )
                    } else {
                        sendSlackFailureNotification(
                            serviceAppName, channel.channel ?: '', channel.webhook, buildUrl, buildNumber,
                            gitBranch, caproverUrl, deploymentType, additionalInfo,
                            duration, commitHash, commitMessage, author
                        )
                    }
                    break

                case 'discord':
                    if (!channel.webhook) {
                        echo "⚠️  Discord channel configured but no webhook specified, skipping"
                        break
                    }

                    if (status == 'success') {
                        sendDiscordSuccessNotification(
                            serviceAppName, channel.channel ?: '', channel.webhook, buildUrl, buildNumber,
                            gitBranch, caproverUrl, deploymentType, additionalInfo,
                            duration, commitHash, commitMessage, author
                        )
                    } else {
                        sendDiscordFailureNotification(
                            serviceAppName, channel.channel ?: '', channel.webhook, buildUrl, buildNumber,
                            gitBranch, caproverUrl, deploymentType, additionalInfo,
                            duration, commitHash, commitMessage, author
                        )
                    }
                    break

                default:
                    echo "⚠️  Unknown channel type: ${channelType}, skipping"
            }
        }
    } else {
        echo "⚠️  Unknown status: ${status}. Expected 'success' or 'failure'"
    }
}

/**
 * Send success notification email
 */
def sendEmailSuccessNotification(serviceAppName, recipients, fromEmail, buildUrl, buildNumber,
                            gitBranch, caproverUrl, deploymentType, additionalInfo,
                            duration, commitHash, commitMessage, author) {
    def appUrl = caproverUrl ? "https://${serviceAppName}.${caproverUrl}" : ''
    def dashboardUrl = caproverUrl ? "https://${caproverUrl}" : ''

    emailext (
        subject: "✅ SUCCESS: ${serviceAppName} ${deploymentType} Deployment",
        body: """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        margin: 0;
                        padding: 0;
                        background-color: #f5f5f5;
                    }
                    .container {
                        max-width: 650px;
                        margin: 20px auto;
                        background: white;
                        border-radius: 8px;
                        overflow: hidden;
                        box-shadow: 0 2px 8px rgba(0,0,0,0.1);
                    }
                    .header {
                        background: linear-gradient(135deg, #28a745 0%, #20c997 100%);
                        color: white;
                        padding: 40px 30px;
                        text-align: center;
                    }
                    .header h1 {
                        margin: 0 0 10px 0;
                        font-size: 28px;
                        font-weight: 600;
                    }
                    .header p {
                        margin: 0;
                        font-size: 16px;
                        opacity: 0.95;
                    }
                    .content {
                        padding: 30px;
                    }
                    .success-badge {
                        background-color: #d4edda;
                        border-left: 4px solid #28a745;
                        padding: 15px 20px;
                        margin: 0 0 25px 0;
                        border-radius: 4px;
                    }
                    .success-badge strong {
                        color: #155724;
                        font-size: 16px;
                    }
                    .success-badge p {
                        margin: 8px 0 0 0;
                        color: #155724;
                    }
                    .info-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin: 20px 0;
                    }
                    .info-table tr {
                        border-bottom: 1px solid #e9ecef;
                    }
                    .info-table tr:last-child {
                        border-bottom: none;
                    }
                    .info-table td {
                        padding: 12px 8px;
                        vertical-align: top;
                    }
                    .info-table td:first-child {
                        font-weight: 600;
                        width: 180px;
                        color: #28a745;
                    }
                    .info-table td:last-child {
                        color: #495057;
                    }
                    .info-table a {
                        color: #007bff;
                        text-decoration: none;
                        word-break: break-all;
                    }
                    .info-table a:hover {
                        text-decoration: underline;
                    }
                    .commit-info {
                        background-color: #f8f9fa;
                        padding: 15px;
                        border-radius: 4px;
                        margin: 20px 0;
                        font-family: 'Courier New', monospace;
                        font-size: 13px;
                    }
                    .quick-links {
                        display: flex;
                        gap: 10px;
                        margin: 25px 0;
                        flex-wrap: wrap;
                    }
                    .quick-links a {
                        display: inline-block;
                        padding: 10px 20px;
                        background: #28a745;
                        color: white !important;
                        text-decoration: none;
                        border-radius: 4px;
                        font-weight: 500;
                        transition: background 0.2s;
                    }
                    .quick-links a:hover {
                        background: #218838;
                    }
                    .footer {
                        margin-top: 30px;
                        padding-top: 20px;
                        border-top: 2px solid #28a745;
                        color: #6c757d;
                        font-size: 13px;
                        text-align: center;
                    }
                    .footer p {
                        margin: 5px 0;
                    }
                    .emoji {
                        font-size: 20px;
                        margin-right: 5px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>✅ Deployment Successful</h1>
                        <p>Your ${deploymentType} application is now live!</p>
                    </div>
                    <div class="content">
                        <div class="success-badge">
                            <strong>🎉 Deployment Completed Successfully!</strong>
                            <p>${serviceAppName} has been deployed and is ready to serve requests.</p>
                        </div>

                        ${appUrl || dashboardUrl ? """
                        <div class="quick-links">
                            ${appUrl ? "<a href=\"${appUrl}\" target=\"_blank\">🌐 View Application</a>" : ''}
                            ${dashboardUrl ? "<a href=\"${dashboardUrl}\" target=\"_blank\">📊 CapRover Dashboard</a>" : ''}
                            ${buildUrl ? "<a href=\"${buildUrl}\" target=\"_blank\">🔗 View Build Log</a>" : ''}
                        </div>
                        """ : ''}

                        <table class="info-table">
                            <tr>
                                <td><span class="emoji">📦</span>Application</td>
                                <td><strong>${serviceAppName}</strong></td>
                            </tr>
                            <tr>
                                <td><span class="emoji">🏗️</span>Deployment Type</td>
                                <td>${deploymentType}</td>
                            </tr>
                            <tr>
                                <td><span class="emoji">🌿</span>Branch</td>
                                <td><strong>${gitBranch}</strong></td>
                            </tr>
                            ${caproverUrl ? """
                            <tr>
                                <td><span class="emoji">🔗</span>CapRover Server</td>
                                <td>${caproverUrl}</td>
                            </tr>
                            """ : ''}
                            ${appUrl ? """
                            <tr>
                                <td><span class="emoji">🌐</span>Application URL</td>
                                <td><a href="${appUrl}" target="_blank">${appUrl}</a></td>
                            </tr>
                            """ : ''}
                            <tr>
                                <td><span class="emoji">🔢</span>Build Number</td>
                                <td>#${buildNumber}</td>
                            </tr>
                            ${duration ? """
                            <tr>
                                <td><span class="emoji">⏱️</span>Duration</td>
                                <td>${duration}</td>
                            </tr>
                            """ : ''}
                            ${buildUrl ? """
                            <tr>
                                <td><span class="emoji">🔗</span>Build URL</td>
                                <td><a href="${buildUrl}" target="_blank">${buildUrl}</a></td>
                            </tr>
                            """ : ''}
                            <tr>
                                <td><span class="emoji">⏰</span>Completed At</td>
                                <td>${new Date().format('yyyy-MM-dd HH:mm:ss z')}</td>
                            </tr>
                        </table>

                        ${commitHash || commitMessage ? """
                        <div class="commit-info">
                            ${commitHash ? "<div><strong>Commit:</strong> ${commitHash}</div>" : ''}
                            ${commitMessage ? "<div><strong>Message:</strong> ${commitMessage}</div>" : ''}
                            ${author ? "<div><strong>Author:</strong> ${author}</div>" : ''}
                        </div>
                        """ : ''}

                        ${additionalInfo ? """
                        <div style="background-color: #e7f3ff; padding: 15px; border-radius: 4px; margin: 20px 0;">
                            <strong>ℹ️ Additional Information:</strong>
                            <p style="margin: 8px 0 0 0;">${additionalInfo}</p>
                        </div>
                        """ : ''}

                        <div class="footer">
                            <p>🤖 This is an automated notification from Jenkins CI/CD Pipeline</p>
                            <p>ℹ️ Build performed by CapRover using your captain-definition</p>
                        </div>
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

/**
 * Send failure notification email
 */
def sendEmailFailureNotification(serviceAppName, recipients, fromEmail, buildUrl, buildNumber,
                            gitBranch, caproverUrl, deploymentType, additionalInfo,
                            duration, commitHash, commitMessage, author) {
    def dashboardUrl = caproverUrl ? "https://${caproverUrl}" : ''

    emailext (
        subject: "❌ FAILED: ${serviceAppName} ${deploymentType} Deployment",
        body: """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        margin: 0;
                        padding: 0;
                        background-color: #f5f5f5;
                    }
                    .container {
                        max-width: 650px;
                        margin: 20px auto;
                        background: white;
                        border-radius: 8px;
                        overflow: hidden;
                        box-shadow: 0 2px 8px rgba(0,0,0,0.1);
                    }
                    .header {
                        background: linear-gradient(135deg, #dc3545 0%, #c82333 100%);
                        color: white;
                        padding: 40px 30px;
                        text-align: center;
                    }
                    .header h1 {
                        margin: 0 0 10px 0;
                        font-size: 28px;
                        font-weight: 600;
                    }
                    .header p {
                        margin: 0;
                        font-size: 16px;
                        opacity: 0.95;
                    }
                    .content {
                        padding: 30px;
                    }
                    .error-badge {
                        background-color: #f8d7da;
                        border-left: 4px solid #dc3545;
                        padding: 15px 20px;
                        margin: 0 0 25px 0;
                        border-radius: 4px;
                    }
                    .error-badge strong {
                        color: #721c24;
                        font-size: 16px;
                    }
                    .error-badge p {
                        margin: 8px 0 0 0;
                        color: #721c24;
                    }
                    .info-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin: 20px 0;
                    }
                    .info-table tr {
                        border-bottom: 1px solid #e9ecef;
                    }
                    .info-table tr:last-child {
                        border-bottom: none;
                    }
                    .info-table td {
                        padding: 12px 8px;
                        vertical-align: top;
                    }
                    .info-table td:first-child {
                        font-weight: 600;
                        width: 180px;
                        color: #dc3545;
                    }
                    .info-table td:last-child {
                        color: #495057;
                    }
                    .info-table a {
                        color: #007bff;
                        text-decoration: none;
                        word-break: break-all;
                    }
                    .info-table a:hover {
                        text-decoration: underline;
                    }
                    .commit-info {
                        background-color: #f8f9fa;
                        padding: 15px;
                        border-radius: 4px;
                        margin: 20px 0;
                        font-family: 'Courier New', monospace;
                        font-size: 13px;
                    }
                    .troubleshooting {
                        background-color: #fff3cd;
                        border-left: 4px solid #ffc107;
                        padding: 20px;
                        margin: 20px 0;
                        border-radius: 4px;
                    }
                    .troubleshooting h3 {
                        margin: 0 0 15px 0;
                        color: #856404;
                        font-size: 16px;
                    }
                    .troubleshooting ol {
                        margin: 0;
                        padding-left: 20px;
                        color: #856404;
                    }
                    .troubleshooting li {
                        margin: 8px 0;
                    }
                    .quick-links {
                        display: flex;
                        gap: 10px;
                        margin: 25px 0;
                        flex-wrap: wrap;
                    }
                    .quick-links a {
                        display: inline-block;
                        padding: 10px 20px;
                        background: #dc3545;
                        color: white !important;
                        text-decoration: none;
                        border-radius: 4px;
                        font-weight: 500;
                        transition: background 0.2s;
                    }
                    .quick-links a:hover {
                        background: #c82333;
                    }
                    .footer {
                        margin-top: 30px;
                        padding-top: 20px;
                        border-top: 2px solid #dc3545;
                        color: #6c757d;
                        font-size: 13px;
                        text-align: center;
                    }
                    .footer p {
                        margin: 5px 0;
                    }
                    .emoji {
                        font-size: 20px;
                        margin-right: 5px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>❌ Deployment Failed</h1>
                        <p>Action required</p>
                    </div>
                    <div class="content">
                        <div class="error-badge">
                            <strong>⚠️ Deployment Failed</strong>
                            <p>The ${serviceAppName} ${deploymentType} deployment has failed. Please review the logs and take corrective action.</p>
                        </div>

                        ${buildUrl || dashboardUrl ? """
                        <div class="quick-links">
                            ${buildUrl ? "<a href=\"${buildUrl}console\" target=\"_blank\">📋 View Full Console Log</a>" : ''}
                            ${dashboardUrl ? "<a href=\"${dashboardUrl}\" target=\"_blank\">📊 CapRover Dashboard</a>" : ''}
                        </div>
                        """ : ''}

                        <table class="info-table">
                            <tr>
                                <td><span class="emoji">📦</span>Application</td>
                                <td><strong>${serviceAppName}</strong></td>
                            </tr>
                            <tr>
                                <td><span class="emoji">🏗️</span>Deployment Type</td>
                                <td>${deploymentType}</td>
                            </tr>
                            <tr>
                                <td><span class="emoji">🌿</span>Branch</td>
                                <td><strong>${gitBranch}</strong></td>
                            </tr>
                            ${caproverUrl ? """
                            <tr>
                                <td><span class="emoji">🔗</span>CapRover Server</td>
                                <td>${caproverUrl}</td>
                            </tr>
                            """ : ''}
                            <tr>
                                <td><span class="emoji">🔢</span>Build Number</td>
                                <td>#${buildNumber}</td>
                            </tr>
                            ${duration ? """
                            <tr>
                                <td><span class="emoji">⏱️</span>Duration</td>
                                <td>${duration}</td>
                            </tr>
                            """ : ''}
                            ${buildUrl ? """
                            <tr>
                                <td><span class="emoji">🔗</span>Build URL</td>
                                <td><a href="${buildUrl}" target="_blank">${buildUrl}</a></td>
                            </tr>
                            """ : ''}
                            <tr>
                                <td><span class="emoji">❌</span>Failed At</td>
                                <td>${new Date().format('yyyy-MM-dd HH:mm:ss z')}</td>
                            </tr>
                        </table>

                        ${commitHash || commitMessage ? """
                        <div class="commit-info">
                            ${commitHash ? "<div><strong>Commit:</strong> ${commitHash}</div>" : ''}
                            ${commitMessage ? "<div><strong>Message:</strong> ${commitMessage}</div>" : ''}
                            ${author ? "<div><strong>Author:</strong> ${author}</div>" : ''}
                        </div>
                        """ : ''}

                        <div class="troubleshooting">
                            <h3>🔧 Troubleshooting Steps</h3>
                            <ol>
                                <li>Check the attached Jenkins build log for detailed error messages</li>
                                <li>Review CapRover deployment logs in the dashboard</li>
                                <li>Verify captain-definition file is properly configured</li>
                                <li>Ensure all dependencies are correctly specified (pom.xml, package.json, etc.)</li>
                                <li>Check that all required environment variables are set</li>
                                <li>Verify CapRover server has sufficient resources (CPU, memory, disk)</li>
                                <li>Test the Docker build locally before deploying</li>
                                <li>Review recent code changes that might have introduced the issue</li>
                            </ol>
                        </div>

                        ${additionalInfo ? """
                        <div style="background-color: #e7f3ff; padding: 15px; border-radius: 4px; margin: 20px 0;">
                            <strong>ℹ️ Additional Information:</strong>
                            <p style="margin: 8px 0 0 0;">${additionalInfo}</p>
                        </div>
                        """ : ''}

                        <div class="footer">
                            <p>🤖 This is an automated notification from Jenkins CI/CD Pipeline</p>
                            <p>📧 If you need assistance, please contact your DevOps team</p>
                        </div>
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

/**
 * Send success notification to Slack
 */
def sendSlackSuccessNotification(serviceAppName, slackChannel, slackWebhook, buildUrl, buildNumber,
                                 gitBranch, caproverUrl, deploymentType, additionalInfo,
                                 duration, commitHash, commitMessage, author) {
    def appUrl = caproverUrl ? "https://${serviceAppName}.${caproverUrl}" : ''
    def dashboardUrl = caproverUrl ? "https://${caproverUrl}" : ''

    def message = [
        text: "✅ *Deployment Successful*",
        blocks: [
            [
                type: "header",
                text: [
                    type: "plain_text",
                    text: "✅ ${serviceAppName} Deployed Successfully",
                    emoji: true
                ]
            ],
            [
                type: "section",
                fields: [
                    [type: "mrkdwn", text: "*Application:*\n${serviceAppName}"],
                    [type: "mrkdwn", text: "*Type:*\n${deploymentType}"],
                    [type: "mrkdwn", text: "*Branch:*\n`${gitBranch}`"],
                    [type: "mrkdwn", text: "*Build:*\n#${buildNumber}"]
                ]
            ]
        ]
    ]

    if (duration) {
        message.blocks[1].fields << [type: "mrkdwn", text: "*Duration:*\n${duration}"]
    }

    if (commitHash || commitMessage) {
        def commitInfo = ""
        if (commitHash) commitInfo += "`${commitHash}`"
        if (commitMessage) commitInfo += (commitHash ? "\n" : "") + commitMessage
        message.blocks << [
            type: "section",
            text: [
                type: "mrkdwn",
                text: "*Commit:*\n${commitInfo}"
            ]
        ]
    }

    if (appUrl || dashboardUrl || buildUrl) {
        def actions = []
        if (appUrl) {
            actions << [
                type: "button",
                text: [type: "plain_text", text: "🌐 View App"],
                url: appUrl,
                style: "primary"
            ]
        }
        if (dashboardUrl) {
            actions << [
                type: "button",
                text: [type: "plain_text", text: "📊 Dashboard"],
                url: dashboardUrl
            ]
        }
        if (buildUrl) {
            actions << [
                type: "button",
                text: [type: "plain_text", text: "📋 Build Log"],
                url: buildUrl
            ]
        }
        if (actions) {
            message.blocks << [type: "actions", elements: actions]
        }
    }

    if (additionalInfo) {
        message.blocks << [
            type: "context",
            elements: [
                [type: "mrkdwn", text: ":information_source: ${additionalInfo}"]
            ]
        ]
    }

    sendSlackMessage(slackWebhook, slackChannel, message)
}

/**
 * Send failure notification to Slack
 */
def sendSlackFailureNotification(serviceAppName, slackChannel, slackWebhook, buildUrl, buildNumber,
                                 gitBranch, caproverUrl, deploymentType, additionalInfo,
                                 duration, commitHash, commitMessage, author) {
    def dashboardUrl = caproverUrl ? "https://${caproverUrl}" : ''

    def message = [
        text: "❌ *Deployment Failed*",
        blocks: [
            [
                type: "header",
                text: [
                    type: "plain_text",
                    text: "❌ ${serviceAppName} Deployment Failed",
                    emoji: true
                ]
            ],
            [
                type: "section",
                fields: [
                    [type: "mrkdwn", text: "*Application:*\n${serviceAppName}"],
                    [type: "mrkdwn", text: "*Type:*\n${deploymentType}"],
                    [type: "mrkdwn", text: "*Branch:*\n`${gitBranch}`"],
                    [type: "mrkdwn", text: "*Build:*\n#${buildNumber}"]
                ]
            ]
        ]
    ]

    if (duration) {
        message.blocks[1].fields << [type: "mrkdwn", text: "*Duration:*\n${duration}"]
    }

    if (commitHash || commitMessage) {
        def commitInfo = ""
        if (commitHash) commitInfo += "`${commitHash}`"
        if (commitMessage) commitInfo += (commitHash ? "\n" : "") + commitMessage
        message.blocks << [
            type: "section",
            text: [
                type: "mrkdwn",
                text: "*Commit:*\n${commitInfo}"
            ]
        ]
    }

    // Add troubleshooting section
    message.blocks << [
        type: "section",
        text: [
            type: "mrkdwn",
            text: "*🔧 Troubleshooting:*\n• Check build logs for errors\n• Review CapRover dashboard\n• Verify captain-definition\n• Check dependencies and resources"
        ]
    ]

    if (dashboardUrl || buildUrl) {
        def actions = []
        if (buildUrl) {
            actions << [
                type: "button",
                text: [type: "plain_text", text: "📋 View Logs"],
                url: "${buildUrl}console",
                style: "danger"
            ]
        }
        if (dashboardUrl) {
            actions << [
                type: "button",
                text: [type: "plain_text", text: "📊 Dashboard"],
                url: dashboardUrl
            ]
        }
        if (actions) {
            message.blocks << [type: "actions", elements: actions]
        }
    }

    if (additionalInfo) {
        message.blocks << [
            type: "context",
            elements: [
                [type: "mrkdwn", text: ":information_source: ${additionalInfo}"]
            ]
        ]
    }

    sendSlackMessage(slackWebhook, slackChannel, message)
}

/**
 * Send message to Slack using webhook
 */
def sendSlackMessage(webhook, channel, message) {
    if (!webhook) {
        echo "⚠️  Slack webhook URL not configured, skipping Slack notification"
        return
    }

    try {
        if (channel) {
            message.channel = channel
        }

        def payload = groovy.json.JsonOutput.toJson(message)

        sh """
            curl -X POST '${webhook}' \
                -H 'Content-Type: application/json' \
                -d '${payload.replace("'", "'\\''")}' \
                --silent --show-error
        """

        echo "✅ Slack notification sent successfully"
    } catch (Exception e) {
        echo "⚠️  Failed to send Slack notification: ${e.message}"
    }
}

/**
 * Send success notification to Discord
 */
def sendDiscordSuccessNotification(serviceAppName, discordChannel, discordWebhook, buildUrl, buildNumber,
                                   gitBranch, caproverUrl, deploymentType, additionalInfo,
                                   duration, commitHash, commitMessage, author) {
    def appUrl = caproverUrl ? "https://${serviceAppName}.${caproverUrl}" : ''
    def dashboardUrl = caproverUrl ? "https://${caproverUrl}" : ''

    def fields = [
        [name: "Application", value: serviceAppName, inline: true],
        [name: "Type", value: deploymentType, inline: true],
        [name: "Branch", value: "`${gitBranch}`", inline: true],
        [name: "Build", value: "#${buildNumber}", inline: true]
    ]

    if (duration) {
        fields << [name: "Duration", value: duration, inline: true]
    }

    if (appUrl) {
        fields << [name: "Application URL", value: "[View App](${appUrl})", inline: false]
    }

    if (commitHash || commitMessage) {
        def commitInfo = ""
        if (commitHash) commitInfo += "`${commitHash}`"
        if (commitMessage) commitInfo += (commitHash ? " - " : "") + commitMessage
        fields << [name: "Commit", value: commitInfo, inline: false]
    }

    def message = [
        username: "Jenkins CI/CD",
        avatar_url: "https://www.jenkins.io/images/logos/jenkins/jenkins.png",
        embeds: [
            [
                title: "✅ ${serviceAppName} Deployed Successfully",
                description: "Your ${deploymentType} application is now live!",
                color: 3066993, // Green color
                fields: fields,
                footer: [
                    text: "Jenkins Deployment Pipeline"
                ],
                timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'")
            ]
        ]
    ]

    sendDiscordMessage(discordWebhook, message)
}

/**
 * Send failure notification to Discord
 */
def sendDiscordFailureNotification(serviceAppName, discordChannel, discordWebhook, buildUrl, buildNumber,
                                   gitBranch, caproverUrl, deploymentType, additionalInfo,
                                   duration, commitHash, commitMessage, author) {
    def dashboardUrl = caproverUrl ? "https://${caproverUrl}" : ''

    def fields = [
        [name: "Application", value: serviceAppName, inline: true],
        [name: "Type", value: deploymentType, inline: true],
        [name: "Branch", value: "`${gitBranch}`", inline: true],
        [name: "Build", value: "#${buildNumber}", inline: true]
    ]

    if (duration) {
        fields << [name: "Duration", value: duration, inline: true]
    }

    if (buildUrl) {
        fields << [name: "Build Logs", value: "[View Console]( ${buildUrl}console)", inline: false]
    }

    if (commitHash || commitMessage) {
        def commitInfo = ""
        if (commitHash) commitInfo += "`${commitHash}`"
        if (commitMessage) commitInfo += (commitHash ? " - " : "") + commitMessage
        fields << [name: "Commit", value: commitInfo, inline: false]
    }

    fields << [name: "Troubleshooting", value: "• Check build logs\n• Review CapRover dashboard\n• Verify dependencies", inline: false]

    def message = [
        username: "Jenkins CI/CD",
        avatar_url: "https://www.jenkins.io/images/logos/jenkins/jenkins.png",
        embeds: [
            [
                title: "❌ ${serviceAppName} Deployment Failed",
                description: "Action required - deployment has failed",
                color: 15158332, // Red color
                fields: fields,
                footer: [
                    text: "Jenkins Deployment Pipeline"
                ],
                timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'")
            ]
        ]
    ]

    sendDiscordMessage(discordWebhook, message)
}

/**
 * Send message to Discord using webhook
 */
def sendDiscordMessage(webhook, message) {
    if (!webhook) {
        echo "⚠️  Discord webhook URL not configured, skipping Discord notification"
        return
    }

    try {
        def payload = groovy.json.JsonOutput.toJson(message)

        sh """
            curl -X POST '${webhook}' \
                -H 'Content-Type: application/json' \
                -d '${payload.replace("'", "'\\''")}' \
                --silent --show-error
        """

        echo "✅ Discord notification sent successfully"
    } catch (Exception e) {
        echo "⚠️  Failed to send Discord notification: ${e.message}"
    }
}
