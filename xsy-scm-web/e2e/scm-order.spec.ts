/* W4 real PostgreSQL/API/browser acceptance. Account secrets stay in memory. */
import {test,expect,request,type APIRequestContext,type Page} from '@playwright/test';
import {randomBytes,randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {copyFileSync,readFileSync} from 'node:fs';
import smCrypto from 'sm-crypto';
const apiUrl='http://127.0.0.1:18080';
const name='w4_e2e_'+Date.now().toString(36),password='W4@'+randomBytes(8).toString('hex');
const env={...process.env,W4_E2E_NAME:name,W4_E2E_PASSWORD:password};
let api:APIRequestContext,token:string,customerId:string,skuId:string,standardSkuId:string;
let confirmedOrder:any;
test.describe.configure({mode:'serial'});
async function login(account:string){const c=await request.newContext({baseURL:apiUrl});const captcha=(await(await c.get('/login/getCaptcha')).json()).data;const source=readFileSync('src/lib/encrypt.ts','utf8');const key=/const SM4_KEY = '([^']+)'/.exec(source)![1];const encrypted=Buffer.from(smCrypto.sm4.encrypt(password,Buffer.from(key).toString('hex'))).toString('base64');const r=await(await c.post('/login',{data:{loginName:account,password:encrypted,captchaUuid:captcha.captchaUuid,captchaCode:captcha.captchaText,loginDevice:1}})).json();expect(r.code).toBe(0);await c.dispose();return r.data.token;}
async function post(path:string,data:unknown,key=randomUUID()){const r=await(await api.post(path,{data,headers:{'Idempotency-Key':key}})).json();expect(r.code,`${path}: ${r.msg}`).toBe(0);return r.data;}
function draft(sku=skuId){return {customerId,orderSource:'ADMIN',address:{receiverName:'W4验收',receiverPhone:'13800000000',address:'验收地址'},remark:name,items:[{skuId:sku,orderedQuantity:'2.0000',manualPriceOverride:false}]};}
async function detail(id:string){const r=await(await api.get('/scm/order/detail/'+id)).json();expect(r.code).toBe(0);return r.data;}
async function browse(page:Page,path='/order/order-list'){await page.addInitScript(v=>localStorage.setItem('smart_admin_user_token',v),token);await page.goto('/#'+path);}
/**
 * 用模板自身作为载体写数据行：保留 C/E/H 列的文本格式，
 * 否则 Excel/openpyxl 会把客户编码、SKU 编码、手机号转成数值。
 */
const writeRowsScript=`from openpyxl import load_workbook\nimport sys,json\np=sys.argv[1]; rows=json.loads(sys.argv[2])\nwb=load_workbook(p); ws=wb.active\nfor r in range(2,ws.max_row+1):\n    for c in range(1,13): ws.cell(r,c).value=None\nfor i,row in enumerate(rows,2):\n    for j,v in enumerate(row,1): ws.cell(i,j).value=v\nwb.save(p)`;
function fillRows(path:string,rows:unknown[][]){execFileSync('python',['-c',writeRowsScript,path,JSON.stringify(rows)]);}
async function select(page:Page,label:string,value:string){const box=page.locator(`.ant-form-item:has(#form_item_${label}) .ant-select-selector`);await box.click();await box.locator('input').fill(value);await page.locator('.ant-select-dropdown:visible').getByText(value,{exact:false}).first().click();}
test.beforeAll(async()=>{
 execFileSync('python',['../tools/w4_e2e_accounts.py','setup'],{env,stdio:'pipe'});token=await login(name);api=await request.newContext({baseURL:apiUrl,extraHTTPHeaders:{Authorization:`Bearer ${token}`}});
 const types=(await(await api.post('/scm/customer/type/option/list',{data:{}})).json()).data;
 customerId=await post('/scm/customer/add',{customerCode:name.toUpperCase(),name:name,customerTypeId:types[0].typeId,settleMode:'INDEPENDENT',contactName:'W4验收',contactPhone:'13800000000',address:'验收地址'});
 const customer=(await(await api.get('/scm/customer/detail/'+customerId)).json()).data;await post('/scm/customer/updateStatus',{customerId,version:customer.version,status:'COOPERATING'});
 const tree=(await(await api.post('/scm/product/category/tree',{data:{}})).json()).data;const flatten=(rows:any[]):any[]=>rows.flatMap(x=>[x,...flatten(x.children??[])]);const category=flatten(tree).find(x=>x.level===3)??flatten(tree)[0];
 await post('/scm/product/add',{spuCode:name.toUpperCase(),name:name+'商品',categoryId:category.categoryId,status:'ON_SHELF',images:[],skuList:[{skuCode:name.toUpperCase()+'-KG',specName:'散装',specValues:{规格:'散装'},saleUnit:'kg',productType:'NON_STANDARD',marketPrice:'3.5000',status:'ON_SHELF',defaultFlag:true,sortOrder:0},{skuCode:name.toUpperCase()+'-BOX',specName:'整箱',specValues:{规格:'整箱'},saleUnit:'箱',productType:'STANDARD',marketPrice:'0.0000',status:'ON_SHELF',defaultFlag:false,sortOrder:1}]});
 const options=(await(await api.post('/scm/product/sku/option-list',{data:{keyword:name,limit:10}})).json()).data.options;skuId=options.find((x:any)=>x.specName==='散装').skuId;standardSkuId=options.find((x:any)=>x.specName==='整箱').skuId;
});
test.afterAll(async()=>{if(api){await api.get('/login/logout');await api.dispose();}execFileSync('python',['../tools/w4_e2e_accounts.py','cleanup'],{env,stdio:'pipe'});});

