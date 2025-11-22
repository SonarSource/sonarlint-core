#!/bin/bash
# SonarQube for IDE {{AGENT}} Hook - Analyze and Report Issues
# Auto-generated script for Bash
# Analyzes tracked files and reports issues back to the agent

set -e

STARTING_PORT=64120
ENDING_PORT=64130
EXPECTED_IDE_NAME="{{AGENT}}"
PORT_SCAN_TIMEOUT=0.1
ANALYSIS_TIMEOUT=30
MAX_LOOP_COUNT=2

get_session_file_path() {
  local conversation_id=$1
  echo "${TMPDIR:-/tmp}/sonarlint-cursor-${conversation_id}.json"
}

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

analyze_files() {
  local port=$1
  local files_json=$2
  
  local request_body=$(jq -n --argjson files "$files_json" '{fileAbsolutePaths: $files}')
  
  local result=$(curl -s --max-time $ANALYSIS_TIMEOUT -X POST \
    -H "Content-Type: application/json" \
    -H "Origin: ai-agent://{{AGENT}}" \
    -d "$request_body" \
    "http://localhost:${port}/sonarlint/api/analysis/files" 2>/dev/null)
  
  echo "$result"
}

format_issues() {
  local analysis_result=$1
  
  local issues_count=$(echo "$analysis_result" | jq '.issues | length' 2>/dev/null || echo "0")
  
  if [ "$issues_count" = "0" ]; then
    echo ""
    return
  fi
  
  local message="SonarLint found ${issues_count} issue(s):\n\n"
  
  # Group issues by file and format
  local files=$(echo "$analysis_result" | jq -r '.issues | group_by(.filePath) | .[] | @json')
  
  while IFS= read -r file_issues; do
    local file_path=$(echo "$file_issues" | jq -r '.[0].filePath')
    local file_name=$(basename "$file_path")
    
    message="${message}${file_name}:\n"
    
    local issue_lines=$(echo "$file_issues" | jq -r '.[] | "  - Line \(.startLine // "?"): \(.message // "No description")\(if .ruleKey then " (\(.ruleKey))" else "" end)"')
    message="${message}${issue_lines}\n\n"
  done <<< "$files"
  
  message="${message}Please fix these issues."
  echo -e "$message"
}

cleanup_session_file() {
  local conversation_id=$1
  local session_file=$(get_session_file_path "$conversation_id")
  
  if [ -f "$session_file" ]; then
    rm -f "$session_file" 2>/dev/null || true
  fi
}

EVENT_JSON=$(cat)

if ! command -v jq &> /dev/null; then
  echo "{}"
  exit 0
fi

CONVERSATION_ID=$(echo "$EVENT_JSON" | jq -r '.conversation_id // empty' 2>/dev/null || echo "")
STATUS=$(echo "$EVENT_JSON" | jq -r '.status // empty' 2>/dev/null || echo "")
LOOP_COUNT=$(echo "$EVENT_JSON" | jq -r '.loop_count // 0' 2>/dev/null || echo "0")

# Only proceed if completed and haven't looped too many times
if [ "$STATUS" != "completed" ] || [ "$LOOP_COUNT" -ge "$MAX_LOOP_COUNT" ]; then
  cleanup_session_file "$CONVERSATION_ID"
  echo "{}"
  exit 0
fi

# Read tracked files
SESSION_FILE=$(get_session_file_path "$CONVERSATION_ID")
if [ ! -f "$SESSION_FILE" ]; then
  echo "{}"
  exit 0
fi

TRACKED_FILES=$(cat "$SESSION_FILE" 2>/dev/null || echo "[]")
FILES_COUNT=$(echo "$TRACKED_FILES" | jq 'length' 2>/dev/null || echo "0")

if [ "$FILES_COUNT" = "0" ]; then
  cleanup_session_file "$CONVERSATION_ID"
  echo "{}"
  exit 0
fi

# Find backend
BACKEND_PORT=$(find_backend_port)
if [ $? -ne 0 ]; then
  cleanup_session_file "$CONVERSATION_ID"
  echo "{}"
  exit 0
fi

# Analyze files
ANALYSIS_RESULT=$(analyze_files "$BACKEND_PORT" "$TRACKED_FILES")

# Format issues
MESSAGE=$(format_issues "$ANALYSIS_RESULT")

# Cleanup
cleanup_session_file "$CONVERSATION_ID"

# Return response
if [ -n "$MESSAGE" ]; then
  jq -n --arg msg "$MESSAGE" '{followup_message: $msg}'
else
  echo "{}"
fi

exit 0

