/*
  配送类 E2E 共用的夹具（自建，不借候选池）。

  P1 分拣落地后，「已确认订单」不再自动等于「可配送候选」——候选要求订单每条有效明细行都被
  **已完成**的分拣任务覆盖（docs/decisions.md「P1 分拣管理裁决」第 11 条与补充第 18 条）。
  因此配送用例不能再从共享的候选池里挑单：池子里留下的历史订单永远不会自己长出分拣事实。

  本模块把这条前置链一次做全：三级分类 → 非标品 SKU → 已定位客户 → 创建/提交/录实重/确认订单
  → 建分拣任务 → 逐行录入 → 完成任务。每一步都走真实命令接口，因为只有真实链路才能证明
  「资格确实由任务状态决定」，而不是夹具用 SQL 或状态字段糊出来的。
*/
import {type APIRequestContext} from '@playwright/test';
import {randomUUID} from 'node:crypto';

type Row = Record<string, any>;

/** 失败时带出接口路径与服务端 msg，避免只看到一个没有上下文的 code != 0。 */
async function call<T = Row>(client: APIRequestContext, method: 'get' | 'post', path: string,
                             data?: unknown): Promise<T> {
    const response = method === 'get'
        ? await client.get(path)
        : await client[method](path, {data, headers: {'Idempotency-Key': randomUUID()}});
    const body = await response.json();
    if (body.code !== 0) throw new Error(`${method.toUpperCase()} ${path} 失败：${body.code} ${body.msg}`);
    return body.data as T;
}

/** 商品必须挂三级分类，因此分类链先建满。 */
async function createCategoryChain(client: APIRequestContext, runTag: string): Promise<string> {
    let parentId: string | null = null;
    for (const level of [1, 2, 3]) {
        parentId = String(await call(client, 'post', '/scm/product/category/add', {
            parentId,
            categoryCode: `${runTag}-L${level}`.toUpperCase(),
            name: `${runTag}分类${level}`,
            sortOrder: 0,
            status: 'ENABLED',
        }));
    }
    return parentId as string;
}

/** 一个非标品 SKU：非标才会走「录实重 → 确认」这条真实链路。 */
export async function createSku(client: APIRequestContext, runTag: string, tag: string): Promise<string> {
    const categoryId = await createCategoryChain(client, `${runTag}${tag}`);
    const code = `${runTag}-${tag}`.toUpperCase();
    await call(client, 'post', '/scm/product/add', {
        spuCode: code,
        name: `${runTag}商品${tag}`,
        categoryId,
        status: 'ON_SHELF',
        images: [],
        skuList: [{
            skuCode: code, specName: `散装${tag}`, specValues: {规格: '散装'}, saleUnit: 'kg',
            productType: 'NON_STANDARD', marketPrice: '3.5000', status: 'ON_SHELF', defaultFlag: true, sortOrder: 0,
        }],
    });
    const options = (await call(client, 'post', '/scm/product/sku/option-list', {keyword: code, limit: 10})).options;
    const sku = options.find((o: Row) => o.skuCode === code);
    if (!sku) throw new Error(`商品 ${code} 的 SKU 选项没查到，无法继续造订单`);
    return String(sku.skuId);
}

/** 已定位（带 GCJ-02 坐标）的客户 —— 停靠点定位与候选资格都要求坐标完整。 */
export async function createLocatedCustomer(client: APIRequestContext, runTag: string, tag: string,
                                            address: string): Promise<string> {
    const typeData: any = await call(client, 'post', '/scm/customer/type/option/list', {});
    const typeList = Array.isArray(typeData) ? typeData : (typeData.options ?? typeData.list ?? []);
    if (!typeList.length) throw new Error('开发库没有客户类型，无法建客户夹具');
    const customerTypeId = Number(typeList[0].typeId ?? typeList[0].customerTypeId ?? typeList[0].id);
    const id = String(await call(client, 'post', '/scm/customer/add', {
        customerCode: `${runTag}-C${tag}`.toUpperCase(),
        name: `${runTag}客户${tag}`,
        customerTypeId,
        settleMode: 'INDEPENDENT',
        contactName: '配送夹具联系人',
        contactPhone: '13800000000',
        address,
        provinceCode: 330000, provinceName: '浙江省',
        cityCode: 330100, cityName: '杭州市',
        districtCode: 330106, districtName: '西湖区',
        longitude: '113.94000000', latitude: '22.54000000', geomCrs: 'GCJ02',
    }));
    const created = await call<Row>(client, 'get', `/scm/customer/detail/${id}`);
    await call(client, 'post', '/scm/customer/updateStatus', {
        customerId: id, version: created.version, status: 'COOPERATING',
    });
    return id;
}