test('1 UI draft entry, diff identity, submit locks price',async({page})=>{
 const consoleErrors:string[]=[];page.on('pageerror',e=>consoleErrors.push(e.message));await browse(page);await expect(page.locator('#order-table')).toBeVisible();await page.getByRole('button',{name:'新建订单',exact:true}).click();await select(page,'customerId',name);
 await page.getByRole('button',{name:'添加商品',exact:true}).click();const skuSelect=page.locator('#order-item-table .ant-select-selector');await skuSelect.click();await skuSelect.locator('input').fill(name);await page.locator('.ant-select-dropdown:visible').getByText('散装',{exact:false}).first().click();await page.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(page.getByText('草稿已保存',{exact:true})).toBeVisible();
 const result=await post('/scm/order/query',{pageNum:1,pageSize:20,customerId});let o=await detail(result.list[0].orderId);const item=o.items[0];expect(item.draftUnitPrice).toBe('3.5000');
 o=await post('/scm/order/update',{...draft(),orderId:o.orderId,version:o.version,items:[{itemId:item.itemId,version:item.version,skuId,orderedQuantity:'3.0000',manualPriceOverride:false}]});expect(o.items[0].itemId).toBe(item.itemId);
 o=await post('/scm/order/submit',{orderId:o.orderId,version:o.version});expect(o.status).toBe('PENDING');expect(o.items[0].lockedUnitPrice).toBe('3.5000');expect(o.orderedTotalAmount).toBe('10.5000');await page.getByRole('button',{name:/^查\s*询$/}).click();await page.screenshot({path:'../.runtime/w4-order-list.png',fullPage:true});expect(consoleErrors).toEqual([]);
});
test('2 nonstandard weight UI, reason and final settlement',async({page})=>{
 let o=await post('/scm/order/create',draft());o=await post('/scm/order/submit',{orderId:o.orderId,version:o.version});expect(o.items[0].actualQuantity).toBeNull();const denied=await(await api.post('/scm/order/confirm',{data:{orderId:o.orderId,version:o.version},headers:{'Idempotency-Key':randomUUID()}})).json();expect(denied.code).toBe(40963);
 await browse(page);await page.getByPlaceholder('名称或订单号').fill(o.orderNo);await page.getByRole('button',{name:/^查\s*询$/}).click();await page.getByText(o.orderNo,{exact:true}).click();await page.getByRole('button',{name:'录入实重',exact:true}).click();await page.locator('#form_item_actualQuantity').fill('1.2500');await page.locator('#form_item_actualQuantityReason').fill('电子秤人工录入');await page.locator('.ant-modal:visible').getByRole('button',{name:'确 定'}).click();await expect(page.locator('.ant-modal:visible')).toHaveCount(0);await page.getByRole('button',{name:'确认订单',exact:true}).click();await page.locator('.ant-modal-confirm').getByRole('button',{name:'确 定'}).click();await expect(page.locator('.ant-drawer-body').getByText('已确认',{exact:true})).toBeVisible();confirmedOrder=await detail(o.orderId);expect(confirmedOrder.settlementTotalAmount).toBe('4.3750');await expect(page.locator('.ant-modal-confirm')).toHaveCount(0);await page.screenshot({path:'../.runtime/w4-order-detail.png',fullPage:true});
});
test('3 cancellation preserves terminal rules and zero-price standard order',async({page})=>{
 const o=await post('/scm/order/create',draft(standardSkuId));expect(o.orderedTotalAmount).toBe('0.0000');await browse(page);await page.getByPlaceholder('名称或订单号').fill(o.orderNo);await page.getByRole('button',{name:/^查\s*询$/}).click();await page.getByText(o.orderNo,{exact:true}).click();await page.getByRole('button',{name:'取消订单',exact:true}).click();await page.locator('#form_item_cancelReason').fill('客户取消');await page.locator('.ant-modal:visible').getByRole('button',{name:'确 定'}).click();await expect(page.locator('.ant-drawer-body').getByText('已取消',{exact:true})).toBeVisible();const denied=await(await api.post('/scm/order/cancel',{data:{orderId:confirmedOrder.orderId,version:confirmedOrder.version,reason:'不可取消'},headers:{'Idempotency-Key':randomUUID()}})).json();expect(denied.code).toBe(40960);
});
test('4 supplement reason, original identity and duplicate request',async()=>{
 const data={...draft(),orderSource:'SUPPLEMENT',originalOrderId:confirmedOrder.orderId};let r=await(await api.post('/scm/order/create',{data,headers:{'Idempotency-Key':randomUUID()}})).json();expect(r.code).toBe(40060);const key=randomUUID();const accepted={...data,supplementReason:'漏录补单'};const first=await post('/scm/order/create',accepted,key);const second=await post('/scm/order/create',accepted,key);expect(first.orderId).toBe(second.orderId);r=await(await api.post('/scm/order/create',{data:{...accepted,remark:'changed'},headers:{'Idempotency-Key':key}})).json();expect(r.code).toBe(40966);
});
test('5 audit page, error retry, empty results and read-only permission',async({page})=>{
 await browse(page,'/order/order-log-list');await expect(page.locator('#order-log-table')).toBeVisible();const changedLog=page.locator('#order-log-table tr').filter({hasText:'修改'}).first();await changedLog.getByRole('button',{name:'变更前后'}).click();const diff=page.locator('.ant-modal:visible .scm-diff');await expect(diff).toContainText('变更前');await expect(diff).toContainText('变更后');await expect(diff).toContainText('已变更');await page.locator('.ant-modal-close').click();
 const readToken=await login(name+'_read');const read=await request.newContext({baseURL:apiUrl,extraHTTPHeaders:{Authorization:`Bearer ${readToken}`}});const denied=await(await read.post('/scm/order/create',{data:draft(),headers:{'Idempotency-Key':randomUUID()}})).json();expect(denied.code).toBe(30005);await read.get('/login/logout');await read.dispose();
 await browse(page);await page.route('**/scm/order/query',route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code:50000,msg:'验收模拟错误',ok:false})}));await page.getByRole('button',{name:/^查\s*询$/}).click();await expect(page.locator('.ant-alert-error')).toContainText('验收模拟错误');await page.unroute('**/scm/order/query');const recovered=page.waitForResponse(r=>r.url().endsWith('/scm/order/query')&&r.request().method()==='POST');await page.locator('.ant-alert').getByRole('button',{name:/^重\s*试$/}).click();await recovered;await expect(page.locator('.ant-alert-error')).toHaveCount(0);await expect(page.locator('#order-table .ant-spin-spinning')).toHaveCount(0);await page.getByPlaceholder('名称或订单号').fill('NO-SUCH-W4-ORDER');await page.getByPlaceholder('名称或订单号').press('Enter');await expect(page.locator('#order-table .ant-empty')).toBeVisible();await page.setViewportSize({width:1920,height:1080});await page.screenshot({path:'../.runtime/w4-order-empty-1920.png',fullPage:true});
});
test('6 return approval atomically generates refund and completes in UI',async({page})=>{
 const f={orderId:confirmedOrder.orderId,reason:'质量问题',items:[{orderItemId:confirmedOrder.items[0].itemId,requestedQuantity:'1.0000'}]};const r=await post('/scm/order/return/create',f);const excess=await(await api.post('/scm/order/return/create',{data:f,headers:{'Idempotency-Key':randomUUID()}})).json();expect(excess.code).toBe(40969);
 await browse(page,'/order/order-return-list');await expect(page.locator('#order-return-table')).toBeVisible();const row=page.locator('tr').filter({hasText:r.returnNo});await row.getByRole('button',{name:/^批\s*准$/}).click();await page.locator('.ant-modal:visible').getByRole('button',{name:'确 定'}).click();await expect(row).toContainText('已批准');
 const refunds=await post('/scm/order/refund/query',{pageNum:1,pageSize:20,orderId:confirmedOrder.orderId});expect(refunds.list).toHaveLength(1);await browse(page,'/order/order-refund-list');const refund=page.locator('tr').filter({hasText:refunds.list[0].refundNo});await refund.getByRole('button',{name:'登记退款完成'}).click();await page.locator('.ant-modal:visible input').fill(name+'-refund');await page.locator('.ant-modal:visible').getByRole('button',{name:'确 定'}).click();await expect(refund).toContainText('已完成');await expect(page.locator('.ant-modal:visible')).toHaveCount(0);await page.screenshot({path:'../.runtime/w4-refund-list.png',fullPage:true});expect((await detail(confirmedOrder.orderId)).status).toBe('CONFIRMED');
});

