/**
 * SCM UI foundation 的契约单测（主题变量 + 全局容器对齐）。
 *
 * ## 主题变量
 *
 * 守的是一条<b>曾经真实踩过、而且不报错</b>的线：项目里出现过 30+ 处
 * `var(--ant-color-text-secondary)` 之类的写法，看起来像 antd 的主题变量，
 * 实际上 antd-vue 4.2.5 <b>默认不开 cssVar</b>，静态产物里根本搜不到 `--ant-color-*` 的定义，
 * 而 `src/` 里也没有任何地方定义过它们 —— 于是这些声明<b>全部静默失效</b>，
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
 * antd 的 `.ant-drawer-footer` 只设了 padding / border-top / flexShrink，<b>没有设对齐</b>，
 * 而 `.ant-modal-footer` 自带 `textAlign: end` —— 同一套「取消 + 保存」在抽屉里贴左边、
 * 在弹窗里贴右边。这条也只能靠 foundation 统一，页面各自加类会漏。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

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

test('表头统一居中，且只动表头、不动列内容', () => {
  const table = readFileSync(new URL('../src/theme/scm/table.less', import.meta.url), 'utf8');
  // antd 把 column.align 写成 <th>/<td> 的行内样式，普通选择器压不过它，必须 !important
  assert.match(table, /\.ant-table-thead > tr > th \{[\s\S]{0,160}text-align: center !important/,
      '表头未统一居中（或漏了 !important，行内样式会盖住它）');
  // 只动表头：把 tbody 一起拉进来会废掉「数量 / 金额右对齐」
  assert.doesNotMatch(table, /\.ant-table-tbody[\s\S]{0,120}text-align: center !important/,
      '不得改动列内容的对齐');
  // 规则必须真的被引进来
  const index = readFileSync(new URL('../src/theme/scm/index.less', import.meta.url), 'utf8');
  assert.match(index, /@import '\.\/table\.less';/);
});
