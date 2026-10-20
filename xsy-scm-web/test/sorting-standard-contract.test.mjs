import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8');
test('standard batch UI consumes server eligibility and submits identity and versions only', () => {
  const modal = read('../src/views/business/scm/sorting/components/sorting-standard-modal.vue');
  assert.match(modal, /disabled: !row\.eligible/);
  assert.match(modal, /id: item\.id, version: item\.version/);
  assert.doesNotMatch(modal, /productTypeSnapshot|sortedQuantity:|result:\s*['"]NORMAL/);
  assert.match(modal, /不会自动完成任务/);
  assert.match(modal, /刷新预览/);
  const api = read('../src/api/business/scm/sorting-api.ts');
  assert.match(api, /command<SortingStandardResult>/);
  const drawer = read('../src/views/business/scm/sorting/components/sorting-task-detail-drawer.vue');
  assert.match(drawer, /!canEditItems \|\| busy \|\| dirtyCount > 0/);
});
