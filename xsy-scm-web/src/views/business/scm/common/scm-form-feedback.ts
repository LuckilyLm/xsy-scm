import {message} from 'ant-design-vue';
import type {FormInstance} from 'ant-design-vue';

interface FormErrorField {
  name: (string | number)[];
  errors: string[];
}

/**
 * 表单校验失败时统一给一个 toast，并滚动到第一个未通过的字段。
 *
 * 抽屉和弹窗里的字段多、滚动位置固定，`a-form-item` 的红色提示经常落在可视区外：
 * 用户只看到「点保存没反应」。toast 说清缺什么，滚动把第一个问题带到眼前；
 * 字段级提示仍然保留，不互相替代。
 */
export function notifyFormInvalid(err: unknown, form?: FormInstance): void {
  const fields = (err as {errorFields?: FormErrorField[]})?.errorFields ?? [];
  const reasons = fields.map((field) => field.errors?.[0] ?? field.name.join('.')).filter(Boolean);
  message.error(reasons.length ? reasons.join('；') : '请检查表单填写');
  const first = fields[0]?.name;
  if (first) form?.scrollToField(first);
}
