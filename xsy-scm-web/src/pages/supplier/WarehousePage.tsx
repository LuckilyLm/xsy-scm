import type { ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { fetchWarehouses,type WarehouseMaster } from '../../api/suppliers';
import { PageContainer } from '../../components/common/PageContainer';
export function WarehousePage(){const columns:ProColumns<WarehouseMaster>[]=[{title:'仓库编码',dataIndex:'warehouseCode'},{title:'仓库名称',dataIndex:'name'},{title:'地址',dataIndex:'address'},{title:'状态',dataIndex:'status'}];return <PageContainer><ProTable rowKey="id" search={false} options={false} columns={columns} request={async()=>{const rows=await fetchWarehouses();return{data:rows,total:rows.length,success:true};}}/></PageContainer>;}
