/*
  W6 库存写流程 E2E：出库确认 / 盘点确认 / 报损报溢审批 / 调拨发出→收货 / 规格转换审批。

  与 scm-inventory.spec.ts（只读页 + 入库）互补：本文件覆盖**会改库存的五个入口**，
  每个入口都跑真实链路（HTTP + 真实 PG + 真实浏览器），并断言三件事同时成立：
      单据状态机 · 余额与流水（append-only 链） · 金额守恒。
  其中调拨与转换的「成本随货平移」是本轮修的账务缺陷，断言在测试 4 与 5。
  账号密钥只存在内存里，跑完由 tools/w6_e2e_accounts.py 清理。
*/
import {test,expect,request,type APIRequestContext,type Page} from '@playwright/test';
import {randomBytes,randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import smCrypto from 'sm-crypto';

const apiUrl='http://127.0.0.1:18080';
const name='w6_e2e_'+Date.now().toString(36);
const password='W6W@'+randomBytes(9).toString('hex');
// E2E_SECOND_ADMIN：额外建 `<name>_two` 第二管理员。报损报溢禁止自建自审（裁决第 8 条制衡），
// 审批必须由录单人以外的账号发起；仍是管理员，避免把写侧仓库守卫卷进本用例。
const env={...process.env,W6_E2E_NAME:name,W6_E2E_PASSWORD:password,E2E_SECOND_ADMIN:'1'};
const purchasePrice='6.2000';

let api:APIRequestContext,token:string;
/** 第二管理员客户端：只用于报损报溢的审批/驳回调用。 */
let auditor:APIRequestContext;
let warehouseId:string,supplierId:string,categoryId:string;
/** 每个流程独占一个 SKU：余额与流水断言都按 (仓库, SKU) 收敛，互不干扰。 */
const sku=new Map<string,{id:string;code:string}>();
const pageErrors:string[]=[];

async function login(account:string,secret:string){
  const c=await request.newContext({baseURL:apiUrl});
  const captcha=(await(await c.get('/login/getCaptcha')).json()).data;
  const source=readFileSync('src/lib/encrypt.ts','utf8');
  const key=/const SM4_KEY = '([^']+)'/.exec(source)![1];
  const encrypted=Buffer.from(smCrypto.sm4.encrypt(secret,Buffer.from(key).toString('hex'))).toString('base64');
  const r=await(await c.post('/login',{data:{loginName:account,password:encrypted,captchaUuid:captcha.captchaUuid,captchaCode:captcha.captchaText,loginDevice:1}})).json();
  expect(r.code).toBe(0);
  await c.dispose();
  return r.data.token;
}
/** 每次调用都换新幂等键：同一把键会命中幂等回放，拿不到「重复操作应被拒」的真实错误码。 */
async function raw(path:string,data:unknown=null,key=randomUUID()){
  return await rawAs(api,path,data,key);
}
/** 用指定账号发同一个请求：报损报溢审批要由录单人以外的人发起（裁决第 8 条制衡）。 */
async function rawAs(client:APIRequestContext,path:string,data:unknown=null,key=randomUUID()){
  return await(await client.post(path,{data:data??{},headers:{'Idempotency-Key':key}})).json();
}
async function post(path:string,data:unknown=null){
  const r=await raw(path,data);
  expect(r.code,`${path}: ${r.msg}`).toBe(0);
  return r.data;
}
async function postAs(client:APIRequestContext,path:string,data:unknown=null){
  const r=await rawAs(client,path,data);
  expect(r.code,`${path}: ${r.msg}`).toBe(0);
  return r.data;
}
async function get(path:string){
  const r=await(await api.get(path)).json();
  expect(r.code,`${path}: ${r.msg}`).toBe(0);
  return r.data;
}
function browse(page:Page,path:string,overrideToken?:string){
  return page.addInitScript(v=>localStorage.setItem('smart_admin_user_token',v),overrideToken??token)
    .then(()=>page.goto('/#'+path));
}
async function search(page:Page){
  await page.getByRole('button',{name:/^查\s*询$/}).click();
}
const rows=(page:Page,tableId:string,text:string)=>page.locator(`#${tableId} tr`).filter({hasText:text});
/** 金额与均价用数值比较：SCALE 由 SQL 决定，字面量断言会把口径变更误报成失败。 */
const num=(v:unknown)=>Number(v);
const balanceQuery=(warehouse:string|number,skuId:string)=>post('/scm/inventory/balance/query',{pageNum:1,pageSize:50,warehouseId:warehouse,skuId});
const movementQuery=(warehouse:string|number,skuId:string)=>post('/scm/inventory/movement/query',{pageNum:1,pageSize:50,warehouseId:warehouse,skuId});

