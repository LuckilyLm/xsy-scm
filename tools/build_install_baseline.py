"""检查24份最终模块SQL的身份与清单；只用标准库，不拼接或重放历史迁移。"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

from migration_checksum_guard import MIGRATION_DIR, ROOT, FILENAME_RE, version_key

MANIFEST_PATH = ROOT / 'tools/install_baseline_manifest.json'
BASELINE_VERSION = '1000.24'


def check_snapshot(directory: Path = MIGRATION_DIR, manifest_path: Path = MANIFEST_PATH) -> dict:
    """检查已批准模块快照；目录和清单必须存在，差异抛ValueError，不连接数据库。"""
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    if manifest.get('format') != 'postgresql-module-snapshot-v1' or manifest.get('version') != BASELINE_VERSION:
        raise ValueError('模块初始化清单格式或版本不符')
    entries = manifest.get('modules', [])
    if len(entries) != 24 or len({e['file'] for e in entries}) != 24:
        raise ValueError('模块初始化必须完整包含24份唯一文件')
    tables = []
    for number, entry in enumerate(entries, 1):
        filename = entry['file']
        if filename != f"V1000.{number:02d}__{entry['module']}.sql" or entry['version'] != f'1000.{number}':
            raise ValueError('模块编号或文件名不一致')
        sql = (directory / filename).read_text(encoding='utf-8')
        if hashlib.sha256(sql.encode('utf-8')).hexdigest() != entry['sha256']:
            raise ValueError('模块SQL内容与已发布清单不符：' + filename)
        declared = sorted(re.findall(r'^CREATE TABLE xsy_v2\.([a-z0-9_]+) \(', sql, re.M))
        if declared != entry['tables']:
            raise ValueError('模块表归属与清单不符：' + filename)
        tables.extend(declared)
    if len(tables) != 195 or len(set(tables)) != 195:
        raise ValueError('195张表有遗漏或重复归属')
    if manifest.get('columns') != 2685:
        raise ValueError('最终字段清单不完整')
    for name, key in [('schema_baseline_catalog.json', 'catalog_sha256'),
                      ('legacy_migration_manifest.json', 'legacy_manifest_sha256')]:
        content = (ROOT / 'tools' / name).read_text(encoding='utf-8')
        if hashlib.sha256(content.encode('utf-8')).hexdigest() != manifest.get(key):
            raise ValueError('旧库切换的元数据与模块快照清单不符：' + name)
    for path in directory.glob('*.sql'):
        if path.name in {entry['file'] for entry in entries}:
            continue
        match = FILENAME_RE.fullmatch(path.name)
        if not match or match['prefix'] != 'V' or version_key(match['version']) <= version_key(BASELINE_VERSION):
            raise ValueError('活动目录混入旧SQL或未知脚本：' + path.name)
    if directory.resolve() == MIGRATION_DIR.resolve():
        if list((directory.parent.parent / 'migration-history').glob('*.sql')):
            raise ValueError('历史SQL不能继续保留在正式资源目录')
        if list(directory.parent.glob('*.sql')):
            raise ValueError('旧活动目录不能继续保留退役SQL')
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['check'])
    parser.add_argument('--migration-dir', type=Path, default=MIGRATION_DIR)
    parser.add_argument('--manifest', type=Path, default=MANIFEST_PATH)
    args = parser.parse_args()
    try:
        manifest = check_snapshot(args.migration_dir, args.manifest)
        print(f"PASS: {len(manifest['modules'])}份模块SQL，195张最终表，无历史脚本依赖")
        return 0
    except (ValueError, OSError, KeyError) as exc:
        print('FAIL: ' + str(exc), file=sys.stderr)
        return 1


if __name__ == '__main__':
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
        sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    sys.exit(main())
