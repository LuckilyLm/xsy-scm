/* W6 real PostgreSQL/API/browser acceptance. Account secrets stay in memory. */
import {test,expect,request,type APIRequestContext,type Page} from '@playwright/test';
import {randomBytes,randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import smCrypto from 'sm-crypto';

const apiUrl='http://127.0.0.1:18080';
const name='w6_e2e_'+Date.now().toString(36),password='W6@'+randomBytes(8).toString('hex');
const env={...process.env,W6_E2E_NAME:name,W6_E2E_PASSWORD:password};
let api:APIRequestContext,token:string;
let warehouseId:string,skuId:string,skuCode:string,emptySkuCode:string,supplierId:string,customerId:string;
let orderA:any;
test.describe.configure({mode:'serial'});

async function login(account:string){
  const c=await request.newContext({baseURL:apiUrl});
  const captcha=(await(await c.get('/login/getCaptcha')).json()).data;
  const source=readFileSync('src/lib/encrypt.ts','utf8');
  const key=/const SM4_KEY = '([^']+)'/.exec(source)![1];
  const encrypted=Buffer.from(smCrypto.sm4.encrypt(password,Buffer.from(key).toString('hex'))).toString('base64');
  const r=await(await c.post('/login',{data:{loginName:account,password:encrypted,captchaUuid:captcha.captchaUuid,captchaCode:captcha.captchaText,loginDevice:1}})).json();
  expect(r.code).toBe(0);
  await c.dispose();
  return r.data.token;
}
async function raw(path:string,data:unknown,key=randomUUID()){return await(await api.post(path,{data,headers:{'Idempotency-Key':key}})).json();}
async function post(path:string,data:unknown,key=randomUUID()){const r=await raw(path,data,key);expect(r.code,`${path}: ${r.msg}`).toBe(0);return r.data;}
async function get(path:string){const r=await(await api.get(path)).json();expect(r.code,`${path}: ${r.msg}`).toBe(0);return r.data;}
function browse(page:Page,path:string){return page.addInitScript(v=>localStorage.setItem('smart_admin_user_token',v),token).then(()=>page.goto('/#'+path));}
async function search(page:Page){await page.getByRole('button',{name:/^查\s*询$/}).click();}
const balanceRows=(page:Page,text:string)=>page.locator('#scm-inventory-balance-table tr').filter({hasText:text});
const movementRows=(page:Page,text:string)=>page.locator('#scm-inventory-movement-table tr').filter({hasText:text});
const receiptRows=(page:Page,text:string)=>page.locator('#scm-purchase-receipt-table tr').filter({hasText:text});
const balanceQuery=(data:unknown)=>post('/scm/inventory/balance/query',{pageNum:1,pageSize:20,...(data as object)});
const movementQuery=(data:unknown)=>post('/scm/inventory/movement/query',{pageNum:1,pageSize:20,...(data as object)});

/** 一张**没有需求来源**的已提交采购单 —— 收货类用例的干净前置（采购量 ≠ 需求量是合法业务）。 */
async function submittedPlainOrder(quantity:string){
  const o=await post('/scm/purchase/create',{supplierId,warehouseId,purchaserId:null,plannedArrivalDate:null,remark:name,items:[{skuId,quantity,price:'6.2000',allocations:[]}]});
  await post('/scm/purchase/submit',{id:o.id,version:o.version});
  return await get('/scm/purchase/detail/'+o.id);
}
async function createReceipt(purchaseOrderId:string,receiptMode:'DIRECT'|'WAREHOUSE_CONFIRM'){
  return await post('/scm/purchase/receipt/create',{purchaseOrderId,receiptMode,remark:name});
}
function line(receipt:any,declared:string,actualWeight:string|null){const item=receipt.items[0];return {receiptItemId:item.id,version:item.version,receivedQuantity:declared,actualWeight,weightSource:actualWeight?('MANUAL' as const):null};}
const confirmReceipt=(receipt:any,quantity:string)=>post('/scm/purchase/receipt/confirm',{id:receipt.id,version:receipt.version,items:[line(receipt,quantity,quantity)]});

