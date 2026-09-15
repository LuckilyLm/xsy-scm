"""W2 legacy 冻结复验：与 W1 同一份冻结清单，只把「已应用 migration」清单扩到 V9。

W1 的 `verify_w1_legacy.py` 校验 V6/V7；W2 新增 V8/V9 之后，这两份也必须进入
「已应用即不可回改」的保护范围，否则一次静默改动就能让 Flyway 校验和与代码脱钩。
除清单路径外，判定规则与 W1 完全一致（不新增放行项）。
"""
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
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
            allowed = {'.workbuddy-ai', '**/.runtime/'}
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
    migration_root = ROOT / 'xsy-scm-server/sa-admin/src/main/resources/db/migration'
    applied = (ROOT / 'docs/architecture/w2-applied-migrations.sha256').read_text(encoding='utf-8-sig').splitlines()
    for line in applied:
        expected, filename = line.split(' *', 1)
        if hashlib.sha256((migration_root / filename).read_bytes()).hexdigest() != expected.lower():
            failures.append(f'APPLIED MIGRATION CHANGED {filename}')
    output = ROOT / '.runtime'
    output.mkdir(exist_ok=True)
    (output / 'w2-legacy-recheck.manifest').write_text('\n'.join(actual) + '\n', encoding='utf-8')
    result = {'files': len(entries), 'head': head,
              'approved_difference': '.gitignore: .workbuddy-ai + **/.runtime/',
              'applied_migrations_checked': [line.split(' *', 1)[1] for line in applied],
              'failures': failures, 'pass': not failures}
    (output / 'w2-legacy-recheck.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))
    return 1 if failures else 0


if __name__ == '__main__':
    raise SystemExit(verify())
