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
  // 迁移逻辑是「版本不等就整体回落新默认值」，版本不升，已有浏览器会一直用 localStorage 里的旧宽度
  assert.ok(version >= 8, '侧栏宽度已从 184 收到 168，configVersion 必须 ≥ 8 才能迁移老用户');
  assert.match(
      code('../src/store/modules/system/app-config.ts'),
      /cached\.configVersion === appDefaultConfig\.configVersion/,
      '配置迁移逻辑被改动，本断言的前提失效，请重新核对',
  );
});

test('侧栏用单档固定宽度，不随子菜单展开变化', () => {
  // 曾经试过「收起时窄、展开二级时放宽」的两档方案，实测下来切换会让内容区左右跳动，
  // 已放弃：侧栏只认用户设置的那一个宽度。这条断言防止两档逻辑被无意中重新引入。
  assert.doesNotMatch(sideLayout, /SIDE_MENU_EXPANDED_WIDTH|menuExpanded/,
      '侧栏不应再按展开状态改宽度');
  assert.match(sideLayout, /const sideMenuWidth = computed\(\(\) => useAppConfigStore\(\)\.\$state\.sideMenuWidth\)/,
      '侧栏宽度应直接取用户设置值');
  // 子菜单展开后的缩进保持 antd 默认（24/48）：收窄缩进虽然能换来更窄的侧栏，
  // 但展开后二级几乎贴着左边，层级感丢失 —— 已按使用反馈还原。
  assert.doesNotMatch(code(RECURSION_MENU), /inline-indent/, '子菜单缩进应保持 antd 默认，不再收窄');
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

test('侧边栏隐藏一级菜单的展开箭头，顶部菜单不受影响', () => {
  // 箭头是 antd 默认的 <i class="ant-menu-submenu-arrow">。菜单项本身点一下就能展开，
  // 箭头在窄侧栏里只是重复占位，去掉后标题才有横向空间让侧栏收窄而不换行。
  // 必须走 :deep()：scoped 样式不会给 antd 子组件内部的节点加 data-v 属性。
  assert.match(recursionMenu, /:deep\(\.ant-menu-submenu-arrow\)\s*\{[\s\S]{0,80}display:\s*none/,
      '侧边栏应隐藏 .ant-menu-submenu-arrow');
  // 顶部菜单复用同一个 .smart-menu 类，但它是横向布局、子菜单靠浮层展开，箭头在那里是必要提示
  assert.doesNotMatch(code('../src/layout/components/top-menu/recursion-menu.vue'),
      /submenu-arrow/, '顶部菜单不应跟着隐藏箭头');
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