/** (仓库, SKU) 的当前余额行；没有行返回 null。 */
async function balance(warehouse:string|number,skuId:string){
  const r=await balanceQuery(warehouse,skuId);
  return r.total===0?null:r.list[0];
}
async function movement(warehouse:string|number,skuId:string,type:string){
  const list=(await movementQuery(warehouse,skuId)).list.filter((m:any)=>m.movementType===type);
  expect(list).toHaveLength(1);
  return list[0];
}
/** 该 SKU 在**所有仓库**的库存金额合计 —— 守恒断言的基准。 */
async function totalCost(skuId:string){
  const rowsOfSku=(await post('/scm/inventory/balance/query',{pageNum:1,pageSize:50,skuId})).list
    .filter((b:any)=>String(b.skuId)===skuId);
  return rowsOfSku.reduce((sum:number,b:any)=>sum+num(b.quantity)*num(b.avgCost),0);
}

/** 单据当前版本：审批 / 确认类接口都带乐观锁，取最新版本才能表达「这次操作」的意图。 */
async function version(modulePath:string,id:string|number){
  return (await get(modulePath+'/detail/'+id)).version;
}

/**
 * 自建一条 1→2→3 级分类链并返回叶子 id。
 * 不能沿用「从分类树找 level===3」：SPU 只允许挂在三级且 ENABLED 的分类下，
 * 而开发库的种子分类只播到二级，取不到三级就会退化成一级根节点并被 40011 拒绝。
 */
async function ensureCategoryChain(){
  let parentId:string|null=null;
  for(const level of [1,2,3]){
    parentId=String(await post('/scm/product/category/add',{
      parentId,categoryCode:(name+'-L'+level).toUpperCase(),
      name:name+'分类'+level,sortOrder:0,status:'ENABLED'}));
  }
  return parentId;
}

/** 建一个非标品 SKU（散装 / kg）。 */
async function newSku(tag:string){
  const code=(name+'-'+tag).toUpperCase();
  await post('/scm/product/add',{spuCode:code,name:name+'商品'+tag,categoryId,status:'ON_SHELF',images:[],
    skuList:[{skuCode:code,specName:'散装'+tag,specValues:{规格:'散装'},saleUnit:'kg',productType:'NON_STANDARD',marketPrice:'3.5000',status:'ON_SHELF',defaultFlag:true,sortOrder:0}]});
  const options=(await post('/scm/product/sku/option-list',{keyword:code,limit:10})).options;
  sku.set(tag,{id:String(options.find((x:any)=>x.skuCode===code).skuId),code});
}

/** 采购 → 提交 → DIRECT 收货确认，把 SKU  stocking 到指定数量（均价 = 采购价）。 */
async function stockIn(skuId:string,quantity:string){
  const order=await post('/scm/purchase/create',{supplierId,warehouseId,purchaserId:null,plannedArrivalDate:null,remark:name,
    items:[{skuId,quantity,price:purchasePrice,allocations:[]}]});
  await post('/scm/purchase/submit',{id:order.id,version:order.version});
  const receipt=await post('/scm/purchase/receipt/create',{purchaseOrderId:order.id,receiptMode:'DIRECT',remark:name});
  const item=receipt.items[0];
  return await post('/scm/purchase/receipt/confirm',{id:receipt.id,version:receipt.version,
    items:[{receiptItemId:item.id,version:item.version,receivedQuantity:quantity,actualWeight:quantity,weightSource:'MANUAL'}]});
}

