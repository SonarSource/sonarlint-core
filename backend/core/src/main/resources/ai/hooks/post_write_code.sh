#!/bin/bash
# SonarQube for IDE {{AGENT}} Hook - post_write_code
# Auto-generated script for Bash
# Connects to SonarQube for IDE backend

set -e

STARTING_PORT=64120
ENDING_PORT=64130
EXPECTED_IDE_NAME="{{AGENT}}"
PORT_SCAN_TIMEOUT=0.05  # 50ms per port

# Fast port discovery: find the correct SonarQube for IDE backend
find_backend_port() {
  for port in $(seq $STARTING_PORT $ENDING_PORT); do
    if check_port "$port"; then
      echo "$port"
      return 0
    fi
  done
  return 1
}

# Check if a port has a valid SonarQube for IDE backend
check_port() {
  local port=$1
  local status_json=$(curl -s --max-time $PORT_SCAN_TIMEOUT "http://localhost:${port}/sonarlint/api/status" 2>/dev/null)
  
  if [ $? -ne 0 ]; then
    return 1
  fi
  
  local ide_name=$(echo "$status_json" | jq -r '.ideName // empty' 2>/dev/null)
  
  if [ "$ide_name" = "$EXPECTED_IDE_NAME" ]; then
    return 0
  fi
  
  return 1
}

# Call the analysis endpoint
analyze_file() {
  local port=$1
  local file_path=$2
  local request_body=$(jq -n --arg file "$file_path" '{fileAbsolutePaths: [$file]}')
  
  local http_status=$(curl -s -w "%{http_code}" -o /dev/null -X POST \
    -H "Content-Type: application/json" \
    -H "Origin: ai-agent://{{AGENT}}" \
    -d "$request_body" \
    "http://localhost:${port}/sonarlint/api/analysis/files" 2>/dev/null)
  
  if [ "$http_status" = "200" ]; then
    echo "Analysis completed"
    return 0
  else
    echo "Analysis failed with status: $http_status" >&2
    return 1
  fi
}

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

# Find the backend port
BACKEND_PORT=$(find_backend_port)
if [ $? -ne 0 ]; then
  echo "SonarQube for IDE backend not found" >&2
  exit 1
fi

# Call analysis endpoint
analyze_file "$BACKEND_PORT" "$FILE_PATH"

