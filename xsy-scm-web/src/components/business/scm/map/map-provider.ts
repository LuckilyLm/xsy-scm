import type {MapPoint, RouteDrivingResult, RouteMapStatus, RoutePosition, ScmLocation} from './types';
import {isLocated} from './types';
import {planRoadRoute, type DrivingSdk} from './amap-driving-provider';

const coordinate = (value: number) => value.toFixed(8);

interface LngLat {
    getLng(): number;

    getLat(): number;
}

interface MapEvent {
    lnglat: LngLat;
}

interface Overlay {
    on(event: string, handler: (event: MapEvent) => void): void;
}

interface MapInstance {
    add(overlays: Overlay[]): void;

    remove(overlays: Overlay[]): void;

    on(event: string, handler: (event: MapEvent) => void): void;

    setFitView(): void;

    setCenter(position: RoutePosition): void;

    setZoom(zoom: number): void;

    destroy(): void;
}

interface Sdk extends DrivingSdk {
    Map: new (element: HTMLElement, options: object) => MapInstance;
    Marker: new (options: object) => Overlay;
    Polyline: new (options: object) => Overlay;
    Pixel: new (x: number, y: number) => unknown;
    InfoWindow: new (options: object) => { open(map: MapInstance, position: RoutePosition): void };
    Geocoder: new (options: object) => {
        getLocation(
            address: string,
            callback: (status: string, result: {
                info?: string;
                geocodes?: { location: LngLat; formattedAddress: string }[]
            }) => void
        ): void;
    };

    convertFrom(position: RoutePosition, crs: string, callback: (status: string, result: {
        locations?: LngLat[]
    }) => void): void;
}

const globalMap = window as unknown as {
    AMap?: Sdk;
    _AMapSecurityConfig?: { serviceHost?: string; securityJsCode?: string }
};
let loading: Promise<Sdk> | undefined;
export const mapConfigured = () => Boolean(import.meta.env.VITE_AMAP_KEY);