test('7 download template and import standard/nonstandard orders atomically',async({page})=>{
 await browse(page);await page.getByRole('button',{name:'导入订单',exact:true}).click();
 const downloadPromise=page.waitForEvent('download');await page.getByRole('button',{name:'下载 Excel 模板',exact:true}).click();const download=await downloadPromise;
 expect(download.suggestedFilename()).toBe('销售订单导入模板.xlsx');const valid='../.runtime/w4-order-import-valid.xlsx';await download.saveAs(valid);
 const customerCode=name.toUpperCase(),standardCode=customerCode+'-BOX',weightCode=customerCode+'-KG';
 const fillScript=`from openpyxl import load_workbook\nimport sys\np=sys.argv[1]; customer=sys.argv[2]; standard=sys.argv[3]; weight=sys.argv[4]; bad=sys.argv[5]=='bad'\nwb=load_workbook(p); ws=wb.active\nfor row in range(2,ws.max_row+1):\n    for col in range(1,13): ws.cell(row,col).value=None\nrows=[['1.0','IMPORT-STANDARD',customer,'W4验收','13800000000','验收地址',None,standard,'2.0000',None,None,'页面联调'],['1.0','IMPORT-WEIGHT',customer,'W4验收','13800000000','验收地址',None,weight,'3.0000',None,None,'页面联调']]\nif bad: rows.append(['1.0','IMPORT-BAD',customer,'W4验收','13800000000','验收地址',None,'SKU-NOT-FOUND','1.0000',None,None,'错误行'])\nfor r,row in enumerate(rows,2):\n    for c,v in enumerate(row,1): ws.cell(r,c).value=v\nwb.save(p)`;
 execFileSync('python',['-c',fillScript,valid,customerCode,standardCode,weightCode,'valid']);
 await page.locator('.ant-modal:visible input[type=file]').setInputFiles(valid);await page.getByRole('button',{name:'开始导入',exact:true}).click();
 await expect(page.locator('.ant-modal:visible .ant-result-title')).toHaveText('订单导入完成');await expect(page.locator('.ant-modal:visible .ant-result-subtitle')).toContainText('已确认 1 张，待称重 1 张');
 const imported=await post('/scm/order/query',{pageNum:1,pageSize:20,customerId,orderSource:'IMPORT'});const importedRows=imported.list.filter((x:any)=>x.remark==='页面联调');expect(importedRows.map((x:any)=>x.status).sort()).toEqual(['CONFIRMED','PENDING']);
 const bad='../.runtime/w4-order-import-bad.xlsx';copyFileSync(valid,bad);
 execFileSync('python',['-c',fillScript,bad,customerCode,standardCode,weightCode,'bad']);await page.getByRole('button',{name:'取 消'}).click();await page.getByRole('button',{name:'导入订单',exact:true}).click();
 const before=(await post('/scm/order/query',{pageNum:1,pageSize:100,customerId,orderSource:'IMPORT'})).total;await page.locator('.ant-modal:visible input[type=file]').setInputFiles(bad);await page.getByRole('button',{name:'开始导入',exact:true}).click();
 await expect(page.getByText(/发现 1 个问题，订单未写入/)).toBeVisible();await expect(page.locator('.ant-modal:visible')).toContainText('SKU 编码不存在');const after=(await post('/scm/order/query',{pageNum:1,pageSize:100,customerId,orderSource:'IMPORT'})).total;expect(after).toBe(before);
 const bytes=readFileSync(valid);const key=randomUUID();const first=await api.post('/scm/order/import',{multipart:{file:{name:'retry.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:bytes}},headers:{'Idempotency-Key':key}});const second=await api.post('/scm/order/import',{multipart:{file:{name:'retry.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:bytes}},headers:{'Idempotency-Key':key}});const one=(await first.json()).data.orders.map((x:any)=>x.orderId),two=(await second.json()).data.orders.map((x:any)=>x.orderId);expect(two).toEqual(one);
});