test.describe.configure({mode:'serial'});

test.beforeAll(async()=>{
  execFileSync('python',['../tools/w6_e2e_accounts.py','setup'],{env,stdio:'pipe'});
  token=await login(name,password);
  api=await request.newContext({baseURL:apiUrl,extraHTTPHeaders:{Authorization:`Bearer ${token}`}});
  // 审批账号与录单账号分开：同一人审批报损报溢会被 41065 挡下（裁决第 8 条制衡）
  auditor=await request.newContext({baseURL:apiUrl,extraHTTPHeaders:{Authorization:`Bearer ${await login(name+'_two',password)}`}});
  // Q12：订单链路依赖「恰好一个启用仓库」，因此取用并断言 WH001，不假设它。
  const warehouses=await get('/scm/warehouse/list');
  warehouseId=String((warehouses.find((w:any)=>w.warehouseCode==='WH001')??warehouses[0]).id);

  supplierId=await post('/scm/supplier/add',{supplierCode:(name+'-SUP').toUpperCase(),name:name+'供应商'});
  categoryId=await ensureCategoryChain();
  for(const tag of ['1','2','3','4','5','6'])await newSku(tag);
  // replace 是**整体替换**，必须一次带上全部 SKU
  await post('/scm/supplier/sku/replace',{supplierId,
    items:[...sku.values()].map(x=>({skuId:Number(x.id),purchaseUnit:'kg',defaultFlag:true,status:'ENABLED'}))});
});

test.afterAll(async()=>{
  if(auditor){await auditor.get('/login/logout');await auditor.dispose();}
  if(api){await api.get('/login/logout');await api.dispose();}
  execFileSync('python',['../tools/w6_e2e_accounts.py','cleanup'],{env,stdio:'pipe'});
});

test.beforeEach(async({page})=>{
  page.on('pageerror',e=>pageErrors.push(e.message));
});

test.afterEach(async()=>{
  expect(pageErrors,`浏览器 pageerror：${pageErrors.join(' | ')}`).toEqual([]);
});

// ------------------------------------------------------------------
// 1. 出库确认（UI 驱动写入）：扣库存、写 SALES_OUT、按当时均价记成本
// ------------------------------------------------------------------

test('1 outbound confirm deducts stock through the real button and stamps the moving average',async({page})=>{
  const {id}=sku.get('1')!;
  await stockIn(id,'20.0000');
  const opening=await balance(warehouseId,id);
  expect(opening.quantity).toBe('20.0000');
  expect(num(opening.avgCost)).toBe(6.2);

  const outboundId=await post('/scm/inventory/outbound/create',{warehouseId,remark:name,items:[{skuId:Number(id),quantity:'5.0000'}]});
  const draft=await get('/scm/inventory/outbound/detail/'+outboundId);
  expect(draft.status).toBe('DRAFT');

  await browse(page,'/inventory/inventory-outbound-list');
  await page.getByPlaceholder('出库单号').fill(draft.outboundNo);
  await search(page);
  const row=rows(page,'scm-inventory-outbound-table',draft.outboundNo);
  await expect(row).toHaveCount(1);
  await expect(row).toContainText('草稿');

  await row.getByRole('button',{name:'确认出库'}).click();
  await page.locator('.ant-modal-confirm:visible').getByRole('button',{name:'确认出库'}).click();
  await expect(page.locator('.ant-message-notice-content').getByText('出库完成，库存已扣减',{exact:true})).toBeVisible();
  await expect(row).toContainText('已确认');
  await expect(row.getByRole('button',{name:'确认出库'})).toHaveCount(0);
  await page.screenshot({path:'../.runtime/w6-write-outbound.png',fullPage:true});

  // 余额扣减 + 一条出库流水：出库不改变均价，但必须写下当时的均价（成本事实）
  const after=await balance(warehouseId,id);
  expect(after.quantity).toBe('15.0000');
  expect(num(after.avgCost)).toBe(6.2);
  expect(num(after.amount)).toBeCloseTo(93,5);
  const out=await movement(warehouseId,id,'SALES_OUT');
  expect(out.quantity).toBe('5.0000');
  expect(out.beforeQuantity).toBe('20.0000');
  expect(out.afterQuantity).toBe('15.0000');
  expect(out.unitCost).toBe('6.2000');

  // 状态机：已确认不可重复确认，且失败不产生第二次扣减
  expect((await raw('/scm/inventory/outbound/confirm/'+outboundId)).code).toBe(41014);
  expect((await balance(warehouseId,id)).quantity).toBe('15.0000');
  expect((await movementQuery(warehouseId,id)).total).toBe(2);   // 采购入库 + 出库
});