test.beforeAll(async()=>{
  execFileSync('python',['../tools/w6_e2e_accounts.py','setup'],{env,stdio:'pipe'});
  token=await login(name);
  api=await request.newContext({baseURL:apiUrl,extraHTTPHeaders:{Authorization:`Bearer ${token}`}});
  // 仓库：用 V15 播种的默认仓（W5 只做「新建 / 编辑基础信息」，无启停写入路径 → G1）。
  // Q12 的前提是「恰好一个启用仓库」，因此这里断言它，而不是假设它。
  const warehouses=await get('/scm/warehouse/list');
  warehouseId=String((warehouses.find((w:any)=>w.warehouseCode==='WH001')??warehouses[0]).id);
  const types=await post('/scm/customer/type/option/list',{});
  customerId=await post('/scm/customer/add',{customerCode:name.toUpperCase(),name:name,customerTypeId:types[0].typeId,settleMode:'INDEPENDENT',contactName:'W6验收',contactPhone:'13800000000',address:'验收地址'});
  const customer=await get('/scm/customer/detail/'+customerId);
  await post('/scm/customer/updateStatus',{customerId,version:customer.version,status:'COOPERATING'});
  const tree=await post('/scm/product/category/tree',{});
  const flatten=(rows:any[]):any[]=>rows.flatMap(x=>[x,...flatten(x.children??[])]);
  const category=flatten(tree).find(x=>x.level===3)??flatten(tree)[0];
  const sku=(suffix:string)=>[{skuCode:name.toUpperCase()+suffix,specName:'散装'+suffix,specValues:{规格:'散装'},saleUnit:'kg',productType:'NON_STANDARD',marketPrice:'3.5000',status:'ON_SHELF',defaultFlag:true,sortOrder:0}];
  await post('/scm/product/add',{spuCode:name.toUpperCase()+'-A',name:name+'商品A',categoryId:category.categoryId,status:'ON_SHELF',images:[],skuList:sku('-A')});
  // 第二个 SKU 只用于「空状态」：它永远不会有库存
  await post('/scm/product/add',{spuCode:name.toUpperCase()+'-E',name:name+'商品E',categoryId:category.categoryId,status:'ON_SHELF',images:[],skuList:sku('-E')});
  const options=(await post('/scm/product/sku/option-list',{keyword:name,limit:10})).options;
  skuId=String(options.find((x:any)=>x.specName==='散装-A').skuId);
  emptySkuCode=String(options.find((x:any)=>x.specName==='散装-E').skuCode);
  skuCode=String(options.find((x:any)=>x.specName==='散装-A').skuCode);
  supplierId=await post('/scm/supplier/add',{supplierCode:name.toUpperCase(),name:name+'供应商'});
  await post('/scm/supplier/sku/replace',{supplierId,items:[{skuId,purchaseUnit:'kg',defaultFlag:true,status:'ENABLED'}]});
});
test.afterAll(async()=>{if(api){await api.get('/login/logout');await api.dispose();}execFileSync('python',['../tools/w6_e2e_accounts.py','cleanup'],{env,stdio:'pipe'});});

// ------------------------------------------------------------------
// 1. 收货确认即入库 → 余额页出现该 SKU 行
// ------------------------------------------------------------------

