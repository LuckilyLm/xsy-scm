import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {chromium,expect} from '@playwright/test';

// 必须使用显式GroupingFixture的新隔离目录，不能对现场运行地址猜测测试。
const base=process.env.XSY_SORT_TEST_URL,fixture=process.env.XSY_SORT_FIXTURE_URL,output=process.env.XSY_SORT_ARTIFACTS;
test('商品客户分组、同名身份隔离和跨入口共用记录', {skip:!base||!fixture||!output,timeout:90000},async()=>{
  for(const url of [base,fixture])assert.equal(new URL(url).hostname,'127.0.0.1');
  await mkdir(output,{recursive:true});
  const browser=await chromium.launch({channel:'chrome',headless:false,args:['--start-maximized']});
  const context=await browser.newContext({viewport:{width:1366,height:768}}),page=await context.newPage();
  const headers={'X-Workstation-Request':'1','X-Operator-Session':'MOCK-OPERATOR'},errors=[],external=[],checks=[];
  page.on('pageerror',error=>errors.push(error.message));
  page.on('console',message=>{if(message.type()==='warning'&&message.text().includes('[Vue warn]'))errors.push(message.text());});
  page.on('request',request=>{if(new URL(request.url()).hostname!=='127.0.0.1')external.push(request.url());});
  const query='?'+new URLSearchParams({jobId:'mock-job',taskId:'9007199254740993',taskItemId:'9007199254740994'});
  const note=text=>{checks.push(text);console.log('PASS '+text);};
  async function api(route){const response=await context.request.get(base+'/api/agent'+route,{headers});assert.equal(response.status(),200,await response.text());return response.json();}
  async function weight(value){await context.request.post(fixture+'/test/scale',{data:{weight:value,stable:true}});await expect(page.getByTestId('current-weight')).toContainText(value);}
  async function screenshot(name,width=1366){
    await page.setViewportSize({width,height:768});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
    await page.screenshot({path:path.join(output,name+'.png'),fullPage:true,animations:'disabled'});
  }
  /** 检查唯一返回按钮的触屏尺寸，随后验证确定父级；不依赖浏览器历史。 */
  async function parent(expected,width=1024){
    await page.setViewportSize({width,height:768});
    const button=page.getByRole('button',{name:'返回上级',exact:true});
    await expect(button).toHaveCount(1);await expect(button).toBeVisible();await expect(button).toBeEnabled();
    // 完成后页面可能保留滚动位置，先按实际点击行为滚入视口，再检验触屏尺寸和父级。
    await button.scrollIntoViewIfNeeded();
    const box=await button.boundingBox();assert(box&&box.height>=48&&box.width>=48&&box.x>=0&&box.y>=0&&box.x+box.width<=width&&box.y+box.height<=768);
    await button.click();await expect.poll(()=>new URL(page.url()).hash).toBe('#'+expected);
  }
  const group=label=>page.locator('.sorting-group').filter({hasText:label});
  try{
    await page.goto(base);await expect(page.locator('.task-overview>.metric')).toHaveCount(0);
    await expect(page.locator('.sorting-entry')).toHaveCount(2);
    await expect(page.locator('.task-overview').getByText(/待称商品|本次可见客户|任务来源/)).toHaveCount(0);
    await expect(page.getByRole('button',{name:'返回上级',exact:true})).toHaveCount(0);
    await screenshot('task-home-1366');await screenshot('task-home-1024',1024);
    await page.getByRole('link',{name:/按商品分拣/}).click();
    // RouterLink异步导航完成后再刷新，避免reload抢在路由提交前仍刷新首页。
    await expect.poll(()=>new URL(page.url()).hash).toBe('#/tasks/products');
    await page.reload();await parent('/tasks');await page.getByRole('link',{name:/按商品分拣/}).click();
    await expect(page.locator('.sorting-group')).toHaveCount(6);
    await expect(group('测试散装')).toContainText('2 项任务');
    await expect(group('测试散装')).toContainText('2 位客户');
    await expect(group('小颗精品')).toHaveCount(1);
    await expect(group('旧任务散装')).toHaveCount(2);
    await screenshot('product-groups-1366');await screenshot('product-groups-1024',1024);note('首页只留两个入口，商品选择刷新后可返回；同SKU/不同ID/缺ID分组保持');
    await page.getByPlaceholder('搜索商品或规格').fill('不存在的商品');await expect(page.getByText('暂无匹配任务')).toBeVisible();
    await page.getByPlaceholder('搜索商品或规格').fill('测试散装');await expect(page.locator('.sorting-group')).toHaveCount(1);
    await group('测试散装').click();await expect(page.locator('.task-row')).toHaveCount(2);
    const productPath=page.url();await page.reload();await expect(page.locator('.task-row')).toHaveCount(2);assert.equal(page.url(),productPath);
    await parent('/tasks/products');await group('测试散装').click();assert.equal(page.url(),productPath);
    await page.getByPlaceholder('搜索客户、订单或商品').fill('清风');await expect(page.locator('.task-row')).toHaveCount(1);
    await page.getByPlaceholder('搜索客户、订单或商品').fill('');await screenshot('product-customer-tasks-1366');await screenshot('product-customer-tasks-1024',1024);
    await page.getByRole('button',{name:'领取并称重'}).first().click();
    await expect(page.getByRole('button',{name:'记录上传并打印'})).toBeEnabled();
    await weight('8.84');await page.getByRole('button',{name:'记录上传并打印'}).click();
    await expect.poll(async()=>(await api('/basket-summary'+query)).basketCount).toBe(1);
    await screenshot('product-weigh-1024',1024);await page.reload();await expect(page.getByTestId('current-weight')).toBeVisible();
    await parent(new URL(productPath).hash.slice(1));assert.equal(page.url(),productPath);
    await parent('/tasks/products');await parent('/tasks',1366);
    await page.getByRole('link',{name:/按客户分拣/}).click();await expect(page.locator('.sorting-group')).toHaveCount(5);
    await page.reload();await parent('/tasks',1366);await page.getByRole('link',{name:/按客户分拣/}).click();
    await expect(group('悦来餐厅')).toHaveCount(2);await expect(group('同名旧客户')).toHaveCount(2);
    await screenshot('customer-groups-1366');await screenshot('customer-groups-1024',1024);note('商品路径称重/列表/选择逐级返回，客户选择刷新后可返回，同名客户仍独立');
    await group('客户编号 customer-1').click();await expect(page.locator('.task-row')).toHaveCount(3);
    const customerPath=page.url();await page.reload();await expect(page.locator('.task-row')).toHaveCount(3);
    await parent('/tasks/customers',1366);await group('客户编号 customer-1').click();assert.equal(page.url(),customerPath);
    await screenshot('customer-product-tasks-1366');await screenshot('customer-product-tasks-1024',1024);
    await page.locator('.task-row').filter({hasText:'测试生菜'}).getByRole('button',{name:'进入称重'}).click();
    await expect(page.getByRole('button',{name:'已换筐，继续称重'})).toBeVisible();
    assert.equal((await api('/basket-summary'+query)).basketCount,1);
    await page.getByRole('button',{name:'已换筐，继续称重'}).click();await weight('8.86');
    await page.getByRole('button',{name:'记录上传并打印'}).click();
    await expect.poll(async()=>(await api('/basket-summary'+query)).basketCount).toBe(2);
    const summary=await api('/basket-summary'+query);assert.equal(Number(summary.totalQuantity),17.7);
    const tickets=await api('/local-tickets'+query);assert.equal(tickets.length,2);assert(tickets.every(t=>t.snapshot.traceUrl===null&&t.snapshot.traceReference===t.snapshot.eventKey));
    await page.getByRole('button',{name:'完成商品',exact:true}).click();await page.getByRole('dialog').getByRole('combobox').click();
    await page.getByText('超出',{exact:true}).click();await page.getByRole('dialog').getByRole('textbox').fill('分组路径共享累计验证');
    await page.getByRole('button',{name:'确认完成商品',exact:true}).click();await expect(page.getByText('本地已完成',{exact:true})).toBeVisible();
    await screenshot('customer-weigh-1366');await parent(new URL(customerPath).hash.slice(1),1366);
    await parent('/tasks/customers');await parent('/tasks');
    note('商品路径8.84加客户路径8.86仍为原任务两筐17.70斤，未生成双份记录');
    await page.goto(base+'/#/tasks/products');await group('测试散装').click();
    await page.locator('.task-row').filter({hasText:'悦来餐厅'}).getByRole('button',{name:'进入称重'}).click();
    await expect(page.getByText('本地已完成',{exact:true})).toBeVisible();
    // 原页面完成后用完成面板替换采集按钮，同时核实服务端仍拒绝新增筐。
    await expect(page.getByRole('button',{name:'记录上传并打印'})).toHaveCount(0);
    const status=await api('/status');
    const denied=await context.request.post(base+'/api/agent/baskets',{headers,data:{jobId:'mock-job',taskId:'9007199254740993',taskItemId:'9007199254740994',jobVersion:'0',taskItemVersion:0,operatorSessionId:'MOCK-OPERATOR',operationId:crypto.randomUUID(),sampleId:status.reading.sampleId}});
    assert.equal(denied.status(),409);assert.equal((await denied.json()).code,'ITEM_LOCALLY_COMPLETED');
    await page.getByRole('link',{name:'称重记录',exact:true}).click();await page.getByRole('button',{name:/凭条$/}).first().click();
    await expect(page.getByText('查询入口待配置',{exact:true})).toBeVisible();await screenshot('trace-pending-preview-1024',1024);
    await page.getByRole('button',{name:'Close',exact:true}).click();
    await context.request.post(fixture+'/test/network',{data:{online:false}});
    await page.goto(base+'/#/tasks/customers');await page.reload();await expect(page.getByText('离线缓存作业')).toBeVisible();
    await expect(page.locator('.sorting-group')).toHaveCount(5);await group('客户编号 customer-1').click();await expect(page.locator('.task-row')).toHaveCount(3);
    for(const mode of ['products','customers']){
      await page.goto(base+'/#/tasks/'+mode+'/unavailable-group');await page.reload();
      await expect(page.getByText('当前分组不可用',{exact:true}).last()).toBeVisible();
      await parent('/tasks/'+mode);await expect(page.locator('.sorting-group')).toHaveCount(mode==='products'?6:5);
      await parent('/tasks');
    }
    const customerReturn=new URL(customerPath).hash.slice(1);
    await page.goto(base+'/#/weigh/missing/item?'+new URLSearchParams({returnTo:customerReturn}));await page.reload();
    await expect(page.getByText('当前任务不可用',{exact:true})).toBeVisible();await parent(customerReturn);
    await expect(page.locator('.task-row')).toHaveCount(3);
    assert.equal((await api('/basket-summary'+query)).basketCount,2);assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
    note('客户路径称重/列表/选择逐级返回；离线、失效分组及失效任务刷新后可返回，无外部请求或Vue异常');
    await writeFile(path.join(output,'browser-results.json'),JSON.stringify({checks,errors,external,summary,tickets},null,2));
  }catch(error){await page.screenshot({path:path.join(output,'failure.png'),fullPage:true}).catch(()=>{});await writeFile(path.join(output,'failure.txt'),String(error)+'\n'+await page.locator('body').innerText());throw error;}
  finally{await browser.close();}
});
