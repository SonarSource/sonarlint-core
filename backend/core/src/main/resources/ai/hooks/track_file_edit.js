#!/usr/bin/env node
// SonarQube for IDE {{AGENT}} Hook - Track File Edits
// Auto-generated script for Node.js
// Tracks files edited during the AI agent session

const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');

const TEMP_DIR = os.tmpdir();

function getSessionFilePath(conversationId) {
    return path.join(TEMP_DIR, `sonarlint-cursor-${conversationId}.json`);
}

function trackFile(conversationId, filePath) {
    const sessionFile = getSessionFilePath(conversationId);

    let trackedFiles = [];
    try {
        if (fs.existsSync(sessionFile)) {
            const content = fs.readFileSync(sessionFile, 'utf8');
            trackedFiles = JSON.parse(content);
        }
    } catch {
        // File doesn't exist or is invalid, start fresh
        trackedFiles = [];
    }

    // Add file if not already tracked
    if (!trackedFiles.includes(filePath)) {
        trackedFiles.push(filePath);
        fs.writeFileSync(sessionFile, JSON.stringify(trackedFiles, null, 2), 'utf8');
    }
}

// Read the event JSON from stdin
let eventJson = '';
process.stdin.setEncoding('utf8');

process.stdin.on('data', (chunk) => {
    eventJson += chunk;
});

process.stdin.on('end', () => {
    try {
        const event = JSON.parse(eventJson);
        const conversationId = event.conversation_id;
        const filePath = event.file_path;

        if (!conversationId || !filePath) {
            process.exit(0);
        }

        trackFile(conversationId, filePath);
        process.exit(0);
    } catch (e) {
        console.log(`Error: ${e.message}`);
        process.exit(1);
    }
});

