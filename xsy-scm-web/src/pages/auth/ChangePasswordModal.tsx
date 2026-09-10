import { Alert, Form, Input, Modal, message } from 'antd';
import type { FormInstance, FormRule } from 'antd';
import { useState } from 'react';
import { ApiError } from '../../api/http';
import { useAuth } from '../../auth/AuthProvider';
import { PASSWORD_HINT, isValidPassword } from '../../utils/password';

interface ChangePasswordForm {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

interface ChangePasswordModalProps {
  open: boolean;
  /** 强制改密：不允许关闭，直到修改成功。 */
  forced?: boolean;
  onClose: () => void;
}

/** 修改当前用户密码；成功后刷新 /auth/me，后端会撤销该用户其他会话。 */
export function ChangePasswordModal({ open, forced = false, onClose }: ChangePasswordModalProps) {
  const { user, changeOwnPassword, refresh } = useAuth();
  const [form] = Form.useForm<ChangePasswordForm>();
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  async function submit() {
    if (!user) {
      return;
    }
    let values: ChangePasswordForm;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await changeOwnPassword({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
        version: user.version,
      });
      form.resetFields();
      await refresh();
      message.success('密码已修改，请重新登录其他设备');
      onClose();
    } catch (error) {
      setErrorMessage(
        error instanceof ApiError ? error.message : '修改密码失败，请稍后重试',
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      closable={!forced}
      maskClosable={!forced}
      okText="确认修改"
      cancelText={forced ? undefined : '取消'}
      cancelButtonProps={forced ? { style: { display: 'none' } } : undefined}
      confirmLoading={submitting}
      onCancel={forced ? undefined : onClose}
      onOk={() => void submit()}
      open={open}
      title="修改密码"
      destroyOnHidden
    >
      {forced ? (
        <Alert
          style={{ marginBottom: 16 }}
          type="warning"
          showIcon
          message="账号要求修改密码后才能继续操作"
        />
      ) : null}
      <Form form={form} layout="vertical" preserve={false} disabled={submitting}>
        <Form.Item
          label="当前密码"
          name="currentPassword"
          rules={[{ required: true, message: '请输入当前密码' }]}
        >
          <Input.Password autoComplete="current-password" placeholder="请输入当前密码" />
        </Form.Item>
        <Form.Item
          label="新密码"
          name="newPassword"
          extra={PASSWORD_HINT}
          rules={[
            { required: true, message: '请输入新密码' },
            {
              validator: (_rule: FormRule, value: string) =>
                !value || isValidPassword(value)
                  ? Promise.resolve()
                  : Promise.reject(new Error(PASSWORD_HINT)),
            },
          ]}
        >
          <Input.Password autoComplete="new-password" placeholder="请输入新密码" />
        </Form.Item>
        <Form.Item
          label="确认新密码"
          name="confirmPassword"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: '请再次输入新密码' },
            ({ getFieldValue }: FormInstance) => ({
              validator: (_rule: FormRule, value: string) =>
                !value || value === getFieldValue('newPassword')
                  ? Promise.resolve()
                  : Promise.reject(new Error('两次输入的新密码不一致')),
            }),
          ]}
        >
          <Input.Password autoComplete="new-password" placeholder="请再次输入新密码" />
        </Form.Item>
      </Form>
      {errorMessage ? <Alert type="error" showIcon message={errorMessage} /> : null}
    </Modal>
  );
}
