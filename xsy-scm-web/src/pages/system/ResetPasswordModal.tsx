import {Alert, Form, Input, Modal, message} from 'antd';
import type {FormRule} from 'antd';
import {useEffect, useState} from 'react';
import {resetUserPassword} from '../../api/system/users';
import type {SystemUser} from '../../types/system';
import {PASSWORD_HINT, isValidPassword} from '../../utils/password';
import {describeError} from './systemUtils';

interface ResetPasswordForm {
    newPassword: string;
    confirmPassword: string;
}

interface ResetPasswordModalProps {
    open: boolean;
    user: SystemUser | null;
    onClose: () => void;
    onSuccess: () => void;
}

/**
 * 管理员重置密码：后端生成一次性凭证并强制该用户下次登录改密，
 * 同时撤销该用户的全部会话。
 */
export function ResetPasswordModal({open, user, onClose, onSuccess}: ResetPasswordModalProps) {
    const [form] = Form.useForm<ResetPasswordForm>();
    const [submitting, setSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    useEffect(() => {
        if (open) {
            setErrorMessage(null);
            form.resetFields();
        }
    }, [form, open]);

    async function submit() {
        if (!user) {
            return;
        }
        let values: ResetPasswordForm;
        try {
            values = await form.validateFields();
        } catch {
            return;
        }
        setSubmitting(true);
        setErrorMessage(null);
        try {
            await resetUserPassword(user.id, {newPassword: values.newPassword, version: user.version});
            message.success(`已重置 ${user.displayName} 的密码，请告知用户尽快修改`);
            onSuccess();
        } catch (error) {
            setErrorMessage(describeError(error, '重置密码失败，请稍后重试'));
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <Modal
            okText="确认重置"
            cancelText="取消"
            confirmLoading={submitting}
            onCancel={onClose}
            onOk={() => void submit()}
            open={open}
            title={user ? `重置密码 — ${user.displayName}` : '重置密码'}
            destroyOnHidden
        >
            <Alert
                style={{marginBottom: 16}}
                type="info"
                showIcon
                message="重置后该用户的全部登录会话立即失效，并且下次登录必须修改密码。"
            />
            <Form form={form} layout="vertical" preserve={false} disabled={submitting}>
                <Form.Item
                    label="新密码"
                    name="newPassword"
                    extra={PASSWORD_HINT}
                    rules={[
                        {required: true, message: '请输入新密码'},
                        {
                            validator: (_rule: FormRule, value: string) =>
                                !value || isValidPassword(value)
                                    ? Promise.resolve()
                                    : Promise.reject(new Error(PASSWORD_HINT)),
                        },
                    ]}
                >
                    <Input.Password autoComplete="new-password" placeholder="请输入新密码"/>
                </Form.Item>
                <Form.Item
                    label="确认新密码"
                    name="confirmPassword"
                    dependencies={['newPassword']}
                    rules={[
                        {required: true, message: '请再次输入新密码'},
                        (form) => ({
                            validator: (_rule: FormRule, value: string) =>
                                !value || value === form.getFieldValue('newPassword')
                                    ? Promise.resolve()
                                    : Promise.reject(new Error('两次输入的新密码不一致')),
                        }),
                    ]}
                >
                    <Input.Password autoComplete="new-password" placeholder="请再次输入新密码"/>
                </Form.Item>
            </Form>
            {errorMessage ? <Alert type="error" showIcon message={errorMessage}/> : null}
        </Modal>
    );
}
