#!/usr/bin/env python3
"""旧库保数据切换模块基线；默认只读，--apply才在单事务内登记新历史。"""
import argparse
import json
import os
from pathlib import Path
import sys

from schema_catalog import CATALOG_SQL, differences, fingerprints
from migration_checksum_guard import version_key

ROOT = Path(__file__).resolve().parents[1]
VERSION = '1000.24'
DESCRIPTION = 'SCM module baseline V244'

# 旧V243与最终模块快照的唯一默认数据差异；不处理自建模板及历史打印快照。
PRINT_TEMPLATE_PATCH = """
UPDATE xsy_v2.xsy_print_template t
SET model=jsonb_insert(t.model,
    ARRAY['columns',(SELECT (c.column_no-1)::text
      FROM jsonb_array_elements_text(t.model->'columns') WITH ORDINALITY AS c(value,column_no)
      WHERE c.value='actualQuantity' LIMIT 1)], '"orderedQuantity"'::jsonb, false),
    version=version+1, updated_at=CURRENT_TIMESTAMP, updated_by='system'
WHERE t.document_type='CUSTOMER_DELIVERY_NOTE'
  AND t.template_code='CUSTOMER_DELIVERY_NOTE_DEFAULT' AND NOT t.deleted
  AND jsonb_typeof(t.model->'columns')='array'
  AND (t.model->'columns') ? 'actualQuantity'
  AND NOT ((t.model->'columns') ? 'orderedQuantity')
"""


def history_state(rows: list, manifest: dict) -> str:
    """校验完整成功历史；仅接受已知逐V243/244历史或完整模块初始化记录。"""
    if not rows or any(not row['success'] for row in rows):
        raise ValueError('历史为空或有失败记录，不能切换')
    versioned = [row for row in rows if row['version'] is not None]
    # Flyway比较时去前导0，历史展示仍可保留1000.01；不能直接比较原始字符串。
    versions = ['.'.join(str(part) for part in version_key(row['version'])) for row in versioned]
    if any(row['type'] != 'SCHEMA' for row in rows if row['version'] is None):
        raise ValueError('存在未知非版本历史，需先核对')
    upgraded = [row for row in versioned if version_key(row['version']) > version_key(VERSION)]
    if upgraded:
        if [row['version'] for row in upgraded] != ['1001'] or versioned[-len(upgraded):] != upgraded:
            raise ValueError('存在尚未定义结构目标的增量历史')
        base = history_state([row for row in rows if row not in upgraded], manifest)
        if base not in ('modules', 'cutover'):
            raise ValueError('增量历史缺少完整模块初始化或显式切换基线')
        from migration_checksum_guard import scan_migrations, MIGRATION_DIR
        known = scan_migrations(MIGRATION_DIR)
        for row in upgraded:
            entry = known.get(row['version'])
            if not entry or row['type'] != 'SQL' or row['script'] != entry['file'] or row['checksum'] != entry['checksum']:
                raise ValueError('增量脚本或checksum与当前发布文件不一致')
        return base + '_upgraded'
    if (len(versioned) == 1 and versions[0] == VERSION
            and versioned[0]['type'] == 'BASELINE' and versioned[0]['description'] == DESCRIPTION):
        return 'cutover'
    if versions == [f'1000.{i}' for i in range(1, 25)]:
        from migration_checksum_guard import load_snapshot, SNAPSHOT_PATH
        expected = load_snapshot(SNAPSHOT_PATH)['migrations']
        for row, version in zip(versioned, versions):
            entry = expected[version]
            if row['type'] != 'SQL' or row['script'] != entry['file'] or row['checksum'] != entry['checksum']:
                raise ValueError('模块初始化历史与发布文件不一致')
        return 'modules'
    if versions not in ([str(i) for i in range(1, 244)], [str(i) for i in range(1, 245)]):
        raise ValueError('仅支持完整V1～V243或V1～V244；更早、分叉或未知版本须单独升级')
    for row, version in zip(versioned, versions):
        expected = manifest['migrations'].get(version)
        if (not expected or row['type'] != 'SQL' or row['script'] != expected['file']
                or row['checksum'] != expected['checksum']):
            raise ValueError(f"V{row['version']}脚本或checksum与已确认历史不一致")
    return 'legacy'


def backend_rule_catalog(expected: dict) -> dict:
    """仅为已验证V1001历史派生结构参照；其余对象的原始指纹一律保留。"""
    checks = {key for key in expected if key.startswith('constraint:') and (
        key.rsplit('.', 1)[-1].startswith('ck_')
        or key.rsplit('.', 1)[-1].endswith('_check')
        or key in {'constraint:xsy_trace_inspection_report.xsy_trace_inspection_report_check1',
                   'constraint:xsy_trace_inspection_report.xsy_trace_inspection_report_check2'})}
    triggers = {key for key in expected if key.startswith('trigger:')}
    functions = {key for key in expected if key.startswith('function:')}
    if (len(checks), len(triggers), len(functions)) != (675, 34, 31):
        raise ValueError('冻结基线的业务保护清单不一致，不能派生V1001结构目标')
    return {key: value for key, value in expected.items() if key not in checks | triggers | functions}


