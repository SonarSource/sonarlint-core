#!/usr/bin/env python3
# SonarQube for IDE {{AGENT}} Hook - Track File Edits
# Auto-generated script for Python
# Tracks files edited during the AI agent session

import sys
import json
import os
import tempfile


def get_session_file_path(conversation_id):
    temp_dir = tempfile.gettempdir()
    return os.path.join(temp_dir, f'sonarlint-cursor-{conversation_id}.json')


def track_file(conversation_id, file_path):
    session_file = get_session_file_path(conversation_id)

    tracked_files = []
    try:
        if os.path.exists(session_file):
            with open(session_file, 'r', encoding='utf-8') as f:
                tracked_files = json.load(f)
    except Exception:
        # File doesn't exist or is invalid, start fresh
        tracked_files = []

    # Add file if not already tracked
    if file_path not in tracked_files:
        tracked_files.append(file_path)
        with open(session_file, 'w', encoding='utf-8') as f:
            json.dump(tracked_files, f, indent=2)


def main():
    try:
        event_json = sys.stdin.read()
        event = json.loads(event_json)

        conversation_id = event.get('conversation_id')
        file_path = event.get('file_path')

        if not conversation_id or not file_path:
            sys.exit(0)

        track_file(conversation_id, file_path)
        sys.exit(0)
    except Exception as e:
        print(f'Error: {e}')
        sys.exit(1)


if __name__ == '__main__':
    main()
