import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen, waitFor} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {AuthProvider} from '../../auth/AuthProvider';
import {AdminLayout} from './index';

vi.mock('../../api/auth', () => ({
    fetchCsrf: vi.fn().mockResolvedValue({headerName: 'X-XSRF-TOKEN', parameterName: '_csrf'}),
    fetchCurrentUser: vi.fn().mockResolvedValue({
        id: 1,
        username: 'admin',
        displayName: '系统管理员',
        administrator: false,
        mustChangePassword: false,
        roles: [],
        // 只授予商品读取，用于验证导航按权限裁剪。
        permissions: ['product.read'],
        version: 0,
    }),
    login: vi.fn(),
    logout: vi.fn(),
    changePassword: vi.fn(),
}));

function renderLayout(initialPath = '/products') {
    const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
    return render(
        <QueryClientProvider client={queryClient}>
            <AuthProvider>
                <MemoryRouter initialEntries={[initialPath]}>
                    <AdminLayout/>
                </MemoryRouter>
            </AuthProvider>
        </QueryClientProvider>,
    );
}

describe('AdminLayout', () => {
    it('renders only navigation permitted for the current user', async () => {
        renderLayout();

        expect(await screen.findByRole('navigation', {name: '一级导航'})).toBeInTheDocument();
        await waitFor(() => {
            expect(screen.getByRole('navigation', {name: '商品二级导航'})).toBeInTheDocument();
        });
        expect(screen.getByText('商品档案')).toBeInTheDocument();
        expect(screen.queryByText('销售订单')).not.toBeInTheDocument();
    });

    it('shows the signed-in user instead of a placeholder name', async () => {
        renderLayout();

        expect(await screen.findByText('系统管理员')).toBeInTheDocument();
    });
});