// ------------------------------------------------------------------
// 2. 盘点确认：盘盈不创造成本、盘亏按当时均价结转
// ------------------------------------------------------------------

test('2 stocktake confirm moves both directions and never invents a cost',async()=>{
  const {id}=sku.get('2')!;
  await stockIn(id,'20.0000');

  const gainId=await post('/scm/inventory/stocktake/create',{warehouseId,remark:name,items:[{skuId:Number(id),actualQuantity:'25.0000'}]});
  await post('/scm/inventory/stocktake/confirm/'+gainId);
  let row=await balance(warehouseId,id);
  expect(row.quantity).toBe('25.0000');
  expect(num(row.avgCost)).toBe(6.2);                // 盘盈没有采购价格事实，均价不动
  const gain=await movement(warehouseId,id,'STOCKTAKE_GAIN');
  expect(gain.quantity).toBe('5.0000');
  expect(gain.beforeQuantity).toBe('20.0000');
  expect(gain.afterQuantity).toBe('25.0000');
  expect(gain.unitCost).toBe('6.2000');

  const lossId=await post('/scm/inventory/stocktake/create',{warehouseId,remark:name,items:[{skuId:Number(id),actualQuantity:'22.0000'}]});
  const lossDetail=await get('/scm/inventory/stocktake/detail/'+lossId);
  expect(lossDetail.items[0].bookQuantity).toBe('25.0000');
  expect(lossDetail.items[0].deltaQuantity).toBe('-3.0000');
  await post('/scm/inventory/stocktake/confirm/'+lossId);
  row=await balance(warehouseId,id);
  expect(row.quantity).toBe('22.0000');
  const loss=await movement(warehouseId,id,'STOCKTAKE_LOSS');
  expect(loss.quantity).toBe('3.0000');
  expect(loss.beforeQuantity).toBe('25.0000');
  expect(loss.afterQuantity).toBe('22.0000');
  expect(loss.unitCost).toBe('6.2000');

  // 状态机 + 边界：已确认不可重复确认；实盘量为负进不来（表单校验先拦），库存不动
  expect((await raw('/scm/inventory/stocktake/confirm/'+lossId)).code).toBe(41020);
  expect((await raw('/scm/inventory/stocktake/create',{warehouseId,remark:name,
    items:[{skuId:Number(id),actualQuantity:'-1.0000'}]})).code).not.toBe(0);
  expect((await balance(warehouseId,id)).quantity).toBe('22.0000');
});

// ------------------------------------------------------------------
// 3. 报损报溢审批：驳回必须留意见、乐观锁、通过后扣库存
// ------------------------------------------------------------------

