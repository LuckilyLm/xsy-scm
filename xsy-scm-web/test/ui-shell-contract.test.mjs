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

test('两种侧栏布局隐藏子菜单箭头但保留内联菜单交互', () => {
  const sideExpandMenu = code('../src/layout/components/side-expand-menu/recursion-menu.vue');
  assert.match(recursionMenu, /:deep\(\.ant-menu-submenu-arrow\)\s*\{\s*display:\s*none;/,
      '传统侧栏只应在菜单作用域内隐藏子菜单箭头');
  assert.match(sideExpandMenu, /:deep\(\.ant-menu-submenu-arrow\)\s*\{\s*display:\s*none;/,
      '展开式侧栏只应在子菜单作用域内隐藏子菜单箭头');
  assert.match(recursionMenu, /mode="inline"/, '传统侧栏必须保留内联子菜单');
  assert.match(sideExpandMenu, /mode="inline"/, '展开式侧栏必须保留内联子菜单');
  assert.match(recursionMenu, /\.smart-menu:not\(\.ant-menu-dark\)/,
      '菜单图标配色规则仍须排除暗色菜单');
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
