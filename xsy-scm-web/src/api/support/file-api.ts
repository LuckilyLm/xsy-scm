/*
 * 文件上传
 *
 */
import {postRequest, getRequest, getDownload} from '/@/lib/axios';
import type {RequestOptions} from '/@/lib/axios';

export const fileApi = {
  // 文件上传
  uploadUrl: '/support/file/upload',
  uploadFile: <T = any>(param: FormData, folder: number, options?: RequestOptions) => {
    return postRequest<T>(`/support/file/upload?folder=${folder}`, param, options);
  },

  /**
   * 分页查询
   */
  queryPage: (param) => {
    return postRequest('/support/file/queryPage', param);
  },
  /**
   * 获取文件URL：根据fileKey
   */
  getUrl: (fileKey) => {
    return getRequest(`/support/file/getFileUrl?fileKey=${fileKey}`);
  },

  /**
   * 下载文件流（根据fileKey）
   */
  downLoadFile: (fileKey) => {
    return getDownload('/support/file/downLoad', {fileKey});
  },
};
