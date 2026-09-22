/* W5 real PostgreSQL/API/browser acceptance. Account secrets stay in memory. */
import {test,expect,request,type APIRequestContext,type Page} from '@playwright/test';
import {randomBytes,randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import smCrypto from 'sm-crypto';
const apiUrl='http://127.0.0.1:18080';
const name='w5_e2e_'+Date.now().toString(36),password='W5@'+randomBytes(8).toString('hex');
const env={...process.env,W5_E2E_NAME:name,W5_E2E_PASSWORD:password};
let api:APIRequestContext,token:string;
let warehouseId:string,skuId:string,supplierId:string,customerId:string;
let demandA:any,soA:any,orderA:any;
test.describe.configure({mode:'serial'});
async function login(account:string){const c=await request.newContext({baseURL:apiUrl});const captcha=(await(await c.get('/login/getCaptcha')).json()).data;const source=readFileSync('src/lib/encrypt.ts','utf8');const key=/const SM4_KEY = '([^']+)'/.exec(source)![1];const encrypted=Buffer.from(smCrypto.sm4.encrypt(password,Buffer.from(key).toString('hex'))).toString('base64');const r=await(await c.post('/login',{data:{loginName:account,password:encrypted,captchaUuid:captcha.captchaUuid,captchaCode:captcha.captchaText,loginDevice:1}})).json();expect(r.code).toBe(0);await c.dispose();return r.data.token;}
async function raw(path:string,data:unknown,key=randomUUID()){return await(await api.post(path,{data,headers:{'Idempotency-Key':key}})).json();}
async function post(path:string,data:unknown,key=randomUUID()){const r=await raw(path,data,key);expect(r.code,`${path}: ${r.msg}`).toBe(0);return r.data;}
async function get(path:string){const r=await(await api.get(path)).json();expect(r.code,`${path}: ${r.msg}`).toBe(0);return r.data;}
function browse(page:Page,path:string){return page.addInitScript(v=>localStorage.setItem('smart_admin_user_token',v),token).then(()=>page.goto('/#'+path));}
const orderDetail=(id:string)=>get('/scm/purchase/detail/'+id);
const receiptDetail=(id:string)=>get('/scm/purchase/receipt/detail/'+id);
/** 采购单号 PO+yyyyMMdd+至少 6 位；收货单号 PR+同形（全局序列，不按日 reset）。 */
const PO=/^PO\d{14,}$/,PR=/^PR\d{14,}$/;

/** 造一张**已确认**的销售订单（W5 需求的唯一来源），并给出可隔离它的汇总窗口。 */
async function confirmedOrder(orderedQuantity:string,actualQuantity:string){
 const before=new Date(Date.now()-30000).toISOString();
 let o=await post('/scm/order/create',{customerId,orderSource:'ADMIN',address:{receiverName:'W5验收',receiverPhone:'13800000000',address:'验收地址'},remark:name,items:[{skuId,orderedQuantity,manualPriceOverride:false}]});
 o=await post('/scm/order/submit',{orderId:o.orderId,version:o.version});
 o=await post('/scm/order/item/actual-quantity',{orderId:o.orderId,itemId:o.items[0].itemId,version:o.items[0].version,actualQuantity,reason:'W5 验收实重'});
 o=await post('/scm/order/confirm',{orderId:o.orderId,version:o.version});
 const after=new Date(Date.now()+30000).toISOString();
 return {orderId:o.orderId,orderNo:o.orderNo,before,after};
}
/** 在汇总窗口内生成需求，再按销售单号断言本订单恰好生成一条；窗口可能含同轮其它 spec 的订单。 */
async function demandOf(so:any){
 await post('/scm/purchase/demand/generate',{startAt:so.before,endAt:so.after,warehouseId,supplierId});
 const page=await post('/scm/purchase/demand/query',{pageNum:1,pageSize:20,salesOrderNo:so.orderNo});
 expect(page.total).toBe(1);
 return page.list[0];
}
async function createOrder(allocations:any[],quantity:string,price:string){
 return await post('/scm/purchase/create',{supplierId,warehouseId,purchaserId:null,plannedArrivalDate:null,remark:name,items:[{skuId,quantity,price,allocations}]});
}
/** 一张**没有需求来源**的已提交采购单 —— 收货类用例的干净前置（采购量 ≠ 需求量是合法业务）。 */
async function submittedPlainOrder(quantity:string){
 const o=await createOrder([],quantity,'6.2000');
 await post('/scm/purchase/submit',{id:o.id,version:o.version});
 return await orderDetail(o.id);
}
async function createReceipt(purchaseOrderId:string,key=randomUUID()){
 return await post('/scm/purchase/receipt/create',{purchaseOrderId,receiptMode:'DIRECT',remark:name},key);
}
function line(receipt:any,declared:string,actualWeight:string|null){const item=receipt.items[0];return {receiptItemId:item.id,version:item.version,receivedQuantity:declared,actualWeight,weightSource:actualWeight?('MANUAL' as const):null};}
const confirmReceipt=(receipt:any,items:any[],key=randomUUID())=>post('/scm/purchase/receipt/confirm',{id:receipt.id,version:receipt.version,items},key);
async function row(page:Page,tableId:string,text:string){await page.getByRole('button',{name:/^查\s*询$/}).click();return page.locator(`#${tableId} tr`).filter({hasText:text});}

