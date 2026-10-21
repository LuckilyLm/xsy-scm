/*
 * 网络状态。
 *
 * 小程序断网时不会给任何提示：请求只是超时或失败，用户容易以为是自己点错了。
 * 这里在全局注册一次监听（App 启动时调用），断网/恢复时给一条轻提示，
 * 页面也可以拿 isConnected 决定是否禁用提交。
 *
 * 监听是进程级的，只注册一次；不要在页面里重复注册，否则会重复弹提示。
 */
import { ref } from 'vue';

const isConnected = ref(true);
const networkType = ref('unknown');
let started = false;

function apply(network) {
  const type = (network && network.networkType) || 'unknown';
  networkType.value = type;
  isConnected.value = type !== 'none' && (!network || network.isConnected !== false);
}

function start() {
  if (started) {
    return;
  }
  started = true;
  try {
    uni.getNetworkType({
      success: (res) => apply(res),
      fail: () => {
        // 取不到网络类型时按在线处理，避免误报断网
      },
    });
    if (typeof uni.onNetworkStatusChange === 'function') {
      uni.onNetworkStatusChange((res) => apply(res));
    }
  } catch {
    // 平台不支持网络状态监听时保持默认在线
  }
}

export function useNetworkStatus() {
  start();
  return { isConnected, networkType };
}
