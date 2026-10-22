/*
 * 扫码溯源公开接口
 *
 * 免登录：凭据是标签上的令牌，不带客户令牌也能访问。
 * 服务端只返回脱敏后的字段，客户端不做二次裁剪。
 */
import { getRequest } from '@/lib/smart-request';

const baseUrl = import.meta.env.VITE_APP_API_URL;

export const tracePublicApi = {
  /** 按令牌取溯源信息 */
  detail: (token) => getRequest(`/scm/trace/public/${encodeURIComponent(token)}`),

  /** 附件下载地址：直接交给 uni.downloadFile，不经过请求层（它要带 Authorization 头） */
  fileUrl: (token, fileKey) => `${baseUrl}/scm/trace/public/${encodeURIComponent(token)}/files?fileKey=${encodeURIComponent(fileKey)}`,
};