test('3 loss report approval enforces opinion, optimistic lock and then deducts stock',async({page})=>{
  const {id}=sku.get('3')!;
  await stockIn(id,'20.0000');
  const create=()=>post('/scm/inventory/loss-gain/create',{adjustType:'LOSS',warehouseId,reason:'到货变质',remark:name,
    items:[{skuId:Number(id),quantity:'3.0000'}]});

  const rejected=await create();
  // 本组审批调用一律走 auditor（`<name>_two` 第二管理员）：录单人自己驳回会被 41065 挡下（裁决第 8 条制衡）
  // 驳回不带意见 → 拒绝：沟通成本不该转嫁给录单人
  expect((await rawAs(auditor,`/scm/inventory/loss-gain/reject/${rejected}`,
    {version:await version('/scm/inventory/loss-gain',rejected)})).code).toBe(41037);
  await postAs(auditor,`/scm/inventory/loss-gain/reject/${rejected}`,
    {version:await version('/scm/inventory/loss-gain',rejected),auditOpinion:'数量与验收单不符，请核对'});
  const rejectedDoc=await get('/scm/inventory/loss-gain/detail/'+rejected);
  expect(rejectedDoc.status).toBe('REJECTED');
  expect((await balance(warehouseId,id)).quantity).toBe('20.0000');   // 驳回不产生库存影响
  expect((await movementQuery(warehouseId,id)).list.filter((m:any)=>m.movementType==='LOSS_REPORT')).toHaveLength(0);

  // 乐观锁：版本对不上先失败，不会静默覆盖别人的编辑
  const stale=await create();
  // 审批人同样换人（裁决第 8 条制衡）：换人后下面的 40921 / 41029 才分别来自乐观锁与状态判定，而不是自审禁令
  expect((await rawAs(auditor,`/scm/inventory/loss-gain/approve/${stale}`,{version:9999})).code).toBe(40921);
  expect((await get('/scm/inventory/loss-gain/detail/'+stale)).status).toBe('PENDING');

  await postAs(auditor,`/scm/inventory/loss-gain/approve/${stale}`,
    {version:await version('/scm/inventory/loss-gain',stale),auditOpinion:'已核对'});
  const done=await get('/scm/inventory/loss-gain/detail/'+stale);
  expect(done.status).toBe('COMPLETED');
  expect((await balance(warehouseId,id)).quantity).toBe('17.0000');
  const loss=await movement(warehouseId,id,'LOSS_REPORT');
  expect(loss.unitCost).toBe('6.2000');
  expect(loss.beforeQuantity).toBe('20.0000');
  expect(loss.afterQuantity).toBe('17.0000');
  expect((await rawAs(auditor,`/scm/inventory/loss-gain/approve/${stale}`,{version:done.version})).code).toBe(41029);

  await browse(page,'/inventory/inventory-loss-gain-list');
  await page.getByPlaceholder('单据号').fill(done.lossGainNo);
  await search(page);
  const row=rows(page,'scm-inventory-loss-gain-table',done.lossGainNo);
  await expect(row).toHaveCount(1);
  await expect(row).toContainText('已完成');
  await page.screenshot({path:'../.runtime/w6-write-loss-gain.png',fullPage:true});
});

// ------------------------------------------------------------------
// 4. 调拨发出 → 在途 → 收货（UI 驱动两步）：成本随货平移，不随目标仓清零
// ------------------------------------------------------------------