async function load(): Promise<Sdk> {
    if (!mapConfigured()) throw new Error('地图尚未配置，可先手工填写经纬度并编排停靠顺序。');
    if (globalMap.AMap) return globalMap.AMap;
    if (loading) return loading;
    loading = new Promise<Sdk>((resolve, reject) => {
        const serviceHost = import.meta.env.VITE_AMAP_SERVICE_HOST;
        const securityJsCode = import.meta.env.VITE_AMAP_SECURITY_CODE;
        if (serviceHost) globalMap._AMapSecurityConfig = {serviceHost};
        else if (securityJsCode) globalMap._AMapSecurityConfig = {securityJsCode};
        const script = document.createElement('script');
        const timer = window.setTimeout(() => fail(), 15000);

        function fail() {
            window.clearTimeout(timer);
            script.remove();
            loading = undefined;
            reject(new Error('地图加载失败，请检查网络或地图配置后重试。'));
        }

        script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(import.meta.env.VITE_AMAP_KEY)}&plugin=AMap.Geocoder,AMap.Driving`;
        script.onerror = fail;
        script.onload = () => {
            window.clearTimeout(timer);
            if (globalMap.AMap) resolve(globalMap.AMap);
            else fail();
        };
        document.head.appendChild(script);
    });
    return loading;
}

function timeout<T>(work: Promise<T>): Promise<T> {
    return new Promise((resolve, reject) => {
        const timer = window.setTimeout(() => reject(new Error('地图服务请求超时，请重试。')), 12000);
        work.then(
            (value) => {
                clearTimeout(timer);
                resolve(value);
            },
            (error) => {
                clearTimeout(timer);
                reject(error);
            }
        );
    });
}

async function position(sdk: Sdk, point: ScmLocation): Promise<RoutePosition> {
    const raw: RoutePosition = [Number(point.longitude), Number(point.latitude)];
    if (point.geomCrs === 'GCJ02') return raw;
    // Conversion is display-only. Stored WGS84 snapshots are never relabelled as GCJ02.
    return timeout(
        new Promise((resolve, reject) =>
            sdk.convertFrom(raw, 'gps', (status, result) => {
                const p = result.locations?.[0];
                if (status === 'complete' && p) resolve([p.getLng(), p.getLat()]);
                else reject(new Error('WGS84 坐标转换失败，请重试。'));
            })
        )
    );
}

export async function geocode(address: string): Promise<ScmLocation> {
    if (!address.trim()) throw new Error('请先填写详细地址。');
    const sdk = await load();
    return timeout(
        new Promise((resolve, reject) => {
            new sdk.Geocoder({}).getLocation(address, (status, result) => {
                const points = result.geocodes ?? [];
                if (status !== 'complete' || !points.length) return reject(new Error('未找到地址，请补全地址或在地图上选点。'));
                if (points.length > 1) return reject(new Error('地址匹配到多个地点，请补全省市和门牌号后重试。'));
                resolve({
                    longitude: coordinate(points[0].location.getLng()),
                    latitude: coordinate(points[0].location.getLat()),
                    geomCrs: 'GCJ02'
                });
            });
        })
    );
}

export async function createMap(
    element: HTMLElement,
    onPick?: (location: ScmLocation) => void,
    onRouteStatus?: (status: RouteMapStatus) => void
) {
    const sdk = await load();
    const map = new sdk.Map(element, {zoom: 5, center: [105, 35], viewMode: '2D'});
    let overlays: Overlay[] = [];
    let revision = 0;
    let destroyed = false;
    if (onPick)
        map.on('click', (event) =>
            onPick({
                longitude: coordinate(event.lnglat.getLng()),
                latitude: coordinate(event.lnglat.getLat()),
                geomCrs: 'GCJ02'
            })
        );

    function addPolylines(paths: RoutePosition[][]) {
        const lines = paths
            .filter((path) => path.length)
            .map((path) => new sdk.Polyline({path, strokeColor: '#00B96B', strokeWeight: 4, showDir: true}));
        if (!lines.length) return;
        overlays.push(...lines);
        map.add(lines);
        map.setFitView();
    }

    /** Missing locations break the polyline; never imply a route skipping an unlocated stop. */
    function straightLines(positions: (RoutePosition | null)[]) {
        const lines: RoutePosition[][] = [];
        positions.forEach((point, index) => {
            const previous = positions[index - 1];
            if (index > 0 && point && previous) lines.push([previous, point]);
        });
        return lines;
    }

    function combined(results: RouteDrivingResult[]): RouteDrivingResult {
        let distanceMeters = 0;
        let durationSeconds = 0;
        let segmentCount = 0;
        const paths: RoutePosition[][] = [];
        for (const result of results) {
            distanceMeters += result.distanceMeters;
            durationSeconds += result.durationSeconds;
            segmentCount += result.segmentCount;
            paths.push(...result.paths);
        }
        return {distanceMeters, durationSeconds, paths, segmentCount, provider: 'AMAP', fallback: false};
    }

    async function drawRoute(positions: (RoutePosition | null)[], current: number) {
        // 未定位的点把路线切成多段，算路只在首尾都定位的连续段内进行。
        const runs: RoutePosition[][] = [];
        let run: RoutePosition[] = [];
        for (const point of positions) {
            if (point) run.push(point);
            else if (run.length) {
                runs.push(run);
                run = [];
            }
        }
        if (run.length) runs.push(run);

        const legs = runs.filter((points) => points.length > 1);
        if (!legs.length) {
            onRouteStatus?.({state: 'idle'});
            return;
        }
        onRouteStatus?.({state: 'planning'});
        const results: RouteDrivingResult[] = [];
        for (const points of legs) {
            const result = await planRoadRoute(sdk, points);
            if (destroyed || current !== revision) return;
            if (result.fallback) {
                // 任一段算不出道路路线就整体回退直线，避免一段道路一段直线被读成完整路线。
                addPolylines(straightLines(positions));
                onRouteStatus?.({state: 'fallback'});
                return;
            }
            results.push(result);
        }
        addPolylines(results.flatMap((result) => result.paths));
        onRouteStatus?.({state: 'ready', result: combined(results)});
    }

    return {
        async draw(points: MapPoint[], route = false) {
            const current = ++revision;
            const positions = await Promise.all(points.map((point) => (isLocated(point) ? position(sdk, point) : null)));
            if (destroyed || current !== revision) return;
            map.remove(overlays);
            overlays = [];
            positions.forEach((p, index) => {
                if (!p) return;
                const point = points[index];
                const label = document.createElement('span');
                label.className = 'scm-map-label';
                label.textContent = point.label;
                const marker = new sdk.Marker({
                    position: p,
                    title: point.label,
                    label: {content: label, direction: 'top'},
                    draggable: Boolean(onPick)
                });
                marker.on('click', () => {
                    const content = document.createElement('div');
                    content.className = 'scm-map-info';
                    content.textContent = `${point.label}\n${point.description ?? ''}`;
                    new sdk.InfoWindow({content, offset: new sdk.Pixel(0, -24)}).open(map, p);
                });
                if (onPick)
                    marker.on('dragend', (event) =>
                        onPick({
                            longitude: coordinate(event.lnglat.getLng()),
                            latitude: coordinate(event.lnglat.getLat()),
                            geomCrs: 'GCJ02'
                        })
                    );
                overlays.push(marker);
            });
            map.add(overlays);
            if (overlays.length) map.setFitView();
            if (!route && positions[0]) {
                map.setCenter(positions[0]);
                map.setZoom(16);
            }
            // 先落 Marker 再异步补路线：算路期间地图上已经能看到起点与停靠点。
            if (route) await drawRoute(positions, current);
        },
        destroy() {
            destroyed = true;
            revision++;
            map.destroy();
        },
    };
}
