import { fireEvent, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import { fetchCustomers, fetchCustomerTypes } from '../../api/customers';
import { ALL_BUSINESS_PERMISSIONS, renderWithProviders } from '../../test/renderWithProviders';
import { CustomerPage } from './CustomerPage';

vi.mock('../../api/customers', () => ({ fetchCustomers: vi.fn(), fetchCustomerTypes: vi.fn(), deleteCustomer: vi.fn(), updateCustomerStatus: vi.fn(), fetchCustomer: vi.fn(), createCustomer: vi.fn(), updateCustomer: vi.fn(), fetchSkuCatalog: vi.fn() }));
vi.mock('../../api/auth', () => ({ fetchCsrf: vi.fn().mockResolvedValue({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }), fetchCurrentUser: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() }));
vi.mock('./CustomerDrawer', () => ({ CustomerDrawer: () => null }));
function renderPage() { return renderWithProviders(<CustomerPage />, { permissions: ALL_BUSINESS_PERMISSIONS }); }
beforeEach(() => { vi.mocked(fetchCustomerTypes).mockResolvedValue([]); vi.mocked(fetchCustomers).mockResolvedValue({ records: [{ id: 1, version: 2, customerCode: 'C001', name: '春风食堂', customerTypeId: 3, customerTypeName: '餐饮客户', status: 'ENABLED', visibilityPolicy: 'ALLOWLIST', updatedAt: '2026-09-03T00:00:00Z' }], page: 1, pageSize: 20, total: 1 }); });
it('loads customers and maps keyword filter', async () => { renderPage(); expect(await screen.findByText('春风食堂')).toBeInTheDocument(); fireEvent.change(screen.getByPlaceholderText('客户名称 / 客户编码'), { target: { value: '春风' } }); fireEvent.click(screen.getByRole('button', { name: /查\s*询/ })); await waitFor(() => expect(fetchCustomers).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '春风', page: 1, pageSize: 20 }))); });
it('shows retry when loading fails', async () => { vi.mocked(fetchCustomers).mockRejectedValueOnce(new Error('offline')); renderPage(); expect(await screen.findByText('客户数据加载失败')).toBeInTheDocument(); expect(screen.getByRole('button', { name: '重新加载' })).toBeInTheDocument(); });
