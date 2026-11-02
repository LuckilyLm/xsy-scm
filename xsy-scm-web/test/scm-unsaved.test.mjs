import test from 'node:test';
import assert from 'node:assert/strict';
import {formChanged, formSnapshot} from '../src/views/business/scm/common/scm-unsaved.ts';

test('未加载完成时（没有基线）不拦截关闭', () => {
  assert.equal(formChanged({name: '新客户'}, undefined), false);
});

test('表单内容变化才视为未保存修改', () => {
  const baseline = formSnapshot({name: '客户甲', creditLimit: '0.0000'});
  assert.equal(formChanged({name: '客户甲', creditLimit: '0.0000'}, baseline), false);
  assert.equal(formChanged({name: '客户乙', creditLimit: '0.0000'}, baseline), true);
  assert.equal(formChanged({name: '客户甲', creditLimit: '100.0000'}, baseline), true);
});

test('空基线字符串与 undefined 都表示没有可比较的快照', () => {
  assert.equal(formChanged({}, ''), false);
  assert.equal(formChanged({}, undefined), false);
});
