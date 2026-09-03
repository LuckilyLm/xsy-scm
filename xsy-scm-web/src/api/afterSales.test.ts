import { describe, expect, it, vi } from 'vitest';
import { approveReturn, createReturn } from './afterSales';
import { apiClient } from './http';

vi.mock('./http', () => ({ apiClient: { post: vi.fn() } }));

describe('after-sales API contracts', () => {
  it('sends requestedQuantity when creating a return', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: 7 } as never);
    await createReturn({ orderId: 1, reason: 'damaged', items: [{ orderItemId: 2, requestedQuantity: '1.0000' }] }, 'key');
    expect(apiClient.post).toHaveBeenCalledWith('/order-returns', expect.objectContaining({ items: [expect.objectContaining({ requestedQuantity: '1.0000' })] }), expect.anything());
  });

  it('sends every approval line with identity and version', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: 8 } as never);
    const payload = { version: 3, items: [
      { returnItemId: 11, version: 1, approvedQuantity: '1.0000' },
      { returnItemId: 12, version: 2, approvedQuantity: '2.0000' },
    ] };
    await approveReturn(5, payload, 'key');
    expect(apiClient.post).toHaveBeenCalledWith('/order-returns/5/approve', payload, expect.anything());
  });
});
