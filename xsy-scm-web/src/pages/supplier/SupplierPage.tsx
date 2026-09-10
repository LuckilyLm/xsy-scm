import type {ProColumns} from '@ant-design/pro-components';
import {ProTable} from '@ant-design/pro-components';
import {fetchSuppliers, type SupplierMaster} from '../../api/suppliers';
import {PageContainer} from '../../components/common/PageContainer';

export function SupplierPage() {
    const columns: ProColumns<SupplierMaster>[] = [{
        title: '供应商编码',
        dataIndex: 'supplierCode'
    }, {title: '供应商名称', dataIndex: 'name'}, {title: '状态', dataIndex: 'status'}];
    return <PageContainer><ProTable rowKey="id" search={false} options={false} columns={columns} request={async () => {
        const rows = await fetchSuppliers();
        return {data: rows, total: rows.length, success: true};
    }}/></PageContainer>;
}
