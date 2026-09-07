import { Alert, Button, Space, Spin, message } from 'antd';
import { useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { createReceipt } from '../../api/purchases';
import { PageContainer } from '../../components/common/PageContainer';

export function PurchaseReceiptEntryPage() {
  const purchaseOrderId = Number(useParams().id);
  const navigate = useNavigate();
  const started = useRef(false);

  useEffect(() => {
    if (started.current || !Number.isSafeInteger(purchaseOrderId)) return;
    started.current = true;
    createReceipt(purchaseOrderId)
      .then((receiptId) => navigate(`/purchases/receipts/${receiptId}`, { replace: true }))
      .catch(() => message.error('创建或打开收货单失败'));
  }, [navigate, purchaseOrderId]);

  if (!Number.isSafeInteger(purchaseOrderId)) {
    return <Alert type="error" showIcon message="采购单编号无效" />;
  }

  return (
    <PageContainer>
      <Space direction="vertical" align="center" style={{ width: '100%' }}>
        <Spin />
        <span>正在创建或打开收货单…</span>
        <Button onClick={() => navigate(`/purchases/orders/${purchaseOrderId}`)}>
          返回采购单
        </Button>
      </Space>
    </PageContainer>
  );
}
