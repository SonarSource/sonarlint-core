#!/usr/bin/env python3
# SonarQube for IDE {{AGENT}} Hook - post_write_code
# Auto-generated script for Python
# Connects to SonarQube for IDE backend on port {{PORT}}

import sys
import json
import urllib.request
import urllib.error

def main():
    try:
        # Read the event JSON from stdin
        event_json = sys.stdin.read()
        event = json.loads(event_json)
        
        # Extract file paths from the event
        files = event.get('files', [])
        
        if not files:
            print("No files to analyze")
            return
        
        # Build request body
        request_body = json.dumps({'fileAbsolutePaths': files})
        
        # Call SonarQube for IDE analysis endpoint
        url = 'http://localhost:{{PORT}}/sonarlint/api/analysis/files'
        req = urllib.request.Request(
            url,
            data=request_body.encode('utf-8'),
            headers={'Content-Type': 'application/json'}
        )
        
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                result = json.loads(response.read().decode('utf-8'))
        except urllib.error.URLError as e:
            print(f"Warning: Failed to connect to SonarQube for IDE backend on port {{PORT}}: {e}", file=sys.stderr)
            return
        
        # Format and output findings
        findings = result.get('findings', [])
        
        if findings:
            print("SonarQube for IDE Analysis Results:")
            for finding in findings:
                severity = finding.get('severity', 'UNKNOWN')
                rule_key = finding.get('ruleKey', 'unknown')
                message = finding.get('message', 'No message')
                file_path = finding.get('filePath', 'unknown')
                text_range = finding.get('textRange', {})
                start_line = text_range.get('startLine', '?')
                print(f"[{severity}] {rule_key}: {message} at {file_path}:{start_line}")
        else:
            print("No issues found")
    
    except json.JSONDecodeError as e:
        print(f"Error: Failed to parse JSON: {e}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == '__main__':
    main()

