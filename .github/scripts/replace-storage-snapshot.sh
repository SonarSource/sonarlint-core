#!/usr/bin/env bash
# Replaces the storage snapshot of StorageUpgradeMediumTests with the one generated from a release, for the version bump PR,
# and writes a note describing the outcome for the PR body.
#
# Master must always keep exactly one valid snapshot, otherwise the upgrade test fails on every PR. So this script never fails:
# when the new snapshot is missing, incomplete or cannot be copied, the previous one is kept.
#
# Environment:
#   SNAPSHOT_RESULT   result of the job generating the snapshot: success, failure, cancelled or skipped
#   RELEASED_VERSION  the release tag
#   GENERATED_DIR     the generated snapshot folder
#   GITHUB_OUTPUT     receives the note, as the "note" output
set -uo pipefail

SNAPSHOTS_DIR=medium-tests/src/test/resources/storage-snapshots

# Files

is_complete() {
  [ -s "$GENERATED_DIR/h2/sq-ide.mv.db" ] && [ -s "$GENERATED_DIR/snapshot.properties" ]
}

replace_snapshot() {
  find "$SNAPSHOTS_DIR" -mindepth 1 -maxdepth 1 -type d -exec rm -rf {} + && cp -R "$GENERATED_DIR" "$SNAPSHOTS_DIR/"
}

# Git

restore_previous_snapshot() {
  git checkout -q -- "$SNAPSHOTS_DIR"
  git clean -fdq -- "$SNAPSHOTS_DIR"
}

# Messages

replaced_note() {
  printf 'The storage snapshot was regenerated from %s. Review the changes of `snapshot.properties` and `schema.sql`, the `.mv.db` file always differs.' \
    "$RELEASED_VERSION"
}

kept_note() {
  printf '> [!WARNING]\n> The storage snapshot could not be regenerated from %s, the previous one is kept. Generate it manually, see `%s/README.md`.' \
    "$RELEASED_VERSION" "$SNAPSHOTS_DIR"
}

write_note() {
  {
    echo "note<<EOF"
    echo "$1"
    echo "EOF"
  } >> "$GITHUB_OUTPUT"
}

if [ "$SNAPSHOT_RESULT" = skipped ]; then
  # Not a release from master, its snapshot does not belong there
  write_note ""
elif [ "$SNAPSHOT_RESULT" = success ] && is_complete && replace_snapshot; then
  write_note "$(replaced_note)"
else
  restore_previous_snapshot
  write_note "$(kept_note)"
fi
exit 0
