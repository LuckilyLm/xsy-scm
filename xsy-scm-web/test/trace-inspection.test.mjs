import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import {
  SCM_TRACE_INSPECTION_STATUS_ENUM,
  SCM_TRACE_INSPECTION_CONCLUSION_ENUM,
  inspectionPublicResult,
  inspectionStatusLabel,
  inspectionConclusionLabel,
} from '../src/constants/business/scm/trace-const.ts';
import {
  TRACE_INSPECTION_STATUS_ENUM,
  TRACE_INSPECTION_CONCLUSION_ENUM,
  inspectionPublicResult as miniPublicResult,
} from '../../xsy-scm-miniprogram/src/constants/trace-const.js';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
const javaRoot = '../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/trace/constant/';

test('inspection enum values and Chinese labels agree across Java, admin, mini program and database', () => {
  for (const [name, web, mini, column] of [
    ['ScmTraceInspectionStatusEnum', SCM_TRACE_INSPECTION_STATUS_ENUM, TRACE_INSPECTION_STATUS_ENUM, 'status'],
    ['ScmTraceInspectionConclusionEnum', SCM_TRACE_INSPECTION_CONCLUSION_ENUM, TRACE_INSPECTION_CONCLUSION_ENUM, 'conclusion'],
  ]) {
    const source = read(javaRoot + name + '.java');
    const entries = [...source.matchAll(/\b([A-Z_]+)\("([^"]+)"\)/g)].map(([, value, desc]) => [value, {value, desc}]);
    assert.deepEqual(web, Object.fromEntries(entries));
    assert.deepEqual(web, mini);
    const sql = read('../../xsy-scm-server/sa-admin/src/main/resources/db/migration/V176__scm_trace_inspection_report.sql');
    const allowed = sql.match(new RegExp(column + " IN \\(([^)]+)\\)"))?.[1];
    assert.ok(allowed, column + ' missing CHECK');
    assert.deepEqual([...allowed.matchAll(/'([^']+)'/g)].map((item) => item[1]).sort(), Object.keys(web).sort());
  }
  const registry = read('../src/constants/index.ts');
  assert.match(registry, /import scmTrace from '.\/business\/scm\/trace-const'/);
  assert.match(registry, /\.\.\.scmTrace/);
});

test('only a published unexpired qualified report receives the qualified badge on both clients', () => {
  for (const status of ['DRAFT', 'PUBLISHED', 'WITHDRAWN', 'UNKNOWN']) {
    for (const conclusion of ['QUALIFIED', 'UNQUALIFIED', 'UNDETERMINED', 'UNKNOWN']) {
      for (const expired of [false, true]) {
        const report = {status, conclusion, expired};
        const expected = status === 'PUBLISHED' && conclusion === 'QUALIFIED' && !expired;
        const web = inspectionPublicResult(report);
        assert.equal(web.label === '检测合格', expected);
        assert.equal(web.tone === 'success', expected);
        assert.deepEqual(web, miniPublicResult(report));
      }
    }
  }
  assert.equal(inspectionStatusLabel('UNKNOWN'), '未知状态');
  assert.equal(inspectionConclusionLabel('UNKNOWN'), '未知结论');
});
