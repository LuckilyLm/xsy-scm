/*
 * 键盘高度。
 *
 * 小程序里 position: fixed 的底栏不会跟着键盘上移，键盘一弹出就被整块盖住，
 * 表单页需要在键盘弹出时把底栏抬到键盘上沿；输入框本身还要配合
 * cursor-spacing 留出与键盘的距离。
 *
 * H5 由浏览器自己压缩可视区，一般不会触发键盘高度回调，height 保持 0，
 * 底栏跟着视口收缩即可。
 */
import { computed, onUnmounted, ref } from 'vue';

export function useKeyboardHeight() {
  const keyboardHeight = ref(0);

  const handleChange = (event) => {
    const height = Number(event && event.height);
    keyboardHeight.value = Number.isFinite(height) && height > 0 ? height : 0;
  };

  try {
    if (typeof uni.onKeyboardHeightChange === 'function') {
      uni.onKeyboardHeightChange(handleChange);
    }
  } catch {
    // 个别平台没有键盘高度回调，保持 0，底栏沿用样式里的定位
  }

  onUnmounted(() => {
    try {
      if (typeof uni.offKeyboardHeightChange === 'function') {
        uni.offKeyboardHeightChange(handleChange);
      }
    } catch {
      // 同上，没有对应 API 时无需解绑
    }
  });

  /** 键盘弹出时给固定底栏用的偏移；未弹出返回空对象，交回样式表控制 */
  const barStyle = computed(() => (keyboardHeight.value > 0 ? { bottom: `${keyboardHeight.value}px` } : {}));

  return { keyboardHeight, barStyle };
}
