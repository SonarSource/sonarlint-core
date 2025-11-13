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
// Connects to SonarQube for IDE backend on port {{PORT}}

const https = require('https');
const http = require('http');

// Read the event JSON from stdin
let eventJson = '';
process.stdin.setEncoding('utf8');

process.stdin.on('data', (chunk) => {
  eventJson += chunk;
});

process.stdin.on('end', () => {
  try {
    const event = JSON.parse(eventJson);
    
    // Extract file path from tool_info (single file for post_write_code)
    const filePath = event.tool_info?.file_path;
    
    if (!filePath) {
      console.log('No file to analyze');
      return;
    }
    
    // Build request body with single file
    const requestBody = JSON.stringify({ fileAbsolutePaths: [filePath] });
    
    // Call SonarQube for IDE analysis endpoint
    const options = {
      hostname: 'localhost',
      port: {{PORT}},
      path: '/sonarlint/api/analysis/files',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(requestBody),
        'Origin': 'ai-agent://{{AGENT}}'
      },
      timeout: 30000
    };
    
    const req = http.request(options, (res) => {
      if (res.statusCode === 200) {
        console.log('Analysis completed');
      } else {
        console.error(`Analysis failed with status: ${res.statusCode}`);
        process.exit(1);
      }
      res.resume();
    });
    
    req.on('error', (e) => {
      console.error(`Warning: Failed to connect to SonarQube for IDE backend on port {{PORT}}: ${e.message}`);
    });
    
    req.on('timeout', () => {
      req.destroy();
      console.error('Request timeout');
    });
    
    req.write(requestBody);
    req.end();
  } catch (e) {
    console.error('Error:', e.message);
    process.exit(1);
  }
});

