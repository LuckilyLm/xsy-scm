/**
 * 采购需求、收货、日志、仓库的前端契约测试。
 *
 * 这四个页面此前只做过「操作列居中」（commit ffa95370 是 8 文件 16 行的 align 改动），
 * 列精简与动作分组都没有做。本文件钉住三件容易回退的事：
 *
 * 1. 越界的操作列宽度：普通操作列 120～160px，
 *    而采购收货此前是 250px。
 * 2. 日志页的时间不能隐藏，裸 ID 不能上列：日志是审计凭据，
 *    「采购单 id」这种内部主键对排查没有价值，且会被误读成单号。
 * 3. 危险动作不能与普通动作同排常驻：删除 / 停用必须进「更多」，
 *    否则操作列里最显眼的永远是那排低价值或红色的文字。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

/** 读源码并剥掉注释（先剥 HTML 注释再剥块注释，顺序不能反）。 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
}

const BASE = '../src/views/business/scm/';
const demand = code(`${BASE}purchase/purchase-demand-list.vue`);
const receipt = code(`${BASE}purchase/purchase-receipt-list.vue`);
const purchaseLog = code(`${BASE}purchase/purchase-log-list.vue`);
const warehouse = code(`${BASE}purchase/warehouse-list.vue`);

/** 取出「操作」列声明里的 width；取不到就断言失败，不返回默认值。 */
function actionWidth(source, name) {
  const matched = /dataIndex: 'action'[\s\S]{0,80}?width: (\d+)/.exec(source);
  assert.ok(matched, `${name}：找不到操作列的 width`);
  return Number(matched[1]);
}

test('采购需求：来源、批次、商品和编码分列，常用列前置', () => {
  assert.match(demand, /title: '来源单号', dataIndex: 'salesOrderNoSnapshot'/);
  assert.match(demand, /title: '需求来源 \/ 批次', dataIndex: 'calculationBatchId'/);
  assert.match(demand, /title: '商品名称', dataIndex: 'productName'/);
  assert.match(demand, /title: '规格编码', dataIndex: 'skuCode'[\s\S]{0,80}showFlag: false/);
  assert.ok(!demand.includes(`title: '需求单位'`), '采购需求仍有独立的需求单位列');
  // 单位跟在每个数量后面（量纲注脚），三个数量列都要有
  assert.match(demand, /dataIndex === 'requiredQuantity'[\s\S]{0,200}record\.demandUnit/);
  assert.match(demand, /dataIndex === 'unallocatedQuantity'[\s\S]{0,200}record\.demandUnit/);
  assert.match(demand, /dataIndex === 'skuCode'[\s\S]{0,100}record\.skuCode/);
  assert.match(demand, /const columns = ref<DemandListColumn\[\]>\(/);
  // scrollX 必须由列宽派生，不能写死数字；具体实现经共享的 scmColumnsWidth 计算。
  assert.match(demand, /const scrollX = computed\(\(\) => scmColumnsWidth\(columns\.value\)\)/);
  assert.match(demand, /:scroll="\{ x: scrollX \}"/);
  assert.match(demand, /dataIndex: 'requiredQuantity'[\s\S]{0,300}dataIndex: 'unallocatedQuantity'[\s\S]{0,300}dataIndex: 'status'/);
  assert.match(demand, /<TableOperator v-model="columns"/);
});

test('采购收货：操作列收到 160px 以内，编辑与删除进「更多」', () => {
  const width = actionWidth(receipt, '采购收货');
  assert.ok(width <= 160, `采购收货操作列 ${width}px 超出 160px`);
  // 当前状态唯一的推进动作仍常驻行内
  assert.match(receipt, /record\.status === 'DRAFT'/);
  assert.match(receipt, /record\.putawayStatus === 'PENDING'/);
  // 低频 / 危险动作收进「更多」，并走 hasPermission 裁剪（v-privilege 对菜单项不生效）
  assert.match(receipt, /ScmActionMore/);
  assert.match(receipt, /hasPermission\('scm:purchase:receipt:update'\)/);
  assert.match(receipt, /hasPermission\('scm:purchase:receipt:delete'\)/);
});

test('采购日志：时间必须保留，裸主键列必须移除或改名', () => {
  // 时间字段例外：日志页时间不能被隐藏
  assert.match(purchaseLog, /title: '时间'/);
  // 内部主键不是业务标识，上列只会被误读成单号
  assert.ok(!purchaseLog.includes(`title: '采购单 id'`), '采购日志仍有裸的「采购单 id」列');
  // 收货单列仍在，但列名必须点明它是内部编号而不是单号
  assert.match(purchaseLog, /title: '关联收货单（内部编号）'/);
  assert.match(purchaseLog, /title: '操作人'/);
  assert.match(purchaseLog, /title: '原因'/);
});

test('仓库：创建时间下沉，定位状态可见，启停进「更多」', () => {
  assert.ok(!warehouse.includes(`title: '创建时间'`), '仓库列表仍默认展示创建时间');
  const width = actionWidth(warehouse, '仓库');
  assert.ok(width <= 160, `仓库操作列 ${width}px 超出 160px`);
  // 仓库编码是运营配置识别字段，保留
  assert.match(warehouse, /title: '仓库编码'/);
  // 地址不能因为「省市区能定位」就整列删掉：它是库管实际找货的凭据
  assert.match(warehouse, /title: '区域 \/ 地址'/);
  assert.match(warehouse, /record\.address/);
  // 定位状态是「一眼要挑出来」的信号，用图标 + Tooltip
  assert.match(warehouse, /title: '定位'/);
  assert.match(warehouse, /isLocated\(record\)/);
  // 启用 / 停用是状态机动作，不与普通动作同排常驻
  assert.match(warehouse, /ScmActionMore/);
  assert.match(warehouse, /hasPermission\('scm:warehouse:disable'\)/);
  assert.match(warehouse, /hasPermission\('scm:warehouse:enable'\)/);
});

test('四个采购页面的操作列全部居中', () => {
  for (const [name, source] of [['采购需求', demand], ['采购收货', receipt], ['采购日志', purchaseLog], ['仓库', warehouse]]) {
    // 列声明有单行与多行两种写法，不能假设 align 紧跟在 dataIndex 后面
    assert.match(source, /dataIndex: 'action'[\s\S]{0,80}?align: 'center'/, `${name}的操作列未居中`);
  }
});
