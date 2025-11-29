#!/bin/bash
# SonarQube for IDE {{AGENT}} Hook - Track File Edits
# Auto-generated script for Bash
# Tracks files edited during the AI agent session

set -e

get_session_file_path() {
  local conversation_id=$1
  echo "${TMPDIR:-/tmp}/sonarlint-cursor-${conversation_id}.json"
}

track_file() {
  local conversation_id=$1
  local file_path=$2
  local session_file=$(get_session_file_path "$conversation_id")
  
  local tracked_files="[]"
  if [ -f "$session_file" ]; then
    tracked_files=$(cat "$session_file" 2>/dev/null || echo "[]")
  fi
  
  # Check if file is already tracked
  local already_tracked=$(echo "$tracked_files" | jq --arg file "$file_path" 'index($file) != null')
  
  if [ "$already_tracked" = "false" ]; then
    # Add file to tracked list
    tracked_files=$(echo "$tracked_files" | jq --arg file "$file_path" '. + [$file]')
    echo "$tracked_files" > "$session_file"
  fi
}

EVENT_JSON=$(cat)

if ! command -v jq &> /dev/null; then
  exit 1
fi

CONVERSATION_ID=$(echo "$EVENT_JSON" | jq -r '.conversation_id // empty' 2>/dev/null || echo "")
FILE_PATH=$(echo "$EVENT_JSON" | jq -r '.file_path // empty' 2>/dev/null || echo "")

if [ -z "$CONVERSATION_ID" ] || [ -z "$FILE_PATH" ]; then
  exit 0
fi

track_file "$CONVERSATION_ID" "$FILE_PATH"
exit 0

