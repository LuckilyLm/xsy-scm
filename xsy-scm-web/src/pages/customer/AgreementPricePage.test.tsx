import {screen} from '@testing-library/react';
import {vi} from 'vitest';
import {fetchAgreementPrices, fetchCustomers} from '../../api/customers';
import {ALL_BUSINESS_PERMISSIONS, renderWithProviders} from '../../test/renderWithProviders';
import {AgreementPricePage} from './AgreementPricePage';

vi.mock('../../api/customers', () => ({
    fetchAgreementPrices: vi.fn(),
    fetchCustomers: vi.fn(),
    deleteAgreementPrice: vi.fn()
}));
vi.mock('../../api/auth', () => ({
    fetchCsrf: vi.fn().mockResolvedValue({
        headerName: 'X-XSRF-TOKEN',
        parameterName: '_csrf'
    }), fetchCurrentUser: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn()
}));
vi.mock('./AgreementPriceDrawer', () => ({AgreementPriceDrawer: () => null}));

function renderPage() {
    return renderWithProviders(<AgreementPricePage/>, {permissions: ALL_BUSINESS_PERMISSIONS});
}

beforeEach(() => {
    vi.mocked(fetchAgreementPrices).mockResolvedValue({
        records: [{
            id: 5,
            version: 1,
            customerId: 1,
            skuId: 22,
            unitPrice: '6.5000',
            effectiveFrom: '2026-09-03T00:00:00Z',
            effectiveTo: null
        }], page: 1, pageSize: 20, total: 1
    });
    vi.mocked(fetchCustomers).mockResolvedValue({
        records: [{
            id: 1,
            version: 0,
            customerCode: 'C001',
            name: '春风食堂',
            customerTypeId: 1,
            customerTypeName: '餐饮客户',
            status: 'ENABLED',
            visibilityPolicy: 'ALL_ENABLED',
            updatedAt: '2026-09-03T00:00:00Z'
        }], page: 1, pageSize: 100, total: 1
    });
});
it('renders agreement decimal and open-ended period', async () => {
    renderPage();
    expect(await screen.findByText('春风食堂')).toBeInTheDocument();
    expect(screen.getByText('¥ 6.5000')).toBeInTheDocument();
    expect(screen.getByText('长期有效')).toBeInTheDocument();
});
it('shows retry when loading fails', async () => {
    vi.mocked(fetchAgreementPrices).mockRejectedValueOnce(new Error('offline'));
    renderPage();
    expect(await screen.findByText('协议价数据加载失败')).toBeInTheDocument();
});
