import { Alert, Button, Drawer, Form, Input, TreeSelect } from 'antd';
import type { FormRule } from 'antd';
import { useEffect, useState } from 'react';
import { createUser, updateUser } from '../../api/system/users';
import type { DepartmentTreeNode, SystemUser } from '../../types/system';
import { PASSWORD_HINT, isValidPassword } from '../../utils/password';
import { describeError, toDepartmentTreeData } from './systemUtils';

interface UserForm {
  username: string;
  displayName: string;
  password?: string;
  departmentId?: number | null;
  email?: string | null;
  phone?: string | null;
}

interface UserDrawerProps {
  open: boolean;
  /** 传值即编辑，空即新增。 */
  value: SystemUser | null;
  departments: DepartmentTreeNode[];
  onClose: () => void;
  onSuccess: () => void;
}

export function UserDrawer({ open, value, departments, onClose, onSuccess }: UserDrawerProps) {
  const [form] = Form.useForm<UserForm>();
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    setErrorMessage(null);
    form.setFieldsValue({
      username: value?.username ?? '',
      displayName: value?.displayName ?? '',
      password: '',
      departmentId: value?.departmentId ?? null,
      email: value?.email ?? '',
      phone: value?.phone ?? '',
    });
  }, [form, open, value]);

  async function submit() {
    let values: UserForm;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    setSubmitting(true);
    setErrorMessage(null);
    try {
      const email = values.email?.trim() ? values.email.trim() : null;
      const phone = values.phone?.trim() ? values.phone.trim() : null;
      if (value) {
        await updateUser(value.id, {
          displayName: values.displayName.trim(),
          departmentId: values.departmentId ?? null,
          email,
          phone,
          version: value.version,
        });
      } else {
        await createUser({
          username: values.username.trim(),
          displayName: values.displayName.trim(),
          password: values.password ?? '',
          departmentId: values.departmentId ?? null,
          email,
          phone,
        });
      }
      onSuccess();
    } catch (error) {
      setErrorMessage(describeError(error, '保存失败，请稍后重试'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Drawer
      onClose={onClose}
      open={open}
      title={value ? '编辑用户' : '新增用户'}
      width={420}
      destroyOnHidden
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button onClick={onClose}>取消</Button>
          <Button loading={submitting} onClick={() => void submit()} type="primary">
            保存
          </Button>
        </div>
      }
    >
      <Form form={form} layout="vertical" disabled={submitting} preserve={false}>
        <Form.Item
          label="用户名"
          name="username"
          extra={value ? '用户名创建后不可修改' : undefined}
          rules={[
            { required: true, message: '请输入用户名' },
            { max: 64, message: '最多 64 个字符' },
          ]}
        >
          <Input autoComplete="off" disabled={Boolean(value)} placeholder="请输入用户名" />
        </Form.Item>
        <Form.Item
          label="姓名"
          name="displayName"
          rules={[
            { required: true, message: '请输入姓名' },
            { max: 100, message: '最多 100 个字符' },
          ]}
        >
          <Input placeholder="请输入姓名" />
        </Form.Item>
        {value ? null : (
          <Form.Item
            label="初始密码"
            name="password"
            extra={PASSWORD_HINT}
            rules={[
              { required: true, message: '请输入初始密码' },
              {
                validator: (_rule: FormRule, password: string) =>
                  !password || isValidPassword(password)
                    ? Promise.resolve()
                    : Promise.reject(new Error(PASSWORD_HINT)),
              },
            ]}
          >
            <Input.Password autoComplete="new-password" placeholder="请输入初始密码" />
          </Form.Item>
        )}
        <Form.Item label="所属部门" name="departmentId">
          <TreeSelect
            allowClear
            placeholder="请选择部门"
            treeData={toDepartmentTreeData(departments)}
            treeDefaultExpandAll
          />
        </Form.Item>
        <Form.Item
          label="邮箱"
          name="email"
          rules={[{ type: 'email', message: '邮箱格式不正确' }]}
        >
          <Input placeholder="选填" />
        </Form.Item>
        <Form.Item label="手机号" name="phone" rules={[{ max: 32, message: '最多 32 个字符' }]}>
          <Input placeholder="选填" />
        </Form.Item>
      </Form>
      {errorMessage ? <Alert type="error" showIcon message={errorMessage} /> : null}
    </Drawer>
  );
}