test('8 sample row and manual override rules block the whole batch',async({page})=>{
 const customerCode=name.toUpperCase(),standardCode=customerCode+'-BOX';
 const importFile=async(path:string)=>{await page.locator('.ant-modal:visible input[type=file]').setInputFiles(path);await page.getByRole('button',{name:'开始导入',exact:true}).click();};
 const reopen=async()=>{await page.getByRole('button',{name:'取 消'}).click();await page.getByRole('button',{name:'导入订单',exact:true}).click();};
 const importedTotal=async()=>(await post('/scm/order/query',{pageNum:1,pageSize:100,customerId,orderSource:'IMPORT'})).total;
 await browse(page);await page.getByRole('button',{name:'导入订单',exact:true}).click();

 // A) 原样上传刚下载的模板（示例行未替换）→ 整批失败、零写入，错误指到具体字段
 const raw='../.runtime/w4-order-import-raw.xlsx';
 const downloadPromise=page.waitForEvent('download');await page.getByRole('button',{name:'下载 Excel 模板',exact:true}).click();await (await downloadPromise).saveAs(raw);
 const baseline=await importedTotal();
 await importFile(raw);
 await expect(page.locator('.ant-modal:visible')).toContainText('发现 2 个问题，订单未写入');
 const sampleErrors=page.locator('.ant-modal:visible .ant-table tbody tr.ant-table-row');
 await expect(sampleErrors).toHaveCount(2);
 // 实际行号（Excel 第 2 行）+ 订单标识 + 字段 + 原因
 await expect(sampleErrors.first().locator('td').first()).toHaveText('2');
 await expect(sampleErrors.first()).toContainText('ORDER-001');
 await expect(sampleErrors.first()).toContainText('客户编码');
 await expect(sampleErrors.first()).toContainText('客户编码不存在');
 await expect(sampleErrors.nth(1)).toContainText('SKU编码');
 await expect(sampleErrors.nth(1)).toContainText('SKU 编码不存在');
 expect(await importedTotal()).toBe(baseline);

 // B) 人工单价 + 改价原因（账号同时持有 scm:order:import 与 scm:order:price-override）→ 成功且按人工价成交
 const manual='../.runtime/w4-order-import-manual.xlsx';copyFileSync(raw,manual);
 fillRows(manual,[['1.0','IMPORT-MANUAL',customerCode,'W4验收','13800000000','验收地址',null,standardCode,'2.0000','1.2345','客户议价','人工改价']]);
 await reopen();await importFile(manual);
 await expect(page.locator('.ant-modal:visible .ant-result-title')).toHaveText('订单导入完成');
 const manualOrder=(await post('/scm/order/query',{pageNum:1,pageSize:20,customerId,orderSource:'IMPORT'})).list.find((x:any)=>x.remark==='人工改价');
 const manualDetail=await detail(manualOrder.orderId);expect(manualDetail.status).toBe('CONFIRMED');expect(manualDetail.items[0].lockedUnitPrice).toBe('1.2345');expect(manualDetail.orderedTotalAmount).toBe('2.4690');

 // C) 填了人工单价却不填改价原因 → 整批失败、零写入
 const noReason='../.runtime/w4-order-import-noreason.xlsx';copyFileSync(raw,noReason);
 fillRows(noReason,[['1.0','IMPORT-NOREASON',customerCode,'W4验收','13800000000','验收地址',null,standardCode,'2.0000','1.2345',null,'缺原因']]);
 const beforeC=await importedTotal();await reopen();await importFile(noReason);
 await expect(page.locator('.ant-modal:visible')).toContainText('发现 1 个问题，订单未写入');
 await expect(page.locator('.ant-modal:visible .ant-table')).toContainText('填写人工单价时必须填写改价原因');
 expect(await importedTotal()).toBe(beforeC);
});

