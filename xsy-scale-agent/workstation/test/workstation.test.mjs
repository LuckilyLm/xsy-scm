import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {chromium,expect} from '@playwright/test';

// 必须显式指向隔离演练；不以默认地址猜测现场服务。
const base=process.env.XSY_UI_TEST_URL, control=process.env.XSY_UI_CONTROL_URL, output=process.env.XSY_UI_ARTIFACTS;
test('秤端任务、多筐、离线、打印尝试与触屏布局', {skip:!base||!control||!output,timeout:120000},async()=>{
  for(const value of [base,control])assert.equal(new URL(value).hostname,'127.0.0.1');
  await mkdir(output,{recursive:true});
  const browser=await chromium.launch({channel:'chrome',headless:false,args:['--start-maximized']});
  const context=await browser.newContext({viewport:{width:1366,height:768}}),page=await context.newPage();
  const errors=[],external=[],checks=[];
  page.on('pageerror',error=>errors.push(error.message));
  page.on('console',message=>{if(message.type()==='warning'&&message.text().includes('[Vue warn]'))errors.push(message.text());});
  page.on('request',request=>{if(new URL(request.url()).hostname!=='127.0.0.1')external.push(request.url());});
  const note=name=>{checks.push(name);console.log('PASS '+name);};
  const query='?jobId=mock-job&taskId=9007199254740993&taskItemId=9007199254740994';
  async function api(route){const response=await context.request.get(base+'/api/agent'+route,{headers:{'X-Workstation-Request':'1','X-Operator-Session':'MOCK-OPERATOR'}});assert.equal(response.status(),200);return response.json();}
  async function fixture(route,data){const response=await context.request.post(control+route,{data});assert.equal(response.status(),200);}
  async function layout(name,width){
    await page.setViewportSize({width,height:768});
    await expect(page.getByRole('button',{name:'记录本筐并打印'})).toBeVisible();
    const capture=await page.getByRole('button',{name:'记录本筐并打印'}).boundingBox();
    const finish=await page.getByRole('button',{name:'完成商品',exact:true}).boundingBox();
    assert(capture&&finish&&capture.y>=0&&capture.y+capture.height<=768&&finish.y+finish.height<=768,'主操作须在首屏可触达');
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
    await page.screenshot({path:path.join(output,name+'.png'),fullPage:true,animations:'disabled'});note(width+'×768布局主操作首屏可达');
  }
  try{
    await page.goto(base);const boot=await api('/workstation');assert.equal(boot.localTest,true);
    assert.equal(boot.version,'0.3.6');assert.equal(boot.hardwareTest,false);assert.equal(JSON.stringify(boot).includes('LOCAL-TEST-MAINTENANCE'),false);
    const cookies=await context.cookies();assert.equal(cookies.find(c=>c.name==='XSY-Workstation').httpOnly,true);
    assert.equal((await context.request.get(base+'/api/agent/workstation')).status(),401);
    assert.equal((await context.request.get(base+'/api/agent/workstation',{headers:{'X-Workstation-Request':'1',Origin:'https://wrong.example'}})).status(),401);
    assert.equal((await context.request.get(base+'/api/agent/workstation',{headers:{'X-Workstation-Request':'1',Host:'wrong.example'}})).status(),400);
    assert.equal((await context.request.post(base+'/api/agent/workstation/config',{headers:{'X-Workstation-Request':'1','X-Maintenance-Code':'wrong'},data:{protocol:'SOHE_HTTP',serialPort:'',baudRate:9600,httpScaleUrl:control,httpUnit:'kg',httpStableField:'stable',printerName:'EXERCISE-NO-PHYSICAL-PRINTER',localTicketsEnabled:true,paperWidthMm:58,paperHeightMm:110}})).status(),409);
    note('同源HttpOnly鉴权、来源/Host限制及错误维护码拒绝，无密钥泄露');
    await page.getByRole('link',{name:/按商品分拣/}).click();
    await page.locator('.sorting-group').filter({hasText:'测试散装'}).click();
    await page.getByRole('button',{name:'领取并称重'}).first().click();
    await expect(page.getByRole('button',{name:'记录本筐并打印'})).toBeEnabled();
    await layout('weigh-1366',1366);await layout('weigh-1024',1024);await page.setViewportSize({width:1366,height:768});
    await fixture('/test/scale',{weight:'5.000',stable:false});
    await expect(page.getByRole('button',{name:'记录本筐并打印'})).toBeDisabled();note('不稳定读数禁止记录');
    await fixture('/test/scale',{weight:'151.000',stable:true});
    await expect(page.getByText('重量超出可记录范围')).toBeVisible();
    await expect(page.getByRole('button',{name:'记录本筐并打印'})).toBeDisabled();note('超量程禁止记录');
    await fixture('/test/scale',{weight:'5.000',stable:true});
    await expect(page.getByRole('button',{name:'记录本筐并打印'})).toBeEnabled();
    await page.getByRole('button',{name:'记录本筐并打印'}).click({clickCount:2,delay:10});
    await expect.poll(async()=>(await api('/basket-summary'+query)).basketCount).toBe(1);
    await expect(page.getByRole('button',{name:'已换筐，继续称重'})).toBeVisible();
    await expect.poll(async()=>(await api('/local-tickets'+query))[0].state).toBe('TEST_ONLY');
    assert.equal(Number((await api('/basket-summary'+query)).totalQuantity),5);note('连点只保存一筐，演练打印不出纸');
    await fixture('/test/network',{online:false});await fixture('/test/scale',{weight:'4.000',stable:true});
    await page.reload();await expect(page.getByText('离线缓存作业')).toBeVisible();
    await expect(page.getByRole('button',{name:'已换筐，继续称重'})).toBeVisible();
    await page.getByRole('button',{name:'已换筐，继续称重'}).click();
    await expect(page.locator('.weight-display')).toContainText('4');
    // 已落盘的响应被丢弃，页面须用原操作号核对，不能追加第三筐。
    await page.route('**/api/agent/baskets',async route=>{if(route.request().method()==='POST'){await route.fetch();await route.abort('failed');}else await route.continue();},{times:1});
    await page.getByRole('button',{name:'记录本筐并打印'}).click();
    await expect.poll(async()=>(await api('/basket-summary'+query)).basketCount).toBe(2);
    await expect(page.getByRole('button',{name:'已换筐，继续称重'})).toBeVisible({timeout:10000});
    assert.equal(Number((await api('/basket-summary'+query)).totalQuantity),9);note('断网刷新后继续称重5＋4＝9；保存响应丢失未重复记重');
    await page.getByRole('button',{name:'完成商品',exact:true}).click();
    const dialog=page.getByRole('dialog');await dialog.getByRole('combobox').click();
    await page.getByText('短少',{exact:true}).click();
    await dialog.getByRole('textbox').fill('演练缺少1kg');
    await dialog.getByRole('button',{name:'确认完成商品',exact:true}).click();
    await expect(page.getByText('本地已完成',{exact:true})).toBeVisible();
    const summary=await api('/basket-summary'+query);assert.equal(summary.basketCount,2);assert.equal(summary.completion.eventKeys.length,2);
    assert.equal(Number(summary.totalQuantity),9);assert.equal(summary.queueState,'WAITING_CONTRACT');
    const queue=await api('/basket-queue'+query);assert.equal(queue.length,3);assert(queue.every(v=>v.state==='WAITING_CONTRACT'));
    note('完成商品冻结两筐，不再取重、不额外出票、真实同步状态保留待对接');
    await page.screenshot({path:path.join(output,'completed-offline.png'),fullPage:true,animations:'disabled'});
    await page.getByRole('link',{name:'称重记录',exact:true}).click();
    await expect(page.getByRole('button',{name:/补打$/}).first()).toBeEnabled();
    await page.getByRole('button',{name:/凭条$/}).first().click();
    await expect(page.getByText('演练样张 · 不出纸')).toBeVisible();
    await page.screenshot({path:path.join(output,'ticket-preview.png'),fullPage:true,animations:'disabled'});
    await page.getByRole('button',{name:'Close',exact:true}).click();
    await page.getByRole('button',{name:/补打$/}).first().click();
    await page.getByRole('dialog').getByRole('textbox').fill('演练：核对后补打');
    await page.getByRole('button',{name:'确认补打',exact:true}).click();
    await expect.poll(async()=>(await api('/local-tickets'+query)).length).toBe(3);
    assert.equal((await api('/basket-summary'+query)).basketCount,2);assert.equal(Number((await api('/basket-summary'+query)).totalQuantity),9);
    await page.getByRole('button',{name:'刷新记录'}).click();
    await page.screenshot({path:path.join(output,'records.png'),fullPage:true,animations:'disabled'});note('原凭条预览与理由补打只增加打印尝试，累计仍9kg');
    await page.getByRole('link',{name:'设备设置'}).click();
    await expect(page.getByRole('button',{name:'进入配置编辑'})).toBeDisabled();
    await page.screenshot({path:path.join(output,'settings.png'),fullPage:true,animations:'disabled'});note('日常设备配置只读，维护动作受码保护');
    await page.reload();await page.getByRole('link',{name:'称重作业',exact:true}).click();
    await expect(page.getByText('本地已完成',{exact:true})).toBeVisible();note('离线刷新保留两筐与本地完成状态');
    assert.deepEqual(errors,[]);assert.deepEqual(external,[]);note('无Vue运行异常、无外部资源或统计请求');
    await writeFile(path.join(output,'browser-results.json'),JSON.stringify({checks,errors,external,summary},null,2));
  }catch(error){await page.screenshot({path:path.join(output,'failure.png'),fullPage:true,animations:'disabled'}).catch(()=>{});await writeFile(path.join(output,'failure.txt'),String(error)+'\n'+await page.locator('body').innerText());throw error;}
  finally{await browser.close();}
});
