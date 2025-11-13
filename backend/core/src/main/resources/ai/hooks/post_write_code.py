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
PORT_SCAN_TIMEOUT = 0.05  # 50ms per port

def find_backend_port():
    """Fast port discovery: find the correct SonarQube for IDE backend"""
    for port in range(STARTING_PORT, ENDING_PORT + 1):
        if check_port(port):
            return port
    return None

def check_port(port):
    """Check if a port has a valid SonarQube for IDE backend"""
    try:
        url = f'http://localhost:{port}/sonarlint/api/status'
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=PORT_SCAN_TIMEOUT) as response:
            if response.status == 200:
                data = json.loads(response.read().decode('utf-8'))
                if data.get('ideName') == EXPECTED_IDE_NAME:
                    return True
    except (urllib.error.URLError, socket.timeout, json.JSONDecodeError):
        pass
    return False

def analyze_file(port, file_path):
    """Call the analysis endpoint"""
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
        with urllib.request.urlopen(req, timeout=30) as response:
            if response.status == 200:
                print("Analysis completed")
            else:
                print(f"Analysis failed with status: {response.status}", file=sys.stderr)
                sys.exit(1)
    except urllib.error.URLError as e:
        print(f"Analysis failed: {e}", file=sys.stderr)
        sys.exit(1)

def main():
    try:
        # Read the event JSON from stdin
        event_json = sys.stdin.read()
        event = json.loads(event_json)
        
        # Extract file path from tool_info (single file for post_write_code)
        tool_info = event.get('tool_info', {})
        file_path = tool_info.get('file_path')
        
        if not file_path:
            print("No file to analyze")
            return
        
        # Find the backend port
        port = find_backend_port()
        if not port:
            print("SonarQube for IDE backend not found", file=sys.stderr)
            sys.exit(1)
        
        # Call analysis endpoint
        analyze_file(port, file_path)
    
    except json.JSONDecodeError as e:
        print(f"Error: Failed to parse JSON: {e}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == '__main__':
    main()