test('1 a confirmed receipt lands as a PURCHASE_IN balance row with the purchase unit',async({page})=>{
  const consoleErrors:string[]=[];page.on('pageerror',e=>consoleErrors.push(e.message));
  orderA=await submittedPlainOrder('20.0000');
  const receipt=await createReceipt(orderA.id,'DIRECT');
  const confirmed=await confirmReceipt(receipt,'10.0000');
  expect(confirmed.status).toBe('CONFIRMED');
  expect(confirmed.receiptMode).toBe('DIRECT');
  expect(confirmed.putawayStatus).toBe('COMPLETED');
  expect(confirmed.putawayAt).not.toBeNull();
  expect(confirmed.putawayBy).not.toBeNull();

  // 接口层：余额与流水同时落地，单位取采购单位快照（Q13）
  const balances=await balanceQuery({warehouseId,skuId});
  expect(balances.total).toBe(1);
  expect(balances.list[0].quantity).toBe('10.0000');
  expect(balances.list[0].unit).toBe('kg');
  expect(balances.list[0].skuCode).toBe(skuCode);
  const movements=await movementQuery({warehouseId,skuId});
  expect(movements.total).toBe(1);
  expect(movements.list[0].movementType).toBe('PURCHASE_IN');
  expect(movements.list[0].beforeQuantity).toBe('0.0000');
  expect(movements.list[0].afterQuantity).toBe('10.0000');
  expect(movements.list[0].unitCost).toBe('6.2000');
  expect(movements.list[0].receiptNo).toBe(receipt.receiptNo);

  // 页面层
  await browse(page,'/inventory/inventory-balance-list');
  await expect(page.locator('#scm-inventory-balance-table')).toBeVisible();
  await page.getByPlaceholder('SKU 编码').fill(skuCode);
  await search(page);
  const row=balanceRows(page,skuCode);
  await expect(row).toHaveCount(1);
  await expect(row).toContainText('10.0000');
  await expect(row).toContainText('kg');
  await expect(row).toContainText(name+'商品A');
  await page.screenshot({path:'../.runtime/w6-inventory-balance.png',fullPage:true});
  expect(consoleErrors).toEqual([]);
});

// ------------------------------------------------------------------
// 2. 再次部分收货 → 余额累加、流水新增、来源单号可跳收货单
// ------------------------------------------------------------------

test('2 a second partial receipt accumulates the balance and adds a traceable movement',async({page})=>{
  const receipt=await createReceipt(orderA.id,'DIRECT');
  await confirmReceipt(receipt,'5.0000');

  const balances=await balanceQuery({warehouseId,skuId});
  expect(balances.total).toBe(1);
  expect(balances.list[0].quantity).toBe('15.0000');
  expect(balances.list[0].version).toBe(2);
  const movements=await movementQuery({warehouseId,skuId});
  expect(movements.total).toBe(2);
  // 回放链：第二条的期初 = 第一条的期末（append-only 账本的核心恒等式）
  const chain=movements.list.slice().sort((a:any,b:any)=>Number(a.id)-Number(b.id));
  expect(chain[0].beforeQuantity).toBe('0.0000');expect(chain[0].afterQuantity).toBe('10.0000');
  expect(chain[1].beforeQuantity).toBe('10.0000');expect(chain[1].afterQuantity).toBe('15.0000');

  await browse(page,'/inventory/inventory-movement-list');
  await expect(page.locator('#scm-inventory-movement-table')).toBeVisible();
  await page.getByPlaceholder('SKU 编码').fill(skuCode);
  await search(page);
  const rows=movementRows(page,skuCode);
  await expect(rows).toHaveCount(2);
  await expect(rows.first()).toContainText('采购入库');
  await page.screenshot({path:'../.runtime/w6-inventory-movement.png',fullPage:true});

  // 来源单号 → 收货单列表（带 receiptNo 查询参数）
  await page.locator('#scm-inventory-movement-table').getByText(receipt.receiptNo,{exact:true}).first().click();
  await expect(page.getByPlaceholder('收货单号')).toHaveValue(receipt.receiptNo);
  await expect(receiptRows(page,receipt.receiptNo)).toHaveCount(1);
});

// ------------------------------------------------------------------
// 3. 空状态
// ------------------------------------------------------------------

test('3 a SKU without stock renders the empty state instead of an error',async({page})=>{
  const empty=await balanceQuery({skuCode:emptySkuCode});
  expect(empty.total).toBe(0);

  await browse(page,'/inventory/inventory-balance-list');
  await page.getByPlaceholder('SKU 编码').fill(emptySkuCode);
  await search(page);
  await expect(page.locator('#scm-inventory-balance-table')).toContainText('暂无库存余额');
  // 空态不是错误：不得出现错误提示
  await expect(page.locator('.ant-alert-error')).toHaveCount(0);
});

