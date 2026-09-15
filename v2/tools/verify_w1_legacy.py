"""Verify frozen file bytes; allow only the two approved .gitignore additions."""
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
FREEZE = Path(r'D:\DevCaches\Codex\reviews\xsy-scm-legacy-freeze-2026-09-14')
HEAD = '95a54233586aa3c652e55cd833bb346d51f5a952'
APPROVED_GITIGNORE = '7a01b8ea6126557744f5e01696417de1d51944ab89557adf489534d05d0f2f3a'


def verify():
    entries = (FREEZE / 'evidence/original-sha256.manifest').read_text(encoding='utf-8-sig').splitlines()
    failures = []
    actual = []
    frozen_paths = set()
    for line in entries:
        expected, relative = line.split(' *', 1)
        frozen_paths.add(relative)
        path = ROOT / relative
        if not path.is_file():
            failures.append(f'MISSING {relative}')
            continue
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        actual.append(f'{digest} *{relative}')
        if relative == '.gitignore':
            baseline = (FREEZE / 'legacy/.gitignore').read_text(encoding='utf-8-sig').splitlines()
            current = path.read_text(encoding='utf-8-sig').splitlines()
            allowed = {'.workbuddy-ai', 'v2/.runtime'}
            if digest != APPROVED_GITIGNORE or any(current.count(item) != 1 for item in allowed) or [x for x in current if x not in allowed] != baseline:
                failures.append('UNAPPROVED .gitignore changes')
        elif digest != expected:
            failures.append(f'CHANGED {relative}')
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    if head != HEAD:
        failures.append('HEAD changed')
    candidates = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard', '--',
        'xsy-scm-server', 'xsy-scm-web', 'xsy-scm-miniapp', 'docs'], cwd=ROOT).decode('utf-8').split('\0')
    failures.extend(f'ADDED {path}' for path in sorted(set(candidates) - frozen_paths - {''}))
    migration_root = ROOT / 'v2/xsy-scm-v2-server/sa-admin/src/main/resources/db/migration'
    for line in (ROOT / 'v2/docs/architecture/w1-applied-migrations.sha256').read_text(encoding='utf-8-sig').splitlines():
        expected, filename = line.split(' *', 1)
        if hashlib.sha256((migration_root / filename).read_bytes()).hexdigest() != expected.lower():
            failures.append(f'APPLIED MIGRATION CHANGED {filename}')
    output = ROOT / 'v2/.runtime'
    output.mkdir(exist_ok=True)
    (output / 'w1-legacy-recheck.manifest').write_text('\n'.join(actual) + '\n', encoding='utf-8')
    result = {'files': len(entries), 'head': head, 'approved_difference': '.gitignore: .workbuddy-ai + v2/.runtime',
              'failures': failures, 'pass': not failures}
    (output / 'w1-legacy-recheck.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))
    return 1 if failures else 0


if __name__ == '__main__':
    raise SystemExit(verify())
