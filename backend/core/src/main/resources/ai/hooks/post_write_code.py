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
        
        # Extract file path from tool_info (single file for post_write_code)
        tool_info = event.get('tool_info', {})
        file_path = tool_info.get('file_path')
        
        if not file_path:
            print("No file to analyze")
            return
        
        # Build request body with single file
        request_body = json.dumps({'fileAbsolutePaths': [file_path]})
        
        # Call SonarQube for IDE analysis endpoint
        url = 'http://localhost:{{PORT}}/sonarlint/api/analysis/files'
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
    
    except json.JSONDecodeError as e:
        print(f"Error: Failed to parse JSON: {e}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == '__main__':
    main()

