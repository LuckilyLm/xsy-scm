import { ArrowLeftOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Descriptions, Space, Spin, Typography } from 'antd';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { fetchRefund } from '../../api/afterSales';
import { PageContainer } from '../../components/common/PageContainer';
import { StatusTag } from '../../components/common/StatusTag';
import { RefundCompleteModal } from './RefundCompleteModal';
import styles from './Sales.module.css';

export function RefundDetailPage() {
  const { id } = useParams();
  const refundId = Number(id);
  const navigate = useNavigate();
  const [completeOpen, setCompleteOpen] = useState(false);
  const query = useQuery({ queryKey: ['refund', refundId], queryFn: () => fetchRefund(refundId) });
  const data = query.data;

  if (query.isLoading) return <PageContainer><Spin /></PageContainer>;
  if (query.isError || !data) return <PageContainer><Alert type="error" message="退款详情加载失败" action={<Button onClick={() => query.refetch()}>重试</Button>} /></PageContainer>;

  return <PageContainer>
    <div className={styles.header}>
      <Space><Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/order-refunds')}>返回列表</Button><Typography.Title level={4} className={styles.title}>{data.refundNo}</Typography.Title><StatusTag status={data.status} /></Space>
      {data.status === 'PENDING' ? <Button type="primary" onClick={() => setCompleteOpen(true)}>完成退款</Button> : null}
    </div>
    <Descriptions bordered size="small" column={2} items={[
      { key: 'return', label: '退货单 ID', children: <Button type="link" onClick={() => navigate(`/order-returns/${data.returnId}`)}>{data.returnId}</Button> },
      { key: 'amount', label: '退款金额', children: `¥ ${data.refundAmount}` },
      { key: 'external', label: '外部退款凭证', children: data.externalReference || '--' },
      { key: 'version', label: '版本', children: data.version },
    ]} />
    <RefundCompleteModal refund={completeOpen ? data : null} onOpenChange={setCompleteOpen} onCompleted={query.refetch} />
  </PageContainer>;
}
