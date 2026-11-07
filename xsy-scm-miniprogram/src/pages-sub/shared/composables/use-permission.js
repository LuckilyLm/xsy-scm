/*
 * 授权引导。
 *
 * 微信的 scope 一旦被用户拒绝，再次调用对应 API 会直接失败，不会再弹系统授权框，
 * 只能引导用户去设置页手动打开。定位、收货地址、录音、相册都需要同一段逻辑，
 * 统一放这里，避免每个业务各写一份 modal + openSetting。
 */

/**
 * 弹窗引导用户去设置页开启指定 scope。
 * 返回 Promise<boolean>：true 表示用户在设置页把该 scope 打开了。
 */
export function guideToSetting(scope, options = {}) {
  const { title = '需要授权', content = '', confirmText = '去设置' } = options;
  return new Promise((resolve) => {
    uni.showModal({
      title,
      content: content || '请在设置中开启对应权限后重试。',
      confirmText,
      success: ({ confirm }) => {
        if (!confirm) {
          resolve(false);
          return;
        }
        uni.openSetting({
          success: ({ authSetting }) => resolve(Boolean(authSetting && authSetting[scope])),
          fail: () => resolve(false),
        });
      },
      fail: () => resolve(false),
    });
  });
}

/** 微信隐私协议授权；非微信端或低版本没有该 API 时直接放行 */
export function ensurePrivacyAuthorized() {
  let task = Promise.resolve(true);

  // #ifdef MP-WEIXIN
  task = new Promise((resolve) => {
    if (typeof wx === 'undefined' || typeof wx.requirePrivacyAuthorize !== 'function') {
      resolve(true);
      return;
    }
    wx.requirePrivacyAuthorize({
      success: () => resolve(true),
      fail: () => resolve(false),
    });
  });
  // #endif

  return task;
}
