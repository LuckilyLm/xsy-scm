import type {ActionType, ProColumns} from '@ant-design/pro-components';
import {ProTable} from '@ant-design/pro-components';
import {Button, Popconfirm, Space, message} from 'antd';
import {useRef} from 'react';
import {useNavigate} from 'react-router-dom';
import {cancelPurchaseOrder, createReceipt, fetchPurchaseOrders, submitPurchaseOrder} from '../../api/purchases';
import {AUTHORITIES} from '../../auth/authorities';
import {Permission} from '../../auth/Permission';
import {AmountText} from '../../components/common/AmountText';
import {PageContainer} from '../../components/common/PageContainer';
import type {PurchaseOrder} from '../../types/purchase';

const key = () => crypto.randomUUID();

export function PurchaseOrderListPage() {
    const nav = useNavigate();
    const action = useRef<ActionType>(null);
    const command = async (fn: () => Promise<unknown>) => {
        try {
            await fn();
            message.success('操作成功');
            action.current?.reload();
        } catch {
            message.error('操作失败，请刷新后重试');
        }
    };
    const columns: ProColumns<PurchaseOrder>[] = [{
        title: '采购单号',
        dataIndex: 'orderNo',
        render: (_, r) => <Button type="link" onClick={() => nav(`/purchases/orders/${r.id}`)}>{r.orderNo}</Button>
    }, {title: '供应商', dataIndex: 'supplierName'}, {title: '仓库', dataIndex: 'warehouseName'}, {
        title: '金额',
        dataIndex: 'totalAmount',
        align: 'right',
        render: (_, r) => <AmountText value={r.totalAmount}/>
    }, {title: '状态', dataIndex: 'status'}, {
        title: '操作',
        valueType: 'option',
        render: (_, r) => [r.status === 'DRAFT' ?
            <Permission key="submit" authority={AUTHORITIES.purchaseManage}><Popconfirm title="确认提交采购单？"
                                                                                        onConfirm={() => command(() => submitPurchaseOrder(r.id, r.version, key()))}><Button
                type="link">提交</Button></Popconfirm></Permission> : null, ['SUBMITTED', 'PARTIALLY_RECEIVED'].includes(r.status) ?
            <Permission key="receipt" authority={AUTHORITIES.purchaseManage}><Button type="link"
                                                                                     onClick={() => command(async () => nav(`/purchases/receipts/${await createReceipt(r.id)}`))}>收货</Button></Permission> : null, ['DRAFT', 'SUBMITTED'].includes(r.status) ?
            <Permission key="cancel" authority={AUTHORITIES.purchaseManage}><Popconfirm title="确认取消采购单？"
                                                                                        onConfirm={() => command(() => cancelPurchaseOrder(r.id, r.version, '用户取消', key()))}><Button
                danger type="link">取消</Button></Popconfirm></Permission> : null]
    }];
    return <PageContainer><Space direction="vertical" style={{width: '100%'}}><Permission
        authority={AUTHORITIES.purchaseManage}><Button type="primary"
                                                       onClick={() => nav('/purchases/orders/new')}>新建采购单</Button></Permission><ProTable
        actionRef={action} rowKey="id" search={false} options={false} columns={columns} request={async () => {
        const rows = await fetchPurchaseOrders();
        return {data: rows, total: rows.length, success: true};
    }}/></Space></PageContainer>;
}
