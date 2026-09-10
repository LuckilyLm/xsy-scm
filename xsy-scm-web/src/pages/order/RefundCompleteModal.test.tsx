import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {vi} from 'vitest';
import {completeRefund} from '../../api/afterSales';
import type {Refund} from '../../types/sales';
import {RefundCompleteModal} from './RefundCompleteModal';

vi.mock('../../api/afterSales', () => ({completeRefund: vi.fn()}));

const refund: Refund = {
    id: 8,
    version: 2,
    refundNo: 'RF202609040001',
    returnId: 6,
    orderId: 5,
    customerId: 3,
    status: 'PENDING',
    refundAmount: '12.3400',
    externalReference: null,
    completedAt: null,
};

it('submits the complete external reference through the shared refund form', async () => {
    const onCompleted = vi.fn().mockResolvedValue(undefined);
    const onOpenChange = vi.fn();
    const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
    render(<QueryClientProvider client={client}>
        <RefundCompleteModal refund={refund} onOpenChange={onOpenChange} onCompleted={onCompleted}/>
    </QueryClientProvider>);

    await userEvent.type(await screen.findByPlaceholderText('外部退款凭证（选填且不可重复）'), 'REF-2026-001');
    await userEvent.click(screen.getByRole('button', {name: '确认完成'}));

    await waitFor(() => expect(completeRefund).toHaveBeenCalledWith(
        refund.id,
        {version: refund.version, externalReference: 'REF-2026-001'},
        expect.any(String),
    ));
    expect(onCompleted).toHaveBeenCalled();
    expect(onOpenChange).toHaveBeenCalledWith(false);
});
