#!/bin/bash
# SonarQube for IDE {{AGENT}} Hook - sonarqube_analysis_hook
# Auto-generated script for Bash
# Connects AI Agents to SonarQube for IDE backend

set -e

STARTING_PORT=64120
ENDING_PORT=64130
EXPECTED_IDE_NAME="{{AGENT}}"
PORT_SCAN_TIMEOUT=0.1

find_backend_port() {
  for port in $(seq $STARTING_PORT $ENDING_PORT); do
    if check_port "$port"; then
      echo "$port"
      return 0
    fi
  done
  return 1
}

check_port() {
  local port=$1
  local status_json=$(curl -s --max-time $PORT_SCAN_TIMEOUT -H "Origin: ai-agent://{{AGENT}}" "http://localhost:${port}/sonarlint/api/status" 2>/dev/null)
  
  if [ $? -ne 0 ]; then
    return 1
  fi
  
  local ide_name=$(echo "$status_json" | jq -r '.ideName // empty' 2>/dev/null)
  
  if [ "$ide_name" = "$EXPECTED_IDE_NAME" ]; then
    return 0
  fi
  
  return 1
}

analyze_file() {
  local port=$1
  local file_path=$2
  echo "Analyzing: $file_path (port $port)"
  local request_body=$(jq -n --arg file "$file_path" '{fileAbsolutePaths: [$file]}')

  curl -s --max-time 5 -X POST \
    -H "Content-Type: application/json" \
    -H "Origin: ai-agent://{{AGENT}}" \
    -H "Connection: close" \
    -d "$request_body" \
    "http://localhost:${port}/sonarlint/api/analysis/files" \
    > /dev/null 2>&1 &

  return 0
}

EVENT_JSON=$(cat)

if ! command -v jq &> /dev/null; then
  echo "jq not found"
  exit 1
fi

# Support both Windsurf (tool_info.file_path) and Cursor (file_path) event formats
FILE_PATH=$(echo "$EVENT_JSON" | jq -r '.file_path // .tool_info.file_path // empty' 2>/dev/null || echo "")

if [ -z "$FILE_PATH" ]; then
  echo "No file path in event"
  exit 0
fi

BACKEND_PORT=$(find_backend_port)
if [ $? -ne 0 ]; then
  echo "Backend not found"
  exit 1
fi

analyze_file "$BACKEND_PORT" "$FILE_PATH"

