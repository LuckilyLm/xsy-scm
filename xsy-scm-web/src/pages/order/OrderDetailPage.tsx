import {ArrowLeftOutlined} from '@ant-design/icons';
import {useQuery} from '@tanstack/react-query';
import {
    Alert,
    Button,
    Descriptions,
    Empty,
    Form,
    Input,
    Modal,
    Space,
    Spin,
    Table,
    Tabs,
    Typography,
    message
} from 'antd';
import {useRef, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {ApiError} from '../../api/http';
import {cancelOrder, confirmOrder, fetchOrder, fetchOrderLogs, updateActualQuantity} from '../../api/orders';
import {AUTHORITIES} from '../../auth/authorities';
import {Permission} from '../../auth/Permission';
import {PageContainer} from '../../components/common/PageContainer';
import {StatusTag} from '../../components/common/StatusTag';
import type {OrderItem} from '../../types/sales';
import styles from './Sales.module.css';

const requestKey = () => `${Date.now()}-${Math.random().toString(36).slice(2)}`;

export function OrderDetailPage() {
    const {id} = useParams();
    const orderId = Number(id);
    const nav = useNavigate();
    const [pending, setPending] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const actionKey = useRef(requestKey());
    const detail = useQuery({queryKey: ['order', orderId], queryFn: () => fetchOrder(orderId)});
    const logs = useQuery({queryKey: ['order-logs', orderId], queryFn: () => fetchOrderLogs(orderId), retry: false});
    const order = detail.data;

    async function act(fn: () => Promise<void>, success: string) {
        if (pending) return;
        setPending(true);
        setError(null);
        try {
            await fn();
            message.success(success);
            await Promise.all([detail.refetch(), logs.refetch()]);
            actionKey.current = requestKey();
        } catch (cause) {
            setError(cause instanceof ApiError && cause.status === 409 ? '操作冲突：订单已被其他人处理，请重新加载' : cause instanceof Error ? cause.message : '操作失败，请重试');
            actionKey.current = requestKey();
        } finally {
            setPending(false)
        }
    }

    function weight(row: OrderItem) {
        let actual = row.actualQuantity ?? '';
        let reason = '';
        Modal.confirm({
            title: `录入 ${row.productName} 实重`,
            content: <Form layout="vertical"><Form.Item label="实际数量 / 重量" required><Input defaultValue={actual}
                                                                                                suffix={row.saleUnit}
                                                                                                onChange={e => actual = e.target.value}/></Form.Item><Form.Item
                label="人工录入原因" required><Input.TextArea placeholder="说明称重或修正原因"
                                                              onChange={e => reason = e.target.value}/></Form.Item></Form>,
            okText: '确认录入',
            onOk: async () => {
                if (!actual || !reason.trim()) throw new Error('请填写实重和原因');
                await act(() => updateActualQuantity(orderId, row.id!, {
                    version: row.version!,
                    actualQuantity: actual,
                    reason: reason.trim()
                }, requestKey()), '实重已更新');
            }
        })
    }

    function cancel() {
        let reason = '';
        Modal.confirm({
            title: '取消订单',
            content: <Input.TextArea placeholder="请输入取消原因" onChange={e => reason = e.target.value}/>,
            okButtonProps: {danger: true},
            okText: '确认取消',
            onOk: async () => {
                if (!reason.trim()) throw new Error('请输入取消原因');
                await act(() => cancelOrder(orderId, {
                    version: order!.version,
                    reason: reason.trim()
                }, actionKey.current), '订单已取消')
            }
        })
    }

    if (detail.isLoading) return <PageContainer><Spin tip="订单详情加载中…"/></PageContainer>;
    if (detail.isError || !order) return <PageContainer><Alert type="error" showIcon message="订单详情加载失败"
                                                               action={<Button
                                                                   onClick={() => detail.refetch()}>重试</Button>}/></PageContainer>;
    const ready = order.items.every(i => i.actualQuantity && Number(i.actualQuantity) > 0);
    return <PageContainer>
        <div className={styles.header}><Space><Button icon={<ArrowLeftOutlined/>}
                                                      onClick={() => nav('/orders')}>返回列表</Button><Typography.Title
            level={4} className={styles.title}>{order.orderNo}</Typography.Title><StatusTag
            status={order.status}/></Space><Space>{order.status === 'DRAFT' ?
            <Permission authority={AUTHORITIES.orderManage}><Button
                onClick={() => nav(`/orders/${order.id}/edit`)}>编辑草稿</Button></Permission> : null}{order.status === 'PENDING' ?
            <Permission authority={AUTHORITIES.orderManage}><Button type="primary" disabled={!ready || pending}
                                                                    loading={pending}
                                                                    onClick={() => act(() => confirmOrder(order.id, order.version, actionKey.current), '订单已确认')}>确认订单</Button></Permission> : null}{order.status === 'CONFIRMED' ?
            <Permission authority={AUTHORITIES.orderManage}><Button type="primary"
                                                                    onClick={() => nav(`/order-returns/new?orderId=${order.id}`)}>发起退货</Button></Permission> : null}{['DRAFT', 'PENDING'].includes(order.status) ?
            <Permission authority={AUTHORITIES.orderManage}><Button danger disabled={pending}
                                                                    onClick={cancel}>取消订单</Button></Permission> : null}</Space>
        </div>
        {error ? <Alert className={styles.error} type="error" showIcon message={error}
                        action={<Button onClick={() => detail.refetch()}>重新加载</Button>}/> : null}<Tabs items={[{
        key: 'detail',
        label: '订单详情',
        children: <><Descriptions bordered size="small" column={4} items={[{
            key: 'customer',
            label: '客户',
            children: order.customerName
        }, {key: 'source', label: '类型', children: order.source === 'SUPPLEMENT' ? '补单' : '普通订单'}, {
            key: 'total',
            label: '结算金额',
            children: `¥ ${order.totalAmount}`
        }, {key: 'version', label: '版本', children: order.version}, {
            key: 'created',
            label: '创建时间',
            children: new Date(order.createdAt).toLocaleString('zh-CN', {hour12: false})
        }, {key: 'supplement', label: '补单原因', children: order.supplementReason || '--', span: 3}]}/><Table
            style={{marginTop: 16}} size="small" pagination={false} rowKey={r => r.id ?? r.skuId}
            dataSource={order.items} scroll={{x: 1050}} columns={[{
            title: '商品',
            width: 210,
            render: (_, r) => <><b>{r.productName}</b>
                <div className={styles.note}>{r.skuCode} · {r.specName}</div>
            </>
        }, {
            title: '类型',
            width: 80,
            render: (_, r) => r.productType === 'STANDARD' ? '标品' : '非标品'
        }, {
            title: '订购数量',
            width: 120,
            align: 'right',
            render: (_, r) => `${r.orderedQuantity} ${r.saleUnit}`
        }, {
            title: '实际数量',
            width: 140,
            align: 'right',
            render: (_, r) => r.actualQuantity ? `${r.actualQuantity} ${r.saleUnit}` :
                <Typography.Text type="warning">待录入</Typography.Text>
        }, {
            title: '锁定单价',
            width: 140,
            align: 'right',
            render: (_, r) => <span className={styles.locked}>¥ {r.lockedUnitPrice ?? '--'}</span>
        }, {title: '来源', width: 100, render: (_, r) => <StatusTag status={r.priceSource}/>}, {
            title: '金额',
            width: 140,
            align: 'right',
            render: (_, r) => r.amount ? `¥ ${r.amount}` : '--'
        }, ...(order.status === 'PENDING' ? [{
            title: '操作',
            width: 120,
            fixed: 'right' as const,
            render: (_: unknown, r: OrderItem) => r.productType === 'NON_STANDARD' ?
                <Permission authority={AUTHORITIES.orderManage}><Button type="link" disabled={pending}
                                                                        onClick={() => weight(r)}>{r.actualQuantity ? '修正实重' : '录入实重'}</Button></Permission> : '--'
        }] : [])]}/>{order.status === 'PENDING' && !ready ? <Alert style={{marginTop: 12}} type="warning" showIcon
                                                                   message="确认前需为所有非标品录入大于零的实际数量"/> : null}</>
    }, {
        key: 'logs',
        label: '操作日志',
        children: logs.isLoading ? <Spin/> : logs.isError ? <Alert type="error" message="操作日志加载失败"
                                                                   action={<Button
                                                                       onClick={() => logs.refetch()}>重试</Button>}/> : logs.data?.length ?
            <Table size="small" pagination={false} rowKey="id" dataSource={logs.data} columns={[{
                title: '时间',
                dataIndex: 'createdAt',
                width: 190,
                render: v => new Date(v).toLocaleString('zh-CN', {hour12: false})
            }, {title: '操作', dataIndex: 'operationType', width: 160}, {
                title: '操作者',
                dataIndex: 'operator',
                width: 120
            }, {
                title: '变更',
                render: (_, r) => <Typography.Text code>{JSON.stringify(r.afterData)}</Typography.Text>
            }]}/> : <Empty description="暂无操作日志"/>
    }]}/></PageContainer>
}
