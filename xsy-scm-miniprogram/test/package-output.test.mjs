import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync, unlinkSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import process from 'node:process';
import test from 'node:test';

const workspace = fileURLToPath(new URL('../../', import.meta.url));
const tool = fileURLToPath(new URL('../tools/verify-package.mjs', import.meta.url));
const fixtures = resolve(workspace, '.codex-tmp/package-output-tests');
mkdirSync(fixtures, { recursive: true });

/** 创建主包页面及一个分包组件，验证微信JSON声明的加载路径。 */
function fixture() {
  const root = mkdtempSync(resolve(fixtures, 'case-'));
  const put = (name, content) => {
    const path = resolve(root, name);
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, typeof content === 'string' ? content : JSON.stringify(content));
  };
  put('app.js', '');
  put('app.json', { pages: ['pages/home'], subPackages: [{ root: 'business', pages: ['detail'] }] });
  put('pages/home.js', '');
  put('pages/home.json', {});
  put('pages/home.wxml', '<view/>');
  put('business/detail.js', '');
  put('business/detail.json', { usingComponents: { card: './card' } });
  put('business/detail.wxml', '<card/>');
  put('business/card.js', '');
  put('business/card.json', { component: true });
  put('business/card.wxml', '<view/>');
  return { root, put, run: () => spawnSync(process.execPath, [tool, root], { encoding: 'utf8' }) };
}

test('package audit accepts complete main and business page declarations', () => {
  const check = fixture().run();
  assert.equal(check.status, 0, check.stderr);
  assert.deepEqual(JSON.parse(check.stdout).unusedMain, []);
});

test('package audit rejects missing component modules, configurations and templates in a subpackage', () => {
  for (const extension of ['js', 'json', 'wxml']) {
    const check = fixture();
    unlinkSync(resolve(check.root, 'business/card.' + extension));
    const result = check.run();
    assert.notEqual(result.status, 0, extension);
    assert.match(result.stderr, new RegExp('business/card\\.' + extension));
  }
});

test('subpackage use cannot hide an unused main-package module', () => {
  const check = fixture();
  check.put('sub-only.js', '');
  check.put('business/detail.js', 'require("../sub-only.js");');
  const result = check.run();
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /主包存在仅分包使用或未使用的JS/);
});

test('main-package pages cannot declare a subpackage component', () => {
  const check = fixture();
  check.put('pages/home.json', { usingComponents: { card: '../business/card' } });
  const result = check.run();
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /跨包引用组件/);
});
