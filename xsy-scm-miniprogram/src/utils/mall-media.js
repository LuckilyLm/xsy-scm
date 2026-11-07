const base = (import.meta.env.VITE_APP_MEDIA_URL || '').trim().replace(/\/+$/, '');

/**
 * 返回MinIO大插画地址；不包含导航和表单小图，也不从接口地址猜测存储端口。
 * @param {string} path media目录内的相对资源路径；空值或越级路径返回空串供界面兜底。
 * @returns {string} 公开图片URL，未配置媒体服务时返回空串。
 */
export function mallMedia(path) {
  if (!base || !path || path.startsWith('/') || path.split('/').includes('..')) return '';
  return base + '/' + path;
}
