import type {ProColumns} from '@ant-design/pro-components';
import {ProTable} from '@ant-design/pro-components';
import {Alert, Button, Tag} from 'antd';
import dayjs from 'dayjs';
import {useRef, useState} from 'react';
import type {ActionType} from '@ant-design/pro-components';
import {fetchLoginLogs, type LoginLog} from '../../api/system/auditLogs';
import {PageContainer} from '../../components/common/PageContainer';
import {describeError} from './systemUtils';

const RESULT_ENUM = {
    SUCCESS: {text: '成功', status: 'Success' as const},
    FAILURE: {text: '失败', status: 'Error' as const},
    LOCKED: {text: '已锁定', status: 'Warning' as const},
    LOGOUT: {text: '退出', status: 'Default' as const},
};

export function LoginLogPage() {
    const actionRef = useRef<ActionType>(null);
    const [error, setError] = useState<string | null>(null);
    const columns: ProColumns<LoginLog>[] = [
        {title: '用户名', dataIndex: 'username', render: (_value, row) => row.usernameSnapshot ?? '-'},
        {
            title: '结果',
            dataIndex: 'result',
            valueType: 'select',
            valueEnum: RESULT_ENUM,
            render: (_value, row) => <Tag
                color={RESULT_ENUM[row.result].status.toLowerCase()}>{RESULT_ENUM[row.result].text}</Tag>
        },
        {title: '失败原因', dataIndex: 'failureReasonCode', search: false, render: (value) => value || '-'},
        {title: 'IP 地址', dataIndex: 'ip', search: false, render: (value) => value || '-'},
        {
            title: '发生时间',
            dataIndex: 'occurredAt',
            search: false,
            render: (value) => dayjs(value as string).format('YYYY-MM-DD HH:mm:ss')
        },
    ];
    return <PageContainer title="登录日志">
        {error ? <Alert type="error" showIcon message={error}
                        action={<Button onClick={() => actionRef.current?.reload()}>重试</Button>}/> : null}
        <ProTable<LoginLog> actionRef={actionRef} columns={columns} rowKey="id" size="small"
                            search={{labelWidth: 'auto'}} pagination={{pageSize: 20}} request={async (params) => {
            try {
                const page = await fetchLoginLogs({
                    page: params.current,
                    pageSize: params.pageSize,
                    username: params.username,
                    result: params.result
                });
                setError(null);
                return {data: page.records, total: page.total, success: true};
            } catch (cause) {
                setError(describeError(cause, '登录日志加载失败'));
                return {data: [], total: 0, success: false};
            }
        }}/>
    </PageContainer>;
}
