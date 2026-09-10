import {expect, test} from '@playwright/test';
import type {APIRequestContext} from '@playwright/test';
import {mutationHeaders} from './authenticated-api';

const apiBase = process.env.XSY_API_BASE_URL ?? 'http://127.0.0.1:8080/api';

interface ApiEnvelope<T> {
    code: number;
    message: string;
    data: T;
}

interface ProductListItem {
    id: number;
    version: number;
    spuCode: string;
}

interface ProductDetail {
    id: number;
    version: number;
    skus: Array<{ id: number; version: number; skuCode: string }>;
}

async function findProduct(request: APIRequestContext, code: string) {
    const response = await request.get(`${apiBase}/products`, {
        params: {page: 1, pageSize: 20, keyword: code},
    });
    expect(response.status()).toBe(200);
    const body = (await response.json()) as ApiEnvelope<{
        records: ProductListItem[];
    }>;
    expect(body.code).toBe(0);
    return body.data.records.find((record) => record.spuCode === code);
}

async function getProduct(
    request: APIRequestContext,
    id: number,
) {
    const response = await request.get(`${apiBase}/products/${id}`);
    expect(response.status()).toBe(200);
    const body = (await response.json()) as ApiEnvelope<ProductDetail>;
    expect(body.code).toBe(0);
    return body.data;
}