def cutover(connection, expected_database: str | None, apply: bool) -> dict:
    """在一个事务中核对并切换；连接非空，apply仅由显式命令开启，异常由事务回滚。"""
    from psycopg import sql
    from psycopg.rows import dict_row
    from build_install_baseline import check_snapshot
    check_snapshot()
    legacy = json.loads((ROOT / 'tools/legacy_migration_manifest.json').read_text(encoding='utf-8'))
    expected = json.loads((ROOT / 'tools/schema_baseline_catalog.json').read_text(encoding='utf-8'))['objects']
    with connection.transaction(), connection.cursor(row_factory=dict_row) as cursor:
        cursor.execute('SET TRANSACTION ISOLATION LEVEL SERIALIZABLE' + ('' if apply else ' READ ONLY'))
        cursor.execute("SET LOCAL search_path TO xsy_v2, pg_catalog")
        cursor.execute("SET LOCAL lock_timeout TO '5s'")
        cursor.execute('SELECT current_database() AS name, current_schema() AS schema')
        database = cursor.fetchone()['name']
        if apply and database != expected_database:
            raise ValueError('当前数据库名称与--expected-database不符')
        cursor.execute("SELECT to_regclass('xsy_v2.flyway_schema_history') AS history, "
                       "to_regclass('xsy_v2.flyway_schema_history_legacy') AS legacy")
        relations = cursor.fetchone()
        if not relations['history']:
            raise ValueError('没有旧迁移历史；空库由Flyway直接按模块安装')
        if apply:
            cursor.execute('LOCK TABLE xsy_v2.flyway_schema_history IN ACCESS EXCLUSIVE MODE')
        cursor.execute('SELECT version,type,description,script,checksum,success '
                       'FROM xsy_v2.flyway_schema_history ORDER BY installed_rank')
        state = history_state(cursor.fetchall(), legacy)
        if state == 'legacy' and relations['legacy']:
            raise ValueError('旧历史备份表已存在，拒绝覆盖')
        if state.startswith('cutover') and not relations['legacy']:
            raise ValueError('基线标记缺少保留的原迁移历史，拒绝接管')
        if state.startswith('cutover'):
            cursor.execute('SELECT version,type,description,script,checksum,success '
                           'FROM xsy_v2.flyway_schema_history_legacy ORDER BY installed_rank')
            if history_state(cursor.fetchall(), legacy) != 'legacy':
                raise ValueError('保留的原迁移历史异常')
        if state.endswith('_upgraded'):
            expected = backend_rule_catalog(expected)
        if apply:
            # 短暂阻止业务写入与DDL，防止目录核对和切换之间结构发生变化；超时整笔回滚。
            tables = sorted(key[6:] for key in expected if key.startswith('table:'))
            cursor.execute(sql.SQL('LOCK TABLE {} IN SHARE ROW EXCLUSIVE MODE').format(
                sql.SQL(',').join(sql.Identifier('xsy_v2', name) for name in tables)))
        cursor.execute(CATALOG_SQL)
        actual = fingerprints(cursor.fetchone()['coalesce'])
        drift = differences(expected, actual)
        if any(drift.values()):
            detail = {key: values[:20] for key, values in drift.items() if values}
            raise ValueError('最终结构不一致，未切换：' + json.dumps(detail, ensure_ascii=False))
        result = {'database': database, 'state': state, 'structure': 'matched', 'applied': False}
        if not apply or state != 'legacy':
            return result
        cursor.execute(PRINT_TEMPLATE_PATCH)
        result['builtin_templates_updated'] = cursor.rowcount
        cursor.execute('ALTER TABLE xsy_v2.flyway_schema_history RENAME TO flyway_schema_history_legacy')
        # 复制Flyway列形状，不复制原主键/索引名称，避免与保留历史中的同名对象冲突。
        cursor.execute('CREATE TABLE xsy_v2.flyway_schema_history '
                       '(LIKE xsy_v2.flyway_schema_history_legacy INCLUDING DEFAULTS INCLUDING CONSTRAINTS)')
        cursor.execute('ALTER TABLE xsy_v2.flyway_schema_history ADD CONSTRAINT '
                       'flyway_schema_history_module_pk PRIMARY KEY (installed_rank)')
        cursor.execute('CREATE INDEX flyway_schema_history_module_success_idx '
                       'ON xsy_v2.flyway_schema_history (success)')
        cursor.execute('INSERT INTO xsy_v2.flyway_schema_history '
                       '(installed_rank,version,description,type,script,checksum,installed_by,execution_time,success) '
                       'VALUES (1,%s,%s,%s,%s,NULL,current_user,0,true)',
                       (VERSION, DESCRIPTION, 'BASELINE', '<< Flyway Baseline >>'))
        result['applied'] = True
        return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='显式事务切换；缺省只读检查')
    parser.add_argument('--expected-database', help='写入时必须与实际库名完全相同')
    parser.add_argument('--backup', type=Path, help='写入前已完成的数据库备份文件')
    args = parser.parse_args()
    try:
        if args.apply and (not args.expected_database or not args.backup
                           or not args.backup.is_file() or args.backup.stat().st_size == 0):
            raise ValueError('--apply必须提供准确库名和非空数据库备份文件')
        dsn = os.environ.get('SCM_MIGRATION_DSN')
        if not dsn:
            raise ValueError('请通过SCM_MIGRATION_DSN提供目标PostgreSQL连接；不会打印连接串')
        try:
            import psycopg
        except ImportError as exc:
            raise ValueError('切换工具需要安装tools/requirements-baseline.txt') from exc
        with psycopg.connect(dsn, autocommit=True, connect_timeout=10) as connection:
            result = cutover(connection, args.expected_database, args.apply)
        print(json.dumps(result, ensure_ascii=False))
        return 0
    except ValueError as exc:
        print('FAIL: ' + str(exc), file=sys.stderr)
    except Exception as exc:
        # 驱动错误可能包含连接细节；只输出类型与SQLSTATE，完整原因由维护者在受控环境排查。
        print(f"FAIL: {type(exc).__name__} SQLSTATE={getattr(exc, 'sqlstate', None)}；事务未成功则自动回滚",
              file=sys.stderr)
    return 1


if __name__ == '__main__':
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
        sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    sys.exit(main())
