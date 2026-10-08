import { customRef, ref } from 'vue';
import { message } from 'ant-design-vue';

/**
 * 列表 / 详情页的查询与操作错误状态。
 *
 * 失败原因以 toast 呈现而不是常驻 Alert：一次失败的查询会在页面顶部留下整幅红条，
 * 用户改条件重试后仍然挂在那里，把首屏业务内容往下推。写入仍保留 `error` 值，
 * 供「导出」「重试」等按钮判断当前是否有未恢复的失败。
 *
 * 赋值前先清空，使同一文案连续失败也能连续提示。
 */
export function useScmErrorToast() {
  const inner = ref('');
  return customRef<string>((track, trigger) => ({
    get: () => {
      track();
      return inner.value;
    },
    set: (value) => {
      inner.value = '';
      if (value) {
        message.error(value);
      }
      inner.value = value;
      trigger();
    },
  }));
}