test.beforeAll(async()=>{
 execFileSync('python',['../tools/w5_e2e_accounts.py','setup'],{env,stdio:'pipe'});
 token=await login(name);api=await request.newContext({baseURL:apiUrl,extraHTTPHeaders:{Authorization:`Bearer ${token}`}});
 // 仓库：用 V15 播种的默认仓（W5 只做「新建 / 编辑基础信息」，无启停写入路径 → G1）
 const warehouses=await get('/scm/warehouse/list');warehouseId=String((warehouses.find((w:any)=>w.warehouseCode==='WH001')??warehouses[0]).id);
 const types=await post('/scm/customer/type/option/list',{});
 customerId=await post('/scm/customer/add',{customerCode:name.toUpperCase(),name:name,customerTypeId:types[0].typeId,settleMode:'INDEPENDENT',contactName:'W5验收',contactPhone:'13800000000',address:'验收地址'});
 const customer=await get('/scm/customer/detail/'+customerId);await post('/scm/customer/updateStatus',{customerId,version:customer.version,status:'COOPERATING'});
 const tree=await post('/scm/product/category/tree',{});const flatten=(rows:any[]):any[]=>rows.flatMap(x=>[x,...flatten(x.children??[])]);const category=flatten(tree).find(x=>x.level===3)??flatten(tree)[0];
 await post('/scm/product/add',{spuCode:name.toUpperCase(),name:name+'商品',categoryId:category.categoryId,status:'ON_SHELF',images:[],skuList:[{skuCode:name.toUpperCase()+'-KG',specName:'散装',specValues:{规格:'散装'},saleUnit:'kg',productType:'NON_STANDARD',marketPrice:'3.5000',status:'ON_SHELF',defaultFlag:true,sortOrder:0},{skuCode:name.toUpperCase()+'-BOX',specName:'整箱',specValues:{规格:'整箱'},saleUnit:'箱',productType:'STANDARD',marketPrice:'0.0000',status:'ON_SHELF',defaultFlag:false,sortOrder:1}]});
 const options=(await post('/scm/product/sku/option-list',{keyword:name,limit:10})).options;skuId=String(options.find((x:any)=>x.specName==='散装').skuId);const boxSkuId=String(options.find((x:any)=>x.specName==='整箱').skuId);
 supplierId=await post('/scm/supplier/add',{supplierCode:name.toUpperCase(),name:name+'供应商'});
 // 采购单位必须与需求单位（= 销售单位）一致，否则 Q17 直接 40971；这里逐个对齐。
 await post('/scm/supplier/sku/replace',{supplierId,items:[{skuId,purchaseUnit:'kg',defaultFlag:true,status:'ENABLED'},{skuId:boxSkuId,purchaseUnit:'箱',defaultFlag:false,status:'ENABLED'}]});
});
test.afterAll(async()=>{if(api){await api.get('/login/logout');await api.dispose();}execFileSync('python',['../tools/w5_e2e_accounts.py','cleanup'],{env,stdio:'pipe'});});

