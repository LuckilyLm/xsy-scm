import type { ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { fetchInventories } from '../../api/inventory';
import { PageContainer } from '../../components/common/PageContainer';
import type { InventoryBalance } from '../../types/inventory';
export function InventoryPage(){const columns:ProColumns<InventoryBalance>[]=[{title:'仓库',dataIndex:'warehouseNameSnapshot'},{title:'SKU',dataIndex:'skuCodeSnapshot'},{title:'商品',dataIndex:'skuNameSnapshot'},{title:'当前数量',dataIndex:'quantity',align:'right'},{title:'单位',dataIndex:'unit'},{title:'更新时间',dataIndex:'updatedAt'}];return <PageContainer><ProTable rowKey="id" search={false} options={false} columns={columns} request={async()=>{const rows=await fetchInventories();return{data:rows,total:rows.length,success:true};}}/></PageContainer>;}