// ------------------------------------------------------------------
// 4. 权限
// ------------------------------------------------------------------

// 用 `_none` 而不是 `_read`：只读角色含全部页面菜单和 `:query` 按钮（含 scm:inventory:balance:query），
// 拿它断言「查不到」会得到 code 0 的假失败。
test('4 an account with no role sees no page and is rejected by the API',async({page})=>{
  const readToken=await login(name+'_none');
  const client=await request.newContext({baseURL:apiUrl,extraHTTPHeaders:{Authorization:`Bearer ${readToken}`}});
  // 载荷合法（pageNum/pageSize 必填且合规），因此 @Valid 通过、拦截器先抛权限错误 30005
  const denied=await(await client.post('/scm/inventory/balance/query',{data:{pageNum:1,pageSize:20}})).json();
  expect(denied.code).toBe(30005);
  const deniedMovement=await(await client.post('/scm/inventory/movement/query',{data:{pageNum:1,pageSize:20}})).json();
  expect(deniedMovement.code).toBe(30005);

  // 前端：菜单不渲染 → 路由不注册 → 直接访问该路径落到 404 页，库存表格永不出现。
  //
  // 断言刻意落在「404 页出现」而不是「侧边栏不含库存管理」：该账号连布局都没渲染，
  // `.ant-menu` 在 DOM 里根本不存在，而 Playwright 的 `not.toContainText` 要求元素先存在
  // —— 用后者会得到一个「元素找不到」的假失败，掩盖掉真正被验证的事实。
  await page.addInitScript(v=>localStorage.setItem('smart_admin_user_token',v),readToken);
  await page.goto('/#/inventory/inventory-balance-list');
  await expect(page.getByText('您访问的内容不存在')).toBeVisible();
  await expect(page.locator('#scm-inventory-balance-table')).toHaveCount(0);

  await client.get('/login/logout');
  await client.dispose();
});

// ------------------------------------------------------------------
// 5. 分页与筛选
// ------------------------------------------------------------------

test('5 filters and paging work on both read-only pages',async({page})=>{
  // 余额：SKU 编码关键字命中 1 行；不存在的编码 → 0 行
  const hit=await balanceQuery({skuCode:name});
  expect(hit.total).toBe(1);
  expect(await balanceQuery({skuCode:name+'-NO-SUCH'})).toMatchObject({total:0});
  // 分页：pageSize=1 时只返回 1 行，但 total 仍是全量
  const paged=await post('/scm/inventory/balance/query',{pageNum:1,pageSize:1,skuCode:name});
  expect(paged.list).toHaveLength(1);
  expect(paged.total).toBeGreaterThanOrEqual(1);

  await browse(page,'/inventory/inventory-balance-list');
  await page.getByPlaceholder('SKU 编码').fill(skuCode);
  await search(page);
  await expect(balanceRows(page,skuCode)).toHaveCount(1);

  // 流水：类型筛选 + 时间范围（occurred_at，左闭右开）
  const typed=await movementQuery({skuId,movementType:'PURCHASE_IN'});
  expect(typed.total).toBe(2);
  const recent=await movementQuery({skuId,occurredFrom:new Date(Date.now()-3600_000).toISOString()});
  expect(recent.total).toBe(2);
  // 区间落在 2020 年 → 0 行（证明过滤的是业务发生时刻，而不是写入时刻）
  const ancient=await movementQuery({skuId,occurredFrom:'2020-01-01T00:00:00+08:00',occurredTo:'2020-01-02T00:00:00+08:00'});
  expect(ancient.total).toBe(0);

  await browse(page,'/inventory/inventory-movement-list');
  await page.getByPlaceholder('SKU 编码').fill(skuCode);
  await search(page);
  await expect(movementRows(page,skuCode)).toHaveCount(2);
  // 区间选择器：设到 2020 年 → 空态
  const picker=page.locator('.ant-picker-input input');
  await picker.first().click();
  await picker.first().fill('2020-01-01 00:00:00');
  await page.keyboard.press('Enter');
  await picker.last().fill('2020-01-02 00:00:00');
  await page.keyboard.press('Enter');
  await search(page);
  await expect(page.locator('#scm-inventory-movement-table')).toContainText('暂无库存流水');
  // 清空筛选后仍可查询（枚举清空必须送 undefined，不能送空串，否则 40000）。
  // 按钮名用正则：antd 会在两个中文字之间插入空格，可访问名实际是「重 置」。
  await page.getByRole('button',{name:/^重\s*置$/}).click();
  await page.getByPlaceholder('SKU 编码').fill(skuCode);
  await search(page);
  await expect(movementRows(page,skuCode)).toHaveCount(2);
});

