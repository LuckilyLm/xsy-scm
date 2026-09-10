import type {ProColumns} from '@ant-design/pro-components';
import {ProTable} from '@ant-design/pro-components';
import {Alert, Button, Tag} from 'antd';
import dayjs from 'dayjs';
import {useRef, useState} from 'react';
import type {ActionType} from '@ant-design/pro-components';
import {fetchOperationLogs, type OperationLog} from '../../api/system/auditLogs';
import {PageContainer} from '../../components/common/PageContainer';
import {describeError} from './systemUtils';

export function OperationLogPage() {
    const actionRef = useRef<ActionType>(null);
    const [error, setError] = useState<string | null>(null);
    const columns: ProColumns<OperationLog>[] = [
        {title: '操作人', dataIndex: 'actorName', render: (_value, row) => row.actorNameSnapshot ?? '-'},
        {title: '模块', dataIndex: 'module'},
        {title: '操作编码', dataIndex: 'operationCode'},
        {
            title: '目标',
            dataIndex: 'targetType',
            render: (_value, row) => row.targetId ? `${row.targetType ?? '-'} #${row.targetId}` : row.targetType ?? '-'
        },
        {
            title: '结果',
            dataIndex: 'success',
            valueType: 'select',
            valueEnum: {true: {text: '成功'}, false: {text: '失败'}},
            render: (_value, row) => <Tag
                color={row.success ? 'success' : 'error'}>{row.success ? '成功' : '失败'}</Tag>
        },
        {
            title: '时间',
            dataIndex: 'occurredAt',
            search: false,
            render: (value) => dayjs(value as string).format('YYYY-MM-DD HH:mm:ss')
        },
    ];
    return <PageContainer title="操作日志">
        {error ? <Alert type="error" showIcon message={error}
                        action={<Button onClick={() => actionRef.current?.reload()}>重试</Button>}/> : null}
        <ProTable<OperationLog> actionRef={actionRef} columns={columns} rowKey="id" size="small"
                                search={{labelWidth: 'auto'}} pagination={{pageSize: 20}} request={async (params) => {
            try {
                const page = await fetchOperationLogs({
                    page: params.current,
                    pageSize: params.pageSize,
                    module: params.module,
                    operationCode: params.operationCode,
                    targetType: params.targetType,
                    targetId: params.targetId,
                    success: params.success
                });
                setError(null);
                return {data: page.records, total: page.total, success: true};
            } catch (cause) {
                setError(describeError(cause, '操作日志加载失败'));
                return {data: [], total: 0, success: false};
            }
        }}/>
    </PageContainer>;
}