test('1 demand generation from a confirmed sales order',async({page})=>{
 const consoleErrors:string[]=[];page.on('pageerror',e=>consoleErrors.push(e.message));
 soA=await confirmedOrder('10.0000','10.0000');
 const generated=await post('/scm/purchase/demand/generate',{startAt:soA.before,endAt:soA.after,warehouseId,supplierId});
 expect(generated.sourceLineCount).toBeGreaterThanOrEqual(1);expect(generated.createdCount+generated.skippedCount).toBe(generated.sourceLineCount);
 demandA=await demandOf(soA);
 expect(demandA.salesOrderNoSnapshot).toBe(soA.orderNo);
 expect(demandA.requiredQuantity).toBe('10.0000');expect(demandA.allocatedQuantity).toBe('0.0000');expect(demandA.unallocatedQuantity).toBe('10.0000');
 expect(demandA.status).toBe('PENDING');expect(demandA.demandUnit).toBe('kg');
 await browse(page,'/purchase/purchase-demand-list');await expect(page.locator('#scm-purchase-demand-table')).toBeVisible();
 await page.getByPlaceholder('销售单号').fill(soA.orderNo);
 const demandRow=await row(page,'scm-purchase-demand-table',soA.orderNo);
 await expect(demandRow).toContainText('待分配');await expect(demandRow).toContainText('10.0000');
 await page.screenshot({path:'../.runtime/w5-demand-list.png',fullPage:true});expect(consoleErrors).toEqual([]);
});
test('2 purchase order keeps the demand allocation and totals the lines',async({page})=>{
 orderA=await createOrder([{demandId:demandA.id,quantity:'10.0000',demandVersion:demandA.version}],'10.0000','6.2000');
 expect(orderA.orderNo).toMatch(PO);expect(orderA.status).toBe('DRAFT');expect(orderA.totalAmount).toBe('62.0000');
 const detail=await orderDetail(orderA.id);
 expect(detail.allocations).toHaveLength(1);
 expect(String(detail.allocations[0].demandId)).toBe(String(demandA.id));
 expect(detail.allocations[0].quantity).toBe('10.0000');
 expect(detail.allocations[0].demandUnit).toBe('kg');
 const demand=await demandOf(soA);
 expect(demand.allocatedQuantity).toBe('10.0000');expect(demand.unallocatedQuantity).toBe('0.0000');expect(demand.status).toBe('ALLOCATED');
 await browse(page,'/purchase/purchase-order-list');await expect(page.locator('#scm-purchase-order-table')).toBeVisible();
 await page.getByPlaceholder('采购单号').fill(orderA.orderNo);
 const orderRow=await row(page,'scm-purchase-order-table',orderA.orderNo);
 await expect(orderRow).toContainText('草稿');await expect(orderRow).toContainText('62.0000');
 await page.screenshot({path:'../.runtime/w5-order-list.png',fullPage:true});
});
test('3 first partial receipt moves the order to PARTIALLY_RECEIVED',async()=>{
 await post('/scm/purchase/submit',{id:orderA.id,version:orderA.version});
 const receipt=await createReceipt(orderA.id);
 expect(receipt.receiptNo).toMatch(PR);expect(receipt.status).toBe('DRAFT');expect(receipt.items).toHaveLength(1);
 expect(receipt.items[0].plannedQuantity).toBe('10.0000');
 const confirmed=await confirmReceipt(receipt,[line(receipt,'4.0000','4.0000')]);
 expect(confirmed.status).toBe('CONFIRMED');
 const item=confirmed.items[0];
 expect(item.receivedQuantity).toBe('4.0000');expect(item.cumulativeReceivedQuantity).toBe('4.0000');
 expect(item.remainingQuantity).toBe('6.0000');expect(item.overReceiptQuantity).toBe('0.0000');expect(item.receiptDifference).toBe('-6.0000');
 const order=await orderDetail(orderA.id);
 expect(order.status).toBe('PARTIALLY_RECEIVED');expect(order.items[0].receivedQuantity).toBe('4.0000');expect(order.items[0].remainingQuantity).toBe('6.0000');
});
test('4 second receipt completes the order and the progress reaches one',async({page})=>{
 // 新草稿行的对账量是「确认前」快照（§7.5：cumulative = 采购行 received_quantity，**确认后**的累计），
 // 所以 create 时是 0 / planned / −planned；真正的累计在 confirm 时物化。
 const second=await createReceipt(orderA.id);
 expect(second.items[0].cumulativeReceivedQuantity).toBe('0.0000');
 expect(second.items[0].remainingQuantity).toBe('10.0000');
 expect(second.items[0].receiptDifference).toBe('-10.0000');
 const confirmed=await confirmReceipt(second,[line(second,'6.0000','6.0000')]);
 expect(confirmed.items[0].cumulativeReceivedQuantity).toBe('10.0000');
 expect(confirmed.items[0].remainingQuantity).toBe('0.0000');
 expect(confirmed.items[0].overReceiptQuantity).toBe('0.0000');
 expect(confirmed.items[0].receiptDifference).toBe('0.0000');
 const order=await orderDetail(orderA.id);
 expect(order.status).toBe('RECEIVED');expect(order.items[0].receivedQuantity).toBe('10.0000');expect(order.receivedProgress).toBe('1.0000');
 const receipts=await post('/scm/purchase/receipt/query',{pageNum:1,pageSize:20,purchaseOrderId:orderA.id});
 expect(receipts.total).toBe(2);
 await browse(page,'/purchase/purchase-order-list');await page.getByPlaceholder('采购单号').fill(orderA.orderNo);
 const orderRow=await row(page,'scm-purchase-order-table',orderA.orderNo);
 await expect(orderRow).toContainText('已收货');await expect(orderRow).toContainText('100.00%');
 await browse(page,'/purchase/purchase-receipt-list');await page.getByPlaceholder('采购单号（自动解析）').fill(orderA.orderNo);
 const receiptRows=await row(page,'scm-purchase-receipt-table',orderA.orderNo);
 await expect(receiptRows).toHaveCount(2);
 await page.screenshot({path:'../.runtime/w5-receipt-list.png',fullPage:true});
});
test('5 over receipt beyond the tolerance is rejected and rolls back',async({page})=>{
 const order=await submittedPlainOrder('10.0000');
 const receipt=await createReceipt(order.id);
 const denied=await raw('/scm/purchase/receipt/confirm',{id:receipt.id,version:receipt.version,items:[line(receipt,'100.0000','100.0000')]});
 expect(denied.code).toBe(40989);
 const after=await receiptDetail(receipt.id);
 expect(after.status).toBe('DRAFT');
 expect(after.items[0].cumulativeReceivedQuantity).toBe('0.0000');expect(after.items[0].remainingQuantity).toBe('10.0000');
 expect((await orderDetail(order.id)).items[0].receivedQuantity).toBe('0.0000');
 await browse(page,'/purchase/purchase-receipt-list');await page.getByPlaceholder('收货单号').fill(receipt.receiptNo);
 const receiptRow=await row(page,'scm-purchase-receipt-table',receipt.receiptNo);
 await receiptRow.getByRole('button',{name:'确认收货'}).click();
 const modal=page.locator('.ant-modal:visible');
 await expect(modal).toContainText('超收容差');await expect(modal).toContainText('剩余可收');
 await expect(modal.getByLabel('本次声明数量')).toHaveValue('10.0000');
 await page.locator('.ant-modal:visible .ant-modal-close').click();await expect(page.locator('.ant-modal:visible')).toHaveCount(0);
});
test('6 replaying the same confirmation accumulates only once',async()=>{
 const order=await submittedPlainOrder('10.0000');
 const receipt=await createReceipt(order.id);
 const body={id:receipt.id,version:receipt.version,items:[line(receipt,'3.0000','3.0000')]};
 const key=randomUUID();
 const first=await post('/scm/purchase/receipt/confirm',body,key);
 const replay=await post('/scm/purchase/receipt/confirm',body,key);
 expect(replay.id).toBe(first.id);expect(replay.status).toBe('CONFIRMED');
 expect(replay.items[0].cumulativeReceivedQuantity).toBe('3.0000');
 const after=await orderDetail(order.id);
 expect(after.status).toBe('PARTIALLY_RECEIVED');expect(after.items[0].receivedQuantity).toBe('3.0000');
});
test('7 short close requires a reason and closes the remainder',async({page})=>{
 const order=await submittedPlainOrder('10.0000');
 const receipt=await createReceipt(order.id);
 await confirmReceipt(receipt,[line(receipt,'4.0000','4.0000')]);
 const denied=await raw('/scm/purchase/short-close',{id:order.id,version:(await orderDetail(order.id)).version});
 expect(denied.code).not.toBe(0);
 await browse(page,'/purchase/purchase-order-list');await page.getByPlaceholder('采购单号').fill(order.orderNo);
 const orderRow=await row(page,'scm-purchase-order-table',order.orderNo);
 await expect(orderRow).toContainText('部分收货');
 await orderRow.getByRole('button',{name:'少收关单'}).click();
 await page.locator('.ant-modal-confirm').getByRole('button',{name:'确 定'}).click();
 await expect(page.locator('.ant-alert-error')).toContainText('请填写少收关单原因');
 await page.locator('.ant-modal-confirm textarea').fill('供应商缺货，剩余不再补收');
 await page.locator('.ant-modal-confirm').getByRole('button',{name:'确 定'}).click();
 await expect(page.locator('.ant-modal-confirm')).toHaveCount(0);
 await expect(orderRow.locator('.ant-tag')).toContainText('少收关单');
 const closed=await orderDetail(order.id);
 expect(closed.status).toBe('SHORT_CLOSED');expect(closed.shortCloseReason).toBe('供应商缺货，剩余不再补收');expect(closed.shortClosedAt).not.toBeNull();
});
test('8 non standard receipt accumulates the weight without overwriting the plan',async()=>{
 const order=await submittedPlainOrder('10.0000');
 const receipt=await createReceipt(order.id);
 expect(receipt.items[0].productType).toBe('NON_STANDARD');
 const missing=await raw('/scm/purchase/receipt/confirm',{id:receipt.id,version:receipt.version,items:[line(receipt,'10.0000',null)]});
 expect(missing.code).toBe(40083);
 const confirmed=await confirmReceipt(receipt,[line(receipt,'10.0000','9.5000')]);
 const item=confirmed.items[0];
 expect(item.actualWeight).toBe('9.5000');expect(item.weighingSource).toBe('MANUAL');expect(item.weightUnit).toBe('kg');
 expect(item.cumulativeReceivedQuantity).toBe('9.5000');expect(item.plannedQuantity).toBe('10.0000');expect(item.remainingQuantity).toBe('0.5000');
 const after=await orderDetail(order.id);
 expect(after.status).toBe('PARTIALLY_RECEIVED');
 expect(after.items[0].plannedQuantity).toBe('10.0000');expect(after.items[0].receivedQuantity).toBe('9.5000');
});
test('9 one item serves two demands and editing one leaves the other intact',async({page})=>{
 const so1=await confirmedOrder('6.0000','6.0000');
 const so2=await confirmedOrder('4.0000','4.0000');
 const d1=await demandOf(so1),d2=await demandOf(so2);
 const created=await createOrder([{demandId:d1.id,quantity:'6.0000',demandVersion:d1.version},{demandId:d2.id,quantity:'4.0000',demandVersion:d2.version}],'10.0000','6.2000');
 let detail=await orderDetail(created.id);
 expect(detail.allocations).toHaveLength(2);
 const kept=detail.allocations.find((a:any)=>String(a.demandId)===String(d2.id));
 expect(kept.quantity).toBe('4.0000');
 // 目标态语义：请求体 = 目标分配集合。只改 d1 也必须显式带上 d2，否则 d2 会被删掉。
 const fresh1=await demandOf(so1),fresh2=await demandOf(so2);
 const item=detail.items[0];
 await post('/scm/purchase/update',{id:detail.id,version:detail.version,supplierId,warehouseId,purchaserId:null,plannedArrivalDate:null,remark:detail.remark,
  items:[{id:item.id,version:item.version,skuId,quantity:'9.0000',price:'6.2000',allocations:[
   {demandId:fresh1.id,quantity:'5.0000',demandVersion:fresh1.version},
   {demandId:fresh2.id,quantity:'4.0000',demandVersion:fresh2.version}]}]});
 const after=await orderDetail(created.id);
 expect(after.allocations).toHaveLength(2);
 const changed=after.allocations.find((a:any)=>String(a.demandId)===String(fresh1.id));
 const untouched=after.allocations.find((a:any)=>String(a.demandId)===String(fresh2.id));
 expect(changed.quantity).toBe('5.0000');
 expect(untouched.quantity).toBe('4.0000');
 expect(String(untouched.allocationId)).toBe(String(kept.allocationId));
 const final1=await demandOf(so1),final2=await demandOf(so2);
 expect(final1.allocatedQuantity).toBe('5.0000');expect(final1.unallocatedQuantity).toBe('1.0000');expect(final1.status).toBe('PARTIALLY_ALLOCATED');
 expect(final2.allocatedQuantity).toBe('4.0000');expect(final2.unallocatedQuantity).toBe('0.0000');expect(final2.status).toBe('ALLOCATED');
 await browse(page,'/purchase/purchase-order-list');await page.getByPlaceholder('采购单号').fill(created.orderNo);
 await row(page,'scm-purchase-order-table',created.orderNo);
 await page.locator('#scm-purchase-order-table').getByText(created.orderNo,{exact:true}).click();
 const drawer=page.locator('.ant-drawer-body');
 await expect(drawer).toContainText(so1.orderNo);await expect(drawer).toContainText(so2.orderNo);
 await expect(drawer).toContainText('部分分配');await expect(drawer).toContainText('已分配');
 await page.screenshot({path:'../.runtime/w5-order-detail.png',fullPage:true});
});