test('9 history reuse prefills a new draft and the recent-price popover shows the locked price',async({page})=>{
 const consoleErrors:string[]=[];page.on('pageerror',e=>consoleErrors.push(e.message));
 await browse(page);
 await page.getByPlaceholder('名称或订单号').fill(confirmedOrder.orderNo);
 await page.getByRole('button',{name:/^查\s*询$/}).click();
 const row=page.locator('#order-table tbody tr').filter({hasText:confirmedOrder.orderNo}).first();
 await row.getByRole('button',{name:'复用为新单'}).click();
 const drawer=page.locator('.ant-drawer:visible');
 // 复用生成的是「新建」态：只带一行历史商品，价格按当前价重新解析，绝不沿用历史锁价
 await expect(drawer.getByRole('button',{name:'创建订单'})).toBeVisible();
 // 横向滚动表格的 tbody 还含一行零高度测量行，只数真实数据行
 await expect(page.locator('#order-item-table tbody tr.ant-table-row')).toHaveCount(1);
 // 历史价只读旁证：点开后展示该客户该 SKU 的最近已确认订单价与来源订单
 await page.locator('#order-item-table .recent-btn').click();
 const pop=page.locator('.ant-popover:visible');
 await expect(pop).toContainText('3.5000');
 await expect(pop.locator('.recent-meta')).toContainText(confirmedOrder.orderNo);
 // 价签必须挂在抽屉内部：浮层此前挂在 body 上，抽屉关了它还飘在列表页。
 // 这里刻意在浮层开着的状态下关抽屉，直接复现该缺陷。
 await drawer.locator('.ant-drawer-footer').getByRole('button',{name:/^关\s*闭$/}).click();
 // 关掉的抽屉根节点仍挂在文档里（fixed 定位，:visible 恒真），开合状态只能看 ant-drawer-open。
 await expect(page.locator('.ant-drawer-open')).toHaveCount(0);
 await expect(page.locator('.ant-popover:visible')).toHaveCount(0);
 // 组件实例不销毁，重开抽屉时不能直接冒出上次的价签。仍走复用入口：
 // 上面那次关闭落了本地草稿，走「新建订单」会先弹草稿恢复框，与本用例要验的东西无关。
 await row.getByRole('button',{name:'复用为新单'}).click();
 await expect(drawer.getByRole('button',{name:'创建订单'})).toBeVisible();
 await expect(page.locator('.ant-popover:visible')).toHaveCount(0);
 expect(consoleErrors).toEqual([]);
});

