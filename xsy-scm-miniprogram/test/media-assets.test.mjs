import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import { ref } from 'vue';
import { withMedia, TEST_MEDIA_ROOT } from './mall-media-loader.mjs';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');

test('online artwork retains every original byte and the directory digest matches the manifest', () => {
  const manifest = JSON.parse(read('../media/manifest.json'));
  assert.equal(manifest.files.length, 46);
  for (const item of manifest.files) {
    const data = readFileSync(new URL('../media/' + item.file, import.meta.url));
    assert.equal(data.length, item.size, item.file);
    assert.equal(createHash('sha256').update(data).digest('hex'), item.sha256, item.file);
    assert.ok(!new URL('../media/' + item.file, import.meta.url).pathname.includes('/src/'));
  }
  const canonical = JSON.stringify(manifest.files.map((item) => ({ file: item.file, sha256: item.sha256, size: item.size })));
  assert.equal(createHash('sha256').update(canonical).digest('hex').slice(0, 16), manifest.digest);
});

test('local navigation, logo and form icons remain in the package and media stays below 200KB', () => {
  const root = new URL('../src/static/', import.meta.url);
  const files = [];
  const scan = (dir) => {
    for (const item of readdirSync(dir, { withFileTypes: true })) {
      const url = new URL(item.name + (item.isDirectory() ? '/' : ''), dir);
      if (item.isDirectory()) scan(url);
      else if (/\.(png|jpe?g|webp|gif|mp3|wav|mp4)$/i.test(item.name)) files.push(url);
    }
  };
  scan(root);
  assert.equal(files.length, 16);
  assert.ok(files.reduce((sum, file) => sum + statSync(file).size, 0) < 200 * 1024);
  assert.ok(files.every((file) => /mall-navigation|mall-login/.test(file.pathname)));
  assert.ok(statSync(new URL('mall-login/logo.png', root)).size);
  for (const key of ['home', 'category', 'cart', 'orders', 'mine']) {
    for (const state of ['normal', 'selected']) assert.ok(statSync(new URL(`mall-navigation/${key}-${state}.png`, root)).size < 10 * 1024);
  }
});

test('media URLs use configuration, normalize slashes and avoid unsafe or missing paths', async () => {
  const load = (base) => import(`data:text/javascript;base64,${Buffer.from(withMedia('export {mallMedia};', base)).toString('base64')}`);
  const configured = await load(TEST_MEDIA_ROOT + '/');
  assert.equal(configured.mallMedia('mall-login/hero.png'), TEST_MEDIA_ROOT + '/mall-login/hero.png');
  assert.equal(configured.mallMedia('../secret'), '');
  assert.equal(configured.mallMedia('/mall-login/hero.png'), '');
  assert.equal((await load('')).mallMedia('mall-login/hero.png'), '');
  const visuals = await import(
    `data:text/javascript;base64,${Buffer.from(withMedia(read('../src/constants/mall-visual-const.js'), '')).toString('base64')}`
  );
  assert.equal(visuals.MALL_EMPTY_IMAGES.products, '');
  assert.equal(visuals.MALL_PROFILE_IMAGES.orders, '');
  assert.match(visuals.MALL_NAVIGATION_IMAGES.home.normal, /^\/static\//);
});

test('failed remote artwork uses fallback and retries when its URL changes or the page returns', () => {
  const props = { src: 'https://assets.example.com/a.png' };
  const hooks = {};
  const context = vm.createContext({
    ref,
    defineOptions: () => {},
    defineProps: () => props,
    defineEmits: () => {},
    watch: (read, change) => {
      hooks.read = read;
      hooks.change = change;
    },
    onShow: (show) => {
      hooks.show = show;
    },
  });
  const source = read('../src/components/common/xsy-remote-image.vue');
  const script = source
    .match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .trimStart()
    .replace(/^[^\S\r\n]*import[\s\S]*?;\r?\n/gm, '');
  vm.runInContext(script + '\nglobalThis.failed = failed;', context);
  context.failed.value = true;
  props.src = 'https://assets.example.com/b.png';
  hooks.change(hooks.read());
  assert.equal(context.failed.value, false);
  context.failed.value = true;
  hooks.show();
  assert.equal(context.failed.value, false);
  assert.match(source, /v-if="src && !failed"/);
  assert.match(source, /@error="failed = true"/);
});