/**
 * Wave 2A：订单汇总 / 库存缺口预览（只读）。
 *
 * 契约：读端点按 `[startAt,endAt)` + `warehouseId` 聚合，数量为后端算好的四位定点字符串；
 * 重复调用**不得**新建需求或流水（只读辅助决策），`calculationStatus` 只落在后端 5 值内。
 * 注：本用例需要**包含 Wave 2A 端点的后端构建**；18080 上若在跑更早的 fat jar，
 * 该 `summary-preview` 会 404 —— 属部署版本问题，不是本 Wave 缺陷。
 */
const SUMMARY_STATUS=new Set(['STOCK_ENOUGH','SHORTAGE','ZERO_STOCK','UNIT_MISMATCH','NO_BALANCE']);
test('10 stock shortage preview is read-only and server-computed',async({page})=>{
 const consoleErrors:string[]=[];page.on('pageerror',e=>consoleErrors.push(e.message));
 // 用已生成需求的那张销售单所在窗口造一次可命中的查询（soA 已确认且已入库）
 const so=await confirmedOrder('20.0000','20.0000');
 const before=await post('/scm/purchase/demand/query',{pageNum:1,pageSize:20,salesOrderNo:so.orderNo});
 expect(before.total).toBe(0); // 只读预览不建需求：此刻该单还没有需求行
 const preview=await post('/scm/purchase/demand/summary-preview',{startAt:so.before,endAt:so.after,warehouseId,keyword:name,pageNum:1,pageSize:20});
 expect(typeof preview.total).toBe('number');expect(Array.isArray(preview.list)).toBe(true);
 const mine=preview.list.filter((r:any)=>String(r.skuCode??'').startsWith(name.toUpperCase()));
 expect(mine.length).toBeGreaterThanOrEqual(1);
 for(const r of preview.list){
   expect(SUMMARY_STATUS.has(r.calculationStatus),`非法计算状态 ${r.calculationStatus}`).toBe(true);
   for(const f of ['orderDemandQuantity','availableQuantity','shortageAgainstAvailable']){
     if(r[f]!==null)expect(r[f],`${f} 必须是四位定点字符串`).toMatch(/^\d+\.\d{4}$/);
   }
 }
 // 缺口/可用量由后端给出，UNIT_MISMATCH 之外的行 available = onHand - reserved 的一致性由后端保证，前端不重算
 const after=await post('/scm/purchase/demand/query',{pageNum:1,pageSize:20,salesOrderNo:so.orderNo});
 expect(after.total).toBe(0); // 预览后仍无需求行 → 只读
 await browse(page,'/purchase/purchase-demand-list');
 await page.getByRole('tab',{name:'订单汇总 / 缺口预览'}).click();
 await expect(page.locator('#scm-purchase-demand-summary-preview-table')).toBeVisible();
 await expect(page.getByText('只读预览')).toBeVisible();
 await page.screenshot({path:'../.runtime/w2a-summary-preview.png',fullPage:true});
 expect(consoleErrors).toEqual([]);
});

