#!/bin/bash
# SonarQube for IDE {{AGENT}} Hook - post_write_code
# Auto-generated script for Bash
# Connects to SonarQube for IDE backend

set -e

STARTING_PORT=64120
ENDING_PORT=64130
EXPECTED_IDE_NAME="{{AGENT}}"
PORT_SCAN_TIMEOUT=0.1

debug() {
  echo "[DEBUG] $1" >&2
}

find_backend_port() {
  debug "Starting port scan from $STARTING_PORT to $ENDING_PORT"
  for port in $(seq $STARTING_PORT $ENDING_PORT); do
    if check_port "$port"; then
      debug "Found backend on port $port"
      echo "$port"
      return 0
    fi
  done
  debug "No backend port found"
  return 1
}

check_port() {
  local port=$1
  local status_json=$(curl -s --max-time $PORT_SCAN_TIMEOUT -H "Origin: ai-agent://{{AGENT}}" "http://localhost:${port}/sonarlint/api/status" 2>/dev/null)
  
  if [ $? -ne 0 ]; then
    debug "Port $port error: curl failed"
    return 1
  fi
  
  local ide_name=$(echo "$status_json" | jq -r '.ideName // empty' 2>/dev/null)
  
  if [ -z "$ide_name" ]; then
    debug "Port $port: no ideName in response"
    return 1
  fi
  
  debug "Port $port responded with ideName: $ide_name"
  
  if [ "$ide_name" = "$EXPECTED_IDE_NAME" ]; then
    debug "Port $port matches expected IDE: $EXPECTED_IDE_NAME"
    return 0
  fi
  
  debug "Port $port IDE mismatch. Expected: $EXPECTED_IDE_NAME, Got: $ide_name"
  return 1
}

analyze_file() {
  local port=$1
  local file_path=$2
  debug "Triggering analysis for file: $file_path on port $port"
  local request_body=$(jq -n --arg file "$file_path" '{fileAbsolutePaths: [$file]}')

  curl -s --max-time 5 -X POST \
    -H "Content-Type: application/json" \
    -H "Origin: ai-agent://{{AGENT}}" \
    -H "Connection: close" \
    -d "$request_body" \
    "http://localhost:${port}/sonarlint/api/analysis/files" \
    > /dev/null 2>&1 &

  debug "Analysis request sent (fire-and-forget)"
  return 0
}

debug "Hook script started"
EVENT_JSON=$(cat)
debug "Received event from stdin"

if ! command -v jq &> /dev/null; then
  debug "jq not found"
  exit 1
fi

debug "Parsing event JSON"
FILE_PATH=$(echo "$EVENT_JSON" | jq -r '.tool_info.file_path // empty' 2>/dev/null || echo "")

if [ -z "$FILE_PATH" ]; then
  debug "No file_path in event"
  exit 0
fi

debug "File to analyze: $FILE_PATH"
BACKEND_PORT=$(find_backend_port)
if [ $? -ne 0 ]; then
  debug "Backend not found, exiting with error"
  exit 1
fi

analyze_file "$BACKEND_PORT" "$FILE_PATH"

