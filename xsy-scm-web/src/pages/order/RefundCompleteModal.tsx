import { ModalForm, ProFormText } from '@ant-design/pro-components';
import { Alert } from 'antd';
import { useEffect, useState } from 'react';
import { completeRefund } from '../../api/afterSales';
import { ApiError } from '../../api/http';
import type { Refund } from '../../types/sales';

interface Props {
  refund: Refund | null;
  onOpenChange: (open: boolean) => void;
  onCompleted: () => Promise<unknown>;
}

interface RefundCompleteFormValues {
  externalReference?: string;
}

export function RefundCompleteModal({ refund, onOpenChange, onCompleted }: Props) {
  const [error, setError] = useState<string | null>(null);

  useEffect(() => setError(null), [refund?.id]);

  return <ModalForm<RefundCompleteFormValues>
    key={refund?.id ?? 'closed'}
    open={refund !== null}
    title={refund ? `完成退款 ${refund.refundNo}` : '完成退款'}
    initialValues={{ externalReference: refund?.externalReference ?? '' }}
    onOpenChange={onOpenChange}
    modalProps={{ destroyOnHidden: true }}
    submitter={{ searchConfig: { submitText: '确认完成', resetText: '取消' } }}
    onFinish={async (values) => {
      if (!refund) return false;
      setError(null);
      try {
        await completeRefund(refund.id, {
          version: refund.version,
          externalReference: values.externalReference?.trim() || undefined,
        }, crypto.randomUUID());
        await onCompleted();
        onOpenChange(false);
        return true;
      } catch (cause) {
        setError(cause instanceof ApiError && cause.status === 409
          ? '退款已被处理或外部凭证重复，请重新加载'
          : cause instanceof Error ? cause.message : '退款操作失败');
        return false;
      }
    }}
  >
    {error ? <Alert type="error" showIcon message={error} /> : null}
    <ProFormText name="externalReference" label="外部退款凭证" placeholder="外部退款凭证（选填且不可重复）" fieldProps={{ maxLength: 128 }} />
  </ModalForm>;
}