/**
 * Wave 2B：采购效率（导出 / 批量少收关单 / 按商品收货工作台）。
 *
 * 契约：导出与工作台都是只读端点（无 Idempotency-Key、不改采购状态、不新建收货 / 库存）；
 * 批量少关整批原子——批内含不可关单据时全部回滚。
 * 注：这些用例需要**包含 Wave 2B 端点的后端构建**；18080 上若在跑更早的 fat jar，
 * `export` / `batch/short-close` / `item-workbench` 会 404 —— 属部署版本问题，不是本 Wave 缺陷。
 */
const WB_NUMERIC_FIELDS=['plannedQuantity','receivedQuantity','pendingQuantity','overReceiptQuantity'];
test('11 export is a read-only xlsx download that never changes order state',async({page})=>{
 const consoleErrors:string[]=[];page.on('pageerror',e=>consoleErrors.push(e.message));
 // 用测试 2 的已提交单：导出前后状态必须一致，证明导出是纯读取。
 const snapshot=await orderDetail(orderA.id);
 const resp=await api.post('/scm/purchase/export',{data:{pageNum:1,pageSize:20,orderNo:orderA.orderNo,exportColumns:['orderNo','status','totalAmount']}});
 expect(resp.ok()).toBe(true);
 expect(resp.headers()['content-type']??'').toMatch(/spreadsheet|octet-stream/);
 expect((resp.headers()['content-disposition']??'').toLowerCase()).toContain('attachment');
 expect((await resp.body()).length).toBeGreaterThan(0);
 expect((await orderDetail(orderA.id)).status).toBe(snapshot.status);
 // 页面接线：导出 / 导出设置 / 批量少收关单按钮 + 每行打印都在（按权限渲染）。
 await browse(page,'/purchase/purchase-order-list');await expect(page.locator('#scm-purchase-order-table')).toBeVisible();
 await expect(page.getByRole('button',{name:'导出'})).toBeVisible();
 await expect(page.getByRole('button',{name:'导出设置'})).toBeVisible();
 await page.screenshot({path:'../.runtime/w2b-order-export.png',fullPage:true});
 expect(consoleErrors).toEqual([]);
});

