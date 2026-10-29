import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {chromium,expect} from '@playwright/test';

// 仅显式全新回环演练；不连接串口或真实Windows打印队列。
const base=process.env.XSY_MANUAL_URL,fixture=process.env.XSY_MANUAL_FIXTURE,output=process.env.XSY_MANUAL_ARTIFACTS;
test('市斤实称与手输、触屏键盘、离线/断秤和未知结果恢复', {skip:!base||!fixture||!output,timeout:120000},async()=>{
  for(const url of [base,fixture])assert.equal(new URL(url).hostname,'127.0.0.1');
  await mkdir(output,{recursive:true});
  const browser=await chromium.launch({channel:'chrome',headless:false,args:['--start-maximized']});
  const context=await browser.newContext({viewport:{width:1366,height:768}}),page=await context.newPage();
  const headers={'X-Workstation-Request':'1','X-Operator-Session':'MOCK-OPERATOR'},checks=[],errors=[],external=[];
  page.on('pageerror',e=>errors.push(e.message));
  page.on('console',m=>{if(m.type()==='warning'&&m.text().includes('[Vue warn]'))errors.push(m.text());});
  page.on('request',r=>{if(new URL(r.url()).hostname!=='127.0.0.1')external.push(r.url());});
  const scope={jobId:'mock-job',taskId:'9007199254740993',taskItemId:'9007199254740994'},q='?'+new URLSearchParams(scope);
  const note=s=>{checks.push(s);console.log('PASS '+s);};
  async function api(route,data){const r=await context.request[data===undefined?'get':'post'](base+'/api/agent'+route,{headers,...(data===undefined?{}:{data})});assert.equal(r.status(),200,await r.text());return r.json();}
  async function set(route,data){const r=await context.request.post(fixture+route,{data});assert.equal(r.status(),200);}
  async function weight(value){await set('/test/scale',{weight:value,stable:true,connected:true});await expect(page.getByTestId('current-weight')).toContainText(value);}
  async function count(n,total){await expect.poll(async()=>(await api('/basket-summary'+q)).basketCount).toBe(n);assert.equal(Number((await api('/basket-summary'+q)).totalQuantity),total);}
  async function open(){await page.getByRole('button',{name:'手工录入净重',exact:true}).click();await expect(page.getByRole('dialog',{name:'手工录入净重'})).toBeVisible();}
  async function shot(name,width){await page.setViewportSize({width,height:768});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await page.screenshot({path:path.join(output,name+'.png'),fullPage:true,animations:'disabled'});}
  try{
    await page.goto(base);assert.equal((await api('/workstation')).version,'0.4.1');assert((await page.title()).includes('鲜蔬园'));
    await page.getByRole('link',{name:/按商品分拣/}).click();await page.locator('.sorting-group').filter({hasText:'测试散装'}).click();
    await page.getByRole('button',{name:'领取并称重'}).first().click();
    const save=page.getByRole('button',{name:'记录上传并打印'});
    for(const value of ['0.35','380']){await weight(value);await expect(save).toBeEnabled();}
    await weight('0');await expect(save).toBeDisabled();await expect(page.getByText('净重须大于零')).toBeVisible();
    await weight('8.55');await expect(save).toBeEnabled();
    for(const width of [1366,1024]){
      await shot('jin-weigh-'+width,width);const box=await save.boundingBox();const finish=await page.getByRole('button',{name:'完成商品',exact:true}).boundingBox();
      assert(box&&finish&&box.y+box.height<=768&&finish.y+finish.height<=768,'主操作须在首屏内');
    }
    await save.click();await count(1,8.55);note('0.35和380斤不受固定范围拦截，零值拒绝；8.55斤实称原值保存');
    await open();const input=page.getByRole('textbox',{name:'手工净重（斤）'}),confirm=page.getByRole('dialog',{name:'手工录入净重'}).getByRole('button',{name:'记录上传并打印'});
    for(const bad of ['0','-1','1.00001','1e3']){await input.fill(bad);await expect(confirm).toBeDisabled();}
    await input.fill('120.5');await page.getByRole('textbox',{name:'称重说明'}).fill('西安仓库大秤已扣皮重');await expect(confirm).toBeEnabled();
    await shot('manual-dialog-1024',1024);const btn=await confirm.boundingBox();assert(btn&&btn.y+btn.height<=768);
    await confirm.click({clickCount:2,delay:10});await count(2,129.05);
    const records=await api('/baskets'+q);assert.equal(records.items[0].measurement.entrySource,'DEVICE');assert.equal(records.items[1].measurement.entrySource,'MANUAL');
    assert.equal(records.items[1].measurement.stableFlag,false);assert.equal(records.items[1].measurement.entryNote,'西安仓库大秤已扣皮重');
    note('手输120.5与实称8.55合计129.05，连点不重复，手输不冒充稳定采样');
    await open();await expect(input).toHaveValue('');
    for(const key of ['0','.','3','5'])await page.locator('.manual-keypad').getByRole('button',{name:key,exact:true}).click();
    await expect(input).toHaveValue('0.35');await confirm.click();await count(3,129.4);note('触屏键盘输入0.35斤，每次保存后清空草稿');
    await set('/test/network',{online:false});await set('/test/scale',{weight:'0',stable:false,connected:false});
    await expect.poll(async()=>(await api('/status')).scaleConnected).toBe(false);await page.reload();await expect(page.getByText('离线缓存作业')).toBeVisible();
    await open();await input.fill('380.35');
    await page.route('**/api/agent/manual-baskets',async route=>{await route.fetch();await route.abort('failed');},{times:1});
    await confirm.click();await count(4,509.75);
    await expect(page.getByRole('dialog',{name:'手工录入净重'})).toBeHidden({timeout:10000});
    await page.reload();await count(4,509.75);note('平台断网且电子秤断连，手输380.35仍保存；丢失成功响应/刷新未重复');
    await open();await input.fill('14.25');
    await page.route('**/api/agent/manual-baskets',route=>route.abort('failed'),{times:1});await confirm.click();
    await expect(page.getByRole('button',{name:'重试原保存'})).toBeVisible();await page.reload();
    await expect(input).toHaveValue('14.25');await expect(input).toBeDisabled();
    await page.getByRole('button',{name:'重试原保存'}).click();await count(5,524);note('请求未到服务端时刷新恢复冻结请求，同操作号重试只新增一次');
    await expect.poll(async()=>(await api('/local-tickets'+q)).every(t=>t.state==='TEST_ONLY')).toBe(true);
    await page.getByRole('button',{name:'完成商品',exact:true}).click();const finishDialog=page.getByRole('dialog');await finishDialog.getByRole('combobox').click();await page.getByText('超出',{exact:true}).click();await finishDialog.getByRole('textbox').fill('含其他大秤手输重量');await finishDialog.getByRole('button',{name:'确认完成商品',exact:true}).click();
    await expect(page.getByText('本地已完成',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'手工录入净重',exact:true})).toBeDisabled();await count(5,524);
    const done=await api('/basket-summary'+q);assert.equal(done.completion.eventKeys.length,5);assert.equal(done.queueState,'WAITING_CONTRACT');
    await page.getByRole('link',{name:'称重记录',exact:true}).click();await expect(page.locator('td').getByText('手工录入',{exact:true})).toHaveCount(4);
    await shot('manual-records-1024',1024);note('混合来源共用完成集合，来源和说明可追溯，正式同步仍等待契约');
    assert.deepEqual(errors,[]);assert.deepEqual(external,[]);await writeFile(path.join(output,'browser-results.json'),JSON.stringify({checks,errors,external,summary:done},null,2));
  }catch(e){await page.screenshot({path:path.join(output,'failure.png'),fullPage:true}).catch(()=>{});await writeFile(path.join(output,'failure.txt'),String(e)+'\n'+await page.locator('body').innerText());throw e;}
  finally{await browser.close();}
});
