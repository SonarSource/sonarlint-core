#!/usr/bin/env node
/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
// SonarQube for IDE {{AGENT}} Hook - post_write_code
// Auto-generated script for Node.js
// Connects to SonarQube for IDE backend

const http = require('node:http');

const STARTING_PORT = 64120;
const ENDING_PORT = 64130;
const EXPECTED_IDE_NAME = '{{AGENT}}';
const PORT_SCAN_TIMEOUT = 100;

function debug(message) {
  console.error(`[DEBUG] ${message}`);
}

async function findBackendPort() {
  debug(`Starting port scan from ${STARTING_PORT} to ${ENDING_PORT}`);
  const portPromises = [];
  for (let port = STARTING_PORT; port <= ENDING_PORT; port++) {
    portPromises.push(checkPort(port));
  }
  const results = await Promise.allSettled(portPromises);
  for (const result of results) {
    if (result.status === 'fulfilled' && result.value !== null) {
      debug(`Found backend on port ${result.value}`);
      return result.value;
    }
  }
  debug('No backend port found');
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
          debug(`Port ${port} responded with ideName: ${status.ideName}`);
          if (status.ideName === EXPECTED_IDE_NAME) {
            debug(`Port ${port} matches expected IDE: ${EXPECTED_IDE_NAME}`);
            resolve(port);
          } else {
            debug(`Port ${port} IDE mismatch. Expected: ${EXPECTED_IDE_NAME}, Got: ${status.ideName}`);
            resolve(null);
          }
        } catch (e) {
          debug(`Port ${port} failed to parse JSON: ${e.message}`);
          resolve(null);
        }
      });
    });
    
    req.on('error', (err) => {
      debug(`Port ${port} error: ${err.message}`);
      resolve(null);
    });
    req.on('timeout', () => {
      debug(`Port ${port} timeout`);
      req.destroy();
      resolve(null);
    });
  });
}

function analyzeFile(port, filePath) {
  debug(`Triggering analysis for file: ${filePath} on port ${port}`);
  const requestBody = JSON.stringify({ fileAbsolutePaths: [filePath] });
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
    agent: new http.Agent({ keepAlive: false }),
    timeout: 1000
  };
  const req = http.request(options);
  req.on('socket', (socket) => {
    socket.unref();
  });
  req.on('error', (err) => {
    debug(`Analysis request error: ${err.message}`);
  });
  req.on('response', (res) => {
    debug(`Analysis request response status: ${res.statusCode}`);
  });
  req.write(requestBody);
  req.end();
  debug('Analysis request sent (fire-and-forget)');
  setImmediate(() => {
    process.exit(0);
  });
}

// Read the event JSON from stdin
debug('Hook script started');
let eventJson = '';
process.stdin.setEncoding('utf8');

process.stdin.on('data', (chunk) => {
  eventJson += chunk;
});

process.stdin.on('end', async () => {
  try {
    debug('Received event from stdin');
    const event = JSON.parse(eventJson);
    debug(`Parsed event: ${JSON.stringify(event, null, 2)}`);
    const filePath = event.tool_info?.file_path;
    if (!filePath) {
      debug('No file_path in event');
      console.log('No file to analyze');
      return;
    }
    debug(`File to analyze: ${filePath}`);
    const port = await findBackendPort();
    if (!port) {
      debug('Backend not found, exiting with error');
      console.error('SonarQube for IDE backend not found');
      process.exit(1);
    }
    analyzeFile(port, filePath);
  } catch (e) {
    debug(`Error: ${e.message}`);
    process.exit(1);
  }
});

