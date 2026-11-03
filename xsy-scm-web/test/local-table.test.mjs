import assert from 'node:assert/strict';
import test from 'node:test';
import {effectScope, nextTick, ref} from 'vue';
import {useLocalPagination} from '../src/components/support/local-table/use-local-pagination.ts';

test('完整数组分页保留源数据，切换条数与筛选回首页，删除末页后页码仍有效', async () => {
  const scope = effectScope();
  const original = Array.from({length: 65}, (_, id) => ({id}));
  const rows = ref(original);
  const pagination = scope.run(() => useLocalPagination(rows, 20));
  try {
    pagination.change(3, 20);
    assert.equal(pagination.current.value, 3);
    assert.equal(pagination.total.value, 65);
    assert.deepEqual(
      original.map((row) => row.id),
      Array.from({length: 65}, (_, id) => id)
    );

    rows.value.splice(25);
    await nextTick();
    assert.equal(pagination.current.value, 2, '末页删除后回到最后一个有效页');

    pagination.change(2, 10);
    assert.equal(pagination.current.value, 1, '改变条数不能沿用旧页码');
    assert.equal(pagination.pageSize.value, 10);

    pagination.change(3, 10);
    rows.value = [{id: 99}];
    await nextTick();
    assert.equal(pagination.current.value, 1, '替换筛选结果必须回到首页');
    assert.equal(pagination.total.value, 1);

    rows.value = [];
    await nextTick();
    pagination.change(9, 10);
    assert.equal(pagination.current.value, 1);
    assert.equal(pagination.total.value, 0);
  } finally {
    scope.stop();
  }
});
