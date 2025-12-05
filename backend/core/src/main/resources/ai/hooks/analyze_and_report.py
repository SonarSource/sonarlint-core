#!/usr/bin/env python3
# SonarQube for IDE {{AGENT}} Hook - Analyze and Report Issues
# Auto-generated script for Python
# Analyzes tracked files and reports issues back to the agent

import sys
import json
import os
import tempfile
import urllib.request
import urllib.error

STARTING_PORT = 64120
ENDING_PORT = 64130
EXPECTED_IDE_NAME = '{{AGENT}}'
PORT_SCAN_TIMEOUT = 0.1
ANALYSIS_TIMEOUT = 30
MAX_LOOP_COUNT = 2

def get_session_file_path(conversation_id):
    temp_dir = tempfile.gettempdir()
    return os.path.join(temp_dir, f'sonarlint-cursor-{conversation_id}.json')

def find_backend_port():
    """Find the correct SonarQube for IDE backend"""
    for port in range(STARTING_PORT, ENDING_PORT + 1):
        if check_port(port):
            return port
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
                if ide_name == EXPECTED_IDE_NAME:
                    return True
    except Exception:
        pass
    return False

def analyze_files(port, file_paths):
    """Analyze files and return issues"""
    request_body = json.dumps({'fileAbsolutePaths': file_paths})
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
        with urllib.request.urlopen(req, timeout=ANALYSIS_TIMEOUT) as response:
            if response.status == 200:
                return json.loads(response.read().decode('utf-8'))
    except Exception:
        pass
    return None

def format_issues(analysis_result):
    """Format issues into a readable message"""
    if not analysis_result or not analysis_result.get('issues'):
        return None
    
    issues = analysis_result['issues']
    if len(issues) == 0:
        return None
    
    # Group issues by file
    issues_by_file = {}
    for issue in issues:
        file_path = issue.get('filePath', 'Unknown')
        if file_path not in issues_by_file:
            issues_by_file[file_path] = []
        issues_by_file[file_path].append(issue)
    
    # Format message
    file_count = len(issues_by_file)
    total_issues = len(issues)
    
    message = f'SonarLint found {total_issues} issue(s) in {file_count} file(s):\n\n'
    
    for file_path, file_issues in issues_by_file.items():
        file_name = os.path.basename(file_path)
        message += f'{file_name}:\n'
        
        for issue in file_issues:
            line = issue.get('startLine', '?')
            msg = issue.get('message', 'No description')
            rule_key = issue.get('ruleKey', '')
            message += f'  - Line {line}: {msg}'
            if rule_key:
                message += f' ({rule_key})'
            message += '\n'
        message += '\n'
    
    message += 'Please fix these issues.'
    return message

def cleanup_session_file(conversation_id):
    """Remove the session tracking file"""
    try:
        session_file = get_session_file_path(conversation_id)
        if os.path.exists(session_file):
            os.remove(session_file)
    except Exception:
        pass

def main():
    try:
        event_json = sys.stdin.read()
        event = json.loads(event_json)
        
        conversation_id = event.get('conversation_id')
        status = event.get('status')
        loop_count = event.get('loop_count', 0)
        
        # Only proceed if completed and haven't looped too many times
        if status != 'completed' or loop_count >= MAX_LOOP_COUNT:
            cleanup_session_file(conversation_id)
            print('{}')
            sys.exit(0)
        
        # Read tracked files
        session_file = get_session_file_path(conversation_id)
        if not os.path.exists(session_file):
            print('{}')
            sys.exit(0)
        
        with open(session_file, 'r', encoding='utf-8') as f:
            tracked_files = json.load(f)
        
        if len(tracked_files) == 0:
            cleanup_session_file(conversation_id)
            print('{}')
            sys.exit(0)
        
        # Find backend
        port = find_backend_port()
        if not port:
            cleanup_session_file(conversation_id)
            print('{}')
            sys.exit(0)
        
        # Analyze files
        analysis_result = analyze_files(port, tracked_files)
        
        # Format issues
        message = format_issues(analysis_result)
        
        # Cleanup
        cleanup_session_file(conversation_id)
        
        # Return response
        if message:
            print(json.dumps({'followup_message': message}))
        else:
            print('{}')
        
        sys.exit(0)
    except Exception:
        print('{}')
        sys.exit(0)

if __name__ == '__main__':
    main()

