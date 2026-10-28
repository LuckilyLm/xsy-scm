/*
 * 本文件由按域拆分的 E2E 用例合并而成：每个 test.describe 对应一份原文件，
 * 块内状态与钩子互不共享，执行顺序按来源文件排列。
 */
import {test, expect} from './scm-test-base';
import {accessibleName, apiClient, authenticate, login, provisionTempAccounts, type TempAccounts} from './scm-e2e-account';
import {randomUUID} from 'node:crypto';
import {createDeliveryReadyOrder, createLocatedCustomer, createSku} from './scm-delivery-fixtures';
import {type APIRequestContext, type Locator, type Page} from '@playwright/test';

// ---------------------------------------------------------------- 来源：e2e/scm-delivery.spec.ts
test.describe('scm-delivery', () => {
  /**
   * 物流配送 E2E：仓库与客户定位 → 司机车辆主档 → 建线路、组单、定位、确认规划、取消释放。
   * 确认规划不写库存；夹具自建，收尾取消线路。地图选点与地址搜索不在本用例断言。
   */
  type Row = Record<string, any>;

  test.describe.configure({mode: 'serial'});

  const accounts: TempAccounts = provisionTempAccounts('w5');
  const name = `W5${Date.now().toString(36).toUpperCase()}`;
  // 业务日界一律 Asia/Shanghai；用 toISOString() 会在 UTC/本地跨天时拿到「昨天」，
  // 导致候选订单被日期过滤器静默清空。
  const today = new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Shanghai'}).format(new Date());

  const START_LNG = '113.93000000';
  const START_LAT = '22.54000000';
  const STOP_LNG = '113.94600000';
  const STOP_LAT = '22.55300000';

  let api: Awaited<ReturnType<typeof apiClient>>;
  /**
   * 整轮复用同一个管理员令牌：Sa-Token 同账号不并发，测试内再次 login 会把
   * beforeAll 建的 API 客户端顶成「未登录」（30007），表现为随机红。
   */
  let adminToken = '';
  let warehouse = {} as Row;
  let warehouseName = '';
  const driverCode = `${name}-D`;
  const vehicleNo = `${name}-V1`;
  let driverId = '';
  let vehicleId = '';
  let routeId = '';
  const orders: {id: string; no: string; customerId: string}[] = [];
  let geolessOrderId = '';
  let locatedCustomerIdA = '';
  let geolessCustomerId = '';

  async function ok<T = Row>(
    client: typeof api,
    method: 'get' | 'post' | 'put' | 'delete',
    path: string,
    data?: unknown,
    key = randomUUID()
  ): Promise<T> {
    const response = method === 'get' ? await client.get(path) : await client[method](path, {data, headers: {'Idempotency-Key': key}});
    const body = await response.json();
    expect(body.code, `${method.toUpperCase()} ${path}: ${body.msg}`).toBe(0);
    return body.data as T;
  }

  const get = <T = Row>(path: string, client: typeof api = api) => ok<T>(client, 'get', path);
  const post = <T = Row>(path: string, data?: unknown, client: typeof api = api) => ok<T>(client, 'post', path, data);
  const put = <T = Row>(path: string, data?: unknown, client: typeof api = api) => ok<T>(client, 'put', path, data);

  /** 负向入口：返回原始信封，不因为 code != 0 就先把用例判红。 */
  async function envelope(path: string, data: unknown, method: 'post' | 'put' | 'delete' = 'post', key = randomUUID()) {
    const response =
      method === 'delete'
        ? await api.delete(path, {data, headers: {'Idempotency-Key': key}})
        : await api[method](path, {data, headers: {'Idempotency-Key': key}});
    const body = await response.json().catch(() => ({}) as Row);
    return {status: response.status(), ok: body.ok === true, code: body.code, msg: body.msg ?? ''};
  }

  async function route(): Promise<Row> {
    return (await get<Row>(`/scm/delivery/routes/${routeId}`)).route as Row;
  }

  async function routeVersion(): Promise<number> {
    return Number((await route()).version);
  }

  /** 线路详情一次取回头、停靠点与订单三层，覆盖度与合并结果都从这里判。 */
  async function detail(): Promise<Row> {
    return await get<Row>(`/scm/delivery/routes/${routeId}`);
  }

  function button(scope: any, text: string) {
    return scope.getByRole('button', {name: accessibleName(text)});
  }

  /** antd 的 label 与 input 之间没有稳定的 for 关联，按表单项文本定位再填内部控件。 */
  function field(scope: any, label: string) {
    return scope.locator('.ant-form-item').filter({hasText: label}).first();
  }

  async function fillField(scope: any, label: string, value: string) {
    const control = field(scope, label).locator('input, textarea').first();
    await control.fill(value);
    await control.press('Enter').catch((): undefined => undefined);
  }

  async function pickOption(page: any, scope: any, label: string, optionText: string) {
    await field(scope, label).locator('.ant-select-selector').first().click();
    await page.locator('.ant-select-dropdown:visible').locator('.ant-select-item-option-content').filter({hasText: optionText}).first().click();
  }

  test.beforeAll(async () => {
    adminToken = await login(accounts, accounts.admin);
    api = await apiClient(adminToken);

    // L0：给启用仓库补齐定位。列表接口不返回坐标，因此必须按明细回读才算落库。
    const warehouses = await get<Row[]>('/scm/delivery/options/warehouses');
    expect(warehouses.length, '开发库没有启用仓库，无法验收配送 L0').toBeGreaterThan(0);
    const target = warehouses.find((w) => w.warehouseCode === 'WH001') ?? warehouses[0];
    warehouseName = String(target.name);
    const fresh = await get<Row>(`/scm/warehouse/detail/${target.id}`);
    await ok(api, 'post', '/scm/warehouse/update', {
      id: target.id,
      version: fresh.version,
      warehouseCode: target.warehouseCode,
      name: target.name,
      address: fresh.address ?? '验收路 1 号',
      longitude: START_LNG,
      latitude: START_LAT,
      geomCrs: 'GCJ02',
      remark: name,
    });
    warehouse = await get<Row>(`/scm/warehouse/detail/${target.id}`);
    warehouse.id = String(target.id);

    let parentId: string | null = null;
    for (const level of [1, 2, 3]) {
      parentId = String(
        await post('/scm/product/category/add', {
          parentId,
          categoryCode: `${name}-L${level}`.toUpperCase(),
          name: `${name}分类${level}`,
          sortOrder: 0,
          status: 'ENABLED',
        })
      );
    }
    const categoryId = parentId as string;
    const skuIds: string[] = [];
    for (const tag of ['A', 'B']) {
      const code = `${name}-${tag}`.toUpperCase();
      const spuId = await post('/scm/product/add', {
        spuCode: code,
        name: `${name}商品${tag}`,
        categoryId,
        status: 'ON_SHELF',
        images: [],
        skuList: [
          {
            skuCode: code,
            specName: `散装${tag}`,
            specValues: {规格: '散装'},
            saleUnit: 'kg',
            productType: 'NON_STANDARD',
            marketPrice: '3.5000',
            status: 'ON_SHELF',
            defaultFlag: true,
            sortOrder: 0,
          },
        ],
      });
      // V147 起规格编码由后端发号：回读详情取真实 skuId
      const detail = await get<Row>(`/scm/product/detail/${spuId}`);
      skuIds.push(String((detail.skuList ?? [])[0].skuId));
    }

    const typeData: any = await post('/scm/customer/type/option/list', {});
    const typeList = Array.isArray(typeData) ? typeData : typeData.options ?? typeData.list ?? [];
    expect(typeList.length, '开发库没有客户类型，无法建客户夹具').toBeGreaterThan(0);
    const rawTypeId = typeList[0].typeId ?? typeList[0].customerTypeId ?? typeList[0].id;
    // 夹具把不存在的字段当数字传，服务端只会回 30001「参数JSON格式错误」，
    // 看不出是夹具坏了，因此在这里显性失败并带出真实字段名。
    expect(Number.isFinite(Number(rawTypeId)), `客户类型选项结构异常，实际字段：${JSON.stringify(Object.keys(typeList[0]))}`).toBeTruthy();
    const customerTypeId = Number(rawTypeId);

    async function newCustomer(tag: string, address: string, located: boolean): Promise<string> {
      const id = String(
        await post('/scm/customer/add', {
          name: `${name}客户${tag}`,
          customerTypeId,
          settleMode: 'INDEPENDENT',
          contactName: '验收联系人',
          contactPhone: '13800000000',
          address,
          provinceCode: 330000,
          provinceName: '浙江省',
          cityCode: 330100,
          cityName: '杭州市',
          districtCode: 330106,
          districtName: '西湖区',
          ...(located
            ? {
                longitude: tag === 'A' ? '113.94100000' : '114.07100000',
                latitude: tag === 'A' ? '22.55100000' : '22.62100000',
                geomCrs: 'GCJ02',
              }
            : {}),
        })
      );
      const created = await get<Row>(`/scm/customer/detail/${id}`);
      await post('/scm/customer/updateStatus', {customerId: id, version: created.version, status: 'COOPERATING'});
      return id;
    }

    locatedCustomerIdA = await newCustomer('A', '验收路A号', true);
    const customerB = await newCustomer('B', '验收路B号', true);
    // 第三个客户刻意不给坐标：只有这样才造得出「未定位停靠点」，41104 才是真被验到而不是被绕开。
    geolessCustomerId = await newCustomer('G', '验收路无坐标号', false);

    async function confirmedOrder(customerId: string, skuId: string, address: string) {
      let order = await post<Row>('/scm/order/create', {
        customerId,
        orderSource: 'ADMIN',
        address: {receiverName: '配送验收', receiverPhone: '13800000000', address},
        expectDeliveryTime: `${today} 10:00:00`,
        remark: name,
        items: [{skuId, orderedQuantity: '1.0000', manualPriceOverride: false}],
      });
      order = await post<Row>('/scm/order/submit', {orderId: order.orderId, version: order.version});
      const item = order.items[0];
      await post('/scm/order/item/actual-quantity', {
        orderId: order.orderId,
        itemId: item.itemId,
        version: item.version,
        actualQuantity: '1.0000',
        reason: '配送验收实重',
      });
      const after = await get<Row>(`/scm/order/detail/${order.orderId}`);
      const confirmed = await post<Row>('/scm/order/confirm', {orderId: order.orderId, version: after.version});
      expect(confirmed.status).toBe('CONFIRMED');
      await sortingCompleted(after);
      return {id: String(order.orderId), no: String(confirmed.orderNo ?? after.orderNo)};
    }

    /**
     * P1 之后「已确认」不再等于「可配送」：候选要求订单每条有效明细行都被**已完成**的分拣任务覆盖
     * （`docs/decisions.md`「P1 分拣管理裁决」第 11 条与补充第 18 条）。组单夹具因此必须走一遍
     * 真实分拣命令链（建单 → 录入 → 完成），不能靠改订单状态糊过去 —— 那正是被验收的口径本身。
     */
    async function sortingCompleted(detail: Row) {
      const itemIds = (detail.items as Row[]).map((i) => Number(i.itemId));
      expect(itemIds.length, '订单至少要有一行明细').toBeGreaterThan(0);
      const created = await post<Row>('/scm/sorting/tasks', {
        warehouseId: Number(warehouse.id),
        salesOrderItemIds: itemIds,
        remark: `${name} 配送前置分拣`,
      });
      const entries = (created.items as Row[]).map((line) => ({
        id: line.id,
        version: line.version,
        sortedQuantity: line.plannedQuantitySnapshot,
        result: 'NORMAL',
      }));
      await post(`/scm/sorting/tasks/${created.task.id}/entry`, {items: entries});
      const ready = await get<Row>(`/scm/sorting/tasks/${created.task.id}`);
      await post(`/scm/sorting/tasks/${created.task.id}/complete`, {version: ready.task.version});
      const done = await get<Row>(`/scm/sorting/tasks/${created.task.id}`);
      expect(done.task.status, '前置分拣任务必须已完成').toBe('COMPLETED');
    }

    for (const [tag, customerId, skuId] of [
      ['A1', locatedCustomerIdA, skuIds[0]],
      ['A2', locatedCustomerIdA, skuIds[1]],
      ['B1', customerB, skuIds[0]],
    ] as const) {
      const created = await confirmedOrder(customerId, skuId, customerId === locatedCustomerIdA ? '验收路A号' : '验收路B号');
      orders.push({...created, customerId});
      void tag;
    }
    const geoless = await confirmedOrder(geolessCustomerId, skuIds[1], '验收路无坐标号');
    geolessOrderId = geoless.id;
    orders.push({...geoless, customerId: geolessCustomerId});
  });

  test.afterAll(async () => {
    if (routeId && api) {
      const current = await route();
      if (current.status === 'DRAFT' || current.status === 'PLANNED') {
        await envelope(`/scm/delivery/routes/${current.routeId ?? routeId}/cancel`, {version: Number(current.version), reason: 'E2E 收尾释放'});
      }
    }
    accounts.cleanup();
  });

  test('1｜L0 仓库与客户定位落成结构化坐标，可按明细回读', async () => {
    expect(warehouse.geomCrs, '仓库坐标系必须回读为 GCJ02').toBe('GCJ02');
    expect(String(warehouse.longitude)).toContain('113.93');

    const located = await get<Row>(`/scm/customer/detail/${locatedCustomerIdA}`);
    // 区划编码是整型列，接口按数字下发，断言不能依赖字符串形态
    expect(String(located.cityCode)).toBe('330100');
    expect(located.geomCrs).toBe('GCJ02');

    const geoless = await get<Row>(`/scm/customer/detail/${geolessCustomerId}`);
    expect(geoless.longitude ?? null, '未定位客户不得被猜出坐标').toBeNull();
    expect(geoless.cityName, '省市区归属与坐标是两件事，解析不出坐标也仍应有归属').toBe('杭州市');
  });

  test('2｜L1 司机与车辆在主档页新建并落库，重复编码被服务端拒绝', async ({page}) => {
    await authenticate(page, adminToken);

    await page.goto('/#/delivery/drivers');
    await expect(page.locator('#scm-delivery-driver-table')).toBeVisible();

    // 司机现在必须绑定系统员工才能启用，而绑定用的是员工选择器（需要按姓名检索）；
    // 本用例的目的是「主档新建 + 服务端唯一性」，因此改由接口带 employeeId 建司机，
    // 页面这一段只验「新列能渲染出绑定员工」，不重复验选择器交互。
    await ok(api, 'post', '/scm/delivery/drivers', {
      driverCode,
      driverName: `${name}司机`,
      phone: '13900000000',
      employeeId: accounts.employeeIds[accounts.readOnly],
      status: 'ENABLED',
    });
    await page.reload();
    // 主档列表按 driver_code 升序分页（pageSize 20），而 E2E 按既有约定只回收临时账号、
    // 不删业务行 —— 开发库累计到 20+ 条司机后，新行会落到第一页之外。
    // 所以这里必须按编码搜索再断言「唯一一行」：断的是服务端唯一性与列表渲染，
    // 而不是「它恰好排在第一页」。
    // 司机编码在服务端 trim + toUpperCase，关键字查询用的是区分大小写的 LIKE
    const driverCodeUpper = driverCode.toUpperCase();
    await page.locator('.smart-query-form input').first().fill(driverCodeUpper);
    // 关键字框不绑 Enter，必须点查询按钮
    await page
      .locator('.smart-query-form')
      .getByRole('button', {name: /^查\s*询$/})
      .click();
    await expect(
      page.locator('#scm-delivery-driver-table tbody tr.ant-table-row').filter({hasText: `${name}司机`}),
      '列表应只出现这一条新建司机'
    ).toHaveCount(1);
    // 绑定关系的证据在下面按接口回读 employeeId（列表里显示的是员工姓名，
    // 由临时账号脚本决定，不该用文案去猜），页面这一段只验新行确实出现在主档列表里。

    const drivers = await get<Row>(`/scm/delivery/drivers?keyword=${driverCodeUpper}&pageNum=1&pageSize=20`);
    expect(drivers.list, '司机必须真的落库').toHaveLength(1);
    driverId = String(drivers.list[0].id);
    expect(drivers.list[0].status).toBe('ENABLED');
    expect(drivers.list[0].employeeId, '启用司机必须带上绑定的员工 id').toBeTruthy();

    // 未绑定的启用司机是新增的服务端规则，不能只靠前端必填星号
    const unbound = await envelope('/scm/delivery/drivers', {
      driverCode: `${name}-NB`,
      driverName: '未绑定启用司机',
      phone: '13900000009',
      status: 'ENABLED',
    });
    expect(unbound.ok, '未绑定员工的启用司机不得建立').toBe(false);
    const stillOne = await get<Row>(`/scm/delivery/drivers?keyword=${`${name}-NB`.toUpperCase()}&pageNum=1&pageSize=20`);
    expect(stillOne.list, '被拒的司机不得留下档案行').toHaveLength(0);

    await page.goto('/#/delivery/vehicles');
    await expect(page.locator('#scm-delivery-vehicle-table')).toBeVisible();
    await button(page, '新建车辆').click();
    const vehicleModal = page.locator('.ant-modal:visible').last();
    await fillField(vehicleModal, '车牌号', vehicleNo);
    await fillField(vehicleModal, '车型', '验收面包车');
    await button(vehicleModal, '确 定').click();
    await expect(vehicleModal).toBeHidden();
    const vehicles = await get<Row>(`/scm/delivery/vehicles?keyword=${vehicleNo.toUpperCase()}&pageNum=1&pageSize=20`);
    expect(vehicles.list).toHaveLength(1);
    vehicleId = String(vehicles.list[0].id);

    // 重复编码这条断言要单独成立，就不能让「启用必须绑定员工」的校验先挡下来：
    // 用 DISABLED 提交，绑定校验被跳过，服务端才会走到 driver_code 的唯一性判定。
    const duplicate = await envelope('/scm/delivery/drivers', {
      driverCode,
      driverName: '重复',
      phone: '13900000001',
      status: 'DISABLED',
    });
    expect(duplicate.code, '重复司机编码必须由服务端挡住').toBe(41106);
  });

  test('3｜L2 页面新建草稿线路并快照仓库起点坐标', async ({page}) => {
    await authenticate(page, adminToken);
    await page.goto('/#/delivery/routes');
    await expect(page.locator('#scm-delivery-route-table')).toBeVisible();

    await button(page, '新建线路').click();
    // 新建线路仍是抽屉表单；按标题锁定它，避免在保存未生效时误命中页面上的其它浮层。
    const drawer = page.locator('.ant-drawer-open').filter({hasText: '新建配送线路'});
    await expect(button(drawer, '保存线路')).toBeEnabled();
    await fillField(drawer, '线路名称', `${name} 1 线`);
    // 配送日期由表单默认给当天（antd DatePicker 的输入框只读，不靠 fill 硬塞值）
    await pickOption(page, drawer, '起点仓库', warehouseName);
    await button(drawer, '保存线路').click();
    const closed = await drawer
      .waitFor({state: 'detached', timeout: 8000})
      .then(() => true)
      .catch(() => false);
    if (!closed) {
      // 抽屉不关就是保存被挡住；把页面自己的提示与表单现值一起带进失败信息，
      // 否则只剩一个 toHaveCount(0) 的红灯，看不出是校验、权限还是接口失败。
      const hints = await drawer.locator('.ant-form-item-explain-error, .ant-alert-message').allInnerTexts();
      const values = await drawer.locator('input').evaluateAll((els) =>
        els.map((element) => {
          if (!(element instanceof HTMLInputElement)) return '';
          return `${element.placeholder || element.type}=${element.value}`;
        })
      );
      throw new Error(`保存线路未生效；页面提示 ${JSON.stringify(hints)}；表单现值 ${JSON.stringify(values)}`);
    }

    const found = await get<Row>(`/scm/delivery/routes?keyword=${name}&pageNum=1&pageSize=20`);
    expect(found.list, '新线路必须能在列表里查到').toHaveLength(1);
    routeId = String(found.list[0].id);
    expect(String(found.list[0].deliveryDate).slice(0, 10), '表单默认配送日期应为当天').toBe(today);

    const created = await route();
    expect(created.status).toBe('DRAFT');
    expect(String(created.startLongitude), '线路起点必须取仓库现值，不是页面自己填的').toContain('113.93');
    expect(created.startGeomCrs).toBe('GCJ02');
    expect(created.warehouseId != null, '线路必须绑定仓库').toBeTruthy();

    const withDriver = await envelope(
      `/scm/delivery/routes/${routeId}`,
      {
        routeName: `${name} 2 线`,
        deliveryDate: today,
        warehouseId: Number(warehouse.id),
        driverId: Number(driverId),
        vehicleId: Number(vehicleId),
        version: 999999,
      },
      'put'
    );
    expect(withDriver.code, `过期 version 编辑必须被乐观锁拒绝，实际 ${withDriver.code} ${withDriver.msg}`).toBe(40921);

    await put(`/scm/delivery/routes/${routeId}`, {
      routeName: `${name} 1 线`,
      deliveryDate: today,
      warehouseId: Number(warehouse.id),
      driverId: Number(driverId),
      vehicleId: Number(vehicleId),
      version: await routeVersion(),
    });
    const assigned = await route();
    expect(assigned.driverId != null, '司机/车辆可稍后分配，但分配后必须落库').toBeTruthy();
    expect(assigned.vehicleId != null).toBeTruthy();
  });

  test('4｜L2 组单：同客户同地址合并停靠点，未定位客户单照旧入线', async ({page}) => {
    await authenticate(page, adminToken);
    await page.goto('/#/delivery/routes');
    await page.locator('#scm-delivery-route-table tbody tr.ant-table-row').filter({hasText: name}).first().getByText(accessibleName('详情')).click();
    // 详情已是独立页面（列表页不再挂线路详情抽屉）：URL 落在 /delivery/routes/<id>?tab=base
    await expect(page).toHaveURL(/#\/delivery\/routes\/\d+\?tab=base/);
    const detailPage = page.locator('.route-detail-page');
    await expect(detailPage.locator('.scm-detail-header')).toBeVisible();
    await detailPage.getByRole('tab', {name: accessibleName('线路订单')}).click();

    // 候选清单是共享池且分页，且重新查询会重置勾选，因此按「一个订单一趟往返」驱动：
    // 关键字筛到该单 → 勾选 → 填组单原因 → 加入线路。这同时验到关键字过滤与逐次组单后的版本推进。
    for (const order of orders) {
      await button(detailPage, '加入订单').click();
      const modal = page.locator('.ant-modal:visible').last();
      await expect(modal).toContainText('选择待配送订单');
      const keyword = field(modal, '客户').locator('input').first();
      await keyword.fill(order.no);
      await button(modal, '查询').click();
      const row = modal.locator('tbody tr.ant-table-row').filter({hasText: order.no}).first();
      await expect(row, `按订单号 ${order.no} 应筛到该单`).toBeVisible();
      await row.locator('input[type=checkbox]').check();
      await fillField(modal, '组单原因', '本次城区配送安排');
      await modal.getByRole('button', {name: /加\s*入\s*线\s*路/}).click();
      await expect(modal).toBeHidden({timeout: 15000});
    }

    const after = await detail();
    expect(
      after.orders,
      `4 张订单全部入线，实际 ${JSON.stringify((after.orders ?? []).map((o: Row) => o.orderId))}，期望 ${JSON.stringify(orders.map((o) => o.id))}`
    ).toHaveLength(4);
    // A 客户两张单同地址 → 1 个停靠点；B、无坐标客户各 1 个 → 共 3 个停靠点
    expect(after.stops).toHaveLength(3);
    expect(after.route.stopCount).toBe(3);
    expect(after.route.locatedCount, '未定位客户的停靠点不得被猜成已定位').toBe(2);

    const duplicate = await envelope(`/scm/delivery/routes/${routeId}/orders`, {
      version: await routeVersion(),
      orderIds: [Number(orders[0].id)],
      reason: '重复组单',
    });
    expect(duplicate.code, '已分配订单不能重复加入').toBe(41103);
  });

  test('5｜L2 未定位时确认规划被拒，页面补定位后放行', async ({page}) => {
    await authenticate(page, adminToken);

    const stale = await envelope(`/scm/delivery/routes/${routeId}/plan`, {version: 1});
    expect(stale.code, '过期 version 的规划请求必须先被乐观锁挡住').toBe(40921);

    const blocked = await envelope(`/scm/delivery/routes/${routeId}/plan`, {version: await routeVersion()});
    expect(blocked.code, '尚有停靠点未定位时不得确认规划').toBe(41104);
    expect(await (await route()).status).toBe('DRAFT');

    await page.goto('/#/delivery/routes');
    await page.locator('#scm-delivery-route-table tbody tr.ant-table-row').filter({hasText: name}).first().getByText(accessibleName('路线')).click();
    // 「路线」直达独立详情页的地图 Tab（URL 带 ?tab=map），不再是抽屉内切页
    await expect(page).toHaveURL(/#\/delivery\/routes\/\d+\?tab=map/);
    const detailPage = page.locator('.route-detail-page');
    await expect(detailPage.locator('.route-map-layout')).toBeVisible();
    await expect(detailPage).toContainText('尚有 1 个停靠点未定位');
    // 只有「未定位」那个停靠点需要补坐标：按停靠点卡片文本筛，避免点到已定位项的同类按钮。
    await detailPage
      .locator('.stop-card')
      .filter({hasText: '未定位'})
      .first()
      .getByRole('button', {name: accessibleName('编辑站点')})
      .click();
    // 经纬度与坐标系是「停靠点定位与备注」弹窗里的内联输入，直接改字段值；
    // 只有需要地图选点时才会再叠一层选择器弹窗，因此必须按标题锁定外层弹窗。
    const modal = page.locator('.ant-modal:visible').filter({hasText: '停靠点定位与备注'}).last();
    // 选点组件的输入框用 aria-label（placeholder 是示例值「例如 121.47370100」）
    await modal.getByLabel('经度', {exact: true}).fill(STOP_LNG);
    await modal.getByLabel('纬度', {exact: true}).fill(STOP_LAT);
    await modal.locator('.ant-select').last().click();
    await page.locator('.ant-select-dropdown:visible .ant-select-item-option-content').filter({hasText: 'GCJ02'}).first().click();
    await expect(modal.getByRole('button', {name: /已\s*定\s*位/}))
      .toBeVisible()
      .catch(() => undefined);
    await modal.getByRole('button', {name: accessibleName('确 定')}).click();
    await expect(modal).toBeHidden({timeout: 15000});

    const located = await detail();
    const patched = located.stops.find((s: Row) => s.geomCrs === 'GCJ02' && String(s.longitude ?? '').includes('113.946'));
    expect(patched, '页面补的坐标必须落库').toBeTruthy();
    expect((await route()).locatedCount).toBe(3);
  });

  test('6｜L2 确认规划：状态迁移、重复请求与后续编辑全部封死', async ({page}) => {
    await authenticate(page, adminToken);
    await page.goto('/#/delivery/routes');
    await page.locator('#scm-delivery-route-table tbody tr.ant-table-row').filter({hasText: name}).first().getByText(accessibleName('详情')).click();
    const detailPage = page.locator('.route-detail-page');
    await expect(page).toHaveURL(/#\/delivery\/routes\/\d+\?tab=base/);
    await button(detailPage, '确认规划').click();
    await page
      .locator('.ant-modal-confirm:visible')
      .getByRole('button', {name: accessibleName('确 定')})
      .click();
    // toast 挂在 body 上、不在详情页容器里，可能先于断言消失；状态迁移本身才是承重断言，回读接口确认
    await expect(page.getByText('线路已规划'))
      .toBeVisible({timeout: 15000})
      .catch(async () => {
        expect((await route()).status).toBe('PLANNED');
      });

    expect((await route()).status).toBe('PLANNED');

    const again = await envelope(`/scm/delivery/routes/${routeId}/plan`, {version: await routeVersion()});
    expect(again.code, '重复确认规划不能再次成功，必须是状态拒绝').toBe(41101);

    const addOrders = await envelope(`/scm/delivery/routes/${routeId}/orders`, {
      version: await routeVersion(),
      orderIds: [Number(geolessOrderId)],
      reason: '规划后加单',
    });
    expect(addOrders.code, '规划后不得再加单').toBe(41101);

    const remove = await envelope(
      `/scm/delivery/routes/${routeId}/orders/${orders[0].id}`,
      {version: await routeVersion(), reason: '规划后移除'},
      'delete'
    );
    expect(remove.code, '规划后不得再移除订单').toBe(41101);

    const edit = await envelope(
      `/scm/delivery/routes/${routeId}`,
      {
        routeName: '改名',
        deliveryDate: today,
        warehouseId: Number(warehouse.id),
        version: await routeVersion(),
      },
      'put'
    );
    expect(edit.code, '规划后不得改头信息').toBe(41101);

    // L3 端点在 P2 已经真实存在，因此这段断言的目标从「不存在」换成「存在且被守住」：
    // 用错误版本 / 错误状态去撞，拿到的是乐观锁或状态拒绝，而不是 404 —— 既证明路由已注册，
    // 又不会在这条 L0–L2 用例里真的扣库存。
    const staleVersion = (await routeVersion()) + 7;
    const dispatchGuard = await envelope(`/scm/delivery/routes/${routeId}/dispatch`, {version: staleVersion});
    expect(dispatchGuard.code, `发车端点应已注册并被乐观锁守住：${dispatchGuard.msg}`).toBe(40921);

    const completeGuard = await envelope(`/scm/delivery/routes/${routeId}/complete`, {version: await routeVersion()});
    expect(completeGuard.code, 'PLANNED 线路不能直接完成').toBe(41101);

    const signGuard = await envelope(`/scm/delivery/routes/${routeId}/orders/${orders[0].id}/sign`, {version: 1, result: 'SIGNED'});
    expect(signGuard.code, '未发车的线路不能签收').toBe(41101);

    const order = await get<Row>(`/scm/order/detail/${orders[0].id}`);
    expect(order.status, '确认规划不改变订单状态').toBe('CONFIRMED');
  });

  test('7｜L2 取消线路：历史保留、分配释放回池', async () => {
    const cancelled = await envelope(`/scm/delivery/routes/${routeId}/cancel`, {version: await routeVersion(), reason: '验收收尾取消'});
    expect(cancelled.code, `取消失败：${cancelled.msg}`).toBe(0);

    const after = await detail();
    expect(after.route.status).toBe('CANCELLED');
    expect(after.orders, '取消保留历史订单行，不是删掉').toHaveLength(4);

    const candidates = await get<Row[]>('/scm/delivery/candidate-orders?deliveryDate=' + today + '&pageNum=1&pageSize=100');
    const back = (Array.isArray(candidates) ? candidates : (candidates as any).list ?? []).map((o: Row) => String(o.orderId));
    for (const order of orders) {
      expect(back, `取消后订单 ${order.id} 必须重新可分配`).toContain(order.id);
    }

    const replan = await envelope(`/scm/delivery/routes/${routeId}/plan`, {version: await routeVersion()});
    expect(replan.code, '已取消是终态，不得再规划').toBe(41101);
  });

  test('8｜权限负向：只读账号看不到写入口、写接口一律 30005', async ({page}) => {
    const readOnlyToken = await login(accounts, accounts.readOnly);
    const readOnly = await apiClient(readOnlyToken);
    await authenticate(page, readOnlyToken);

    const readable = await ok<Row>(readOnly, 'get', `/scm/delivery/routes/${routeId}`);
    // 独立账号回读：既证明 :query 读权限放行，也证明「取消保留历史」不是只有管理员看得见
    expect(readable.route.status, '只读账号应看到已取消线路的历史').toBe('CANCELLED');
    expect(readable.orders.length, '取消后线路订单明细仍可查看').toBeGreaterThan(0);
    for (const [path, body] of [
      [`/scm/delivery/routes/${routeId || 1}/plan`, {version: 1}],
      [`/scm/delivery/routes/${routeId || 1}/cancel`, {version: 1, reason: 'x'}],
      [`/scm/delivery/routes/${routeId || 1}/orders`, {version: 1, orderIds: [1], reason: 'x'}],
    ] as const) {
      const denied = await readOnly.post(path, {data: body, headers: {'Idempotency-Key': randomUUID()}});
      expect((await denied.json()).code, `${path} 必须由服务端把关`).toBe(30005);
    }
    const saveDriver = await readOnly.post('/scm/delivery/drivers', {
      data: {driverCode: `${name}-X`, driverName: '越权', phone: '13900000002', status: 'ENABLED'},
      headers: {'Idempotency-Key': randomUUID()},
    });
    expect((await saveDriver.json()).code).toBe(30005);

    await page.goto('/#/delivery/routes');
    await expect(page.locator('#scm-delivery-route-table')).toBeVisible();
    await expect(button(page, '新建线路')).toHaveCount(0);
    await page.goto('/#/delivery/drivers');
    await expect(button(page, '新建司机')).toHaveCount(0);

    const noRole = await apiClient(await login(accounts, accounts.noRole));
    const deniedRead = await noRole.get('/scm/delivery/routes?pageNum=1&pageSize=20');
    expect((await deniedRead.json()).code, '无角色连查询都不该通过').toBe(30005);
  });
});

// ---------------------------------------------------------------- 来源：e2e/scm-delivery-l3.spec.ts
test.describe('scm-delivery-l3', () => {
  /**
   * P2 物流配送 L3 的浏览器整链验收：
   * 备货 → 订单 → 分拣完成（其中一张少拣）→ 组单 → 确认规划 → **发车** → 出库单与 SALES_OUT 取证 →
   * 签收 / 异常签收 → 完成线路，再加四条「页面照样能跑、事实却错了」的负向。
   *
   * 为什么必须跑整链而不是只看接口码：
   * 1. 实发量必须来自分拣的 {@code sorted_quantity}。两张订单刻意给不同实发量（3 与 2），
   *    再从余额与流水反推 —— 若发车偷偷回读订单行的 {@code actual_quantity}，余额差就对不上，
   *    而接口一路返回成功。
   * 2. 发车只产生一份事实：同键重放、以及对已 DISPATCHED 线路再发一次，都不许追加出库单或二次扣账。
   * 3. 发车与分拣重开互斥要双向验（重开后整线拒发、发车后任务不能重开）；
   *    这条守卫跨 sorting / delivery / inventory 三个模块。
   * 4. 取证照落在非超管账号上：没有仓库授权的调度不能发车，司机不能签别人的线路。
   *
   * 库存与订单全部自建：发车会真的扣库存，借共享余额行会让断言依赖执行顺序。
   */
  type Row = Record<string, any>;
  type Paged = {list: Row[]; total: number};

  test.describe.configure({mode: 'serial'});

  const accounts: TempAccounts = provisionTempAccounts('w5', undefined, ['SCM_DISPATCHER', 'SCM_DRIVER']);
  const tag = `P2${Date.now().toString(36).toUpperCase()}`;
  const today = new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Shanghai'}).format(new Date());
  const START_LNG = '113.93000000';
  const START_LAT = '22.54000000';
  const PRICE = '3.5000';
  /** 发车出库的合计实发量：A 全量 3 + B 少拣 2。 */
  const SHIPPED = 5;

  let api: Awaited<ReturnType<typeof apiClient>>;
  let adminToken = '';
  let dispatcher: Awaited<ReturnType<typeof apiClient>>;
  let driver: Awaited<ReturnType<typeof apiClient>>;
  let dispatcherEmployeeId = 0;
  let driverEmployeeId = 0;

  let warehouseId = 0;
  let skuId = '';
  let supplierId = '';
  let routeId = '';
  let orderA = '';
  let orderB = '';
  let taskB = '';
  let outboundId = 0;
  let dispatchKey: string = '';
  let balanceAfterStock = 0;

  async function ok<T = Row>(
    client: typeof api,
    method: 'get' | 'post' | 'put',
    path: string,
    data?: unknown,
    key: string = randomUUID()
  ): Promise<T> {
    const response = method === 'get' ? await client.get(path) : await client[method](path, {data, headers: {'Idempotency-Key': key}});
    const body = await response.json();
    expect(body.code, `${method.toUpperCase()} ${path} 失败：${body.code} ${body.msg}`).toBe(0);
    return body.data as T;
  }

  const get = <T = Row>(path: string, client: typeof api = api) => ok<T>(client, 'get', path);
  const post = <T = Row>(path: string, data?: unknown, client: typeof api = api) => ok<T>(client, 'post', path, data);

  /** 负向入口：拿原始信封，不因 code != 0 先把用例判红。 */
  async function envelope(path: string, data: unknown, client: typeof api = api, key = randomUUID(), method: 'post' | 'delete' = 'post') {
    const response =
      method === 'delete'
        ? await client.delete(path, {data, headers: {'Idempotency-Key': key}})
        : await client.post(path, {data, headers: {'Idempotency-Key': key}});
    const body = await response.json().catch(() => ({}) as Row);
    return {status: response.status(), code: Number(body.code), msg: String(body.msg ?? '')};
  }

  const routeDetail = (id: string | number = routeId, client: typeof api = api) => get<Row>(`/scm/delivery/routes/${id}`, client);
  const routeRow = async () => (await routeDetail()).route as Row;
  const routeVersion = async () => Number((await routeRow()).version);
  const ordersOnRoute = async () => (await routeDetail()).orders as Row[];
  const orderOnRoute = async (orderId: string) => (await ordersOnRoute()).find((o) => Number(o.orderId) === Number(orderId));

  async function balanceQuantity(): Promise<number> {
    const page = await post<Paged>('/scm/inventory/balance/query', {pageNum: 1, pageSize: 50, warehouseId, skuId: Number(skuId)});
    const row = page.list.find((b) => Number(b.skuId) === Number(skuId));
    expect(row, `仓库 ${warehouseId} / SKU ${skuId} 应有余额行`).toBeTruthy();
    return Number(row.quantity);
  }

  async function salesOutMovements(): Promise<Row[]> {
    const page = await post<Paged>('/scm/inventory/movement/query', {
      pageNum: 1,
      pageSize: 100,
      warehouseId,
      skuId: Number(skuId),
      movementType: 'SALES_OUT',
    });
    return page.list;
  }

  /** 真实采购链路备货：建单 → 提交 → DIRECT 收货确认（收货即入账）。 */
  async function stockIn(quantity: string) {
    const order = await post<Row>('/scm/purchase/create', {
      supplierId,
      warehouseId,
      purchaserId: null,
      plannedArrivalDate: null,
      remark: `${tag} 发车备货`,
      items: [{skuId: Number(skuId), quantity, price: PRICE, allocations: []}],
    });
    await post('/scm/purchase/submit', {id: order.id, version: order.version});
    const receipt = await post<Row>('/scm/purchase/receipt/create', {
      purchaseOrderId: order.id,
      receiptMode: 'DIRECT',
      remark: `${tag} 发车备货收货`,
    });
    const item = (receipt.items as Row[])[0];
    return post('/scm/purchase/receipt/confirm', {
      id: receipt.id,
      version: receipt.version,
      items: [{receiptItemId: item.id, version: item.version, receivedQuantity: quantity, actualWeight: quantity, weightSource: 'MANUAL'}],
    });
  }

  /** 一张「已分拣完成」的订单；sortedQuantity 小于 quantity 就是少拣。 */
  async function sortedOrder(
    addressTag: string,
    quantity: string,
    sortedQuantity: string,
    result: 'NORMAL' | 'SHORT'
  ): Promise<{orderId: string; taskId: string}> {
    const customerId = await createLocatedCustomer(api, tag, addressTag, `${tag}${addressTag} 验收路`);
    const created = await createDeliveryReadyOrder(api, {
      runTag: tag,
      customerId,
      skuId,
      address: `${tag}${addressTag} 验收路`,
      warehouseId,
      quantity,
      sortedQuantity,
      sortedResult: result,
      ...(result === 'SHORT' ? {sortReason: `${tag} 现场缺货`} : {}),
    });
    // 夹具返回的是 sortingTaskId；本 spec 里统一叫 taskId，少拣取证要按任务回读。
    return {orderId: created.orderId, taskId: created.sortingTaskId};
  }

  /** 把线路推到 PLANNED：组单 + 补齐停靠点坐标 + 确认规划。 */
  async function plannedRouteWith(orders: string[], routeName: string): Promise<string> {
    // 建单接口返回的 data 就是线路 id 本身（ResponseDTO<Long>），不是对象。
    const id = String(
      await post('/scm/delivery/routes', {
        routeName,
        deliveryDate: today,
        warehouseId,
        remark: tag,
      })
    );
    let version = Number((await routeDetail(id)).route.version);
    await post(`/scm/delivery/routes/${id}/orders`, {version, orderIds: orders.map(Number), reason: `${tag} 组单`});
    const detail = await routeDetail(id);
    version = Number(detail.route.version);
    for (const stop of detail.stops as Row[]) {
      if (stop.longitude != null && stop.latitude != null) continue;
      await ok(api, 'put', `/scm/delivery/routes/${id}/stops/${stop.id}`, {version, longitude: START_LNG, latitude: START_LAT, geomCrs: 'GCJ02'});
      version = Number((await routeDetail(id)).route.version);
    }
    await post(`/scm/delivery/routes/${id}/plan`, {version, reason: `${tag} 规划`});
    return id;
  }

  function button(scope: any, text: string) {
    return scope.getByRole('button', {name: accessibleName(text)});
  }

  test.beforeAll(async () => {
    adminToken = await login(accounts, accounts.admin);
    api = await apiClient(adminToken);
    dispatcherEmployeeId = accounts.employeeIds[accounts.roleAccounts.SCM_DISPATCHER];
    driverEmployeeId = accounts.employeeIds[accounts.roleAccounts.SCM_DRIVER];
    expect(dispatcherEmployeeId, '未建立 SCM_DISPATCHER 账号，发车权限取证没有证据').toBeGreaterThan(0);
    expect(driverEmployeeId, '未建立 SCM_DRIVER 账号，签收范围取证没有证据').toBeGreaterThan(0);
    dispatcher = await apiClient(await login(accounts, accounts.roleAccounts.SCM_DISPATCHER));
    driver = await apiClient(await login(accounts, accounts.roleAccounts.SCM_DRIVER));

    const options = await get<Row[]>('/scm/delivery/options/warehouses');
    const target = options.find((w) => w.warehouseCode === 'WH001') ?? options[0];
    expect(target, '开发库没有启用仓库').toBeTruthy();
    warehouseId = Number(target.id);
    const fresh = await get<Row>(`/scm/warehouse/detail/${warehouseId}`);
    if (fresh.longitude == null) {
      // 规划要求仓库起点带坐标；列表接口不返回坐标，因此按明细回读后补齐再写回。
      await ok(api, 'post', '/scm/warehouse/update', {
        id: warehouseId,
        version: fresh.version,
        warehouseCode: target.warehouseCode,
        name: target.name,
        address: fresh.address ?? `${tag} 仓`,
        longitude: START_LNG,
        latitude: START_LAT,
        geomCrs: 'GCJ02',
        remark: tag,
      });
    }

    skuId = await createSku(api, tag, 'L3');
    // 编码由服务端生成（SUP + 6 位序号），夹具不再提交 supplierCode。
    supplierId = String(await post('/scm/supplier/add', {name: `${tag}供应商`}));
    await post('/scm/supplier/sku/replace', {
      supplierId,
      items: [{skuId: Number(skuId), purchaseUnit: 'kg', defaultFlag: true, status: 'ENABLED'}],
    });
    await stockIn('20.0000');
    balanceAfterStock = await balanceQuantity();

    // 发车走库存域既有的仓库范围守卫，所以调度岗必须持有本仓授权行 —— 这是配置要求，
    // 不是放宽守卫的理由。缺授权的那条反例在第 8 条用例里现证。
    await post('/scm/warehouse/scope/update', {employeeId: dispatcherEmployeeId, warehouseIds: [warehouseId]}, api);
  });

  test.afterAll(async () => {
    // 授权行不随账号回收，必须显式清空，否则下一轮仍带着上一轮的仓库范围。
    if (dispatcherEmployeeId && api) {
      await post('/scm/warehouse/scope/update', {employeeId: dispatcherEmployeeId, warehouseIds: []}, api);
    }
    if (driverEmployeeId && api) {
      await post('/scm/warehouse/scope/update', {employeeId: driverEmployeeId, warehouseIds: []}, api);
    }
    for (const client of [dispatcher, driver, api]) {
      if (!client) continue;
      await client.get('/login/logout').catch(() => undefined);
      await client.dispose();
    }
    accounts.cleanup();
  });

  test('1｜夹具：两张已分拣完成订单，其中一张少拣 2（计划 3）', async () => {
    expect(balanceAfterStock, '备货必须真实入账，否则发车会因库存不足而失败').toBeGreaterThan(SHIPPED);
    const a = await sortedOrder('A', '3.0000', '3.0000', 'NORMAL');
    const b = await sortedOrder('B', '3.0000', '2.0000', 'SHORT');
    orderA = a.orderId;
    orderB = b.orderId;
    taskB = b.taskId;
    expect(orderA).toBeTruthy();
    expect(orderB).toBeTruthy();

    const sortedB = await get<Row>(`/scm/sorting/tasks/${taskB}`);
    expect(sortedB.task.status).toBe('COMPLETED');
    const activeB = (sortedB.items as Row[]).filter((i) => i.occupationStatus === 'ACTIVE');
    expect(activeB.length, '任务里应有一条活动明细').toBe(1);
    expect(Number(activeB[0].sortedQuantity), '少拣事实必须落在分拣行上，发车才有唯一来源').toBe(2);
    expect(activeB[0].result).toBe('SHORT');
  });

  test('2｜组单与确认规划：线路 PLANNED，一行库存都不扣', async () => {
    routeId = await plannedRouteWith([orderA, orderB], `${tag} 发车验收线路`);

    expect((await routeRow()).status).toBe('PLANNED');
    expect(await balanceQuantity(), '确认规划不产生任何库存写入').toBe(balanceAfterStock);
    expect((await salesOutMovements()).length, '规划不产生 SALES_OUT').toBe(0);
    expect((await routeRow()).outboundId ?? null, '规划不生成出库单').toBeNull();
  });

  test('3｜发车：DISPATCHED + 出库单 + 库存只按分拣实发量扣一次', async () => {
    const before = await balanceQuantity();
    dispatchKey = randomUUID();
    const body = {version: await routeVersion()};
    const dispatched = await ok<Row>(dispatcher, 'post', `/scm/delivery/routes/${routeId}/dispatch`, body, dispatchKey);
    outboundId = Number(dispatched.outboundId);

    expect(dispatched.status).toBe('DISPATCHED');
    expect(outboundId, '有实物离仓就必须有出库单').toBeGreaterThan(0);
    expect(Number(dispatched.shippedLineCount), '两个订单行各一条出库明细').toBe(2);

    const route = await routeRow();
    expect(route.status).toBe('DISPATCHED');
    expect(Number(route.outboundId)).toBe(outboundId);
    expect(route.outboundNo, '线路详情要能直接读回出库单号').toBeTruthy();
    expect(route.dispatchedAt, '发车时点与操作人成对落库').toBeTruthy();
    expect(route.dispatchedBy).toBeTruthy();

    expect(before - (await balanceQuantity()), `扣的必须是分拣实发量 ${SHIPPED}（3 + 2），不是订单行数量 6`).toBe(SHIPPED);

    const outbound = await get<Row>(`/scm/inventory/outbound/detail/${outboundId}`);
    expect(outbound.status, '发车出库直接是 CONFIRMED，不经过 DRAFT').toBe('CONFIRMED');
    expect((outbound.items as Row[]).length).toBe(2);
    const movements = await salesOutMovements();
    expect(movements.length, '两条 SALES_OUT，一订单行一条').toBe(2);
    expect(movements.reduce((sum, m) => sum + Number(m.quantity), 0)).toBe(SHIPPED);

    for (const orderId of [orderA, orderB]) {
      expect((await orderOnRoute(orderId)).fulfillmentStatus, '发车把活动订单全部推进在途').toBe('IN_TRANSIT');
    }

    // 同一把键 + 同一份载荷必须走回放分支：拿回同一张出库单，账上不再动一次。
    // 真实客户端的重试就是 axios 原样重发，所以载荷复用同一个对象，不重新读 version。
    const afterFirst = await balanceQuantity();
    const replay = await ok<Row>(dispatcher, 'post', `/scm/delivery/routes/${routeId}/dispatch`, body, dispatchKey);
    expect(Number(replay.outboundId), '重放不能再生成一张出库单').toBe(outboundId);
    // 基线是**首发之后**的余额：首发扣掉 5 是事实，重放必须在同一个数上不动。
    expect(await balanceQuantity(), '重放不再扣账').toBe(afterFirst);
    expect(afterFirst, '首发只扣了一个实发量合计').toBe(before - SHIPPED);
  });

  test('4｜重复发车：同键回放同一张出库单，换键再发被状态拒绝，账上不多一份', async () => {
    const before = await balanceQuantity();
    const again = await envelope(`/scm/delivery/routes/${routeId}/dispatch`, {version: await routeVersion()}, dispatcher);
    expect(again.code, '已发车线路换一把键也不能再发一次').toBe(41101);
    expect(Number((await routeRow()).outboundId)).toBe(outboundId);
    expect(await balanceQuantity(), '被拒的重复发车一次账都不许多扣').toBe(before);

    const page = await post<Paged>('/scm/inventory/outbound/query', {pageNum: 1, pageSize: 100, warehouseId});
    expect(page.list.filter((o) => Number(o.id) === outboundId).length).toBe(1);
    expect((await salesOutMovements()).length, '流水不许追加').toBe(2);
  });

  test('5｜签收与完成：异常必填原因，全部终态后线路才能完成', async () => {
    const bVersion = Number((await orderOnRoute(orderB)).version);
    const missing = await envelope(`/scm/delivery/routes/${routeId}/orders/${orderB}/sign`, {version: bVersion, result: 'EXCEPTION'}, dispatcher);
    expect(missing.code, '异常签收没有原因必须被拒').toBe(41118);

    const early = await envelope(`/scm/delivery/routes/${routeId}/complete`, {version: await routeVersion()}, dispatcher);
    expect(early.code, '仍有订单在途时不能完成线路').toBe(41117);

    await post(
      `/scm/delivery/routes/${routeId}/orders/${orderB}/sign`,
      {version: Number((await orderOnRoute(orderB)).version), result: 'EXCEPTION', reason: `${tag} 客户拒收破损`},
      dispatcher
    );
    const signedB = await orderOnRoute(orderB);
    expect(signedB.fulfillmentStatus).toBe('EXCEPTION');
    expect(String(signedB.signReason), '异常原因要读得回来').toContain(tag);
    expect(signedB.signedAt, '终态必须留下签收时点与签收人').toBeTruthy();
    expect(signedB.signedBy).toBeTruthy();

    const stillOpen = await envelope(`/scm/delivery/routes/${routeId}/complete`, {version: await routeVersion()}, dispatcher);
    expect(stillOpen.code, '只签了一单仍不能完成').toBe(41117);

    await post(
      `/scm/delivery/routes/${routeId}/orders/${orderA}/sign`,
      {version: Number((await orderOnRoute(orderA)).version), result: 'SIGNED'},
      dispatcher
    );
    await post(`/scm/delivery/routes/${routeId}/complete`, {version: await routeVersion()}, dispatcher);

    const route = await routeRow();
    expect(route.status).toBe('COMPLETED');
    expect(route.completedAt).toBeTruthy();
    expect(route.completedBy).toBeTruthy();
    expect((await orderOnRoute(orderA)).fulfillmentStatus).toBe('SIGNED');
    expect(await balanceQuantity(), '签收与完成都不产生库存事实').toBe(balanceAfterStock - SHIPPED);
  });

  test('6｜发车后不可逆：线路不能取消，订单也不能被摘掉', async () => {
    const cancel = await envelope(`/scm/delivery/routes/${routeId}/cancel`, {version: await routeVersion(), reason: `${tag} 发车后取消`}, dispatcher);
    expect(cancel.code, '已发车的线路不允许取消').toBe(41101);

    const remove = await envelope(
      `/scm/delivery/routes/${routeId}/orders/${orderA}`,
      {version: await routeVersion(), reason: `${tag} 发车后摘单`},
      dispatcher,
      randomUUID(),
      'delete'
    );
    expect(remove.code, '线路上的订单归属在发车后不可变更').toBe(41101);
    expect((await ordersOnRoute()).length).toBe(2);
  });

  test('7｜已真实出库的分拣任务不能重开（P1 第 21 条守卫）', async () => {
    const detail = await get<Row>(`/scm/sorting/tasks/${taskB}`);
    const blocked = await envelope(`/scm/sorting/tasks/${taskB}/reopen`, {version: detail.task.version, reason: `${tag} 出库后重开`});
    expect(blocked.code, `发车后重开必须被拒：${blocked.msg}`).toBe(41128);
    expect((await get<Row>(`/scm/sorting/tasks/${taskB}`)).task.status, '被拒的重开不许改任务状态').toBe('COMPLETED');
  });

  test('8｜非超管取证：缺仓库授权不能发车，司机签不了别人的线路', async ({page}) => {
    const extra = await sortedOrder('C', '1.0000', '1.0000', 'NORMAL');
    const scopedRouteId = await plannedRouteWith([extra.orderId], `${tag} 范围验收线路`);
    const version = () => routeVersionOf(scopedRouteId);

    // 先撤掉授权行再看同一把钥匙：功能权限 V64 已给到 SCM_DISPATCHER，
    // 被拒只能是因为仓库维度 —— 这样这条用例证的才是范围守卫，而不是功能权限缺失。
    await post('/scm/warehouse/scope/update', {employeeId: dispatcherEmployeeId, warehouseIds: []}, api);
    const denied = await envelope(`/scm/delivery/routes/${scopedRouteId}/dispatch`, {version: await version()}, dispatcher);
    expect(denied.code, '发车走库存域既有的仓库范围守卫，缺授权必须 30005').toBe(30005);
    expect((await routeDetail(scopedRouteId)).route.status, '被拒的发车不留任何痕迹').toBe('PLANNED');

    const signDenied = await envelope(`/scm/delivery/routes/${scopedRouteId}/orders/${extra.orderId}/sign`, {version: 0, result: 'SIGNED'}, driver);
    expect(signDenied.code, '司机不能凭 id 替不属于自己的线路签收').toBe(30005);

    await post('/scm/warehouse/scope/update', {employeeId: dispatcherEmployeeId, warehouseIds: [warehouseId]}, api);
    const allowed = await ok<Row>(dispatcher, 'post', `/scm/delivery/routes/${scopedRouteId}/dispatch`, {version: await version()});
    expect(allowed.status).toBe('DISPATCHED');

    // 页面上：已完成的线路不再提供发车动作，出库单线索在详情页基础信息里读得到。
    // 令牌必须在 goto 之前注入 localStorage，否则页面停在登录路由上什么都找不到。
    await authenticate(page, adminToken);
    await page.goto('/#/delivery/routes');
    const row = page
      .locator('tr')
      .filter({hasText: `${tag} 发车验收线路`})
      .first();
    await expect(row, '线路列表要能按名称找到本轮线路').toBeVisible({timeout: 20000});
    await button(row, '详情').click();
    // 详情已是独立页面（列表页不再有线路详情抽屉），无 Escape 关闭语义。
    await expect(page).toHaveURL(/#\/delivery\/routes\/\d+\?tab=base/);
    const detailPage = page.locator('.route-detail-page');
    await expect(button(detailPage, '发车'), '已完成线路不应再提供发车动作').toHaveCount(0);
    await expect(detailPage.getByText('出库单').first(), '发车后的线路要能看出库单线索').toBeVisible({timeout: 10000});

    // 履约标签页是这次新增的 UI 主体：状态必须来自后端返回值，签收/异常两行都要看得见，
    // 且已进入终态的行不能再有签收动作（按钮留在页上就是「可以再签一次」的错觉）。
    await detailPage.getByRole('tab', {name: accessibleName('履约')}).click();
    const pane = detailPage.locator('.ant-tabs-tabpane-active').last();
    await expect(pane.getByText('已签收').first(), '正常签收行要渲染出终态').toBeVisible({timeout: 10000});
    await expect(pane.getByText('异常签收').first().or(pane.getByText('异常').first()), '异常签收行要渲染出终态').toBeVisible();
    await expect(pane.getByText(`${tag} 客户拒收破损`).first(), '异常原因要看得见面').toBeVisible();
    await expect(button(pane, '签收'), '终态行不得再提供签收动作').toHaveCount(0);

    // 关闭详情 = 回列表页（页面左上「返回线路管理」；按钮内含图标，图标自身 aria-label
    // 会混进可及名，因此直接按专用类名定位而不是按纯文案的可及名匹配）。
    await detailPage.locator('.scm-detail-header__back').click();
    await expect(page).toHaveURL(/#\/delivery\/routes$/);
  });

  async function routeVersionOf(id: string | number): Promise<number> {
    return Number((await routeDetail(id)).route.version);
  }
});

// ---------------------------------------------------------------- 来源：e2e/scm-delivery-print.spec.ts
test.describe('scm-delivery-print', () => {
  /*
    Wave 5 配送打印追踪 E2E：自建线路 → 挂单 → 补坐标 → 规划，再验证双只读视角、GET 预览不计次、
    正式登记计次与幂等、按客户 PARTIAL 只补未打印订单，以及缺权限账号被服务端拒。

    原先这组用例靠外部注入的一次性管理员令牌门控（缺令牌即整组 skip），于是「文件存在」被当成了
    「场景已验收」；现在它自建临时账号与线路，收尾取消线路把订单退回候选池并删除账号。
    口径按 §1.2 钉死：打印只登记计次，绝不等于发货确认，也不扣减库存。
  */

  type Row = Record<string, any>;

  const accounts: TempAccounts = provisionTempAccounts('w5');
  const routeName = accounts.admin + '配送线路';
  /** 本轮夹具编码前缀：临时账号名本身就是逐次唯一的，拿它当标识避免与上一轮的商品/客户撞码。 */
  const routeTag = accounts.admin.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
  /** 停靠点坐标与仓库起点同坐标系（GCJ02）即可规划，取值只服务几何展示。 */
  const STOP_COORDS = [
    {lng: '113.22000000', lat: '23.28000000'},
    {lng: '113.23000000', lat: '23.29000000'},
  ];

  let api: APIRequestContext;
  let adminToken: string;
  let readOnlyToken: string;
  let routeId = '';
  /** 线路上挂的三张订单：同一客户两张（构成 PARTIAL）+ 另一客户一张（证明筛选不外溢）。 */
  let picked: Row[] = [];
  let primaryCustomerId = '';

  test.describe.configure({mode: 'serial'});

  async function ok<T = Row>(
    client: APIRequestContext,
    method: 'get' | 'post' | 'put',
    path: string,
    data?: unknown,
    key = randomUUID()
  ): Promise<T> {
    const response =
      method === 'get'
        ? await client.get(path)
        : // 计次类命令在后端强制要求 Idempotency-Key（缺失即 40069），写命令统一带键
          await client[method](path, {data, headers: {'Idempotency-Key': key}});
    const body = await response.json();
    expect(body.code, `${method.toUpperCase()} ${path}: ${body.msg}`).toBe(0);
    return body.data as T;
  }
  const get = <T = Row>(path: string, client: APIRequestContext = api) => ok<T>(client, 'get', path);
  /** 只取响应信封，用于反例断言（不预设成功）。 */
  async function envelope(client: APIRequestContext, path: string, data: Row, key = randomUUID()) {
    return (await (await client.post(path, {data, headers: {'Idempotency-Key': key}})).json()) as Row;
  }
  const routeVersion = async () => Number((await get(`/scm/delivery/routes/${routeId}`)).route.version);
  const ordersView = async () => await get<Row[]>(`/scm/delivery/routes/${routeId}/orders-view`);
  const customersView = async () => await get<Row[]>(`/scm/delivery/routes/${routeId}/customers-view`);
  const countOf = (rows: Row[], orderId: any) => Number(rows.find((o) => String(o.orderId) === String(orderId))?.printCount ?? -1);
  const totalPrintCount = (rows: Row[]) => rows.reduce((sum, o) => sum + Number(o.printCount), 0);

  test.beforeAll(async () => {
    adminToken = await login(accounts, accounts.admin);
    readOnlyToken = await login(accounts, accounts.readOnly);
    api = await apiClient(adminToken);

    // 起点仓库必须已定位且启用，否则后端会拒绝规划；从下拉选项里挑，不写死主键。
    const warehouses = await get<Row[]>('/scm/delivery/options/warehouses');
    const warehouse = warehouses.find((w) => w.status === 'ENABLED' && w.longitude != null);
    expect(warehouse, '候选仓库里需要至少一个已定位（含经纬度）且启用的仓库').toBeTruthy();

    // 订单全部自建并完成分拣：P1 之后候选池里的历史订单不会因为「已确认」就变成可配送，
    // 借用池子等于把本用例的成立与否交给上一轮留下的数据。
    const skuId = await createSku(api, routeTag, 'P');
    primaryCustomerId = await createLocatedCustomer(api, routeTag, 'A', '打印验收路A号');
    const otherCustomerId = await createLocatedCustomer(api, routeTag, 'B', '打印验收路B号');
    // 同一客户两张（构成 PARTIAL）+ 另一客户一张（证明按客户筛选不外溢）。
    picked = [
      await createDeliveryReadyOrder(api, {
        runTag: routeTag,
        customerId: primaryCustomerId,
        skuId,
        address: '打印验收路A号',
        warehouseId: warehouse!.id,
      }),
      await createDeliveryReadyOrder(api, {
        runTag: routeTag,
        customerId: primaryCustomerId,
        skuId,
        address: '打印验收路A号',
        warehouseId: warehouse!.id,
      }),
      await createDeliveryReadyOrder(api, {
        runTag: routeTag,
        customerId: otherCustomerId,
        skuId,
        address: '打印验收路B号',
        warehouseId: warehouse!.id,
      }),
    ];

    routeId = String(
      await ok(api, 'post', '/scm/delivery/routes', {
        routeName,
        deliveryDate: new Date(Date.now() + 86400000).toISOString().slice(0, 10),
        warehouseId: warehouse!.id,
      })
    );
    await ok(api, 'post', `/scm/delivery/routes/${routeId}/orders`, {
      version: await routeVersion(),
      orderIds: picked.map((p) => p.orderId),
      reason: 'E2E 挂单',
    });

    // 挂单时停靠点沿用订单地址快照；历史订单多数没有坐标，这里走真实的选点写入路径补齐。
    const stops: Row[] = (await get(`/scm/delivery/routes/${routeId}`)).stops;
    expect(stops.length, '挂单后应按客户生成停靠点').toBe(2);
    for (const [index, stop] of stops.entries()) {
      await ok(api, 'put', `/scm/delivery/routes/${routeId}/stops/${stop.id}`, {
        version: await routeVersion(),
        longitude: STOP_COORDS[index].lng,
        latitude: STOP_COORDS[index].lat,
        geomCrs: 'GCJ02',
      });
    }
    await ok(api, 'post', `/scm/delivery/routes/${routeId}/plan`, {version: await routeVersion(), reason: 'E2E 规划'});
  });

  test.afterAll(async () => {
    try {
      // 取消线路即把 ACTIVE 指派释放回候选池，避免把已确认订单长期占死。
      if (routeId && api)
        await ok(api, 'post', `/scm/delivery/routes/${routeId}/cancel`, {version: await routeVersion(), reason: 'E2E 收尾：释放候选订单'});
    } finally {
      await api?.get('/login/logout');
      await api?.dispose();
      accounts.cleanup();
    }
  });

  test('只读视角与 GET 预览都不计次，客户聚合与订单事实同源', async () => {
    const before = await ordersView();
    expect(before.length).toBe(3);
    for (const row of before) expect(['PRINTED', 'UNPRINTED']).toContain(row.printStatus);
    await get(`/scm/delivery/routes/${routeId}/print`);
    await get(`/scm/delivery/routes/${routeId}/map`);
    const after = await ordersView();
    expect(after.map((o) => [o.orderId, o.printCount])).toEqual(before.map((o) => [o.orderId, o.printCount]));
    for (const customer of await customersView()) {
      const own = after.filter((o) => String(o.customerId) === String(customer.customerId));
      expect(customer.printedOrderCount).toBe(own.filter((o) => Number(o.printCount) > 0).length);
      expect(customer.orderCount).toBe(own.length);
      expect(customer.printStatus).toBe(
        customer.printedOrderCount === 0 ? 'UNPRINTED' : customer.printedOrderCount === own.length ? 'PRINTED' : 'PARTIAL'
      );
    }
  });

  test('按订单登记：只有提交的订单计次，同幂等键重放不再累加，越界订单被拒', async () => {
    const [first, second] = picked;
    const before = await ordersView();
    const key = randomUUID();
    const result = await ok<Row>(
      api,
      'post',
      `/scm/delivery/routes/${routeId}/print/orders`,
      {version: await routeVersion(), orderIds: [first.orderId]},
      key
    );
    expect(result.orderCount).toBe(1);
    const after = await ordersView();
    expect(countOf(after, first.orderId)).toBe(countOf(before, first.orderId) + 1);
    expect(countOf(after, second.orderId), '未提交的订单不得被顺带计次').toBe(countOf(before, second.orderId));
    await ok<Row>(api, 'post', `/scm/delivery/routes/${routeId}/print/orders`, {version: await routeVersion(), orderIds: [first.orderId]}, key);
    expect(countOf(await ordersView(), first.orderId), '同 Idempotency-Key 重放不得重复计次').toBe(countOf(after, first.orderId));
    // 提交集含不属于本线路 ACTIVE 集合的订单，说明清单已过期，必须整笔拒绝
    const stale = await envelope(api, `/scm/delivery/routes/${routeId}/print/orders`, {
      version: await routeVersion(),
      orderIds: [first.orderId, -1],
    });
    expect(stale.ok, '越界订单集合应被拒绝').toBe(false);
    expect(countOf(await ordersView(), first.orderId), '被拒请求不得留下计次').toBe(countOf(after, first.orderId));
  });

  test('按客户登记：PARTIAL 客户只补未打印订单，已打印订单与其它客户不动', async () => {
    const before = await ordersView();
    const unprintedOfPrimary = before.filter((o) => String(o.customerId) === primaryCustomerId && Number(o.printCount) === 0);
    const printedOfPrimary = before.filter((o) => String(o.customerId) === primaryCustomerId && Number(o.printCount) > 0);
    const otherOrders = before.filter((o) => String(o.customerId) !== primaryCustomerId);
    expect(printedOfPrimary.length, '主客户需要先处于 PARTIAL（部分订单已打印）').toBeGreaterThan(0);
    expect(unprintedOfPrimary.length).toBeGreaterThan(0);
    expect(
      otherOrders.every((o) => Number(o.printCount) === 0),
      '另一客户此时应整体未打印'
    ).toBe(true);

    const result = await ok<Row>(api, 'post', `/scm/delivery/routes/${routeId}/print/customers`, {
      version: await routeVersion(),
      customerStatusFilter: 'PARTIAL',
      orderPrintFilter: 'UNPRINTED',
    });
    expect(result.orderCount).toBe(unprintedOfPrimary.length);
    const after = await ordersView();
    for (const order of unprintedOfPrimary) {
      expect(countOf(after, order.orderId), 'PARTIAL 客户的未打印订单应各计一次').toBe(1);
    }
    for (const order of printedOfPrimary) {
      expect(countOf(after, order.orderId), '已打印订单不得被重打').toBe(Number(order.printCount));
    }
    for (const order of otherOrders) {
      expect(countOf(after, order.orderId), '未纳入 PARTIAL 范围的客户不受影响').toBe(0);
    }
    expect((await customersView()).find((c) => String(c.customerId) === primaryCustomerId).printStatus).toBe('PRINTED');

    // 没有 PARTIAL 客户了：按同一筛选再生成必须被拒，而不是静默产出零单清单
    const empty = await envelope(api, `/scm/delivery/routes/${routeId}/print/customers`, {
      version: await routeVersion(),
      customerStatusFilter: 'PARTIAL',
      orderPrintFilter: 'UNPRINTED',
    });
    expect(empty.ok, '无匹配订单时应拒绝而非生成零单打印').toBe(false);
    // PARTIAL 是客户维度状态，作为订单筛选要被参数校验拦住
    const badFilter = await envelope(api, `/scm/delivery/routes/${routeId}/print/customers`, {
      version: await routeVersion(),
      customerStatusFilter: 'ALL',
      orderPrintFilter: 'PARTIAL',
    });
    expect(badFilter.code, badFilter.msg).toBe(30001);
    // 不给客户名单也不给状态范围时，禁止无选择地重打整条线路
    const unscoped = await envelope(api, `/scm/delivery/routes/${routeId}/print/customers`, {version: await routeVersion()});
    expect(unscoped.ok, 'ALL + 空名单必须被拒').toBe(false);
    expect(totalPrintCount(await ordersView())).toBe(totalPrintCount(after));
  });

  test('打印不改线路状态、不确认发货', async () => {
    expect((await get(`/scm/delivery/routes/${routeId}`)).route.status).toBe('PLANNED');
    for (const order of picked) {
      expect((await get(`/scm/order/detail/${order.orderId}`)).status, '打印不等于发货确认').toBe('CONFIRMED');
    }
  });

  test('缺 scm:delivery:route:print 的只读账号：读视角放行、打印与规划被服务端拒', async () => {
    const readOnly = await apiClient(readOnlyToken);
    const countsBefore = await ordersView();
    try {
      expect((await (await readOnly.get('/scm/delivery/routes?pageNum=1&pageSize=20')).json()).code).toBe(0);
      await get<Row[]>(`/scm/delivery/routes/${routeId}/orders-view`, readOnly);
      await get<Row[]>(`/scm/delivery/routes/${routeId}/customers-view`, readOnly);
      // GET 预览同样受 print 权限保护，不能靠前端隐藏按钮当权限
      expect((await (await readOnly.get(`/scm/delivery/routes/${routeId}/print`)).json()).code).toBe(30005);
      for (const [path, data] of [
        [`/scm/delivery/routes/${routeId}/print/orders`, {version: await routeVersion(), orderIds: [picked[0].orderId]}],
        [`/scm/delivery/routes/${routeId}/print/customers`, {version: await routeVersion(), customerStatusFilter: 'UNPRINTED'}],
        [`/scm/delivery/routes/${routeId}/plan`, {version: await routeVersion(), reason: '越权'}],
      ] as [string, Row][]) {
        expect((await envelope(readOnly, path, data)).code, `${path} 必须被权限拦截`).toBe(30005);
      }
      expect(totalPrintCount(await ordersView()), '被拒请求不得改变计次').toBe(totalPrintCount(countsBefore));
    } finally {
      await readOnly.dispose();
    }
  });

  test('页面接线：打印标签双视角可切换，登记只发计次请求且不碰库存', async ({page}) => {
    await authenticate(page, adminToken);
    const writes: string[] = [];
    page.on('request', (req) => {
      if (req.method() !== 'GET') writes.push(`${req.method()} ${new URL(req.url()).pathname}`);
    });
    await page.goto('/#/delivery/routes');
    await expect(page.locator('#scm-delivery-route-table')).toBeVisible();
    await page.getByPlaceholder('线路名称 / 编号').fill(routeName);
    await page.keyboard.press('Enter');
    const row = page.locator('#scm-delivery-route-table tbody tr.ant-table-row').filter({hasText: routeName});
    await expect(row).toHaveCount(1);
    await row.getByRole('button', {name: accessibleName('详情')}).click();

    // 详情已是独立页面；打印视角挂在页面的「配送打印」Tab 上。
    await expect(page).toHaveURL(/#\/delivery\/routes\/\d+\?tab=base/);
    const detailPage = page.locator('.route-detail-page');
    const pane = detailPage.locator('.ant-tabs-tabpane-active');
    await detailPage.getByRole('tab', {name: accessibleName('配送打印')}).click();
    // 打印面板就位：默认「按订单」视角（原常驻口径说明已按文案治理删除，语义由下面的接口计数断言承载）
    await expect(pane.getByText('按订单', {exact: true}).first()).toBeVisible();
    // 默认「按订单」视角：未勾选任何行时登记按钮必须禁用，避免无选择地重打整条线路
    const record = pane.getByRole('button', {name: /^生\s*成\s*打\s*印/});
    await expect(record).toBeDisabled();
    // 「已登记打印」两次点击文案相同，靠文案等待会让第一次的 toast 冒充第二次；只有各自的响应才能证明登记真的发了
    const register = async (kind: 'orders' | 'customers') => {
      const [response] = await Promise.all([
        page.waitForResponse((res) => res.url().includes(`/print/${kind}`) && res.request().method() === 'POST'),
        record.click(),
      ]);
      const body = await response.json();
      expect(body.code, `页面登记的 POST /print/${kind} 应成功：${body.msg}`).toBe(0);
    };
    const before = await ordersView();
    // 线路上只有 3 张订单，打印面板渲染的是卡片列表（≤3 行），不再是表格行。
    await pane.locator('.print-card').first().locator('.ant-checkbox-input').check();
    await expect(record).toBeEnabled();
    await expect(record).toContainText('登记 1');
    await register('orders');
    expect(totalPrintCount(await ordersView()), '页面登记只加一次').toBe(totalPrintCount(before) + 1);

    // 切到「按客户」：状态与订单范围两个筛选同时生效，未勾选时按状态范围提交
    await pane.getByText('按客户', {exact: true}).click();
    const selects = pane.locator('.smart-table-btn-block .ant-select');
    await expect(selects).toHaveCount(2);
    await expect(record).toBeDisabled();
    await selects.first().locator('.ant-select-selector').click();
    await page.locator('.ant-select-dropdown:visible .ant-select-item-option-content', {hasText: '未打印客户'}).click();
    await selects.nth(1).locator('.ant-select-selector').click();
    await page.locator('.ant-select-dropdown:visible .ant-select-item-option-content', {hasText: '仅未打印订单'}).click();
    await expect(record).toBeEnabled();
    const customersBefore = await customersView();
    const unprintedCustomer = customersBefore.find((c) => c.printStatus === 'UNPRINTED');
    expect(unprintedCustomer, '另一客户此时应整体未打印').toBeTruthy();
    const printedBefore = await ordersView();
    await register('customers');
    const printedAfter = await ordersView();
    const ownOrders = printedAfter.filter((o) => String(o.customerId) === String(unprintedCustomer.customerId));
    expect(
      ownOrders.every((o) => Number(o.printCount) === 1),
      '未打印客户的订单各计一次'
    ).toBe(true);
    for (const order of printedBefore.filter((o) => String(o.customerId) !== String(unprintedCustomer.customerId))) {
      expect(countOf(printedAfter, order.orderId), '已打印客户不受影响').toBe(Number(order.printCount));
    }
    // 关闭详情 = 回线路管理列表（左上「返回线路管理」；按钮内含图标，图标自身 aria-label
    // 会混进可及名，因此按专用类名定位）。页面已无抽屉。
    await detailPage.locator('.scm-detail-header__back').click();
    // 列表查询条件按登录人记忆（Wave 8），返回列表会带回 keyword，只校验路由本身。
    await expect(page).toHaveURL(/#\/delivery\/routes(\?.*)?$/);
    // 全程只有两次打印登记：任何其它 SCM 写接口（库存、出库、发货）都不该被页面偷偷发起。
    // 底座 Layout 自身的非业务请求（如 /support/feedback/query 轮询）不在这条约束的语义范围内。
    const scmWrites = writes.filter((u) => u.includes(' /scm/'));
    expect(
      scmWrites.every((u) => /^POST \/scm\/delivery\/routes\/\d+\/print\/(orders|customers)$/.test(u)),
      scmWrites.join('\n')
    ).toBe(true);
    expect(new Set(scmWrites).size).toBe(2);
    await page.screenshot({path: '../.runtime/w5-delivery-print-tab.png', fullPage: true});
  });
});

// ---------------------------------------------------------------- 来源：e2e/scm-sorting.spec.ts
test.describe('scm-sorting', () => {
  /*
    P1 分拣管理的浏览器验收（主线计划 P1「完成标准」：按订单与按商品同一套事实、不直接修改库存、
    分拣结果可稳定供 Delivery L3 使用、重复生成不产生重复业务事实、正式分拣角色可完整使用）。

    四条边界各自都有正反两侧的取证：
      * 不回写订单（实发量与结算额逐字段比对基线）、不写库存（流水总数前后不变）；
      * 范围 = 授权仓 ∩ 受指派人 —— 断言一律用 SCM_SORTER / SCM_STOREKEEPER_LEAD 这两个
        administrator_flag=false 的角色账号；未指派任务对分拣员必须是 30005，而不是「不存在」；
      * 差异必填原因、未全处理不能完成（41125），补全后才 COMPLETED；
      * 打印只计次：预览不计数、登记后 +1，且状态与版本都不动。

    夹具全部自建（商品 / 客户 / 已确认订单 / DRAFT 线路），不借共享候选池 —— P1 之后池子里的
    历史订单不会因为「已确认」就变成可配送，借用等于把本轮结论交给上一轮留下的数据。
    收尾显式清空 employee_warehouse_scope：删账号不会连带回收授权行。
  */

  type Row = Record<string, any>;
  type Envelope = {code: number; msg?: string; data?: Row; status?: number};
  type Paged = {list: Row[]; total: number};
  type Fixture = {
    orderId: string;
    orderNo: string;
    itemId: string;
    /** 确认时算出的基线；非标品分拣后会按实称量重算（见「实称量回写订单」决策）。 */
    actualQuantity: string;
    settlementLineAmount: string | null;
  };

  const accounts: TempAccounts = provisionTempAccounts('p1', undefined, ['SCM_SORTER', 'SCM_STOREKEEPER_LEAD']);
  const tag = accounts.admin.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
  const today = new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Shanghai'}).format(new Date());
  const REMARK = `${tag} 两句任务`;

  let api: APIRequestContext;
  let sorterToken = '';
  let leadToken = '';
  let sorterClient: APIRequestContext;
  let leadClient: APIRequestContext;
  let sorterEmployeeId = 0;
  let leadEmployeeId = 0;
  let warehouseId = 0;
  let warehouseName = '';
  let routeId = '';
  let customerId = '';
  let skuId = '';
  let taskOne: Fixture = {} as Fixture;
  let taskTwo: Fixture = {} as Fixture;
  /** 第三张订单只用来造「未指派」任务：它全程不参与上面的两句任务。 */
  let unassignedOrder: Fixture = {} as Fixture;
  let taskNo = '';
  let movementsAtStart = 0;

  test.describe.configure({mode: 'serial'});

  async function ok<T = Row>(client: APIRequestContext, method: 'get' | 'post', path: string, data?: unknown, key = randomUUID()): Promise<T> {
    const response = method === 'get' ? await client.get(path) : await client.post(path, {data, headers: {'Idempotency-Key': key}});
    const body = await response.json();
    expect(body.code, `${method.toUpperCase()} ${path}: ${body.msg}`).toBe(0);
    return body.data as T;
  }

  const get = <T = Row>(path: string, client: APIRequestContext = api) => ok<T>(client, 'get', path);
  const post = <T = Row>(path: string, data?: unknown, client: APIRequestContext = api) => ok<T>(client, 'post', path, data);

  /** 负向入口：返回原始信封，不因为 code != 0 就先把用例判红。 */
  async function envelope(client: APIRequestContext, path: string, data?: unknown, method: 'get' | 'post' = 'post'): Promise<Envelope> {
    const response = method === 'get' ? await client.get(path) : await client.post(path, {data, headers: {'Idempotency-Key': randomUUID()}});
    const body = await response.json();
    return {...body, status: response.status()} as Envelope;
  }

  const grantWarehouses = (employeeId: number, warehouseIds: number[]) => ok(api, 'post', '/scm/warehouse/scope/update', {employeeId, warehouseIds});

  const taskPage = (client: APIRequestContext) => ok<Paged>(client, 'get', '/scm/sorting/tasks?pageNum=1&pageSize=100');
  const taskOf = async (client: APIRequestContext, no: string) => (await taskPage(client)).list.find((r) => String(r.taskNo) === no);
  const taskDetail = (client: APIRequestContext, id: string | number) => ok<Row>(client, 'get', `/scm/sorting/tasks/${id}`);
  const routeVersion = async () => Number((await get<Row>(`/scm/delivery/routes/${routeId}`)).route.version);
  /** 候选池按单号精确筛：只断「不在前 N 条里」会被大池子糊过去。 */
  const candidateOf = async (orderNo: string) =>
    (await ok<Paged>(api, 'get', `/scm/delivery/candidate-orders?pageNum=1&pageSize=50&keyword=${orderNo}`)).list;
  const movementTotal = () => ok<Paged>(api, 'post', '/scm/inventory/movement/query', {pageNum: 1, pageSize: 1}).then((p) => Number(p.total));
  const button = (scope: Page | Locator, text: string) => scope.getByRole('button', {name: accessibleName(text)});

  /**
   * 任务列表按创建时间倒序分页，而 E2E 按既有约定只回收临时账号、不回收任务行 ——
   * 开发库累计几十条任务后目标行会落到第一页之外。所以点行内按钮前先按单号筛，
   * 断的是「筛出来唯一一行」，不是「它恰好排在第一页」。
   */
  async function filterTaskRow(page: Page) {
    const keyword = page.locator('.smart-query-form input').first();
    await keyword.fill(taskNo);
    await keyword.press('Enter');
    await expect(page.locator('.ant-table-tbody tr').filter({hasText: taskNo})).toHaveCount(1);
  }

  async function confirmedOrder(quantity: string, seq: number): Promise<Fixture> {
    let order = await post<Row>('/scm/order/create', {
      customerId,
      orderSource: 'ADMIN',
      address: {receiverName: '分拣验收', receiverPhone: '13800000000', address: '分拣验收路1号'},
      expectDeliveryTime: `${today} 09:00:00`,
      remark: `${tag}-${seq}`,
      items: [{skuId, orderedQuantity: quantity, manualPriceOverride: false}],
    });
    order = await post<Row>('/scm/order/submit', {orderId: order.orderId, version: order.version});
    const line = order.items[0];
    await post('/scm/order/item/actual-quantity', {
      orderId: order.orderId,
      itemId: line.itemId,
      version: line.version,
      actualQuantity: quantity,
      reason: `${tag} 夹具实重`,
    });
    const detail = await get<Row>(`/scm/order/detail/${order.orderId}`);
    const confirmed = await post<Row>('/scm/order/confirm', {orderId: order.orderId, version: detail.version});
    // 基线必须取在确认**之后**：结算额是确认动作算出来的，取在确认前会拿到 null，
    // 于是「分拣不得改写结算额」这条断言会被自己造的时序差判红。
    const settled = await get<Row>(`/scm/order/detail/${order.orderId}`);
    const item = (settled.items as Row[]).find((i) => String(i.itemId) === String(line.itemId));
    expect(item, '订单明细在确认后可回读').toBeTruthy();
    return {
      orderId: String(order.orderId),
      orderNo: String(confirmed.orderNo ?? detail.orderNo),
      itemId: String(line.itemId),
      actualQuantity: String(item.actualQuantity),
      settlementLineAmount: item.settlementLineAmount == null ? null : String(item.settlementLineAmount),
    };
  }

  test.beforeAll(async () => {
    api = await apiClient(await login(accounts, accounts.admin));
    sorterToken = await login(accounts, accounts.roleAccounts.SCM_SORTER);
    leadToken = await login(accounts, accounts.roleAccounts.SCM_STOREKEEPER_LEAD);
    sorterEmployeeId = accounts.employeeIds[accounts.roleAccounts.SCM_SORTER];
    leadEmployeeId = accounts.employeeIds[accounts.roleAccounts.SCM_STOREKEEPER_LEAD];
    expect(sorterEmployeeId, '未建立 SCM_SORTER 账号，分拣范围断言将没有证据').toBeGreaterThan(0);
    expect(leadEmployeeId, '未建立 SCM_STOREKEEPER_LEAD 账号').toBeGreaterThan(0);
    sorterClient = await apiClient(sorterToken);
    leadClient = await apiClient(leadToken);

    const warehouses = await get<Row[]>('/scm/warehouse/list');
    const enabled = warehouses.find((w) => w.status === 'ENABLED');
    expect(enabled, '开发库需要至少一个启用仓库').toBeTruthy();
    warehouseId = Number(enabled.id ?? enabled.warehouseId);
    warehouseName = String(enabled.name ?? '');
    expect(warehouseName, '仓库名要能作为下拉筛选关键字').not.toBe('');
    // 两维范围都要靠授权行才成立：两个角色账号都授到同一个仓。
    await grantWarehouses(sorterEmployeeId, [warehouseId]);
    await grantWarehouses(leadEmployeeId, [warehouseId]);

    skuId = await createSku(api, tag, 'S');
    customerId = await createLocatedCustomer(api, tag, 'S', '分拣验收路1号');
    taskOne = await confirmedOrder('3.0000', 1);
    taskTwo = await confirmedOrder('2.0000', 2);
    unassignedOrder = await confirmedOrder('1.0000', 3);
    movementsAtStart = await movementTotal();

    // DRAFT 线路只用来验「分拣完成才可排线」这一半交接；不规划、不发车。
    routeId = String(await post('/scm/delivery/routes', {routeName: `${tag} 分拣交接线路`, deliveryDate: today, warehouseId}));
  });

  test.afterAll(async () => {
    try {
      if (routeId && api) {
        await ok(api, 'post', `/scm/delivery/routes/${routeId}/cancel`, {version: await routeVersion(), reason: 'P1 收尾：释放线路'});
      }
      await grantWarehouses(sorterEmployeeId, []);
      await grantWarehouses(leadEmployeeId, []);
    } finally {
      for (const client of [api, sorterClient, leadClient]) {
        await client?.get('/login/logout');
        await client?.dispose();
      }
      accounts.cleanup();
    }
  });

  test('未分拣的已确认订单不是配送候选：按单号筛候选池恰好筛不到它', async () => {
    expect(await candidateOf(taskOne.orderNo), '订单已确认但没分拣，不该进候选池').toEqual([]);
    const rejected = await envelope(api, `/scm/delivery/routes/${routeId}/orders`, {
      version: await routeVersion(),
      orderIds: [taskOne.orderId],
      reason: 'P1 未分拣不得排线',
    });
    expect(rejected.code, '组单侧必须给出可分辨的「不符合配送条件」').toBe(41102);
  });

  test('主管在页面上建单并派给分拣员，任务以 PENDING 落库', async ({page}) => {
    authenticate(page, leadToken);
    await page.goto('/#/sorting/tasks');
    await button(page, '新建分拣任务').click();
    const modal = page.locator('.ant-modal-content').last();
    // antd 的下拉在关闭后仍留在 DOM 里，`:visible` / role 都会命中上一个刚收起的下拉；
    // 因此统一取「最后一个 dropdown」并在里面按 option 文案点选，失败时把面板实际内容带进断言消息。
    const pickOption = async (label: string, keyword: string, search: boolean) => {
      const select = modal.locator(`.ant-form-item:has-text("${label}") .ant-select`).first();
      await select.click();
      const dropdown = page.locator('.ant-select-dropdown').last();
      await expect(dropdown).toBeVisible();
      if (search) {
        // 员工列表在开发库里有上百行，虚拟滚动下目标项不在 DOM 里，必须先过滤。
        await select.locator('.ant-select-selection-search-input').first().type(keyword, {delay: 20});
      }
      const option = dropdown.locator('.ant-select-item-option-content').filter({hasText: keyword}).first();
      await expect(option, `「${label}」下拉过滤 ${keyword} 后没有命中项；面板实际内容：${await dropdown.innerText()}`).toBeVisible({
        timeout: 8000,
      });
      await option.click();
    };
    await pickOption('仓库', warehouseName, false);
    await pickOption('受指派人', accounts.roleAccounts.SCM_SORTER, true);
    await modal.getByPlaceholder('可选').fill(REMARK);

    // 一次查询里勾两行：筛选条件一变，页面会清空已勾选项（隐藏的行不能继续计入创建数量），
    // 所以按单号查一次勾一次是勾不到两行的。两张单同属一个客户，按客户名筛即可同时命中。
    await modal.getByPlaceholder('订单 / 客户 / 商品').fill(`${tag}客户S`);
    await button(modal, '查询候选行').click();
    for (const orderNo of [taskOne.orderNo, taskTwo.orderNo]) {
      const line = modal.locator('.ant-table-tbody tr').filter({hasText: orderNo}).first();
      await expect(line, `按客户筛候选行后应看到订单 ${orderNo}`).toBeVisible();
      await line.locator('input[type=checkbox]').check();
    }
    await button(modal, '创建任务（2 行）').click();
    await expect(page.locator('.ant-message-notice')).toContainText('成功', {timeout: 20000});

    const created = (await taskPage(leadClient)).list.find((r) => String(r.remark) === REMARK);
    expect(created, '新任务应出现在主管的列表里').toBeTruthy();
    taskNo = String(created.taskNo);
    expect(created.status).toBe('PENDING');
    expect(Number(created.itemCount)).toBe(2);
    expect(Number(created.processedCount)).toBe(0);
    expect(Number(created.assigneeEmployeeId)).toBe(sorterEmployeeId);
    expect(String(created.taskNo)).toMatch(/^SRT\d{14}$/);
  });

  test('范围是授权仓 ∩ 受指派人：分拣员只看派给自己的，未指派任务回 30005', async () => {
    // 未指派任务由超管建，然后按分拣员身份读 —— 越权必须是 30005，不能伪装成「不存在」。
    const free = await post<Row>('/scm/sorting/tasks', {
      warehouseId,
      salesOrderItemIds: [unassignedOrder.itemId],
      remark: `${tag} 未指派`,
    });
    const freeTaskId = free.task.id;
    const denied = await envelope(sorterClient, `/scm/sorting/tasks/${freeTaskId}`, undefined, 'get');
    expect(denied.code, '未指派任务对分拣员必须是越权').toBe(30005);
    expect(
      (await envelope(api, `/scm/sorting/tasks/${freeTaskId}`, undefined, 'get')).code,
      '超管按 break-glass 可读，说明上面的 30005 来自范围判定而不是数据缺失'
    ).toBe(0);

    const visible = await taskPage(sorterClient);
    expect(visible.list.length, '分拣员至少要看到派给自己的那条任务').toBeGreaterThan(0);
    expect(
      visible.list.every((r) => Number(r.assigneeEmployeeId) === sorterEmployeeId),
      '分拣员的列表必须恒等于派给本人的任务'
    ).toBeTruthy();
    expect(visible.list.map((r) => r.taskNo)).toContain(taskNo);
    const leadVisible = await taskPage(leadClient);
    expect(
      leadVisible.list.map((r) => r.taskNo),
      '队列管理者应跨指派人可见'
    ).toEqual(expect.arrayContaining([taskNo, String(free.task.taskNo)]));
  });

  test('差异必填原因、未全处理不能完成；补全后才 COMPLETED', async () => {
    const before = await taskDetail(sorterClient, (await taskOf(sorterClient, taskNo)).id);
    const [lineA, lineB] = before.items;

    const missingReason = await envelope(sorterClient, `/scm/sorting/tasks/${before.task.id}/entry`, {
      items: [{id: lineA.id, version: lineA.version, sortedQuantity: '2.0000', result: 'SHORT'}],
    });
    expect(missingReason.code, '少拣不写原因必须被拒').not.toBe(0);
    expect((await taskDetail(sorterClient, before.task.id)).task.status, '被拒的录入不得留下任何痕迹，也不得把任务推成 SORTING').toBe('PENDING');

    await ok(sorterClient, 'post', `/scm/sorting/tasks/${before.task.id}/entry`, {
      items: [{id: lineA.id, version: lineA.version, sortedQuantity: '2.0000', result: 'SHORT', reason: '当日到货不足'}],
    });
    const started = await taskDetail(sorterClient, before.task.id);
    expect(started.task.status).toBe('SORTING');
    expect(Number(started.task.processedCount)).toBe(1);
    expect(Number(started.task.version)).toBeGreaterThan(Number(before.task.version));

    const incomplete = await envelope(sorterClient, `/scm/sorting/tasks/${before.task.id}/complete`, {version: started.task.version});
    expect(incomplete.code, '还有未处理明细时不能完成').toBe(41125);

    await ok(sorterClient, 'post', `/scm/sorting/tasks/${before.task.id}/entry`, {
      items: [{id: lineB.id, version: lineB.version, sortedQuantity: lineB.plannedQuantitySnapshot, result: 'NORMAL'}],
    });
    const ready = await taskDetail(sorterClient, before.task.id);
    await ok(sorterClient, 'post', `/scm/sorting/tasks/${before.task.id}/complete`, {version: ready.task.version});
    const done = await taskDetail(sorterClient, before.task.id);
    expect(done.task.status).toBe('COMPLETED');
    expect(Number(done.task.processedCount)).toBe(2);

    // 重复提交同一行（带过期版本）必须被乐观锁拦住，而不是覆盖成第三条数。
    const stale = await envelope(sorterClient, `/scm/sorting/tasks/${before.task.id}/entry`, {
      items: [{id: lineA.id, version: lineA.version, sortedQuantity: '9.0000', result: 'OVER', reason: '过期版本重放'}],
    });
    expect(stale.code, '已完成任务不接受再录入，且过期版本必被拒').not.toBe(0);
  });

  test('分拣不写库存；非标品实称量回写订单行与结算额，完成后的订单成为配送候选并可排线', async () => {
    // 2026-10-27 决策：分拣录入（手工与秤端走同一条命令）把非标品实称量写回已确认订单行，
    // 并重算行结算额与订单合计。分拣本身仍然绝不写库存流水。
    expect(await movementTotal(), '整条分拣链不得产生任何库存流水').toBe(movementsAtStart);
    const completed = await taskDetail(sorterClient, (await taskOf(sorterClient, taskNo)).id);
    for (const item of (completed.items as Row[]).filter((row) => row.sourceType !== 'PROMOTION_GIFT')) {
      const detail = await get<Row>(`/scm/order/detail/${item.salesOrderId}`);
      const line = (detail.items as Row[]).find((row) => String(row.itemId) === String(item.salesOrderItemId));
      expect(line, `订单 ${item.orderNoSnapshot} 应能回读到分拣行对应的明细`).toBeTruthy();
      expect(String(line.actualQuantity), `订单 ${item.orderNoSnapshot} 实发量应为分拣实称量`).toBe(
        String(item.sortedQuantity)
      );
      const expected = (Number(item.sortedQuantity) * Number(line.lockedUnitPrice)).toFixed(4);
      expect(String(line.settlementLineAmount), '结算额按实称量 × 锁定单价重算').toBe(expected);
    }

    expect(await candidateOf(taskOne.orderNo), '分拣完成后应成为候选').toHaveLength(1);
    await ok(api, 'post', `/scm/delivery/routes/${routeId}/orders`, {
      version: await routeVersion(),
      orderIds: [taskOne.orderId, taskTwo.orderId],
      reason: 'P1 完成后排线',
    });
    const route = await get<Row>(`/scm/delivery/routes/${routeId}`);
    expect(Number(route.route.orderCount ?? route.orders.length)).toBe(2);
  });

  test('重开保留已录内容并让订单立刻掉出配送候选', async ({page}) => {
    authenticate(page, leadToken);
    await page.goto('/#/sorting/tasks');
    await filterTaskRow(page);
    // 操作列收敛为「详情 + 当前下一步 + 更多」，重开属低频动作，只在「更多」菜单里出现。
    const taskRow = page.locator('.ant-table-tbody tr').filter({hasText: taskNo}).first();
    await taskRow.getByRole('button', {name: /更多/}).click();
    await page.locator('.ant-dropdown:visible').getByText('重开', {exact: true}).click();
    const modal = page.locator('.ant-modal-content').last();
    await modal.locator('textarea, input[type=text]').last().fill('客户改量，需要重分拣');
    await button(modal, '确认').click();
    await expect(page.locator('.ant-message-notice')).toContainText('成功', {timeout: 20000});

    const reopened = await taskOf(sorterClient, taskNo);
    const detail = await taskDetail(sorterClient, reopened.id);
    expect(detail.task.status).toBe('SORTING');
    expect(detail.task.completedAt, '曾完成的时间要留下来').toBeTruthy();
    expect(detail.items.map((i: Row) => i.result)).toEqual(['SHORT', 'NORMAL']);
    expect(detail.items.map((i: Row) => i.reason).filter(Boolean), '只有差异行才带原因').toEqual(['当日到货不足']);

    expect(await candidateOf(taskOne.orderNo), '重开只改任务状态就应让订单掉出候选').toEqual([]);
  });

  test('打印只计次：预览不计数，登记后累加且状态与版本都不动', async ({page}) => {
    const target = await taskOf(sorterClient, taskNo);
    const before = await taskDetail(sorterClient, target.id);
    expect(before.task.status, '打印要求 SORTING 或 COMPLETED').toBe('SORTING');

    authenticate(page, sorterToken);
    await page.goto('/#/sorting/tasks');
    await filterTaskRow(page);
    // 打印同样收在「更多」菜单里，行内只保留详情与当前下一步。
    const printRow = page.locator('.ant-table-tbody tr').filter({hasText: taskNo}).first();
    await printRow.getByRole('button', {name: /更多/}).click();
    await page.locator('.ant-dropdown:visible').getByText('打印', {exact: true}).click();
    const preview = page.locator('.ant-modal-content').last();
    await expect(preview).toContainText(taskNo);
    expect((await taskDetail(sorterClient, target.id)).task.printCount, '预览不得计次').toBe(before.task.printCount);

    await button(preview, '登记打印').click();
    await expect(page.locator('.ant-message-notice')).toContainText('已登记打印', {timeout: 20000});
    const after = await taskDetail(sorterClient, target.id);
    expect(Number(after.task.printCount)).toBe(Number(before.task.printCount) + 1);
    expect(after.task.status).toBe('SORTING');
    expect(Number(after.task.version), '打印不改版本，否则会把别人的编辑顶成假冲突').toBe(Number(before.task.version));
  });

  test('按商品汇总是同一套事实的只读视角，页面不提供任何录入口', async ({page}) => {
    authenticate(page, leadToken);
    await page.goto('/#/sorting/summary');
    await expect(page.locator('.ant-table-tbody')).toContainText('kg', {timeout: 20000});
    expect(await page.locator('.ant-input-number').count(), '汇总视角不许承载录入').toBe(0);
    expect(
      await page
        .getByRole('button')
        .filter({hasText: /提交|录入|新建|完成|重开|取消/})
        .count(),
      '汇总视角不得出现写动作入口'
    ).toBe(0);

    const summary = await ok<Paged>(leadClient, 'get', '/scm/sorting/summary?pageNum=1&pageSize=100');
    const row = summary.list.find((r) => Number(r.skuId) === Number(skuId) && String(r.saleUnitSnapshot) === 'kg' && Number(r.lineCount) >= 2);
    expect(row, '两句任务应在汇总里并成一行同单位的聚合').toBeTruthy();
    // 汇总量与逐行事实必须一致：同一 SKU + 单位下，计划合计 = 各行计划量之和。
    const items = (await taskPage(leadClient)).list.filter((t) => Number(t.warehouseId) === warehouseId);
    const lines = [];
    for (const task of items) {
      lines.push(...(await taskDetail(leadClient, task.id)).items.filter((i: Row) => i.occupationStatus === 'ACTIVE'));
    }
    const planned = lines
      .filter((i) => i.saleUnitSnapshot === 'kg' && Number(i.skuId) === Number(row.skuId))
      .reduce((sum, i) => sum + Number(i.plannedQuantitySnapshot), 0);
    expect(Number(row.plannedQuantity)).toBeCloseTo(planned, 4);
  });

  test('标品一键分拣显示服务端预览，只确认所选行且不完成任务', async ({page}) => {
    const standardSkuId = await createSku(api, tag, 'STANDARD', 'STANDARD');
    let order = await post<Row>('/scm/order/create', {
      customerId,
      orderSource: 'ADMIN',
      address: {receiverName: '标品验收', receiverPhone: '13800000000', address: '分拣验收路1号'},
      items: [{skuId: standardSkuId, orderedQuantity: '2.0000', manualPriceOverride: false}],
    });
    if (order.status === 'DRAFT') {
      order = await post<Row>('/scm/order/submit', {orderId: order.orderId, version: order.version});
    }
    if (order.status !== 'CONFIRMED') {
      order = await post<Row>('/scm/order/confirm', {orderId: order.orderId, version: order.version});
    }
    const created = await post<Row>('/scm/sorting/tasks', {
      warehouseId,
      assigneeEmployeeId: sorterEmployeeId,
      salesOrderItemIds: [order.items[0].itemId],
      remark: `${tag} 标品批量`,
    });
    const taskId = created.task.id;
    const beforeMovements = await movementTotal();
    authenticate(page, sorterToken);
    await page.goto('/#/sorting/tasks');
    const keyword = page.locator('.smart-query-form input').first();
    await keyword.fill(String(created.task.taskNo));
    await keyword.press('Enter');
    const row = page.locator('.ant-table-tbody tr').filter({hasText: String(created.task.taskNo)});
    await button(row, '详情').click();
    await button(page.locator('.ant-drawer-content'), '标品一键分拣').click();
    const modal = page.locator('.ant-modal-content:visible').last();
    await expect(modal).toContainText('2.0000');
    // 文案里不再写「不会自动完成任务」，改由后面的任务状态断言证明；这里只钉「没勾选就没有可确认的行」
    await expect(modal).toContainText('已选择 0 条明细');
    await expect(button(modal, '确认所选标品')).toBeDisabled();
    await modal.locator('.ant-table-tbody input[type=checkbox]').first().check();
    await button(modal, '确认所选标品').click();
    await expect(modal).not.toBeVisible();
    const confirmed = await taskDetail(sorterClient, taskId);
    expect(confirmed.task.status).toBe('SORTING');
    expect(confirmed.items[0].sortedQuantity).toBe('2.0000');
    expect(confirmed.items[0].result).toBe('NORMAL');
    const history = await get<Row>(`/scm/sorting/tasks/${taskId}/items/${confirmed.items[0].id}/parts`, sorterClient);
    expect(history.records).toHaveLength(1);
    expect(await movementTotal()).toBe(beforeMovements);
    const denied = await envelope(leadClient, `/scm/sorting/tasks/${taskId}/standard-preview`, undefined, 'get');
    expect(denied.code).not.toBe(0);
  });
});
