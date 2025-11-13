#!/bin/bash
# SonarQube for IDE {{AGENT}} Hook - post_write_code
# Auto-generated script for Bash
# Connects to SonarQube for IDE backend on port {{PORT}}

set -e

# Read the event JSON from stdin
EVENT_JSON=$(cat)

# Check if jq is available
if ! command -v jq &> /dev/null; then
  echo "Error: jq is required but not installed. Please install jq to use this hook." >&2
  exit 1
fi

# Extract file path from tool_info (single file for post_write_code)
FILE_PATH=$(echo "$EVENT_JSON" | jq -r '.tool_info.file_path // empty' 2>/dev/null || echo "")

if [ -z "$FILE_PATH" ]; then
  echo "No file to analyze"
  exit 0
fi

# Build JSON request body with single file
REQUEST_BODY=$(jq -n --arg file "$FILE_PATH" '{fileAbsolutePaths: [$file]}')

# Call SonarQube for IDE analysis endpoint
HTTP_STATUS=$(curl -s -w "%{http_code}" -o /dev/null -X POST \
  -H "Content-Type: application/json" \
  -H "Origin: ai-agent://{{AGENT}}" \
  -d "$REQUEST_BODY" \
  "http://localhost:{{PORT}}/sonarlint/api/analysis/files" 2>/dev/null)

# Check if the request was successful
if [ "$HTTP_STATUS" = "200" ]; then
  echo "Analysis completed"
else
  echo "Analysis failed with status: $HTTP_STATUS" >&2
  exit 1
fi

