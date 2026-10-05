/**
 * UI 令牌（CSS 自定义属性）契约单测。
 *
 * 守的是一条**曾经真实踩过、而且不报错**的线：项目里出现过 30+ 处
 * `var(--ant-color-text-secondary)` 之类的写法，看起来像 antd 的主题变量，
 * 实际上 antd-vue 4.2.5 **默认不开 cssVar**，静态产物里根本搜不到 `--ant-color-*` 的定义，
 * 而 `src/` 里也没有任何地方定义过它们 —— 于是这些声明**全部静默失效**，
 * 颜色退化成继承值，页面照样能跑、构建照样通过、单测照样全绿。
 *
 * 项目真正的主题变量只有一套：`useScmThemeVars()` 在 `<html>` 上落下的 `--scm-*`。
 * 因此这里钉两件事：
 *
 * 1. 全仓库不得再出现 `--ant-color-*`；
 * 2. 用到的每一个 `--scm-*` 都必须在 `useScmThemeVars()` 里有定义（打错一个字母同样是静默失效）。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

const SRC = fileURLToPath(new URL('../src/', import.meta.url));
const THEME_VARS = '../src/theme/scm/use-scm-theme-vars.ts';

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
