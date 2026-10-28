/**
 * 系统与横切规则模块契约测试。
 *
 * 由 11 个独立测试文件按模块合并：每个 `{ … }` 块对应原来一份文件，
 * 块内 helper / 常量各自作用域隔离；只有 import 提到顶部并去重。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync, statSync} from 'node:fs';
import {areaColumnsOf, areaNodesOf} from '../src/views/business/scm/common/scm-area.ts';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {createRequire} from 'node:module';
import {INVENTORY_LOSS_GAIN_LIST_PATH, messageBusinessLink, MESSAGE_TYPE_INVENTORY_LOSS_GAIN} from '../src/lib/message-business-link.ts';
import {MESSAGE_TYPE_ENUM} from '../src/constants/business/message/message-const.ts';
import {deepLinkId, deepLinkFilters} from '../src/lib/query-deep-link.ts';
import {maskSensitive} from '../src/views/support/operate-log/operate-log-mask.ts';

// ---------------------------------------------------------------- 来源：admin-roadmap-contract.test.mjs
{
  const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

  test('ADM-02 return receipt command is exposed with idempotent API and dispositions', () => {
    const api = read('src/api/business/scm/order-return-api.ts');
    const page = read('src/views/business/scm/order/order-return-list.vue');
    assert.match(api, /\/scm\/order\/return\/receive/);
    assert.match(page, /RETURN_TO_STOCK/);
    assert.match(page, /DAMAGE/);
    assert.match(page, /warehouseId/);
  });

  test('ADM-03 settlement customer is explicit in customer forms and details', () => {
    const types = read('src/types/business/scm/customer.d.ts');
    const form = read('src/views/business/scm/customer/components/customer-form-drawer.vue');
    const detail = read('src/views/business/scm/customer/customer-detail.vue');
    assert.match(types, /settlementCustomerId/);
    assert.match(form, /统一结算方/);
    assert.match(detail, /settlementCustomerName/);
  });

  test('ADM-04 credit check contract is exposed', () => {
    const api = read('src/api/business/scm/order-api.ts');
    assert.match(api, /\/scm\/order\/credit-check\//);
    assert.match(api, /projectedExposure/);
    assert.match(api, /overdue/);
  });
}

// ---------------------------------------------------------------- 来源：scm-area.test.mjs
{
  /*
   * 省 / 市 / 区路径 ↔ 六列快照转换单测（地图 M0）
   *
   * 覆盖两条会静默写错数据的规则：
   * 1. 名称快照必须来自同一次选择的节点，且清空选择要把六列整体归 null，不能只清编码；
   * 2. 回填给 `a-cascader` 的必须是一条连续路径——缺市就停在省，不能拼出「省 + 空 + 区」。
   */

  const PATH = [
    {value: 330000, label: '浙江省'},
    {value: 330100, label: '杭州市'},
    {value: 330106, label: '西湖区'},
  ];

  test('完整三级选择拆成六列，编码与名称取自同一节点', () => {
    assert.deepEqual(areaColumnsOf(PATH), {
      provinceCode: 330000,
      provinceName: '浙江省',
      cityCode: 330100,
      cityName: '杭州市',
      districtCode: 330106,
      districtName: '西湖区',
    });
  });

  test('只选到省时市 / 区整组留空，不拿上一级的名称冒充', () => {
    const columns = areaColumnsOf([PATH[0]]);
    assert.equal(columns.provinceCode, 330000);
    assert.equal(columns.cityCode, null);
    assert.equal(columns.cityName, null);
    assert.equal(columns.districtCode, null);
    assert.equal(columns.districtName, null);
  });

  test('清空选择把六列显式置 null，不残留上一次的层级', () => {
    for (const columns of [areaColumnsOf([]), areaColumnsOf(null)]) {
      assert.deepEqual(Object.values(columns), [null, null, null, null, null, null]);
      assert.equal(Object.keys(columns).length, 6);
    }
  });

  test('回填只还原连续路径：省 + 空市 + 区停在省', () => {
    assert.deepEqual(areaNodesOf(areaColumnsOf(PATH)), PATH);
    assert.deepEqual(areaNodesOf({provinceCode: 330000, provinceName: '浙江省', districtCode: 330106, districtName: '西湖区'}), [PATH[0]]);
    assert.deepEqual(areaNodesOf(null), []);
    // 名称快照缺失时该级不可信，从该级截断（大屏按 city_code 聚合，与是否回填无关）。
    assert.deepEqual(areaNodesOf({provinceCode: 330000, provinceName: '浙江省', cityCode: 330100}), [PATH[0]]);
  });
}