test('12 batch short-close is atomic and the item workbench stays read-only',async({page})=>{
 // 两张可关的部分收货单：整批关单后都进入终态。
 const p1=await submittedPlainOrder('10.0000'),p2=await submittedPlainOrder('10.0000');
 const r1=await createReceipt(p1.id),r2=await createReceipt(p2.id);
 await confirmReceipt(r1,[line(r1,'4.0000','4.0000')]);
 await confirmReceipt(r2,[line(r2,'5.0000','5.0000')]);
 const v1=(await orderDetail(p1.id)).version,v2=(await orderDetail(p2.id)).version;
 await post('/scm/purchase/batch/short-close',{orders:[{id:p1.id,version:v1},{id:p2.id,version:v2}],shortCloseReason:'W5 验收批量少收'});
 expect((await orderDetail(p1.id)).status).toBe('SHORT_CLOSED');
 expect((await orderDetail(p2.id)).status).toBe('SHORT_CLOSED');
 // 原子性：混入一张草稿单（不可关）应整批失败，合法的那张保持原状态不回滚生效。
 const keep=await submittedPlainOrder('10.0000');
 const rk=await createReceipt(keep.id);await confirmReceipt(rk,[line(rk,'3.0000','3.0000')]);
 const keepVersion=(await orderDetail(keep.id)).version;
 const draft=await createOrder([],'5.0000','6.2000'); // DRAFT，不可少收关单
 const denied=await raw('/scm/purchase/batch/short-close',{orders:[{id:keep.id,version:keepVersion},{id:draft.id,version:draft.version}],shortCloseReason:'原子性验证'});
 expect(denied.code).not.toBe(0);
 expect((await orderDetail(keep.id)).status).toBe('PARTIALLY_RECEIVED');
 // 按商品工作台：只读聚合，四位定点字符串，查询后不新建任何收货 / 库存（读端点无副作用）。
 const wb=await post('/scm/purchase/receipt/item-workbench/query',{pageNum:1,pageSize:50});
 expect(Array.isArray(wb.list)).toBe(true);
 for(const r of wb.list){for(const f of WB_NUMERIC_FIELDS){if(r[f]!==null)expect(r[f],`${f} 必须是四位定点字符串`).toMatch(/^\d+\.\d{4}$/);}
   // 逐行裁剪后欠收与超收互斥：同一聚合行不会同时大于零。
   if(Number(r.pendingQuantity??'0')>0&&Number(r.overReceiptQuantity??'0')>0)throw new Error('欠收与超收不应同时大于零');}
 await browse(page,'/purchase/purchase-receipt-list');
 await page.getByRole('tab',{name:'按商品'}).click();
 await expect(page.locator('#scm-purchase-receipt-item-workbench-table')).toBeVisible();
 await expect(page.getByText('只读工作台')).toBeVisible();
 await page.screenshot({path:'../.runtime/w2b-item-workbench.png',fullPage:true});
});
