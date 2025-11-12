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

# Extract file paths from the event
FILES=$(echo "$EVENT_JSON" | jq -r '.files[]? // empty' 2>/dev/null || echo "")

if [ -z "$FILES" ]; then
  echo "No files to analyze"
  exit 0
fi

# Build JSON array of file paths
FILE_ARRAY=$(echo "$FILES" | jq -R . | jq -s .)
REQUEST_BODY=$(jq -n --argjson files "$FILE_ARRAY" '{fileAbsolutePaths: $files}')

# Call SonarQube for IDE analysis endpoint
RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -H "Origin: ai-agent://{{AGENT}}" \
  -d "$REQUEST_BODY" \
  "http://localhost:{{PORT}}/sonarlint/api/analysis/files" 2>/dev/null || echo '{"findings":[]}')

# Check if the request was successful
if [ $? -ne 0 ]; then
  echo "Warning: Failed to connect to SonarQube for IDE backend on port {{PORT}}" >&2
  exit 0
fi

# Format and output findings
FINDINGS_COUNT=$(echo "$RESPONSE" | jq '.findings | length' 2>/dev/null || echo "0")

if [ "$FINDINGS_COUNT" -gt 0 ]; then
  echo "SonarQube for IDE Analysis Results:"
  echo "$RESPONSE" | jq -r '.findings[] | "[\(.severity // "UNKNOWN")] \(.ruleKey): \(.message) at \(.filePath):\(.textRange.startLine // "?")"' 2>/dev/null || echo "Error parsing results"
else
  echo "No issues found"
fi