test('10 unsaved new-order draft is kept locally and offered for recovery on reopen',async({page})=>{
 await browse(page);
 await page.getByRole('button',{name:'新建订单',exact:true}).click();
 await select(page,'customerId',name);
 await page.getByRole('button',{name:'添加商品',exact:true}).click();
 const skuSelect=page.locator('#order-item-table .ant-select-selector');
 await skuSelect.click();await skuSelect.locator('input').fill(name);
 await page.locator('.ant-select-dropdown:visible').getByText('散装',{exact:false}).first().click();
 await page.locator('.ant-drawer:visible').locator('.ant-drawer-footer').getByRole('button',{name:/^关\s*闭$/}).click();
 await expect(page.locator('.ant-drawer-open')).toHaveCount(0);
 // 重新新建：命中本地草稿恢复提示，选「恢复」回填并按当前价格重新解析
 await page.getByRole('button',{name:'新建订单',exact:true}).click();
 const confirmBox=page.locator('.ant-modal-confirm');
 await expect(confirmBox).toContainText('未提交的订单草稿');
 await confirmBox.getByRole('button',{name:/^恢\s*复$/}).click();
 await expect(page.locator('#order-item-table tbody tr.ant-table-row')).toHaveCount(1);
 await expect(page.locator('#order-item-table .price-cell').first()).toContainText('3.5000');
 await page.locator('.ant-drawer:visible').locator('.ant-drawer-footer').getByRole('button',{name:/^关\s*闭$/}).click();
});