test('4 transfer moves stock and cost across warehouses in two steps',async({page})=>{
  const {id}=sku.get('4')!;
  await stockIn(id,'20.0000');
  const before=await totalCost(id);
  expect(num(before)).toBeCloseTo(124,5);
  // 目标仓建完立刻停用：Q12 要求启用仓库唯一，不能把第二个启用仓留在库里
  const destId=await post('/scm/warehouse/create',{warehouseCode:(name+'-DST').toUpperCase(),name:name+'调入仓',address:null,remark:name});
  await post('/scm/warehouse/disable',{id:destId,version:await version('/scm/warehouse',destId)});

  const transferId=await post('/scm/inventory/transfer/create',{fromWarehouseId:warehouseId,toWarehouseId:destId,remark:name,
    items:[{skuId:Number(id),quantity:'6.0000'}]});

  await browse(page,'/inventory/inventory-transfer-list');
  const draft=await get('/scm/inventory/transfer/detail/'+transferId);
  await page.getByPlaceholder('调拨单号').fill(draft.transferNo);
  await search(page);
  const row=rows(page,'scm-inventory-transfer-table',draft.transferNo);
  await expect(row).toHaveCount(1);
  await expect(row).toContainText('草稿');

  // 发出：源仓立即扣减，目标仓还没有 —— 在途期间这批货不在任何余额行里（没有虚拟在途仓）
  await row.getByRole('button',{name:'发出'}).click();
  await page.locator('.ant-modal-confirm:visible').getByRole('button',{name:'确认发出'}).click();
  await expect(row).toContainText('在途');
  expect((await balance(warehouseId,id)).quantity).toBe('14.0000');
  expect(await balance(destId,id)).toBeNull();
  const transit=(await get('/scm/inventory/transfer/in-transit')).find((x:any)=>String(x.skuId)===id);
  expect(transit).toBeTruthy();
  expect(transit.quantity).toBe('6.0000');
  expect(transit.toWarehouseId).toBe(Number(destId));

  // 目标仓停用 → 收货被拒，单据停在在途（需要人工启用目标仓后重试）
  expect((await raw(`/scm/inventory/transfer/receive/${transferId}`)).code).toBe(41048);
  expect((await get('/scm/inventory/transfer/detail/'+transferId)).status).toBe('SHIPPED');
  await post('/scm/warehouse/enable',{id:destId,version:await version('/scm/warehouse',destId)});

  // 收货：本轮修的缺陷就落在这里 —— 成本跟着货走，不按目标仓的 0 均价入账
  await row.getByRole('button',{name:'收货'}).click();
  await page.locator('.ant-modal-confirm:visible').getByRole('button',{name:'确认收货'}).click();
  await expect(row).toContainText('已完成');
  await page.screenshot({path:'../.runtime/w6-write-transfer.png',fullPage:true});

  const outLeg=await movement(warehouseId,id,'TRANSFER_OUT');
  const inLeg=await movement(destId,id,'TRANSFER_IN');
  expect(outLeg.unitCost).toBe('6.2000');
  expect(inLeg.unitCost).toBe(outLeg.unitCost);           // 成本随货平移
  expect(inLeg.beforeQuantity).toBe('0.0000');
  expect(inLeg.afterQuantity).toBe('6.0000');
  expect(inLeg.unitSnapshot).toBe(outLeg.unitSnapshot);   // Q13：目标仓新建行沿用源仓记账单位

  const dest=await balance(destId,id);
  expect(dest.quantity).toBe('6.0000');
  expect(num(dest.avgCost)).toBe(6.2);                    // 不是 0
  expect(dest.unit).toBe('kg');                           // Q13：目标仓新建行沿用源仓单位
  const src=await balance(warehouseId,id);
  expect(num(src.amount)+num(dest.amount)).toBeCloseTo(before,5);   // 跨仓总成本守恒
  expect((await get('/scm/inventory/transfer/in-transit')).find((x:any)=>String(x.skuId)===id)).toBeUndefined();

  // 收尾：先反向调拨把货调回源仓，目标仓才有得停用（停用守卫按 quantity > 0 拦，41005）。
  // 启用仓库不唯一会让预留库存直接 41018，所以不能把第二个启用仓留在库里。
  const backId=await post('/scm/inventory/transfer/create',{fromWarehouseId:destId,toWarehouseId:warehouseId,remark:name+'-回库',
    items:[{skuId:Number(id),quantity:'6.0000'}]});
  await post(`/scm/inventory/transfer/ship/${backId}`);
  await post(`/scm/inventory/transfer/receive/${backId}`);
  expect((await balance(destId,id)).quantity).toBe('0.0000');
  expect((await balance(warehouseId,id)).quantity).toBe('20.0000');
  await post('/scm/warehouse/disable',{id:destId,version:await version('/scm/warehouse',destId)});
});

// ------------------------------------------------------------------
// 5. 规格转换审批：跨单位按总成本折算，守恒的是总成本
// ------------------------------------------------------------------

