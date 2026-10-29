import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {chromium,expect} from '@playwright/test';

// 显式隔离演练启动时加-EnableScaleControl；不猜测任何现场地址。
const base=process.env.XSY_CONTROL_TEST_URL,fixture=process.env.XSY_CONTROL_FIXTURE_URL,output=process.env.XSY_CONTROL_ARTIFACTS;
test('软件去皮置零、未确认采集门禁与不重复发送', {skip:!base||!fixture||!output,timeout:90000},async()=>{
  for(const url of [base,fixture])assert.equal(new URL(url).hostname,'127.0.0.1');
  await mkdir(output,{recursive:true});
  const browser=await chromium.launch({channel:'chrome',headless:false,args:['--start-maximized']});
  const context=await browser.newContext({viewport:{width:1366,height:768}}),page=await context.newPage();
  const headers={'X-Workstation-Request':'1','X-Operator-Session':'MOCK-OPERATOR'},errors=[],external=[],checks=[];
  page.on('pageerror',error=>errors.push(error.message));
  page.on('console',message=>{if(message.type()==='warning'&&message.text().includes('[Vue warn]'))errors.push(message.text());});
  page.on('request',request=>{if(new URL(request.url()).hostname!=='127.0.0.1')external.push(request.url());});
  const scope={jobId:'mock-job',taskId:'9007199254740993',taskItemId:'9007199254740994'};
  const query='?'+new URLSearchParams(scope),hint=page.getByTestId('scale-control-status');
  const save=page.getByRole('button',{name:'记录上传并打印'}),tare=page.getByRole('button',{name:/^去\s*皮$/}),zero=page.getByRole('button',{name:/^置\s*零$/});
  const note=text=>{checks.push(text);console.log('PASS '+text);};
  async function api(route,data){const response=await context.request[ data===undefined?'get':'post'](base+'/api/agent'+route,{headers,...(data===undefined?{}:{data})});assert.equal(response.status(),200,await response.text());return response.json();}
  async function set(route,data){const response=await context.request.post(fixture+route,{data});assert.equal(response.status(),200);}
  async function weight(value,stable=true){await set('/test/scale',{weight:value,stable});await expect(page.getByTestId('current-weight')).toContainText(String(Number(value)));}
  async function counts(){return (await context.request.get(fixture+'/test/control')).json();}
  async function captureDenied(code){
    // 控制后的500ms窗口reading=null是预期行为，等待新帧后再验证采集门禁。
    await expect.poll(async()=>!!(await api('/status')).reading).toBe(true);
    const status=await api('/status');
    const response=await context.request.post(base+'/api/agent/baskets',{headers,data:{...scope,operationId:crypto.randomUUID(),sampleId:status.reading.sampleId,operatorSessionId:'MOCK-OPERATOR',jobVersion:'0',taskItemVersion:0}});
    assert.equal(response.status(),409);assert.equal((await response.json()).code,code);
  }
  async function shot(name,width){
    await page.setViewportSize({width,height:768});
    for(const button of [tare,zero,save]){const box=await button.boundingBox();assert(box&&box.y+box.height<=768,'主操作首屏可达');}
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
    await page.screenshot({path:path.join(output,name+'.png'),fullPage:true,animations:'disabled'});
  }
  try{
    await page.goto(base);const boot=await api('/workstation');assert.equal(boot.version,'0.4.1');assert.equal(boot.localTest,true);assert.equal(boot.hardwareTest,false);assert.equal(boot.allowScaleControl,true);
    const denied=await context.request.post(base+'/api/agent/scale/tare',{headers:{'X-Workstation-Request':'1','X-Operator-Session':'not-authorized'},data:{}});
    assert.equal(denied.status(),409);assert.equal((await denied.json()).code,'OPERATOR_SESSION_EXPIRED');assert.deepEqual(await counts(),{tareCalls:0,zeroCalls:0});
    await page.getByRole('link',{name:/按商品分拣/}).click();
    await page.locator('.sorting-group').filter({hasText:'测试散装'}).click();
    await page.getByRole('button',{name:'领取并称重'}).first().click();
    await weight('5',false);await expect(tare).toBeDisabled();await expect(zero).toBeDisabled();
    await weight('5');await expect(zero).toBeEnabled();await zero.click({clickCount:2,delay:10});
    await expect(hint).toHaveText('已收到设备稳定零读数');await expect(save).toBeDisabled();
    assert.equal((await api('/status')).control.command,'zero');assert.deepEqual(await counts(),{tareCalls:0,zeroCalls:1});
    note('无授权/不稳定拒绝控制；置零双击只发送一次，真实零重量不作为商品记录');
    await weight('0.28');await expect(tare).toBeEnabled();await tare.click({clickCount:2,delay:10});
    // 前次置零的成功文案可能暂留；必须核对本次去皮的最终真实观测，不能读到WAITING中的null。
    await expect.poll(async()=>{const control=(await api('/status')).control;return [control.command,control.state,control.observedMode];}).toEqual(['tare','ZERO_OBSERVED','NET']);
    await expect(hint).toHaveText('已收到设备稳定零读数');assert.deepEqual(await counts(),{tareCalls:1,zeroCalls:1});
    assert.equal((await api('/status')).control.observedMode,'NET');
    await weight('8.84');await expect(save).toBeEnabled();await shot('control-normal-1366',1366);await shot('control-normal-1024',1024);await page.setViewportSize({width:1366,height:768});
    await save.click();await expect.poll(async()=>(await api('/basket-summary'+query)).basketCount).toBe(1);
    await expect.poll(async()=>(await api('/local-tickets'+query))[0].state).toBe('TEST_ONLY');
    assert.equal(Number((await api('/local-tickets'+query))[0].snapshot.netWeight),8.84);
    note('去皮后稳定NET零值解锁；8.84斤原值保存与演练票一致，1024/1366布局通过');
    await page.getByRole('button',{name:'已换筐，继续称重'}).click();
    await weight('8.86');await set('/test/control',{changesWeight:false});await tare.click();
    await expect(hint).toHaveText('等待稳定零读数，暂不能记录');
    const duplicate=await context.request.post(base+'/api/agent/scale/zero',{headers,data:{}});
    assert.equal(duplicate.status(),409);assert.equal((await duplicate.json()).code,'CONTROL_IN_PROGRESS');
    await expect(page.getByTestId('current-weight')).toContainText('8.86');await captureDenied('CONTROL_IN_PROGRESS');
    await expect(hint).toHaveText('结果未确认，请核对设备后再操作',{timeout:8000});await expect(save).toBeDisabled();
    await captureDenied('CONTROL_UNCONFIRMED');assert.deepEqual(await counts(),{tareCalls:2,zeroCalls:1});
    assert.equal((await api('/basket-summary'+query)).basketCount,1);
    await shot('control-unconfirmed-1024',1024);await page.reload();await expect(hint).toHaveText('结果未确认，请核对设备后再操作');
    await page.getByRole('button',{name:'已换筐，继续称重'}).click();await expect(save).toBeDisabled();
    assert.deepEqual(await counts(),{tareCalls:2,zeroCalls:1});
    note('命令返回成功但无零值时超时未确认；UI和API均禁止新增筐，刷新不重发命令');
    await weight('0');await expect(hint).toHaveText('已收到设备稳定零读数');
    await weight('8.86');await expect(save).toBeEnabled();await save.click();
    await expect.poll(async()=>(await api('/basket-summary'+query)).basketCount).toBe(2);
    assert.equal(Number((await api('/basket-summary'+query)).totalQuantity),17.7);
    await expect.poll(async()=>(await api('/local-tickets'+query)).filter(t=>t.state==='TEST_ONLY').length).toBe(2);
    assert.deepEqual((await api('/baskets'+query)).items.map(b=>Number(b.measurement.netWeight)),[8.84,8.86]);
    note('后续设备稳定零值恢复观测，8.84＋8.86精确累计17.70，两张演练票均不出纸');
    await set('/test/control',{changesWeight:true});
    await page.route('**/api/agent/scale/zero',async route=>{await route.fetch();await route.abort('failed');},{times:1});
    await expect(zero).toBeEnabled();await zero.click();
    await expect.poll(async()=>(await api('/status')).control.state).toBe('ZERO_OBSERVED');
    await expect(zero).toBeEnabled();assert.deepEqual(await counts(),{tareCalls:2,zeroCalls:2});
    const status=await api('/status');assert.equal(status.controlHistory.length,4);assert(status.controlHistory.every(c=>c.state==='ZERO_OBSERVED'));
    note('发送响应丢失不重发，后续SSE与状态查询显示零值观测，最近控制历史可导出');
    assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
    await writeFile(path.join(output,'browser-results.json'),JSON.stringify({checks,errors,external,counts:await counts(),status},null,2));
  }catch(error){await page.screenshot({path:path.join(output,'failure.png'),fullPage:true,animations:'disabled'}).catch(()=>{});await writeFile(path.join(output,'failure.txt'),String(error)+'\n'+await page.locator('body').innerText());throw error;}
  finally{await browser.close();}
});
