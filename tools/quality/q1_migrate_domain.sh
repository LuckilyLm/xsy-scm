#!/usr/bin/env bash
# Q1 per-domain migration driver — enforces the §3.1 order from
# docs/quality/package-migration-readiness.md.
#
# Usage:  tools/quality/q1_migrate_domain.sh <domain> <step> [targeted-test-pattern]
#   step 1 : git mv + rewrite + baseline migration + gates (stops before capture)
#   step 2 : capture + check + verify.py quality  (run AFTER targeted tests pass)
#
# Deliberately does NOT pre-create the <domain> directory: doing so makes
# `git mv` nest the source inside it (com/xsy/scm/<domain>/<domain>/).
set -euo pipefail

DOMAIN="${1:?usage: $0 <domain> <step> [test-pattern]}"
STEP="${2:?usage: $0 <domain> <step> [test-pattern]}"
PATTERN="${3:-}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

LEGACY_DIR="xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/$DOMAIN"
NEW_DIR="xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/$DOMAIN"
LEGACY_TEST="xsy-scm-server/sa-admin/src/test/java/net/lab1024/sa/admin/module/scm/$DOMAIN"
NEW_TEST="xsy-scm-server/sa-admin/src/test/java/com/xsy/scm/$DOMAIN"

PY=python

case "$STEP" in
1)
  echo "=== [$DOMAIN] step 1/2: move + rewrite + baseline + gates ==="

  # --- ① git mv (do NOT pre-create the <domain> level) ---
  mkdir -p "xsy-scm-server/sa-admin/src/main/java/com/xsy/scm"
  mkdir -p "xsy-scm-server/sa-admin/src/test/java/com/xsy/scm"
  git mv "$LEGACY_DIR" "$NEW_DIR"
  git mv "$LEGACY_TEST" "$NEW_TEST"

  # self-check: no nested <domain>/<domain>
  if find xsy-scm-server/sa-admin/src -type d -name "$DOMAIN" | grep -q "$DOMAIN/$DOMAIN"; then
    echo "FATAL: nested $DOMAIN/$DOMAIN detected — aborting"; exit 1
  fi
  echo "[ok] moved, no nesting"

  # --- ② rewrite package/imports/XML/full-qualified refs ---
  $PY tools/quality/migrate_scm_package.py --domain "$DOMAIN" --dry-run | tee /tmp/q1_$DOMAIN.dryrun
  echo "--- checking dry-run paths for nested domain ---"
  if grep -q "/$DOMAIN/$DOMAIN/" /tmp/q1_$DOMAIN.dryrun; then
    echo "FATAL: dry-run shows nested paths — aborting"; exit 1
  fi
  $PY tools/quality/migrate_scm_package.py --domain "$DOMAIN" --apply | tail -3
  echo "--- idempotency re-run (expect NOTHING TO DO) ---"
  $PY tools/quality/migrate_scm_package.py --domain "$DOMAIN" --apply | tail -2

  # --- ③ baseline path migration: four invariants ---
  $PY tools/quality/migrate_baseline_paths.py --domain "$DOMAIN" --dry-run | tail -14
  $PY tools/quality/migrate_baseline_paths.py --domain "$DOMAIN" --apply | tail -3

  # --- ④ no new debt ---
  # NOTE: checkstyle:check writes xsy-scm-server/target/checkstyle-result.xml by
  # scanning the *current* tree. It MUST be regenerated after the move:
  #   - if the report is stale, it still lists the old paths, and each violation
  #     is counted twice (IMPROVEMENT on the new path + NEW DEFECT on the old
  #     path) -> a spurious FAIL.
  #   - Maven's checkstyle plugin will NOT rewrite an existing result file that
  #     it thinks is up to date, so `rm -f` first.
  rm -f xsy-scm-server/target/checkstyle-result.xml
  ( cd xsy-scm-server && mvn -B -N checkstyle:check >/dev/null )
  $PY tools/quality/quality_guard.py check --checkstyle | tail -6

  # --- ⑤ domain completeness ---
  $PY tools/quality/package_migration_readiness.py --assert-domain-migrated "$DOMAIN" | tail -6

  # --- ⑥ compile ---
  ( cd xsy-scm-server && mvn -B -q -pl sa-admin -am clean compile -DskipTests )
  echo "[ok] compile clean"
  echo
  echo "NEXT: run targeted tests:  $PY tools/quality/q1_migrate_domain.sh $DOMAIN test ${PATTERN:-<Pattern>*}"
  echo "      then:                 $PY tools/quality/q1_migrate_domain.sh $DOMAIN 2"
  ;;

2)
  echo "=== [$DOMAIN] step 2/2: capture + check + verify ==="
  # regenerate checkstyle-result.xml if a clean wiped it
  if [ ! -f xsy-scm-server/target/checkstyle-result.xml ]; then
    echo "[warn] checkstyle-result.xml missing (mvn clean?) — regenerating"
    ( cd xsy-scm-server && mvn -B -N checkstyle:check >/dev/null )
  fi
  $PY tools/quality/quality_guard.py capture --checkstyle | tail -10
  $PY tools/quality/quality_guard.py check --checkstyle | tail -8
  $PY tools/verify.py quality | tail -6
  ;;

test)
  echo "=== [$DOMAIN] targeted tests (pattern: $PATTERN) ==="
  : "${PATTERN:?test pattern required for the 'test' step}"
  PW=$(grep -m1 '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)
  RPW=$(grep -m1 '^REDIS_PASSWORD=' .env | cut -d= -f2-)
  DB="xsy_q1_$DOMAIN"
  U=$(grep -m1 '^POSTGRES_USER=' .env | cut -d= -f2-)
  docker exec -e PGPASSWORD="$PW" xsy-scm-postgres-1 psql -U "$U" -d postgres \
    -c "DROP DATABASE IF EXISTS $DB WITH (FORCE);" -c "CREATE DATABASE $DB;" >/dev/null
  echo "[ok] fresh db $DB"
  ( cd xsy-scm-server && \
    XSY_V2_DB_URL="jdbc:p6spy:postgresql://127.0.0.1:15432/$DB?currentSchema=xsy_v2&ApplicationName=xsy-scm-v2-test" \
    XSY_V2_DB_USERNAME="$U" XSY_V2_DB_PASSWORD="$PW" \
    SPRING_DATA_REDIS_PASSWORD="$RPW" \
    mvn -B -pl sa-admin -am test -Dtest="$PATTERN" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 \
    | grep -E "Tests run:|BUILD|ERROR\]   [A-Z]" | tail -25 )
  echo "[done] to clean up:  docker exec -e PGPASSWORD='...' xsy-scm-postgres-1 psql -U $U -d postgres -c 'DROP DATABASE IF EXISTS $DB WITH (FORCE);'"
  ;;

*)
  echo "unknown step: $STEP (expected 1, 2, or test)"; exit 1;;
esac
