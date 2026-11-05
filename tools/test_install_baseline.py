"""模块结构清单、checksum与旧库切换前提的回归源码；不依赖数据库即可执行。"""
import contextlib
import copy
import io
import json
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch

import build_install_baseline as baseline
import cutover_schema_baseline as cutover
import migration_checksum_guard as guard
from schema_catalog import differences, fingerprints


class InstallBaselineTest(unittest.TestCase):
    def scratch(self):
        parent = baseline.ROOT / '.codex-tmp'
        parent.mkdir(exist_ok=True)
        return Path(tempfile.mkdtemp(prefix='module-baseline-', dir=parent))

    def test_all_modules_have_unique_tables_and_frozen_checksums(self):
        manifest = baseline.check_snapshot()
        self.assertEqual(24, len(manifest['modules']))
        self.assertEqual(2685, manifest['columns'])
        current = guard.scan_migrations(guard.MIGRATION_DIR)
        result = guard.compare(guard.load_snapshot(guard.SNAPSHOT_PATH), current)
        self.assertFalse(result['drift'])
        self.assertFalse(result['vanished'])
        self.assertFalse(result['pending'])

    def test_one_module_tampering_and_missing_module_are_rejected(self):
        root = self.scratch()
        for source in guard.MIGRATION_DIR.glob('*.sql'):
            shutil.copyfile(source, root / source.name)
        baseline.check_snapshot(root)
        target = next(root.glob('V*__inventory.sql'))
        original = target.read_text(encoding='utf-8')
        target.write_text(original + '\nDROP TABLE xsy_v2.xsy_inventory_balance;', encoding='utf-8')
        with self.assertRaises(ValueError):
            baseline.check_snapshot(root)
        target.write_text(original, encoding='utf-8')
        target.rename(root / 'retired.txt')
        with self.assertRaises(OSError):
            baseline.check_snapshot(root)

    def test_guard_matches_flyway_version_normalization_and_rejects_duplicates(self):
        root = self.scratch()
        (root/'V1000.01__a.sql').write_text('SELECT 1;', encoding='utf-8')
        (root/'B1000.1__b.sql').write_text('SELECT 2;', encoding='utf-8')
        self.assertEqual({'1000.1', 'B1000.1'}, set(guard.scan_migrations(root)))
        (root/'V1000.1__duplicate.sql').write_text('SELECT 1;', encoding='utf-8')
        with self.assertRaises(ValueError):
            guard.scan_migrations(root)

    def legacy(self):
        manifest = json.loads((baseline.ROOT/'tools/legacy_migration_manifest.json').read_text(encoding='utf-8'))
        rows = [{'version': str(i), 'type': 'SQL', 'description': '',
                 'script': manifest['migrations'][str(i)]['file'],
                 'checksum': manifest['migrations'][str(i)]['checksum'], 'success': True}
                for i in range(1, 244)]
        return rows, manifest

    def test_legacy_history_requires_complete_known_successful_chain(self):
        rows, manifest = self.legacy()
        self.assertEqual('legacy', cutover.history_state(rows, manifest))
        rows244 = rows + [{'version': '244', 'type': 'SQL', 'description': '',
            'script': manifest['migrations']['244']['file'],
            'checksum': manifest['migrations']['244']['checksum'], 'success': True}]
        self.assertEqual('legacy', cutover.history_state(rows244, manifest))
        for field, value in [('checksum', 0), ('script', 'changed.sql'), ('success', False), ('type','BASELINE')]:
            changed = copy.deepcopy(rows)
            changed[189][field] = value
            with self.assertRaises(ValueError):
                cutover.history_state(changed, manifest)
        with self.assertRaises(ValueError):
            cutover.history_state(rows[:-1], manifest)
        with self.assertRaises(ValueError):
            cutover.history_state(rows[1:], manifest)

    def test_new_module_history_and_cutover_marker_are_recognized(self):
        _, legacy = self.legacy()
        snapshot = guard.load_snapshot(guard.SNAPSHOT_PATH)['migrations']
        rows = [{'version': v, 'type': 'SQL', 'description': '', 'script': entry['file'],
                 'checksum': entry['checksum'], 'success': True} for v, entry in snapshot.items()
                if guard.version_key(v) <= guard.version_key(cutover.VERSION)]
        self.assertEqual('modules', cutover.history_state(rows, legacy))
        displayed = copy.deepcopy(rows)
        for index, row in enumerate(displayed, 1):
            row['version'] = f'1000.{index:02d}'
        self.assertEqual('modules', cutover.history_state(displayed, legacy))
        marker = [{'version':cutover.VERSION, 'type':'BASELINE', 'description':cutover.DESCRIPTION,
                   'script':'<< Flyway Baseline >>','checksum':None,'success':True}]
        self.assertEqual('cutover', cutover.history_state(marker, legacy))
        marker[0]['description'] = 'manual baseline'
        with self.assertRaises(ValueError):
            cutover.history_state(marker, legacy)

    def test_backend_rules_upgrade_requires_known_history_and_preserves_other_catalog_objects(self):
        _, legacy = self.legacy()
        snapshot = guard.load_snapshot(guard.SNAPSHOT_PATH)['migrations']
        rows = [{'version': v, 'type': 'SQL', 'description': '', 'script': e['file'],
                 'checksum':e['checksum'], 'success':True} for v,e in snapshot.items()
                if guard.version_key(v) <= guard.version_key(cutover.VERSION)]
        entry = guard.scan_migrations(guard.MIGRATION_DIR)['1001']
        upgrade = {'version':'1001','type':'SQL','description':'','script':entry['file'],
                   'checksum':entry['checksum'],'success':True}
        self.assertEqual('modules_upgraded', cutover.history_state(rows+[upgrade], legacy))
        wrong = copy.deepcopy(upgrade)
        wrong['checksum'] += 1
        with self.assertRaises(ValueError):
            cutover.history_state(rows+[wrong], legacy)
        catalog = json.loads((baseline.ROOT/'tools/schema_baseline_catalog.json').read_text(encoding='utf-8'))['objects']
        target = cutover.backend_rule_catalog(catalog)
        self.assertEqual(740, len(catalog)-len(target))
        for key,value in target.items():
            self.assertEqual(catalog[key],value)
        self.assertTrue(any(k.startswith('index:') for k in target))
        self.assertTrue(any(k.startswith('column:') for k in target))

    def test_job_class_upgrade_requires_complete_known_incremental_history(self):
        _, legacy = self.legacy()
        snapshot = guard.scan_migrations(guard.MIGRATION_DIR)
        rows = [{'version': v, 'type': 'SQL', 'description': '', 'script': e['file'],
                 'checksum': e['checksum'], 'success': True} for v, e in snapshot.items()
                if guard.version_key(v) <= guard.version_key(cutover.VERSION)]
        increments = [{'version': v, 'type': 'SQL', 'description': '', 'script': snapshot[v]['file'],
                       'checksum': snapshot[v]['checksum'], 'success': True} for v in ('1001', '1002')]
        self.assertEqual('modules_upgraded', cutover.history_state(rows + increments, legacy))
        marker = [{'version': cutover.VERSION, 'type': 'BASELINE', 'description': cutover.DESCRIPTION,
                   'script': '<< Flyway Baseline >>', 'checksum': None, 'success': True}]
        self.assertEqual('cutover_upgraded', cutover.history_state(marker + increments, legacy))
        with self.assertRaises(ValueError):
            cutover.history_state(rows + increments[1:], legacy)
        for field, value in [('checksum', increments[1]['checksum'] + 1),
                             ('script', 'changed.sql'), ('success', False), ('type', 'BASELINE')]:
            changed = copy.deepcopy(increments)
            changed[1][field] = value
            with self.assertRaises(ValueError):
                cutover.history_state(rows + changed, legacy)

    def test_catalog_drift_is_explicit_and_order_independent(self):
        a = fingerprints({'table:a': {'not_null':True,'type':'bigint'}})
        self.assertEqual(a, fingerprints({'table:a': {'type':'bigint','not_null':True}}))
        b = fingerprints({'table:a': {'type':'integer'}, 'table:b':{}})
        self.assertEqual({'missing':[], 'extra':['table:b'], 'changed':['table:a']}, differences(a,b))
        self.assertEqual(['table:a'], differences(a,{})['missing'])

    def test_apply_without_explicit_target_and_backup_fails_before_connection(self):
        with patch('sys.argv', ['cutover', '--apply']), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, cutover.main())

    def test_text_array_coercion_matches_without_hiding_constraint_changes(self):
        old = "CHECK (((status)::text = ANY ((ARRAY['A'::character varying, 'B'::character varying])::text[])))"
        new = "CHECK (((status)::text = ANY (ARRAY[('A'::character varying)::text, ('B'::character varying)::text])))"
        def digest(expression):
            return fingerprints({'constraint:t.status': {'definition':expression}})
        self.assertEqual(digest(old), digest(new))
        self.assertNotEqual(digest(old), digest(new.replace("'B'", "'C'")))
        self.assertNotEqual(digest(old), digest(old.replace('character varying', 'character varying(1)')))
        literal = "CHECK (note = 'ARRAY[''A''::character varying]')"
        self.assertNotEqual(digest(literal), digest(literal.replace("''A''", "''B''")))


if __name__ == '__main__':
    unittest.main()
