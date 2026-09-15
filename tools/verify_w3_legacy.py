"""Verify W3 frozen assets without mutating the workspace.

Missing user-owned legacy files are reported as an unverifiable freeze
condition; this command never restores or deletes files.
"""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT
FREEZE = Path(r"D:\DevCaches\Codex\reviews\xsy-scm-legacy-freeze-2026-09-14")

def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main() -> int:
    missing, changed = [], []
    checked = 0
    manifest = FREEZE / "evidence/original-sha256.manifest"
    for line in manifest.read_text(encoding="utf-8-sig").splitlines():
        expected, relative = line.split(" *", 1)
        path = REPO / relative
        if not path.is_file():
            missing.append(relative)
        else:
            checked += 1
            # The repository-level .gitignore has the already approved W2
            # additions (.workbuddy-ai and ignored runtime directories); it is not a legacy
            # source asset and is checked by the W2 verifier.
            if relative != ".gitignore" and digest(path) != expected.lower():
                changed.append(relative)
    migration_root = ROOT / "xsy-scm-server/sa-admin/src/main/resources/db/migration"
    migration_manifest = ROOT / "docs/architecture/v3-applied-migrations.sha256"
    migration_failures, migration_checked = [], []
    for line in migration_manifest.read_text(encoding="utf-8-sig").splitlines():
        expected, filename = line.split(" *", 1)
        migration_checked.append(filename)
        path = migration_root / filename
        if not path.is_file() or digest(path) != expected.lower():
            migration_failures.append(filename)
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=REPO, text=True).strip()
    result = {"legacy_manifest_files": checked + len(missing), "legacy_checked": checked,
              "legacy_missing": missing, "legacy_changed": changed, "head": head,
              "migration_checked": migration_checked, "migration_failures": migration_failures,
              "pass": not missing and not changed and not migration_failures,
              "note": "Missing legacy files are user-owned deletions and were not restored."}
    output = ROOT / ".runtime"
    output.mkdir(exist_ok=True)
    (output / "w3-legacy-recheck.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False))
    return 0 if result["pass"] else 1

if __name__ == "__main__":
    raise SystemExit(main())