// ------------------------------------------------------------------
// 6. Q12 默认仓库
// ------------------------------------------------------------------

test('6 Q12 the balance page defaults the warehouse only when exactly one is enabled',async({page})=>{
  const warehouses=await get('/scm/warehouse/list');
  const select=()=>page.locator('.smart-query-form .ant-select').first();
  const selection=()=>select().locator('.ant-select-selection-item');

  // 真实环境
  await browse(page,'/inventory/inventory-balance-list');
  await expect(page.locator('#scm-inventory-balance-table')).toBeVisible();
  if(warehouses.length===1){
    await expect(selection()).toContainText(warehouses[0].name);
  }else{
    await expect(selection()).toHaveCount(0);
  }

  // 条件化默认的另一半：伪造两个启用仓库，断言页面不自动选任何一个。
  // 必须 `reload()` 而不是再次 `goto` 同一个 hash：hash 路由不变时 vue-router 不会重新挂载
  // 组件，`onMounted` 里的 Q12 判定不会重跑，于是会读到上一次留下的默认值（假失败）。
  await page.route('**/scm/warehouse/list*',route=>route.fulfill({json:{code:0,msg:'ok',data:[
    {id:900001,name:'验收仓A',warehouseCode:'W6A'},{id:900002,name:'验收仓B',warehouseCode:'W6B'}]}}));
  await page.reload();
  await expect(page.locator('#scm-inventory-balance-table')).toBeVisible();
  await expect(selection()).toHaveCount(0);

  // 多仓下仍提供普通筛选（可手动选择）
  await select().click();
  await page.locator('.ant-select-dropdown:visible').getByText('验收仓A').click();
  await expect(selection()).toContainText('验收仓A');
});

// ------------------------------------------------------------------
// 7. B1 仓库确认入库
// ------------------------------------------------------------------

