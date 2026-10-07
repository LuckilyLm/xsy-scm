export type GeomCrs = 'GCJ02' | 'WGS84';

export interface ScmLocation {
    longitude?: number | string | null;
    latitude?: number | string | null;
    geomCrs?: GeomCrs | null;
}

export interface MapPoint extends ScmLocation {
    label: string;
    description?: string;
}

export const emptyLocation = (): ScmLocation => ({longitude: null, latitude: null, geomCrs: null});

export function isLocated(point: ScmLocation): boolean {
    return (
        point.longitude != null &&
        point.latitude != null &&
        point.longitude !== '' &&
        point.latitude !== '' &&
        Number.isFinite(Number(point.longitude)) &&
        Number.isFinite(Number(point.latitude)) &&
        Math.abs(Number(point.longitude)) <= 180 &&
        Math.abs(Number(point.latitude)) <= 90 &&
        (point.geomCrs === 'GCJ02' || point.geomCrs === 'WGS84')
    );
}

export function locationError(point: ScmLocation): string | undefined {
    if (point.longitude == null && point.latitude == null && point.geomCrs == null) return undefined;
    return isLocated(point) ? undefined : '请完整填写有效经纬度与坐标系，或清空定位';
}

/** 地图坐标对，顺序是 [经度, 纬度]；地图统一使用 GCJ02。 */
export type RoutePosition = [number, number];

export interface RouteDrivingResult {
    distanceMeters: number;
    durationSeconds: number;
    /** 道路折线的坐标序列，直接作为 Polyline 的 path。 */
    paths: RoutePosition[][];
    /** 规划请求段数：途经点超过单次上限时按共享端点切段累计。 */
    segmentCount: number;
    provider: 'AMAP';
    /** 算路失败时为 true，paths 为空，由调用方改画点间直线。 */
    fallback: boolean;
}

/** 路线地图的算路状态：面板据此展示摘要或降级提示。 */
export type RouteMapStatus =
    | { state: 'idle' }
    | { state: 'planning' }
    | { state: 'ready'; result: RouteDrivingResult }
    | { state: 'fallback' };
