"""W5 scope, provenance, immutable-Flyway and zero-call-site verification.

Read-only except for the evidence file written under ``.runtime/``.

Assertions
----------
A  reference asset library untouched
B  V1-V14 byte-identical to the recorded manifests (V1-V12 closure + V13/V14 W4)
C  V15/V16 byte-identical to the W5 manifest
D  frozen W1-W4 domains, SmartAdmin base and the mini program untouched
E  no inventory implementation: the contract exists but has zero call sites
F  every V16 menu component path exists on disk
G  Provenance headers present on every copied frontend file
H  V16 menu ids and the role grant set are exactly the same set
I  no second infrastructure stack inside the W5 module
J  the A1-A32 frontend adaptations that are machine-checkable
K  the frozen 22-file frontend inventory plus the test files exist
"""
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / 'xsy-scm-server'
WEB = ROOT / 'xsy-scm-web'
MIGRATIONS = SERVER / 'sa-admin/src/main/resources/db/migration'
SCM = SERVER / 'sa-admin/src/main/java/net/lab1024/sa/admin/module/scm'
ARCH = ROOT / 'docs/architecture'
PURCHASE_VIEWS = WEB / 'src/views/business/scm/purchase'


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def changed_paths() -> list[str]:
    """Every touched path, with untracked directories expanded to individual files."""
    tracked = subprocess.check_output(
        ['git', 'diff', '--name-only', 'HEAD'], cwd=ROOT, text=True).splitlines()
    untracked = subprocess.check_output(
        ['git', 'ls-files', '--others', '--exclude-standard'], cwd=ROOT, text=True).splitlines()
    return [line for line in tracked + untracked if line.strip()]


def verify_manifest(name: str, expected: int) -> bool:
    records = (ARCH / name).read_text(encoding='utf-8-sig').splitlines()
    rows = [line.split(' *', 1) for line in records if line.strip()]
    return len(rows) == expected and all(
        digest(MIGRATIONS / path) == sha.lower() for sha, path in rows)


def code_only(path: Path) -> str:
    """Source with comments stripped.

    Provenance headers legitimately *name* the pruned constructs, so the
    pruned-construct gate must look at code, not at the header.  The strip order
    matters: a header writes a ``/**`` glob, and running the block-comment regex
    first would swallow the closing marker of the HTML comment.
    """
    text = path.read_text(encoding='utf-8')
    text = re.sub(r'<!--.*?-->', '', text, flags=re.S)
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    return re.sub(r'^\s*//.*$', '', text, flags=re.M)


def java_code_only(text: str) -> str:
    """Java source with comments stripped.

    Needed because W5 documents its own zero-call-site rule *in comments*
    (``// §4.3 第 15 步（W6）：PurchaseInventoryContract.postInbound(...)``), and a
    naive regex over raw text reports those comments as call sites.
    """
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    return re.sub(r'//[^\n]*', '', text)


