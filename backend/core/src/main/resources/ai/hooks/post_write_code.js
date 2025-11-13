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

const http = require('http');

const STARTING_PORT = 64120;
const ENDING_PORT = 64130;
const EXPECTED_IDE_NAME = '{{AGENT}}';
const PORT_SCAN_TIMEOUT = 50; // ms per port

// Fast port discovery: find the correct SonarQube for IDE backend
async function findBackendPort() {
  const portPromises = [];
  
  for (let port = STARTING_PORT; port <= ENDING_PORT; port++) {
    portPromises.push(checkPort(port));
  }
  
  // Race all port checks - return first successful match
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
      port: port,
      path: '/sonarlint/api/status',
      timeout: PORT_SCAN_TIMEOUT
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
        } catch (e) {
          resolve(null);
        }
      });
    });
    
    req.on('error', () => resolve(null));
    req.on('timeout', () => {
      req.destroy();
      resolve(null);
    });
  });
}

async function analyzeFile(port, filePath) {
  const requestBody = JSON.stringify({ fileAbsolutePaths: [filePath] });
  
  const options = {
    hostname: 'localhost',
    port: port,
    path: '/sonarlint/api/analysis/files',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(requestBody),
      'Origin': 'ai-agent://{{AGENT}}'
    },
    timeout: 30000
  };
  
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      if (res.statusCode === 200) {
        console.log('Analysis completed');
        resolve();
      } else {
        reject(new Error(`Analysis failed with status: ${res.statusCode}`));
      }
      res.resume();
    });
    
    req.on('error', reject);
    req.on('timeout', () => {
      req.destroy();
      reject(new Error('Request timeout'));
    });
    
    req.write(requestBody);
    req.end();
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
    
    // Extract file path from tool_info (single file for post_write_code)
    const filePath = event.tool_info?.file_path;
    
    if (!filePath) {
      console.log('No file to analyze');
      return;
    }
    
    // Find the backend port
    const port = await findBackendPort();
    if (!port) {
      console.error('SonarQube for IDE backend not found');
      process.exit(1);
    }
    
    // Call analysis endpoint
    await analyzeFile(port, filePath);
  } catch (e) {
    console.error('Error:', e.message);
    process.exit(1);
  }
});

