/*
 * 大屏地图底图与区划字典的同源契约（地图 M1）
 *
 * 钉的是唯一一个**跨目录、跨语言**才会成立的不变量：省级着色数据按 `adcode`（= `province_code`，
 * 同为 GB/T 2260 六位码）挂到底图要素上。底图少一个码，那个省就**静默不着色** ——
 * 接口不报错、图上看只是「没有数据」，而大屏上「没有数据」和「数据为 0」长得一模一样，
 * 靠肉眼验收发现不了。名称不作为匹配键（港澳简称与官方全称不同源，见最后一个用例）。
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const GEO_JSON = new URL('../public/screen/china-province.json', import.meta.url);
/** 区划字典种子的原文：被测的就是上线的那段 SQL，测试里不复制第二份省名清单。 */
const V40 = new URL(
  '../../xsy-scm-server/sa-admin/src/main/resources/db/migration/V40__scm_geo_region_and_master_location.sql',
  import.meta.url,
);

function geoFeatures() {
  const collection = JSON.parse(readFileSync(GEO_JSON, 'utf8'));
  assert.equal(collection.type, 'FeatureCollection');
  return collection.features;
}

/** V40 Step 2 的省级行（level 1）。 */
function seededProvinces() {
  return [...readFileSync(V40, 'utf8').matchAll(/^\((\d{6}), NULL, '([^']+)', [^,]+, [^,]+, 1\),$/gm)].map(
    ([, code, name]) => ({ code, name }),
  );
}

test('底图存档结构可用：每个省都有 adcode 与 name，且 adcode 不重复', () => {
  const named = geoFeatures().filter((feature) => feature.properties?.name);

  // 35 个要素里有 1 条无名的南海诸岛要素：它必须留在图里（边界完整性），但不参与匹配
  assert.equal(named.length, 34);
  for (const feature of named) {
    assert.match(String(feature.properties.adcode), /^\d{6}$/);
    assert.ok(feature.geometry, `${feature.properties.name} 缺几何`);
  }
  assert.equal(new Set(named.map((feature) => feature.properties.adcode)).size, named.length);
});

test('每个省级区划码在底图里都有对应要素（着色按码挂载）', () => {
  const adcodes = new Set(geoFeatures().map((feature) => String(feature.properties?.adcode)));
  const provinces = seededProvinces();

  assert.equal(provinces.length, 34, 'V40 的省级种子行数变了，需同步复核底图');
  assert.deepEqual(
    provinces.filter((province) => !adcodes.has(province.code)),
    [],
    '这些省在底图里没有对应 adcode，着色层会静默丢省',
  );
});

/**
 * 名称差异必须被「按码匹配」消化掉。
 *
 * 字典沿用 `area-cascader` 的简称（香港 / 澳门），官方边界数据用全称
 * （香港特别行政区 / 澳门特别行政区）。这两行是**故意**留在这里的告警：
 * 一旦有人把匹配键从 adcode 改回名称，港澳就会从图上消失且没有任何报错。
 */
test('港澳名称与底图不同源，因此匹配键只能是 adcode', () => {
  const nameByAdcode = new Map(
    geoFeatures().map((feature) => [String(feature.properties?.adcode), feature.properties?.name]),
  );
  const drift = seededProvinces()
    .filter((province) => nameByAdcode.get(province.code) !== province.name)
    .map((province) => `${province.code}: 字典「${province.name}」 vs 底图「${nameByAdcode.get(province.code)}」`);

  assert.deepEqual(drift, [
    '810000: 字典「香港」 vs 底图「香港特别行政区」',
    '820000: 字典「澳门」 vs 底图「澳门特别行政区」',
  ]);
});
