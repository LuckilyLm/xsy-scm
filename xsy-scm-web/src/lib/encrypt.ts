import CryptoJS from 'crypto-js';
import CryptoSM from 'sm-crypto';

function object2string(data) {
  if (typeof data === 'object') {
    return JSON.stringify(data);
  }

  let str = JSON.stringify(data);
  if (str.startsWith("'") || str.startsWith('"')) {
    str = str.substring(1);
  }
  if (str.endsWith("'") || str.endsWith('"')) {
    str = str.substring(0, str.length - 1);
  }
  return str;
}

/** 字符串转十六进制 */
function stringToHex(str) {
  let hex = '';
  for (let i = 0; i < str.length; i++) {
    hex += str.charCodeAt(i).toString(16).padStart(2, '0');
  }
  return hex;
}

/* AES-128，密钥固定 16 个字符 */
const AES_KEY = '1024lab__1024lab';

const AES = {
  encryptData: function (data) {
    // AES 加密后转 base64
    let utf8Data = CryptoJS.enc.Utf8.parse(object2string(data));
    const key = CryptoJS.enc.Utf8.parse(AES_KEY);
    const encrypted = CryptoJS.AES.encrypt(utf8Data, key, {
      mode: CryptoJS.mode.ECB,
      padding: CryptoJS.pad.Pkcs7,
    });
    return CryptoJS.enc.Base64.stringify(encrypted.ciphertext);
  },

  decryptData: function (data) {
    let words = CryptoJS.enc.Base64.parse(data);

    const key = CryptoJS.enc.Utf8.parse(AES_KEY);
    return CryptoJS.AES.decrypt({ciphertext: words}, key, {
      mode: CryptoJS.mode.ECB,
      padding: CryptoJS.pad.Pkcs7,
    }).toString(CryptoJS.enc.Utf8);
  },
};

/* 国密 SM4，密钥固定 16 个字符 */
const SM4_KEY = '1024lab__1024lab';

const SM4 = {
  encryptData: function (data) {
    // SM4 加密后转 base64
    let encryptData = CryptoSM.sm4.encrypt(object2string(data), stringToHex(SM4_KEY));
    return CryptoJS.enc.Base64.stringify(CryptoJS.enc.Utf8.parse(encryptData));
  },

  decryptData: function (data) {
    let words = CryptoJS.enc.Base64.parse(data);
    let decode64Str = CryptoJS.enc.Utf8.stringify(words);

    return CryptoSM.sm4.decrypt(decode64Str, stringToHex(SM4_KEY));
  },
};

// 对外接口

// 默认使用 SM4
const EncryptObject = SM4;
// const EncryptObject = AES;

/**
 * 加密
 */
export const encryptData = function (data) {
  return !data ? null : EncryptObject.encryptData(data);
};

/**
 * 解密
 */
export const decryptData = function (data) {
  return !data ? null : EncryptObject.decryptData(data);
};
