/** 只读核对微信产物的模块引用和包边界；不搬移、不改写编译文件。 */
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { resolve, relative, posix } from 'node:path';
import { fileURLToPath, URL } from 'node:url';
import process from 'node:process';
import console from 'node:console';

const output = resolve(fileURLToPath(new URL('../', import.meta.url)), process.argv[2] || 'dist/build/mp-weixin');
const read = (name) => readFileSync(resolve(output, name), 'utf8');
const app = JSON.parse(read('app.json'));
const files = [];
const scan = (dir) => {
  for (const item of readdirSync(dir, { withFileTypes: true })) {
    const path = resolve(dir, item.name);
    if (item.isDirectory()) scan(path);
    else files.push(relative(output, path).replaceAll('\\', '/'));
  }
};
scan(output);
const packageOf = (file) => app.subPackages.find((item) => file.startsWith(item.root + '/'))?.root || 'main';
const modules = files.filter((file) => file.endsWith('.js'));
const graph = new Map();
for (const file of modules) {
  const dependencies = [];
  for (const match of read(file).matchAll(/\brequire\(\s*['"]([./][^'"]+)['"]\s*\)/g)) {
    const target = match[1].startsWith('/') ? match[1].slice(1) : posix.normalize(posix.join(posix.dirname(file), match[1]));
    const js = target.endsWith('.js') ? target : target + '.js';
    assert.ok(existsSync(resolve(output, js)), file + '引用了缺失文件：' + js);
    const owner = packageOf(file),
      other = packageOf(js);
    assert.ok(other === 'main' || other === owner, file + '跨包引用：' + js);
    dependencies.push(js);
  }
  graph.set(file, dependencies);
}

const reachable = new Set();
const components = new Set();
const visit = (file) => {
  if (reachable.has(file)) return;
  assert.ok(graph.has(file), '入口或组件模块缺失：' + file);
  reachable.add(file);
  for (const dependency of graph.get(file) || []) visit(dependency);
};
const visitComponent = (file) => {
  if (components.has(file)) return;
  components.add(file);
  visit(file + '.js');
  const json = resolve(output, file + '.json');
  assert.ok(existsSync(json), '页面或组件配置缺失：' + file + '.json');
  if (file !== 'app') assert.ok(existsSync(resolve(output, file + '.wxml')), '页面或组件模板缺失：' + file + '.wxml');
  for (const value of Object.values(JSON.parse(readFileSync(json, 'utf8')).usingComponents || {})) {
    if (value.startsWith('plugin://')) continue;
    const component = value.startsWith('/') ? value.slice(1) : posix.normalize(posix.join(posix.dirname(file), value));
    assert.ok(packageOf(component) === 'main' || packageOf(component) === packageOf(file), file + '跨包引用组件：' + component);
    visitComponent(component);
  }
};
visitComponent('app');
for (const page of app.pages) visitComponent(page);
const unusedMain = modules.filter((file) => packageOf(file) === 'main' && !reachable.has(file));
assert.deepEqual(unusedMain, [], '主包存在仅分包使用或未使用的JS');
const mainModules = reachable.size;
// 先单独确认主包闭包，再验证分包入口，避免分包引用掩盖主包中的闲置模块。
for (const group of app.subPackages) {
  for (const page of group.pages) visitComponent(group.root + '/' + page);
}
const sizes = Object.fromEntries(['main', ...app.subPackages.map((item) => item.root)].map((root) => [root, 0]));
for (const file of files.filter((file) => !/^project\./.test(file))) sizes[packageOf(file)] += statSync(resolve(output, file)).size;
for (const [name, size] of Object.entries(sizes)) assert.ok(size < 2 * 1024 * 1024, name + '超过2MiB');
console.log(JSON.stringify({ modules: modules.length, mainModules, unusedMain, sizes }, null, 2));
