/* ajax 请求 */
import {message, Modal} from 'ant-design-vue';
import axios, {type AxiosRequestConfig, type AxiosResponse} from 'axios';
import {localRead} from '/@/utils/local-util';
import {useUserStore} from '/@/store/modules/system/user';
import {decryptData, encryptData} from './encrypt';
import {DATA_TYPE_ENUM} from '../constants/common-const';
import {errorText} from '/@/utils/error-text';
import _ from 'lodash';
import LocalStorageKeyConst from '/@/constants/local-storage-key-const';

declare module 'axios' {
  interface AxiosRequestConfig {
    /** 失败时不弹全局提示，由页面自行展示。 */
    suppressGlobalErrorMessage?: boolean;
  }
}

export type RequestOptions = {
  suppressGlobalErrorMessage?: boolean;
};

/** SmartAdmin 统一响应信封；文件流等其他响应不经过此类型。 */
export interface ApiResponse<T = any> {
  code: number;
  ok: boolean;
  msg: string;
  data: T;
  level?: string;
  dataType?: number;
  /** 返回加密时保留的密文，供调试页面展示；解密结果在 data。 */
  encryptData?: string;
}

const TOKEN_HEADER = 'Authorization';

const smartAxios = axios.create({
  baseURL: import.meta.env.VITE_APP_API_URL,
});

// 退出系统
function logout() {
  useUserStore().logout();
  location.href = '/';
}

// 请求拦截器

smartAxios.interceptors.request.use(
  (config) => {
    // 带上登录令牌
    const token = localRead(LocalStorageKeyConst.USER_TOKEN);
    if (token) {
      config.headers[TOKEN_HEADER] = 'Bearer ' + token;
    } else {
      delete config.headers[TOKEN_HEADER];
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
smartAxios.interceptors.response.use(
  (response) => {
    // 非 JSON 直接返回原响应
    let contentType = response.headers['content-type'] ? response.headers['content-type'] : response.headers['Content-Type'];
    if (contentType.indexOf('application/json') === -1) {
      return Promise.resolve(response);
    }

    if (response.data && response.data instanceof Blob) {
      return Promise.reject(response.data);
    }

    // 加密数据先解密
    if (response.data.dataType === DATA_TYPE_ENUM.ENCRYPT.value) {
      response.data.encryptData = response.data.data;
      let decryptStr = decryptData(response.data.data);
      if (decryptStr) {
        response.data.data = JSON.parse(decryptStr);
      }
    }

    const res = response.data;
    if (res.code && res.code !== 1) {
      // `token` 过期或者账号已在别处登录
      if (res.code === 30007 || res.code === 30008) {
        message.destroy();
        message.error('您没有登录，请重新登录');
        setTimeout(logout, 300);
        return Promise.reject(response);
      }

      // 登录安全提醒
      if (res.code === 30010 || res.code === 30011) {
        Modal.error({
          title: '重要提醒',
          content: res.msg,
        });
        return Promise.reject(response);
      }

      // 长时间未操作系统，需要重新登录
      if (res.code === 30012) {
        Modal.error({
          title: '重要提醒',
          content: res.msg,
          onOk: logout,
        });
        setTimeout(logout, 3000);
        return Promise.reject(response);
      }
      if (!response.config.suppressGlobalErrorMessage) {
        message.destroy();
        message.error(errorText(res.msg));
      }
      return Promise.reject(response);
    } else {
      return Promise.resolve(res);
    }
  },
  (error) => {
    if (error.config?.suppressGlobalErrorMessage) {
      return Promise.reject(error);
    }
    if (error.message.indexOf('timeout') !== -1) {
      message.destroy();
      message.error('网络超时');
    } else if (error.message === 'Network Error') {
      message.destroy();
      message.error('网络连接错误');
    } else if (error.message.indexOf('Request') !== -1) {
      message.destroy();
      message.error('网络发生错误');
    }
    return Promise.reject(error);
  }
);

// 对外请求方法

/**
 * JSON 响应在拦截器里被替换成业务信封，文件流仍返回原始 AxiosResponse；
 * 运行时分支无法用单一类型表达，只在 dispatch 出口做一次收口。
 */
function dispatch(config: AxiosRequestConfig): Promise<unknown> {
  return smartAxios.request(config);
}

/** GET 请求 */
export const getRequest = <T = any>(url: string, params?: unknown, options?: RequestOptions): Promise<ApiResponse<T>> =>
  dispatch({url, method: 'get', params, ...options}) as Promise<ApiResponse<T>>;

/** 通用请求封装 */
export const request = <T = any>(config: AxiosRequestConfig): Promise<ApiResponse<T>> => dispatch(config) as Promise<ApiResponse<T>>;

/** POST 请求 */
export const postRequest = <T = any>(url: string, data?: unknown, options?: RequestOptions): Promise<ApiResponse<T>> =>
  dispatch({
    data,
    url,
    method: 'post',
    ...options,
  }) as Promise<ApiResponse<T>>;

// 加密

/** 加密请求参数的 POST 请求 */
export const postEncryptRequest = <T = any>(url: string, data: unknown): Promise<ApiResponse<T>> =>
  dispatch({
    data: {encryptData: encryptData(data)},
    url,
    method: 'post',
  }) as Promise<ApiResponse<T>>;

// 下载

export const postDownload = function (url: string, data: unknown) {
  return dispatch({
    method: 'post',
    url,
    data,
    responseType: 'blob',
  })
    .then((data) => {
      handleDownloadData(data as AxiosResponse);
    })
    .catch((error) => {
      handleDownloadError(error);
    });
};

/** 文件下载；失败已在此处提示，Promise 不会 reject。 */
export const getDownload = function (url: string, params?: unknown) {
  return dispatch({
    method: 'get',
    url,
    params,
    responseType: 'blob',
  })
    .then((data) => {
      handleDownloadData(data as AxiosResponse);
    })
    .catch((error) => {
      handleDownloadError(error);
    });
};

function handleDownloadError(error: unknown) {
  if (error instanceof Blob) {
    error.text().then((text: string) => {
      message.destroy();
      message.error(downloadErrorText(text));
    });
    return;
  }
  message.destroy();
  message.error('网络发生错误');
}

/** 从二进制响应里提取可读错误文案，避免把整页 HTML 弹给用户。 */
function downloadErrorText(text: string) {
  const value = (text || '').trim();
  if (!value) return '文件下载失败';
  try {
    const msg = JSON.parse(value)?.msg;
    if (typeof msg === 'string' && msg.trim()) return msg;
  } catch {
    if (value.length <= 200 && !value.startsWith('<')) return value;
  }
  return '文件下载失败';
}

function downloadFileName(contentDisposition: string | undefined) {
  if (!contentDisposition) return 'download';
  const encoded = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const plain = contentDisposition.match(/filename=([^;]+)/i)?.[1];
  const value = (encoded || plain || 'download').trim().replace(/^"|"$/g, '');
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

function handleDownloadData(response?: AxiosResponse) {
  if (!response) return;
  const contentType = response.headers['content-type'] || response.headers['Content-Type'] || 'application/octet-stream';
  const contentDisposition = response.headers['content-disposition'] || response.headers['Content-Disposition'];
  const url = window.URL.createObjectURL(new Blob([response.data], {type: contentType}));
  const link = document.createElement('a');
  link.style.display = 'none';
  link.href = url;
  link.setAttribute('download', downloadFileName(contentDisposition));
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
  // 文件流交给浏览器下载，页面不会有任何跳转；不给提示的话用户会以为按钮没反应。
  message.success('文件已开始下载');
}
