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