/**
 * 造一张「可进配送候选」的订单：已确认 **且** 每条明细都落在已完成的分拣任务里。
 *
 * @returns `orderId` / `customerId` / `orderNo`，够线路侧用例挂单与断言用
 */
export async function createDeliveryReadyOrder(client: APIRequestContext, input: {
    runTag: string;
    customerId: string;
    skuId: string;
    address: string;
    warehouseId: number | string;
    quantity?: string;
    /**
     * 少拣夹具用：把某条明细的实发量改成这个值并带上结果码与原因。
     * 省略就是「全量正常」，与 P1 交付时的默认口径一致。
     */
    sortedQuantity?: string;
    sortedResult?: 'NORMAL' | 'SHORT' | 'OUT_OF_STOCK' | 'OVER';
    sortReason?: string;
}): Promise<{ orderId: string; customerId: string; orderNo: string; sortingTaskId: string }> {
    const quantity = input.quantity ?? '1.0000';
    let order = await call<Row>(client, 'post', '/scm/order/create', {
        customerId: input.customerId,
        orderSource: 'ADMIN',
        address: {receiverName: '配送夹具', receiverPhone: '13800000000', address: input.address},
        remark: input.runTag,
        items: [{skuId: input.skuId, orderedQuantity: quantity, manualPriceOverride: false}],
    });
    order = await call<Row>(client, 'post', '/scm/order/submit', {orderId: order.orderId, version: order.version});
    const line = order.items[0];
    await call(client, 'post', '/scm/order/item/actual-quantity', {
        orderId: order.orderId, itemId: line.itemId, version: line.version,
        actualQuantity: quantity, reason: `${input.runTag} 夹具实重`,
    });
    const detail = await call<Row>(client, 'get', `/scm/order/detail/${order.orderId}`);
    const confirmed = await call<Row>(client, 'post', '/scm/order/confirm', {
        orderId: order.orderId, version: detail.version,
    });
    const sortingTaskId = await completeSorting(client, detail, input.warehouseId, input.runTag, input);
    return {orderId: String(order.orderId), customerId: String(input.customerId),
        orderNo: String(confirmed.orderNo ?? detail.orderNo), sortingTaskId};
}

/**
 * 把订单每条有效明细做到 COMPLETED。走真实分拣命令链而不是直接改状态：
 * 「按任务状态判定资格」这条口径本身也要被跑到。
 */
async function completeSorting(client: APIRequestContext, orderDetail: Row, warehouseId: number | string,
                               runTag: string,
                               override: {sortedQuantity?: string; sortedResult?: string; sortReason?: string} = {}) {
    const itemIds = (orderDetail.items as Row[]).map(i => Number(i.itemId));
    const created = await call<Row>(client, 'post', '/scm/sorting/tasks', {
        warehouseId: Number(warehouseId),
        salesOrderItemIds: itemIds,
        remark: `${runTag} 配送前置分拣`,
    });
    // 少拣要改「已完成后重开再录入」才是真实链路；直接在首次录入里给小量同样成立，
    // 因为 COMPLETED 只要求每行都有结果与量，不要求等于计划量。
    const entries = (created.items as Row[]).map(line => ({
        id: line.id,
        version: line.version,
        sortedQuantity: override.sortedQuantity ?? line.plannedQuantitySnapshot,
        result: override.sortedResult ?? 'NORMAL',
        ...(override.sortReason ? {reason: override.sortReason} : {}),
    }));
    await call(client, 'post', `/scm/sorting/tasks/${created.task.id}/entry`, {items: entries});
    const ready = await call<Row>(client, 'get', `/scm/sorting/tasks/${created.task.id}`);
    await call(client, 'post', `/scm/sorting/tasks/${created.task.id}/complete`, {version: ready.task.version});
    return String(created.task.id);
}
