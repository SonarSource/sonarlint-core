#!/usr/bin/env node
// SonarQube for IDE {{AGENT}} Hook - Analyze and Report Issues
// Auto-generated script for Node.js
// Analyzes tracked files and reports issues back to the agent

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');

const OK_CODE = 200;
const STARTING_PORT = 64120;
const ENDING_PORT = 64130;
const EXPECTED_IDE_NAME = '{{AGENT}}';
const PORT_SCAN_TIMEOUT = 100;
const ANALYSIS_TIMEOUT = 30000; // 30 seconds for analysis
const MAX_LOOP_COUNT = 2;
const TEMP_DIR = os.tmpdir();

function getSessionFilePath(conversationId) {
    return path.join(TEMP_DIR, `sonarlint-cursor-${conversationId}.json`);
}

async function findBackendPort() {
    const portPromises = [];
    for (let port = STARTING_PORT; port <= ENDING_PORT; port++) {
        portPromises.push(checkPort(port));
    }
    const results = await Promise.allSettled(portPromises);
    for (const result of results) {
        if (result.status === 'fulfilled' && result.value !== null) {
            return result.value;
        }
    }
    return null;
}

function checkPort(port) {
    return new Promise((resolve) => {
        const req = http.get({
            hostname: 'localhost',
            port,
            path: '/sonarlint/api/status',
            timeout: PORT_SCAN_TIMEOUT,
            headers: {
                'Origin': 'ai-agent://{{AGENT}}'
            }
        }, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const status = JSON.parse(data);
                    if (status.ideName === EXPECTED_IDE_NAME) {
                        resolve(port);
                    } else {
                        resolve(null);
                    }
                } catch {
                    resolve(null);
                }
            });
        });

        req.on('error', () => {
            resolve(null);
        });
        req.on('timeout', () => {
            req.destroy();
            resolve(null);
        });
    });
}

function analyzeFiles(port, filePaths) {
    return new Promise((resolve, reject) => {
        const requestBody = JSON.stringify({fileAbsolutePaths: filePaths});
        const options = {
            hostname: 'localhost',
            port,
            path: '/sonarlint/api/analysis/files',
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(requestBody),
                'Origin': 'ai-agent://{{AGENT}}'
            },
            timeout: ANALYSIS_TIMEOUT
        };

        const req = http.request(options, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    if (res.statusCode === OK_CODE) {
                        const result = JSON.parse(data);
                        resolve(result);
                    } else {
                        reject(new Error(`Analysis failed with status ${res.statusCode}`));
                    }
                } catch (e) {
                    reject(e);
                }
            });
        });

        req.on('error', (err) => {
            reject(err);
        });
        req.on('timeout', () => {
            req.destroy();
            reject(new Error('Analysis timeout'));
        });

        req.write(requestBody);
        req.end();
    });
}

function formatIssues(analysisResult) {
    if (!analysisResult?.issues || analysisResult.issues.length === 0) {
        return null;
    }

    // Group issues by file
    const issuesByFile = {};
    for (const issue of analysisResult.issues) {
        const file = issue.filePath || 'Unknown';
        if (!issuesByFile[file]) {
            issuesByFile[file] = [];
        }
        issuesByFile[file].push(issue);
    }

    // Format message
    const fileCount = Object.keys(issuesByFile).length;
    const totalIssues = analysisResult.issues.length;

    let message = `SonarLint found ${totalIssues} issue(s) in ${fileCount} file(s):\n\n`;

    for (const [file, issues] of Object.entries(issuesByFile)) {
        const fileName = path.basename(file);
        message += `${fileName}:\n`;

        for (const issue of issues) {
            const line = issue.startLine || '?';
            const msg = issue.message || 'No description';
            const ruleKey = issue.ruleKey || '';
            message += `  - Line ${line}: ${msg}`;
            if (ruleKey) {
                message += ` (${ruleKey})`;
            }
            message += '\n';
        }
        message += '\n';
    }

    message += 'Please fix these issues.';
    return message;
}

function cleanupSessionFile(conversationId) {
    try {
        const sessionFile = getSessionFilePath(conversationId);
        if (fs.existsSync(sessionFile)) {
            fs.unlinkSync(sessionFile);
        }
    } catch {
        // Ignore cleanup errors
    }
}

try {
    const eventJson = await new Promise((resolve) => {
        let data = '';
        process.stdin.setEncoding('utf8');
        process.stdin.on('data', chunk => data += chunk);
        process.stdin.on('end', () => resolve(data));
    });

    const event = JSON.parse(eventJson);
    const conversationId = event.conversation_id;
    const status = event.status;
    const loopCount = event.loop_count || 0;

    // Only proceed if completed and haven't looped too many times
    if (status !== 'completed' || loopCount >= MAX_LOOP_COUNT) {
        cleanupSessionFile(conversationId);
        console.log('{}'); // Empty response, let session end
        process.exit(0);
    }

    // Read tracked files
    const sessionFile = getSessionFilePath(conversationId);
    if (!fs.existsSync(sessionFile)) {
        console.log('{}'); // No files tracked, let session end
        process.exit(0);
    }

    const trackedFiles = JSON.parse(fs.readFileSync(sessionFile, 'utf8'));
    if (trackedFiles.length === 0) {
        cleanupSessionFile(conversationId);
        console.log('{}'); // No files to analyze
        process.exit(0);
    }

    // Find backend
    const port = await findBackendPort();
    if (!port) {
        cleanupSessionFile(conversationId);
        console.log('{}'); // Backend not found, let session end
        process.exit(0);
    }

    // Analyze files
    const analysisResult = await analyzeFiles(port, trackedFiles);

    // Format issues
    const message = formatIssues(analysisResult);

    // Cleanup
    cleanupSessionFile(conversationId);

    // Return response
    if (message) {
        console.log(JSON.stringify({followup_message: message}));
    } else {
        console.log('{}'); // No issues, let session end
    }

    process.exit(0);
} catch {
    console.log('{}'); // On error, let session end gracefully
    process.exit(0);
}
