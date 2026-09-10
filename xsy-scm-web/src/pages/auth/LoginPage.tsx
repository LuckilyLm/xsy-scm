import { Alert, Button, Form, Input } from 'antd';
import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthProvider';
import { describeAuthError } from '../../utils/authErrors';
import styles from './LoginPage.module.css';

interface LoginForm {
  username: string;
  password: string;
}

interface LocationState {
  from?: string;
}

/** 登录页不写入任何本地存储，会话凭据只由后端 HttpOnly Cookie 持有。 */
export function LoginPage() {
  const { status, user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form] = Form.useForm<LoginForm>();
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const redirectTo = (location.state as LocationState | null)?.from ?? '/';

  // 已登录用户不应停留在登录页；强制改密由布局层拦截。
  if (status === 'authenticated' && user) {
    return <Navigate replace to={redirectTo} />;
  }

  async function submit() {
    if (submitting) {
      return;
    }
    let values: LoginForm;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await login({ username: values.username.trim(), password: values.password });
      navigate(redirectTo, { replace: true });
    } catch (error) {
      setErrorMessage(describeAuthError(error));
      // 登录失败不保留密码，避免再次提交旧凭据。
      form.setFieldValue('password', '');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <div className={styles.brand}>鲜蔬源智慧供应链</div>
        <div className={styles.slogan}>请使用管理员账号登录后台</div>
        <Form
          form={form}
          layout="vertical"
          size="large"
          disabled={submitting}
          onFinish={() => void submit()}
        >
          <Form.Item
            label="用户名"
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input autoComplete="username" autoFocus placeholder="请输入用户名" />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password autoComplete="current-password" placeholder="请输入密码" />
          </Form.Item>
          {errorMessage ? (
            <Alert style={{ marginBottom: 16 }} type="error" showIcon message={errorMessage} />
          ) : null}
          <Button
            block
            className={styles.submit}
            htmlType="submit"
            loading={submitting}
            type="primary"
          >
            登录
          </Button>
        </Form>
        <div className={styles.hint}>登录失败次数过多会临时锁定账号</div>
      </div>
    </div>
  );
}
