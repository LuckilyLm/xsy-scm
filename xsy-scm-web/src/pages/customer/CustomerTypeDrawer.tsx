import { Alert, Button, Drawer, Form, Input, Radio } from 'antd';
import { useEffect, useState } from 'react';
import type { CustomerType, CustomerTypeInput } from '../../types/sales';
import { describeError, STATUS_OPTIONS } from '../system/systemUtils';

interface CustomerTypeForm {
  typeCode: string;
  name: string;
  status: 'ENABLED' | 'DISABLED';
}

interface CustomerTypeDrawerProps {
  open: boolean;
  /** 传值即编辑，空即新增。 */
  value: CustomerType | null;
  onClose: () => void;
  onSuccess: (payload: CustomerTypeInput) => void;
}

export function CustomerTypeDrawer({ open, value, onClose, onSuccess }: CustomerTypeDrawerProps) {
  const [form] = Form.useForm<CustomerTypeForm>();
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    setErrorMessage(null);
    form.setFieldsValue({
      typeCode: value?.typeCode ?? '',
      name: value?.name ?? '',
      status: value?.status ?? 'ENABLED',
    });
  }, [form, open, value]);

  async function submit() {
    let values: CustomerTypeForm;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    setSubmitting(true);
    setErrorMessage(null);
    try {
      onSuccess({
        typeCode: values.typeCode.trim(),
        name: values.name.trim(),
        status: values.status,
        ...(value ? { version: value.version } : {}),
      });
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
      title={value ? '编辑客户类型' : '新增客户类型'}
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
          label="类型编码"
          name="typeCode"
          rules={[
            { required: true, message: '请输入类型编码' },
            { max: 64, message: '最多 64 个字符' },
          ]}
        >
          <Input placeholder="例如：CATERING" />
        </Form.Item>
        <Form.Item
          label="类型名称"
          name="name"
          rules={[
            { required: true, message: '请输入类型名称' },
            { max: 100, message: '最多 100 个字符' },
          ]}
        >
          <Input placeholder="请输入类型名称" />
        </Form.Item>
        <Form.Item label="状态" name="status" rules={[{ required: true, message: '请选择状态' }]}>
          <Radio.Group optionType="button" options={STATUS_OPTIONS} />
        </Form.Item>
      </Form>
      {errorMessage ? <Alert type="error" showIcon message={errorMessage} /> : null}
    </Drawer>
  );
}
