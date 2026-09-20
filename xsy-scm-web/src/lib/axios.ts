/*
 *  ajax请求
 *
 */
import { message, Modal } from 'ant-design-vue';
import axios from 'axios';
import { localRead } from '/@/utils/local-util';
import { useUserStore } from '/@/store/modules/system/user';
import { decryptData, encryptData } from './encrypt';
import { DATA_TYPE_ENUM } from '../constants/common-const';
import _ from 'lodash';
import LocalStorageKeyConst from '/@/constants/local-storage-key-const';

// token的消息头
const TOKEN_HEADER = 'Authorization';

// 创建axios对象
const smartAxios = axios.create({
  baseURL: import.meta.env.VITE_APP_API_URL,
});

// 退出系统
function logout() {
  useUserStore().logout();
  location.href = '/';
}

// ================================= 请求拦截器 =================================

smartAxios.interceptors.request.use(
  (config) => {
    // 在发送请求之前消息头加入token token
    const token = localRead(LocalStorageKeyConst.USER_TOKEN);
    if (token) {
      config.headers[TOKEN_HEADER] = 'Bearer ' + token;
    } else {
      delete config.headers[TOKEN_HEADER];
    }
    return config;
  },
  (error) => {
    // 对请求错误做些什么
    return Promise.reject(error);
  }
);

// ================================= 响应拦截器 =================================

// 添加响应拦截器
smartAxios.interceptors.response.use(
  (response) => {
    // 根据content-type ，判断是否为 json 数据
    let contentType = response.headers['content-type'] ? response.headers['content-type'] : response.headers['Content-Type'];
    if (contentType.indexOf('application/json') === -1) {
      return Promise.resolve(response);
    }

    // 如果是json数据
    if (response.data && response.data instanceof Blob) {
      return Promise.reject(response.data);
    }

    // 如果是加密数据
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

      // 等保安全的登录提醒
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
      message.destroy();
      message.error(res.msg);
      return Promise.reject(response);
    } else {
      return Promise.resolve(res);
    }
  },
  (error) => {
    // 对响应错误做点什么
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

// ================================= 对外提供请求方法：通用请求，get， post, 下载download等 =================================

/**
 * get请求
 */
export const getRequest = (url, params) => {
  return request({ url, method: 'get', params });
};

/**
 * 通用请求封装
 * @param config
 */
export const request = (config) => {
  return smartAxios.request(config);
};

/**
 * post请求
 */
export const postRequest = (url, data) => {
  return request({
    data,
    url,
    method: 'post',
  });
};

// ================================= 加密 =================================

/**
 * 加密请求参数的post请求
 */
export const postEncryptRequest = (url, data) => {
  return request({
    data: { encryptData: encryptData(data) },
    url,
    method: 'post',
  });
};

// ================================= 下载 =================================

export const postDownload = function (url, data) {
  return request({
    method: 'post',
    url,
    data,
    responseType: 'blob',
  })
    .then((data) => {
      handleDownloadData(data);
    })
    .catch((error) => {
      handleDownloadError(error);
    });
};

/**
 * 文件下载
 *
 * 返回 Promise 以便调用方展示下载中状态；失败已在此处统一提示，故 Promise 不会 reject。
 */
export const getDownload = function (url, params) {
  return request({
    method: 'get',
    url,
    params,
    responseType: 'blob',
  })
    .then((data) => {
      handleDownloadData(data);
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

/**
 * 后端失败时也会返回二进制响应体，这里只从中提取可读文案。
 * 非 JSON 内容仅在足够短且不是 HTML 时采用，避免把整页错误页弹给用户。
 */
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

function handleDownloadData(response) {
  if (!response) return;
  const contentType = response.headers['content-type'] || response.headers['Content-Type'] || 'application/octet-stream';
  const contentDisposition = response.headers['content-disposition'] || response.headers['Content-Disposition'];
  const url = window.URL.createObjectURL(new Blob([response.data], { type: contentType }));
  const link = document.createElement('a');
  link.style.display = 'none';
  link.href = url;
  link.setAttribute('download', downloadFileName(contentDisposition));
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}