/** §5.2 离页防丢：两条通道都不经过「关闭」按钮，且各自用不同证据归因，避免把 keep-alive 当成防丢。 */
test('12 unsaved draft survives a page reload and a route switch',async({page})=>{
 // 存储键由 order-form-model.draftKey 按登录用户拼出；E2E 不 import 生产代码，按前缀取回唯一一条。
 const drafts=()=>page.evaluate(()=>Object.entries(localStorage)
  .filter(([k])=>k.startsWith('xsy-scm:order-draft:'))
  .map(([,v])=>JSON.parse(v)));
 const fillDraft=async()=>{
  await page.getByRole('button',{name:'新建订单',exact:true}).click();
  await select(page,'customerId',name);
  await page.getByRole('button',{name:'添加商品',exact:true}).click();
  const skuSelect=page.locator('#order-item-table .ant-select-selector');
  await skuSelect.click();await skuSelect.locator('input').fill(name);
  await page.locator('.ant-select-dropdown:visible').getByText('散装',{exact:false}).first().click();
  await expect(page.locator('#order-item-table tbody tr.ant-table-row')).toHaveCount(1);
 };
 const recover=async()=>{
  await page.getByRole('button',{name:'新建订单',exact:true}).click();
  const confirmBox=page.locator('.ant-modal-confirm');
  await expect(confirmBox).toContainText('未提交的订单草稿');
  // 刷新后这是本次会话第一次挂载抽屉：确认框必须浮在抽屉之上，否则用户只看到空白抽屉且点不到「恢复」。
  await confirmBox.getByRole('button',{name:/^恢\s*复$/}).click();
  // 恢复后按当前价重新解析，价格与明细行都在
  await expect(page.locator('#order-item-table tbody tr.ant-table-row')).toHaveCount(1);
  await expect(page.locator('#order-item-table .price-cell').first()).toContainText('3.5000');
 };
 await browse(page);
 await fillDraft();
 // 通道一（路由切换）：抽屉开着直接切走，全程没点「关闭」，本地草稿只可能由 onBeforeRouteLeave 写入
 await browse(page,'/purchase/purchase-demand-list');
 const left=await drafts();
 expect(left,'切走路由后本地没有草稿 = onBeforeRouteLeave 未落盘').toHaveLength(1);
 expect(left[0].items).toHaveLength(1);
 // 通道二（刷新）：改一个只存在于这一版的可辨识字段再刷新，草稿里出现它才说明是 beforeunload 写的
 await browse(page);
 const reloadMark='reload_'+Date.now().toString(36);
 await page.locator('#form_item_remark').fill(reloadMark);
 await page.reload();
 await expect(page.getByRole('button',{name:'新建订单',exact:true})).toBeVisible();
 const reloaded=await drafts();
 expect(reloaded).toHaveLength(1);
 expect(reloaded[0].remark,'刷新后草稿仍是切换路由那一版 = beforeunload 未落盘').toBe(reloadMark);
 await recover();
 await expect(page.locator('#form_item_remark')).toHaveValue(reloadMark);
 await page.locator('.ant-drawer:visible').locator('.ant-drawer-footer').getByRole('button',{name:/^关\s*闭$/}).click();
});

