import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {vi} from 'vitest';
import {
    createProduct,
    fetchCategoryTree,
    fetchProduct,
    updateProduct,
} from '../../api/products';
import type {ProductDetail} from '../../types/product';
import {ProductDrawer} from './ProductDrawer';

vi.mock('../../api/products', () => ({
    createProduct: vi.fn(),
    fetchProduct: vi.fn(),
    fetchCategoryTree: vi.fn(),
    updateProduct: vi.fn(),
}));

const detail: ProductDetail = {
    id: 1,
    version: 3,
    spuCode: 'VEG-TOMATO',
    name: '西红柿',
    alias: '番茄',
    categoryId: 30,
    categoryPath: '新鲜蔬菜/茄果类',
    description: '新鲜供应',
    status: 'ON_SHELF',
    createdAt: '2026-09-01T10:00:00+08:00',
    updatedAt: '2026-09-02T10:00:00+08:00',
    skus: [
        {
            id: 11,
            version: 2,
            skuCode: 'VEG-TOMATO-JIN',
            barcode: null,
            specName: '散装',
            specValues: {包装: '散装'},
            saleUnit: '斤',
            productType: 'NON_STANDARD',
            marketPrice: '6.5000',
            status: 'ON_SHELF',
            defaultSku: true,
            sortOrder: 0,
        },
    ],
};

function renderDrawer(productId: number | null = null) {
    const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
    const onOpenChange = vi.fn();
    const onSaved = vi.fn();
    render(
        <QueryClientProvider client={client}>
            <ProductDrawer
                open
                productId={productId}
                onOpenChange={onOpenChange}
                onSaved={onSaved}
            />
        </QueryClientProvider>,
    );
    return {onOpenChange, onSaved};
}

beforeEach(() => {
    vi.mocked(fetchCategoryTree).mockResolvedValue([]);
    vi.mocked(fetchProduct).mockResolvedValue(detail);
    vi.mocked(createProduct).mockResolvedValue(1);
    vi.mocked(updateProduct).mockResolvedValue(undefined);
});

it('starts with one default SKU and can add another SKU', async () => {
    renderDrawer();

    expect(await screen.findAllByLabelText('SKU 编码')).toHaveLength(1);
    expect(screen.getByRole('radio', {name: '默认 SKU 1'})).toBeChecked();

    fireEvent.click(screen.getByRole('button', {name: /新增 SKU/}));

    expect(screen.getAllByLabelText('SKU 编码')).toHaveLength(2);
    fireEvent.click(screen.getByRole('radio', {name: '默认 SKU 2'}));
    expect(screen.getByRole('radio', {name: '默认 SKU 2'})).toBeChecked();
    expect(screen.getByRole('radio', {name: '默认 SKU 1'})).not.toBeChecked();
});

it('preserves existing SKU identity when submitting an edit', async () => {
    const {onOpenChange, onSaved} = renderDrawer(1);

    expect(await screen.findByDisplayValue('VEG-TOMATO-JIN')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: /保\s*存/}));

    await waitFor(() =>
        expect(updateProduct).toHaveBeenCalledWith(
            1,
            expect.objectContaining({
                version: 3,
                skus: [expect.objectContaining({id: 11, version: 2})],
            }),
        ),
    );
    expect(onSaved).toHaveBeenCalledOnce();
    expect(onOpenChange).toHaveBeenCalledWith(false);
});

it('keeps save disabled while the mutation is pending', async () => {
    let resolveUpdate: (() => void) | undefined;
    vi.mocked(updateProduct).mockImplementation(
        () => new Promise<void>((resolve) => (resolveUpdate = resolve)),
    );
    renderDrawer(1);

    await screen.findByDisplayValue('VEG-TOMATO-JIN');
    const save = screen.getByRole('button', {name: /保\s*存/});
    fireEvent.click(save);

    await waitFor(() => expect(save).toBeDisabled());
    resolveUpdate?.();
});