test('5 conversion approval carries total cost across SKUs instead of pricing at zero',async({page})=>{
  const source=sku.get('5')!;
  const target=sku.get('6')!;
  await stockIn(source.id,'20.0000');
  const before=await totalCost(source.id);
  expect(num(before)).toBeCloseTo(124,5);

  const form={warehouseId,convertType:'SPLIT',reason:'客户要散装',remark:name,
    items:[{sourceSkuId:Number(source.id),sourceQuantity:'4.0000',sourceUnit:'kg',
      targetSkuId:Number(target.id),targetQuantity:'10.0000',targetUnit:'kg'}]};
  const conversionId=await post('/scm/inventory/conversion/create',form);
  expect((await get('/scm/inventory/conversion/detail/'+conversionId)).status).toBe('PENDING');

  expect((await raw(`/scm/inventory/conversion/reject/${conversionId}`,
    {version:await version('/scm/inventory/conversion',conversionId)})).code).toBe(41063);
  await post(`/scm/inventory/conversion/reject/${conversionId}`,
    {version:await version('/scm/inventory/conversion',conversionId),auditOpinion:'折算率与供应商不符'});
  expect((await get('/scm/inventory/conversion/detail/'+conversionId)).status).toBe('REJECTED');
  expect((await balance(warehouseId,source.id)).quantity).toBe('20.0000');
  expect(await balance(warehouseId,target.id)).toBeNull();   // 驳回不动库存

  const second=await post('/scm/inventory/conversion/create',form);
  expect((await raw(`/scm/inventory/conversion/approve/${second}`,{version:9999})).code).toBe(40921);
  await post(`/scm/inventory/conversion/approve/${second}`,
    {version:await version('/scm/inventory/conversion',second),auditOpinion:'已核对折算率'});
  const done=await get('/scm/inventory/conversion/detail/'+second);
  expect(done.status).toBe('COMPLETED');

  const outLeg=await movement(warehouseId,source.id,'CONVERT_OUT');
  const inLeg=await movement(warehouseId,target.id,'CONVERT_IN');
  expect(outLeg.unitCost).toBe('6.2000');
  expect(outLeg.afterQuantity).toBe('16.0000');
  // 4 kg × 6.2 = 24.8 的总成本落到 10 kg 上 → 2.48；不是 0，也不是目标 SKU 的现有均价
  expect(num(inLeg.unitCost)).toBeCloseTo(2.48,5);
  expect(num(outLeg.quantity)*num(outLeg.unitCost)).toBeCloseTo(num(inLeg.quantity)*num(inLeg.unitCost),5);

  const targetBalance=await balance(warehouseId,target.id);
  expect(targetBalance.quantity).toBe('10.0000');
  expect(num(targetBalance.avgCost)).toBeCloseTo(2.48,5);
  expect(num(targetBalance.amount)).toBeCloseTo(24.8,5);
  expect(num(await totalCost(source.id))+num(targetBalance.amount)).toBeCloseTo(before,5);

  await browse(page,'/inventory/inventory-conversion-list');
  await page.getByPlaceholder('转换单号').fill(done.conversionNo);
  await search(page);
  const row=rows(page,'scm-inventory-conversion-table',done.conversionNo);
  await expect(row).toHaveCount(1);
  await expect(row).toContainText('已完成');
  await page.screenshot({path:'../.runtime/w6-write-conversion.png',fullPage:true});
});

// ------------------------------------------------------------------
// 6. 写入权限：只读账号既拿不到按钮，也调不动接口
// ------------------------------------------------------------------

test('6 a read-only account sees no write button and is rejected by every write endpoint',async({page})=>{
  const readToken=await login(name+'_read',password);
  const client=await request.newContext({baseURL:apiUrl,extraHTTPHeaders:{Authorization:`Bearer ${readToken}`}});
  const target=sku.get('1')!;
  const body={warehouseId,remark:name,items:[{skuId:Number(target.id),quantity:'1.0000'}]};
  for(const path of ['/scm/inventory/outbound/create','/scm/inventory/stocktake/create','/scm/inventory/transfer/create']){
    const r=await(await client.post(path,{data:body,headers:{'Idempotency-Key':randomUUID()}})).json();
    expect(r.code,`${path} 必须被权限拦截器拒绝`).toBe(30005);
  }
  // 只读账号有页面菜单（否则落到 404，验证不到「按钮被隐藏」），但没有写权限
  await browse(page,'/inventory/inventory-outbound-list',readToken);
  await expect(page.locator('#scm-inventory-outbound-table')).toBeVisible();
  await expect(page.getByRole('button',{name:'新建出库单'})).toHaveCount(0);
  await client.get('/login/logout');
  await client.dispose();
});
