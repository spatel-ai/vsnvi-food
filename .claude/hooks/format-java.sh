#!/usr/bin/env bash
# PostToolUse hook: format a Java file with Spotless after Claude writes or edits it.
# Reads the hook JSON on stdin. Only the edited file is formatted.
set -uo pipefail

file=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty')

case "$file" in
  *.java) ;;
  *) exit 0 ;;
esac
[ -f "$file" ] || exit 0

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}" || exit 0

# spotlessFiles is a regex matched against absolute paths, so escape regex metacharacters.
pattern=$(printf '%s' "$file" | sed 's/[.[\*^$()+?{|]/\\&/g')

if ! output=$(./mvnw -q spotless:apply -DspotlessFiles="$pattern" 2>&1); then
  echo "Spotless failed for $file:" >&2
  printf '%s\n' "$output" | grep -v '^WARNING:' | tail -20 >&2
  exit 1   # non-blocking: reported, the edit stays
fi
exit 0
