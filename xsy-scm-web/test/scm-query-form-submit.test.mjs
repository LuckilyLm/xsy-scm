/**
 * SCM 列表页查询按钮可达性静态门禁。
 *
 * 起因：真实浏览器验收点「查询」后一个请求都没发出去。根因是 ant-design-vue 的
 * `Form.handleSubmit` 只在 `props.model` 存在时才校验并 emit `finish`
 * （见 node_modules/ant-design-vue/es/form/Form.js），所以
 * `<a-form @finish="search">` + `<a-button html-type="submit">` 在无 `:model` 时是**静默死按钮**：
 * 页面照常渲染、照常首屏查询，只有点按钮没反应，单测里 `search()` 的实现再怎么对也没用。
 *
 * 本门禁只钉两件事：
 * 1. 业务页面不得再用 `html-type="submit"`（查询按钮沿用 SmartAdmin 原生 `@click` 范式）；
 * 2. 任何带 `@finish` 的 `a-form` 必须同时带 `:model`，否则 finish 分支永不触发。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readdirSync, readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

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
