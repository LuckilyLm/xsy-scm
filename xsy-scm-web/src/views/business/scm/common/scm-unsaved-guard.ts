/** 表单抽屉关闭前的未保存修改守卫。 */
import {Modal} from 'ant-design-vue';
import type {ModalFuncProps} from 'ant-design-vue';
import {formChanged, formSnapshot} from './scm-unsaved';

export interface UnsavedGuard {
  /** 以当前表单内容作为基线；打开抽屉并加载完成后调用。 */
  capture: () => void;
  /** 清空基线；开始加载新记录时调用，避免沿用上一条记录。 */
  clear: () => void;
  isDirty: () => boolean;
  /** 有未保存修改时弹出确认；返回 true 表示可以关闭。 */
  confirmDiscard: () => Promise<boolean>;
}

/** Modal.confirm 返回的是销毁句柄而不是 Promise，这里包一层拿到用户选择。 */
export function confirmDialog(options: ModalFuncProps): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      ...options,
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}

export function createUnsavedGuard<T>(read: () => T, content = '关闭后本次修改将丢失。'): UnsavedGuard {
  let baseline: string | undefined;
  const isDirty = () => formChanged(read(), baseline);
  return {
    capture() {
      baseline = formSnapshot(read());
    },
    clear() {
      baseline = undefined;
    },
    isDirty,
    async confirmDiscard() {
      if (!isDirty()) return true;
      return confirmDialog({
        title: '放弃未保存的修改？',
        content,
        okText: '放弃修改',
        okType: 'danger',
        cancelText: '继续编辑',
      });
    },
  };
}
