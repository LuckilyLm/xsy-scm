/*
 * 定位授权。
 *
 * 微信的 getLocation 有三种状态，页面不该各写一遍：
 *   1. 从未询问过 —— 调 authorize 弹系统授权框；
 *   2. 已经同意   —— 直接取位置；
 *   3. 曾经拒绝   —— authorize 会直接失败，必须引导用户去设置页手动打开。
 * 另外微信自 2023 年起要求先通过隐私协议授权，未同意时连授权框都不会弹。
 *
 * 对外只暴露一个 Promise<boolean>：true 表示可以继续取位置，
 * false 表示用户拒绝或环境不支持，页面负责给文案。
 */
import { ensurePrivacyAuthorized, guideToSetting } from '@/composables/use-permission';

const LOCATION_SCOPE = 'scope.userLocation';

/** 确认定位权限可用；返回 false 时不要继续调用 getLocation */
export function requestLocationPermission() {
  let task = Promise.resolve(true);

  // #ifdef MP-WEIXIN
  task = ensurePrivacyAuthorized().then((privacyGranted) => {
    if (!privacyGranted) {
      return false;
    }
    return new Promise((resolve) => {
      uni.getSetting({
        success: ({ authSetting }) => {
          const granted = authSetting && authSetting[LOCATION_SCOPE];
          if (granted === true) {
            resolve(true);
            return;
          }
          if (granted === false) {
            resolve(
              guideToSetting(LOCATION_SCOPE, {
                title: '需要定位权限',
                content: '开启定位后才能使用「使用当前位置」，是否前往设置开启？',
              })
            );
            return;
          }
          uni.authorize({
            scope: LOCATION_SCOPE,
            success: () => resolve(true),
            fail: () =>
              resolve(
                guideToSetting(LOCATION_SCOPE, {
                  title: '需要定位权限',
                  content: '开启定位后才能使用「使用当前位置」，是否前往设置开启？',
                })
              ),
          });
        },
        fail: () => resolve(false),
      });
    });
  });
  // #endif

  return task;
}

/**
 * 取当前位置（GCJ-02）。
 * 权限流程走完后才调用，失败时区分「用户拒绝」与「系统定位关闭」。
 */
export function getCurrentLocation() {
  return new Promise((resolve, reject) => {
    uni.getLocation({
      type: 'gcj02',
      success: ({ longitude, latitude }) => resolve({ longitude, latitude }),
      fail: (error) => reject(error || {}),
    });
  });
}