test('real SPU/SKU lifecycle preserves retained SKU identity', async ({page, request}) => {
    test.setTimeout(180_000);
    page.setDefaultTimeout(10_000);
    const suffix = `${Date.now()}`;
    const spuCode = `E2E-SPU-${suffix}`;
    const firstSkuCode = `E2E-SKU-A-${suffix}`;
    const secondSkuCode = `E2E-SKU-B-${suffix}`;
    const thirdSkuCode = `E2E-SKU-C-${suffix}`;
    const productName = `验收蔬果-${suffix}`;
    const consoleErrors: string[] = [];
    const pageErrors: string[] = [];
    let createdId: number | undefined;

    page.on('console', (message) => {
        if (message.type() === 'error') consoleErrors.push(message.text());
    });
    page.on('pageerror', (error) => pageErrors.push(error.message));

    try {
        await page.goto('/products');
        await expect(page.getByText('红富士苹果')).toBeVisible();

        await page.getByRole('button', {name: /新增商品/}).click();
        const createDrawer = page.getByRole('dialog', {name: '新增商品'});
        await expect(createDrawer).toBeVisible();

        await page.getByLabel('商品名称').fill(productName);
        await page.getByLabel('SPU 编码').fill(spuCode);
        await createDrawer
            .getByRole('combobox')
            .first()
            .locator('xpath=../../..')
            .click();
        await page.getByText('生鲜食材', {exact: true}).last().click();
        await page.getByText('新鲜蔬果', {exact: true}).last().click();
        await page.getByText('新鲜水果', {exact: true}).last().click();

        await page.getByLabel('SKU 编码').fill(firstSkuCode);
        await page.getByLabel('销售单位').fill('斤');
        await page.getByLabel('市场价').fill('6.5000');
        await page.getByRole('button', {name: /添加属性/}).first().click();
        await page.getByLabel('规格属性名').first().fill('包装');
        await page.getByLabel('规格属性值').first().fill('散装');

        await page.getByRole('button', {name: /新增 SKU/}).click();
        await page.getByLabel('SKU 编码').nth(1).fill(secondSkuCode);
        await page.getByLabel('规格名称').nth(1).fill('礼盒装');
        await page.getByLabel('销售单位').nth(1).fill('盒');
        await page.getByLabel('市场价').nth(1).fill('39.9000');
        await page.getByRole('button', {name: /添加属性/}).nth(1).click();
        await page.getByLabel('规格属性名').nth(1).fill('包装');
        await page.getByLabel('规格属性值').nth(1).fill('礼盒');
        await page.getByRole('button', {name: /保\s*存/}).click();
        await expect(page.getByRole('dialog', {name: '新增商品'})).toBeHidden();

        await expect.poll(() => findProduct(request, spuCode)).not.toBeUndefined();
        const listItem = await findProduct(request, spuCode);
        expect(listItem).toBeDefined();
        createdId = listItem!.id;

        await page.getByPlaceholder('商品名 / SPU编码 / SKU编码 / 条码').fill(spuCode);
        await page.getByRole('button', {name: /查\s*询/}).click();
        const productRow = page.getByRole('row').filter({hasText: productName});
        await expect(productRow).toBeVisible();
        await productRow.getByRole('button', {name: '编辑'}).click();

        await expect(page.getByLabel('SKU 编码').first()).toHaveValue(firstSkuCode);
        const beforeEdit = await getProduct(request, createdId);
        const retainedSkuId = beforeEdit.skus.find((sku) => sku.skuCode === firstSkuCode)!.id;
        await page.getByLabel('规格名称').first().fill('散装称重');
        await page.getByRole('button', {name: /新增 SKU/}).click();
        await page.getByLabel('SKU 编码').nth(2).fill(thirdSkuCode);
        await page.getByLabel('规格名称').nth(2).fill('精品装');
        await page.getByLabel('销售单位').nth(2).fill('份');
        await page.getByLabel('市场价').nth(2).fill('18.0000');
        await page.getByRole('button', {name: /添加属性/}).nth(2).click();
        await page.getByLabel('规格属性名').nth(2).fill('包装');
        await page.getByLabel('规格属性值').nth(2).fill('精品');
        await page.getByRole('button', {name: /保\s*存/}).click();
        await expect(page.getByRole('dialog', {name: '编辑商品'})).toBeHidden();

        const afterEdit = await getProduct(request, createdId);
        expect(afterEdit.skus).toHaveLength(3);
        expect(afterEdit.skus.find((sku) => sku.skuCode === firstSkuCode)?.id).toBe(retainedSkuId);

        await page.getByRole('row').filter({hasText: productName}).getByRole('button', {name: '编辑'}).click();
        await expect(page.getByLabel('SKU 编码').nth(1)).toHaveValue(secondSkuCode);
        await page.getByRole('button', {name: '删除 SKU 2'}).click();
        await page.getByRole('button', {name: '确 定'}).last().click();
        await page.getByRole('button', {name: /保\s*存/}).click();
        await expect(page.getByRole('dialog', {name: '编辑商品'})).toBeHidden();

        const afterRemoval = await getProduct(request, createdId);
        expect(afterRemoval.skus).toHaveLength(2);
        expect(afterRemoval.skus.some((sku) => sku.skuCode === secondSkuCode)).toBe(false);

        const refreshedRow = page.getByRole('row').filter({hasText: productName});
        await refreshedRow.getByRole('button', {name: '下架'}).click();
        await page.getByRole('button', {name: '确 定'}).last().click();
        await expect(refreshedRow.getByText('已下架')).toBeVisible();

        await refreshedRow.getByRole('button', {name: '删除'}).click();
        const deleteConfirmation = page
            .getByRole('tooltip')
            .filter({hasText: '确认删除该商品？'});
        await expect(deleteConfirmation).toBeVisible();
        await deleteConfirmation.getByRole('button', {name: /删\s*除/}).click();
        await expect(refreshedRow).toBeHidden();

        expect(consoleErrors, `Unexpected console errors: ${consoleErrors.join('\n')}`).toEqual([]);
        expect(pageErrors, `Unexpected page errors: ${pageErrors.join('\n')}`).toEqual([]);
    } finally {
        if (createdId !== undefined) {
            const detailResponse = await request.get(`${apiBase}/products/${createdId}`);
            if (detailResponse.ok()) {
                const body = (await detailResponse.json()) as ApiEnvelope<ProductDetail>;
                if (body.code === 0) {
                    await request.delete(`${apiBase}/products/${createdId}`, {
                        headers: await mutationHeaders(request),
                        params: {version: body.data.version},
                    });
                }
            }
        }
    }
});
