import { getRequest } from '@/lib/smart-request';

/** 地点搜索与逆地理编码由服务端调用高德，Web Service Key 不进入客户端。 */
export const mallMapApi = {
  search: (keyword, city) => getRequest('/scm/mall/map/search', { keyword, city }),
  reverse: (longitude, latitude) => getRequest('/scm/mall/map/reverse', { longitude, latitude }),
};
