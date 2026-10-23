import type {RouteDrivingResult, RoutePosition} from './types';

/** 单次请求的途经点上限；加上起点与终点，一段最多 18 个点。 */
const MAX_WAYPOINTS = 16;
const BATCH_POINT_LIMIT = MAX_WAYPOINTS + 2;
const SEARCH_TIMEOUT_MS = 12000;

interface DrivingStep {
  path?: RoutePosition[];
}

interface DrivingRoute {
  distance?: number;
  time?: number;
  steps?: DrivingStep[];
}

interface DrivingSearchResult {
  routes?: DrivingRoute[];
}

interface DrivingInstance {
  search(
    origin: RoutePosition,
    destination: RoutePosition,
    options: {waypoints?: RoutePosition[]},
    callback: (status: string, result: DrivingSearchResult) => void
  ): void;
}

/** 结构类型：只需 SDK 带 Driving 构造器，测试可以注入替身。 */
export interface DrivingSdk {
  Driving: new (options: object) => DrivingInstance;
}

interface DrivingBatch {
  distanceMeters: number;
  durationSeconds: number;
  path: RoutePosition[];
}

/** 超过单次途经点上限时切段；相邻两段共享端点，各段折线因此首尾相接。 */
export function splitDrivingBatches(positions: RoutePosition[]): RoutePosition[][] {
  const batches: RoutePosition[][] = [];
  for (let start = 0; start < positions.length - 1; start += BATCH_POINT_LIMIT - 1) {
    batches.push(positions.slice(start, start + BATCH_POINT_LIMIT));
  }
  return batches;
}

/** 按 JSAPI 2.0 实际返回的 `routes[]` 读取：距离米、耗时秒，折线在各 step 的 `path` 上。 */
function searchBatch(sdk: DrivingSdk, points: RoutePosition[]): Promise<DrivingBatch> {
  const waypoints = points.slice(1, -1);
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('地图服务请求超时，请重试。')), SEARCH_TIMEOUT_MS);
    new sdk.Driving({}).search(points[0], points[points.length - 1], waypoints.length ? {waypoints} : {}, (status, result) => {
      clearTimeout(timer);
      const route = status === 'complete' ? result.routes?.[0] : undefined;
      if (!route) {
        reject(new Error('道路路线规划失败。'));
        return;
      }
      resolve({
        distanceMeters: route.distance ?? 0,
        durationSeconds: route.time ?? 0,
        path: (route.steps ?? []).flatMap((step) => step.path ?? []),
      });
    });
  });
}

/** 按传入顺序算路；任一段失败即整体回退，不返回残缺折线。 */
export async function planRoadRoute(sdk: DrivingSdk, positions: RoutePosition[]): Promise<RouteDrivingResult> {
  const failed: RouteDrivingResult = {
    distanceMeters: 0,
    durationSeconds: 0,
    paths: [],
    segmentCount: 0,
    provider: 'AMAP',
    fallback: true,
  };
  if (positions.length < 2) return failed;
  const batches = splitDrivingBatches(positions);
  const paths: RoutePosition[][] = [];
  let distanceMeters = 0;
  let durationSeconds = 0;
  for (const points of batches) {
    let batch: DrivingBatch;
    try {
      batch = await searchBatch(sdk, points);
    } catch {
      return failed;
    }
    distanceMeters += batch.distanceMeters;
    durationSeconds += batch.durationSeconds;
    paths.push(batch.path);
  }
  return {
    distanceMeters,
    durationSeconds,
    paths,
    segmentCount: batches.length,
    provider: 'AMAP',
    fallback: false,
  };
}
