import { fireEvent, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import { fetchCategoryTree, fetchProducts } from '../../api/products';
import { ALL_BUSINESS_PERMISSIONS, renderWithProviders } from '../../test/renderWithProviders';
import type { ProductSummary } from '../../types/product';
import { ProductPage } from './ProductPage';

vi.mock('../../api/products', () => ({
  fetchProducts: vi.fn(),
  fetchCategoryTree: vi.fn(),
  deleteProduct: vi.fn(),
  updateProductStatus: vi.fn(),
}));

vi.mock('../../api/auth', () => ({
  fetchCsrf: vi.fn().mockResolvedValue({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }),
  fetchCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  changePassword: vi.fn(),
}));

const products: ProductSummary[] = [
  {
    id: 1,
    version: 0,
    spuCode: 'VEG-TOMATO',
    name: '西红柿',
    alias: '番茄',
    categoryId: 30,
    categoryPath: '新鲜蔬菜/茄果类',
    defaultSku: {
      id: 11,
      version: 0,
      skuCode: 'VEG-TOMATO-JIN',
      barcode: null,
      specName: '散装',
      specValues: { 包装: '散装' },
      saleUnit: '斤',
      productType: 'NON_STANDARD',
      marketPrice: '6.5000',
      status: 'ON_SHELF',
      defaultSku: true,
      sortOrder: 0,
    },
    skuCount: 2,
    minMarketPrice: '6.5000',
    maxMarketPrice: '12.0000',
    status: 'ON_SHELF',
    updatedAt: '2026-09-02T10:00:00+08:00',
    skus: [
      {
        id: 11,
        version: 0,
        skuCode: 'VEG-TOMATO-JIN',
        barcode: null,
        specName: '散装',
        specValues: { 包装: '散装' },
        saleUnit: '斤',
        productType: 'NON_STANDARD',
        marketPrice: '6.5000',
        status: 'ON_SHELF',
        defaultSku: true,
        sortOrder: 0,
      },
      {
        id: 12,
        version: 0,
        skuCode: 'VEG-TOMATO-BOX',
        barcode: '6900000000012',
        specName: '箱装',
        specValues: { 包装: '箱装' },
        saleUnit: '箱',
        productType: 'STANDARD',
        marketPrice: '12.0000',
        status: 'ON_SHELF',
        defaultSku: false,
        sortOrder: 1,
      },
    ],
  },
  {
    id: 2,
    version: 1,
    spuCode: 'VEG-POTATO',
    name: '红皮土豆',
    alias: null,
    categoryId: 31,
    categoryPath: '新鲜蔬菜/根茎类',
    defaultSku: {
      id: 21,
      version: 0,
      skuCode: 'VEG-POTATO-JIN',
      barcode: null,
      specName: '默认规格',
      specValues: {},
      saleUnit: '斤',
      productType: 'NON_STANDARD',
      marketPrice: '1.8000',
      status: 'OFF_SHELF',
      defaultSku: true,
      sortOrder: 0,
    },
    skuCount: 1,
    minMarketPrice: '1.8000',
    maxMarketPrice: '1.8000',
    status: 'OFF_SHELF',
    updatedAt: '2026-09-01T10:00:00+08:00',
    skus: [],
  },
];

function renderPage() {
  return renderWithProviders(<ProductPage />, { permissions: ALL_BUSINESS_PERMISSIONS });
}

beforeEach(() => {
  vi.mocked(fetchCategoryTree).mockResolvedValue([]);
  vi.mocked(fetchProducts).mockResolvedValue({ records: products, page: 1, pageSize: 20, total: 2 });
});

it('renders one main row per SPU and expands SKU details', async () => {
  renderPage();

  expect(await screen.findByText('西红柿')).toBeInTheDocument();
  expect(screen.getByText('红皮土豆')).toBeInTheDocument();
  expect(screen.queryByText('VEG-TOMATO-BOX')).not.toBeInTheDocument();

  fireEvent.click(screen.getByRole('button', { name: '展开西红柿的 SKU' }));

  expect(await screen.findByText('VEG-TOMATO-BOX')).toBeInTheDocument();
});

it('maps visible filters to backend query names', async () => {
  renderPage();
  await screen.findByText('西红柿');

  fireEvent.change(screen.getByPlaceholderText('商品名 / SPU编码 / SKU编码 / 条码'), {
    target: { value: '番茄' },
  });
  fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));

  await waitFor(() =>
    expect(fetchProducts).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1, pageSize: 20, keyword: '番茄' }),
    ),
  );
});

it('renders retry after a request failure', async () => {
  vi.mocked(fetchProducts).mockRejectedValueOnce(new Error('offline'));
  renderPage();

  expect(await screen.findByText('商品数据加载失败')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '重新加载' })).toBeInTheDocument();
});

it('asks for confirmation before delete', async () => {
  renderPage();
  await screen.findByText('西红柿');

  fireEvent.click(screen.getAllByRole('button', { name: '删除' })[0]);

  expect(await screen.findByText('确认删除该商品？')).toBeInTheDocument();
});

it('hides write actions when the user only holds product.read', async () => {
  renderWithProviders(<ProductPage />, { permissions: ['product.read'] });
  await screen.findByText('西红柿');

  expect(screen.queryByRole('button', { name: '新增商品' })).not.toBeInTheDocument();
  expect(screen.queryAllByRole('button', { name: '编辑' })).toHaveLength(0);
  expect(screen.queryAllByRole('button', { name: '删除' })).toHaveLength(0);
});
