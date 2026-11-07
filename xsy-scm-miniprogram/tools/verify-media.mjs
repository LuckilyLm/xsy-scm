/** 核验微信构建产物的本地资源边界，不修改分包或编译文件。 */
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { resolve, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { URL } from 'node:url';
import process from 'node:process';
import console from 'node:console';

const root = fileURLToPath(new URL('../', import.meta.url));
const output = resolve(root, process.argv[2] || 'dist/build/mp-weixin');
const files = [];
const scan = (dir) => {
  for (const item of readdirSync(dir, { withFileTypes: true })) {
    const path = resolve(dir, item.name);
    if (item.isDirectory()) scan(path);
    else files.push({ path, name: relative(output, path).replaceAll('\\', '/'), size: statSync(path).size });
  }
};
scan(output);
const names = new Set(files.map((file) => file.name));
const app = JSON.parse(readFileSync(resolve(output, 'app.json'), 'utf8'));
for (const name of [
  'app.js',
  'app.wxss',
  'pages/home/index.js',
  'pages/login/login.js',
  'common/vendor.js',
  'store/index.js',
  'lib/xsy-request.js',
]) {
  assert.ok(names.has(name), '基础代码缺失：' + name);
}
assert.equal(app.lazyCodeLoading, 'requiredComponents');
assert.ok(app.pages.includes('pages/home/index'));
const media = files.filter((file) => /\.(png|jpe?g|webp|gif|svg|mp3|wav|mp4)$/i.test(file.name));
assert.equal(media.length, 16, '包内只保留导航、Logo与表单小图');
assert.ok(media.every((file) => /^static\/mall-(navigation|login)\//.test(file.name)));
const bytes = media.reduce((sum, file) => sum + file.size, 0);
assert.ok(bytes < 200 * 1024, '包内媒体超过200KiB');
for (const file of media.filter((file) => !file.name.endsWith('/logo.png'))) assert.ok(file.size < 10 * 1024, file.name + '超过10KiB');
assert.ok(!files.some((file) => file.name.startsWith('media/')));
const subRoots = app.subPackages.map((item) => item.root + '/');
const mainBytes = files
  .filter((file) => !subRoots.some((prefix) => file.name.startsWith(prefix)) && !/^project\./.test(file.name))
  .reduce((sum, file) => sum + file.size, 0);
assert.ok(mainBytes < 2 * 1024 * 1024, '主包超过2MiB');
console.log(
  JSON.stringify(
    { mediaFiles: media.length, mediaBytes: bytes, mainBytes, onlineArtworkInPackage: false, lazyCodeLoading: app.lazyCodeLoading },
    null,
    2
  )
);
