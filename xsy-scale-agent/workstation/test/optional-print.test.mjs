import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {chromium,expect} from '@playwright/test';

// 必须使用全新隔离演练；不访问现场串口、打印机或正式平台。
const base=process.env.XSY_OPTIONAL_URL,fixture=process.env.XSY_OPTIONAL_FIXTURE,output=process.env.XSY_OPTIONAL_ARTIFACTS;
test('逐筐可选打印、原选择重试、手输与离线共用累计', {skip:!base||!fixture||!output,timeout:120000},async()=>{
  for(const url of [base,fixture])assert.equal(new URL(url).hostname,'127.0.0.1');
  await mkdir(output,{recursive:true});
  const browser=await chromium.launch({channel:'chrome',headless:false,args:['--start-maximized']});
  const context=await browser.newContext({viewport:{width:1366,height:768}}),page=await context.newPage();
  const headers={'X-Workstation-Request':'1','X-Operator-Session':'MOCK-OPERATOR'},checks=[],errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  page.on('console',m=>{if(m.type()==='warning'&&m.text().includes('[Vue warn]'))errors.push(m.text());});
  const q='?jobId=mock-job&taskId=9007199254740993&taskItemId=9007199254740994';
  async function api(route){const r=await context.request.get(base+'/api/agent'+route,{headers});assert.equal(r.status(),200);return r.json();}
  async function set(route,data){const r=await context.request.post(fixture+route,{data});assert.equal(r.status(),200);}
  async function weight(value){await set('/test/scale',{weight:value,stable:true,connected:true});await expect(page.getByTestId('current-weight')).toContainText(value);}
  async function count(n,total,tickets){await expect.poll(async()=>(await api('/basket-summary'+q)).basketCount).toBe(n);assert.equal(Number((await api('/basket-summary'+q)).totalQuantity),total);assert.equal((await api('/local-tickets'+q)).length,tickets);}
  function note(s){checks.push(s);console.log('PASS '+s);}
  async function shot(name,width){await page.setViewportSize({width,height:768});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await page.screenshot({path:path.join(output,name+'.png'),fullPage:true,animations:'disabled'});}
  try{
    await page.goto(base);assert.equal((await api('/workstation')).version,'0.4.1');
    await page.getByRole('link',{name:/按商品分拣/}).click();await page.locator('.sorting-group').filter({hasText:'测试散装'}).click();
    await page.getByRole('button',{name:'领取并称重'}).first().click();
    const print=page.getByRole('button',{name:/记录上传并打印$/}),upload=page.getByRole('button',{name:/记录上传$/});
    await weight('8.55');
    for(const width of [1366,1024]){
      await shot('two-actions-'+width,width);
      for(const button of [print,upload,page.getByRole('button',{name:'完成商品',exact:true})]){
        const b=await button.boundingBox();assert(b&&b.height>=48&&b.y>=0&&b.y+b.height<=768,'两个动作和完成按钮首屏可触达');
      }
      assert((await upload.boundingBox()).y>(await print.boundingBox()).y,'记录上传在打印按钮下方');
    }
    await upload.click({clickCount:2,delay:10});await count(1,8.55,0);
    await expect(page.getByText('未要求打印',{exact:true})).toBeVisible();note('设备只上传连点仅一条、无票、明确待同步');
    await page.getByRole('button',{name:'已换筐，继续称重'}).click();await weight('4.25');await print.click();await count(2,12.8,1);
    await expect.poll(async()=>(await api('/local-tickets'+q))[0].state).toBe('TEST_ONLY');
    note('下一筐选择打印只生成一张，累计包含两种动作');
    await page.getByRole('button',{name:'已换筐，继续称重'}).click();await weight('1.25');
    await page.route('**/api/agent/baskets',route=>route.request().method()==='POST'?route.abort('failed'):route.continue(),{times:1});
    await upload.click();await expect(page.getByRole('button',{name:'重试上次保存'})).toBeVisible();await expect(print).toBeDisabled();
    await page.reload();await expect(page.getByRole('button',{name:'重试上次保存'})).toBeVisible();await expect(print).toBeDisabled();
    await page.getByRole('button',{name:'重试上次保存'}).click();await count(3,14.05,1);note('请求未到服务端，刷新仅原号重试且不允许改成打印');
    await set('/test/network',{online:false});await set('/test/scale',{weight:'0',stable:false,connected:false});
    await page.getByRole('button',{name:'手工录入净重',exact:true}).click();
    const dialog=page.getByRole('dialog',{name:'手工录入净重'}),input=dialog.getByRole('textbox',{name:'手工净重（斤）'});
    await input.fill('120.5');
    await shot('manual-two-actions-1024',1024);
    for(const label of ['记录上传并打印','记录上传']){const b=await dialog.getByRole('button',{name:label,exact:true}).boundingBox();assert(b&&b.height>=48&&b.y+b.height<=768);}
    await page.route('**/api/agent/manual-baskets',route=>route.abort('failed'),{times:1});
    await dialog.getByRole('button',{name:'记录上传',exact:true}).click();await expect(dialog.getByRole('button',{name:'重试原保存'})).toBeVisible();
    await expect(dialog.getByRole('button',{name:'记录上传并打印'})).toBeDisabled();await page.reload();
    await expect(input).toHaveValue('120.5');await expect(input).toBeDisabled();
    await dialog.getByRole('button',{name:'重试原保存'}).click();await count(4,134.55,1);
    note('断网且断秤手输只上传，原打印选择跨刷新冻结且无新增票');
    await page.getByRole('button',{name:'手工录入净重',exact:true}).click();await input.fill('2.5');
    await page.route('**/api/agent/manual-baskets',async route=>{await route.fetch();await route.abort('failed');},{times:1});
    await dialog.getByRole('button',{name:'记录上传',exact:true}).click();await count(5,137.05,1);
    await expect(dialog).toBeHidden({timeout:10000});await page.reload();await count(5,137.05,1);note('已保存但响应丢失不会重复，网络补传意图与打印选择独立');
    const rows=(await api('/baskets'+q)).items;assert.equal(rows.filter(r=>r.printState==='NOT_REQUESTED').length,4);
    await page.getByRole('button',{name:'完成商品',exact:true}).click();const complete=page.getByRole('dialog');await complete.getByRole('combobox').click();await page.getByText('超出',{exact:true}).click();await complete.getByRole('textbox').fill('混合可选打印测试');await complete.getByRole('button',{name:'确认完成商品',exact:true}).click();
    await expect(page.getByText('本地已完成',{exact:true})).toBeVisible();const total=await api('/basket-summary'+q);assert.equal(total.completion.eventKeys.length,5);assert.equal(total.queueState,'WAITING_CONTRACT');
    await page.getByRole('link',{name:'称重记录',exact:true}).click();await expect(page.locator('td').getByText('未要求打印',{exact:true})).toHaveCount(4);
    const noPrintRows=page.locator('tr').filter({hasText:'未要求打印'});await expect(noPrintRows).toHaveCount(4);
    for(const row of await noPrintRows.all()){await expect(row.getByRole('button',{name:/凭条$/})).toBeDisabled();await expect(row.getByRole('button',{name:/补打$/})).toBeDisabled();}
    await shot('optional-records-1024',1024);note('完成包含五筐，未打印记录明确状态且没有假凭条或补打入口');
    assert.deepEqual(errors,[]);await writeFile(path.join(output,'browser-results.json'),JSON.stringify({checks,errors,total},null,2));
  }catch(e){await page.screenshot({path:path.join(output,'failure.png'),fullPage:true}).catch(()=>{});await writeFile(path.join(output,'failure.txt'),String(e)+'\n'+await page.locator('body').innerText());throw e;}
  finally{await browser.close();}
});
