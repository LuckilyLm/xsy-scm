import type {ProColumns} from '@ant-design/pro-components';
import {ProTable} from '@ant-design/pro-components';
import {Button} from 'antd';
import {useNavigate} from 'react-router-dom';
import {fetchReceipts} from '../../api/purchases';
import {PageContainer} from '../../components/common/PageContainer';
import type {PurchaseReceipt} from '../../types/purchase';

export function PurchaseReceiptListPage() {
    const nav = useNavigate();
    const columns: ProColumns<PurchaseReceipt>[] = [{
        title: '收货单号',
        dataIndex: 'receiptNo',
        render: (_, r) => <Button type="link" onClick={() => nav(`/purchases/receipts/${r.id}`)}>{r.receiptNo}</Button>
    }, {title: '采购单号', dataIndex: 'purchaseOrderNoSnapshot'}, {
        title: '仓库',
        dataIndex: 'warehouseNameSnapshot'
    }, {title: '状态', dataIndex: 'status'}, {title: '确认时间', dataIndex: 'confirmedAt'}];
    return <PageContainer><ProTable rowKey="id" search={false} options={false} columns={columns} request={async () => {
        const rows = await fetchReceipts();
        return {data: rows, total: rows.length, success: true};
    }}/></PageContainer>;
}
