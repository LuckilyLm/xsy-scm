/** Resolve public homepage assets for both root and /admin/ deployments. */
export function homeAsset(path: string): string {
    const base = import.meta.env.BASE_URL.endsWith('/') ? import.meta.env.BASE_URL : `${import.meta.env.BASE_URL}/`;
    return `${base}home-assets/${path}`;
}