def main() -> int:
    a: dict[str, bool] = {}
    changes = changed_paths()

    # A - the upstream reference library is a read-only asset.
    a['A_reference_unchanged'] = not any(
        p.startswith('project-reference-examples/') for p in changes)

    # B/C - Flyway is the only schema-evolution mechanism and applied files never change.
    a['B_V1_V14_immutable'] = (
        verify_manifest('pg-closure-applied-migrations.sha256', 12)
        and verify_manifest('w4-applied-migrations.sha256', 2))
    a['C_V15_V16_recorded'] = verify_manifest('w5-applied-migrations.sha256', 2)

    # D - frozen domains. Scope the match to main sources / web views so the
    # W5-owned test tree under module/scm/common (test bases) is not a violation.
    frozen = (
        'xsy-scm-miniapp/',
        'xsy-scm-server/sa-base/',
        'sa-admin/src/main/java/net/lab1024/sa/admin/module/system/',
        'sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/common/',
        'sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/product/',
        'sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/customer/',
        'sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/supplier/',
        'sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/pricing/',
        'sa-admin/src/main/java/net/lab1024/sa/admin/module/scm/order/',
        'src/views/business/scm/product/',
        'src/views/business/scm/customer/',
        'src/views/business/scm/supplier/',
        'src/views/business/scm/pricing/',
        'src/views/business/scm/order/',
        'src/layout/',
        'src/router/',
        'src/lib/',
        'src/components/system/',
    )
    a['D_frozen_domains'] = not any(any(part in p for part in frozen) for p in changes)

    # E - inventory is defined as a contract only; W5 must never mutate stock.
    # Comments are stripped first: W5 *documents* its own zero-call-site rule in a
    # comment, so a raw-text regex reports the documentation as a call site.
    purchase_main = java_code_only('\n'.join(
        p.read_text(encoding='utf-8') for p in (SCM / 'purchase').rglob('*.java')))
    contract = (SCM / 'purchase/support/NoOpPurchaseInventoryContract.java').read_text(encoding='utf-8')
    call_sites = re.findall(
        r'[A-Za-z_]\w*[Ii]nventory\w*\.(?:postInbound|queryAvailability)\s*\(', purchase_main)
    a['E_inventory_zero_call_sites'] = (
        not call_sites
        and re.search(r'class NoOpPurchaseInventoryContract', contract) is not None
        and not re.search(r'inventory_balance|inventory_movement|stockOperateService|saleOutbound',
                          purchase_main))

    # F - every menu component path is a real file (701 is a directory node: no component).
    sql = (MIGRATIONS / 'V16__scm_purchase_permissions.sql').read_text(encoding='utf-8')
    components = re.findall(r"'(/business/scm/purchase/[^']+\.vue)'", sql)
    a['F_menu_components'] = len(components) == 5 and all(
        (WEB / 'src/views' / path.lstrip('/')).is_file() for path in components)

    # G - copied files carry Provenance; brand-new files are not required to.
    copied = (
        'src/constants/business/scm/purchase-const.ts',
        'src/api/business/scm/purchase-order-api.ts',
        'src/api/business/scm/purchase-demand-api.ts',
        'src/api/business/scm/purchase-receipt-api.ts',
        'src/views/business/scm/purchase/purchase-order-list.vue',
        'src/views/business/scm/purchase/purchase-receipt-list.vue',
        'src/views/business/scm/purchase/components/purchase-demand-generate-modal.vue',
        'src/views/business/scm/purchase/components/purchase-order-item-editable-table.vue',
    )
    a['G_provenance_headers'] = len(copied) == 8 and all(
        'Copy First + Adapt' in (WEB / rel).read_text(encoding='utf-8')[:900]
        and 'project-reference-examples/xsy-scm/' in (WEB / rel).read_text(encoding='utf-8')[:900]
        for rel in copied)

    # H - V16 grants exactly the menus it defines.
    menu_ids = re.findall(r'INSERT INTO t_menu\(menu_id[^)]*\)\s*\n?VALUES \((\d+),', sql)
    grant = re.search(r'm\.menu_id IN \(([^)]+)\)', sql)
    a['H_role_grants'] = (
        len(menu_ids) == 25
        and grant is not None
        and set(menu_ids) == set(grant.group(1).split(',')))

    # I - SmartAdmin Native First: no second response envelope / paging / advice / mapper.
    forbidden = ('mapstruct', 'class ResponseDTO', 'class PageParam', 'class PageResult',
                 '@RestControllerAdvice', 'class ScmIdempotencyService')
    a['I_no_second_infra'] = not any(token in purchase_main for token in forbidden)

    # J - machine-checkable subset of the A1-A32 adaptation list.
    views = list(PURCHASE_VIEWS.rglob('*.vue'))
    view_code = {p: code_only(p) for p in views}
    constants = code_only(WEB / 'src/constants/business/scm/purchase-const.ts')
    api_files = (
        'src/api/business/scm/purchase-order-api.ts',
        'src/api/business/scm/purchase-demand-api.ts',
        'src/api/business/scm/purchase-receipt-api.ts',
        'src/api/business/scm/warehouse-api.ts',
    )
    # Comments stripped: the Provenance header names the pruned `calculateStock` and
    # the old `startTime/endTime` shape, which is documentation, not wiring.
    api_text = '\n'.join(code_only(WEB / rel) for rel in api_files)
    api_paths = re.findall(r"'(/scm/[a-z0-9/_-]+)'", api_text)
    list_pages = [
        'purchase-order-list.vue', 'purchase-receipt-list.vue', 'purchase-demand-list.vue',
        'purchase-log-list.vue', 'warehouse-list.vue',
    ]
    a['J1_no_resize_plumbing'] = not any(
        re.search(r'resizable|resizeColumn|handleResizeColumn', text) for text in view_code.values())
    a['J2_six_state_string_enum'] = (
        re.search(r'SCM_PURCHASE_STATUS_ENUM[\s\S]*?\};', constants) is not None
        and re.findall(r"value: '([A-Z_]+)'", re.search(r'SCM_PURCHASE_STATUS_ENUM[\s\S]*?\};', constants).group(0))
        == ['DRAFT', 'SUBMITTED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'SHORT_CLOSED', 'CANCELLED'])
    a['J3_pruned_enums_absent'] = not re.search(
        r'RECEIVE_FLAG_ENUM|SUPPLIER_STATUS_ENUM|INQUIRY_STATUS_ENUM|PURCHASE_ITEM_STATUS_ENUM', constants)
    a['J4_api_prefix'] = bool(api_paths) and all(p.startswith('/scm/purchase/') or p.startswith('/scm/warehouse/') for p in api_paths)
    a['J5_table_ids_wired'] = all(
        'SCM_PURCHASE_TABLE_ID' in view_code[PURCHASE_VIEWS / page]
        and 'TableOperator' in view_code[PURCHASE_VIEWS / page] for page in list_pages)
    frontend_perms = set(re.findall(r"'(scm:[a-z:._-]+)'", '\n'.join(view_code.values())))
    backend_perms = set(re.findall(r"'(scm:[a-z:._-]+)'", sql))
    a['J6_perms_declared'] = frontend_perms <= backend_perms and len(frontend_perms) >= 15
    a['J7_no_actual_amount'] = not re.search(r'actualAmount', '\n'.join(view_code.values()))
    a['J8_no_inventory_wiring'] = not re.search(r'inventory|stock', api_text, re.I)
    # A21/Q6a lives in the type + the generate modal, not in the thin API wrapper
    # (the wrapper just forwards the `DemandGenerate` payload).
    types_code = code_only(PURCHASE_VIEWS / 'purchase-types.ts')
    generate_modal = code_only(PURCHASE_VIEWS / 'components/purchase-demand-generate-modal.vue')
    frontend_all = '\n'.join(view_code.values()) + '\n' + api_text + '\n' + types_code
    a['J9_half_open_window'] = (
        re.search(r'interface DemandGenerate\s*\{[^}]*startAt:\s*string;[^}]*endAt:\s*string;', types_code) is not None
        and 'startAt' in generate_modal and 'endAt' in generate_modal
        and not re.search(r'startTime|endTime', frontend_all))
    a['J10_idempotency_helper'] = 'Idempotency-Key' in api_text and 'purchaseCommand' in api_text
    a['J11_unit_mismatch_guard'] = (
        'unitMismatch' in (PURCHASE_VIEWS / 'purchase-form-model.ts').read_text(encoding='utf-8')
        and 'demandUnit' in (PURCHASE_VIEWS / 'purchase-form-model.ts').read_text(encoding='utf-8'))

    # K - the frozen deliverable inventory.
    backend_files = [p for p in (SCM / 'purchase').rglob('*.java')]
    inventory = [
        'src/constants/business/scm/purchase-const.ts',
        'src/api/business/scm/purchase-order-api.ts',
        'src/api/business/scm/purchase-demand-api.ts',
        'src/api/business/scm/purchase-receipt-api.ts',
        'src/api/business/scm/warehouse-api.ts',
        'src/views/business/scm/purchase/purchase-types.ts',
        'src/views/business/scm/purchase/purchase-errors.ts',
        'src/views/business/scm/purchase/purchase-form-model.ts',
        'src/views/business/scm/purchase/purchase-order-list.vue',
        'src/views/business/scm/purchase/purchase-receipt-list.vue',
        'src/views/business/scm/purchase/purchase-demand-list.vue',
        'src/views/business/scm/purchase/purchase-log-list.vue',
        'src/views/business/scm/purchase/warehouse-list.vue',
        'src/views/business/scm/purchase/components/purchase-order-form-drawer.vue',
        'src/views/business/scm/purchase/components/purchase-order-detail-drawer.vue',
        'src/views/business/scm/purchase/components/purchase-order-item-editable-table.vue',
        'src/views/business/scm/purchase/components/purchase-receipt-form-drawer.vue',
        'src/views/business/scm/purchase/components/purchase-receipt-confirm-modal.vue',
        'src/views/business/scm/purchase/components/purchase-demand-generate-modal.vue',
        'test/w5-purchase-contract.test.mjs',
        'e2e/scm-purchase.spec.ts',
    ]
    contract_test = (WEB / 'test/w5-purchase-contract.test.mjs').read_text(encoding='utf-8')
    e2e_test = (WEB / 'e2e/scm-purchase.spec.ts').read_text(encoding='utf-8')
    a['K_deliverable_inventory'] = (
        len(inventory) == 21
        and all((WEB / rel).is_file() for rel in inventory)
        and len(backend_files) >= 45
        and len(re.findall(r'^test\(', contract_test, re.M)) == 24
        and len(re.findall(r"^test\('", e2e_test, re.M)) == 9
        and (ROOT / 'tools/w5_e2e_accounts.py').is_file())

    result = {
        'assertions': a,
        'pass': all(a.values()),
        'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'changed_path_count': len(changes),
    }
    out = ROOT / '.runtime'
    out.mkdir(exist_ok=True)
    (out / 'w5-legacy-recheck.json').write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))
    return 0 if result['pass'] else 1


if __name__ == '__main__':
    sys.exit(main())
