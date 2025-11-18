#!/usr/bin/env python3
# SonarQube for IDE {{AGENT}} Hook - post_write_code
# Auto-generated script for Python
# Connects to SonarQube for IDE backend

import sys
import json
import urllib.request
import urllib.error
import socket

STARTING_PORT = 64120
ENDING_PORT = 64130
EXPECTED_IDE_NAME = '{{AGENT}}'
PORT_SCAN_TIMEOUT = 0.1

def debug(message):
    """Print debug message to stderr"""
    print(f'[DEBUG] {message}', file=sys.stderr)

def find_backend_port():
    """Fast port discovery: find the correct SonarQube for IDE backend"""
    debug(f'Starting port scan from {STARTING_PORT} to {ENDING_PORT}')
    for port in range(STARTING_PORT, ENDING_PORT + 1):
        if check_port(port):
            debug(f'Found backend on port {port}')
            return port
    debug('No backend port found')
    return None

def check_port(port):
    """Check if a port has a valid SonarQube for IDE backend"""
    try:
        url = f'http://localhost:{port}/sonarlint/api/status'
        req = urllib.request.Request(url, headers={'Origin': 'ai-agent://{{AGENT}}'})
        with urllib.request.urlopen(req, timeout=PORT_SCAN_TIMEOUT) as response:
            if response.status == 200:
                data = json.loads(response.read().decode('utf-8'))
                ide_name = data.get('ideName')
                debug(f'Port {port} responded with ideName: {ide_name}')
                if ide_name == EXPECTED_IDE_NAME:
                    debug(f'Port {port} matches expected IDE: {EXPECTED_IDE_NAME}')
                    return True
                else:
                    debug(f'Port {port} IDE mismatch. Expected: {EXPECTED_IDE_NAME}, Got: {ide_name}')
    except Exception as e:
        debug(f'Port {port} error: {e}')
    return False

def analyze_file(port, file_path):
    """Call the analysis endpoint (fire-and-forget, non-blocking)"""
    debug(f'Triggering analysis for file: {file_path} on port {port}')
    request_body = json.dumps({'fileAbsolutePaths': [file_path]})
    url = f'http://localhost:{port}/sonarlint/api/analysis/files'
    req = urllib.request.Request(
        url,
        data=request_body.encode('utf-8'),
        headers={
            'Content-Type': 'application/json',
            'Origin': 'ai-agent://{{AGENT}}'
        }
    )

    try:
        response = urllib.request.urlopen(req, timeout=1)
        debug(f'Analysis request response status: {response.status}')
        response.close()
    except Exception as e:
        debug(f'Analysis request error: {e}')

    debug('Analysis request sent (fire-and-forget)')
    sys.exit(0)

def main():
    debug('Hook script started')
    try:
        debug('Reading event from stdin')
        event_json = sys.stdin.read()
        debug(f'Received event: {event_json}')
        event = json.loads(event_json)
        debug(f'Parsed event: {json.dumps(event, indent=2)}')

        tool_info = event.get('tool_info', {})
        file_path = tool_info.get('file_path')
        
        if not file_path:
            debug('No file_path in event')
            return

        debug(f'File to analyze: {file_path}')
        port = find_backend_port()
        if not port:
            debug('Backend not found, exiting with error')
            sys.exit(1)

        analyze_file(port, file_path)
    
    except json.JSONDecodeError as e:
        debug(f'JSON decode error: {e}')
        import traceback
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        debug(f'Error: {e}')
        import traceback
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)

if __name__ == '__main__':
    main()

