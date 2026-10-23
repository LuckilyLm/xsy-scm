/** 解析首页静态资源路径，兼容根路径与 /admin/ 部署。 */
export function homeAsset(path: string): string {
  const base = import.meta.env.BASE_URL.endsWith('/') ? import.meta.env.BASE_URL : `${import.meta.env.BASE_URL}/`;
  return `${base}home-assets/${path}`;
}
