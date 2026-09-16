"""W4 scope, provenance and immutable Flyway verification (read-only except evidence)."""
from pathlib import Path
import hashlib
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
MIGRATIONS = ROOT / 'xsy-scm-server/sa-admin/src/main/resources/db/migration'

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    assertions = {}
    changes = subprocess.check_output(['git', 'diff', '--name-only', 'HEAD'], cwd=ROOT, text=True).splitlines()
    assertions['A_reference_unchanged'] = not any(p.startswith('project-reference-examples/') for p in changes)
    for label, manifest, count in [('B_V1_V12', 'pg-closure-applied-migrations.sha256', 12), ('C_V13_V14', 'w4-applied-migrations.sha256', 2)]:
        records = (ROOT / 'docs/architecture' / manifest).read_text(encoding='utf-8-sig').splitlines()
        valid = [line.split(' *', 1) for line in records if line.strip()]
        assertions[label] = len(valid) == count and all(digest(MIGRATIONS / path) == sha.lower() for sha, path in valid)
    forbidden = ('/scm/product/', '/scm/customer/', '/scm/pricing/', '/module/system/', 'xsy-scm-server/sa-base/', 'xsy-scm-miniapp/')
    assertions['D_frozen_domains'] = not any(any(part in path for part in forbidden) for path in changes)
    order = ROOT / 'xsy-scm-server/sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/order'
    service_text = '\n'.join(p.read_text(encoding='utf-8') for p in (order / 'service').glob('*.java'))
    assertions['E_no_inventory_implementation'] = not re.search(r'\.reserve\(|\.release\(|saleOutbound|stockOperateService|inventory_balance|inventory_movement', service_text)
    sql = (MIGRATIONS / 'V14__scm_sales_order_permissions.sql').read_text(encoding='utf-8')
    components = re.findall(r"'(/business/scm/order/[^']+\.vue)'", sql)
    assertions['F_menu_components'] = len(components) == 4 and all((ROOT / 'xsy-scm-web/src/views' / path.lstrip('/')).is_file() for path in components)
    views = ROOT / 'xsy-scm-web/src/views/business/scm/order'
    copied = [views / name for name in ('order-list.vue', 'order-return-list.vue', 'order-refund-list.vue', 'order-log-list.vue', 'components/order-item-editable-table.vue')]
    assertions['G_provenance_headers'] = all('Copy First + Adapt' in p.read_text(encoding='utf-8')[:600] and 'project-reference-examples/xsy-scm/' in p.read_text(encoding='utf-8')[:600] for p in copied)
    ids = re.findall(r'VALUES \((6\d\d),', sql)
    grants = re.search(r'm.menu_id IN \(([^)]+)\)', sql)
    assertions['H_role_grants'] = len(ids) == 22 and set(ids) == set(grants[1].split(','))
    source_hashes = json.loads((ROOT / 'docs/architecture/w4-frontend-source-sha256.json').read_text(encoding='utf-8'))
    assertions['I_source_hashes'] = all(digest(ROOT / p) == sha for p, sha in source_hashes.items())
    result = {'assertions': assertions, 'pass': all(assertions.values()), 'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()}
    out = ROOT / '.runtime';out.mkdir(exist_ok=True)
    (out / 'w4-legacy-recheck.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))
    return 0 if result['pass'] else 1

if __name__ == '__main__':
    raise SystemExit(main())