// ---------------------------------------------------------------- 来源：scm-comment-noise-contract.test.mjs
{
  /**
   * SCM 生产源码注释噪声契约。
   *
   * 只约束开发过程标记（来源 / 复制日期 / 波次 / 阶段号 / 批次号 / 计划章节号 / 「新增文件」），
   * 不限制注释长度 —— 长度是审计信号，`longCommentCount` 只输出报告。
   *
   * 标记必须为 0：任何命中都失败，确需保留的例外逐条登记在 `WHITELIST` 并写明理由。
   *
   * 标记用组合 / 条件匹配，避免误报：裸 `来源：` 会命中业务含义（`来源：销售订单`），
   * 裸 `§` 会命中指向 `CONTRIBUTING.md` 的有效链接，裸 `P\d+` 会命中指向
   * `docs/decisions.md` 的「P0/P1/P2 裁决」与 `CONTRIBUTING.md` 的「P12 锁序」。
   *
   * 详细规则见 `AGENTS.md`。
   */

  const REPO = fileURLToPath(new URL('../../', import.meta.url)).replace(/\\/g, '/');

  /** 扫描范围。`db/migration` 单列出来排除 —— Flyway 已应用的 migration 不得改字节。 */
  const SCOPES = [
    {
      key: 'xsy-scm-web/src',
      root: `${REPO}xsy-scm-web/src`,
      extensions: new Set(['.ts', '.vue', '.less', '.js', '.mjs', '.tsx']),
    },
    {
      // 测试源码只扫注释（`commentsOf` 对 `.mjs` 走 `codeComments`，天然跳过字符串、
      // 模板串与正则字面量），因此测试名、断言、正则与内联快照都不在射程内。
      // 但契约测试自身必须排除：它把标记模式写成注释与正则字面量，扫自己必然自证违规。
      key: 'xsy-scm-web/test',
      root: `${REPO}xsy-scm-web/test`,
      extensions: new Set(['.mjs']),
      excludedFiles: new Set([
        // 文案契约与注释契约合并进了 system.test.mjs：它们把标记模式写成注释与正则字面量，
        // 扫自己必然自证违规，因此整份排除。
        'xsy-scm-web/test/system.test.mjs',
      ]),
    },
    {
      key: 'xsy-scm-server/sa-admin/src/main/java/com/xsy/scm',
      root: `${REPO}xsy-scm-server/sa-admin/src/main/java/com/xsy/scm`,
      extensions: new Set(['.java']),
      java: true,
    },
    {
      key: 'xsy-scm-server/sa-admin/src/main/resources',
      root: `${REPO}xsy-scm-server/sa-admin/src/main/resources`,
      extensions: new Set(['.xml', '.yml', '.yaml', '.properties']),
    },
    {
      // 后端测试树。与 `xsy-scm-web/test` 同理：只扫注释，测试类名、方法名、@DisplayName
      // 与断言里的字符串都不在射程内（`commentsOf` 对 `.java` 走 codeComments，先看字面量
      // 再看注释），所以「W5-」这类造数前缀、`assertThat(...)` 的期望值不会被误判。
      // 范围刻意覆盖 net/lab1024 —— SmartAdminMapperPgValidationIT 的阶段演进账就在里面。
      key: 'xsy-scm-server/sa-admin/src/test/java',
      root: `${REPO}xsy-scm-server/sa-admin/src/test/java`,
      extensions: new Set(['.java']),
      java: true,
    },
  ];

  const EXCLUDED_DIRECTORIES = new Set(['node_modules', 'dist', 'dist-verify', 'target', '.runtime', 'archive', '__pycache__', '.vite', 'coverage']);

  /**
   * Flyway 迁移目录：只从扫描中排除，永远不修改。
   *
   * 已应用的 migration 被 Flyway 记录 checksum（`validate-on-migrate: true`），改任何一个
   * 字节都会让后端启动时校验失败。因此这里连注释也不动 —— 哪怕只是把 `**x**` 换成
   * `x`。`resources` 范围的 extensions 不含 `.sql`，正是同一道防线。
   * 目录名本身不带尾斜杠，故不加尾斜杠比对。
   */
  const FLYWAY_DIRECTORY = '/db/migration';

  // ------------------------------------------------------------------
  // 标记集（组合 / 条件匹配，见文件头）
  // ------------------------------------------------------------------

  const PROCESS_MARKERS = [
    ['来源：历史参考实现', /来源\s*[:：]\s*\**\s*(历史参考实现|旧系统代码|legacy)/],
    ['来源：新写', /来源\s*[:：]\s*\**\s*新写/],
    // `来源：W1 派生` 是第三种形态，P4.1 的组合匹配没覆盖到
    ['来源：Wn 派生', /来源\s*[:：]\s*\**\s*派生/],
    ['复制日期', /复制日期/],
    ['Copy First + Adapt', /Copy\s*First/],
    ['剪枝 / 适配 / 验收记录', /(剪枝|适配|验收)\s*[:：]/],
    // 不能只匹配 `Playwright`：`表格 DOM id —— 给 Playwright 定位用` 是在解释
    // 「为什么有两套 id」，属正常约束（实测 4 处误报）。要求 `验收/测试` 在前。
    ['测试 / 验收记录', /(验收|测试)[^\n]{0,20}(Playwright|E2E|单测)|单测已过|测试已过|已通过测试/],
    ['参考项目 / 旧项目对比', /参考项目|参考实现|legacy 项目|V2 W\d/],
    ['为什么新增此文件', /为什么(必须)?(新增|新建)此?文件/],
    ['AI 指令式措辞', /绝不能改|不要乱动|刻意这样|不得改动|禁止修改|必须照做/],
    // closeout 补：这一批是「没有被 marker 集合覆盖」的历史计划标签，
    // 语境上仍是开发批次编号而不是当前业务概念。
    ['新增文件', /新增文件/],
    ['仿 xxx 文件', /仿\s*[`\w]/],
    ['Provenance 要求', /Provenance/],
    ['本阶段', /本阶段/],
    ['HD-Bn 批次号', /HD-[A-Z0-9-]+/],
    ['A.n 条目号', /\bA\.\d+\b/],
    // 裸 `P\d+` 会命中指向 `docs/decisions.md`「P0/P1/P2/P3 裁决」与
    // `CONTRIBUTING.md`「P12 锁序」的<b>活锚点</b>，因此只在没有这些后缀时才算标记。
    ['Pn 批次号（非裁决锚点）', /(?<!锁序 )\bP\d{1,2}\b(?!\s*(?:裁决|基线收口裁决|锁序))/],
    // 裸 `B\d+` 会命中缺口文档的 `Bn` 溯源（那是活契约），由 WHITELIST 逐文件放行。
    ['Bn 批次号（非缺口溯源）', /\bB\d{1,2}\b/],
    // 裸 `A\d+` 曾因「`A4` 是纸张尺寸」被放过，结果 `A17/A18/A25/A26/A31` 全漏了。
    // 现在改成<b>默认禁止 + 精确白名单放行</b>（纸张尺寸的两个文件已登记）。
    ['An 条目号（非业务值）', /\bA\d{1,2}\b/],
    // 「设计考古」：旧项目当时怎么做、旧口径的段落名。
    ['考古：C 的 / A 源 / C 段', /C 的|A 源|C 段|V2 原生/],
    // 开发波次。注意 `预配送波次`（`deliveryWave`）是真实业务字段，不能裸匹配 `波次`。
    ['开发波次', /(出库|盘点|报损报溢|调拨|阈值预警|规格转换)波次|波次新增|本波次/],
    // commit 考古：`历史：提交 48134bf 曾…`。Git 已是历史，源码不需要复述它。
    ['提交 sha 考古', /(历史|背景|提交)\s*[:：]?\s*(提交\s*)?[0-9a-f]{7,40}\b/],
    // 迁移版本号的<b>开发语境</b>：`V15 一并落库` / `V15 阶段` / `V15 旧版` / `V15 migration 提供`。
    // 裸 `V\d+` 不禁 —— `V34 移动加权`、`V63 ck_*`、`V61 web_perms` 是指向真实 migration 文件的
    // 有效溯源（文件存在且不可变），属当前事实。只有把它当"第几批开发"来讲时才是过程标记。
    // 后置语境词只收<b>动作/产物</b>（一并落库 / 播种 / 新建 / 引入）；裸「迁移」不收，
    // 好让「见 V34 迁移头注释」这种有效指向继续成立。
    ['Vn 迁移版本开发语境', /\bV\d{1,3}\b[^\n]{0,10}(一并落库|落库|阶段|旧版|migration|播种|新建|引入|尚未)/],
    // 机械清理后的破损句：删掉 token 时留下了残缺句法。这里只钉<b>已实际出现过</b>的形态，
    // 不搞宽泛的「中文空格」匹配（正常散文里「与 X 成对」「和 Y 一致」是合法的）。
    // 1) 注释体某行以标点开头 —— 主语被删，后面的话失去了依托（`// ：需求单位必须等于…`）。
    //    `body` 对 `//` 已剥掉 `//`，对块注释保留 `*` 前缀，因此两种形态都要放行。
    ['残句：注释行以标点开头', /^[ \t]*(?:\*+[ \t]*)?[：、，。；][^\n]/m],
    // 2) 虚词/动词被空格孤立后直接接「的」（`不让 的内部码`、`与 的`、`以及 的`）。
    ['残句：空格后接的', /(不让|不把|不给|不再|不是|不会|不能|不需要|以及|和|与|或)\s+的/],
    // 3) 单字虚词两侧全空格（`而 对 只有`）—— 中文里几乎必是断裂。
    ['残句：单字虚词两侧空格', /[\u4e00-\u9fa5]\s+(而|对|为|以|用|从|把|被让)\s+[\u4e00-\u9fa5]/],
    // 4) 旧形态：`： 的`。
    ['机械删除残句', /：\s+的/],
    // 5) 折行残迹：手工断行时行尾落在中文里，拼接后成 `…， 而…`。
    //    只钉无歧义的两类：① 空格后紧跟单字虚词再接汉字（`同 的 处理`）；
    //    ② 空格夹在单字虚词两侧（`而 对 只有`，见规则 3）。
    //    宽泛的「汉字 空格 汉字」与「汉字 空格 否定词」会命中正常排版断行
    //    （`标签页 是否显示`、`不能 全部常驻` 是合法分隔），不上。
    ['残句：行内折行留空格', /[\u4e00-\u9fa5][ \t]+(?:的|而|对|为|以|从|把|被)[ \t]*[\u4e00-\u9fa5]/],
    // 6) 排版语法：写作模板的 markdown 粗体 `**x**` 会混进注释里当强调。
    //    源码注释不是渲染载体 —— 强调靠句序和用词表达，不靠标记，也不能换成另一种标记。
    //    前后都排除 `/` 以放过 glob（`../views/**/**.vue`）与除法；字符串字面量里的
    //    `'**'`（脱敏掩码）天然不在注释区内，扫描器只看注释体。
    //    必须允许跨行：`**开头…` 换行 `…结尾**` 是手工写 JSDoc 时的常见形态，
    //    单行正则抓不到。跨度上限 300 字符防止吞掉整段。
    ['markdown 粗体残迹', /(?<!\/)\*\*(?=[^\s*])(?:(?!\*\*)[\s\S]){0,300}?[^\s*]\*\*(?!\/)/],
  ];

  /**
   * 只在 Java 源码 范围内判定的标记。
   *
   * 前端 `.vue` / `.ts` 里反引号是模板字符串（`` const x = `a${b}` ``），不能禁；
   * Java 的 Javadoc 用 `{@code}`，反引号在这里纯粹是写作模板的残留。
   * （markdown 粗体 `**x**` 全范围禁，见 `PROCESS_MARKERS` 第 6 条。）
   */
  const JAVA_ONLY_MARKERS = [['Javadoc markdown 反引号', /\x60/]];

  /**
   * 只在前端源码范围内判定的标记：注释里的 Javadoc / HTML 强调标签。
   *
   * 前端注释不是 HTML 文档，b / i / u 这几个强调标签在这里没有任何渲染含义 ——
   * 它们是「把 Javadoc 写法搬进 `.ts`」的残留，强调应靠句序和用词表达。
   *
   * 刻意不收 p / br / li：它们可能是在描述真实的 HTML 语义（例如「分隔符可设置
   * html 标签如 br 标签」），判成残留会误伤。Java 侧允许 `{@code}` / `{@link}` 与
   * 复杂契约用的段落标签，因此也不入此列。扫描只看注释体，Vue 模板里真实的标签是
   * 运行时 DOM，不在射程内。
   */
  const FRONTEND_ONLY_MARKERS = [
    ['注释里的 HTML 强调标签', /<\/?[biu]\s*\/?>/],
    // 规范第 2 节把 `{@code}` / `{@link}` 与 HTML 强调标签同等对待：它们是 Javadoc 内联标记，
    // 搬到 `.ts` / `.vue` 注释里不会渲染。此前仓库里恰好是 0，但契约没钉，加回来不会红。
    ['前端 Javadoc code', /\{@code\b/],
    ['前端 Javadoc link', /\{@link\b/],
  ];

  /** 声明哪些范围是 Java：反引号规则按语言判定，而不是绑死某个范围名。 */
  const isJavaScope = (scope) => scope.java === true;

  const PLAN_MARKERS = [
    ['Sprint / Wave', /\bWave\b/],
    ['Wn 波次', /\bW\d{1,2}\b/],
    ['Rn 阶段代号', /\bR[0-9]\b/],
    ['A-Dn 编号', /\bA-D\d+\b/],
    ['Qna 编号', /\bQ\d{1,2}[a-z]?\b/],
    // 开发切片号 `3-11a` / `3-12b` / `F1-3A`：ADM-12 是当前决策文档里的长期锚点，但它后面的
    // 「3-11a」在 docs 里没有对应小节，属「这批开发是第几刀」而不是当前约束。
    // 必须收窄，否则会命中两类正常散文：长度范围（`请输入3-16位`）与日期（`09-21`）。
    // 判据是「带字母尾巴」或「紧跟在 ADM-n 之后」——这两种才是切片号的实际写法。
    // `(?<![\d-])` 排掉日期里的中段；`(?![a-z\d])` 排掉 `2-4bit` 这类单位。
    ['字母尾巴的切片号', /(?<![\d-])\d{1,2}-\d{1,2}[a-z](?![a-z\d])/],
    ['ADM 锚点后的切片号', /\bADM-\d{1,2}\s+\d{1,2}-\d{1,2}\b/],
    ['Fn-n 切片号', /\bF\d+-\d+[A-Z]?\b/],
  ];

  /** 判定 `§` 是否指向现存长期文档。 */
  const DOC_REFERENCE = /[\w./-]+\.md/;

  /**
   * 过程标记必须为 0：任何命中都失败，除非逐条登记在下面的 `WHITELIST` 里。
   *
   * 标记集见 `PROCESS_MARKERS` / `PLAN_MARKERS`。它们覆盖了本仓库历史上出现过的全部
   * 过程形式：来源与复制日期、剪枝适配验收记录、参考项目对比、`Wave` / `Wn` / `Rn` /
   * `A-Dn` / `Qna`、无文档引用的 `§`、`Pn` / `Bn` 批次号、`HD-*`、`A.n`、「新增文件」、
   * 「仿 xxx」、「本阶段」、`Provenance`，以及机械删除留下的残句 `： 的`。
   *
   * 需要保留的例外请登记到 `WHITELIST` 并写明理由，不要放宽标记或调高判定。
   */
  /** 显式白名单：确需保留的过程标记逐条登记，`reason` 为空即失败。 */
  const WHITELIST = [
    {
      file: 'xsy-scm-web/src/views/business/scm/promotion/promotion-coupon-list.vue',
      marker: 'Bn 批次号（非缺口溯源）',
      reason: '指向 docs/status.md 的 B7 缺口溯源，是活契约',
    },
    {
      file: 'xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/print/constant/ScmPrintPaperEnum.java',
      marker: 'An 条目号（非业务值）',
      reason: '`{@link #A4}` 是纸张尺寸枚举值，不是计划编号',
    },
    {
      file: 'xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/print/domain/model/ScmPrintTemplateModel.java',
      marker: 'An 条目号（非业务值）',
      reason: '`{@code A4}` 是纸张尺寸，不是计划编号',
    },
    // ---- 测试源码范围（`xsy-scm-web/test`）的例外 ----
    // 测试按模块合并后，这些注释随原文件一起进了模块文件；内容仍是「这条用例为什么存在」，
    // 其中的 Pn / Bn 指向现存文档小节，属活锚点，不是「第几批开发」的过程标记。
    {
      file: 'xsy-scm-web/test/sorting.test.mjs',
      marker: 'Pn 批次号（非裁决锚点）',
      reason: 'P1 指向 docs/decisions.md「P1 分拣管理裁决」现存小节，是活锚点',
    },
    {
      file: 'xsy-scm-web/test/delivery.test.mjs',
      marker: 'Pn 批次号（非裁决锚点）',
      reason: 'P2 指向 docs/decisions.md「P2 物流配送 L3 裁决」现存小节，是活锚点',
    },
    {file: 'xsy-scm-web/test/delivery.test.mjs', marker: 'Provenance 要求', reason: '说明「剥注释」的顺序，避免把头部说明当成代码 —— 属实现约束'},
    {file: 'xsy-scm-web/test/purchase.test.mjs', marker: 'Provenance 要求', reason: '同上，说明剥离顺序不能反'},
    {
      file: 'xsy-scm-web/test/inventory.test.mjs',
      marker: 'Bn 批次号（非缺口溯源）',
      reason: '守卫 docs/status.md 中的后端字段缺口，B1、B4、B5、B7 是逐条校验的对象，属活契约',
    },
    {
      file: 'xsy-scm-web/test/ui.test.mjs',
      marker: 'AI 指令式措辞',
      reason: '`V110 不得改动表结构或增删行` 是断言失败消息，描述迁移不得做之事，不是 AI 指令',
    },
  ];

  /**
   * 只需报告的基线（不参与过程标记的成败判定 —— 那一项要求恒为 0）。
   *
   * `anchoredSection`：指向现存文档的 `§` 链接数，只允许不减少（它们是有效引用）。
   *
   * 这里刻意不再保存 `longCommentCount`：长注释数从来不参与成败，写死的数字却会随治理
   * 漂移（曾经的 130 / 396 早已不是实际值），一个叫 baseline 的错数字比没有更误导人。
   * 实际数量由「长注释计数」那条测试在运行时从扫描结果打印。
   */
  const REPORT_BASELINE = {
    // order-return-list 的 2 个 §14.5 引用随 B6 缺口补齐（原订单号/客户快照已入 VO）一并移除，基线 4→2。
    'xsy-scm-web/src': {anchoredSection: 2},
    'xsy-scm-web/test': {anchoredSection: 0},
    'xsy-scm-server/sa-admin/src/main/java/com/xsy/scm': {anchoredSection: 0},
    'xsy-scm-server/sa-admin/src/main/resources': {anchoredSection: 0},
    'xsy-scm-server/sa-admin/src/test/java': {anchoredSection: 0},
  };

  // ------------------------------------------------------------------
  // 注释抽取（先跳过字符串，再取注释体）
  // ------------------------------------------------------------------

  const countNewlines = (text, from, to) => {
    let count = 0;
    for (let index = from; index < to; index += 1) {
      if (text.charCodeAt(index) === 10) count += 1;
    }
    return count;
  };

  /** 正则字面量只能出现在这些字符之后（`/` 是除号时不能当正则起点）。 */
  const REGEX_PRECEDERS = new Set(['(', ',', '=', ':', '[', '!', '&', '|', '?', '{', '}', ';', '+', '-', '*', '%', '<', '>', '~', '^']);

  /**
   * 抽取 `//` 与 `/* *\/` 注释，返回 `{start, end, body}`（1 基行号）。
   * 跳过字符串字面量、模板字符串、正则字面量；`allowTemplateLiteral === false` 时按
   * Java 处理（额外跳过 `"""` 文本块，且没有模板字符串）。
   */
  function codeComments(text, startLine, allowTemplateLiteral) {
    const found = [];
    const length = text.length;
    let index = 0;
    let line = startLine;
    let previousSignificant = null;

    while (index < length) {
      const character = text[index];

      if (character === '\n') {
        line += 1;
        index += 1;
        continue;
      }
      if (character === ' ' || character === '\t' || character === '\r') {
        index += 1;
        continue;
      }

      // Java 文本块
      if (!allowTemplateLiteral && text.startsWith('"""', index)) {
        let end = text.indexOf('"""', index + 3);
        end = end < 0 ? length : end + 3;
        line += countNewlines(text, index, end);
        index = end;
        previousSignificant = '"';
        continue;
      }

      if (character === '"' || character === "'") {
        index += 1;
        while (index < length) {
          if (text[index] === '\\') {
            index += 2;
            continue;
          }
          if (text[index] === character) {
            index += 1;
            break;
          }
          if (text[index] === '\n') line += 1;
          index += 1;
        }
        previousSignificant = character;
        continue;
      }

      if (allowTemplateLiteral && character === '`') {
        index += 1;
        while (index < length) {
          if (text[index] === '\\') {
            index += 2;
            continue;
          }
          if (text[index] === '`') {
            index += 1;
            break;
          }
          if (text[index] === '\n') line += 1;
          index += 1;
        }
        previousSignificant = '`';
        continue;
      }

      if (text.startsWith('//', index)) {
        let end = text.indexOf('\n', index);
        if (end < 0) end = length;
        found.push({start: line, end: line, body: text.slice(index + 2, end)});
        index = end;
        continue;
      }

      if (text.startsWith('/*', index)) {
        let end = text.indexOf('*/', index + 2);
        end = end < 0 ? length : end + 2;
        const body = text.slice(index, end);
        const lines = countNewlines(text, index, end);
        found.push({start: line, end: line + lines, body});
        line += lines;
        index = end;
        continue;
      }

      // 正则字面量：`/` 不是注释、且上一有效字符允许正则时
      if (character === '/' && (previousSignificant === null || REGEX_PRECEDERS.has(previousSignificant))) {
        index += 1;
        let inClass = false;
        while (index < length) {
          const inner = text[index];
          if (inner === '\\') {
            index += 2;
            continue;
          }
          if (inner === '\n') break;
          if (inner === '[') inClass = true;
          else if (inner === ']') inClass = false;
          else if (inner === '/' && !inClass) {
            index += 1;
            break;
          }
          index += 1;
        }
        while (index < length && /[a-z]/.test(text[index])) index += 1;
        previousSignificant = '/';
        continue;
      }

      previousSignificant = character;
      index += 1;
    }

    return found;
  }

  function htmlComments(text, startLine) {
    const found = [];
    const pattern = /<!--([\s\S]*?)-->/g;
    let match = pattern.exec(text);
    while (match) {
      found.push({
        start: startLine + countNewlines(text, 0, match.index),
        end: startLine + countNewlines(text, 0, match.index + match[0].length),
        body: match[1],
      });
      match = pattern.exec(text);
    }
    return found;
  }

  function hashComments(text, startLine) {
    const found = [];
    const pattern = /(^|\n)[ \t]*#[^\n]*/g;
    let match = pattern.exec(text);
    while (match) {
      found.push({
        start: startLine + countNewlines(text, 0, match.index),
        end: startLine + countNewlines(text, 0, match.index + match[0].length),
        body: match[0],
      });
      match = pattern.exec(text);
    }
    return found;
  }

  /** 顶层 SFC 块切分：`<template>` / `<script>` / `<style>` 约定写在行首。 */
  const SFC_BOUNDARY = /^<(template|script|style)\b[^>]*>|^<\/(template|script|style)>/;

  function vueComments(text) {
    const found = [];
    const lines = text.split('\n');
    const spans = [];
    let open = null;
    let offset = 0;
    for (const line of lines) {
      const match = SFC_BOUNDARY.exec(line);
      if (match) {
        if (match[1] && !open) open = {name: match[1], from: offset};
        else if (match[2] && open && open.name === match[2]) {
          spans.push({name: open.name, from: open.from, to: offset});
          open = null;
        }
      }
      offset += line.length + 1;
    }
    for (const span of spans) {
      const segment = text.slice(span.from, span.to);
      const startLine = countNewlines(text, 0, span.from) + 1;
      if (span.name === 'template') found.push(...htmlComments(segment, startLine));
      else found.push(...codeComments(segment, startLine, true));
    }
    // SFC 块之外的区域也要扫：文件头常写成 `<template>` 之前的 `<!-- ... -->`
    // （`supplier-sku-drawer.vue` 的来源块就在那里）。漏掉这段会低估命中数。
    let cursor = 0;
    const outside = [];
    for (const span of spans) {
      if (span.from > cursor) outside.push([cursor, span.from]);
      cursor = Math.max(cursor, span.to);
    }
    if (cursor < text.length) outside.push([cursor, text.length]);
    for (const [from, to] of outside) {
      found.push(...htmlComments(text.slice(from, to), countNewlines(text, 0, from) + 1));
    }
    return found;
  }

  function commentsOf(path) {
    const text = readFileSync(path, 'utf8');
    const extension = path.slice(path.lastIndexOf('.')).toLowerCase();
    if (extension === '.vue') return vueComments(text);
    if (extension === '.java') return codeComments(text, 1, false);
    if (['.ts', '.js', '.mjs', '.tsx', '.less'].includes(extension)) return codeComments(text, 1, true);
    if (extension === '.xml') return htmlComments(text, 1);
    if (['.yml', '.yaml', '.properties'].includes(extension)) return hashComments(text, 1);
    return [];
  }

  // ------------------------------------------------------------------
  // 扫描
  // ------------------------------------------------------------------

  function collectFiles(root, extensions, excludedFiles = new Set()) {
    const files = [];
    const walk = (directory) => {
      let entries;
      try {
        entries = readdirSync(directory, {withFileTypes: true});
      } catch {
        return;
      }
      for (const entry of entries) {
        const path = `${directory}/${entry.name}`;
        if (entry.isDirectory()) {
          if (EXCLUDED_DIRECTORIES.has(entry.name)) continue;
          if (path.includes(FLYWAY_DIRECTORY)) continue;
          walk(path);
        } else if (extensions.has(path.slice(path.lastIndexOf('.')).toLowerCase())) {
          if (excludedFiles.has(path.slice(REPO.length))) continue;
          files.push(path);
        }
      }
    };
    walk(root);
    return files.sort();
  }

  /** 扫描一个范围，返回各标记命中数、`§` 分类与长注释计数。 */
  function scanScope(scope) {
    const hits = new Map();
    let anchoredSection = 0;
    let longCommentCount = 0;

    const record = (key, relative) => {
      if (!hits.has(key)) hits.set(key, []);
      hits.get(key).push(relative);
    };

    const files = collectFiles(scope.root, scope.extensions, scope.excludedFiles ?? new Set());
    const markers = [
      ...PROCESS_MARKERS,
      ...PLAN_MARKERS,
      ...(isJavaScope(scope) ? JAVA_ONLY_MARKERS : []),
      ...(scope.key.startsWith('xsy-scm-web/') ? FRONTEND_ONLY_MARKERS : []),
    ];
    for (const file of files) {
      const relative = file.slice(REPO.length);
      for (const {start, end, body} of commentsOf(file)) {
        if (end - start + 1 > 8) longCommentCount += 1;
        for (const [key, pattern] of markers) {
          if (pattern.test(body)) record(key, relative);
        }
        const hasDocumentReference = DOC_REFERENCE.test(body);
        const sections = body.match(/§/g);
        if (sections) {
          if (hasDocumentReference) anchoredSection += sections.length;
          else for (const _ of sections) record('§ 无 .md 引用', relative);
        }
      }
    }

    return {files: files.length, hits, anchoredSection, longCommentCount};
  }

  const RESULTS = new Map(SCOPES.map((scope) => [scope.key, scanScope(scope)]));

  // ------------------------------------------------------------------
  // 断言
  // ------------------------------------------------------------------

  test('扫描基线有效：每个范围都能被枚举并解析', () => {
    for (const scope of SCOPES) {
      const result = RESULTS.get(scope.key);
      // 测试目录按模块合并成少量文件（每个模块一个），不适用「> 50 个文件」的体量门槛；其余范围沿用。
      const minimum = scope.key === 'xsy-scm-web/test' ? 10 : 50;
      assert.ok(result.files > minimum, `${scope.key} 只枚举到 ${result.files} 个文件，扫描方式可能已失效`);
    }
    // 反向护栏：如果抽取器坏了（比如把整个文件当成注释），长注释数会异常膨胀
    const web = RESULTS.get('xsy-scm-web/src');
    assert.ok(web.longCommentCount < 400, `前端长注释块 ${web.longCommentCount} 个，疑似注释抽取器失效`);
  });

  test('Flyway 已应用 migration 不在扫描范围内', () => {
    const resources = SCOPES.find((scope) => scope.key.endsWith('resources'));
    const files = collectFiles(resources.root, resources.extensions);
    const migrations = files.filter((file) => file.includes(FLYWAY_DIRECTORY));
    assert.deepEqual(migrations, [], 'db/migration 被纳入了扫描；已应用的 migration 不得修改');
    assert.ok(files.length > 10, 'resources 范围扫描不到文件，排除规则可能写错了');
    // 直接确认排除规则本身生效（而不是靠扩展名恰好不匹配兜住）
    assert.ok(
      readdirSync(`${REPO}xsy-scm-server/sa-admin/src/main/resources/db/migration`).length > 50,
      'migration 目录不存在或为空，排除规则没有被真正验证'
    );
  });

  test('过程标记必须为 0（未登记白名单的一律失败）', () => {
    assert.ok(
      WHITELIST.every((entry) => entry.reason && entry.reason.trim()),
      'WHITELIST 的每条都要写 reason，否则等于无理由放宽'
    );
    const allowed = new Set(WHITELIST.map((entry) => `${entry.marker}\u0000${entry.file}`));
    const failures = [];
    for (const scope of SCOPES) {
      const {hits} = RESULTS.get(scope.key);
      // 断言列表必须与 `scanScope` 实际使用的标记集一致，否则新规则记录后无人校验。
      const checked = [
        ...PROCESS_MARKERS,
        ...PLAN_MARKERS,
        ...(isJavaScope(scope) ? JAVA_ONLY_MARKERS : []),
        ...(scope.key.startsWith('xsy-scm-web/') ? FRONTEND_ONLY_MARKERS : []),
        ['§ 无 .md 引用'],
      ];
      for (const [key] of checked) {
        const remaining = (hits.get(key) ?? []).filter((file) => !allowed.has(`${key}\u0000${file}`));
        if (remaining.length) {
          failures.push(`${scope.key} 的「${key}」${remaining.length} 处：${[...new Set(remaining)].slice(0, 4).join(', ')}`);
        }
      }
    }
    assert.deepEqual(
      failures,
      [],
      '新增了 AI 开发过程残留注释。注释只解释当前代码中无法直接看出的约束，不记录代码是怎么被开发出来的；' +
        '确需保留请登记到 WHITELIST 并写明理由（见 AGENTS.md）'
    );
  });

  test('指向现存文档的 § 链接不得被误删', () => {
    for (const scope of SCOPES) {
      const {anchoredSection} = RESULTS.get(scope.key);
      const expected = REPORT_BASELINE[scope.key].anchoredSection;
      assert.ok(
        anchoredSection >= expected,
        `${scope.key} 指向现存文档的 § 链接从 ${expected} 降到 ${anchoredSection}；` +
          '`长期规则见 CONTRIBUTING.md §SCM 界面规范` 是有效引用，不是过程标记'
      );
    }
  });

  test('长注释计数：仅报告，不判违规', () => {
    // 故意不断言上限：长度是审计信号，不是质量判定。
    // 否则开发者为了过测试会把 9 行有价值的说明硬压成 5 行，反而降低代码质量。
    const report = SCOPES.map((scope) => {
      const {longCommentCount, anchoredSection} = RESULTS.get(scope.key);
      const short = scope.key.replace('xsy-scm-server/sa-admin/src/', '');
      return `${short} >8行=${longCommentCount} 合法§链接=${anchoredSection}`;
    });
    console.log(`[scm-comment-noise] 审计信号（非违规）：${report.join(' | ')}`);
    assert.ok(true);
  });
}

// ---------------------------------------------------------------- 来源：scm-metrics-single-source-contract.test.mjs
{
  /*
   * 经营指标单一来源契约：同一个业务指标只能有一处 SQL 定义。
   *
   * 为什么需要这条门禁：首页、大屏、报表都要显示「今日销售额」。各自写一份 SQL 时，两份都跑得通、
   * 都不报错，只是数字慢慢不一样 —— 而用户会先失去对数字的信任，再去找人核对。这类漂移不会触发任何
   * 构建期或运行期错误，只能靠把「定义处」钉死来防。
   *
   * 本文件只做源码级检查（快、无需数据库）；数值层面的守卫在
   * ScmBusinessMetricsConsistencyPgIT（同区间两端取值必须相等）。
   */

  const ROOT = path.resolve(fileURLToPath(new URL('../../', import.meta.url)));
  const MAPPERS = path.join(ROOT, 'xsy-scm-server/sa-admin/src/main/resources/mapper/scm');
  const METRICS_MAPPER = path.join(MAPPERS, 'metrics/ScmBusinessMetricsMapper.xml');
  const SCREEN_MAPPER = path.join(MAPPERS, 'screen/ScreenDataMapper.xml');
  const REPORT_MAPPER = path.join(MAPPERS, 'report/ReportDao.xml');
  const DASHBOARD_SRC = path.join(ROOT, 'xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/dashboard');

  const read = (file) => readFileSync(file, 'utf8').replace(/\r\n/g, '\n');

  /** 取出一个 mapper 里全部 <select id="..."> 的 id。 */
  const selectIds = (xml) => [...xml.matchAll(/<select\s+id="([^"]+)"/g)].map((m) => m[1]);

  /** 取出一个 mapper 里指定 <sql id="..."> 的完整片段（含标签）。 */
  function sqlFragment(xml, id) {
    const match = new RegExp(`<sql id="${id}">[\\s\\S]*?</sql>`).exec(xml);
    assert.ok(match, `缺少 sql 片段 ${id}`);
    return match[0];
  }

  /** 递归收集目录下所有 .java / .xml 文件。 */
  function sourceFiles(dir) {
    const found = [];
    for (const entry of readdirSync(dir)) {
      const full = path.join(dir, entry);
      if (statSync(full).isDirectory()) found.push(...sourceFiles(full));
      else if (/\.(java|xml)$/.test(entry)) found.push(full);
    }
    return found;
  }

  // 迁入 metrics 的指标：迁完就必须在这里，缺一个都说明「搬了一半」。
  const MOVED_METRICS = [
    'countOrdersByConfirmedAt',
    'sumSettlementAmountByConfirmedAt',
    'sumOrderedAmountByCreatedAt',
    'countCustomersWithOrdersByConfirmedAt',
    'topCustomersByConfirmedAt',
    'topProductsByConfirmedAt',
    'countTotalOrders',
    'sumTotalSettlementAmount',
    'countCustomers',
    'countSuppliers',
    'countSkus',
    'sumInventoryQuantity',
    'countInventorySkus',
    'countEnabledWarehouses',
    'countMovementsByTypeAndRange',
    'inventoryHealthRows',
    'countPurchaseOrdersBySubmittedAt',
    'sumPurchaseAmountBySubmittedAt',
    'countTotalPurchaseOrders',
    'sumTotalPurchaseAmount',
    'countReceipts',
    'countSuppliersWithOrdersBySubmittedAt',
    'trendByDay',
  ];

  test('经营类指标全部定义在 metrics mapper，迁一半会留下两个口径', () => {
    const ids = new Set(selectIds(read(METRICS_MAPPER)));
    const missing = MOVED_METRICS.filter((id) => !ids.has(id));
    assert.deepEqual(missing, [], `这些指标必须定义在 ScmBusinessMetricsMapper.xml：${missing.join(', ')}`);
  });

  test('大屏 mapper 不再重复定义经营类指标', () => {
    const xml = read(SCREEN_MAPPER);
    const ids = selectIds(xml);
    const duplicated = MOVED_METRICS.filter((id) => ids.includes(id));
    assert.deepEqual(duplicated, [], `这些指标已迁入 metrics，大屏不得再有一份：${duplicated.join(', ')}`);

    // 销售金额列只允许出现在 metrics：出现在这里就说明有人又抄了一份销售额口径。
    for (const column of ['settlement_total_amount', 'ordered_total_amount']) {
      assert.ok(!xml.includes(column), `大屏 mapper 不应再出现 ${column}（销售额 / 下单金额的唯一口径在 metrics）`);
    }
  });

  test('报表概览的销售三件套与 metrics 同口径（同轴、同列、同状态）', () => {
    // 报表与大屏的范围策略不同（报表销售不做归属收窄，可见性由页面权限承担），
    // 但口径必须一致：同一根时间轴、同一个金额列、同一个状态。
    const xml = read(REPORT_MAPPER);
    const overview = /<select id="overviewKpi"[\s\S]*?<\/select>/.exec(xml);
    assert.ok(overview, 'ReportDao.xml 必须保留 overviewKpi');
    assert.ok(overview[0].includes('o.confirmed_at'), '销售额必须走确认轴，与首页 / 大屏同轴');
    assert.ok(overview[0].includes('SUM(o.settlement_total_amount)'), '销售额必须取结算金额列');
    assert.ok(overview[0].includes("o.status = 'CONFIRMED'"), '销售额只算已确认订单');
  });

  test('报表的采购状态清单也从采购枚举派生，不硬编码', () => {
    const xml = read(REPORT_MAPPER);
    for (const id of ['overviewKpi', 'purchaseOverview']) {
      const block = new RegExp(`<select id="${id}"[\\s\\S]*?<\\/select>`).exec(xml);
      assert.ok(block, `ReportDao.xml 缺少 ${id}`);
      assert.ok(block[0].includes('committedStatuses'), `${id} 的采购状态必须由参数传入，而不是写死在 SQL 里`);
      for (const status of ['SUBMITTED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'SHORT_CLOSED']) {
        assert.ok(!block[0].includes(`'${status}'`), `${id} 不应硬编码采购状态 ${status}`);
      }
    }
  });

  test('两处共用的数据范围谓词逐字一致，避免两边各自漂移', () => {
    const metrics = read(METRICS_MAPPER);
    const screen = read(SCREEN_MAPPER);
    // 这三段在两边都还要用：metrics 的经营类指标用，大屏的仓库分布 / 网络节点 / 地理分布用。
    for (const id of ['scopeC', 'scopeB', 'scopeW']) {
      assert.equal(
        sqlFragment(screen, id),
        sqlFragment(metrics, id),
        `${id} 在两份 mapper 里不一致：范围谓词抄两份就一定会有一边先改，两边先改的那次不会报错`
      );
    }
  });

  test('dashboard 模块不得自己写经营统计 SQL', () => {
    const offenders = [];
    for (const file of sourceFiles(DASHBOARD_SRC)) {
      const text = read(file);
      if (/\b(xsy_sales_order|xsy_purchase_order|xsy_inventory_balance)\b/.test(text)) {
        offenders.push(path.relative(ROOT, file));
      }
    }
    assert.deepEqual(offenders, [], `dashboard 只做装配与权限裁剪，指标一律向 ScmBusinessMetricsService 取：${offenders.join(', ')}`);
  });
}

// ---------------------------------------------------------------- 来源：scm-ui-copy-contract.test.mjs
{
  /**
   * SCM UI 文案与信息层级的契约单测。
   *
   * ## 为什么需要这份契约
   *
   * 项目曾系统性地把开发设计说明、业务规则推导、技术实现细节直接写进业务 UI：
   * 常驻蓝色说明框、表单 `help` 长句、报表工具栏口径注释，甚至把 `path` / `parent_id` /
   * `SALES_OUT` / `occurred_at` 这类字段名与枚举名展示给业务用户。
   *
   * 典型反例（计量单位编辑弹窗）：
   *
   * ```text
   * 单位名称
   * 业务表按名称记账，改名会让历史数据失去真值来源     ← 数据模型解释
   * 建议小数位
   * 只约束前端输入，不改变数据库精度                   ← 纯实现细节
   * ```
   *
   * 治理规则见 `CONTRIBUTING.md §SCM 界面规范`：
   * 不要为了证明系统设计严谨，而让 UI 替代码写设计文档。
   * 代码注释、ADR、测试负责解释「为什么」；业务 UI 负责告诉用户「现在能做什么」。
   *
   * 本文件钉住治理结果，防止「随手再补一句说明」把噪音重新引回来。
   *
   * ## 为什么用 AST 而不是正则
   *
   * 曾经的一次性扫描用「正则 + 人工排除」，因为要区分「渲染文案」和「源码注释」，
   * 还要排除组件标签名、SVG 属性、`v-for :key`。人工复核可以容忍误报，长期跑的测试不允许。
   *
   * 因此这里用 `vue-eslint-parser`（项目 `devDependencies` 直接依赖）解析 SFC 的 template AST，
   * 只看两类目标：
   *
   * - 文本节点（`VText`）；
   * - `placeholder` / `help` / `title` / `message` / `description` / `label` / `extra` / `empty-text`
   *   的属性值，绑定形式（`:message="…"`）先取表达式里的字面文本再检查。
   *
   * AST 天然放过、不需要额外排除逻辑的：
   *
   * | 形态 | 处理 |
   * | --- | --- |
   * | `@click="save"`、`v-if="…"`、`:key="row.fileKey"` | 不查（指令表达式） |
   * | `import FinanceDetailDrawer`、`<FinanceDetailDrawer>` | 不查（组件标签名 / `<script>`） |
   * | `<!-- 后端说明 -->` | 不查（注释节点） |
   * | `<script>` 里的 `const fileKey = …` | 不查（只解析 template） |
   * | `<svg><path d="…"/></svg>` | 不查（`svg` 子树整体跳过） |
   * | `{{ row.path }}` | 不查（`VExpressionContainer` 无文本节点） |
   *
   * 这就是 `source.includes('后端')` 之类整串匹配不能用的原因：上面每一类都会误报。
   */

  const require = createRequire(import.meta.url);
  const {parseForESLint} = require('vue-eslint-parser');
  const typescriptParser = require('@typescript-eslint/parser');

  const SRC = fileURLToPath(new URL('../src/', import.meta.url));
  const SCM_VIEWS = `${SRC}views/business/scm/`;
  const SCM_COMPONENTS = `${SRC}components/business/scm/`;

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

  const norm = (path) => path.replace(/\\/g, '/');
  const rel = (path) => norm(path).slice(norm(SRC).length);

  const SCM_VIEW_FILES = collect(SCM_VIEWS, /\.vue$/);
  const SCM_COMPONENT_FILES = collect(SCM_COMPONENTS, /\.vue$/);
  /** 黑名单与用词规则扫描全量 SCM 界面；棘轮只针对业务页面。 */
  const SCM_UI_FILES = [...SCM_VIEW_FILES, ...SCM_COMPONENT_FILES];

  /** 用户可见的属性值。只有这些属性名会被读取，`d` / `key` / `type` 天然不在内。 */
  const TEXT_ATTRIBUTES = new Set([
    'placeholder',
    'help',
    'title',
    'message',
    'description',
    'label',
    'extra',
    'empty-text',
    // `hint` 渲染成 ⓘ Tooltip，同样摆在用户眼前；全仓库当前为 0，纳入扫描免得再长出实现细节。
    'hint',
  ]);

  /** 子树整体跳过：不是用户可见文案，且最容易混入技术标识。 */
  const SKIP_SUBTREE = new Set(['svg', 'script', 'style']);

  /**
   * 「就地」上下文：这些组件里的文案只在用户主动打开后出现，不算常驻解释，
   * 因此不计入常驻长句棘轮（弹窗里的操作后果说明、Warning 里的原因都属此列）。
   */
  const CONTEXTUAL = new Set(['amodal', 'adrawer', 'aalert', 'aempty', 'atooltip', 'apopover', 'adescriptions']);

  /**
   * 标签名归一化：parser 会把标签名转小写，但 `ReportNote` 得到 `reportnote`、
   * `report-note` 得到 `report-note` —— 只有去掉连字符两者才等价。
   */
  const tagOf = (node) => node.name.toLowerCase().replace(/-/g, '');

  // ------------------------------------------------------------------
  // 黑名单（CONTRIBUTING.md §SCM 界面规范）
  // ------------------------------------------------------------------

  /**
   * `label` 用于断言失败时告诉维护者「违反了哪一类」。
   * 缩写与字段名加 `\b` 词边界：`商品SKU` 要命中（中文对 `\b` 而言是非单词字符），
   * `SKU_CODE` 里的 `SKU` 不能命中（后面紧跟 `_`，不构成词边界）。
   */
  const BLACKLIST = [
    {label: '模块名 Finance', pattern: /Finance/},
    {label: '阶段代号 R0/R1/R2', pattern: /\bR[012]\b/},
    // 开发波次代号会以错误文案的形式漏到用户面前（实测 `W5 不做自动换算` 出现在 40971
    // 的用户可读文案里），因此必须与 R0/R1/R2 同等对待。
    {label: '阶段代号 Wn', pattern: /\bW\d{1,2}\b/},
    {label: '计划章节号 §', pattern: /§/},
    {label: '术语 SPU', pattern: /\bSPU\b/i},
    {label: '术语 SKU', pattern: /\bSKU\b/i},
    // 中文没有词边界：`商品规格` 里天然含有 `品规` 子串，必须排除，否则整表误报。
    {label: '术语 品规', pattern: /(?<!商)品规(?!格)/},
    {label: '术语 货品', pattern: /货品/},
    {label: '术语 单品', pattern: /单品/},
    {
      label: '字段名',
      pattern: /\b(path|parent_id|seller_id|fileKey|occurred_at|createTime)\b/,
    },
    {
      label: '枚举名',
      pattern: /\b(PURCHASE_IN|SALES_OUT|DIRECT|WAREHOUSE_CONFIRM)\b/,
    },
    {label: '错误码', pattern: /\b(40989|40995)\b/},
    {label: '技术词', pattern: /数据库|事务|幂等|真值|精度|后端|前端|服务端|客户端/},
    // 实现词：数据的存储 / 比对方式不是用户关心的事，页面只说「改了什么」。
    // 曾经以列标题「快照」和说明句「保留快照」出现在财务抽屉与停用确认里。
    {label: '实现词 快照', pattern: /快照/},
    // 「测试」只属于开发过程；用户界面出现它，说明开发期的临时数据或自测入口带上了线。
    {label: '实现词 测试', pattern: /测试/},
    {label: '技术缩写 VO', pattern: /\bVO\b/},
  ];

  // ------------------------------------------------------------------
  // 解析与取值
  // ------------------------------------------------------------------

  /** 解析 SFC，只取 template 的 AST。`<script lang="ts">` 交给 TS parser，避免 espree 语法错误。 */
  function templateRootOf(file) {
    const {ast} = parseForESLint(readFileSync(file, 'utf8'), {
      sourceType: 'module',
      ecmaVersion: 2022,
      parser: typescriptParser,
      parserOptions: {ecmaVersion: 2022, sourceType: 'module'},
    });
    return ast.templateBody;
  }

  /** 遍历 template AST，跳过 `svg` / `script` / `style` 子树。 */
  function walk(root, visit) {
    if (!root) return;
    const stack = [root];
    while (stack.length) {
      const node = stack.pop();
      if (!node || typeof node.type !== 'string') continue;
      if (node.type === 'VElement' && SKIP_SUBTREE.has(tagOf(node))) continue;
      visit(node);
      if (Array.isArray(node.children)) {
        for (let index = node.children.length - 1; index >= 0; index -= 1) {
          stack.push(node.children[index]);
        }
      }
    }
  }

  /** 属性名：普通属性取 `key.name`，`v-bind:` / `:` 取指令参数名；其余（`v-bind="obj"`、`v-on`）返回 null。 */
  function attributeName(attribute) {
    if (!attribute.directive) return attribute.key?.name ?? null;
    const argument = attribute.key?.argument;
    return argument?.type === 'VIdentifier' ? argument.name : null;
  }

  /**
   * 该元素是否带了某个指令。注意 `vue-eslint-parser` 把 `v-if` 的指令名解析成 `if`
   * （不带 `v-` 前缀，`rawName` 也不在 `key` 上），因此这里传的是 `'if'` 而不是 `'v-if'`。
   */
  function hasDirective(element, name) {
    return (element.startTag?.attributes ?? []).some((attribute) => attribute.directive && attribute.key?.name?.name === name);
  }

  /** 从绑定表达式中抽取字面文本；纯动态表达式（`:title="row.name"`）返回空数组。 */
  function literalTexts(node, out = []) {
    if (!node) return out;
    switch (node.type) {
      case 'Literal':
        if (typeof node.value === 'string') out.push(node.value);
        break;
      case 'TemplateLiteral':
        for (const quasi of node.quasis) out.push(quasi.value.cooked ?? quasi.value.raw);
        for (const expression of node.expressions) literalTexts(expression, out);
        break;
      case 'BinaryExpression':
        if (node.operator === '+') {
          literalTexts(node.left, out);
          literalTexts(node.right, out);
        }
        break;
      case 'LogicalExpression':
      case 'ConditionalExpression':
        literalTexts(node.left ?? node.test, out);
        literalTexts(node.right ?? node.consequent, out);
        if (node.alternate) literalTexts(node.alternate, out);
        break;
      case 'ArrayExpression':
        for (const element of node.elements) literalTexts(element, out);
        break;
      default:
        break;
    }
    return out;
  }

  /**
   * 收集一个 SFC 里全部用户可见文案。
   * 返回 `{text, where}`，`where` 形如 `placeholder` 或 `text`，用于定位。
   */
  function visibleCopyOf(file) {
    const found = [];
    walk(templateRootOf(file), (node) => {
      if (node.type === 'VText') {
        if (/\S/.test(node.value)) found.push({text: node.value, where: 'text'});
        return;
      }
      if (node.type !== 'VElement') return;
      for (const attribute of node.startTag?.attributes ?? []) {
        const name = attributeName(attribute);
        if (!name || !TEXT_ATTRIBUTES.has(name)) continue;
        if (!attribute.directive) {
          if (typeof attribute.value?.value === 'string') {
            found.push({text: attribute.value.value, where: name});
          }
          continue;
        }
        for (const text of literalTexts(attribute.value?.expression)) {
          if (/\S/.test(text)) found.push({text, where: name});
        }
      }
    });
    return found;
  }

  /** 扫描全部 SCM 界面，按「违反类别 → 命中」聚合。 */
  function blacklistHits() {
    const hits = [];
    for (const file of SCM_UI_FILES) {
      for (const {text, where} of visibleCopyOf(file)) {
        for (const {label, pattern} of BLACKLIST) {
          if (pattern.test(text)) hits.push({file: rel(file), where, label, text: text.trim()});
        }
      }
    }
    return hits;
  }

  /** 统计某个形态的出现次数，可限定文件范围。 */
  function countShape(files, predicate) {
    let total = 0;
    const offenders = [];
    for (const file of files) {
      let perFile = 0;
      walk(templateRootOf(file), (node) => {
        if (predicate(node)) perFile += 1;
      });
      if (perFile > 0) {
        total += perFile;
        offenders.push({file: rel(file), count: perFile});
      }
    }
    return {total, offenders};
  }

  /**
   * 统计常驻正文里的长句（渲染文本 > 40 字，且不在 `CONTEXTUAL` 子树内）。
   *
   * 这一类比 `hint` 类元素更难发现：它没有专门的类名，只是「顺手多写一句」，
   * 因此单钉一条上限棘轮，逼着新增时长句必须有理由。
   */
  function countResidentLongText(files, limit = 40) {
    let total = 0;
    const offenders = [];
    for (const file of files) {
      let perFile = 0;
      const visit = (node, contextual) => {
        if (!node || typeof node.type !== 'string') return;
        if (node.type === 'VElement') {
          const tag = tagOf(node);
          if (SKIP_SUBTREE.has(tag)) return;
          const nested = contextual || CONTEXTUAL.has(tag);
          for (const child of node.children ?? []) visit(child, nested);
          return;
        }
        if (node.type === 'VText' && !contextual && node.value.trim().length > limit) perFile += 1;
      };
      visit(templateRootOf(file), false);
      if (perFile > 0) {
        total += perFile;
        offenders.push({file: rel(file), count: perFile});
      }
    }
    return {total, offenders};
  }

  const isElement = (name) => {
    const expected = name.toLowerCase().replace(/-/g, '');
    return (node) => node.type === 'VElement' && tagOf(node) === expected;
  };

  /** 常驻 `a-alert type="info"`：无 `v-if` / `v-show`，即「每次打开都占首屏」。 */
  const isResidentInfoAlert = (node) => {
    if (!isElement('a-alert')(node)) return false;
    if (hasDirective(node, 'if') || hasDirective(node, 'show')) return false;
    const typeAttribute = (node.startTag?.attributes ?? []).find((attribute) => !attribute.directive && attribute.key?.name === 'type');
    return typeAttribute?.value?.value === 'info';
  };

  /** 表单 `help`：不区分静态/绑定，属性名命中即计入。 */
  const hasHelpAttribute = (node) =>
    node.type === 'VElement' && (node.startTag?.attributes ?? []).some((attribute) => attributeName(attribute) === 'help');

  /** `type="secondary"` 排版：单元格占位、单位、动态计数与解释性文案共用，只看总量棘轮。 */
  const hasSecondaryType = (node) =>
    node.type === 'VElement' &&
    (node.startTag?.attributes ?? []).some((attribute) => {
      if (attributeName(attribute) !== 'type') return false;
      if (!attribute.directive) return attribute.value?.value === 'secondary';
      return literalTexts(attribute.value?.expression).includes('secondary');
    });

  /**
   * 解释性提示的另一种形态：`class` 里带 `hint`（`coupon-hint` / `__hint` / `--hint`）。
   * 治理前统计「解释性 secondary 89 处」时用的就是这一类选择器，单看 `type="secondary"`
   * 会漏掉一大半，因此两种形态各钉一条棘轮。
   */
  const hasHintClass = (node) =>
    node.type === 'VElement' &&
    (node.startTag?.attributes ?? []).some((attribute) => {
      if (attributeName(attribute) !== 'class') return false;
      const values = attribute.directive
        ? literalTexts(attribute.value?.expression)
        : typeof attribute.value?.value === 'string'
        ? [attribute.value.value]
        : [];
      return values.some((value) => /hint/i.test(value));
    });

  // ------------------------------------------------------------------
  // 基线（治理完成后复扫所得，棘轮只降不升）
  // ------------------------------------------------------------------

  /**
   * 基线 = 文案收口完成后由本文件的扫描器跑出来的真实值，棘轮只降不升。
   *
   * `type="secondary"` 与 `hint` 类分开计数：前者是 antd 排版的属性形态，后者是页面自建的
   * 提示样式（`coupon-hint` / `__hint`），两者不重叠也不互相替代。每个数字只用于
   * 「不比上一次多」，不要跨口径对数 —— 尤其不要把纯渲染的 `secondary`
   * （单元格占位 `—`、单位、`已选 N 个`）当成待删项：删了会破坏表格显示。
   *
   * 提高基线必须在 PR 里说明「不告知会导致什么操作错误」，否则应改为
   * Tooltip / 口径说明 / 操作时确认（见 CONTRIBUTING.md SCM 界面规范）。
   *
   * 清理是分批做的：每清完一批就把受影响的基线降到新的实测值，别留旧数字 —— 留下的差额
   * 会被下一轮「顺手补一句」吃掉。
   */
  const BASELINE = {
    // 19 → 9：商品详情、价格预览、供应商参考价对比、盘点等页面里「系统怎么算的」说明句已删，
    // 只留「不告诉用户就可能操作错误」的 9 处（设备未接入、建议可能失效、批量范围等）。
    residentInfoAlert: 9,
    formHelp: 2,
    // 31 = 上一轮收口后的 32 再减 1：`purchase-demand-summary-preview.vue` 那句
    // 「预览不是最终净采购建议，也不生成采购单。」已从页面常驻改成「净采购缺口」表头的问号提示。
    // 免责本身要留（避免把缺口读成采购建议），但它只在有人看那一列时才需要 —— 按 guidelines §8.1
    // 改成 Tooltip，不再占一行正文。
    secondaryType: 31,
    // 33 → 37（2026-10-28 现场确认 P0）：
    // 采购单详情「锁单时间/操作人」与明细操作列占位（4 处里的 2 处）、
    // 仓库表单「允许负库存」的后果说明（关闭前必须先清负余额，告知不到会直接错账）、
    // 秤端会话记录「班次到期需重新签发」及结束原因（不告知会以为登录一次就长期有效）。
    // 四处都是「不告知就可能操作错误」，不是实现口径说明；订单明细区原「合计数量」旁的解释句已删。
    // 37 → 38（2026-10-28 过账特殊单据，D-1028-16）：
    // 过账页工具栏的「虚拟单据：不生成应收应付，不参与核销」，不写的话财务会以为
    // 登记过账就等于给客户挂账，月末对账时把金额重复计一次。属于「不告知就操作错误」，
    // 不是实现口径说明。
    // 38 → 39（合并 origin/main 的商城「仅下单」）：订单详情待确认态补了一句
    // 「确认不会自动扣款」。不告知的话，销售会以为点确认就把钱收了，客户账上却没动。
    hintClass: 39,
    reportResidentInfoAlert: 0,
    residentLongText: 0,
  };

  const REPORT_FILES = SCM_VIEW_FILES.filter((file) => rel(file).includes('/report/'));

  // ------------------------------------------------------------------
  // 1. 扫描基线有效
  // ------------------------------------------------------------------

  test('扫描基线有效：SCM 界面文件都能被解析出 template', () => {
    assert.ok(SCM_VIEW_FILES.length > 140, `只枚举到 ${SCM_VIEW_FILES.length} 个 SCM 页面，扫描方式可能已失效`);
    assert.ok(REPORT_FILES.length > 20, `只枚举到 ${REPORT_FILES.length} 个报表文件`);
    for (const file of SCM_UI_FILES) {
      assert.ok(templateRootOf(file), `${rel(file)} 没有解析出 template，AST 扫描已失效`);
    }
  });

  // ------------------------------------------------------------------
  // 2. 黑名单：术语、字段名、枚举名、技术词
  // ------------------------------------------------------------------

  test('UI 可见文案不含黑名单术语与实现细节', () => {
    const hits = blacklistHits();
    assert.deepEqual(
      hits.map((hit) => `${hit.file} [${hit.where}] ${hit.label} → ${hit.text}`),
      [],
      `UI 文案命中了 ${hits.length} 处黑名单词；业务界面只说「能做什么」，不解释「怎么实现」`
    );
  });

  // ------------------------------------------------------------------
  // 3. UI 用词规则（AGENTS.md）
  // ------------------------------------------------------------------

  test('UI 可见文案不出现 SPU / SKU / 品规 / 货品 / 单品', () => {
    const pattern = /\bSPU\b|\bSKU\b|(?<!商)品规(?!格)|货品|单品/i;
    const hits = [];
    for (const file of SCM_UI_FILES) {
      for (const {text, where} of visibleCopyOf(file)) {
        if (pattern.test(text)) hits.push(`${rel(file)} [${where}] → ${text.trim()}`);
      }
    }
    assert.deepEqual(hits, [], '用户界面统一使用 商品 / 商品规格，不出现 SPU / SKU / 品规 / 货品 / 单品');
  });

  /**
   * 模板里**用户可见**的 markdown / HTML 残迹。
   *
   * 注释层的同类规则在 `scm-comment-noise-contract`，那里只扫注释，因此写在正文里的
   * `**加粗**` 会原样显示成星号 —— 实测在库存盘点详情与转换单页各漏了一处。
   * 只扫 SCM 自己的页面；SmartAdmin 页面按各自的界面契约检查。
   */
  const VISIBLE_MARKDOWN = [
    {label: 'markdown 加粗', pattern: /\*\*/},
    {label: 'HTML 强调标签', pattern: /<\/?[biu]\s*>/},
  ];

  test('SCM 界面可见文案不含 markdown 残迹', () => {
    const hits = [];
    for (const file of SCM_UI_FILES) {
      for (const {text, where} of visibleCopyOf(file)) {
        for (const {label, pattern} of VISIBLE_MARKDOWN) {
          if (pattern.test(text)) hits.push(`${rel(file)} [${where}] ${label} → ${text.trim().slice(0, 60)}`);
        }
      }
    }
    assert.deepEqual(hits, [], '可见文案里出现 markdown / HTML 强调标记，用户看到的是星号或尖括号');
  });

  // ------------------------------------------------------------------
  // 4. 常驻解释性文案：棘轮只降不升
  // ------------------------------------------------------------------

  test('常驻 info alert 数量不超过基线（新增需在 PR 说明理由）', () => {
    const {total, offenders} = countShape(SCM_VIEW_FILES, isResidentInfoAlert);
    assert.ok(
      total <= BASELINE.residentInfoAlert,
      `常驻 info alert 从 ${BASELINE.residentInfoAlert} 涨到 ${total}；` +
        '「不告诉用户就可能操作错误」的信息才留在界面上，其余收进 Tooltip / 口径说明 / 操作时确认。' +
        `当前分布：${JSON.stringify(offenders)}`
    );
  });

  test('表单 help 数量不超过基线（示例值改用 placeholder）', () => {
    const {total, offenders} = countShape(SCM_VIEW_FILES, hasHelpAttribute);
    assert.ok(
      total <= BASELINE.formHelp,
      `表单 help 从 ${BASELINE.formHelp} 涨到 ${total}；示例值走 placeholder，长解释走帮助文档。` + `当前分布：${JSON.stringify(offenders)}`
    );
  });

  test('secondary 排版数量不超过基线（解释性文案收口后不应反弹）', () => {
    const {total} = countShape(SCM_VIEW_FILES, hasSecondaryType);
    assert.ok(
      total <= BASELINE.secondaryType,
      `secondary 排版从 ${BASELINE.secondaryType} 涨到 ${total}；` + '纯渲染占位（`—`、单位、`已选 N 个`）保留，新增解释性说明请先确认必要性。'
    );
  });

  test('hint 类提示数量不超过基线', () => {
    const {total, offenders} = countShape(SCM_VIEW_FILES, hasHintClass);
    assert.ok(
      total <= BASELINE.hintClass,
      `hint 类提示从 ${BASELINE.hintClass} 涨到 ${total}：${JSON.stringify(offenders)}；` +
        '这类文案和 `type="secondary"` 一样属于「常驻解释」，新增前先确认用户不被告知就会操作错误。'
    );
  });

  test('常驻正文长句数量不超过基线', () => {
    const {total, offenders} = countResidentLongText(SCM_VIEW_FILES);
    assert.ok(
      total <= BASELINE.residentLongText,
      `常驻正文长句从 ${BASELINE.residentLongText} 涨到 ${total}：${JSON.stringify(offenders)}；` +
        '弹窗 / 提示 / 空态里的长句是「就地」出现的，不算常驻；正文里只该留一句话。'
    );
  });

  // ------------------------------------------------------------------
  // 5. 报表口径说明：统一走 ReportNote，不再常驻 Alert
  // ------------------------------------------------------------------

  test('report 下不再有常驻 info alert', () => {
    const {total, offenders} = countShape(REPORT_FILES, isResidentInfoAlert);
    assert.ok(
      total <= BASELINE.reportResidentInfoAlert,
      `report 下仍有 ${total} 处常驻 info alert：${JSON.stringify(offenders)}；` + '统计口径统一收进 report-note 的 Popover / Drawer。'
    );
  });

  /** 属性名命中即计入，不区分静态与绑定。 */
  const hasNamedAttribute = (name) => (node) =>
    node.type === 'VElement' && (node.startTag?.attributes ?? []).some((attribute) => attributeName(attribute) === name);

  test('report-note 组件存在且自带硬规则护栏', () => {
    const component = `${SCM_COMPONENTS}report-note/index.vue`;
    const source = readFileSync(component, 'utf8');
    assert.match(source, /props\.points\.length > 3/, 'report-note 未约束 Popover 条数（最多 3 条）');
    assert.match(source, /point\.length > 40/, 'report-note 未约束单条长度（每条 ≤ 40 字）');
    assert.match(source, /sections/, 'report-note 未提供分组（Drawer）形态，长口径只能塞进 Popover');
  });

  /**
   * 报表模块不再常驻口径说明：`report-note`（Popover / Drawer）与指标卡 ⓘ（`hint`）都算常驻解释。
   *
   * 它们讲的多是 SQL、枚举名与推导过程（§8.2 黑名单），而不是「不告知就会操作错误」的信息。
   * 数据缺失、权限受限与操作不可逆仍走 Warning / 操作时确认，不受这条约束。
   */
  test('报表模块不再常驻口径说明', () => {
    const note = countShape(REPORT_FILES, isElement('report-note'));
    assert.equal(note.total, 0, `报表模块仍有 ${note.total} 处 report-note：${JSON.stringify(note.offenders)}；` + '口径不常驻正文，也不做 ⓘ。');

    const hint = countShape(REPORT_FILES, hasNamedAttribute('hint'));
    assert.equal(
      hint.total,
      0,
      `报表模块仍有 ${hint.total} 处 hint：${JSON.stringify(hint.offenders)}；` +
        '`hint` 渲染成 ⓘ Tooltip，属常驻解释，需要提示时用数据异常 / 权限 Warning。'
    );
  });
}

// ---------------------------------------------------------------- 来源：screen-geo-contract.test.mjs
{
  /*
   * 大屏地图底图与区划字典的同源契约（地图 M1）
   *
   * 钉的是唯一一个跨目录、跨语言才会成立的不变量：省级着色数据按 `adcode`（= `province_code`，
   * 同为 GB/T 2260 六位码）挂到底图要素上。底图少一个码，那个省就静默不着色 ——
   * 接口不报错、图上看只是「没有数据」，而大屏上「没有数据」和「数据为 0」长得一模一样，
   * 靠肉眼验收发现不了。名称不作为匹配键（港澳简称与官方全称不同源，见最后一个用例）。
   */

  const GEO_JSON = new URL('../public/screen/china-province.json', import.meta.url);
  /** 区划字典种子的原文：被测的就是上线的那段 SQL，测试里不复制第二份省名清单。 */
  const V40 = new URL('../../xsy-scm-server/sa-admin/src/main/resources/db/migration/V40__scm_geo_region_and_master_location.sql', import.meta.url);

  function geoFeatures() {
    const collection = JSON.parse(readFileSync(GEO_JSON, 'utf8'));
    assert.equal(collection.type, 'FeatureCollection');
    return collection.features;
  }

  /** V40 Step 2 的省级行（level 1）。 */
  function seededProvinces() {
    return [...readFileSync(V40, 'utf8').matchAll(/^\((\d{6}), NULL, '([^']+)', [^,]+, [^,]+, 1\),$/gm)].map(([, code, name]) => ({code, name}));
  }

  test('底图存档结构可用：每个省都有 adcode 与 name，且 adcode 不重复', () => {
    const named = geoFeatures().filter((feature) => feature.properties?.name);

    // 35 个要素里有 1 条无名的南海诸岛要素：它必须留在图里（边界完整性），但不参与匹配
    assert.equal(named.length, 34);
    for (const feature of named) {
      assert.match(String(feature.properties.adcode), /^\d{6}$/);
      assert.ok(feature.geometry, `${feature.properties.name} 缺几何`);
    }
    assert.equal(new Set(named.map((feature) => feature.properties.adcode)).size, named.length);
  });

  test('每个省级区划码在底图里都有对应要素（着色按码挂载）', () => {
    const adcodes = new Set(geoFeatures().map((feature) => String(feature.properties?.adcode)));
    const provinces = seededProvinces();

    assert.equal(provinces.length, 34, 'V40 的省级种子行数变了，需同步复核底图');
    assert.deepEqual(
      provinces.filter((province) => !adcodes.has(province.code)),
      [],
      '这些省在底图里没有对应 adcode，着色层会静默丢省'
    );
  });

  /**
   * 名称差异必须被「按码匹配」消化掉。
   *
   * 字典沿用 `area-cascader` 的简称（香港 / 澳门），官方边界数据用全称
   * （香港特别行政区 / 澳门特别行政区）。这两行是故意留在这里的告警：
   * 一旦有人把匹配键从 adcode 改回名称，港澳就会从图上消失且没有任何报错。
   */
  test('港澳名称与底图不同源，因此匹配键只能是 adcode', () => {
    const nameByAdcode = new Map(geoFeatures().map((feature) => [String(feature.properties?.adcode), feature.properties?.name]));
    const drift = seededProvinces()
      .filter((province) => nameByAdcode.get(province.code) !== province.name)
      .map((province) => `${province.code}: 字典「${province.name}」 vs 底图「${nameByAdcode.get(province.code)}」`);

    assert.deepEqual(drift, ['810000: 字典「香港」 vs 底图「香港特别行政区」', '820000: 字典「澳门」 vs 底图「澳门特别行政区」']);
  });
}

// ---------------------------------------------------------------- 来源：system-hidden-menu-grant-contract.test.mjs
{
  /*
   * 角色权限树的级联契约：勾父菜单必须把隐藏子菜单一并授出。
   *
   * 隐藏的详情页路由（商品详情 / 客户详情 / 供应商详情 / 线路详情）在侧栏里没有入口，
   * 完全靠「勾了父菜单就一并授出」这一条链路。后端菜单树不过滤 visible_flag（否则隐藏菜单
   * 根本不进树），前端勾选递归进 children（否则勾了父菜单也授不出详情）。
   * 两段任一被改掉，新建角色就会出现「能看列表、点详情空白」，而迁移当时的角色仍然正常 ——
   * 这种只在新建角色上暴露的回归最难查，所以在这里钉住。
   */

  const MENU = new URL('../src/views/system/role/components/role-tree/role-tree-menu.vue', import.meta.url);
  const POINT = new URL('../src/views/system/role/components/role-tree/role-tree-point.vue', import.meta.url);
  const STORE = new URL('../src/store/modules/system/role.ts', import.meta.url);

  const code = (url) => readFileSync(url, 'utf8');

  test('勾选菜单时递归勾上全部子级，含隐藏的详情页菜单', () => {
    const menu = code(MENU);
    assert.match(menu, /roleStore\.addCheckedDataAndChildren\(module\)/);
    assert.match(menu, /roleStore\.deleteCheckedDataAndChildren\(module\)/);

    const store = code(STORE);
    // 递归必须真的进 children：只勾本级会让隐藏详情路由授不出去。
    assert.match(
      store,
      /addCheckedDataAndChildren\(data\) \{[\s\S]*?data\.children\.forEach\(\(item\) => \{\s*this\.addCheckedDataAndChildren\(item\);/,
      'addCheckedDataAndChildren 必须递归进 children'
    );
    assert.match(
      store,
      /deleteCheckedDataAndChildren\(data\) \{[\s\S]*?data\.children\.forEach\(\(item\) => \{\s*this\.deleteCheckedDataAndChildren\(item\);/,
      '取消勾选同样要递归，否则会留下授不出去的孤儿菜单'
    );
  });

  test('菜单节点把全部子级铺成可勾选项，隐藏菜单才看得见、点得到', () => {
    const menu = code(MENU);
    // 带功能点子级的菜单走 RoleTreePoint，而它把 module.children 整体铺开（不过滤类型）。
    assert.match(menu, /<RoleTreePoint :tree="module\.children" @selectCheckbox="selectCheckbox"\s*\/>/);

    const point = code(POINT);
    assert.match(point, /<template v-for="module in props\.tree" :key="module\.menuId">/);
    assert.match(point, /<a-checkbox @change="emits\('selectCheckbox', module\)" :value="module\.menuId">/);
  });
}

// ---------------------------------------------------------------- 来源：business-todo-contract.test.mjs
{
  /**
   * 业务待办（首页只读聚合）的前端契约测试。
   *
   * 钉死这一波破坏后不报错的前端边界（权限裁剪与领域计数由 Java 测试覆盖）：
   * 1. 待办是只读 Pull：走 getRequest，绝不 POST、绝不复用带幂等的写命令；
   * 2. 前端不重复判断权限、不缓存计数、不写业务表 —— 卡片清单完全来自后端；
   * 3. 卡片入口用 scm:todo:query 门禁（无权整卡隐藏），不新建第二套消息中心；
   * 4. 点击是带条件跳转：直接使用后端给的 route（已含查询串），不在前端拼状态。
   */

  /** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被禁用的名字是合规的。 */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  const api = code('../src/api/business/scm/dashboard-api.ts');
  const card = code('../src/views/system/home/components/business-todo-card/home-business-todo.vue');
  const home = code('../src/views/system/home/index.vue');

  test('待办接口是只读 GET，不引入写命令或幂等封装', () => {
    assert.match(api, /todo:[\s\S]*?getRequest(?:<[^']*>)?\('\/scm\/dashboard\/todo',\s*\{\},\s*REGION_REQUEST_OPTIONS\)/);
    assert.match(api, /const REGION_REQUEST_OPTIONS = \{suppressGlobalErrorMessage: true\}/);
    assert.doesNotMatch(api, /postRequest|putRequest|deleteRequest|Idempotency|sendMessage/);
  });

  test('卡片挂载即拉取，数字与清单完全来自后端，不做前端权限判断或本地缓存', () => {
    assert.match(card, /onMounted\(load\)/);
    assert.match(card, /useRegionData<ScmTodo\[\]>\(scmDashboardApi\.todo,/);
    assert.match(card, /const todos = computed\(\(\) => data\.value \?\? \[\]\)/);
    assert.match(card, /<region-error\b[^>]*v-if="error"[^>]*@retry="load"/);
    // 前端不自行按权限过滤卡片，也不落 localStorage
    assert.doesNotMatch(card, /localSave|localStorage|hasPermission|usePermission|filter\(.*perm/);
  });

  test('卡片点击直接跳转后端给的条件路由，不在前端拼接查询状态', () => {
    assert.match(card, /<router-link\b[^>]*:to="todo\.route"/);
    assert.doesNotMatch(card, /query:\s*\{[\s\S]*status:/);
  });

  test('首页入口用 scm:todo:query 门禁，复用既有 home 卡片容器', () => {
    assert.match(home, /const canTodo = computed\(\(\) => hasPermission\('scm:todo:query'\)\)/);
    assert.match(home, /<a-col\b[^>]*v-if="canTodo"[^>]*>\s*<HomeBusinessTodo\b/);
    // 允许带属性：首页的刷新按钮通过模板引用调用卡片的 load，所以会挂 ref
    assert.match(home, /<HomeBusinessTodo\b[^>]*\/>/);
    assert.match(card, /DefaultHomeCard|default-home-card/);
  });
}

// ---------------------------------------------------------------- 来源：message-business-link.test.mjs
{
  /**
   * 「报损报溢驳回消息要能进入目标业务」的前端契约测试。
   *
   * 业务类型判定必须走原生 `t_message.message_type` 的数值，不能解析中文标题；
   * 跳转目标必须与后端待办枚举指向同一个列表页（该页消费 `?id=` 打开详情，详情接口自带权限校验）。
   * 这里同时核对前端常量与后端枚举的数值镜像——两侧漂移会让入口静默消失。
   */

  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  const ACCOUNT_DETAIL = '../src/views/system/account/components/message/components/message-detail.vue';
  const HEADER_DETAIL = '../src/layout/components/header-user-space/header-message-detail-modal.vue';
  const BACKEND_ENUM = '../../xsy-scm-server/sa-base/src/main/java/net/lab1024/sa/base/module/support/message/constant/MessageTypeEnum.java';
  const LOSS_GAIN_SERVICE = '../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/inventory/service/InventoryLossGainService.java';

  test('驳回消息解析为单据入口，主键原样进 query', () => {
    const link = messageBusinessLink(MESSAGE_TYPE_INVENTORY_LOSS_GAIN, '1234567890123456789');
    assert.ok(link);
    assert.equal(link.path, INVENTORY_LOSS_GAIN_LIST_PATH);
    // 主键保持字符串：转成 Number 会让 19 位 id 静默失真并跳到另一张单据
    assert.deepEqual(link.query, {id: '1234567890123456789'});
    assert.equal(link.label, '查看业务单据');
    // 原生 MessageService 对 null dataId 做过 String.valueOf，数字型 dataId 也要能识别
    assert.equal(messageBusinessLink(MESSAGE_TYPE_INVENTORY_LOSS_GAIN, 123).query.id, '123');
  });

  test('没有可用主键或非业务消息不给入口', () => {
    for (const dataId of [undefined, null, '', '   ', 'null', '../etc/passwd', '1; drop table', '12.5']) {
      assert.equal(messageBusinessLink(MESSAGE_TYPE_INVENTORY_LOSS_GAIN, dataId), undefined, `${dataId} 不可用`);
    }
    // 通用站内信 / 订单消息不是报损报溢单据
    assert.equal(messageBusinessLink(MESSAGE_TYPE_ENUM.MAIL.value, '123'), undefined);
    assert.equal(messageBusinessLink(MESSAGE_TYPE_ENUM.ORDER.value, '123'), undefined);
  });

  test('前后端业务类型数值镜像一致，且跳转路径与待办入口同一页面', () => {
    assert.equal(MESSAGE_TYPE_ENUM.INVENTORY_LOSS_GAIN.value, MESSAGE_TYPE_INVENTORY_LOSS_GAIN, '前端消息类型常量与跳转解析器必须同值');
    const java = readFileSync(new URL(BACKEND_ENUM, import.meta.url), 'utf8');
    assert.match(
      java,
      new RegExp(`SCM_INVENTORY_LOSS_GAIN\\(${MESSAGE_TYPE_INVENTORY_LOSS_GAIN},`),
      '后端 MessageTypeEnum 的数值必须与前端镜像一致，否则入口静默失效'
    );
    const todo = readFileSync(
      new URL('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/dashboard/constant/ScmTodoCardEnum.java', import.meta.url),
      'utf8'
    );
    assert.ok(todo.includes(`"${INVENTORY_LOSS_GAIN_LIST_PATH}`), '消息入口应跳到待办使用的同一个列表页');
    // 驳回通知改用业务类型，而不是留在通用站内信类型上
    assert.match(
      readFileSync(new URL(LOSS_GAIN_SERVICE, import.meta.url), 'utf8'),
      /setMessageType\(MessageTypeEnum\.SCM_INVENTORY_LOSS_GAIN\.getValue\(\)\)/
    );
  });

  test('两个消息详情视图共用解析入口，且绝不按中文标题判定业务', () => {
    for (const file of [ACCOUNT_DETAIL, HEADER_DETAIL]) {
      const vue = code(file);
      assert.match(vue, /import \{messageBusinessLink\} from '\/@\/lib\/message-business-link';/);
      // dataId 必须显式声明在 reactive 里，否则类型收窄拿不到主键
      assert.match(vue, /dataId: ''/);
      assert.match(vue, /messageBusinessLink\(messageDetail\.messageType, messageDetail\.dataId\)/);
      assert.match(vue, /router\.push\(\{path: link\.path, query: link\.query\}\)/);
      assert.doesNotMatch(vue, /messageDetail\.title\s*\.\s*(includes|indexOf|startsWith)/, '不得解析中文标题');
    }
    // 跳转只落在列表页 deep-link 上：列表/详情接口继续负责鉴权，失去权限时仍是 403
    const lossGain = code('../src/views/business/scm/inventory/inventory-loss-gain-list.vue');
    assert.match(lossGain, /const id = deepLinkId\(route\.query\);/);
    assert.match(lossGain, /await openDetailById\(id\);/);
  });
}

// ---------------------------------------------------------------- 来源：todo-deep-link.test.mjs
{
  /**
   * 「待办卡片带筛选 URL，目标页面必须真正消费」的前端契约测试。
   *
   * 钉死三层：
   * 1. 解析器真实执行：白名单命中、非法值回落、缺参回落、重复键只取第一个；
   * 2. 跨端防漂移：直接读后端 `ScmTodoCardEnum` 的 route 字符串，要求其中每个查询键都被目标页面消费
   *    （日后给待办加参数而页面没接上，就是这里要拦住的断层——正是本次审计的原始缺陷）；
   * 3. 三个页面的进入模式统一：`[route.name, route.query]` watch + 名称守卫 + 无条件整体覆盖，
   *    所以 keep-alive 复用能重新套用新 query，普通菜单进入也不会残留上次 deep-link 条件。
   */

  /** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被禁用的写法是合规的。 */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  const TODO_ENUM_JAVA = '../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/dashboard/constant/ScmTodoCardEnum.java';

  const RECEIPT = '../src/views/business/scm/purchase/purchase-receipt-list.vue';
  const LOSS_GAIN = '../src/views/business/scm/inventory/inventory-loss-gain-list.vue';
  const ROUTES = '../src/views/business/scm/delivery/route-list.vue';

  /** 页面路径 → 「deep-link 常量名 + 被覆盖的查询表单对象」。 */
  const PAGES = [
    {path: '/purchase/purchase-receipt-list', file: RECEIPT, spec: 'RECEIPT_DEEP_LINK', form: 'queryForm'},
    {path: '/inventory/inventory-loss-gain-list', file: LOSS_GAIN, spec: 'LOSS_GAIN_DEEP_LINK', form: 'queryForm'},
    {path: '/delivery/routes', file: ROUTES, spec: 'ROUTE_DEEP_LINK', form: 'query'},
  ];

  /** 后端待办枚举里带 query 的 route：路径 → 查询键集合。 */
  function todoQueryKeys() {
    const java = readFileSync(new URL(TODO_ENUM_JAVA, import.meta.url), 'utf8');
    const keys = new Map();
    for (const match of java.matchAll(/"\/([^"]+)\?([^"]+)"/g)) {
      keys.set('/' + match[1], new Set(match[2].split('&').map((pair) => pair.split('=')[0])));
    }
    return keys;
  }

  /** 页面 deep-link 常量里声明的键。 */
  function specKeys(vue, specName) {
    const block = new RegExp(`const ${specName} = \\{([\\s\\S]*?)\\};`).exec(vue);
    assert.ok(block, `${specName} 必须以模块级常量声明 deep-link 白名单`);
    return [...block[1].matchAll(/^\s*(\w+):/gm)].map((m) => m[1]);
  }

  test('解析器：白名单命中才落到筛选，非法值与空值按未提供处理', () => {
    const spec = {status: ['CONFIRMED', 'DRAFT'], receiptNo: null};
    assert.deepEqual(deepLinkFilters({status: 'CONFIRMED'}, spec), {status: 'CONFIRMED', receiptNo: undefined});
    // 自由文本键（null）只 trim，不校验取值
    assert.deepEqual(deepLinkFilters({receiptNo: '  PR2026  '}, spec), {status: undefined, receiptNo: 'PR2026'});
    // URL 可被手改：字典外的值绝不透传给查询接口
    assert.deepEqual(deepLinkFilters({status: "x' OR 1=1--"}, spec), {status: undefined, receiptNo: undefined});
    // 重复键只取第一个，不发明「多值 OR」语义
    assert.deepEqual(deepLinkFilters({status: ['DRAFT', 'CONFIRMED']}, spec).status, 'DRAFT');
  });

  test('解析器：query 为空时所有键回落 undefined（普通菜单进入不残留 deep-link）', () => {
    const spec = {status: ['PENDING'], lossGainNo: null};
    const filters = deepLinkFilters({}, spec);
    assert.deepEqual(filters, {status: undefined, lossGainNo: undefined});
    // 显式空串同样回落，而不是把「已清空」当成一个筛选值
    assert.equal(deepLinkFilters({status: '   '}, spec).status, undefined);
  });

  test('解析器：业务主键只接受纯数字串，且保持字符串不做数值转换', () => {
    assert.equal(deepLinkId({id: '1234567890123456789'}), '1234567890123456789');
    for (const raw of [{}, {id: ''}, {id: 'null'}, {id: '1e5'}, {id: '-3'}, {id: '12.5'}, {id: 'a1'}, {id: []}]) {
      assert.equal(deepLinkId(raw), undefined, `${JSON.stringify(raw)} 不是可用主键`);
    }
  });

  test('跨端一致：待办 route 的每个查询键都被目标页面 deep-link 白名单消费', () => {
    const todoKeys = todoQueryKeys();
    assert.ok(todoKeys.size >= 3, '待办枚举应至少有 3 张带筛选的卡片');
    for (const page of PAGES) {
      const required = todoKeys.get(page.path);
      assert.ok(required, `待办没有指向 ${page.path} 的卡片，PAGES 需要同步`);
      const declared = specKeys(code(page.file), page.spec);
      for (const key of required) {
        assert.ok(declared.includes(key), `${page.path} 的 deep-link 必须消费待办参数 ${key}`);
      }
    }
  });

  test('三个页面把解析结果整体写回查询表单，不做「有才覆盖」', () => {
    // 待办「待仓库确认入库」三个条件全部落到查询表单上——点进来必须还是那批数据
    const receipt = code(RECEIPT);
    for (const key of ['receiptNo', 'status', 'receiptMode', 'putawayStatus']) {
      assert.match(receipt, new RegExp(`queryForm\\.${key} = filters\\.${key};`), `收货单页缺 queryForm.${key}`);
    }
    // receiptMode / putawayStatus 必须有可见筛选控件，不能只存在表单状态里
    assert.match(receipt, /v-model:value="queryForm\.receiptMode"/);
    assert.match(receipt, /v-model:value="queryForm\.putawayStatus"/);

    const lossGain = code(LOSS_GAIN);
    assert.match(lossGain, /queryForm\.status = filters\.status;/);
    assert.match(lossGain, /queryForm\.lossGainNo = filters\.lossGainNo;/);
    // 重置与 deep-link 进入都回到页面默认，不永久保留旧条件
    assert.match(lossGain, /function resetQuery\(\)[\s\S]*?queryForm\.status = undefined;/);

    const routes = code(ROUTES);
    assert.match(routes, /query\.status = filters\.status;/);
    assert.match(routes, /function reset\(\)\s*\{\s*clearQuery\(\);/);

    for (const page of [receipt, lossGain, routes]) {
      assert.doesNotMatch(page, /filters\.\w+ \?\? /, '回退到旧值会让上一次的 deep-link 条件残留');
    }
  });

  test('三个页面用同一个进入模式：route.name 守卫 + route.query 依赖 + immediate', () => {
    for (const page of PAGES) {
      const vue = code(page.file);
      assert.match(vue, /import \{useRoute(?:, useRouter)?\} from 'vue-router';/);
      assert.match(vue, /const route = useRoute\(\);/);
      assert.match(
        vue,
        /watch\(\s*\[\(\) => route\.name, \(\) => route\.query\],/,
        `${page.path} 必须同时依赖 route.name 与整个 route.query（只盯单个键无法在重新进入时回落默认）`
      );
      assert.match(vue, /if \(name !== \w+RouteName\) return;/, `${page.path} 缺少缓存路由守卫，跳走时还会重复查询`);
      assert.match(vue, /\{immediate: true\}/, `${page.path} 首次进入必须应用 query`);
    }
    // 首挂载与复用只允许一条入口，否则会发两次查询请求
    assert.doesNotMatch(code(LOSS_GAIN), /onMounted/);
  });
}

// ---------------------------------------------------------------- 来源：operate-log-audit-contract.test.mjs
{
  /**
   * 通用操作日志「业务上下文」与脱敏的前端契约测试。
   *
   * 钉死这一波破坏后不报错的前端边界（后端 STRPOS 精确匹配与读权限白名单由
   * OperateLogBusinessFilterPgIT / AdminOperateLogBusinessGuardTest 覆盖）：
   * 1. 脱敏是纯函数行为：password/token/secret 等命中键一律替换为占位符，未命中键原样保留，
   *    且递归覆盖嵌套对象与数组元素；
   * 2. 列表页接收并校验路由里的 businessType/businessId（白名单 + 正整数），
   *    首次加载、刷新、对象切换都重新套用，且重置不会残留业务上下文；
   * 3. 单个脏 response / userAgent 只退化该行，不整页失败；
   * 4. 详情页不重复放操作日志按钮；日志列表统一受查询权限保护，
   *    businessType/businessId 的白名单与查询载荷校验仍由列表页断言。
   */

  /** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被禁用的名字是合规的。 */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  const LIST = '../src/views/support/operate-log/operate-log-list.vue';
  const MODAL = '../src/views/support/operate-log/operate-log-detail-modal.vue';
  const CUSTOMER = '../src/views/business/scm/customer/customer-detail.vue';
  const ROUTE_DETAIL = '../src/views/business/scm/delivery/route-detail-page.vue';

  test('脱敏纯函数：敏感键替换为占位符、其余原样、递归覆盖对象与数组', () => {
    const input = {
      password: 'p@ss',
      token: 'abc',
      accessToken: 'x',
      refreshToken: 'y',
      apiKey: 'z',
      authorization: 'Bearer t',
      secret: 'shh',
      userName: 'alice',
      age: 30,
      nested: {pwd: 'q', keep: 'ok', list: [{sessionToken: 's', note: 'n'}]},
    };
    const masked = maskSensitive(input);
    // 命中键全部变成占位符
    for (const key of ['password', 'token', 'accessToken', 'refreshToken', 'apiKey', 'authorization', 'secret']) {
      assert.equal(masked[key], '******', `${key} 应被脱敏`);
    }
    assert.equal(masked.nested.pwd, '******');
    assert.equal(masked.nested.list[0].sessionToken, '******');
    // 未命中键保持原值与结构
    assert.equal(masked.userName, 'alice');
    assert.equal(masked.age, 30);
    assert.equal(masked.nested.keep, 'ok');
    assert.equal(masked.nested.list[0].note, 'n');
    // 顶层原始对象不能被就地改写
    assert.equal(input.password, 'p@ss');
  });

  test('列表页从路由解析并校验业务上下文，白名单外 / 非正整数视为无上下文', () => {
    const vue = code(LIST);
    assert.match(vue, /const BUSINESS_TYPES = \['PRODUCT', 'CUSTOMER', 'DELIVERY_ROUTE'\];/);
    assert.match(vue, /route\.query\.businessType/);
    assert.match(vue, /Number\.isInteger\(businessId\)\s*&&\s*businessId > 0/);
    assert.match(vue, /BUSINESS_TYPES\.includes\(businessType\)/);
  });

  test('业务上下文在首载、重置与对象切换三处都重新套用，旧筛选不得覆盖', () => {
    const vue = code(LIST);
    // businessType/businessId 属于查询态，重置时先整体复位再按当前路由重算
    assert.match(vue, /businessType:\s*undefined[\s\S]*?businessId:\s*undefined/);
    assert.match(vue, /onMounted\(\(\)\s*=>\s*\{[\s\S]*?applyBusinessContext\(\);[\s\S]*?ajaxQuery\(\);/);
    assert.match(vue, /function resetQuery\(\)[\s\S]*?Object\.assign\(queryForm, queryFormState\);[\s\S]*?applyBusinessContext\(\);/);
    // 刷新 / 换对象：监听路由业务参数，重新套用并回到第一页
    assert.match(vue, /watch\(\s*\(\)\s*=>\s*\[route\.query\.businessType,\s*route\.query\.businessId\]/);
    assert.match(vue, /applyBusinessContext\(\);\s*queryForm\.pageNum = 1;/);
  });

  test('逐行规范化：脏 response 退化为 null，单行异常不整页失败', () => {
    const vue = code(LIST);
    assert.match(
      vue,
      /function normalizeRow\(row[^)]*\)\s*\{[\s\S]*?try\s*\{[\s\S]*?JSON\.parse\(row\.response\)[\s\S]*?\}\s*catch\s*\(e\)\s*\{[\s\S]*?row\.response = null;/
    );
    assert.match(vue, /for \(const e of list\) \{\s*normalizeRow\(e\);\s*\}/);
  });

  test('详情弹窗展示前脱敏：参数与返回结果都过 maskSensitive', () => {
    const vue = code(MODAL);
    assert.match(vue, /import \{maskSensitive\} from '\.\/operate-log-mask';/);
    assert.match(vue, /maskedJson\(detail\.param\)/);
    assert.match(vue, /maskedJson\(detail\.response\)/);
    assert.match(vue, /return maskSensitive\(JSON\.parse\(raw\)\)/);
  });

  test('详情页不重复操作日志入口，统一日志列表仍受查询权限保护', () => {
    for (const file of [CUSTOMER, ROUTE_DETAIL]) {
      assert.doesNotMatch(code(file), /openOperateLog|support:operateLog:query/, `${file} 不应重复提供操作日志入口`);
    }
    assert.match(code(LIST), /v-privilege="'support:operateLog:query'"/);
  });
}
