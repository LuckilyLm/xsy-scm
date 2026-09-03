import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { Alert, Button, Input, Select, Space } from 'antd';
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchOrders } from '../../api/orders';
import { AmountText } from '../../components/common/AmountText';
import { PageContainer } from '../../components/common/PageContainer';
import { StatusTag } from '../../components/common/StatusTag';
import type { OrderStatus, SalesOrder } from '../../types/sales';
import styles from './Sales.module.css';

export function OrderListPage() {
  const navigate = useNavigate(); const actionRef = useRef<ActionType>(null);
  const [keyword,setKeyword]=useState(''); const [status,setStatus]=useState<OrderStatus>(); const [error,setError]=useState(false);
  const columns: ProColumns<SalesOrder>[] = [
    { title:'订单号',dataIndex:'orderNo',width:190,fixed:'left',render:(_,r)=><Button type="link" onClick={()=>navigate(`/orders/${r.id}`)}>{r.orderNo}</Button> },
    { title:'客户',dataIndex:'customerName',width:180 }, { title:'类型',dataIndex:'source',width:90,render:(_,r)=>r.source==='SUPPLEMENT'?'补单':'普通' },
    { title:'商品数',width:90,align:'right',render:(_,r)=>r.items.length }, { title:'订单金额',dataIndex:'totalAmount',width:150,align:'right',render:(_,r)=><AmountText value={r.totalAmount}/> },
    { title:'状态',dataIndex:'status',width:110,align:'center',render:(_,r)=><StatusTag status={r.status}/> },
    { title:'更新时间',dataIndex:'updatedAt',width:180,render:(_,r)=>new Date(r.updatedAt).toLocaleString('zh-CN',{hour12:false}) },
    { title:'操作',valueType:'option',width:130,fixed:'right',render:(_,r)=>[<Button key="detail" type="link" onClick={()=>navigate(`/orders/${r.id}`)}>详情</Button>,...(r.status==='DRAFT'?[<Button key="edit" type="link" onClick={()=>navigate(`/orders/${r.id}/edit`)}>编辑</Button>]:[])] },
  ];
  return <PageContainer><div className={styles.toolbar}><Space><Input value={keyword} allowClear prefix={<SearchOutlined/>} placeholder="订单号 / 客户" onChange={e=>setKeyword(e.target.value)}/><Select allowClear value={status} placeholder="全部状态" options={['DRAFT','PENDING','CONFIRMED','CANCELLED'].map(value=>({value,label:{DRAFT:'草稿',PENDING:'待审核',CONFIRMED:'已确认',CANCELLED:'已取消'}[value]}))} onChange={setStatus}/><Button type="primary" onClick={()=>actionRef.current?.reload()}>查询</Button></Space><Space><Button onClick={()=>navigate('/orders/new?source=SUPPLEMENT')}>新建补单</Button><Button type="primary" icon={<PlusOutlined/>} onClick={()=>navigate('/orders/new')}>新建订单</Button></Space></div>{error?<Alert className={styles.error} type="error" showIcon message="订单加载失败" action={<Button onClick={()=>actionRef.current?.reload()}>重试</Button>}/>:null}<ProTable actionRef={actionRef} rowKey="id" search={false} options={false} columns={columns} scroll={{x:1150}} pagination={{defaultPageSize:20,showSizeChanger:true,showTotal:t=>`共 ${t} 条`}} request={async p=>{try{const data=await fetchOrders({page:p.current??1,pageSize:p.pageSize??20,keyword:keyword.trim()||undefined,status});setError(false);return {data:data.records,total:data.total,success:true};}catch{setError(true);return {data:[],total:0,success:true};}}}/></PageContainer>;
}
