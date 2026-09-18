#!/usr/bin/env bash
# Both platform entry points share checks and exit codes.
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for interpreter in python3 python; do
    # Windows may expose a python3 store alias without an installed interpreter.
    if command -v "$interpreter" >/dev/null 2>&1 &&
        "$interpreter" -c 'import sys; sys.exit(sys.version_info < (3, 10))' >/dev/null 2>&1; then
        exec "$interpreter" "$ROOT/tools/verify.py" "${1:-all}"
    fi
done
printf 'Python 3.10+ is required.\n' >&2
exit 1