/** 历史价浮层是 trigger=click 的切换语义：先确保关闭再点一次，才能拿到「按当前 (客户,SKU) 现算」的那一版内容。 */
async function openRecentPopover(page:Page){
 const pop=page.locator('.ant-popover:visible');
 if(await pop.count()>0){await page.locator('#order-item-table .recent-btn').first().click();}
 await expect(pop).toHaveCount(0);
 await page.locator('#order-item-table .recent-btn').first().click();
 await expect(pop).toHaveCount(1);
}
async function pickSku(page:Page,spec:string){
 const box=page.locator('#order-item-table .ant-select-selector').first();
 await box.click();await box.locator('input').fill(name);
 await page.locator('.ant-select-dropdown:visible').getByText(spec,{exact:false}).first().click();
}
/** 自建合作中客户；名称带独立后缀，保证前端按整名搜索时只命中它自己。 */
async function cooperatingCustomer(suffix:string){
 const types=(await(await api.post('/scm/customer/type/option/list',{data:{}})).json()).data;
 const customerName=name+'_'+suffix;
 const id=await post('/scm/customer/add',{customerCode:customerName.toUpperCase(),name:customerName,customerTypeId:types[0].typeId,settleMode:'INDEPENDENT',contactName:'W4验收',contactPhone:'13800000000',address:'验收地址'});
 const r=await(await api.get('/scm/customer/detail/'+id)).json();
 await post('/scm/customer/updateStatus',{customerId:id,version:r.data.version,status:'COOPERATING'});
 return {customerName,customerId:id};
}
/** 落一张该客户该 SKU 的已确认单：非标品必须先录入实重才允许确认，改价行必须带原因。 */
async function confirmedOrderOf(options:{customer:string,sku:string,price?:string,nonStandard?:boolean}){
 const {customer,sku,price,nonStandard}=options;
 const item:{skuId:string,orderedQuantity:string,manualPriceOverride:boolean,unitPrice?:string,overrideReason?:string}=
  {skuId:sku,orderedQuantity:'1.0000',manualPriceOverride:price!==undefined};
 if(price!==undefined){item.unitPrice=price;item.overrideReason='验收议价';}
 let o=await post('/scm/order/create',{...draft(sku),customerId:customer,items:[item]});
 o=await post('/scm/order/submit',{orderId:o.orderId,version:o.version});
 if(nonStandard)o=await post('/scm/order/item/actual-quantity',{orderId:o.orderId,itemId:o.items[0].itemId,version:o.items[0].version,actualQuantity:'1.0000',reason:'电子秤人工录入'});
 if(o.status!=='CONFIRMED')o=await post('/scm/order/confirm',{orderId:o.orderId,version:o.version});
 expect(o.status).toBe('CONFIRMED');
 return o;
}
test('11 recent-price panel is keyed by customer+SKU: switching either never bleeds the other price',async({page})=>{
 // §12.3 Wave 3「历史价切 SKU 不串 / 切客户不串」。缓存键曾含明细行序号，
 // 换商品或换客户后重开浮层会直接吐出上一档的价，且不再发请求。
 // 本用例自建客户与订单，不借用其他用例留下的数据，因此单独跑也成立。
 const consoleErrors:string[]=[];page.on('pageerror',e=>consoleErrors.push(e.message));
 const customerA=await cooperatingCustomer('r3a');
 const customerB=await cooperatingCustomer('r3b');
 const boxA=await confirmedOrderOf({customer:customerA.customerId,sku:standardSkuId,price:'1.3579'});
 const kgA=await confirmedOrderOf({customer:customerA.customerId,sku:skuId,nonStandard:true});
 const boxB=await confirmedOrderOf({customer:customerB.customerId,sku:standardSkuId,price:'9.8700'});

 await browse(page);
 await page.getByRole('button',{name:'新建订单',exact:true}).click();
 await select(page,'customerId',customerA.customerName);
 await page.getByRole('button',{name:'添加商品',exact:true}).click();
 const pop=page.locator('.ant-popover:visible');
 await pickSku(page,'整箱');
 await openRecentPopover(page);
 // 客户 A + 整箱：只有自建的那一档，不能出现同客户散装档的价
 await expect(pop).toContainText('1.3579');
 await expect(pop.locator('.recent-meta')).toContainText(boxA.orderNo);
 await expect(pop).not.toContainText('3.5000');
 await expect(pop).not.toContainText(kgA.orderNo);
 // 同一行换商品 → 必须按新 SKU 重新取数，不能沿用行序号缓存
 await pickSku(page,'散装');
 await openRecentPopover(page);
 await expect(pop).toContainText('3.5000');
 await expect(pop.locator('.recent-meta')).toContainText(kgA.orderNo);
 await expect(pop).not.toContainText('1.3579');
 // 换回整箱 → 同一 (客户,SKU) 组合仍然命中，且不是「还没查」的空态
 await pickSku(page,'整箱');
 await openRecentPopover(page);
 await expect(pop).toContainText('1.3579');
 // 换客户、SKU 不动 → 只能看到该客户自己的历史成交价
 await select(page,'customerId',customerB.customerName);
 await openRecentPopover(page);
 await expect(pop).toContainText('9.8700');
 await expect(pop.locator('.recent-meta')).toContainText(boxB.orderNo);
 await expect(pop).not.toContainText('1.3579');
 await expect(pop).not.toContainText('3.5000');
 await expect(pop).not.toContainText(boxA.orderNo);
 await expect(pop).not.toContainText(kgA.orderNo);
 await page.screenshot({path:'../.runtime/w4-recent-price.png',fullPage:true});
 expect(consoleErrors).toEqual([]);
});
