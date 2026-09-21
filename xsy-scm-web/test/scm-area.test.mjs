/*
 * 省 / 市 / 区路径 ↔ 六列快照转换单测（地图 M0）
 *
 * 覆盖两条会静默写错数据的规则：
 * 1. 名称快照必须来自**同一次选择**的节点，且清空选择要把六列整体归 null，不能只清编码；
 * 2. 回填给 `a-cascader` 的必须是一条连续路径——缺市就停在省，不能拼出「省 + 空 + 区」。
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { areaColumnsOf, areaNodesOf } from '../src/views/business/scm/common/scm-area.ts';

const PATH = [
  { value: 330000, label: '浙江省' },
  { value: 330100, label: '杭州市' },
  { value: 330106, label: '西湖区' },
];

test('完整三级选择拆成六列，编码与名称取自同一节点', () => {
  assert.deepEqual(areaColumnsOf(PATH), {
    provinceCode: 330000,
    provinceName: '浙江省',
    cityCode: 330100,
    cityName: '杭州市',
    districtCode: 330106,
    districtName: '西湖区',
  });
});

test('只选到省时市 / 区整组留空，不拿上一级的名称冒充', () => {
  const columns = areaColumnsOf([PATH[0]]);
  assert.equal(columns.provinceCode, 330000);
  assert.equal(columns.cityCode, null);
  assert.equal(columns.cityName, null);
  assert.equal(columns.districtCode, null);
  assert.equal(columns.districtName, null);
});

test('清空选择把六列显式置 null，不残留上一次的层级', () => {
  for (const columns of [areaColumnsOf([]), areaColumnsOf(null)]) {
    assert.deepEqual(Object.values(columns), [null, null, null, null, null, null]);
    assert.equal(Object.keys(columns).length, 6);
  }
});

test('回填只还原连续路径：省 + 空市 + 区停在省', () => {
  assert.deepEqual(areaNodesOf(areaColumnsOf(PATH)), PATH);
  assert.deepEqual(areaNodesOf({ provinceCode: 330000, provinceName: '浙江省', districtCode: 330106, districtName: '西湖区' }), [
    PATH[0],
  ]);
  assert.deepEqual(areaNodesOf(null), []);
  // 名称快照缺失时该级不可信，从该级截断（大屏按 city_code 聚合，与是否回填无关）。
  assert.deepEqual(areaNodesOf({ provinceCode: 330000, provinceName: '浙江省', cityCode: 330100 }), [PATH[0]]);
});
