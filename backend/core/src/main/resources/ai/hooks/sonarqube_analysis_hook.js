#!/usr/bin/env node
// SonarQube for IDE {{AGENT}} Hook - sonarqube_analysis_hook
// Auto-generated script for Node.js
// Connects AI Agents to SonarQube for IDE backend

const http = require('node:http');

const STARTING_PORT = 64120;
const ENDING_PORT = 64130;
const EXPECTED_IDE_NAME = '{{AGENT}}';
const PORT_SCAN_TIMEOUT = 100;

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

function analyzeFile(port, filePath) {
    console.log(`Analyzing: ${filePath} (port ${port})`);
    const requestBody = JSON.stringify({fileAbsolutePaths: [filePath]});
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
        agent: new http.Agent({keepAlive: false}),
        timeout: 1000
    };
    const req = http.request(options);
    req.on('socket', (socket) => {
        socket.unref();
    });
    req.on('error', (err) => {
        console.log(`Error: ${err.message}`);
    });
    req.write(requestBody);
    req.end();
    setImmediate(() => {
        process.exit(0);
    });
}

// Read the event JSON from stdin
let eventJson = '';
process.stdin.setEncoding('utf8');

process.stdin.on('data', (chunk) => {
    eventJson += chunk;
});

process.stdin.on('end', async () => {
    try {
        const event = JSON.parse(eventJson);
        // Support both Windsurf (tool_info.file_path) and Cursor (file_path) event formats
        const filePath = event.file_path || event.tool_info?.file_path;
        if (!filePath) {
            console.log('No file path in event');
            return;
        }
        const port = await findBackendPort();
        if (!port) {
            console.log('Backend not found');
            process.exit(1);
        }
        analyzeFile(port, filePath);
    } catch (e) {
        console.log(`Error: ${e.message}`);
        process.exit(1);
    }
});

