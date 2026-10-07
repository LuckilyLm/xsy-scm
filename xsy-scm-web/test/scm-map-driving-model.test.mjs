/**
 * 配送道路算路的切段与汇总契约。
 *
 * 高德驾车规划单次最多 16 个途经点，超过上限必须切段请求；段与段共享端点，
 * 距离与耗时按段累加。切段错了不会报错：只会少算一段路程或让折线断开，
 * 页面上仍是一条「有路线」的样子。因此这里用替身代替真实服务，钉住切段、
 * 汇总与失败回退，避免这类错误只能靠人工比对地图发现。
 *
 * 只测纯逻辑与替身交互：真实 Key、域名白名单与配额属于部署侧，不在单测范围。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {planRoadRoute, splitDrivingBatches} from '../src/components/business/scm/map/amap-driving-provider.ts';

const point = (index) => [114 + index * 0.01, 22.5 + index * 0.01];

let calls = [];
let failAt = null;
let failStatus = 'error';

/** 每次请求固定返回 1 km / 60 秒与一条三点折线，便于逐段核对汇总值；形状照 JSAPI 2.0 的 routes[]。 */
class StubDriving {
  search(origin, destination, options, callback) {
    calls.push({origin, destination, waypoints: options.waypoints ?? []});
    if (failAt !== null && calls.length === failAt) {
      callback(failStatus, {info: failStatus.toUpperCase()});
      return;
    }
    callback('complete', {
      routes: [
        {
          distance: 1000,
          time: 60,
          steps: [{path: [origin, [origin[0] + 0.001, origin[1]], destination]}],
        },
      ],
    });
  }
}

const sdk = {Driving: StubDriving};
const positions = (count) => Array.from({length: count}, (unused, index) => point(index));

test.beforeEach(() => {
  calls = [];
  failAt = null;
  failStatus = 'error';
});

test('途经点超限时按共享端点切段', () => {
  const batches = splitDrivingBatches(positions(41));
  assert.deepEqual(batches.map((batch) => batch.length), [18, 18, 7]);
  // 上一段终点就是下一段起点，各段折线因此首尾相接。
  assert.deepEqual(batches[1][0], batches[0][17]);
  assert.deepEqual(batches[2][0], batches[1][17]);
});

test('不超过上限只请求一次，途经点顺序与输入一致', async () => {
  const input = positions(18);
  const result = await planRoadRoute(sdk, input);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].waypoints.length, 16);
  assert.deepEqual(calls[0].origin, input[0]);
  assert.deepEqual(calls[0].destination, input[17]);
  assert.deepEqual(calls[0].waypoints, input.slice(1, -1));
  assert.equal(result.segmentCount, 1);
  assert.equal(result.provider, 'AMAP');
  assert.equal(result.fallback, false);
});

test('分段请求的距离与耗时按段累计，折线按序合并', async () => {
  const result = await planRoadRoute(sdk, positions(41));
  assert.equal(calls.length, 3);
  assert.equal(result.segmentCount, 3);
  assert.equal(result.distanceMeters, 3000);
  assert.equal(result.durationSeconds, 180);
  assert.equal(result.paths.length, 3);
  assert.deepEqual(result.paths[1][0], result.paths[0][2]);
});

test('任一段请求失败即整体回退，不返回部分道路折线', async () => {
  failAt = 2;
  const result = await planRoadRoute(sdk, positions(41));
  assert.equal(calls.length, 2);
  assert.equal(result.fallback, true);
  assert.deepEqual(result.paths, []);
  assert.equal(result.distanceMeters, 0);
  assert.equal(result.durationSeconds, 0);
});

test('无结果同样回退，且不抛出异常', async () => {
  failStatus = 'no_data';
  failAt = 1;
  const result = await planRoadRoute(sdk, positions(5));
  assert.equal(result.fallback, true);
  assert.deepEqual(result.paths, []);
});

test('少于两个点没有可算路段，直接回退', async () => {
  const result = await planRoadRoute(sdk, [point(0)]);
  assert.equal(result.fallback, true);
  assert.equal(calls.length, 0);
});