test('7 WAREHOUSE_CONFIRM posts inventory only when the warehouse confirms putaway',async({page})=>{
  const order=await submittedPlainOrder('3.0000');
  const receipt=await createReceipt(order.id,'WAREHOUSE_CONFIRM');
  const balanceBefore=await balanceQuery({warehouseId,skuId});
  const movementsBefore=await movementQuery({warehouseId,skuId});

  const confirmed=await confirmReceipt(receipt,'3.0000');
  expect(confirmed.status).toBe('CONFIRMED');
  expect(confirmed.receiptMode).toBe('WAREHOUSE_CONFIRM');
  expect(confirmed.putawayStatus).toBe('PENDING');
  expect(confirmed.putawayAt).toBeNull();
  expect(confirmed.putawayBy).toBeNull();
  expect(await balanceQuery({warehouseId,skuId})).toMatchObject({
    total:balanceBefore.total,
    list:[{quantity:balanceBefore.list[0].quantity}],
  });
  expect((await movementQuery({warehouseId,skuId})).total).toBe(movementsBefore.total);

  await browse(page,'/purchase/purchase-receipt-list');
  await page.getByPlaceholder('收货单号').fill(receipt.receiptNo);
  await search(page);
  const receiptRow=receiptRows(page,receipt.receiptNo);
  await expect(receiptRow).toHaveCount(1);
  await expect(receiptRow).toContainText('仓库确认入库');
  await expect(receiptRow).toContainText('待入库');
  await receiptRow.getByRole('button',{name:'确认入库'}).click();
  const modal=page.locator('.ant-modal-confirm:visible');
  await expect(modal).toContainText('该操作会把本收货单数量正式记入库存');
  await modal.getByRole('button',{name:/确\s*定/}).click();
  await expect(page.locator('.ant-message-notice-content').getByText('已入库',{exact:true})).toBeVisible();
  await expect(receiptRow).toContainText('已入库');
  await expect(receiptRow.getByRole('button',{name:'确认入库'})).toHaveCount(0);

  const completed=await get('/scm/purchase/receipt/detail/'+receipt.id);
  expect(completed.putawayStatus).toBe('COMPLETED');
  expect(completed.putawayAt).not.toBeNull();
  expect(completed.putawayBy).not.toBeNull();
  const balanceAfter=await balanceQuery({warehouseId,skuId});
  expect(balanceAfter.list[0].quantity).toBe('18.0000');
  const movementsAfter=await movementQuery({warehouseId,skuId});
  expect(movementsAfter.total).toBe(movementsBefore.total+1);
  const movement=movementsAfter.list.find((item:any)=>item.receiptNo===receipt.receiptNo);
  expect(movement).toBeTruthy();
  expect(movement.quantity).toBe('3.0000');
  expect(new Date(movement.occurredAt).getTime()).toBe(new Date(completed.putawayAt).getTime());

  const repeated=await raw('/scm/purchase/receipt/putaway',{id:completed.id,version:completed.version});
  expect(repeated.code).toBe(41008);
  expect((await balanceQuery({warehouseId,skuId})).list[0].quantity).toBe('18.0000');
  expect((await movementQuery({warehouseId,skuId})).total).toBe(movementsAfter.total);
});

// ------------------------------------------------------------------
// 8. B1 仓库严格停用与启停往返
// ------------------------------------------------------------------

test('8 warehouse disable is strict and an empty warehouse can be disabled and enabled',async({page})=>{
  const code=(name+'_WH').toUpperCase();
  const isolatedId=await post('/scm/warehouse/create',{warehouseCode:code,name:name+'隔离仓',address:null,remark:name});

  await browse(page,'/purchase/warehouse-list');
  await page.getByPlaceholder('仓库编码').fill('WH001');
  await search(page);
  const defaultRow=page.locator('#scm-warehouse-table tr').filter({hasText:'WH001'});
  await expect(defaultRow).toHaveCount(1);
  await defaultRow.getByRole('button',{name:'停用'}).click();
  await page.locator('.ant-modal-confirm:visible').getByRole('button',{name:/确\s*定/}).click();
  await expect(page.locator('.ant-alert-error')).toContainText('仓库仍有库存余额，不能停用');
  await page.locator('.ant-modal-confirm:visible').getByRole('button',{name:/取\s*消/}).click();

  await page.getByPlaceholder('仓库编码').fill(code);
  await search(page);
  const isolatedRow=page.locator('#scm-warehouse-table tr').filter({hasText:code});
  await expect(isolatedRow).toHaveCount(1);
  await isolatedRow.getByRole('button',{name:'停用'}).click();
  await page.locator('.ant-modal-confirm:visible').getByRole('button',{name:/确\s*定/}).click();
  await expect(page.getByText('仓库已停用',{exact:true})).toBeVisible();
  await expect(isolatedRow).toContainText('停用');
  await isolatedRow.getByRole('button',{name:'启用'}).click();
  await expect(page.getByText('仓库已启用',{exact:true})).toBeVisible();
  await expect(isolatedRow).toContainText('启用');

  const enabled=await get('/scm/warehouse/detail/'+isolatedId);
  await post('/scm/warehouse/disable',{id:enabled.id,version:enabled.version});
  const disabled=await get('/scm/warehouse/detail/'+isolatedId);
  expect(disabled.status).toBe('DISABLED');
});
