/**
 * 界面与布局模块契约测试。
 *
 * 由 13 个独立测试文件按模块合并：每个 `{ … }` 块对应原来一份文件，
 * 块内 helper / 常量各自作用域隔离；只有 import 提到顶部并去重。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, statSync, readdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {mergeColumn} from '../src/components/support/table-operator/smart-table-column-merge.ts';
import {tableColumnsWidth} from '../src/components/support/table-operator/table-width.ts';
import {splitDrivingBatches, planRoadRoute} from '../src/components/business/scm/map/amap-driving-provider.ts';
import ts from 'typescript';

// ---------------------------------------------------------------- 来源：home-ui-contract.test.mjs
{
  function source(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  const TREND = source('../src/views/system/home/components/business-trend.vue');
  const HEADER = source('../src/views/system/home/home-header.vue');
  const RANKING = source('../src/views/system/home/components/ranking-card.vue');
  const NOTICE = source('../src/views/system/home/home-notice.vue');
  const HOME = source('../src/views/system/home/index.vue');
  const METRIC_CARDS = source('../src/views/system/home/components/metric-cards.vue');
  const REGION_ERROR = source('../src/views/system/home/components/region-error.vue');
  const USE_ECHARTS = source('../src/views/business/scm/screen/composables/use-echarts.ts');

  test('home trend distinguishes valid zero series from unavailable trend data', () => {
    assert.match(TREND, /const hasData = computed\(/);
    assert.match(TREND, /const allZero = computed\(/);
    assert.match(TREND, /primarySeries\.length !== trend\.dates\.length/);
    assert.match(TREND, /secondarySeries\.length !== trend\.dates\.length/);
    assert.match(TREND, /Number\.isFinite\(Number\(value\)\)[\s\S]{0,60}Number\(value\) === 0/);
    assert.match(TREND, /description="暂无趋势数据"/);
    assert.match(TREND, /:description="allZeroDescription"/);
    assert.match(TREND, /sales: '暂无成交记录'[\s\S]{0,100}purchase: '暂无采购记录'[\s\S]{0,100}inventory: '暂无库存流转记录'/);
    assert.match(TREND, /height: 264px/);
    assert.match(TREND, /@media \(max-width: 767px\)[\s\S]{0,240}height: 232px/);
  });

  test('home welcome and ranking empty states give real data more room', () => {
    assert.match(HEADER, /min-height: 148px/);
    assert.doesNotMatch(HEADER, /home-welcome__brand|home-welcome__slogan|xsy-logo-mark\.png/);
    assert.match(RANKING, /\.home-rank__empty[\s\S]{0,160}min-height: 200px/);
    assert.match(RANKING, /\.home-rank__list[\s\S]{0,160}min-height: 300px/);
    // Permission-based card visibility and responsive columns remain data-driven.
    assert.match(HOME, /v-if="canRanking"/);
    assert.match(HOME, /v-if="canHealth"/);
    assert.match(METRIC_CARDS, /grid-template-columns: repeat\(var\(--kpi-columns\)/);
  });

  test('blank notice titles have a safe label and preserve the notice source', () => {
    assert.match(NOTICE, /function noticeTitle\(item: HomeNotice\): string/);
    assert.match(NOTICE, /item\.title\?\.trim\(\) \|\| '未命名通知'/);
    assert.match(NOTICE, /noticeApi\.queryEmployeeNotice/);
    assert.match(NOTICE, /:title="noticeTitle\(item\)"/);
    assert.match(NOTICE, /\{\{ noticeTitle\(item\) \}\}/);
  });

  test('home request failures appear centered in their own region with retry', () => {
    assert.match(REGION_ERROR, /<a-empty[\s\S]{0,180}role="alert"/);
    assert.match(REGION_ERROR, /justify-content:\s*center/);
    assert.match(REGION_ERROR, /<a-button size="small" @click="emit\('retry'\)">重试<\/a-button>/);
    assert.doesNotMatch(REGION_ERROR, /<a-alert/);
    for (const component of [TREND, RANKING, NOTICE, REGION_ERROR]) {
      assert.match(component, /retry|重试/);
    }
  });

  /*
   * 「切换了但图不变」回归锁。
   *
   * 首页趋势卡的容器是 v-show 控制的：点指标时 active 先变、data 还是旧响应，
   * hasData 立即转 false，容器短暂 0×0。此刻 render() 已经带着新数据跑过一次，
   * 但 useEcharts.ensure() 见尺寸为 0 就把配置丢进了 pending。
   * 若 ResizeObserver 在元素恢复尺寸时只调 resize()，画布就会用旧配置重绘 —— 五个指标
   * 画出来一模一样。修复点是在有实例时也把 pending 重新 setOption 一次。
   */
  test('chart composable replays the latest option when a hidden container becomes visible again', () => {
    // 尺寸为 0 时必须早退，不能进 resize 分支（否则会在隐藏态白跑一次重绘）
    assert.match(
      USE_ECHARTS,
      /new ResizeObserver\(\(\) => \{[\s\S]{0,400}if \(!el\.clientWidth \|\| !el\.clientHeight\) \{[\s\S]{0,80}return;/
    );
    // 已有实例 + 有 pending ⇒ 必须重新应用配置，而不是只 resize()
    assert.match(USE_ECHARTS, /if \(pending\) \{[\s\S]{0,80}chart\.setOption\(pending, true\);/);
    // setOption 必须同时记住 pending，供上面那条分支消费
    assert.match(USE_ECHARTS, /function setOption\(option: ChartOption, notMerge = true\) \{[\s\S]{0,120}pending = option;/);
  });
}

// ---------------------------------------------------------------- 来源：mall-home-layout-contract.test.mjs
{
  const read = path => readFileSync(new URL(path, import.meta.url), 'utf8');
  const api = read('../src/api/business/scm/mall-home-api.ts');
  const page = read('../src/views/business/scm/mall/mall-home-layout.vue');

  test('首页草稿、发布和回滚提交各自的乐观锁版本与原因', () => {
    assert.match(api, /\/draft', \{version, themeCode, blocks\}/);
    assert.match(api, /\/publish', \{draftVersion, pointerVersion\}/);
    assert.match(api, /\/rollback', \{targetReleaseId, pointerVersion, reason\}/);
    assert.match(page, /:disabled="dirty \|\| !blocks\.length"[^>]*@click="publish"/);
    assert.match(page, /mallHomeApi\.publish\(draftVersion\.value, pointerVersion\.value\)/);
    assert.match(page, /mallHomeApi\.rollback\(rollbackTarget\.value\.id, pointerVersion\.value, rollbackReason\.value\.trim\(\)\)/);
    assert.match(page, /mallHomeApi\.preview\(previewCustomerId\.value, previewWarehouseId\.value\)/);
  });

  test('装修页操作和抽屉遵守 SCM 页面约定', () => {
    const action = page.match(/\{title: '操作', dataIndex: 'action', align: 'center', width: (\d+)\}/);
    assert.ok(action);
    assert.ok(Number(action[1]) <= 180);
    assert.match(page, /:width="scmDrawerWidth\('s'\)"/);
    assert.match(page, /<ScmActionMore v-if="hasPermission\('scm:mall:home:edit'\)"/);
    assert.doesNotMatch(page, /var\(--scm-border-color\)|var\(--scm-fill-color\)/);
  });

  test('装修管理端走后台前缀，不落进客户商城的 /scm/mall/ 拦截范围', () => {
    // 员工令牌打 /scm/mall/** 会被 MallCustomerInterceptor 判成非客户身份（30007）
    assert.doesNotMatch(api, /['"`]\/scm\/mall\//);
    assert.match(api, /\/scm\/mall-admin\/home\/draft/);
  });
}

// ---------------------------------------------------------------- 来源：action-column-centre-contract.test.mjs
{
  /**
   * 操作列的表格统一规范在全树成立的契约测试。
   *
   * 规则：操作列居中；普通场景 120～160px，少数复杂工作台可放宽到 180px。
   * 项目已先后用 `bb77b10e centre all table headers`、`ffa95370 centre the remaining action
   * columns`、`ff8d2ce1` 与 `ff710abe` 批量化过，但每一轮都只覆盖了当时看见的那批页面。
   *
   * 本文件按对象块判定，而不是按行匹配：`{title:'本次金额', align:'right'}, {title:'操作',
   * dataIndex:'action', align:'center'}` 里的 `align:'right'` 属于金额列，是合规的。
   * 按行 grep 会把后者误判成违规，因此这里用括号块截取。
   *
   * 钉住五件事：
   * 1. 凡声明 `dataIndex:'action'` / `key:'action'` 的列对象，必须显式 `align:'center'`。
   *    省略不行 —— antd 默认左对齐，表头中心线与按钮组中心线会对不上。
   * 2. 操作列宽度不得超过 180px（复杂工作台的放宽上限）。
   * 3. 本轮收窄过的页面不得回退为超宽操作列。
   * 4. `ScmActionMore` 的权限裁剪链路仍然存在（菜单项挂不上 `v-privilege`，必须由调用方算 hidden）。
   * 5. 不允许用 `overflow: hidden` + `white-space: nowrap` 把按钮藏起来假装收窄成功。
   */

  const scmRoot = fileURLToPath(new URL('../src/views/business/scm', import.meta.url));
  const srcRoot = fileURLToPath(new URL('../src', import.meta.url));
  const ACTION_MORE = fileURLToPath(
      new URL('../src/components/business/scm/scm-action-more/index.vue', import.meta.url),
  );
  const ACTION_ITEM = fileURLToPath(
      new URL('../src/components/business/scm/scm-action-more/action-item.ts', import.meta.url),
  );

  /** 复杂工作台的放宽上限。 */
  const MAX_ACTION_WIDTH = 180;

  /** 递归收集指定后缀的文件。 */
  function filesWith(dir, ext) {
      const out = [];
      for (const entry of readdirSync(dir)) {
          const full = path.join(dir, entry);
          if (statSync(full).isDirectory()) out.push(...filesWith(full, ext));
          else if (entry.endsWith(ext)) out.push(full);
      }
      return out;
  }

  /**
   * 取每个操作列的对象块。
   *
   * 判定键同时接受 `dataIndex: 'action'` 与 `key: 'action'` —— 前者是主流写法，
   * 后者见于 `customer-sku-visibility-list`。不含 `title:'操作'` 的 `operationType`
   * 列（日志表的「操作类型」）不会被命中，因为它既不是 action 也不叫操作列。
   */
  function actionColumnBlocks(source) {
      const blocks = [];
      const re = /(?:dataIndex|key):\s*'action'/g;
      let match;
      while ((match = re.exec(source)) !== null) {
          const start = source.lastIndexOf('{', match.index);
          const end = source.indexOf('}', match.index);
          if (start >= 0 && end >= 0) {
              blocks.push({
                  block: source.slice(start, end + 1),
                  line: source.slice(0, match.index).split('\n').length,
              });
          }
      }
      return blocks;
  }

  const files = filesWith(scmRoot, '.vue').map((full) => ({
      full,
      rel: path.relative(scmRoot, full).replace(/\\/g, '/'),
      source: readFileSync(full, 'utf8'),
  }));

  test('扫描基线有效：确实枚举到操作列', () => {
      const total = files.reduce((sum, f) => sum + actionColumnBlocks(f.source).length, 0);
      assert.ok(total >= 40, `只枚举到 ${total} 个操作列，扫描方式可能已失效`);
  });

  // ------------------------------------------------------------------
  // 1. 居中
  // ------------------------------------------------------------------

  test('操作列必须显式 align center', () => {
      const offenders = [];
      for (const {rel, source} of files) {
          for (const {block, line} of actionColumnBlocks(source)) {
              if (!/align:\s*'center'/.test(block)) {
                  offenders.push(`${rel}:${line} → ${block.replace(/\s+/g, ' ')}`);
              }
          }
      }
      assert.deepEqual(
          offenders,
          [],
          `以下操作列未显式居中（antd 默认左对齐，表头与按钮中心线会对不上）：\n${offenders.join('\n')}`,
      );
  });

  test('操作列不得右对齐', () => {
      const offenders = [];
      for (const {rel, source} of files) {
          for (const {block, line} of actionColumnBlocks(source)) {
              if (/align:\s*'right'/.test(block)) {
                  offenders.push(`${rel}:${line} → ${block.replace(/\s+/g, ' ')}`);
              }
          }
      }
      assert.deepEqual(offenders, [], `以下操作列仍右对齐：\n${offenders.join('\n')}`);
  });

  // ------------------------------------------------------------------
  // 2. 宽度上限
  // ------------------------------------------------------------------

  test(`操作列宽度不得超过 ${MAX_ACTION_WIDTH}px`, () => {
      const offenders = [];
      for (const {rel, source} of files) {
          for (const {block, line} of actionColumnBlocks(source)) {
              const width = block.match(/width:\s*(\d+)/);
              if (width && Number(width[1]) > MAX_ACTION_WIDTH) {
                  offenders.push(`${rel}:${line} width=${width[1]}`);
              }
          }
      }
      assert.deepEqual(
          offenders,
          [],
          `以下操作列超过 ${MAX_ACTION_WIDTH}px 常驻宽度：\n${offenders.join('\n')}`,
      );
  });

  // ------------------------------------------------------------------
  // 3. 本轮收窄的页面不得回退
  // ------------------------------------------------------------------

  /** 文件 → 该文件内任何操作列都不允许超过的宽度。 */
  const NARROWED = {
      'inventory/inventory-loss-gain-list.vue': 150,
      'inventory/inventory-outbound-list.vue': 150,
      'inventory/inventory-stocktake-list.vue': 150,
      'inventory/inventory-transfer-list.vue': 180,
      'inventory/inventory-conversion-list.vue': 160,
      'sorting/sorting-task-list.vue': 150,
      'promotion/promotion-coupon-list.vue': 150,
      'product/components/category-tree-table.vue': 160,
      'report/report-overview-list.vue': 110,
  };

  test('已收窄的操作列不得回退', () => {
      for (const [rel, limit] of Object.entries(NARROWED)) {
          const file = files.find((f) => f.rel === rel);
          assert.ok(file, `文件不存在：${rel}`);
          const blocks = actionColumnBlocks(file.source);
          assert.ok(blocks.length > 0, `${rel} 未声明操作列，疑似结构被改`);
          for (const {block, line} of blocks) {
              const width = block.match(/width:\s*(\d+)/);
              assert.ok(width, `${rel}:${line} 操作列缺少显式宽度`);
              assert.ok(
                  Number(width[1]) <= limit,
                  `${rel}:${line} 操作列回退为 ${width[1]}px（上限 ${limit}px）`,
              );
          }
      }
  });

  test('已修复的子组件不得回退', () => {
      const fixed = [
          'delivery/components/route-fulfillment-panel.vue',
          'delivery/components/route-orders-panel.vue',
          'delivery/components/route-planning-suggestion-panel.vue',
          'finance/finance-refund-picker.vue',
          'finance/finance-write-off-list.vue',
          'order/components/order-item-editable-table.vue',
          'purchase/components/purchase-order-item-editable-table.vue',
      ];
      for (const rel of fixed) {
          const file = files.find((f) => f.rel === rel);
          assert.ok(file, `文件不存在：${rel}`);
          const blocks = actionColumnBlocks(file.source);
          assert.ok(blocks.length > 0, `${rel} 未声明操作列，疑似结构被改`);
          for (const {block, line} of blocks) {
              assert.ok(!/align:\s*'right'/.test(block), `${rel}:${line} 操作列回退为右对齐`);
              assert.ok(/align:\s*'center'/.test(block), `${rel}:${line} 操作列丢了显式居中`);
          }
      }
  });

  // ------------------------------------------------------------------
  // 4. ScmActionMore 的权限链路
  // ------------------------------------------------------------------

  test('ScmActionMore 本身不依赖 v-privilege（菜单项里指令不生效）', () => {
      const source = readFileSync(ACTION_MORE, 'utf8');
      assert.doesNotMatch(
          source,
          /v-privilege/,
          'ScmActionMore 渲染在 a-menu 里，v-privilege 对菜单项不生效，权限必须由调用方算 hidden',
      );
      // 必须仍然按 hidden 过滤
      assert.match(source, /\.filter\(\s*\(?\s*action\s*\)?\s*=>\s*!action\.hidden\s*\)/,
          'ScmActionMore 不再按 hidden 过滤，无权限动作会被显示出来');
  });

  test('ScmActionItem 仍保留 hidden / danger 语义', () => {
      const source = readFileSync(ACTION_ITEM, 'utf8');
      assert.match(source, /hidden\?:/, 'action-item 丢了 hidden 字段');
      assert.match(source, /danger\?:/, 'action-item 丢了 danger 字段');
      assert.match(source, /label:/, 'action-item 丢了 label 字段');
  });

  /**
   * 只读跳转型调用方：菜单项不涉及写操作，因此没有权限需要裁剪。
   * 例外必须显式登记，避免"忘记裁剪"被伪装成"只读跳转"。
   */
  const READ_ONLY_MORE_CALLERS = new Set(['report/report-overview-list.vue']);

  test('每个 ScmActionMore 调用方都用权限助手裁剪菜单项', () => {
      const callers = files.filter((f) => /ScmActionMore/.test(f.source));
      assert.ok(callers.length >= 10, `只找到 ${callers.length} 个调用方，扫描可能失效`);
      const offenders = callers
          .filter((f) => !READ_ONLY_MORE_CALLERS.has(f.rel))
          .filter((f) => !/hasPermission\(|hasPerm\(/.test(f.source))
          .map((f) => f.rel);
      assert.deepEqual(
          offenders,
          [],
          `以下调用方没有用 hasPermission / hasPerm 裁剪「更多」里的菜单项：\n${offenders.join('\n')}`,
      );
  });

  // ------------------------------------------------------------------
  // 5. 平级入口不得人为分主次
  // ------------------------------------------------------------------

  test('平级的只读入口不人为分主次（报表总览下钻）', () => {
      const file = files.find((f) => f.rel === 'report/report-overview-list.vue');
      assert.ok(file, '文件不存在：report/report-overview-list.vue');
      // 四个维度完全平级，必须同住一个菜单，而不是挑两个提到行内
      for (const key of ['sales', 'purchase', 'receipt', 'inventory']) {
          assert.match(file.source, new RegExp(`\\{key: '${key}', label: '[^']+'\\}`),
              `下钻菜单缺 ${key}`);
      }
      assert.match(file.source, /<ScmActionMore label="下钻"/, '下钻应统一走 ScmActionMore');
      const cell = file.source.match(/column\.dataIndex === 'action'[\s\S]*?<\/template>/)?.[0];
      assert.ok(cell, '缺 action 单元格');
      assert.doesNotMatch(cell, /goAnalysis\(/,
          '行内不应再直接暴露单个下钻跳转，否则又变成了人为分主次');
  });

  // ------------------------------------------------------------------
  // 6. 不允许用 CSS 假装收窄
  // ------------------------------------------------------------------

  test('不得用 overflow hidden + nowrap 把操作按钮藏起来冒充收窄', () => {
      const styleFiles = [
          ...filesWith(srcRoot, '.vue'),
          ...filesWith(srcRoot, '.less'),
          ...filesWith(srcRoot, '.css'),
      ];
      const offenders = [];
      for (const file of styleFiles) {
          const source = readFileSync(file, 'utf8');
          for (const match of source.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
              const selector = match[1];
              const body = match[2];
              // 只关心操作容器本身，不关心 .scm-tags 之类同样用 nowrap+hidden 的正当场景
              if (!/scm-table-actions|smart-table-operate/.test(selector)) continue;
              if (/overflow:\s*hidden/.test(body) && /white-space:\s*nowrap/.test(body)) {
                  offenders.push(`${path.relative(srcRoot, file).replace(/\\/g, '/')} → ${selector.trim()}`);
              }
          }
      }
      assert.deepEqual(
          offenders,
          [],
          `以下样式把操作按钮裁掉而不是真正收窄：\n${offenders.join('\n')}`,
      );
  });
}

// ---------------------------------------------------------------- 来源：scm-drawer-width-contract.test.mjs
{
  /**
   * SCM Drawer 宽度分级的契约单测。
   *
   * ## 为什么需要这份契约
   *
   * 迁移前 `views/business/scm` 的 34 个 `<a-drawer>` 里只有 4 个走公共分级，其余硬编码，
   * 取值散成 `620 / 760 / 860 / 900 / 920 / 1000 / 1080 / 1120 / 1180 / 1200 / 1240 /
   * 1280 / 1500` 外加 `min(NNNpx, 96vw)` —— 同一类页面宽度不同，改一次要改十几处，
   * 而且「视口兜底」和「宽度分级」被混成了同一件事。
   *
   * 现在统一为五档，业务页面一律 `scmDrawerWidth(...)`：
   *
   *   s          600   简单配置 / 简单维护，以及单列只读详情
   *   m          780   中型主数据 / 常规编辑表单
   *   l          940   复杂业务编辑表单
   *   xl        1120   复杂详情 / 内嵌宽表 / 多明细业务
   *   workspace 1440   受限特殊档，见第 4 项断言
   *
   * 长期准入规则见 `CONTRIBUTING.md` §SCM 界面规范；
   * 当前 34 个实例及 workspace 白名单直接由本测试维护。
   *
   * ## 判定方式
   *
   * 只解析 `<a-drawer ...>` 开标签本身，不做全文件 grep —— 否则会误伤普通 CSS 里的
   * `width: 600px`、图表容器、输入框宽度等与抽屉无关的数值。
   */

  const scmRoot = fileURLToPath(new URL('../src/views/business/scm', import.meta.url));
  const DRAWER_TS = '../src/theme/scm/scm-drawer.ts';
  const RESPONSIVE_LESS = '../src/theme/scm/responsive.less';

  /** 迁移完成时的实例基线：低于它就说明扫描方式失效了（线路详情页面化后由 34 降为 33）。 */
  const EXPECTED_INSTANCES = 33;

  /** 只允许这五个等级。 */
  const ALLOWED_LEVELS = ['s', 'm', 'l', 'xl', 'workspace'];

  /**
   * `workspace` 的准入白名单 —— 与 `CONTRIBUTING.md` §SCM 界面规范一致。
   * 每一类都必须能在代码注释里说出「为什么横向空间本身属于业务内容」。
   */
  const WORKSPACE_FILES = new Set([
      'sorting/components/sorting-task-detail-drawer.vue',
      'purchase/components/purchase-demand-batch-detail-drawer.vue',
      'report/report-components/report-drilldown-drawer.vue',
      'sorting/components/sorting-scale-drawer.vue',
  ]);

  /** 递归收集 .vue 文件。 */
  function vueFiles(dir) {
      const out = [];
      for (const entry of readdirSync(dir, {withFileTypes: true})) {
          const full = path.join(dir, entry.name);
          if (entry.isDirectory()) out.push(...vueFiles(full));
          else if (entry.name.endsWith('.vue')) out.push(full);
      }
      return out;
  }

  /** 取每个 `<a-drawer` 的开标签文本（跨行，忽略引号内的 `>`）。 */
  function drawerTags(source) {
      const out = [];
      const re = /<a-drawer\b/g;
      let match;
      while ((match = re.exec(source)) !== null) {
          let i = match.index + match[0].length;
          let inStr = null;
          for (; i < source.length; i++) {
              const ch = source[i];
              if (inStr) {
                  if (ch === inStr) inStr = null;
                  continue;
              }
              if (ch === '"' || ch === "'") { inStr = ch; continue; }
              if (ch === '>') break;
          }
          out.push({
              text: source.slice(match.index, i + 1),
              line: source.slice(0, match.index).split('\n').length,
          });
      }
      return out;
  }

  const files = vueFiles(scmRoot).map((full) => ({
      rel: path.relative(scmRoot, full).replace(/\\/g, '/'),
      source: readFileSync(full, 'utf8'),
  }));

  const instances = [];
  for (const file of files) {
      for (const tag of drawerTags(file.source)) {
          const bound = tag.text.match(/:width="([^"]*)"/);
          const plain = tag.text.match(/(?:^|\s)width="([^"]*)"/);
          instances.push({
              rel: file.rel,
              line: tag.line,
              expr: (bound?.[1] ?? plain?.[1] ?? null),
              isBound: Boolean(bound),
          });
      }
  }

  // ------------------------------------------------------------------
  // 5. 扫描基线（先钉住，后面几条才有意义）
  // ------------------------------------------------------------------

  test('扫描基线：Drawer 实例数不低于迁移完成时的基线', () => {
      assert.ok(
          instances.length >= EXPECTED_INSTANCES,
          `只枚举到 ${instances.length} 个 <a-drawer>，低于基线 ${EXPECTED_INSTANCES}，扫描方式可能已失效`,
      );
  });

  // ------------------------------------------------------------------
  // 1 / 2 / 3. 必须走公共分级，不得硬编码
  // ------------------------------------------------------------------

  test('所有 a-drawer 都必须用 scmDrawerWidth 取值', () => {
      const offenders = instances
          .filter((i) => !i.isBound || !/^scmDrawerWidth\('(?:s|m|l|xl|workspace)'\)$/.test(i.expr.trim()))
          .map((i) => `${i.rel}:${i.line} width=${i.expr ?? '(未声明)'}`);
      assert.deepEqual(
          offenders,
          [],
          `以下抽屉没有通过 scmDrawerWidth 取宽度（禁止硬编码，也禁止 min(NNNpx, 96vw) ——\n` +
          `视口兜底由 theme/scm/responsive.less 统一负责）：\n${offenders.join('\n')}`,
      );
  });

  test('scmDrawerWidth 只接受 s / m / l / xl / workspace', () => {
      const offenders = [];
      for (const i of instances) {
          if (!i.isBound) continue;
          const level = i.expr.match(/scmDrawerWidth\('([^']*)'\)/)?.[1];
          if (level !== undefined && !ALLOWED_LEVELS.includes(level)) {
              offenders.push(`${i.rel}:${i.line} level=${level}`);
          }
      }
      assert.deepEqual(offenders, [], `出现了未定义的等级：\n${offenders.join('\n')}`);
  });

  test('不得再出现 min(NNNpx, Nvw) 形式的抽屉宽度', () => {
      const offenders = instances
          .filter((i) => /min\(\s*\d+px/.test(i.expr ?? ''))
          .map((i) => `${i.rel}:${i.line} width=${i.expr}`);
      assert.deepEqual(offenders, [], `视口兜底应由 responsive.less 统一提供：\n${offenders.join('\n')}`);
  });

  // ------------------------------------------------------------------
  // 4. workspace 是受限档
  // ------------------------------------------------------------------

  test('workspace 只允许出现在已批准的 4 个文件里', () => {
      const offenders = instances
          .filter((i) => /scmDrawerWidth\('workspace'\)/.test(i.expr ?? ''))
          .filter((i) => !WORKSPACE_FILES.has(i.rel))
          .map((i) => `${i.rel}:${i.line}`);
      assert.deepEqual(
          offenders,
          [],
          `workspace 是受限特殊档，只允许分拣称重工作台 / 报表下钻 /\n` +
          `超宽业务数据阅读 / 多面板业务工作台四类（线路详情已页面化，不再占用本档）；\n` +
          `新增用例要先更新 SCM UI 规范并同步本契约：\n${offenders.join('\n')}`,
      );
  });

  test('已批准的 workspace 文件一个都不能少', () => {
      const used = new Set(
          instances
              .filter((i) => /scmDrawerWidth\('workspace'\)/.test(i.expr ?? ''))
              .map((i) => i.rel),
      );
      assert.deepEqual(
          [...WORKSPACE_FILES].filter((rel) => !used.has(rel)),
          [],
          'workspace 白名单里的文件丢失了 workspace 用法，疑似被误降级',
      );
  });

  test('每个 workspace 用例都写明了「为什么横向空间属于业务内容」', () => {
      for (const rel of WORKSPACE_FILES) {
          const file = files.find((f) => f.rel === rel);
          assert.ok(file, `文件不存在：${rel}`);
          assert.match(
              file.source,
              /<!--\s*workspace：/,
              `${rel} 用了 workspace 但没写明准入类别，后人无法判断它该不该在这里`,
          );
      }
  });

  // ------------------------------------------------------------------
  // 等级定义与响应式兜底
  // ------------------------------------------------------------------

  test('scm-drawer.ts 定义了五档且取值正确', () => {
      const source = readFileSync(new URL(DRAWER_TS, import.meta.url), 'utf8');
      const expected = {s: 600, m: 780, l: 940, xl: 1120, workspace: 1440};
      for (const [level, width] of Object.entries(expected)) {
          assert.match(
              source,
              new RegExp(`\\b${level}:\\s*${width}\\b`),
              `scm-drawer.ts 缺少 ${level} = ${width}`,
          );
      }
      // workspace 的受限语义必须写在文件里，而不是只存在于文档
      assert.match(source, /不是普通.{0,6}第五档|受限特殊档/, 'scm-drawer.ts 未说明 workspace 的受限语义');
      for (const category of ['地图工作台', '分拣 / 称重工作台', '报表下钻', '超宽业务数据阅读', '多面板业务工作台']) {
          assert.ok(source.includes(category), `scm-drawer.ts 未列出 workspace 准入类别：${category}`);
      }
  });

  test('responsive.less 的 96vw 兜底仍然存在', () => {
      const source = readFileSync(new URL(RESPONSIVE_LESS, import.meta.url), 'utf8');
      assert.match(source, /\.ant-drawer-content-wrapper\s*\{[\s\S]{0,120}max-width:\s*96vw/,
          '抽屉的视口兜底丢了，workspace 会在大屏之外的场景溢出');
      assert.match(source, /\.ant-modal\s*\{[\s\S]{0,80}max-width:\s*96vw/,
          '弹窗的视口兜底丢了');
  });
}

// ---------------------------------------------------------------- 来源：scm-export-column-selection.test.mjs
{
  const read = file => readFileSync(new URL(file, import.meta.url), 'utf8');
  const api = read('../src/api/business/scm/report-api.ts');
  const productApi = read('../src/api/business/scm/product-api.ts');
  const sales = read('../src/views/business/scm/report/report-sales-list.vue');
  const purchase = read('../src/views/business/scm/report/report-purchase-list.vue');
  const product = read('../src/views/business/scm/product/product-list.vue');

  const reportController = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/report/controller/ScmReportController.java');
  const productController = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/product/controller/ProductExcelController.java');

  test('导出只投影原权限查询，采购成本列仍受额外成本授权', () => {
    assert.match(reportController, /salesReportService\.orderList\(form\)/);
    assert.match(reportController, /purchaseReportService\.byProduct\(form\)/);
    assert.match(reportController, /StpUtil\.hasPermission\(ScmReportPermission\.COST_QUERY\)/);
    assert.match(productController, /productQueryService\.query\(form\)/);
  });

  test('三个试点的自选列均由服务端白名单下发，并与原列表筛选共用请求参数', () => {
    assert.match(api, /sales\/order\/export\/columns/);
    assert.match(api, /purchase\/product\/export\/columns/);
    assert.match(productApi, /product\/export\/columns/);
    assert.match(sales, /exportColumnKeys: orderExportKeys\.value\.length/);
    assert.match(purchase, /exportColumnKeys: purchaseProductExportKeys\.value\.length/);
    assert.match(product, /exportColumnKeys: exportColumnKeys\.value\.length/);
  });
}

// ---------------------------------------------------------------- 来源：scm-filter-layout-contract.test.mjs
{
  function source(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  const FILTER_STYLE = source('../src/theme/scm/filter.less');
  const SMART_ADMIN_STYLE = source('../src/theme/smart-admin.less');
  const PAGES = [
    source('../src/views/business/scm/finance/finance-payable-list.vue'),
    source('../src/views/business/scm/purchase/purchase-demand-list.vue'),
    source('../src/views/business/scm/inventory/inventory-warning-threshold-list.vue'),
  ];

  test('SCM filter fields wrap with aligned controls and separate actions', () => {
    assert.match(FILTER_STYLE, /\.scm-filter-bar\s*\{[\s\S]*?align-items:\s*center[\s\S]*?flex-wrap:\s*wrap[\s\S]*?gap:\s*8px 12px/);
    assert.match(FILTER_STYLE, /\.scm-filter-fields\s*\{[\s\S]*?display:\s*flex[\s\S]*?flex-wrap:\s*wrap/);
    assert.match(FILTER_STYLE, /\.scm-filter-actions\s*\{[\s\S]*?display:\s*flex[\s\S]*?gap:\s*8px[\s\S]*?margin-left:\s*auto/);
    assert.match(FILTER_STYLE, /\.scm-filter-range[\s\S]*?\.ant-picker\s*\{[\s\S]*?min-width:\s*0/);
    assert.match(FILTER_STYLE, /@media\s*\(max-width:\s*768px\)[\s\S]*?\.scm-filter-fields\s*\{[\s\S]*?flex:\s*1 1 100%/);
  });

  test('SCM toolbars use flex gaps while SmartAdmin keeps its separate query rules', () => {
    assert.match(FILTER_STYLE, /\.scm-table-toolbar\.smart-table-btn-block\s*\{[\s\S]*?flex-wrap:\s*wrap/);
    assert.match(FILTER_STYLE, /\.smart-table-setting-block\s*\{[\s\S]*?display:\s*flex[\s\S]*?gap:\s*8px[\s\S]*?float:\s*none[\s\S]*?margin-left:\s*auto/);
    assert.match(FILTER_STYLE, /\.smart-table-operate-block[\s\S]*?gap:\s*8px[\s\S]*?margin-right:\s*0/);
    assert.doesNotMatch(SMART_ADMIN_STYLE, /scm-filter-bar|scm-table-toolbar/);
    assert.match(SMART_ADMIN_STYLE, /\.smart-query-form\.ant-form-inline/);
  });

  test('payables, purchase demand and thresholds share the filter and toolbar layout', () => {
    for (const page of PAGES) {
      assert.match(page, /class="scm-filter-bar"/);
      assert.match(page, /class="scm-filter-fields"/);
      assert.match(page, /class="scm-filter-actions"/);
      assert.match(page, /class="smart-table-btn-block scm-table-toolbar"/);
      assert.doesNotMatch(page, /<a-button-group>/);
      assert.match(page, /查询/);
      assert.match(page, /重置/);
    }
  });
}

// ---------------------------------------------------------------- 来源：scm-list-layout-contract.test.mjs
{
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  const THRESHOLD = code('../src/views/business/scm/inventory/inventory-warning-threshold-list.vue');
  const COLUMN_MERGE = code('../src/components/support/table-operator/smart-table-column-merge.ts');
  const COLUMN_MODAL = code('../src/components/support/table-operator/smart-table-column-modal.vue');
  const VISIBILITY = code('../src/views/business/scm/customer/customer-sku-visibility-list.vue');
  const PRODUCT_CONST = code('../src/constants/business/scm/product-const.ts');
  const PRODUCT_LIST = code('../src/views/business/scm/product/product-list.vue');
  const SCM_COLUMN = code('../src/views/business/scm/common/scm-column.ts');
  const TABLE_THEME = code('../src/theme/scm/table.less');
  const PURCHASE_DEMAND = code('../src/views/business/scm/purchase/purchase-demand-list.vue');
  const CUSTOMER_LIST = code('../src/views/business/scm/customer/customer-list.vue');
  const INVENTORY_BALANCE = code('../src/views/business/scm/inventory/inventory-balance-list.vue');
  const ORDER_LIST = code('../src/views/business/scm/order/order-list.vue');
  const ROUTE_LIST = code('../src/views/business/scm/delivery/route-list.vue');
  const AGREEMENT_PRICE = code('../src/views/business/scm/pricing/agreement-price-list.vue');
  const CUSTOMER_TYPE_PRICE = code('../src/views/business/scm/pricing/customer-type-price-list.vue');
  const INVENTORY_CODE_LISTS = [
    code('../src/views/business/scm/inventory/inventory-warning-list.vue'),
    code('../src/views/business/scm/inventory/inventory-reservation-list.vue'),
    code('../src/views/business/scm/inventory/inventory-movement-list.vue'),
    code('../src/views/business/scm/inventory/inventory-outbound-list.vue'),
    code('../src/views/business/scm/inventory/inventory-stocktake-list.vue'),
    code('../src/views/business/scm/inventory/inventory-loss-gain-list.vue'),
    code('../src/views/business/scm/inventory/inventory-conversion-list.vue'),
  ];

  test('库存阈值将仓库、商品、规格和编码放入独立列', () => {
    for (const [title, dataIndex] of [
      ['仓库', 'warehouseName'],
      ['仓库编码', 'warehouseCode'],
      ['商品', 'productName'],
      ['商品规格', 'skuName'],
      ['规格编码', 'skuCode'],
    ]) {
      assert.match(THRESHOLD, new RegExp(`title: '${title}', dataIndex: '${dataIndex}'`));
    }
    assert.doesNotMatch(THRESHOLD, /class="scm-cell-stack/);
    assert.match(THRESHOLD, /dataIndex === 'warehouseCode'[\s\S]{0,120}class="scm-mono"/);
    assert.match(THRESHOLD, /dataIndex === 'skuCode'[\s\S]{0,120}class="scm-mono"/);
  });

  test('TableOperator respects default hidden columns and keeps new fields visible in older configurations', () => {
    const makeColumns = () => [
      {title: '仓库', dataIndex: 'warehouseName'},
      {title: '仓库编码', dataIndex: 'warehouseCode', showFlag: false},
      {title: '规格编码', dataIndex: 'skuCode'},
    ];
    const defaults = mergeColumn(makeColumns(), []);
    assert.deepEqual(defaults.newColumns.map((column) => column.showFlag), [true, false, true]);

    const olderConfig = mergeColumn(makeColumns(), [
      {columnKey: 'warehouseName', sort: 1, showFlag: true},
    ]);
    assert.deepEqual(olderConfig.newColumns.map((column) => column.showFlag), [true, false, true]);

    const userOptIn = mergeColumn(makeColumns(), [
      {columnKey: 'warehouseCode', sort: 2, showFlag: true},
    ]);
    assert.equal(userOptIn.newColumns.find((column) => column.dataIndex === 'warehouseCode')?.showFlag, true);
    assert.match(COLUMN_MERGE, /fontColumn\.showFlag = fontColumn\.showFlag \?\? true/);
  });

  test('scroll width follows visible resized columns and explicit utility columns', () => {
    const columns = [{width: 180}, {width: 120, showFlag: false}, {width: 144}];
    assert.equal(tableColumnsWidth(columns, 44), 368);
    columns[1].showFlag = true;
    columns[0].width = 220;
    assert.equal(tableColumnsWidth(columns, 44), 528);
    assert.equal(tableColumnsWidth([{width: '50%'}]), 0);
  });

  test('saved user widths persist until the user explicitly chooses recommended widths', () => {
    const merged = mergeColumn([{title: '金额', dataIndex: 'amount', width: 136}],
        [{columnKey: 'amount', sort: 1, showFlag: true, width: 180}]);
    assert.equal(merged.newColumns[0].width, 180);
    assert.match(COLUMN_MODAL, /使用系统推荐列宽/);
    assert.match(COLUMN_MODAL, /recommendedColumns\.map/);
  });

  test('商品表格铺满内容区，但只让仍在使用默认宽度的文本列吸收宽屏额外空间', () => {
    assert.match(SCM_COLUMN, /moneyRange:\s*156/);
    assert.match(SCM_COLUMN, /status:\s*88/);
    assert.match(PRODUCT_LIST, /ref="tableRef"/);
    assert.match(PRODUCT_LIST, /:columns="displayColumns"/);
    assert.match(PRODUCT_LIST, /table-layout="fixed"/);
    assert.match(PRODUCT_LIST, /useScmTableFill\(columns/);
    // 用户改过的列宽必须原样渲染：只有等于页面默认宽度的列才吸收剩余空间
    assert.match(SCM_COLUMN, /export function fillDefaultColumnWidths/);
    assert.match(SCM_COLUMN, /toPixelWidth\(column\.width\) === defaultWidths\[key\]/);
    // 可伸缩列都被改过时按比例放大，保证表格仍然铺满（不留右侧空白）
    assert.match(SCM_COLUMN, /const factor = scalableTotal > 0 \? target \/ scalableTotal : 1/);
    assert.match(SCM_COLUMN, /const scalable = columns\.filter/);
    assert.match(PRODUCT_LIST, /dataIndex: 'price', width: SCM_COLUMN_WIDTH.moneyRange/);
    assert.match(PRODUCT_LIST, /column.dataIndex === 'categoryPath'[\s\S]*?:title="record.categoryPath/);
    assert.doesNotMatch(PRODUCT_LIST, /class="scm-width-fit-table"/);
    assert.doesNotMatch(TABLE_THEME, /\.scm-width-fit-table/);
  });

  test('商品列表旧系统默认宽度随新版规范迁移，手工列宽仍保留', () => {
    const defaults = [
      {title: '市场价', dataIndex: 'price', width: 156},
      {title: '商品名称', dataIndex: 'name', width: 200},
    ];
    const saved = [
      {columnKey: 'price', sort: 1, showFlag: true, width: 184},
      {columnKey: 'name', sort: 2, showFlag: true, width: 220},
    ];
    const migrated = mergeColumn(structuredClone(defaults), saved, 50001);
    assert.deepEqual(migrated.newColumns.map((column) => column.width), [156, 200]);
    const custom = mergeColumn(structuredClone(defaults), [
      {columnKey: 'price', sort: 1, showFlag: true, width: 173},
    ], 50001);
    assert.equal(custom.newColumns[0].width, 173, '明确自定义的非历史默认宽度应保留');
    const otherTable = mergeColumn(structuredClone(defaults), saved, 50005);
    assert.equal(otherTable.newColumns[0].width, 184, '不应将商品历史规则应用到其它表格');
  });

  test('customer visibility uses Chinese product status labels and keeps unknown values diagnosable', () => {
    assert.match(VISIBILITY, /<ScmStatusTag[\s\S]{0,180}shelfStatusLabel\(record\.spuStatus\)/);
    assert.match(VISIBILITY, /<ScmStatusTag[\s\S]{0,180}shelfStatusLabel\(record\.skuStatus\)/);
    assert.match(VISIBILITY, /value === 'ON_SHELF'[\s\S]{0,100}\? 'success'[\s\S]{0,100}: 'warning'/);
    assert.doesNotMatch(VISIBILITY, /<a-tag>\{\{\s*record\.(spuStatus|skuStatus)/);
    assert.match(PRODUCT_CONST, /return '未知状态'/);
    assert.match(PRODUCT_CONST, /console\.warn\([\s\S]{0,100}\$\{value\}/);
  });

  test('purchase demand and threshold pages keep rules at the action and validation point', () => {
    assert.match(PURCHASE_DEMAND, /冻结批次生成需求/);
    assert.doesNotMatch(PURCHASE_DEMAND, /按仓库\s*×\s*商品规格\s*×\s*单位冻结批次后生成需求/);
    assert.doesNotMatch(THRESHOLD, /只有配置了阈值的仓库/);
    assert.match(THRESHOLD, /validator: validateAtLeastOneThreshold/);
    assert.match(THRESHOLD, /throw new Error\('预警下限和上限至少填写一个'\)/);
  });

  test('customer lists expose split code columns through TableOperator', () => {
    for (const [page, titles] of [
      [CUSTOMER_LIST, ['客户编码']],
      [VISIBILITY, ['客户编码', '规格编码']],
    ]) {
      for (const title of titles) {
        assert.match(page, new RegExp(`title: '${title}', dataIndex:`));
      }
    }
    assert.match(CUSTOMER_LIST, /title: '客户编码',[\s\S]{0,100}showFlag: false/);
    assert.match(VISIBILITY, /title: '客户编码',[\s\S]{0,100}showFlag: false/);
    assert.match(VISIBILITY, /title: '规格编码',[\s\S]{0,100}showFlag: false/);
    assert.match(VISIBILITY, /<TableOperator[\s\S]{0,220}SCM_CUSTOMER_SKU_VISIBILITY/);
    assert.doesNotMatch(CUSTOMER_LIST, /class="scm-cell-stack/);
    assert.doesNotMatch(VISIBILITY, /class="scm-cell-stack/);
  });

  test('inventory, order and pricing lists keep identity codes in separate optional columns', () => {
    for (const [page, fields] of [
      [INVENTORY_BALANCE, [['仓库编码', 'warehouseCode'], ['规格编码', 'skuCode']]],
      [ORDER_LIST, [['客户编码', 'customerCodeSnapshot']]],
      [AGREEMENT_PRICE, [['客户编码', 'customerCode'], ['规格编码', 'skuCode']]],
      [CUSTOMER_TYPE_PRICE, [['客户类型编码', 'customerTypeCode'], ['规格编码', 'skuCode']]],
    ]) {
      for (const [title, dataIndex] of fields) {
        assert.match(page, new RegExp(`title: '${title}', dataIndex: '${dataIndex}'`));
        assert.match(page, new RegExp(`dataIndex: '${dataIndex}'[\\s\\S]{0,100}showFlag: false`));
      }
      assert.doesNotMatch(page, /class="scm-cell-stack/);
      assert.match(page, /TableOperator/);
    }
  });

  test('delivery routes expose route number beside its own column', () => {
    assert.match(ROUTE_LIST, /title: '线路名称', dataIndex: 'routeName'/);
    assert.match(ROUTE_LIST, /title: '线路编号', dataIndex: 'routeNo'/);
    assert.doesNotMatch(ROUTE_LIST, /class="scm-cell-stack/);
    assert.match(ROUTE_LIST, /:scroll="\{ x: tableScrollX \}"/);
  });

  test('inventory lists expose warehouse and spec codes separately with dynamic TableOperator widths', () => {
    for (const page of INVENTORY_CODE_LISTS) {
      assert.match(page, /dataIndex: 'warehouseCode'[\s\S]{0,100}showFlag: false/);
      assert.match(page, /dataIndex: 'warehouseName'/);
      assert.match(page, /dataIndex === 'warehouseCode'[\s\S]{0,120}class="scm-mono"/);
      assert.match(page, /const scrollX = computed\(/);
      assert.match(page, /:scroll="\{ x: scrollX \}"/);
      assert.match(page, /TableOperator/);
    }
    for (const page of INVENTORY_CODE_LISTS.slice(0, 3)) {
      assert.match(page, /dataIndex: 'skuCode'[\s\S]{0,100}showFlag: false/);
      assert.match(page, /dataIndex === 'skuCode'[\s\S]{0,120}class="scm-mono"/);
      assert.match(page, /dataIndex: 'skuName'/);
    }
  });
}

// ---------------------------------------------------------------- 来源：scm-map-driving-model.test.mjs
{
  /**
   * 配送道路算路的切段与汇总契约。
   *
   * 高德驾车规划单次最多 16 个途经点，超过上限必须切段请求；段与段共享端点，
   * 距离与耗时按段累加。切段错了不会报错：只会少算一段路程或让折线断开，
   * 页面上仍是一条「有路线」的样子。因此这里用替身代替真实服务，钉住切段、
   * 汇总与失败回退，避免这类错误只能靠人工比对地图发现。
   *
   * 只测纯逻辑与替身交互：真实 Key、域名白名单与配额属于部署侧，不在单测范围。
   */

  const point = (index) => [114 + index * 0.01, 22.5 + index * 0.01];

  let calls = [];
  let failAt = null;
  let failStatus = 'error';

  /** 每次请求固定返回 1 km / 60 秒与一条三点折线，便于逐段核对汇总值；形状照 JSAPI 2.0 的 routes[]。 */
  class StubDriving {
    search(origin, destination, options, callback) {
      calls.push({origin, destination, waypoints: options.waypoints ?? []});
      if (failAt !== null && calls.length === failAt) {
        callback(failStatus, {info: failStatus.toUpperCase()});
        return;
      }
      callback('complete', {
        routes: [
          {
            distance: 1000,
            time: 60,
            steps: [{path: [origin, [origin[0] + 0.001, origin[1]], destination]}],
          },
        ],
      });
    }
  }

  const sdk = {Driving: StubDriving};
  const positions = (count) => Array.from({length: count}, (unused, index) => point(index));

  test.beforeEach(() => {
    calls = [];
    failAt = null;
    failStatus = 'error';
  });

  test('途经点超限时按共享端点切段', () => {
    const batches = splitDrivingBatches(positions(41));
    assert.deepEqual(batches.map((batch) => batch.length), [18, 18, 7]);
    // 上一段终点就是下一段起点，各段折线因此首尾相接。
    assert.deepEqual(batches[1][0], batches[0][17]);
    assert.deepEqual(batches[2][0], batches[1][17]);
  });

  test('不超过上限只请求一次，途经点顺序与输入一致', async () => {
    const input = positions(18);
    const result = await planRoadRoute(sdk, input);
    assert.equal(calls.length, 1);
    assert.equal(calls[0].waypoints.length, 16);
    assert.deepEqual(calls[0].origin, input[0]);
    assert.deepEqual(calls[0].destination, input[17]);
    assert.deepEqual(calls[0].waypoints, input.slice(1, -1));
    assert.equal(result.segmentCount, 1);
    assert.equal(result.provider, 'AMAP');
    assert.equal(result.fallback, false);
  });

  test('分段请求的距离与耗时按段累计，折线按序合并', async () => {
    const result = await planRoadRoute(sdk, positions(41));
    assert.equal(calls.length, 3);
    assert.equal(result.segmentCount, 3);
    assert.equal(result.distanceMeters, 3000);
    assert.equal(result.durationSeconds, 180);
    assert.equal(result.paths.length, 3);
    assert.deepEqual(result.paths[1][0], result.paths[0][2]);
  });

  test('任一段请求失败即整体回退，不返回部分道路折线', async () => {
    failAt = 2;
    const result = await planRoadRoute(sdk, positions(41));
    assert.equal(calls.length, 2);
    assert.equal(result.fallback, true);
    assert.deepEqual(result.paths, []);
    assert.equal(result.distanceMeters, 0);
    assert.equal(result.durationSeconds, 0);
  });

  test('无结果同样回退，且不抛出异常', async () => {
    failStatus = 'no_data';
    failAt = 1;
    const result = await planRoadRoute(sdk, positions(5));
    assert.equal(result.fallback, true);
    assert.deepEqual(result.paths, []);
  });

  test('少于两个点没有可算路段，直接回退', async () => {
    const result = await planRoadRoute(sdk, [point(0)]);
    assert.equal(result.fallback, true);
    assert.equal(calls.length, 0);
  });
}

// ---------------------------------------------------------------- 来源：scm-numeric-presentation-contract.test.mjs
{
  /**
   * SCM 数值与编号视觉口径的契约单测（Sprint F-B）。
   *
   * ## 为什么需要这份契约
   *
   * 项目里曾有 19 个页面各自定义 `.num { font-family: ui-monospace, … }`，
   * 共 55 处使用点。这些局部类把三种不同语义压成同一种视觉：
   *
   * - 金额（要纵向比大小）
   * - 数量（要纵向比大小）
   * - 单号 / 编码（要逐位抄录比对）
   *
   * 结果是口径无法统一演进：改一处不影响其余 18 处，且 `theme/scm` 里根本没有对应的公共类。
   *
   * Sprint F-B 把这 55 处按字段语义迁到公共类，并删掉全部局部定义。本文件钉住这个结果，
   * 防止「随手再写一个 .num」把口径重新打散。
   *
   * ## 口径（CONTRIBUTING.md §SCM 界面规范）
   *
   * - `.scm-money`    金额 / 单价 / 成本 —— 比例字体 + tabular-nums
   * - `.scm-quantity` 数量 / 百分比 / 统计数字 —— 比例字体 + tabular-nums
   * - `.scm-mono`     单号 / 编码 / 外部流水号 —— 等宽字体 + tabular-nums
   *
   * 注意 `.scm-mono` 本轮只建立公共能力，标识字段的全项目接入是独立审计，不在本契约断言范围内。
   */

  const SRC = fileURLToPath(new URL('../src/', import.meta.url));
  const SCM_VIEWS = `${SRC}views/business/scm/`;
  const TABLE_LESS = '../src/theme/scm/table.less';
  const THEME_INDEX = '../src/theme/scm/index.less';

  /** 读源码并剥掉注释 —— 注释里提到反例名字是合规的，不能算违规。 */
  function stripComments(source) {
    return source
        .replace(/<!--[\s\S]*?-->/g, '')
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/^\s*\/\/.*$/gm, '');
  }

  /** 递归收集目录下的文件，可按扩展名过滤。 */
  function collect(dir, pattern) {
    const out = [];
    for (const entry of readdirSync(dir, {withFileTypes: true})) {
      const path = `${dir}${entry.name}`;
      if (entry.isDirectory()) out.push(...collect(`${path}/`, pattern));
      else if (pattern.test(entry.name)) out.push(path);
    }
    return out;
  }

  const SCM_VUE = collect(SCM_VIEWS, /\.vue$/);
  const ALL_SRC_VUE = collect(SRC, /\.vue$/);

  /** 局部 `.num` 样式块定义：行首的 `.num {`。 */
  const LOCAL_NUM_DEFINITION = /^\s*\.num\s*\{/m;
  /** 局部 `.num` 使用：模板里的 class 绑定（含 `class="num"` / `:class="'num'"`）。 */
  const LOCAL_NUM_USAGE = /["'`]num["'`]/;

  test('扫描基线有效：SCM 视图与主题文件都能被枚举到', () => {
    assert.ok(SCM_VUE.length > 100, `只枚举到 ${SCM_VUE.length} 个 SCM 视图，扫描方式可能已失效`);
    assert.ok(ALL_SRC_VUE.length > SCM_VUE.length, '全量视图数应大于 SCM 视图数');
  });

  // ------------------------------------------------------------------
  // 1 / 4. 不得再定义局部 .num
  // ------------------------------------------------------------------

  test('views/business/scm 下不再定义 .num', () => {
    const offenders = SCM_VUE.filter((file) =>
        LOCAL_NUM_DEFINITION.test(stripComments(readFileSync(file, 'utf8'))));
    assert.deepEqual(
        offenders.map((file) => file.slice(SCM_VIEWS.length)),
        [],
        'SCM 页面不得再自定义 .num；请改用 .scm-money / .scm-quantity / .scm-mono',
    );
  });

  test('全仓库业务页面不得重新声明 .num（防止口径再次打散）', () => {
    const offenders = ALL_SRC_VUE.filter((file) =>
        LOCAL_NUM_DEFINITION.test(stripComments(readFileSync(file, 'utf8'))));
    assert.deepEqual(
        offenders.map((file) => file.slice(SRC.length)),
        [],
        '任何页面都不得重新声明 .num；三种数值语义必须走 theme/scm 的公共类',
    );
  });

  // ------------------------------------------------------------------
  // 2. 不得再使用局部 .num
  // ------------------------------------------------------------------

  test('views/business/scm 下不再使用 class="num"', () => {
    const offenders = [];
    for (const file of SCM_VUE) {
      if (LOCAL_NUM_USAGE.test(stripComments(readFileSync(file, 'utf8')))) {
        offenders.push(file.slice(SCM_VIEWS.length));
      }
    }
    assert.deepEqual(offenders, [], '以下文件仍在使用局部 .num class');
  });

  // ------------------------------------------------------------------
  // 3. 公共数值样式必须存在且被引入
  // ------------------------------------------------------------------

  test('scm-money / scm-quantity / scm-mono 三个公共样式都存在', () => {
    const table = readFileSync(new URL(TABLE_LESS, import.meta.url), 'utf8');

    // 规则必须真的被引进来 —— 只建文件不 import 是最容易漏的一步
    const index = readFileSync(new URL(THEME_INDEX, import.meta.url), 'utf8');
    assert.match(index, /@import '\.\/table\.less';/, 'theme/scm/index.less 未引入 table.less');

    // 金额与数量：比例字体 + tabular-nums（共用一条规则，选择器成组出现）
    assert.match(table, /\.scm-money,\s*\n\.scm-quantity\s*\{[\s\S]{0,120}font-variant-numeric: tabular-nums/,
        '.scm-money / .scm-quantity 未共用 tabular-nums 规则');

    // 等宽类：必须是等宽字体，而不是只把数字拉齐 —— 这是它区别于 scm-quantity 的全部意义
    assert.match(table, /\.scm-mono\s*\{[\s\S]{0,240}font-family:[\s\S]{0,160}ui-monospace/,
        '.scm-mono 缺少 ui-monospace 等宽字族');
    assert.match(table, /\.scm-mono\s*\{[\s\S]{0,320}font-variant-numeric: tabular-nums/,
        '.scm-mono 缺少 tabular-nums');
  });

  test('迁移结果仍然存在：SCM 视图里确实在用公共数值类', () => {
    // 反向护栏：如果上面的「不得使用 .num」是因为扫描坏了才通过，这里会把它抓出来
    let money = 0;
    let quantity = 0;
    for (const file of SCM_VUE) {
      const body = readFileSync(file, 'utf8');
      money += [...body.matchAll(/class="scm-money"/g)].length;
      quantity += [...body.matchAll(/class="scm-quantity"/g)].length;
    }
    assert.ok(money >= 12, `只找到 ${money} 处 .scm-money，少于 F-B 迁移的 12 处`);
    assert.ok(quantity >= 43, `只找到 ${quantity} 处 .scm-quantity，少于 F-B 迁移的 43 处`);
  });

  /*
   * 刻意不断言「src 下只有 .scm-mono 用等宽字体」。
   *
   * 除 `.num` 外，项目里还有若干与本次口径无关的等宽样式：
   * `.scm-diff-key` / `.scm-diff-row-title`（变更对比的字段名）、`.log-time`（日志时间）、
   * `.report-kpi-value`（KPI 大字）。它们不是 `.num`，语义也未纳入 C1 的三类，
   * 属后续「标识类字段全项目接入审计」的输入，不在本次数值口径统一的断言范围内。
   */
}

// ---------------------------------------------------------------- 来源：scm-query-form-submit.test.mjs
{
  /**
   * SCM 列表页查询按钮可达性静态门禁。
   *
   * 起因：真实浏览器验收点「查询」后一个请求都没发出去。根因是 ant-design-vue 的
   * `Form.handleSubmit` 只在 `props.model` 存在时才校验并 emit `finish`
   * （见 node_modules/ant-design-vue/es/form/Form.js），所以
   * `<a-form @finish="search">` + `<a-button html-type="submit">` 在无 `:model` 时是静默死按钮：
   * 页面照常渲染、照常首屏查询，只有点按钮没反应，单测里 `search()` 的实现再怎么对也没用。
   *
   * 本门禁只钉两件事：
   * 1. 业务页面不得再用 `html-type="submit"`（查询按钮沿用 SmartAdmin 原生 `@click` 范式）；
   * 2. 任何带 `@finish` 的 `a-form` 必须同时带 `:model`，否则 finish 分支永不触发。
   */

  const ROOT = fileURLToPath(new URL('../src/views/business/scm', import.meta.url));

  function vueFiles(dir) {
    const out = [];
    for (const entry of readdirSync(dir, {withFileTypes: true})) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        out.push(...vueFiles(full));
      } else if (entry.name.endsWith('.vue')) {
        out.push(full);
      }
    }
    return out;
  }

  /** 剥掉注释：模板里的说明文字提到被禁的写法不算违规。 */
  function strip(source) {
    return source
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  const files = vueFiles(ROOT).map((file) => [path.relative(ROOT, file).replace(/\\/g, '/'), strip(readFileSync(file, 'utf8'))]);

  test('业务页面不使用裸 submit 按钮', () => {
    const offenders = files.filter(([, code]) => /html-type="submit"/.test(code)).map(([name]) => name);
    assert.deepEqual(offenders, [], 'a-form 无 :model 时 submit 按钮不会触发任何查询');
  });

  test('带 @finish 的 a-form 必须同时声明 :model', () => {
    const offenders = [];
    for (const [name, code] of files) {
      for (const tag of code.match(/<a-form\b[^>]*>/g) ?? []) {
        if (/@finish=/.test(tag) && !/:model=/.test(tag)) offenders.push(name);
      }
    }
    assert.deepEqual([...new Set(offenders)], [], '无 model 的表单不会 emit finish，处理函数变成死代码');
  });
}

// ---------------------------------------------------------------- 来源：scm-table-alignment-contract.test.mjs
{
  const tableStyle = readFileSync(new URL('../src/theme/scm/table.less', import.meta.url), 'utf8');
  const supplierPage = readFileSync(new URL('../src/views/business/scm/supplier/supplier-list.vue', import.meta.url), 'utf8');
  const supplierEditableTable = readFileSync(new URL('../src/views/business/scm/supplier/components/supplier-sku-editable-table.vue', import.meta.url), 'utf8');
  const payablePage = readFileSync(new URL('../src/views/business/scm/finance/finance-payable-list.vue', import.meta.url), 'utf8');
  const srcRoot = fileURLToPath(new URL('../src', import.meta.url));

  function sourceFiles(directory) {
    const files = [];
    for (const entry of readdirSync(directory)) {
      const absolutePath = path.join(directory, entry);
      if (statSync(absolutePath).isDirectory()) files.push(...sourceFiles(absolutePath));
      else if (absolutePath.endsWith('.vue') || absolutePath.endsWith('.ts')) files.push(absolutePath);
    }
    return files;
  }

  function propertyName(node) {
    return node && (ts.isIdentifier(node) || ts.isStringLiteral(node) || ts.isNumericLiteral(node)) ? node.text : '';
  }

  function property(object, name) {
    return object.properties.find((item) => ts.isPropertyAssignment(item) && propertyName(item.name) === name);
  }

  function stringValue(node) {
    if (node && (ts.isAsExpression(node) || ts.isTypeAssertionExpression(node) || ts.isParenthesizedExpression(node))) {
      return stringValue(node.expression);
    }
    return node && (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) ? node.text : '';
  }

  function tokens(value) {
    return String(value)
      .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
      .replace(/([A-Z])([A-Z][a-z])/g, '$1 $2')
      .toLowerCase()
      .split(/[^a-z0-9]+/)
      .filter(Boolean);
  }

  const numericTokens = new Set([
    'amount', 'price', 'quantity', 'qty', 'count', 'rate', 'ratio', 'percent', 'percentage',
    'balance', 'stock', 'volume', 'weight', 'distance', 'mileage', 'profit', 'discount',
    'creditlimit', 'lowerlimit', 'upperlimit', 'minimum', 'maximum',
  ]);
  const numericTitle = /金额|总额|单价|售价|进价|市场价|数量|进度|(?:商品|规格|订单|关联|浏览|访问|登录失败|生成|行|单据|文档|来源订单|来源行|停靠点|异常|记录)数|库存|销量|折扣率|税率|费率|百分比|占比|比例|额度|余额|核销金额|未核销|已核销|超额核销|分配量|未分配|已分配|实收|上限|下限|载重|容积|距离|里程|耗时|时长|重量|体积|利润|成本|佣金|毛利|合计|入库量|出库量|交付量|登录失败次数|生成数量/;
  const statusTitle = /状态|方向|策略|结清|启用|停用|是否|类型|级别|等级|默认来源|计算状态|履约状态|打印状态|发布状态|锁定状态|方式/;
  const textTitle = /名称|编码|编号|单号|标识|联系人|电话|手机号|供应商|客户|商品|规格|仓库|地址|备注|描述|说明|摘要|时间|日期|时点|来源|内容|标题|设备|用户|部门|角色|菜单|批次|流水号|操作人|IP/;
  const imageTitle = /主图|缩略图|头像|图片|图标|二维码/;
  const shortSourceKeys = new Set(['sourcetype', 'ordersource', 'pricesource', 'lockedpricesource']);

  /**
   * 全站列对齐口径（2026-10-18 起）：只有枚举、分类、操作列居中，其余一律左对齐。
   *
   * 金额、数量、日期等不再右对齐 —— 用户明确要求「除了枚举/分类/操作，其他都左对齐」，
   * 右对齐会让数字贴到单元格右边缘，与左侧标题轴线错位。
   */
  const CENTER_KEYS = new Set([
    'direction', 'settleMode', 'receiptMode', 'visibilityPolicy', 'storageMethod',
    'effectiveness', 'result', 'method', 'state', 'level', 'documentTypeLabel',
    'taxExempt', 'orderSource', 'bizType', 'documentType', 'sourceType',
  ]);

  function expectedAlignment(title, dataIndex) {
    const key = String(dataIndex || '');
    const lowerKey = key.toLowerCase();
    if (/^(action|actions|operate)$/.test(lowerKey)) return 'center';
    if (CENTER_KEYS.has(key)) return 'center';
    // 枚举：*Status / *Type / *Flag；分类：category*
    if (/(status|state|type|flag)$/.test(lowerKey)) return 'center';
    if (/categor/.test(lowerKey) && !/code$/.test(lowerKey)) return 'center';
    return 'left';
  }

  function alignmentViolations(file) {
    const source = readFileSync(file, 'utf8');
    const scriptBlocks = file.endsWith('.vue')
      ? [...source.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)].map((match) => match[1])
      : [source];
    const violations = [];
    for (const block of scriptBlocks) {
      const sourceFile = ts.createSourceFile(file, block, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
      function visit(node) {
        if (ts.isObjectLiteralExpression(node)) {
          const titleProperty = property(node, 'title');
          const dataIndexProperty = property(node, 'dataIndex');
          const keyProperty = property(node, 'key');
          const hasTableColumnProperty = ['width', 'align', 'sorter', 'fixed', 'customRender']
            .some((name) => property(node, name));
          const dataProperty = dataIndexProperty || (hasTableColumnProperty && keyProperty);
          if (titleProperty && dataProperty) {
            const title = stringValue(titleProperty.initializer);
            const dataIndex = stringValue(dataProperty.initializer);
            if (dataIndex) {
              const expected = expectedAlignment(title, dataIndex);
              const alignProperty = property(node, 'align');
              const actual = alignProperty ? stringValue(alignProperty.initializer) : 'left';
              if (expected && actual !== expected) {
                const line = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile)).line + 1;
                violations.push(`${path.relative(srcRoot, file).replace(/\\/g, '/')}:${line} ${title}[${dataIndex}] expected ${expected}, got ${actual || 'dynamic'}`);
              }
            }
          }
        }
        ts.forEachChild(node, visit);
      }
      visit(sourceFile);
    }
    return violations;
  }

  function column(source, dataIndex) {
    const escapedIndex = dataIndex.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const match = new RegExp(`\\{[^{}]*dataIndex:\\s*'${escapedIndex}'[^{}]*\\}`).exec(source);
    assert.ok(match, `缺少 dataIndex=${dataIndex} 的表格列`);
    return match[0];
  }

  test('全局表格样式不覆盖列水平对齐，并让数据单元垂直居中', () => {
    assert.doesNotMatch(tableStyle, /\.ant-table-thead\s*>\s*tr\s*>\s*th\s*\{[^}]*text-align:\s*center\s*!important/s);
    assert.match(tableStyle, /\.ant-table-tbody\s*>\s*tr\s*>\s*td\s*\{[^}]*vertical-align:\s*middle/s);
    assert.match(supplierEditableTable, /th\s*,\s*td\s*\{[^}]*vertical-align:\s*middle/s);
  });

  test('供应商与应付样板按字段语义设置同轴对齐', () => {
    // 数值列也左对齐：只有枚举、分类、操作列居中
    assert.doesNotMatch(column(supplierPage, 'name'), /align:/);
    assert.doesNotMatch(column(supplierPage, 'skuCount'), /align:/);
    assert.match(column(supplierPage, 'status'), /align:\s*'center'/);
    assert.match(column(supplierPage, 'action'), /align:\s*'center'/);

    assert.doesNotMatch(column(payablePage, 'payableNo'), /align:/);
    assert.doesNotMatch(column(payablePage, 'supplierName'), /align:/);
    assert.doesNotMatch(column(payablePage, 'amount'), /align:/);
    assert.doesNotMatch(column(payablePage, 'writtenOffAmount'), /align:/);
    assert.match(column(payablePage, 'settleState'), /align:\s*'center'/);
    assert.match(column(payablePage, 'action'), /align:\s*'center'/);
    assert.doesNotMatch(column(payablePage, 'redAmount'), /align:/);
  });

  test('可排序表头让标题与排序图标作为一组沿列对齐轴排列', () => {
    assert.match(tableStyle, /\.ant-table-column-sorters\s*\{\s*justify-content:\s*flex-start/s);
    assert.match(tableStyle, /\[style\*='text-align: right'\].*?\.ant-table-column-sorters\s*\{\s*justify-content:\s*flex-end/s);
    assert.match(tableStyle, /\[style\*='text-align: center'\].*?\.ant-table-column-sorters\s*\{\s*justify-content:\s*center/s);
    assert.match(tableStyle, /\.ant-table-column-title\s*\{\s*flex:\s*0 1 auto/s);
  });

  test('全站 Vue/TypeScript 表格列按字段语义对齐', () => {
    const violations = sourceFiles(srcRoot).flatMap(alignmentViolations);
    assert.deepEqual(violations, [], `表格列对齐不符合字段语义：\n${violations.join('\n')}`);
  });
}

// ---------------------------------------------------------------- 来源：ui-foundation-contract.test.mjs
{
  /**
   * SCM UI foundation 的契约单测（主题变量 + 全局容器对齐）。
   *
   * ## 主题变量
   *
   * 守的是一条曾经真实踩过、而且不报错的线：项目里出现过 30+ 处
   * `var(--ant-color-text-secondary)` 之类的写法，看起来像 antd 的主题变量，
   * 实际上 antd-vue 4.2.5 默认不开 cssVar，静态产物里根本搜不到 `--ant-color-*` 的定义，
   * 而 `src/` 里也没有任何地方定义过它们 —— 于是这些声明全部静默失效，
   * 颜色退化成继承值，页面照样能跑、构建照样通过、单测照样全绿。
   *
   * 项目真正的主题变量只有一套：`useScmThemeVars()` 在 `<html>` 上落下的 `--scm-*`。
   * 因此这里钉两件事：
   *
   * 1. 全仓库不得再出现 `--ant-color-*`；
   * 2. 用到的每一个 `--scm-*` 都必须在 `useScmThemeVars()` 里有定义（打错一个字母同样是静默失效）。
   *
   * ## Drawer footer 对齐
   *
   * antd 的 `.ant-drawer-footer` 只设了 padding / border-top / flexShrink，没有设对齐，
   * 而 `.ant-modal-footer` 自带 `textAlign: end` —— 同一套「取消 + 保存」在抽屉里贴左边、
   * 在弹窗里贴右边。这条也只能靠 foundation 统一，页面各自加类会漏。
   */

  const SRC = fileURLToPath(new URL('../src/', import.meta.url));
  const THEME_VARS = '../src/theme/scm/use-scm-theme-vars.ts';
  const THEME_INDEX = '../src/theme/scm/index.less';
  const THEME_FOOTER = '../src/theme/scm/footer.less';

  /** 读源码并剥掉注释 —— 注释里提到反例名字（例如本文件的头注释）是合规的。 */
  function stripComments(source) {
    return source
        .replace(/<!--[\s\S]*?-->/g, '')
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/^\s*\/\/.*$/gm, '');
  }

  /** 递归收集 `src/` 下的样式载体文件（.vue / .less / .css）。 */
  function styleFiles(dir) {
    const out = [];
    for (const entry of readdirSync(dir, {withFileTypes: true})) {
      const path = `${dir}${entry.name}`;
      if (entry.isDirectory()) out.push(...styleFiles(`${path}/`));
      else if (/\.(vue|less|css)$/.test(entry.name)) out.push(path);
    }
    return out;
  }

  const FILES = styleFiles(SRC);

  test('全仓库不再引用从未定义的 --ant-color-* 变量', () => {
    const offenders = FILES.filter((file) => stripComments(readFileSync(file, 'utf8')).includes('--ant-color-'));
    assert.deepEqual(
        offenders.map((file) => file.slice(SRC.length)),
        [],
        'antd-vue 4.2.5 不开 cssVar，--ant-color-* 在项目里从未定义；请改用 useScmThemeVars 的 --scm-*',
    );
  });

  test('用到的每一个 --scm-* 变量都在 useScmThemeVars 里有定义', () => {
    const themeVars = readFileSync(new URL(THEME_VARS, import.meta.url), 'utf8');
    const defined = new Set(
        [...themeVars.matchAll(/setProperty\('(--scm-[a-z-]+)'/g)].map((match) => match[1]),
    );
    assert.ok(defined.size >= 10, `只解析到 ${defined.size} 个变量定义，说明扫描方式失效`);

    const missing = new Map();
    for (const file of FILES) {
      const body = stripComments(readFileSync(file, 'utf8'));
      for (const match of body.matchAll(/var\((--scm-[a-z-]+)/g)) {
        if (!defined.has(match[1])) {
          if (!missing.has(match[1])) missing.set(match[1], []);
          missing.get(match[1]).push(file.slice(SRC.length));
        }
      }
    }
    assert.deepEqual(
        [...missing.keys()],
        [],
        `以下变量没有定义：\n${[...missing].map(([name, files]) => `  ${name} ← ${files[0]}`).join('\n')}`,
    );
  });

  // ------------------------------------------------------------------
  // Drawer footer 对齐
  // ------------------------------------------------------------------

  test('抽屉底部操作区统一右对齐，与弹窗口径一致', () => {
    const index = readFileSync(new URL(THEME_INDEX, import.meta.url), 'utf8');
    const footer = readFileSync(new URL(THEME_FOOTER, import.meta.url), 'utf8');

    // 规则必须真的被引进来 —— 只建文件不 import 是最容易漏的一步
    assert.match(index, /@import '\.\/footer\.less';/, 'theme/scm/index.less 未引入 footer.less');
    // antd 只设了 padding / border-top / flexShrink，没有设对齐，所以要自己补 flex + flex-end
    assert.match(footer, /\.ant-drawer-footer \{[\s\S]{0,200}display: flex;/);
    assert.match(footer, /\.ant-drawer-footer \{[\s\S]{0,200}justify-content: flex-end;/);
    // 刻意不动弹窗：它本来就右对齐，改它只会扩大影响面
    assert.doesNotMatch(footer, /\.ant-modal-footer \{/, '弹窗 footer 本来已右对齐，不要动它');
  });

  test('没有页面在抽屉 footer 上私自改回左对齐', () => {
    const offenders = [];
    for (const file of styleFiles(SRC)) {
      const body = stripComments(readFileSync(file, 'utf8'));
      if (/drawer-footer[\s\S]{0,200}justify-content: flex-start/.test(body)) offenders.push(file.slice(SRC.length));
    }
    assert.deepEqual(offenders, [], '以下文件把抽屉 footer 改回了左对齐');
  });

  // ------------------------------------------------------------------
  // 表格表头对齐
  // ------------------------------------------------------------------

  test('表头服从列语义，数据单元格垂直居中', () => {
    const table = readFileSync(new URL('../src/theme/scm/table.less', import.meta.url), 'utf8');
    assert.doesNotMatch(table, /\.ant-table-thead\s*>\s*tr\s*>\s*th\s*\{[^}]*text-align:\s*center\s*!important/s,
        '表头必须保留 column.align 的语义轴');
    assert.match(table, /\.ant-table-tbody\s*>\s*tr\s*>\s*td\s*\{[^}]*vertical-align:\s*middle/s,
        '所有表格数据单元格必须垂直居中');
    // 规则必须真的被引进来
    const index = readFileSync(new URL('../src/theme/scm/index.less', import.meta.url), 'utf8');
    assert.match(index, /@import '\.\/table\.less';/);
  });
}

// ---------------------------------------------------------------- 来源：ui-shell-contract.test.mjs
{
  /**
   * 应用外壳（侧边栏 / 导航顺序）契约单测。
   *
   * 守的是三条「改错了不会报错、只会难看或静默失效」的线：
   *
   * 1. 侧栏宽度有四个口径必须一致：默认值（`app-config.ts`）、设置控件允许的区间
   *    （`header-setting.vue`）、收起态栏宽（`side-layout.vue`）、收起态 logo 容器宽度
   *    （`side-menu/index.vue`）。默认值跑到区间外，用户一打开设置页就看不到自己当前的宽度；
   *    logo 容器与栏宽不等，收起后 logo 会偏。
   * 2. 收起态滚动条不该常驻：`overflow-y: scroll` 会无条件画一条轨道，
   *    侧栏没超出时也画 —— 收起后尤其突兀。
   * 3. 菜单图标配色必须排除暗色主题：用 `--scm-text`（深色）去染暗色菜单的图标
   *    会让它直接看不见，而这在亮色主题下完全测不出来。
   *
   * 第 4 条守的是导航顺序的唯一来源：一级菜单的先后完全由 `t_menu.sort` 决定，
   * 前端不再维护第二份排序名单；V110 只允许改 `sort`。
   */

  /** 读源码并剥掉注释（先剥 HTML 注释再剥块注释，顺序不能反）。 */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
        .replace(/<!--[\s\S]*?-->/g, '')
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/^\s*\/\/.*$/gm, '');
  }

  const APP_CONFIG = '../src/config/app-config.ts';
  const SETTING = '../src/layout/components/header-user-space/header-setting.vue';
  const SIDE_LAYOUT = '../src/layout/side-layout.vue';
  const SIDE_MENU = '../src/layout/components/side-menu/index.vue';
  const RECURSION_MENU = '../src/layout/components/side-menu/recursion-menu.vue';
  const GLOBAL_THEME = '../src/theme/index.less';
  const PAGE_TAGS = [
    '../src/layout/components/page-tag/components/default-tab.vue',
    '../src/layout/components/page-tag/components/chrome-tab.vue',
    '../src/layout/components/page-tag/components/antd-tab.vue',
  ];
  const LAYOUTS = [
    '../src/layout/side-layout.vue',
    '../src/layout/side-expand-layout.vue',
    '../src/layout/top-expand-layout.vue',
  ];
  const V110 = '../../xsy-scm-server/sa-admin/src/main/resources/db/migration/V110__scm_reorder_root_navigation.sql';

  const appConfig = code(APP_CONFIG);
  const setting = code(SETTING);
  const sideLayout = code(SIDE_LAYOUT);
  const sideMenu = code(SIDE_MENU);
  const recursionMenu = code(RECURSION_MENU);

  function num(source, pattern, label) {
    const match = source.match(pattern);
    assert.ok(match, `未解析到 ${label}，说明扫描方式失效`);
    return Number(match[1]);
  }

  test('侧栏宽度四处口径一致，默认值落在设置控件允许的区间内', () => {
    const defaultWidth = num(appConfig, /sideMenuWidth:\s*(\d+)/, '默认侧栏宽度');
    // 必须先切出侧栏宽度这一个控件：同一页里还有圆角滑块，直接匹配 :min/:max 会取到它
    const sideMenuInput = setting.match(/<a-input-number[\s\S]{0,300}formState\.sideMenuWidth[\s\S]{0,300}?\/>/)?.[0];
    assert.ok(sideMenuInput, '未取到侧栏宽度输入框');
    const minWidth = num(sideMenuInput, /:min="(\d+)"/, '设置项最小宽度');
    const maxWidth = num(sideMenuInput, /:max="(\d+)"/, '设置项最大宽度');
    const step = num(sideMenuInput, /:step="(\d+)"/, '设置项步长');
    const collapsedWidth = num(sideLayout, /COLLAPSED_MENU_WIDTH\s*=\s*(\d+)/, '收起态宽度');
    const logoBox = num(sideMenu, /\.min-logo\s*\{[\s\S]*?width:\s*(\d+)px/, '收起态 logo 容器宽度');

    // 默认值必须落在控件允许的区间内，否则打开设置页看到的是一个「不合法的当前值」
    assert.ok(minWidth <= defaultWidth && defaultWidth <= maxWidth,
        `默认宽度 ${defaultWidth} 不在设置控件允许的 [${minWidth}, ${maxWidth}] 内`);
    // 收起态必须比任何允许的展开宽度都窄，否则「收起」反而更宽
    assert.ok(collapsedWidth < minWidth, `收起宽度 ${collapsedWidth} 不应大于等于最小展开宽度 ${minWidth}`);
    // 收起态 logo 容器必须正好等于栏宽，否则 logo 不居中
    assert.equal(logoBox, collapsedWidth, '收起态 logo 容器宽度必须等于收起态栏宽');
    // 步长要能整除区间，否则拖到最后一位会跳
    assert.equal((maxWidth - minWidth) % step, 0, '区间长度应能被步长整除');
  });

  test('改默认侧栏宽度必须同时升级 configVersion', () => {
    const version = num(appConfig, /configVersion:\s*(\d+)/, '配置版本号');
    assert.ok(version >= 4, '侧栏默认宽度调整后，configVersion 必须升级到 4 或更高');
    assert.match(
        code('../src/store/modules/system/app-config.ts'),
        /cached\.configVersion === appDefaultConfig\.configVersion/,
        '配置迁移逻辑被改动，本断言的前提失效，请重新核对',
    );
    const configStore = code('../src/store/modules/system/app-config.ts');
    assert.match(configStore, /cached\.configVersion === 3[\s\S]{0,350}sideMenuWidth:\s*appDefaultConfig\.sideMenuWidth/,
        '版本 3 → 4 应迁移侧栏宽度并保留其他缓存偏好');
  });

  test('传统侧栏使用组件库的实心倒三角，右侧垂直居中，展开式侧栏仍隐藏自带折角', () => {
    const sideExpandMenu = code('../src/layout/components/side-expand-menu/recursion-menu.vue');
    // 使用组件库的实心 CaretDownFilled，折叠朝右、展开朝下，不能回退为描边 Chevron。
    assert.match(recursionMenu, /<template #expandIcon="\{ isOpen \}">/,
        '分组箭头应使用 expandIcon 插槽');
    assert.match(recursionMenu, /<CaretDownFilled class="smart-menu-caret"/,
        '必须使用 Ant Design Vue 的实心三角');
    assert.match(recursionMenu, /'smart-menu-caret-collapsed': !isOpen/,
        '展开向下，收起向右');
    assert.match(recursionMenu, /import \{CaretDownFilled\} from '@ant-design\/icons-vue';/,
        '三角图标必须从 Ant Design Vue 导入');
    assert.doesNotMatch(recursionMenu, /UpCircleFilled|DownCircleFilled/,
        '不再使用实心圆形箭头');
    assert.match(recursionMenu, /\.smart-menu-caret-frame[\s\S]{0,120}?top:\s*50%/,
        '三角容器必须定位到标题高度中线');
    assert.match(recursionMenu, /\.smart-menu-caret-frame[\s\S]{0,270}?translateY\(-50%\)/,
        '必须以自身高度向上补偿，不能偏到右下角');
    assert.match(recursionMenu, /\.smart-menu-caret-collapsed[\s\S]{0,60}?rotate\(-90deg\)/,
        '收起态实心三角朝右');
    assert.match(recursionMenu, /transition:\s*transform 0\.2s ease/,
        '箭头旋转动画为 0.2 秒');
    assert.match(recursionMenu, /color:\s*var\(--scm-text-secondary[^;]*\)/,
        '实心箭头保持次要文字色');
    assert.doesNotMatch(recursionMenu, /\.ant-menu-submenu-arrow/,
        '接管 expandIcon 之后不该再覆盖 antd 自带的折角');
    assert.match(sideExpandMenu, /:deep\(\.ant-menu-submenu-arrow\)\s*\{\s*display:\s*none;/,
        '展开式侧栏的子菜单折角仍应隐藏');
    assert.match(recursionMenu, /mode="inline"/, '传统侧栏必须保留内联子菜单');
    assert.match(sideExpandMenu, /mode="inline"/, '展开式侧栏必须保留内联子菜单');
    assert.match(recursionMenu, /\.smart-menu:not\(\.ant-menu-dark\)/,
        '菜单图标配色规则仍须排除暗色菜单');
  });

  test('受控菜单展开正常，Chevron 在亮暗主题下都保持中性灰', () => {
    assert.match(recursionMenu,
        /if \(!menuSingleExpandFlag\.value\) \{\s*openKeys\.value = \[\.\.\.openKeysParams\];/,
        '允许多组展开时也要同步 openKeys');
    assert.doesNotMatch(recursionMenu,
        /\.ant-menu-submenu-open > \.ant-menu-submenu-title \.ant-menu-submenu-expand-icon[\s\S]{0,140}color:\s*var\(--scm-primary/,
        '展开时箭头不应变为主题色');
    assert.match(recursionMenu,
        /\.smart-menu\.ant-menu-dark\s*\{[\s\S]{0,140}rgba\(255, 255, 255/,
        '暗色主题保持足够对比度');
  });

  test('侧栏滚动条不常驻：auto + 透明轨道 + hover 才显形', () => {
    assert.match(sideLayout, /\.side-menu\s*\{[\s\S]{0,200}overflow-y:\s*auto/, 'overflow-y 应为 auto');
    assert.doesNotMatch(sideLayout, /overflow-y:\s*scroll/, 'overflow-y: scroll 会无条件画滚动条');
    assert.match(sideLayout, /\.side-menu::-webkit-scrollbar-track\s*\{[\s\S]{0,120}background:\s*transparent/,
        '轨道应透明');
    assert.match(sideLayout, /\.side-menu:hover::-webkit-scrollbar-thumb\s*\{[\s\S]{0,120}background:\s*rgba\(0,\s*0,\s*0/,
        '滑块应只在 hover 侧栏时显形');

    // top-expand 布局渲染的是同一个 `.side-menu`，同一个缺陷要同一口径修
    // （两个布局互斥挂载，所以不存在运行时冲突）
    const topExpand = code('../src/layout/top-expand-layout.vue');
    assert.match(topExpand, /\.side-menu::-webkit-scrollbar-thumb\s*\{[\s\S]{0,120}background:\s*transparent/,
        'top-expand 布局的侧栏滑块也应默认透明');
    assert.match(topExpand, /\.side-menu:hover::-webkit-scrollbar-thumb\s*\{[\s\S]{0,120}background:\s*rgba\(0,\s*0,\s*0/,
        'top-expand 布局的侧栏滑块也应只在 hover 时显形');
  });

  test('菜单图标三档配色：普通深色 / hover 主题色 / 当前主题色，且排除暗色菜单', () => {
    // 三档都要显式写出来：antd 亮色菜单的 hover 只换背景不换字色，不写就没有 hover 变色
    assert.match(recursionMenu, /:deep\(\.ant-menu-item \.anticon\),[\s\S]{0,200}color:\s*var\(--scm-text/,
        '普通态图标应显式用 --scm-text');
    assert.match(recursionMenu, /:deep\(\.ant-menu-item:hover \.anticon\),[\s\S]{0,200}color:\s*var\(--scm-primary/,
        'hover 态图标应用 --scm-primary');
    assert.match(recursionMenu, /:deep\(\.ant-menu-item-selected \.anticon\),[\s\S]{0,200}color:\s*var\(--scm-primary/,
        '选中态图标应用 --scm-primary');
    // 暗色菜单必须排除：--scm-text 是深色，染上去图标会看不见
    assert.match(recursionMenu, /\.smart-menu:not\(\.ant-menu-dark\)/, '配色规则必须排除 .ant-menu-dark');
    // 不写死色值：主题色可切换
    const styleBlock = recursionMenu.slice(recursionMenu.indexOf('<style'));
    assert.doesNotMatch(styleBlock, /#333|#00b96b|#515a6e/, '菜单样式里不应出现写死的主题色');
  });

  test('全局键盘焦点清晰可见，导航控件可由键盘操作', () => {
    const globalTheme = code(GLOBAL_THEME);
    assert.doesNotMatch(globalTheme, /\*\s*\{[^}]*outline:\s*none\s*!important/);
    assert.match(globalTheme, /:focus-visible\s*\{[^}]*outline:\s*2px solid var\(--scm-primary/);
    for (const path of LAYOUTS) {
      const layout = code(path);
      assert.match(layout, /<button[\s\S]{0,100}class="collapsed-button"[\s\S]{0,180}aria-label=/);
      assert.match(layout, /<button class="home-button"[^>]*aria-label="首页"/);
    }
  });

  test('all page-tag styles keep labels ellipsized, scrollable and closable by keyboard', () => {
    for (const path of PAGE_TAGS) {
      const pageTag = code(path);
      assert.match(pageTag, /class="smart-page-tag-title"/);
      assert.match(pageTag, /min-width:\s*96px/);
      assert.match(pageTag, /max-width:\s*220px/);
      assert.match(pageTag, /text-overflow:\s*ellipsis/);
      assert.match(pageTag, /flex-wrap:\s*nowrap/);
      assert.match(pageTag, /:aria-label="`关闭\$\{item\.menuTitle\}`"/);
      assert.match(pageTag, /<button[\s\S]{0,180}class="smart-page-tag-close"/);
      assert.match(pageTag, /:deep\(\.ant-tabs-tab-active\)[\s\S]{0,120}color:\s*@color-primary/);
    }
  });

  test('V110 只调整一级菜单的 sort，不动层级 / 路径 / 权限 / 可见性', () => {
    // code() 只认 JS/Vue 的注释形态，SQL 的 `--` 要单独剥掉，
    // 否则迁移头注释里的 `parent_id = 0` 这类说明会被当成真的写库语句
    const sql = code(V110).replace(/--[^\n]*/g, '');
    // 只允许 UPDATE t_menu SET sort = N, update_time = now() WHERE menu_id = M
    const statements = sql.split(';').map((s) => s.trim()).filter(Boolean);
    assert.ok(statements.length >= 20, `只解析到 ${statements.length} 条语句，说明扫描方式失效`);
    for (const statement of statements) {
      assert.match(statement, /^UPDATE xsy_v2\.t_menu SET sort = \d+,\s+update_time = now\(\) WHERE menu_id = \d+$/,
          `V110 出现了非「只改 sort」的语句：\n${statement}`);
    }
    // 不得出现任何 DDL / 其它 DML
    assert.doesNotMatch(sql, /\b(INSERT|DELETE|ALTER|DROP|TRUNCATE|CREATE)\b/i, 'V110 不得改动表结构或增删行');
    // 不得改动这些字段 —— 它们是菜单层级、导航与权限的唯一来源
    for (const column of ['parent_id', 'path', 'component', 'web_perms', 'api_perms', 'visible_flag', 'deleted_flag']) {
      assert.doesNotMatch(sql, new RegExp(`${column}\\s*=`, 'i'), `V110 不得写 ${column}`);
    }
    // 业务区与平台管理区不得撞号（同值会让 ORDER BY sort 的相对顺序不确定）
    const sorts = [...sql.matchAll(/SET sort = (\d+)/g)].map((m) => Number(m[1]));
    assert.equal(new Set(sorts).size, sorts.length, 'V110 出现了重复的 sort 值');
  });
}
